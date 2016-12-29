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

import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.sns.AmazonSNS;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tests the {@link RuleManager}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class RuleManagerTest {
  // Variables for setting up subclass of class under test
  LocalDate fakeCurrentSaturdayDate;
  String fakeCurrentSaturdayDateString;
  squash.booking.lambdas.core.RuleManagerTest.TestRuleManager ruleManager;
  private String adminSnsTopicArn;

  // Mocks
  Mockery mockery = new Mockery();
  LambdaLogger mockLogger;
  IBookingManager mockBookingManager;
  IOptimisticPersister mockOptimisticPersister;
  AmazonSNS mockSNSClient;

  // Create some example booking rules to test with
  BookingRule existingThursdayNonRecurringRule;
  BookingRule existingFridayRecurringRuleWithoutExclusions;
  BookingRule existingSaturdayRecurringRuleWithExclusion;
  BookingRule newNonRecurringRule;

  // Create Booking to use for the existing rules
  String ruleBookingDate;
  String ruleExclusionDate;
  Booking ruleBooking;

  List<BookingRule> existingBookingRules;
  List<Booking> expectedBookingRules;

  String ruleItemName;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() {

    // Set up the rules' booking
    ruleBookingDate = "2016-07-21"; // Thursday
    ruleBooking = new Booking(1, 2, 3, 2, "J.Power/A.Shabana");
    ruleBooking.setDate(ruleBookingDate);

    // Set up the existing test booking rules
    existingThursdayNonRecurringRule = new BookingRule(ruleBooking, false, new String[0]);
    // Tweak day-of-week to avoid clash
    String newDate = LocalDate.parse(ruleBookingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    ruleBooking.setDate(newDate); // Friday;
    existingFridayRecurringRuleWithoutExclusions = new BookingRule(ruleBooking, true, new String[0]);
    // Tweak day-of-week again to avoid clash
    newDate = LocalDate.parse(ruleBookingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    ruleBooking.setDate(newDate); // Saturday
    // Create an exclusion date
    ruleExclusionDate = "2016-09-17"; // Saturday
    existingSaturdayRecurringRuleWithExclusion = new BookingRule(ruleBooking, true,
        new String[] { ruleExclusionDate });
    existingBookingRules = new ArrayList<>();
    existingBookingRules.add(existingThursdayNonRecurringRule);
    existingBookingRules.add(existingFridayRecurringRuleWithoutExclusions);
    existingBookingRules.add(existingSaturdayRecurringRuleWithExclusion);

    // Set up mock logger
    mockLogger = mockery.mock(LambdaLogger.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockLogger);
      }
    });
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockOptimisticPersister = mockery.mock(IOptimisticPersister.class);

    // Set up the rule manager
    fakeCurrentSaturdayDate = LocalDate.of(2015, 12, 24);
    fakeCurrentSaturdayDateString = fakeCurrentSaturdayDate.format(DateTimeFormatter
        .ofPattern("yyyy-MM-dd"));
    ruleManager = new squash.booking.lambdas.core.RuleManagerTest.TestRuleManager();
    ruleManager.setOptimisticPersister(mockOptimisticPersister);
    ruleManager.setCurrentLocalDate(fakeCurrentSaturdayDate);
    adminSnsTopicArn = "adminSnsTopicArn";
    ruleManager.setAdminSnsTopicArn(adminSnsTopicArn);

    ruleItemName = "BookingRulesAndExclusions";
  }

  private void initialiseRuleManager() throws Exception {
    // Call this to initialise the rule manager in tests where this
    // initialisation is not the subject of the test.

    // Allow the call when the rule manager is initialised.
    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).initialise(with.intIs(anything()), with(anything()));
      }
    });
    ruleManager.initialise(mockBookingManager, mockLogger);
  }

  private void expectOptimisticPersisterToReturnVersionedAttributes(int expectedVersion)
      throws Exception {
    expectOptimisticPersisterToReturnVersionedAttributes(expectedVersion, existingBookingRules, 1);
  }

  private void expectOptimisticPersisterToReturnVersionedAttributes(int expectedVersion,
      int numCalls) throws Exception {
    expectOptimisticPersisterToReturnVersionedAttributes(expectedVersion, existingBookingRules,
        numCalls);
  }

  private void expectOptimisticPersisterToReturnVersionedAttributes(int expectedVersion,
      List<BookingRule> bookingRules) throws Exception {
    expectOptimisticPersisterToReturnVersionedAttributes(expectedVersion, bookingRules, 1);
  }

  private void expectOptimisticPersisterToReturnVersionedAttributes(int expectedVersion,
      List<BookingRule> bookingRules, int numCalls) throws Exception {

    // Set up attributes to be returned from the database's booking rule item
    Set<Attribute> attributes = new HashSet<>();
    for (BookingRule bookingRule : bookingRules) {
      Attribute attribute = new Attribute();
      attribute.setName(getAttributeNameFromBookingRule(bookingRule));
      String[] datesToExclude = bookingRule.getDatesToExclude();
      attribute.setValue(StringUtils.join(datesToExclude, ","));
      attributes.add(attribute);
    }
    mockery.checking(new Expectations() {
      {
        exactly(numCalls).of(mockOptimisticPersister).get(with(equal(ruleItemName)));
        will(returnValue(new ImmutablePair<>(Optional.of(expectedVersion), attributes)));
      }
    });
  }

  private void expectToDeleteRulesViaOptimisticPersister(List<BookingRule> rulesToDelete)
      throws Exception {

    // Set up attributes to be deleted from the database's booking rule item
    for (BookingRule ruleToDelete : rulesToDelete) {
      Attribute attribute = new Attribute();
      attribute.setName(getAttributeNameFromBookingRule(ruleToDelete));
      String[] datesToExclude = ruleToDelete.getDatesToExclude();
      attribute.setValue(StringUtils.join(datesToExclude, ","));

      mockery.checking(new Expectations() {
        {
          oneOf(mockOptimisticPersister).delete(with(equal(ruleItemName)), with(equal(attribute)));
        }
      });
    }
  }

  private void expectToPutRuleToOptimisticPersister(int expectedVersion, BookingRule ruleToPut)
      throws Exception {

    // Set up attributes to be returned from the database's booking rule item
    ReplaceableAttribute attribute = new ReplaceableAttribute();
    attribute.setName(getAttributeNameFromBookingRule(ruleToPut));
    String[] datesToExclude = ruleToPut.getDatesToExclude();
    attribute.setValue(StringUtils.join(datesToExclude, ","));
    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)),
            with(Optional.of(expectedVersion)), with(equal(attribute)));
      }
    });
  }

  private void expectToAddOrDeleteRuleExclusionViaOptimisticPersister(int expectedVersion,
      String dateToExclude, Boolean doAdd, BookingRule ruleToAddExclusionTo) throws Exception {

    // Set up attribute to be put to the database's booking rule item
    ReplaceableAttribute replaceableAttribute = new ReplaceableAttribute();
    replaceableAttribute.setName(getAttributeNameFromBookingRule(ruleToAddExclusionTo));
    List<String> datesToExclude = new ArrayList<>();
    datesToExclude.addAll(Arrays.asList(ruleToAddExclusionTo.getDatesToExclude()));
    if (doAdd) {
      datesToExclude.add(dateToExclude);
    } else {
      datesToExclude.remove(dateToExclude);
    }
    if (datesToExclude.size() > 0) {
      replaceableAttribute.setValue(StringUtils.join(datesToExclude.toArray(), ","));
    } else {
      replaceableAttribute.setValue("");
    }
    replaceableAttribute.setReplace(true);

    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)),
            with(Optional.of(expectedVersion)), with(equal(replaceableAttribute)));
      }
    });
  }

  private void expectBookingManagerCall(Booking bookingToCreate) throws Exception {
    mockery.checking(new Expectations() {
      {
        oneOf(mockBookingManager).createBooking(with(equal(bookingToCreate)));
      }
    });
  }

  private void expectPurgeExpiredRulesAndRuleExclusions(int expectedVersion,
      List<BookingRule> existingBookingRules) throws Exception {
    expectPurgeExpiredRulesAndRuleExclusions(expectedVersion, existingBookingRules,
        Optional.empty(), Optional.empty());
  }

  private void expectPurgeExpiredRulesAndRuleExclusions(int expectedVersion,
      List<BookingRule> existingBookingRules, Optional<BookingRule> ruleToDelete,
      Optional<ImmutablePair<BookingRule, String>> ruleExclusionToDelete) throws Exception {
    expectOptimisticPersisterToReturnVersionedAttributes(expectedVersion, existingBookingRules);
    if (ruleToDelete.isPresent()) {
      List<BookingRule> rulesToDelete = new ArrayList<>();
      rulesToDelete.add(ruleToDelete.get());
      expectToDeleteRulesViaOptimisticPersister(rulesToDelete);
    }
    if (ruleExclusionToDelete.isPresent()) {
      expectToAddOrDeleteRuleExclusionViaOptimisticPersister(expectedVersion,
          ruleExclusionToDelete.get().right, false, ruleExclusionToDelete.get().left);
    }
  }

  private String getAttributeNameFromBookingRule(BookingRule bookingRule) {
    return bookingRule.getBooking().getDate().toString() + "-"
        + bookingRule.getBooking().getCourt().toString() + "-"
        + bookingRule.getBooking().getCourtSpan().toString() + "-"
        + bookingRule.getBooking().getSlot().toString() + "-"
        + bookingRule.getBooking().getSlotSpan().toString() + "-"
        + bookingRule.getIsRecurring().toString() + "-" + bookingRule.getBooking().getName();
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test rule manager with some overrides to facilitate testing
  public class TestRuleManager extends RuleManager {
    private AmazonSNS snsClient;
    private LocalDate currentLocalDate;
    private String adminSnsTopicArn;

    public void setOptimisticPersister(IOptimisticPersister optimisticPersister) {
      this.optimisticPersister = optimisticPersister;
    }

    @Override
    public IOptimisticPersister getOptimisticPersister() {
      return optimisticPersister;
    }

    public void setSNSClient(AmazonSNS snsClient) {
      this.snsClient = snsClient;
    }

    @Override
    public AmazonSNS getSNSClient() {
      return snsClient;
    }

    public void setCurrentLocalDate(LocalDate localDate) {
      currentLocalDate = localDate;
    }

    @Override
    public LocalDate getCurrentLocalDate() {
      return currentLocalDate;
    }

    public void setMaxNumberOfDatesToExclude(int maxNumberOfDatesToExclude) {
      this.maxNumberOfDatesToExclude = maxNumberOfDatesToExclude;
    }

    public void setAdminSnsTopicArn(String adminSnsTopicArn) {
      this.adminSnsTopicArn = adminSnsTopicArn;
    }

    @Override
    public String getEnvironmentVariable(String variableName) {
      if (variableName.equals("AdminSNSTopicArn")) {
        return adminSnsTopicArn;
      }
      if (variableName.equals("AWS_REGION")) {
        return "eu-west-1";
      }
      return null;
    }
  }

  private Set<BookingRule> doTestCreateRuleClashesOrNotWithExistingRule(BookingRule ruleToCreate,
      Boolean expectThrow) throws Exception {

    initialiseRuleManager();

    // createRule will first query persister for its existing attributes...
    int versionToUse = 1; // Arbitrary
    expectOptimisticPersisterToReturnVersionedAttributes(versionToUse);

    // ...and then write the attribute for the new rule (unless we're expecting
    // to throw before we put the new rule - e.g. bc of a clash).
    if (!expectThrow) {
      mockery.checking(new Expectations() {
        {
          expectToPutRuleToOptimisticPersister(versionToUse, ruleToCreate);
        }
      });
    }

    // ACT
    return ruleManager.createRule(ruleToCreate);
  }

  @Test
  public void testInitialiseThrowsWhenRuleManagerAlreadyInitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has already been initialised");

    initialiseRuleManager();

    // ACT
    // Initialise a second time - which should throw
    ruleManager.initialise(mockBookingManager, mockLogger);
  }

  @Test
  public void testCreateRuleThrowsWhenTheOptimisticPersisterThrows() throws Exception {
    // N.B. This applies except when the optimistic persister throws a
    // conditional check failed exclusion, which is covered by other tests.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingRule = new BookingRule(existingThursdayNonRecurringRule);
    // Change day-of-week so it no longer clashes
    String existingDate = nonClashingRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingRule.getBooking().setDate(newDate); // Wednesday
    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingRule, true);
  }

  @Test
  public void testCreateRuleThrowsIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionThreeTimesRunning()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exclusion
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if all
    // three tries fail then the rule manager will give up and throw.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Database put failed - conditional check failed";
    thrown.expectMessage(message);
    int versionToUse = 1; // Arbitrary
    expectOptimisticPersisterToReturnVersionedAttributes(versionToUse, 3);

    initialiseRuleManager();

    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingRule = new BookingRule(existingThursdayNonRecurringRule);
    // Change day-of-week so it no longer clashes
    String existingDate = nonClashingRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingRule.getBooking().setDate(newDate); // Wednesday
    mockery.checking(new Expectations() {
      {
        // All three tries throw
        exactly(3).of(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // ACT
    // This should throw - albeit after three tries internally
    ruleManager.createRule(nonClashingRule);
  }

  @Test
  public void testCreateRuleDoesNotThrowIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionOnlyTwice()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exclusion
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if we
    // throw twice but the third try succeeds, then the rule manager does not
    // throw.

    // ARRANGE
    int versionToUse = 1; // Arbitrary
    expectOptimisticPersisterToReturnVersionedAttributes(versionToUse, 3);
    initialiseRuleManager();

    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingRule = new BookingRule(existingThursdayNonRecurringRule);
    // Change day-of-week so it no longer clashes
    String existingDate = nonClashingRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingRule.getBooking().setDate(newDate); // Wednesday

    final Sequence retrySequence = mockery.sequence("retry");
    mockery.checking(new Expectations() {
      {
        // Two failures...
        exactly(2).of(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception("Database put failed - conditional check failed")));
        inSequence(retrySequence);
        // ... but third attempt succeeds
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(returnValue(2));
        inSequence(retrySequence);
      }
    });

    // ACT
    // This should _not_ throw - we are allowed three tries
    ruleManager.createRule(nonClashingRule);
  }

  @Test
  public void testCreateRuleThrowsWhenRuleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has not been initialised");

    // ACT
    // Do not initialise the rule manager first - so createRule should throw
    ruleManager.createRule(existingThursdayNonRecurringRule);
  }

  @Test
  public void testCreateNonrecurringRuleHappyPathCallsTheOptimisticPersisterCorrectly()
      throws Exception {
    // Happy path where createRule goes right through and creates the
    // non-recurring rule.

    // ARRANGE

    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingRule = new BookingRule(existingThursdayNonRecurringRule);
    // Change day-of-week so it no longer clashes
    String existingDate = nonClashingRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingRule.getBooking().setDate(newDate); // Wednesday

    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingRule, false);
  }

  @Test
  public void testCreateRecurringRuleHappyPathCallsTheOptimisticPersisterCorrectly()
      throws Exception {
    // Happy path where createRule goes right through and creates the recurring
    // rule.

    // ARRANGE

    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingRule = new BookingRule(existingFridayRecurringRuleWithoutExclusions);
    // Change day-of-week so it no longer clashes
    String existingDate = nonClashingRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingRule.getBooking().setDate(newDate); // Sunday

    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingRule, false);
  }

  @Test
  public void testCreateNonrecurringRuleHappyPathReturnsCorrectBookingRules() throws Exception {

    // ARRANGE

    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingRule = new BookingRule(existingThursdayNonRecurringRule);
    // Change day-of-week so it no longer clashes
    String existingDate = nonClashingRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingRule.getBooking().setDate(newDate); // Wednesday

    Set<BookingRule> returnedBookingRules = doTestCreateRuleClashesOrNotWithExistingRule(
        nonClashingRule, false);
    Set<BookingRule> expectedBookingRules = new HashSet<>();
    expectedBookingRules.addAll(existingBookingRules);
    expectedBookingRules.add(nonClashingRule);

    assertEquals("Unexpected booking rules returned by createRule", returnedBookingRules,
        expectedBookingRules);
  }

  // Next we have a longish set of tests checking the clash-detection logic:

  // Cases that clash (or not) with an existing non-recurring rule:
  @Test
  public void testCreateRuleThrowsWhenNewRuleClashesWithExistingRules_ExistingNonRecurringNewRecurringSameDayOfWeekOverlappingNoExclusion()
      throws Exception {
    // If the new rule is recurring and there are any overlapping, non-recurring
    // existing rules after the new rule starts, then we have a clash, unless
    // the new rule has a relevant exclusion.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule creation failed");

    // Set up a rule to create that clashes in required way with existing rules
    BookingRule clashingThursdayRule = new BookingRule(existingThursdayNonRecurringRule);
    clashingThursdayRule.setIsRecurring(true);

    // Move date to same day-of-week but to some date earlier than the existing
    // non-recurring rule.
    String existingDate = clashingThursdayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    clashingThursdayRule.getBooking().setDate(newDate);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(clashingThursdayRule, true);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingNonRecurringNewRecurringSameDayOfWeekOverlappingWithExclusion()
      throws Exception {
    // If the new rule is recurring and there are any overlapping, non-recurring
    // existing rules after the new rule starts, then we do not have a clash if
    // the new rule has a relevant exclusion. N.B. We allow exclusions at
    // creation time to support backup/restore.

    // ARRANGE

    // Set up a rule to create that clashes in required way with existing rules
    BookingRule nonClashingThursdayRule = new BookingRule(existingThursdayNonRecurringRule);
    nonClashingThursdayRule.setIsRecurring(true);

    // Move date to same day-of-week but to some date earlier than the existing
    // non-recurring rule.
    String existingDate = nonClashingThursdayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingThursdayRule.getBooking().setDate(newDate);

    // Add exclusion to new rule - so it becomes non-clashing
    nonClashingThursdayRule.setDatesToExclude(new String[] { existingThursdayNonRecurringRule
        .getBooking().getDate() });

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingThursdayRule, false);
  }

  @Test
  public void testCreateRuleThrowsWhenNewRuleClashesWithExistingRules_ExistingNonRecurringNewRecurringSameDayOfWeekPartiallyOverlappingNoExclusion()
      throws Exception {
    // If the new rule is recurring and there are any overlapping, non-recurring
    // existing rules after the new rule starts, then we have a clash. N.B. This
    // tests where the court/time blocks overlap only partially.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule creation failed");

    // Set up a rule to create that clashes in required way with existing rules
    BookingRule clashingThursdayRule = new BookingRule(existingThursdayNonRecurringRule);
    clashingThursdayRule.setIsRecurring(true);

    // Move date to same day-of-week but to some date earlier than the existing
    // non-recurring rule.
    String existingDate = clashingThursdayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    clashingThursdayRule.getBooking().setDate(newDate);

    // Tweak court so that overlap is partial only
    clashingThursdayRule.getBooking().setCourt(
        clashingThursdayRule.getBooking().getCourt()
            + clashingThursdayRule.getBooking().getCourtSpan() - 1);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(clashingThursdayRule, true);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingNonRecurringInPastNewRecurringSameDayOfWeekOverlappingNoExclusion()
      throws Exception {
    // This checks that a new recurring rule does not clash with an existing
    // non-recurring one if that existing rule has a date before the new
    // recurring rule starts.

    // ARRANGE
    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingThursdayRule = new BookingRule(existingThursdayNonRecurringRule);
    nonClashingThursdayRule.setIsRecurring(true);

    // Tweak date to be ahead of the existing non-recurring rule
    String existingDate = nonClashingThursdayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingThursdayRule.getBooking().setDate(newDate);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingThursdayRule, false);
  }

  @Test
  public void testCreateRuleThrowsWhenNewRuleClashesWithExistingRules_ExistingNonRecurringNewNonRecurringSameDateOverlapping()
      throws Exception {
    // If both the new rule and an existing rule are non-recurring for the same
    // date, and overlap, then we have a clash.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule creation failed");

    // Set up a rule to create that clashes in required way with existing rules
    BookingRule clashingThursdayRule = new BookingRule(existingThursdayNonRecurringRule);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(clashingThursdayRule, true);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingNonRecurringNewRecurringSameDayOfWeekNonOverlappingCourt()
      throws Exception {
    // Tests case where we would have a clash if the court blocks
    // overlapped - but they don't, so should not clash.

    // ARRANGE
    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingThursdayRule = new BookingRule(existingThursdayNonRecurringRule);
    nonClashingThursdayRule.setIsRecurring(true);

    // Tweak court to avoid overlap
    nonClashingThursdayRule.getBooking().setCourt(
        nonClashingThursdayRule.getBooking().getCourt()
            + nonClashingThursdayRule.getBooking().getCourtSpan());

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingThursdayRule, false);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingNonRecurringNewRecurringSameDayOfWeekNonOverlappingTime()
      throws Exception {
    // Tests case where we would have a clash if the time blocks
    // overlapped - but they don't, so should not clash.

    // ARRANGE
    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingThursdayRule = new BookingRule(existingThursdayNonRecurringRule);
    nonClashingThursdayRule.setIsRecurring(true);

    // Tweak time slot to avoid overlap
    nonClashingThursdayRule.getBooking().setSlot(
        nonClashingThursdayRule.getBooking().getSlot()
            + nonClashingThursdayRule.getBooking().getSlotSpan());

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingThursdayRule, false);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingNonRecurringNewRecurringDifferentDayOfWeekOverlapping()
      throws Exception {
    // Tests case where we would have a clash if the days-of-the-week agreed -
    // but they don't, so should not clash.

    // ARRANGE
    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingWednesdayRule = new BookingRule(existingThursdayNonRecurringRule);
    nonClashingWednesdayRule.setIsRecurring(true);

    // Move date to a different day of the week
    String existingDate = nonClashingWednesdayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusDays(6).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingWednesdayRule.getBooking().setDate(newDate); // Wednesday

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingWednesdayRule, false);
  }

  // Cases that clash (or not) with an existing recurring rule:
  @Test
  public void testCreateRuleThrowsWhenNewRuleClashesWithExistingRules_ExistingRecurringNewRecurringSameDayOfWeekOverlapping()
      throws Exception {
    // Always clash if both new and existing rules are recurring and
    // overlapping.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule creation failed");

    // Set up a rule to create that clashes in required way with existing rules
    BookingRule clashingFridayRule = new BookingRule(existingFridayRecurringRuleWithoutExclusions);
    // Move date to same day-of-week but to some date later than the existing
    // non-recurring rule - should make no difference.
    String existingDate = clashingFridayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(100).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    clashingFridayRule.getBooking().setDate(newDate);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(clashingFridayRule, true);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingRecurringNewNonRecurringSameDayOfWeekOverlappingExclusion()
      throws Exception {
    // If existing rule is recurring and new rule is non-recurring for
    // overlapping court/time and same day-of-the-week for a date in the future
    // and there is a relevant exclusion, then we do not have a clash.

    // ARRANGE
    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingSaturdayRule = new BookingRule(
        existingSaturdayRecurringRuleWithExclusion);
    nonClashingSaturdayRule.setIsRecurring(false);
    nonClashingSaturdayRule.setDatesToExclude(new String[0]);

    // Move date to equal an exclusion on the existing recurring rule
    String exclusionDate = existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0];
    nonClashingSaturdayRule.getBooking().setDate(exclusionDate);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingSaturdayRule, false);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingRecurringNewNonRecurringSameDayOfWeekOverlappingExclusions()
      throws Exception {
    // If existing rule is recurring and new rule is non-recurring for
    // overlapping court/time and same day-of-the-week for a date in the future
    // and there is a relevant exclusion, then we do not have a clash. This
    // checks that when the relevant exclusion is not the first exclusion in the
    // exclusions array, it is still respected.

    // ARRANGE
    // Set up a rule to create that does not clash with existing rules
    BookingRule nonClashingSaturdayRule = new BookingRule(
        existingSaturdayRecurringRuleWithExclusion);
    nonClashingSaturdayRule.setIsRecurring(false);
    nonClashingSaturdayRule.setDatesToExclude(new String[0]);

    // Move date to equal the second exclusion on the existing recurring rule
    // Add second exclusion:
    String existingExclusion = existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0];
    String newExclusion = LocalDate
        .parse(existingExclusion, DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusWeeks(12)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    String[] newExcludeDates = new String[] {
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0], newExclusion };
    existingSaturdayRecurringRuleWithExclusion.setDatesToExclude(newExcludeDates);

    nonClashingSaturdayRule.getBooking().setDate(newExcludeDates[1]);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingSaturdayRule, false);
  }

  @Test
  public void testCreateRuleThrowsWhenNewRuleClashesWithExistingRules_ExistingRecurringNewNonRecurringSameDayOfWeekOverlappingNoExclusion()
      throws Exception {
    // If existing rule is recurring and new rule is non-recurring for
    // overlapping court/time and same day-of-the-week for a date in the future
    // and there is no relevant exclusion - we have a clash.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule creation failed");

    // Set up a rule to create that clashes in required way with existing rules
    BookingRule clashingFridayRule = new BookingRule(existingFridayRecurringRuleWithoutExclusions);
    clashingFridayRule.setIsRecurring(false);
    // Move date to same day-of-week but to some future date
    String existingDate = clashingFridayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(12).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    clashingFridayRule.getBooking().setDate(newDate);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(clashingFridayRule, true);
  }

  @Test
  public void testCreateRuleDoesNotThrowWhenNewRuleDoesNotClashWithExistingRules_ExistingRecurringNewNonRecurringBeforeStartSameDayOfWeekOverlappingNoExclusion()
      throws Exception {
    // If existing rule is recurring and new rule is non-recurring for
    // overlapping court/time and same day-of-the-week, but for a date both not
    // in the past and before the recurring rule starts, we do not have a clash.

    // ARRANGE

    // Set up a rule to create that avoids clash in required way with existing
    // rules
    BookingRule nonClashingFridayRule = new BookingRule(
        existingFridayRecurringRuleWithoutExclusions);
    nonClashingFridayRule.setIsRecurring(false);
    // Move date to one week earlier i.e. to before recurring rule starts
    String existingDate = nonClashingFridayRule.getBooking().getDate();
    String newDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    nonClashingFridayRule.getBooking().setDate(newDate);

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(nonClashingFridayRule, false);
  }

  @Test
  public void testCreateRuleThrowsWhenNewNonRecurringRuleIsInThePast() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule creation failed");

    // Set up a rule to create that clashes in required way with existing rules
    BookingRule pastRule = new BookingRule(existingThursdayNonRecurringRule);
    // Tweak so does not clash
    pastRule.getBooking().setCourt(
        pastRule.getBooking().getCourt() + pastRule.getBooking().getCourtSpan());
    pastRule.getBooking().setSlot(
        pastRule.getBooking().getSlot() + pastRule.getBooking().getSlotSpan());

    // Set current date to be ahead of this new rule
    ruleManager.setCurrentLocalDate(LocalDate.parse(pastRule.getBooking().getDate(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusDays(1));

    // ACT
    doTestCreateRuleClashesOrNotWithExistingRule(pastRule, true);
  }

  @Test
  public void testGetRulesThrowsWhenRuleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has not been initialised");

    // ACT
    // Do not initialise the rule manager first - so getRules should throw
    ruleManager.getRules();
  }

  @Test
  public void testGetRulesReturnsCorrectRules() throws Exception {

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(1);

    // ACT
    List<BookingRule> returnedBookingRules = ruleManager.getRules();

    // Assert lists equal - ignoring order
    assertTrue(
        "Unexpected booking rules returned by getRules",
        returnedBookingRules.containsAll(existingBookingRules)
            && existingBookingRules.containsAll(returnedBookingRules)
            && (new HashSet<>(returnedBookingRules).size() == returnedBookingRules.size()));
  }

  @Test
  public void testGetRulesReturnsCorrectRules_HyphenatedNames() throws Exception {
    // This tests hyphenated players' names are handled correctly.

    // ARRANGE
    initialiseRuleManager();

    // Ensure an existing rule has hyphenated palyers names
    List<BookingRule> existingRules = new ArrayList<>();
    existingRules.addAll(existingBookingRules);
    // Create a booking rule without exclusions
    BookingRule hyphenatedRule = new BookingRule(existingSaturdayRecurringRuleWithExclusion);
    hyphenatedRule.getBooking().setName("I.AmHyphen-ated/S.O-am-I");
    existingRules.remove(existingSaturdayRecurringRuleWithExclusion);
    existingRules.add(hyphenatedRule);
    expectOptimisticPersisterToReturnVersionedAttributes(2, existingRules);

    // ACT
    List<BookingRule> returnedBookingRules = ruleManager.getRules();

    // Assert lists equal - ignoring order
    assertTrue(
        "Unexpected booking rules returned by getRules",
        returnedBookingRules.containsAll(existingRules)
            && existingRules.containsAll(returnedBookingRules)
            && (new HashSet<>(returnedBookingRules).size() == returnedBookingRules.size()));
  }

  @Test
  public void testGetRulesCallsTheOptimisticPersisterCorrectly() throws Exception {

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(1);

    // ACT
    ruleManager.getRules();
  }

  @Test
  public void testDeleteRuleThrowsWhenRuleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has not been initialised");

    // ACT
    // Do not initialise the rule manager first - so deleteRule should throw
    ruleManager.deleteRule(existingFridayRecurringRuleWithoutExclusions);
  }

  @Test
  public void testDeleteRuleCallsTheOptimisticPersisterCorrectly() throws Exception {

    // ARRANGE
    initialiseRuleManager();
    List<BookingRule> rulesToDelete = new ArrayList<>();
    rulesToDelete.add(existingFridayRecurringRuleWithoutExclusions);
    expectToDeleteRulesViaOptimisticPersister(rulesToDelete);

    // ACT
    ruleManager.deleteRule(existingFridayRecurringRuleWithoutExclusions);
  }

  @Test
  public void testDeleteAllBookingRulesThrowsWhenRuleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has not been initialised");

    // ACT
    // Do not initialise the rule manager first - so deleteAllBookingRules
    // should throw
    ruleManager.deleteAllBookingRules();
  }

  @Test
  public void testDeleteRuleThrowsWhenTheOptimisticPersisterThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    initialiseRuleManager();

    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).delete(with(equal(ruleItemName)), with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // ACT
    // This should throw
    ruleManager.deleteRule(existingThursdayNonRecurringRule);
  }

  @Test
  public void testDeleteAllBookingRulesThrowsIfTheRuleManagerThrowsTooManyRequestsExceptionsThreeTimesRunning()
      throws Exception {
    // The rule manager can throw a TooManyRequests exception
    // if there are many booking rules being deleted. If this happens we should
    // pause for a short time and then continue deleting. We allow up to three
    // attempts to delete each booking rule before giving up. This tests that
    // if all three tries fail then the rule manager will give up and throw.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Boom!";
    thrown.expectMessage(message);

    // ACT
    initialiseRuleManager();

    // Tweak rules to have just one rule - that's all we need here
    existingBookingRules = new ArrayList<>();
    existingBookingRules.add(existingThursdayNonRecurringRule);
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // Set up mock optimistic persister to throw too many requests errors
    // Configure the TooManyRequests error (429)
    AmazonServiceException ase = new AmazonServiceException(message);
    ase.setErrorCode("429");
    mockery.checking(new Expectations() {
      {
        // All three tries throw
        exactly(3).of(mockOptimisticPersister).delete(with(equal(ruleItemName)), with(anything()));
        will(throwException(ase));
      }
    });
    ruleManager.setOptimisticPersister(mockOptimisticPersister);

    // ACT
    // This should throw - albeit after three tries
    ruleManager.deleteAllBookingRules();
  }

  @Test
  public void testDeleteAllBookingRulesDoesNotThrowIfTheRuleManagerThrowsTooManyRequestsExceptionsOnlyTwice()
      throws Exception {
    // The rule manager can throw a TooManyRequests exception
    // if there are many booking rules being deleted. If this happens we should
    // pause for a short time and then continue deleting. We allow up to three
    // attempts to delete each booking rule before giving up. This tests that
    // if we throw twice but the third try succeeds, then the rule manager
    // does not throw.

    // ARRANGE
    String message = "Boom!";

    // ACT
    initialiseRuleManager();

    // Tweak rules to have just one rule - that's all we need here
    existingBookingRules = new ArrayList<>();
    existingBookingRules.add(existingThursdayNonRecurringRule);
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // Set up mock optimistic persister to throw too many requests errors
    // Configure the TooManyRequests error (429)
    AmazonServiceException ase = new AmazonServiceException(message);
    ase.setErrorCode("429");
    mockery.checking(new Expectations() {
      {
        // Throw twice...
        exactly(2).of(mockOptimisticPersister).delete(with(equal(ruleItemName)), with(anything()));
        will(throwException(ase));
        // ...but succeed on the third try
        oneOf(mockOptimisticPersister).delete(with(equal(ruleItemName)), with(anything()));
      }
    });
    ruleManager.setOptimisticPersister(mockOptimisticPersister);

    // ACT
    // This should _not_ throw - we are allowed three tries
    ruleManager.deleteAllBookingRules();
  }

  @Test
  public void testDeleteAllBookingRulesCallsTheOptimisticPersisterCorrectly() throws Exception {

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary
    expectToDeleteRulesViaOptimisticPersister(existingBookingRules);

    // ACT
    ruleManager.deleteAllBookingRules();
  }

  @Test
  public void testDeleteAllBookingRulesThrowsWhenTheOptimisticPersisterThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).delete(with(equal(ruleItemName)), with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // ACT
    // This should throw
    ruleManager.deleteAllBookingRules();
  }

  @Test
  public void testAddRuleExclusionHappyPathCallsTheOptimisticPersisterCorrectly() throws Exception {
    // Happy path where addRuleExclusion goes right through and creates the
    // exclusion.

    // ARRANGE
    initialiseRuleManager();
    int versionToUse = 22; // Arbitrary
    expectOptimisticPersisterToReturnVersionedAttributes(versionToUse);

    // Create an arbitrary, valid exclusion date to add
    String saturdayExclusionDate = LocalDate
        .parse(existingSaturdayRecurringRuleWithExclusion.getBooking().getDate(),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusWeeks(12)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    expectToAddOrDeleteRuleExclusionViaOptimisticPersister(versionToUse, saturdayExclusionDate,
        true, existingSaturdayRecurringRuleWithExclusion);

    // ACT
    Optional<BookingRule> updatedRule = ruleManager.addRuleExclusion(saturdayExclusionDate,
        existingSaturdayRecurringRuleWithExclusion);

    // ASSERT
    String[] newExcludeDates = new String[] {
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0], saturdayExclusionDate };
    existingSaturdayRecurringRuleWithExclusion.setDatesToExclude(newExcludeDates);
    assertTrue("The updated rule should be returned", updatedRule.isPresent());
    assertTrue("The updated rule should be correct",
        updatedRule.get().equals(existingSaturdayRecurringRuleWithExclusion));
  }

  @Test
  public void testAddRuleExclusionThrowsWhenRuleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has not been initialised");

    // ACT
    // Do not initialise the rule manager first - so addRuleExclusion should
    // throw
    ruleManager.addRuleExclusion("2016-08-26", existingFridayRecurringRuleWithoutExclusions);
  }

  @Test
  public void testAddRuleExclusionThrowsWhenTheOptimisticPersisterThrows() throws Exception {
    // N.B. This applies except when the optimistic persister throws a
    // conditional check failed exclusion, which is covered by other tests.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    String existingDate = existingFridayRecurringRuleWithoutExclusions.getBooking().getDate();
    String exclusionDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should throw
    ruleManager.addRuleExclusion(exclusionDate, existingFridayRecurringRuleWithoutExclusions);
  }

  @Test
  public void testAddRuleExclusionThrowsIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionThreeTimesRunning()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exclusion
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if all
    // three tries fail then the rule manager will give up and throw.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Database put failed - conditional check failed";
    thrown.expectMessage(message);

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2, 3); // 2 arbitrary

    mockery.checking(new Expectations() {
      {
        // All three tries throw
        exactly(3).of(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    String existingDate = existingFridayRecurringRuleWithoutExclusions.getBooking().getDate();
    String exclusionDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should throw - albeit after three tries internally
    ruleManager.addRuleExclusion(exclusionDate, existingFridayRecurringRuleWithoutExclusions);
  }

  @Test
  public void testAddRuleExclusionDoesNotThrowIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionOnlyTwice()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exclusion
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if we
    // throw twice but the third try succeeds, then the rule manager does not
    // throw.

    // ARRANGE

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2, 3); // 2 arbitrary

    final Sequence retrySequence = mockery.sequence("retry");
    mockery.checking(new Expectations() {
      {
        // Two failures...
        exactly(2).of(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception("Database put failed - conditional check failed")));
        inSequence(retrySequence);
        // ... but third attempt succeeds
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(returnValue(2));
        inSequence(retrySequence);
      }
    });

    String existingDate = existingFridayRecurringRuleWithoutExclusions.getBooking().getDate();
    String exclusionDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should _not_ throw - we are allowed three tries internally
    ruleManager.addRuleExclusion(exclusionDate, existingFridayRecurringRuleWithoutExclusions);
  }

  @Test
  public void testAddRuleExclusionThrowsIfRuleDoesNotExist() throws Exception {
    // Rule manager should throw if we attempt to add an exclusion to a
    // non-existent rule.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule exclusion addition failed");

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // Create a booking rule that the rule manager does not return
    BookingRule nonExistentRule = new BookingRule(existingFridayRecurringRuleWithoutExclusions);
    // Tweak so no longer matches an existing rule
    nonExistentRule.setIsRecurring(false);

    // ACT
    ruleManager.addRuleExclusion("2016-08-26", nonExistentRule);
  }

  @Test
  public void testAddRuleExclusionThrowsIfRuleIsNonrecurring() throws Exception {
    // It's not valid to add exclusions to non-recurring rules.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule exclusion addition failed");

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // ACT
    ruleManager.addRuleExclusion("2016-08-26", existingThursdayNonRecurringRule);
  }

  @Test
  public void testAddRuleExclusionDoesNothingIfExclusionAlreadyExists() throws Exception {
    // If the exclusion already exists we should return early and not attempt to
    // add it again.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // Rule manager should not attempt to add the exclusion again:
    mockery.checking(new Expectations() {
      {
        never(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
      }
    });

    // ACT
    // Try to add exclusion that already exists
    Optional<BookingRule> updatedRule = ruleManager.addRuleExclusion(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0],
        existingSaturdayRecurringRuleWithExclusion);

    // ASSERT
    // No updated rule should be returned - since no change was made.
    assertTrue("The updated rule should be empty", !updatedRule.isPresent());
  }

  @Test
  public void testAddRuleExclusionThrowsIfExclusionIsForWrongDayOfWeek() throws Exception {
    // Exclusions must be for the same day of the week as their rule.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule exclusion addition failed");

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // ACT
    String fridayExclusionDate = "2016-08-26";
    ruleManager.addRuleExclusion(fridayExclusionDate, existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testAddRuleExclusionThrowsIfExclusionOccursBeforeRuleStarts() throws Exception {
    // Exclusions must be for dates after their rule begins.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule exclusion addition failed");

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    String ruleStartDate = existingSaturdayRecurringRuleWithExclusion.getBooking().getDate();
    String saturdayExclusionDateBeforeRuleStarts = LocalDate
        .parse(ruleStartDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).minusWeeks(1)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    ruleManager.addRuleExclusion(saturdayExclusionDateBeforeRuleStarts,
        existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testAddRuleExclusionThrowsIfExclusionOccursInThePast() throws Exception {
    // Exclusions must be for future dates (not just after their rule begins).
    // To test this we need to try adding an exclusion in the past, but after
    // the rule begins.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule exclusion addition failed");

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // Set current date to a fortnight after the rule begins
    String startSaturday = existingSaturdayRecurringRuleWithExclusion.getBooking().getDate();
    String fortnightAfterStart = LocalDate
        .parse(startSaturday, DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusWeeks(2)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    ruleManager.setCurrentLocalDate(LocalDate.parse(fortnightAfterStart,
        DateTimeFormatter.ofPattern("yyyy-MM-dd")));

    // Choose exclusion date before the fake current date, but after the start
    // date.
    String weekAfterStart = LocalDate
        .parse(startSaturday, DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusWeeks(1)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    ruleManager.addRuleExclusion(weekAfterStart, existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testAddRuleExclusionThrowsIfMaximumNumberOfExclusionsExists() throws Exception {
    // There is a certain maximum number of exclusions that can be present on
    // each rule. Trying to add another should throw.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule exclusion addition failed");

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // Set maximum exclusion number equal to that already present
    ruleManager.setMaxNumberOfDatesToExclude(1);
    // Create an arbitrary-but-otherwise-valid exclusion date to add
    String saturdayExclusionDate = LocalDate
        .parse(existingSaturdayRecurringRuleWithExclusion.getBooking().getDate(),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusWeeks(12)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should throw as adding it would exceed the maximum number
    ruleManager.addRuleExclusion(saturdayExclusionDate, existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testDeleteRuleExclusionHappyPathCallsTheOptimisticPersisterCorrectly()
      throws Exception {
    // Happy path where deleteRuleExclusion goes right through and deletes the
    // exclusion.

    // ARRANGE
    initialiseRuleManager();
    int versionToUse = 24; // Arbitrary
    expectOptimisticPersisterToReturnVersionedAttributes(versionToUse);
    expectToAddOrDeleteRuleExclusionViaOptimisticPersister(versionToUse,
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0], false,
        existingSaturdayRecurringRuleWithExclusion);

    // ACT
    Optional<BookingRule> updatedRule = ruleManager.deleteRuleExclusion(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0],
        existingSaturdayRecurringRuleWithExclusion);

    // ASSERT
    assertTrue("The updated rule should be returned", updatedRule.isPresent());
    assertTrue("The updated rule should be correct",
        updatedRule.get().getDatesToExclude().length == 0);
  }

  @Test
  public void testDeleteRuleExclusionThrowsWhenRuleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has not been initialised");

    // ACT
    // Do not initialise the rule manager first - so deleteRuleExclusion should
    // throw
    ruleManager.deleteRuleExclusion("2016-09-17", existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testDeleteRuleExclusionThrowsWhenTheOptimisticPersisterThrows() throws Exception {
    // N.B. This applies except when the optimistic persister throws a
    // conditional check failed exclusion, which is covered by other tests.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // ACT
    // This should throw
    ruleManager.deleteRuleExclusion(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0],
        existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testDeleteRuleExclusionThrowsIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionThreeTimesRunning()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exclusion
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if all
    // three tries fail then the rule manager will give up and throw.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Database put failed - conditional check failed";
    thrown.expectMessage(message);

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2, 3); // 2 arbitrary
    ;
    mockery.checking(new Expectations() {
      {
        // All three tries throw
        exactly(3).of(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // ACT
    // This should throw - albeit after three tries internally
    ruleManager.deleteRuleExclusion(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0],
        existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testDeleteRuleExclusionDoesNotThrowIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionOnlyTwice()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exclusion
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if we
    // throw twice but the third try succeeds, then the rule manager does not
    // throw.

    // ARRANGE

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2, 3); // 2 arbitrary

    final Sequence retrySequence = mockery.sequence("retry");
    mockery.checking(new Expectations() {
      {
        // Two failures...
        exactly(2).of(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(throwException(new Exception("Database put failed - conditional check failed")));
        inSequence(retrySequence);
        // ... but third attempt succeeds
        oneOf(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
        will(returnValue(2));
        inSequence(retrySequence);
      }
    });

    // ACT
    // This should _not_ throw - as we're allowed three tries
    ruleManager.deleteRuleExclusion(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0],
        existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testDeleteRuleExclusionDoesNothingIfRuleDoesNotExist() throws Exception {
    // If the rule does not exist we should return early and not attempt to
    // delete the exclusion from it.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(2); // 2 arbitrary

    // Rule manager should not attempt to delete the exclusion:
    mockery.checking(new Expectations() {
      {
        never(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
      }
    });

    // Create a booking rule that the rule manager does not return
    BookingRule nonExistentRule = new BookingRule(existingSaturdayRecurringRuleWithExclusion);
    // Tweak so no longer matches an existing rule
    nonExistentRule.getBooking().setName("A.NewPlayer/B.NewPlayer");

    // ACT
    Optional<BookingRule> updatedRule = ruleManager.deleteRuleExclusion(
        nonExistentRule.getDatesToExclude()[0], nonExistentRule);

    // ASSERT
    assertTrue("The updated rule should be empty", !updatedRule.isPresent());
  }

  @Test
  public void testDeleteRuleExclusionDoesNothingIfExclusionDoesNotExist() throws Exception {
    // If the rule does not have the exclusion being deleted we should return
    // early and not attempt to delete the exclusion from it.

    // ARRANGE
    initialiseRuleManager();

    List<BookingRule> existingRules = new ArrayList<>();
    existingRules.addAll(existingBookingRules);
    // Create a booking rule without exclusions
    BookingRule noExclusionRule = new BookingRule(existingSaturdayRecurringRuleWithExclusion);
    noExclusionRule.setDatesToExclude(new String[0]);
    existingRules.remove(existingSaturdayRecurringRuleWithExclusion);
    existingRules.add(noExclusionRule);
    expectOptimisticPersisterToReturnVersionedAttributes(2, existingRules);

    // Rule manager should not attempt to delete the exclusion:
    mockery.checking(new Expectations() {
      {
        never(mockOptimisticPersister).put(with(equal(ruleItemName)), with(anything()),
            with(anything()));
      }
    });

    // ACT
    Optional<BookingRule> updatedRule = ruleManager.deleteRuleExclusion(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0], noExclusionRule);

    // ASSERT
    assertTrue("The updated rule should be empty", !updatedRule.isPresent());
  }

  @Test
  public void testDeleteRuleExclusionThrowsIfExclusionDeletionExposesRuleClash() throws Exception {
    // Removing an exclusion can expose a latent clash between existing rules if
    // a non-recurring rule would have clashed with the recurring rule the
    // exclusion is being removed from - were it not for the exclusion. If such
    // a latent clash is present we should throw and not allow the deletion.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking rule exclusion deletion failed");

    initialiseRuleManager();

    List<BookingRule> existingRules = new ArrayList<>();
    existingRules.addAll(existingBookingRules);
    // Create a non-recurring booking rule that will expose the clash
    BookingRule latentClashingSaturdayRule = new BookingRule(
        existingSaturdayRecurringRuleWithExclusion);
    latentClashingSaturdayRule.setIsRecurring(false);
    latentClashingSaturdayRule.getBooking().setDate(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0]);
    existingRules.add(latentClashingSaturdayRule);
    expectOptimisticPersisterToReturnVersionedAttributes(42, existingRules);

    // ACT
    // Attempt to remove the exclusion to expose the latent clash
    ruleManager.deleteRuleExclusion(
        existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0],
        existingSaturdayRecurringRuleWithExclusion);
  }

  @Test
  public void testApplyRulesThrowsWhenRuleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The rule manager has not been initialised");

    // ACT
    // Do not initialise the rule manager first - so applyRules should throw
    ruleManager.applyRules(existingSaturdayRecurringRuleWithExclusion.getBooking().getDate());
  }

  @Test
  public void testApplyRulesThrowsWhenTheOptimisticPersisterThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    initialiseRuleManager();

    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).get(with(equal(ruleItemName)));
        will(throwException(new Exception(message)));
      }
    });

    // Set up mock SNS client
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockSNSClient);
      }
    });
    ruleManager.setSNSClient(mockSNSClient);

    // ACT
    // This should throw
    ruleManager.applyRules(existingSaturdayRecurringRuleWithExclusion.getBooking().getDate());
  }

  @Test
  public void testApplyRulesThrowsWhenTheBookingManagerThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test BookingManager exception";
    thrown.expectMessage(message);

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);

    mockery.checking(new Expectations() {
      {
        oneOf(mockBookingManager).createBooking(with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // Set up mock SNS client
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockSNSClient);
      }
    });
    ruleManager.setSNSClient(mockSNSClient);

    // ACT
    // This should throw
    ruleManager.applyRules(existingSaturdayRecurringRuleWithExclusion.getBooking().getDate());
  }

  @Test
  public void testApplyRulesNotifiesTheSnsTopicWhenItThrows() throws Exception {
    // It is useful for the admin user to be notified whenever the application
    // of booking rules does not succeed - so that they can apply rule bookings
    // manually instead. This tests that whenever the rule manager catches an
    // exception while applying rules, it notifies the admin SNS topic.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test BookingManager exception";
    thrown.expectMessage(message);

    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);

    mockery.checking(new Expectations() {
      {
        oneOf(mockBookingManager).createBooking(with(anything()));
        will(throwException(new Exception(message)));
      }
    });

    // Set up mock SNS client to expect a notification
    mockSNSClient = mockery.mock(AmazonSNS.class);
    String partialMessage = "Apologies - but there was an error applying the booking rules";
    mockery.checking(new Expectations() {
      {
        oneOf(mockSNSClient).publish(with(equal(adminSnsTopicArn)),
            with(startsWith(partialMessage)), with(equal("Sqawsh booking rules failed to apply")));
      }
    });
    ruleManager.setSNSClient(mockSNSClient);

    // ACT
    // This should throw - and notify the SNS topic
    ruleManager.applyRules(existingSaturdayRecurringRuleWithExclusion.getBooking().getDate());
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_ApplicableNonRecurringRule()
      throws Exception {
    // A nonrecurring rule for the apply date should cause a booking to be made.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectBookingManagerCall(existingThursdayNonRecurringRule.getBooking());
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);

    // ACT
    // This should create a booking
    ruleManager.applyRules(existingThursdayNonRecurringRule.getBooking().getDate());
  }

  @Test
  public void testApplyRulesReturnsBookingsItHasMade_SingleBooking() throws Exception {
    // applyRules should return a list of the rule-based bookings it has made -
    // so that, e.g., they can be backed up.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectBookingManagerCall(existingThursdayNonRecurringRule.getBooking());
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);

    // ACT
    // This should create a booking
    List<Booking> bookings = ruleManager.applyRules(existingThursdayNonRecurringRule.getBooking()
        .getDate());

    // ASSERT
    assertTrue("Unexpected bookings returned by applyRules",
        bookings.get(0).equals(existingThursdayNonRecurringRule.getBooking())
            && bookings.size() == 1);
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_NonRecurringRuleDoesNotRecur()
      throws Exception {
    // A nonrecurring rule should apply for its date only and should not recur
    // in later weeks.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);
    // Set apply date to one week after the non-recurring rule - it should be
    // ignored.
    String existingDate = existingThursdayNonRecurringRule.getBooking().getDate();
    String applyDate = LocalDate.parse(existingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should not create a booking
    ruleManager.applyRules(applyDate);
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_ApplicableRecurringRuleNoExclusion()
      throws Exception {
    // A recurring rule with no exclusion for the apply date should cause a
    // booking to be made.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectBookingManagerCall(existingFridayRecurringRuleWithoutExclusions.getBooking());
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);

    // ACT
    // This should create a booking
    ruleManager.applyRules(existingFridayRecurringRuleWithoutExclusions.getBooking().getDate());
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_ApplicableRecurringRuleNoExclusionRecurrentBooking()
      throws Exception {
    // A recurring rule with no exclusion for the apply date should cause a
    // booking to be made. This tests for an apply date corresponding to a later
    // recurrence of the recurrent rule rather than the rule's initial booking.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);

    // Set up booking for a recurrence of the rule's initial booking
    String initialDate = existingFridayRecurringRuleWithoutExclusions.getBooking().getDate();
    String newBookingDate = LocalDate.parse(initialDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusWeeks(10).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    Booking bookingToCreate = new Booking(existingFridayRecurringRuleWithoutExclusions.getBooking());
    bookingToCreate.setDate(newBookingDate);
    expectBookingManagerCall(bookingToCreate);
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);

    // ACT
    // This should create a booking
    ruleManager.applyRules(newBookingDate);
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_ApplicableRecurringRuleWithExclusion()
      throws Exception {
    // A recurring rule with an exclusion for the apply date should not cause a
    // booking to be made.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);
    mockery.checking(new Expectations() {
      {
        never(mockBookingManager).createBooking(with(anything()));
      }
    });

    // ACT
    // This should not create a booking
    ruleManager.applyRules(existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0]);
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_FutureRecurringRuleNoExclusion()
      throws Exception {
    // A recurring rule with no exclusion for the apply date, but that does not
    // begin until after the apply date should not cause a booking to be made.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);
    mockery.checking(new Expectations() {
      {
        never(mockBookingManager).createBooking(with(anything()));
      }
    });

    // Get date before start date of rule
    String initialDate = existingFridayRecurringRuleWithoutExclusions.getBooking().getDate();
    String earlierDate = LocalDate.parse(initialDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusWeeks(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should not create a booking
    ruleManager.applyRules(earlierDate);
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_NoRelevantRules() throws Exception {
    // An apply date with no relevant rules should not cause a booking to be
    // made, but should still purge expired rules and exclusions.

    // ARRANGE
    initialiseRuleManager();
    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);
    mockery.checking(new Expectations() {
      {
        never(mockBookingManager).createBooking(with(anything()));
      }
    });

    // Get date for day of week different to any rule's
    String friday = existingFridayRecurringRuleWithoutExclusions.getBooking().getDate();
    String monday = LocalDate.parse(friday, DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusDays(3)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should not create a booking - none of our rules are for mondays
    ruleManager.applyRules(monday);
  }

  @Test
  public void testApplyRulesToleratesApplyDateInThePast() throws Exception {
    // An apply date in the past should not cause a booking to be made, but
    // should still purge expired rules and exclusions.

    // ARRANGE
    initialiseRuleManager();
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules);
    mockery.checking(new Expectations() {
      {
        never(mockBookingManager).createBooking(with(anything()));
      }
    });

    // Get date before our current date
    String pastDate = LocalDate
        .parse(fakeCurrentSaturdayDateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusWeeks(33).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    // ACT
    // This should be tolerated even though the date is in the past
    ruleManager.applyRules(pastDate);
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_MultipleApplicableRules() throws Exception {
    // Tests that when there is more than one applicable rule, all applicable
    // rules cause a booking to be made.

    // ARRANGE
    initialiseRuleManager();

    List<BookingRule> existingRules = new ArrayList<>();
    existingRules.addAll(existingBookingRules);
    // Add second booking rule for the same day of the week as an existing one
    BookingRule sameDayRule = new BookingRule(existingFridayRecurringRuleWithoutExclusions);
    // Tweak so does not clash with existing rule
    sameDayRule.getBooking().setCourt(
        sameDayRule.getBooking().getCourt() + sameDayRule.getBooking().getCourtSpan());
    sameDayRule.getBooking().setSlot(
        sameDayRule.getBooking().getSlot() + sameDayRule.getBooking().getSlotSpan());
    existingRules.add(sameDayRule);
    expectOptimisticPersisterToReturnVersionedAttributes(2, existingRules);

    expectBookingManagerCall(existingFridayRecurringRuleWithoutExclusions.getBooking());
    expectBookingManagerCall(sameDayRule.getBooking());
    expectPurgeExpiredRulesAndRuleExclusions(42, existingRules);

    // ACT
    // This should create two bookings for the specified date
    ruleManager.applyRules(sameDayRule.getBooking().getDate());
  }

  @Test
  public void testApplyRulesReturnsBookingsItHasMade_MultipleBookings() throws Exception {
    // applyRules should return a list of the rule-based bookings it has made -
    // so that, e.g., they can be backed up.

    // ARRANGE
    initialiseRuleManager();

    List<BookingRule> existingRules = new ArrayList<>();
    existingRules.addAll(existingBookingRules);
    // Add second booking rule for the same day of the week as an existing one
    BookingRule sameDayRule = new BookingRule(existingFridayRecurringRuleWithoutExclusions);
    // Tweak so does not clash with existing rule
    sameDayRule.getBooking().setCourt(
        sameDayRule.getBooking().getCourt() + sameDayRule.getBooking().getCourtSpan());
    sameDayRule.getBooking().setSlot(
        sameDayRule.getBooking().getSlot() + sameDayRule.getBooking().getSlotSpan());
    existingRules.add(sameDayRule);
    expectOptimisticPersisterToReturnVersionedAttributes(2, existingRules);

    expectBookingManagerCall(existingFridayRecurringRuleWithoutExclusions.getBooking());
    expectBookingManagerCall(sameDayRule.getBooking());
    expectPurgeExpiredRulesAndRuleExclusions(42, existingRules);

    List<Booking> expectedBookings = new ArrayList<>();
    expectedBookings.add(existingFridayRecurringRuleWithoutExclusions.getBooking());
    expectedBookings.add(sameDayRule.getBooking());

    // ACT
    // This should create two bookings for the specified date
    List<Booking> bookings = ruleManager.applyRules(sameDayRule.getBooking().getDate());

    // ASSERT
    assertTrue("Unexpected bookings returned by applyRules", bookings.equals(expectedBookings));
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_PurgeExpiredNonRecurringRule()
      throws Exception {
    // applyRules should delete non-recurring rules for dates before the current
    // date.

    // ARRANGE
    initialiseRuleManager();

    // Set the current date to a date after an existing non-recurring rule, but
    // also for a day-of-the-week when no other rules apply (just to avoid extra
    // createBooking noise):
    String thursday = existingThursdayNonRecurringRule.getBooking().getDate();
    String dayAfterThursday = LocalDate.parse(thursday, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .plusDays(4).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    ruleManager.setCurrentLocalDate(LocalDate.parse(dayAfterThursday,
        DateTimeFormatter.ofPattern("yyyy-MM-dd")));

    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules,
        Optional.of(existingThursdayNonRecurringRule), Optional.empty());

    // ACT
    // This should delete the (expired) Thursday rule
    ruleManager.applyRules(dayAfterThursday);
  }

  @Test
  public void testApplyRulesCallsBookingManagerCorrectly_PurgeExpiredRuleExclusions()
      throws Exception {
    // applyRules should delete rule exclusions for dates before the current
    // day.

    // ARRANGE
    initialiseRuleManager();

    // Set the current date to a date after an exclusion, but also for a
    // day-of-the-week when no other rules apply (just to avoid extra
    // createBooking noise):
    String exclusionDate = existingSaturdayRecurringRuleWithExclusion.getDatesToExclude()[0];
    String daysAfterExclusionDate = LocalDate
        .parse(exclusionDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusDays(2)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    ruleManager.setCurrentLocalDate(LocalDate.parse(daysAfterExclusionDate,
        DateTimeFormatter.ofPattern("yyyy-MM-dd")));

    expectOptimisticPersisterToReturnVersionedAttributes(42);
    expectPurgeExpiredRulesAndRuleExclusions(42, existingBookingRules,
        Optional.of(existingThursdayNonRecurringRule),
        Optional.of(new ImmutablePair<>(existingSaturdayRecurringRuleWithExclusion, exclusionDate)));

    // ACT
    // This should delete the (expired) Thursday rule
    ruleManager.applyRules(daysAfterExclusionDate);
  }
}