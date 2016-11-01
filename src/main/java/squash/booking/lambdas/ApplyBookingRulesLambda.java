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

package squash.booking.lambdas;

import squash.booking.lambdas.core.BackupManager;
import squash.booking.lambdas.core.Booking;
import squash.booking.lambdas.core.BookingManager;
import squash.booking.lambdas.core.IBackupManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.IPageManager;
import squash.booking.lambdas.core.IRuleManager;
import squash.booking.lambdas.core.PageManager;
import squash.booking.lambdas.core.RuleManager;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AWS Lambda function to apply all booking rules for a specific date.
 * 
 * <p>This is usually invoked by a scheduled event. However it can also
 *    be manually invoked at the lambda console.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class ApplyBookingRulesLambda {

  private Optional<IRuleManager> ruleManager;
  private Optional<IBookingManager> bookingManager;
  private Optional<IPageManager> pageManager;
  private Optional<IBackupManager> backupManager;

  public ApplyBookingRulesLambda() {
    ruleManager = Optional.empty();
    bookingManager = Optional.empty();
    pageManager = Optional.empty();
    backupManager = Optional.empty();
  }

  /**
   * Returns the {@link squash.booking.lambdas.core.IRuleManager}.
   */
  protected IRuleManager getRuleManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!ruleManager.isPresent()) {
      ruleManager = Optional.of(new RuleManager());
      ruleManager.get().initialise(getBookingManager(logger), logger);
    }
    return ruleManager.get();
  }

  /**
   * Returns the {@link squash.booking.lambdas.core.IBookingManager}.
   */
  protected IBookingManager getBookingManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!bookingManager.isPresent()) {
      bookingManager = Optional.of(new BookingManager());
      bookingManager.get().initialise(logger);
    }
    return bookingManager.get();
  }

  /**
   * Returns the {@link squash.booking.lambdas.core.IPageManager}.
   */
  protected IPageManager getPageManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!pageManager.isPresent()) {
      pageManager = Optional.of(new PageManager());
      pageManager.get().initialise(getBookingManager(logger), logger);
    }
    return pageManager.get();
  }

  /**
   * Returns the {@link squash.booking.lambdas.core.IBackupManager}.
   */
  protected IBackupManager getBackupManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!backupManager.isPresent()) {
      backupManager = Optional.of(new BackupManager());
      backupManager.get().initialise(getBookingManager(logger), getRuleManager(logger), logger);
    }
    return backupManager.get();
  }

  /**
  * Applies all booking rules for the day after our valid dates.
  * 
  * This day will not have been seen yet so will be unbooked.
  */
  public ApplyBookingRulesLambdaResponse applyBookingRules(ApplyBookingRulesLambdaRequest request,
      Context context) throws Exception {
    LambdaLogger logger = context.getLogger();
    logger.log("Apply booking rules for request: " + request.toString());

    // Apply all booking rules
    List<String> validDatesList = getValidDates();
    String newDay = LocalDate
        .parse(validDatesList.get(validDatesList.size() - 1),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusDays(1)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    List<Booking> ruleBookings = getRuleManager(logger).applyRules(newDay);
    logger.log("Applied booking rules for date: " + newDay);

    // Backup each of the created bookings
    logger.log("About to backup rule-based bookings");
    for (Booking booking : ruleBookings) {
      getBackupManager(logger).backupSingleBooking(booking, true);
      // Short sleep to minimise chance of getting TooManyRequests error
      try {
        Thread.sleep(10);
      } catch (InterruptedException interruptedException) {
        logger.log("Sleep before backing-up next booking has been interrupted.");
      }
    }
    logger.log("Backed-up rule-based bookings");

    // We've applied the rules - so update the corresponding booking page. This
    // will need to have dates on its dropdown that will be valid after the next
    // getValidDates rollover, so we need to increment each valid date. This new
    // page will become visible to users after getValidDates rolls over, and
    // will be replaced again when updateBookings lambda runs. Our goal is to
    // ensure everything stays valid in the short window between getValidDates
    // rollover and updateBookings running.
    // Rollover sequence around midnight will be:
    // - applyRules at 9pm UTC - i.e. 10pm BST
    // - getValidDates rollover at 12.00AM UTC (winter), 11.00PM UTC (BST)
    // - updateBookings at 12.10AM UTC - i.e. 1.10AM (BST)
    // Thus these should not overlap in Summer or Winter.
    logger.log("About to create new booking page in S3 with new rule-based booking(s)");
    IPageManager pageManager = getPageManager(logger);
    String apiGatewayBaseUrl = request.getApiGatewayBaseUrl();
    if (apiGatewayBaseUrl == null) {
      logger.log("Throwing because request has null ApiGatewayBaseUrl");
      throw new Exception("ApiGatewayBaseUrl should not be null");
    }
    List<String> advancedValidDates = getValidDates()
        .stream()
        .map(
            d -> LocalDate.parse(d, DateTimeFormatter.ofPattern("yyyy-MM-dd")).plusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).collect(Collectors.toList());
    pageManager.refreshPage(newDay, advancedValidDates, apiGatewayBaseUrl, true, bookingManager
        .get().getBookings(newDay));
    logger.log("Created new booking page in S3 with new rule-based booking(s)");

    return new ApplyBookingRulesLambdaResponse();
  }

  /**
   * Returns all dates for which bookings can currently be made.
   */
  protected List<String> getValidDates() {
    // Use a getter here so unit tests can substitute a different method.

    return new GetValidDatesLambda().getValidDates(new GetValidDatesLambdaRequest()).getDates();
  }
}