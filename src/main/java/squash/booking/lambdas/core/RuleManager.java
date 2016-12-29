/**
 * Copyright 2016 Robin Steel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package squash.booking.lambdas.core;

import squash.deployment.lambdas.utils.RetryHelper;
import squash.deployment.lambdas.utils.RetryHelper.ThrowingSupplier;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

/**
 * Manages all booking rules and their exclusions.
 *
 * <p>This manages all booking rules and exclusions and their persistence in the
 * database - which is currently SimpleDB. The database interactions are handled
 * using an {@link IOptimisticPersister IOptimisticPersister}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class RuleManager implements IRuleManager {
  private String ruleItemName;
  private Integer maxNumberOfRules = 100;
  protected Integer maxNumberOfDatesToExclude = 30;
  private Region region;
  private String adminSnsTopicArn;
  protected IOptimisticPersister optimisticPersister;
  private IBookingManager bookingManager;
  private LambdaLogger logger;
  private Boolean initialised = false;

  @Override
  public final void initialise(IBookingManager bookingManager, LambdaLogger logger)
      throws Exception {

    if (initialised) {
      throw new IllegalStateException("The rule manager has already been initialised");
    }

    this.bookingManager = bookingManager;
    this.logger = logger;
    ruleItemName = "BookingRulesAndExclusions";
    this.optimisticPersister = getOptimisticPersister();
    optimisticPersister.initialise(maxNumberOfRules, logger);

    adminSnsTopicArn = getEnvironmentVariable("AdminSNSTopicArn");
    region = Region.getRegion(Regions.fromName(getEnvironmentVariable("AWS_REGION")));
    initialised = true;
  }

  @Override
  public Set<BookingRule> createRule(BookingRule bookingRuleToCreate) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The rule manager has not been initialised");
    }

    // We retry the put of the rule if necessary if we get a
    // ConditionalCheckFailed exception, i.e. if someone else modifies the
    // database between us reading and writing it.
    return RetryHelper
        .DoWithRetries(() -> {
          Set<BookingRule> bookingRules = null;

          // Check that non-recurring rule is not for a date in the past.
            if (!bookingRuleToCreate.getIsRecurring()) {
              if ((new SimpleDateFormat("yyyy-MM-dd")).parse(
                  bookingRuleToCreate.getBooking().getDate()).before(
                  new SimpleDateFormat("yyyy-MM-dd").parse(getCurrentLocalDate().format(
                      DateTimeFormatter.ofPattern("yyyy-MM-dd"))))) {
                logger
                    .log("Cannot add non-recurring booking rule for a date in the past, so throwing a 'Booking rule creation failed' exception");
                throw new Exception("Booking rule creation failed");
              }
            }

            // We should POST or DELETE to the BookingRuleExclusion resource,
            // with a BookingRule, and an exclusion date. This will call through
            // to the addBookingRuleExclusion or deleteBookingRuleExclusion
            // methods on this manager.
            logger.log("About to create booking rule in simpledb: " + bookingRuleToCreate);
            ImmutablePair<Optional<Integer>, Set<BookingRule>> versionedBookingRules = getVersionedBookingRules();
            bookingRules = versionedBookingRules.right;

            // Check that the rule we're creating does not clash with an
            // existing rule.
            if (doesRuleClash(bookingRuleToCreate, bookingRules)) {
              logger
                  .log("Cannot create rule as it clashes with existing rule, so throwing a 'Booking rule creation failed - rule would clash' exception");
              throw new Exception("Booking rule creation failed - rule would clash");
            }

            logger
                .log("The new rule does not clash with existing rules - so proceeding to create rule");

            String attributeName = getAttributeNameFromBookingRule(bookingRuleToCreate);
            String attributeValue = "";
            if (bookingRuleToCreate.getDatesToExclude().length > 0) {
              attributeValue = StringUtils.join(bookingRuleToCreate.getDatesToExclude(), ",");
            }
            logger.log("ItemName: " + ruleItemName);
            logger.log("AttributeName: " + attributeName);
            logger.log("AttributeValue: " + attributeValue);
            ReplaceableAttribute bookingRuleAttribute = new ReplaceableAttribute();
            bookingRuleAttribute.setName(attributeName);
            bookingRuleAttribute.setValue(attributeValue);

            optimisticPersister.put(ruleItemName, versionedBookingRules.left, bookingRuleAttribute);
            bookingRules.add(bookingRuleToCreate);
            return bookingRules;
          }, Exception.class, Optional.of("Database put failed - conditional check failed"), logger);
  }

  @Override
  public List<BookingRule> getRules() throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The rule manager has not been initialised");
    }

    logger.log("About to get all booking rules from simpledb");

    List<BookingRule> bookingRules = new ArrayList<>();
    bookingRules.addAll(getVersionedBookingRules().right);
    return bookingRules;
  }

  @Override
  public void deleteRule(BookingRule bookingRuleToDelete) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The rule manager has not been initialised");
    }

    logger.log("About to delete booking rule from simpledb: " + bookingRuleToDelete.toString());

    String attributeName = getAttributeNameFromBookingRule(bookingRuleToDelete);
    String attributeValue = StringUtils.join(bookingRuleToDelete.getDatesToExclude(), ",");
    logger.log("Booking rule attribute name is: " + attributeName);
    logger.log("Booking rule attribute value is: " + attributeValue);
    Attribute attribute = new Attribute();
    attribute.setName(attributeName);
    attribute.setValue(attributeValue);
    optimisticPersister.delete(ruleItemName, attribute);

    logger.log("Deleted booking rule.");
    return;
  }

  @Override
  public void deleteAllBookingRules() throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The rule manager has not been initialised");
    }

    logger.log("Getting all booking rules to delete");
    List<BookingRule> bookingRules = getRules();
    logger.log("Found " + bookingRules.size() + " booking rules to delete");
    logger.log("About to delete all booking rules");
    for (BookingRule bookingRule : bookingRules) {
      RetryHelper.DoWithRetries(() -> {
        deleteRule(bookingRule);
        return null;
      }, AmazonServiceException.class, Optional.of("429"), logger);
    }
    logger.log("Deleted all booking rules");
  }

  @Override
  public Optional<BookingRule> addRuleExclusion(String dateToExclude,
      BookingRule bookingRuleToAddExclusionTo) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The rule manager has not been initialised");
    }

    logger.log("About to add exclusion for " + dateToExclude + " to booking rule: "
        + bookingRuleToAddExclusionTo.toString());

    // We retry the addition of the exclusion if necessary if we get a
    // ConditionalCheckFailed exception, i.e. if someone else modifies
    // the database between us reading and writing it.
    return RetryHelper
        .DoWithRetries(
            (ThrowingSupplier<Optional<BookingRule>>) (() -> {
              ImmutablePair<Optional<Integer>, Set<BookingRule>> versionedBookingRules = getVersionedBookingRules();
              Set<BookingRule> existingBookingRules = versionedBookingRules.right;

              // Check the BookingRule we're adding the exclusion to still
              // exists
              Optional<BookingRule> existingRule = existingBookingRules.stream()
                  .filter(rule -> rule.equals(bookingRuleToAddExclusionTo)).findFirst();
              if (!existingRule.isPresent()) {
                logger.log("Trying to add an exclusion to a booking rule that does not exist.");
                throw new Exception("Booking rule exclusion addition failed");
              }

              // Check rule is recurring - we cannot add exclusions to
              // non-recurring rules
              if (!existingRule.get().getIsRecurring()) {
                logger.log("Trying to add an exclusion to a non-recurring booking rule.");
                throw new Exception("Booking rule exclusion addition failed");
              }

              // Check that the rule exclusion we're creating does not exist
              // already.
              if (ArrayUtils.contains(existingRule.get().getDatesToExclude(), dateToExclude)) {
                logger
                    .log("An identical booking rule exclusion exists already - so quitting early");
                return Optional.empty();
              }

              // Check the exclusion is for the right day of the week.
              DayOfWeek dayToExclude = dayOfWeekFromDate(dateToExclude);
              DayOfWeek dayOfBookingRule = dayOfWeekFromDate(existingRule.get().getBooking()
                  .getDate());
              if (!dayToExclude.equals(dayOfBookingRule)) {
                logger
                    .log("Exclusion being added and target booking rule are for different days of the week.");
                throw new Exception("Booking rule exclusion addition failed");
              }

              // Check it is not in the past, relative to now, or to the
              // Booking rule start date.
              Date bookingRuleStartDate = new SimpleDateFormat("yyyy-MM-dd").parse(existingRule
                  .get().getBooking().getDate());
              Date excludedDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateToExclude);
              if (excludedDate.before(bookingRuleStartDate)) {
                logger.log("Exclusion being added is before target booking rule start date.");
                throw new Exception("Booking rule exclusion addition failed");
              }
              if (excludedDate.before(new SimpleDateFormat("yyyy-MM-dd")
                  .parse(getCurrentLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))) {
                logger.log("Exclusion being added is in the past.");
                throw new Exception("Booking rule exclusion addition failed");
              }

              // Check we'll not exceed the maximum number of dates to exclude
              // (this limit is here as SimpleDB has a 1024-byte limit for
              // attribute
              // values).
              Set<String> datesToExclude = Sets.newHashSet(bookingRuleToAddExclusionTo
                  .getDatesToExclude());
              if (datesToExclude.size() >= maxNumberOfDatesToExclude) {
                logger.log("The maximum number of booking rule exclusions("
                    + maxNumberOfDatesToExclude + ") exists already.");
                throw new Exception("Booking rule exclusion addition failed - too many exclusions");
              }

              logger.log("Proceeding to add the new rule exclusion");
              datesToExclude.add(dateToExclude);
              String attributeName = getAttributeNameFromBookingRule(bookingRuleToAddExclusionTo);
              String attributeValue = StringUtils.join(datesToExclude, ",");
              logger.log("ItemName: " + ruleItemName);
              logger.log("AttributeName: " + attributeName);
              logger.log("AttributeValue: " + attributeValue);
              ReplaceableAttribute bookingRuleAttribute = new ReplaceableAttribute();
              bookingRuleAttribute.setName(attributeName);
              bookingRuleAttribute.setValue(attributeValue);
              bookingRuleAttribute.setReplace(true);

              optimisticPersister.put(ruleItemName, versionedBookingRules.left,
                  bookingRuleAttribute);
              BookingRule updatedBookingRule = new BookingRule(bookingRuleToAddExclusionTo);
              updatedBookingRule.setDatesToExclude(datesToExclude.toArray(new String[datesToExclude
                  .size()]));
              logger.log("Added new rule exclusion");
              return Optional.of(updatedBookingRule);
            }), Exception.class, Optional.of("Database put failed - conditional check failed"),
            logger);
  }

  @Override
  public Optional<BookingRule> deleteRuleExclusion(String dateNotToExclude,
      BookingRule bookingRuleToDeleteExclusionFrom) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The rule manager has not been initialised");
    }

    logger.log("About to delete exclusion for " + dateNotToExclude + " from booking rule: "
        + bookingRuleToDeleteExclusionFrom.toString());

    // We retry the deletion of the exclusion if necessary if we get a
    // ConditionalCheckFailed exception, i.e. if someone else modifies
    // the database between us reading and writing it.
    return RetryHelper
        .DoWithRetries(
            (ThrowingSupplier<Optional<BookingRule>>) (() -> {
              ImmutablePair<Optional<Integer>, Set<BookingRule>> versionedBookingRules = getVersionedBookingRules();
              Set<BookingRule> existingBookingRules = versionedBookingRules.right;

              // Check the BookingRule we're deleting the exclusion from still
              // exists
              Optional<BookingRule> existingRule = existingBookingRules.stream()
                  .filter(rule -> rule.equals(bookingRuleToDeleteExclusionFrom)).findFirst();
              if (!existingRule.isPresent()) {
                logger
                    .log("Trying to delete an exclusion from a booking rule that does not exist, so swallowing and continuing");
                return Optional.empty();
              }

              // Check that the rule exclusion we're deleting still exists.
              if (!Arrays.asList(existingRule.get().getDatesToExclude()).contains(dateNotToExclude)) {
                logger
                    .log("The booking rule exclusion being deleted no longer exists - so quitting early");
                return Optional.empty();
              }

              // Check deleting this exclusion does not cause a latent rule
              // clash to manifest. Do by pretending to add this rule again.
              existingBookingRules.remove(existingRule.get());
              Set<String> datesToExclude = Sets.newHashSet(bookingRuleToDeleteExclusionFrom
                  .getDatesToExclude());
              datesToExclude.remove(dateNotToExclude);
              existingRule.get().setDatesToExclude(
                  datesToExclude.toArray(new String[datesToExclude.size()]));
              if (doesRuleClash(existingRule.get(), existingBookingRules)) {
                logger
                    .log("Cannot delete booking rule exclusion as remaining rules would then clash");
                throw new Exception("Booking rule exclusion deletion failed - latent clash exists");
              }

              logger.log("Proceeding to delete the rule exclusion");
              String attributeName = getAttributeNameFromBookingRule(bookingRuleToDeleteExclusionFrom);
              String attributeValue = StringUtils.join(datesToExclude, ",");
              logger.log("ItemName: " + ruleItemName);
              logger.log("AttributeName: " + attributeName);
              logger.log("AttributeValue: " + attributeValue);
              ReplaceableAttribute bookingRuleAttribute = new ReplaceableAttribute();
              bookingRuleAttribute.setName(attributeName);
              bookingRuleAttribute.setValue(attributeValue);
              bookingRuleAttribute.setReplace(true);

              optimisticPersister.put(ruleItemName, versionedBookingRules.left,
                  bookingRuleAttribute);
              BookingRule updatedBookingRule = new BookingRule(bookingRuleToDeleteExclusionFrom);
              updatedBookingRule.setDatesToExclude(datesToExclude.toArray(new String[datesToExclude
                  .size()]));
              logger.log("Deleted rule exclusion");
              return Optional.of(updatedBookingRule);
            }), Exception.class, Optional.of("Database put failed - conditional check failed"),
            logger);
  }

  @Override
  public List<Booking> applyRules(String date) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The rule manager has not been initialised");
    }

    List<Booking> ruleBookings = new ArrayList<>();
    try {
      // Apply rules only if date is not in the past.
      Boolean applyDateIsInPast = (new SimpleDateFormat("yyyy-MM-dd").parse(date))
          .before((new SimpleDateFormat("yyyy-MM-dd").parse(getCurrentLocalDate().format(
              DateTimeFormatter.ofPattern("yyyy-MM-dd")))));
      if (!applyDateIsInPast) {
        logger.log("About to apply booking rules for date: " + date);
        for (BookingRule rule : getRules()) {
          logger.log("Considering booking rule: " + rule);
          Boolean ruleIsNonRecurringAndForApplyDate = !rule.getIsRecurring()
              && rule.getBooking().getDate().equals(date);
          Boolean ruleIsRecurringAndStartsOnOrBeforeApplyDate = rule.getIsRecurring()
              && dayOfWeekFromDate(rule.getBooking().getDate()).equals(dayOfWeekFromDate(date))
              && !((new SimpleDateFormat("yyyy-MM-dd")).parse(date).before((new SimpleDateFormat(
                  "yyyy-MM-dd")).parse(rule.getBooking().getDate())));
          if (ruleIsNonRecurringAndForApplyDate || ruleIsRecurringAndStartsOnOrBeforeApplyDate) {
            if (ruleIsRecurringAndStartsOnOrBeforeApplyDate) {
              logger
                  .log("Booking rule recurring and starts before date that rules are being applied to: "
                      + rule.toString());
              // Does this rule have a relevant exclusion?
              if (ArrayUtils.contains(rule.getDatesToExclude(), date)) {
                logger.log("Recurring rule does not apply as it has a matching rule exclusion");
                continue;
              }
            } else {
              logger
                  .log("Booking rule non-recurring but applies to date that rules are being applied to.");
            }
            logger.log("Applying booking rule to create booking: " + rule.toString());
            Booking booking = rule.getBooking();
            booking.setDate(date);
            bookingManager.createBooking(booking);
            ruleBookings.add(booking);
            // Short sleep to minimise chance of getting TooManyRequests error
            try {
              Thread.sleep(10);
            } catch (InterruptedException interruptedException) {
              logger.log("Sleep before applying next rule has been interrupted.");
            }
            logger.log("Rule-based booking created.");
          } else {
            logger.log("Rule does not apply to date that rules are being applied to.");
          }
        }
      }
    } catch (Exception exception) {
      logger.log("Exception caught while applying booking rules - so notifying sns topic");
      getSNSClient()
          .publish(
              adminSnsTopicArn,
              "Apologies - but there was an error applying the booking rules for "
                  + date
                  + ". Please make the rule bookings for this date manually instead. The error message was: "
                  + exception.getMessage(), "Sqawsh booking rules failed to apply");
      // Rethrow
      throw exception;
    }

    logger.log("About to purge expired rules and exclusions.");
    purgeExpiredRulesAndRuleExclusions();
    logger.log("Purged expired rules and exclusions.");

    return ruleBookings;
  }

  private DayOfWeek dayOfWeekFromDate(String date) throws ParseException {
    DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    formatter.setTimeZone(TimeZone.getTimeZone("Europe/London"));
    return formatter.parse(date).toInstant()
        .atZone(TimeZone.getTimeZone("Europe/London").toZoneId()).toLocalDate().getDayOfWeek();
  }

  private Boolean doesRuleClash(BookingRule newBookingRule, Set<BookingRule> existingBookingRules)
      throws ParseException {

    logger.log("Determining if new rule clashes with existing rule.");

    // Get all existing rules that apply to the same day of the week...
    DayOfWeek newDay = dayOfWeekFromDate(newBookingRule.getBooking().getDate());
    logger.log("New rule applies to day of the week: " + newDay);
    Set<BookingRule> sameDayRules = new HashSet<>();
    for (BookingRule bookingRule : existingBookingRules) {
      if (dayOfWeekFromDate(bookingRule.getBooking().getDate()).equals(newDay)) {
        sameDayRules.add(bookingRule);
      }
    }
    logger.log(sameDayRules.size() + " existing rules also apply to this day of the week");

    // ..and all of those that overlap the same courttimeblock
    Set<BookingRule> sameDayOverlappingRules = new HashSet<>();
    // Get court/time pairs for the new rule
    Set<ImmutablePair<Integer, Integer>> newBookedCourts = new HashSet<>();
    addBookingRuleToSet(newBookingRule, newBookedCourts);

    for (BookingRule bookingRule : sameDayRules) {
      // Get court/times booked by existing rules for the same day as the new
      // rule
      Set<ImmutablePair<Integer, Integer>> bookedCourts = new HashSet<>();
      addBookingRuleToSet(bookingRule, bookedCourts);
      if (Sets.intersection(newBookedCourts, bookedCourts).size() > 0) {
        sameDayOverlappingRules.add(bookingRule);
      }
    }
    logger
        .log(sameDayOverlappingRules.size()
            + " existing rules also apply to this day of the week and overlap the same Court/Time block");

    // Remove any non-recurring rules with a date before the new rule starts -
    // they cannot possibly clash.
    Set<BookingRule> activeSameDayOverlappingRules = new HashSet<>(sameDayOverlappingRules);
    Date newDate = (new SimpleDateFormat("yyyy-MM-dd"))
        .parse(newBookingRule.getBooking().getDate());
    for (BookingRule bookingRule : sameDayOverlappingRules) {
      if ((!bookingRule.getIsRecurring())
          && ((new SimpleDateFormat("yyyy-MM-dd")).parse(bookingRule.getBooking().getDate())
              .before(newDate))) {
        logger.log("Removing same-day overlapping rule as it does not clash: " + bookingRule);
        activeSameDayOverlappingRules.remove(bookingRule);
      }
    }

    // If the new rule is recurring and any non-recurring existing rules are
    // left, then we have a clash, unless it has a relevant exclusion. N.B. We
    // need to allow exclusions at rule creation time to support backup/restore
    // functionality.
    if (newBookingRule.getIsRecurring()) {
      if (activeSameDayOverlappingRules.stream().anyMatch(
          rule -> (!rule.getIsRecurring() && !ArrayUtils.contains(
              newBookingRule.getDatesToExclude(), rule.getBooking().getDate())))) {
        // New rule have a relevant exclusion, so we have a clash.
        logger
            .log("Clash as there's an existing clashing non-recurring rule and new rule is recurring without a relevant exclusion");
        return true;
      }
    } else {
      // If the new rule is non-recurring and any non-recurring rules remain,
      // then we have a clash if both have the same date.
      if (activeSameDayOverlappingRules.stream().anyMatch(
          rule -> (rule.getBooking().getDate().equals(newBookingRule.getBooking().getDate()))
              && (!rule.getIsRecurring()))) {
        logger
            .log("Clash as new rule is non-recurring and there's an existing clashing non-recurring rule");
        return true;
      }
    }

    // Finished with non-recurring existing rules - so ditch them.
    activeSameDayOverlappingRules.removeIf(rule -> !rule.getIsRecurring());

    // If new rule is non-recurring, we have a clash if any existing recurring
    // rules start before it, unless they have a relevant exclusion.
    if (!newBookingRule.getIsRecurring()) {
      Date newBookingRuleDate = (new SimpleDateFormat("yyyy-MM-dd")).parse(newBookingRule
          .getBooking().getDate());
      for (BookingRule bookingRule : activeSameDayOverlappingRules) {
        if ((new SimpleDateFormat("yyyy-MM-dd")).parse(bookingRule.getBooking().getDate()).before(
            newBookingRuleDate)) {
          // Does this rule have a relevant exclusion?
          if (ArrayUtils.contains(bookingRule.getDatesToExclude(), newBookingRule.getBooking()
              .getDate())) {
            logger.log("Recurring rule does not clash as it has a matching rule exclusion: "
                + bookingRule.toString());
            continue;
          }
          // We have a clash!
          logger
              .log("Clash as new rule is non-recurring and there's an existing clashing recurring rule");
          return true;
        }
      }
    } else {
      if (activeSameDayOverlappingRules.size() > 0) {
        // Always clash if both new and existing rules are recurring.
        logger
            .log("Clash as new rule is recurring and there's an existing clashing recurring rule");
        return true;
      }
    }

    logger.log("No clash!");
    return false;
  }

  private void addBookingRuleToSet(BookingRule bookingRule,
      Set<ImmutablePair<Integer, Integer>> bookedCourts) {
    for (int court = bookingRule.getBooking().getCourt(); court < bookingRule.getBooking()
        .getCourt() + bookingRule.getBooking().getCourtSpan(); court++) {
      for (int slot = bookingRule.getBooking().getSlot(); slot < bookingRule.getBooking().getSlot()
          + bookingRule.getBooking().getSlotSpan(); slot++) {
        bookedCourts.add(new ImmutablePair<>(court, slot));
      }
    }
  }

  private ImmutablePair<Optional<Integer>, Set<BookingRule>> getVersionedBookingRules()
      throws Exception {
    logger.log("About to get all versioned booking rules from simpledb");

    // Get existing booking rules (and version number), via consistent read:
    ImmutablePair<Optional<Integer>, Set<Attribute>> versionedAttributes = optimisticPersister
        .get(ruleItemName);

    // Convert attributes to BookingRules:
    Set<BookingRule> existingBookingRules = new HashSet<>();
    versionedAttributes.right.stream().forEach(attribute -> {
      existingBookingRules.add(getBookingRuleFromAttribute(attribute));
    });

    return new ImmutablePair<>(versionedAttributes.left, existingBookingRules);
  }

  private BookingRule getBookingRuleFromAttribute(Attribute attribute) {
    // N.B. BookingRule attributes have names like
    // <date>-<court>-<courtSpan>-<slot>-<slotSpan>-<isRecurring>-<name>
    // e.g. 2016-07-04-4-2-7-3-true-TeamTraining books courts 4-5 for time slots
    // 7-9 every Monday, starting on Monday 4th July 2016, for TeamTraining.
    // The value is a comma-separated array of dates to exclude.
    String[] parts = attribute.getName().split("-");
    String date = parts[0] + "-" + parts[1] + "-" + parts[2];
    Integer court = Integer.parseInt(parts[3]);
    Integer courtSpan = Integer.parseInt(parts[4]);
    Integer slot = Integer.parseInt(parts[5]);
    Integer slotSpan = Integer.parseInt(parts[6]);
    Boolean isRecurring = Boolean.valueOf(parts[7]);
    // All remaining parts will be the booking name - possibly hyphenated
    String name = "";
    for (int partNum = 8; partNum < parts.length; partNum++) {
      name += parts[partNum];
      if (partNum < (parts.length - 1)) {
        name += "-";
      }
    }
    Booking rulesBooking = new Booking(court, courtSpan, slot, slotSpan, name);
    rulesBooking.setDate(date);
    String[] datesToExclude = new String[0];
    if (attribute.getValue().length() > 0) {
      // Use split only if we have some dates to exclude - otherwise we would
      // get an (invalid) length-1 array containing a single empty string.
      datesToExclude = attribute.getValue().split(",");
    }
    return new BookingRule(rulesBooking, isRecurring, datesToExclude);
  }

  private String getAttributeNameFromBookingRule(BookingRule bookingRule) {
    return bookingRule.getBooking().getDate().toString() + "-"
        + bookingRule.getBooking().getCourt().toString() + "-"
        + bookingRule.getBooking().getCourtSpan().toString() + "-"
        + bookingRule.getBooking().getSlot().toString() + "-"
        + bookingRule.getBooking().getSlotSpan().toString() + "-"
        + bookingRule.getIsRecurring().toString() + "-" + bookingRule.getBooking().getName();
  }

  private void purgeExpiredRulesAndRuleExclusions() throws Exception {
    ImmutablePair<Optional<Integer>, Set<BookingRule>> versionedBookingRules = getVersionedBookingRules();
    Set<BookingRule> existingBookingRules = versionedBookingRules.right;

    Optional<Integer> versionNumber = versionedBookingRules.left;
    String todayFormatted = getCurrentLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    Date today = (new SimpleDateFormat("yyyy-MM-dd").parse(todayFormatted));
    logger.log("Purging all rules and exclusions that expired before: " + todayFormatted);
    for (BookingRule bookingRule : existingBookingRules) {
      if (!bookingRule.getIsRecurring()
          && (new SimpleDateFormat("yyyy-MM-dd").parse(bookingRule.getBooking().getDate()))
              .before(today)) {
        logger.log("Deleting non-recurring booking rule as it has expired: "
            + bookingRule.toString());
        try {
          deleteRule(bookingRule);
          logger.log("Deleted expired booking rule");
        } catch (Exception exception) {
          // Don't want to abort here if we fail to remove a rule - after all
          // we'll get another shot at it in 24 hours time.
          logger
              .log("Exception caught deleting expired booking rule - swallowing and carrying on...");
        }
        continue;
      }

      // Purge any expired exclusions from this rule
      if (!bookingRule.getIsRecurring()) {
        // Non-recurring rules have no exclusions
        continue;
      }
      logger.log("Purging any expired exclusions from recurring rule: " + bookingRule);
      Set<String> datesToExclude = Sets.newHashSet(bookingRule.getDatesToExclude());
      Set<String> newDatesToExclude = new HashSet<>();
      for (String date : datesToExclude) {
        if ((new SimpleDateFormat("yyyy-MM-dd").parse(date)).after(new SimpleDateFormat(
            "yyyy-MM-dd").parse(getCurrentLocalDate().minusDays(1).format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"))))) {
          // Keep the exclusion if it applies to a date after yesterday.
          newDatesToExclude.add(date);
        } else {
          logger.log("Expiring exclusion for: " + date);
        }
        if (datesToExclude.size() > newDatesToExclude.size()) {
          // Update the database as some exclusions have been purged
          logger.log("Proceeding to update the rule after purging expired exclusion(s)");
          String attributeName = getAttributeNameFromBookingRule(bookingRule);
          String attributeValue = StringUtils.join(newDatesToExclude, ",");
          logger.log("ItemName: " + ruleItemName);
          logger.log("AttributeName: " + attributeName);
          logger.log("AttributeValue: " + attributeValue);
          ReplaceableAttribute bookingRuleAttribute = new ReplaceableAttribute();
          bookingRuleAttribute.setName(attributeName);
          bookingRuleAttribute.setValue(attributeValue);
          bookingRuleAttribute.setReplace(true);
          try {
            versionNumber = Optional.of(optimisticPersister.put(ruleItemName, versionNumber,
                bookingRuleAttribute));
            logger.log("Updated rule to purge expired exclusion(s)");
          } catch (Exception exception) {
            // Don't want to abort here if we fail to remove an exclusion -
            // after all we'll get another shot at it in 24 hours time.
            logger
                .log("Exception caught deleting expired booking exclusion - swallowing and carrying on...");
          }
        }
      }
    }
    logger.log("Purged all expired rules and exclusions");
  }

  /**
   * Returns an SNS client.
   *
   * <p>This method is provided so unit tests can mock out SNS.
   */
  protected AmazonSNS getSNSClient() {

    // Use a getter here so unit tests can substitute a mock client
    AmazonSNS client = new AmazonSNSClient();
    client.setRegion(region);
    return client;
  }

  /**
   * Returns an optimistic persister.
   * @throws IOException 
   */
  protected IOptimisticPersister getOptimisticPersister() throws IOException {
    // Use a getter here so unit tests can substitute a mock persister
    return new OptimisticPersister();
  }

  /**
   * Returns the current London local date.
   */
  protected LocalDate getCurrentLocalDate() {
    // Use a getter here so unit tests can substitute a different date.

    // This gets the correct local date no matter what the user's device
    // system time may say it is, and no matter where in AWS we run.
    return BookingsUtilities.getCurrentLocalDate();
  }

  /**
   * Returns a named environment variable.
   * @throws Exception 
   */
  protected String getEnvironmentVariable(String variableName) throws Exception {
    // Use a getter here so unit tests can substitute a mock value.
    // We get the value from an environment variable so that CloudFormation can
    // set the actual value when the stack is created.

    String environmentVariable = System.getenv(variableName);
    if (environmentVariable == null) {
      logger.log("Environment variable: " + variableName + " is not defined, so throwing.");
      throw new Exception("Environment variable: " + variableName + " should be defined.");
    }
    return environmentVariable;
  }
}