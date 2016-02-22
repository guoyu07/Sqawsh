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

package squash.booking.lambdas.utils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.List;

/**
 * Interface for all classes managing web pages.
 * 
 * <p>All modifications to the website's web pages should be
 *    performed by a class implementing this interface.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IPageManager {

  /**
   * Initialises the manager with a CloudwatchLogs logger.
   * 
   * @throws Exception when initialisation fails.
   */
  void Initialise(IBookingManager bookingsManager, LambdaLogger logger) throws Exception;

  /**
   * Refreshes a bookings web page for a specified date.
   * 
   * <p>This has a parameter for requesting a guid-suffixed duplicate of the page also be created.
   *    This is a workaround for S3's only-eventual-consistency, to ensure someone creating or
   *    deleting a booking will always be shown a booking page with their change immediately visible.
   *
   * @param date the date to refresh in YYYY-MM-DD format.
   * @param validDates the dates for which bookings can be made, in YYYY-MM-DD format.
   * @param apiGatewayBaseUrl the base Url of our apigateway Api, e.g. https://dhfmlwxdgr.execute-api.eu-west-1.amazonaws.com/SquashApi.
   * @param createDuplicate whether to create a duplicate of the page in S3 with a GUID suffix.
   * @param bookings the bookings for the specified date.
   *
   * @return The guid embedded in the refreshed page, and used as a suffix when a duplicate is created.
   * @throws Exception when page refresh fails.
   */
  String refreshPage(String date, List<String> validDates, String apiGatewayBaseUrl,
      Boolean createDuplicate, List<Booking> bookings) throws Exception;

  /**
   * Refreshes bookings web pages for all currently-bookable dates.
   * 
   * <p>Refreshes all booking pages for dates that are currently bookable:
   * <ul>
   *     <li>Updates all web pages for currently-bookable dates.</li>
   *     <li>Does not create guid-suffixed duplicates of these pages.</li>
   *     <li>Updates the index web page to redirect to the current day's booking page.</li>
   *     <li>Deletes the booking page for the previous day - if there is one.</li>
   * </ul>
   *
   * @param validDates the dates for which bookings can be made, in YYYY-MM-DD format.
   * @param apiGatewayBaseUrl the base Url of our apigateway Api, e.g. .
   *
   * @throws Exception when refreshing all pages fails.
   */
  void refreshAllPages(List<String> validDates, String apiGatewayBaseUrl) throws Exception;
}