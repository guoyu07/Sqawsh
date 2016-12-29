/**
 * Copyright 2015-2016 Robin Steel
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
import squash.booking.lambdas.core.BookingsUtilities;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.IPageManager;
import squash.booking.lambdas.core.PageManager;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * AWS Lambda function to refresh all booking webpages.
 * 
 * <p>This is usually invoked by AWS Lambda.
 * <p>It is run, e.g., every midnight to advance the site by 1 day.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class UpdateBookingsLambda {

  private Optional<IBookingManager> bookingManager;
  private Optional<IPageManager> pageManager;

  public UpdateBookingsLambda() {
    bookingManager = Optional.empty();
    pageManager = Optional.empty();
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
   *  Returns the dates for which bookings can be made, in YYYY-MM-DD format.
   */
  protected List<String> getValidDates() {
    // Use a getter here so unit tests can substitute a different method.

    return new GetValidDatesLambda().getValidDates(new GetValidDatesLambdaRequest()).getDates();
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
   * Refreshes all booking webpages.
   * 
   * <p>Moves the site one day forward by:
   * <ul>
   *    <li>adding a new page for the most-future bookable date</li>
   *    <li>refreshing all other booking pages to account for the new date range</li>
   *    <li>removing the previous day's page</li>
   * </ul>
   *
   * @param request with details of our Apigateway Api.
   * @return response containing the current date.
   * @throws Exception when the method fails.
   */
  public UpdateBookingsLambdaResponse updateBookings(UpdateBookingsLambdaRequest request,
      Context context) throws Exception {
    LambdaLogger logger = context.getLogger();

    try {
      logger.log("About to refresh all bookings pages");
      IPageManager pageManager = getPageManager(logger);
      String apiGatewayBaseUrl = request.getApiGatewayBaseUrl();
      if (apiGatewayBaseUrl == null) {
        logger.log("Throwing because request has null ApiGatewayBaseUrl");
        throw new Exception("ApiGatewayBaseUrl should not be null");
      }
      String revvingSuffix = getEnvironmentVariable("RevvingSuffix", logger);
      logger.log("Using revvingSuffix: " + revvingSuffix);

      pageManager.refreshAllPages(getValidDates(), apiGatewayBaseUrl, revvingSuffix);
      logger.log("Refreshed all bookings pages");

      // Remove the now-previous day's bookings from the database
      logger.log("About to remove yesterday's bookings from the database");
      IBookingManager bookingManager = getBookingManager(logger);
      bookingManager.deleteYesterdaysBookings();
      logger.log("Removed yesterday's bookings from database");
    } catch (Exception e) {
      logger.log("Exception caught in updateBookings Lambda: " + e.getMessage());
      throw new Exception("Apologies - something has gone wrong. Please try again.", e);
    }

    UpdateBookingsLambdaResponse updateBookingsLambdaResponse = new UpdateBookingsLambdaResponse();
    updateBookingsLambdaResponse.setCurrentDate(getCurrentLocalDate().format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    return updateBookingsLambdaResponse;
  }

  /**
   * Returns a named environment variable.
   * @throws Exception 
   */
  protected String getEnvironmentVariable(String variableName, LambdaLogger logger)
      throws Exception {
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