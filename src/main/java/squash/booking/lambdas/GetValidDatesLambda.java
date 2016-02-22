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

import squash.booking.lambdas.utils.BookingsUtilities;

import com.amazonaws.services.lambda.runtime.Context;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AWS Lambda function returning all dates for which bookings can be made.
 * 
 * <p>This is usually invoked by AWS Lambda.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class GetValidDatesLambda {

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
   *  Returns the number of days in the booking window.
   *
   * @see BookingsConfiguration#getBookingWindowLengthInDays()
   */
  protected Integer getBookingWindowLengthInDays() {
    // Use a getter here so unit tests can substitute a different number.

    // This is the number of days, starting today, for which bookings
    // can be made.
    return BookingsConfiguration.getBookingWindowLengthInDays();
  }

  /**
   * Returns all dates for which bookings can currently be made.
   * 
   * @param request which has no useful information.
   * @param context provided by AWS Lambda.
   * @return response containing the valid dates.
   * @throws Exception when the method fails.
   */
  public GetValidDatesLambdaResponse getValidDates(GetValidDatesLambdaRequest request,
      Context context) throws Exception {
    try {
      return getValidDates(request);
    } catch (Exception e) {
      context.getLogger().log("Exception caught in getValidDates Lambda: " + e.getMessage());
      // Should include redirecturl here?
      throw new Exception("Apologies - something has gone wrong. Please try again.", e);
    }
  }

  /**
   * Returns all dates for which bookings can currently be made.
   * 
   * <p>This overload is provided for use by clients other than AWS Lambda.
   * 
   * @param request which has no useful information.
   * @return response containing the valid dates.
   */
  public GetValidDatesLambdaResponse getValidDates(GetValidDatesLambdaRequest request) {
    // Compute the list of dates for which we can view/make bookings
    // (this will be a restricted-length rolling window starting today)
    // Get the current date in correct time zone
    LocalDate currentDate = getCurrentLocalDate();
    LocalDate lastValidDate = currentDate.plusDays(getBookingWindowLengthInDays());

    // Add all valid dates as String-s to a list
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    List<String> validDates = new ArrayList<>();
    for (LocalDate date = currentDate; date.isBefore(lastValidDate); date = date.plusDays(1)) {
      validDates.add(date.format(formatter));
    }
    GetValidDatesLambdaResponse response = new GetValidDatesLambdaResponse();
    response.setDates(validDates);
    return response;
  }
}