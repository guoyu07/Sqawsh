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

import squash.booking.lambdas.core.BookingManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.IRuleManager;
import squash.booking.lambdas.core.RuleManager;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

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

  public ApplyBookingRulesLambda() {
    ruleManager = Optional.empty();
    bookingManager = Optional.empty();
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
    getRuleManager(logger).applyRules(newDay);
    logger.log("Applied booking rules for date: " + newDay);

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