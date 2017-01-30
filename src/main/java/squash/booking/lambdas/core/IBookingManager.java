/**
 * Copyright 2015-2017 Robin Steel
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

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.List;

/**
 * Interface for all classes managing bookings.
 * 
 * <p>All bookings management should be performed by a class implementing this interface.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IBookingManager {

  /**
   * Initialises the manager with a CloudwatchLogs logger.
   */
  void initialise(LambdaLogger logger) throws Exception;

  /**
   * Creates a court booking.
   * 
   * @param isSquashServiceUserCall false if call is for backup/restore or application of rules.
   * @return All bookings for the same day as the created booking, including the created booking.
   * @throws Exception when the booking creation fails.
   */
  List<Booking> createBooking(Booking booking, boolean isSquashServiceUserCall) throws Exception;

  /**
   * Validates a court booking.
   * 
   * @throws Exception when the booking is invalid.
   */
  void validateBooking(Booking booking) throws Exception;

  /**
   * Returns all court bookings for a given date.
   * 
   * @param date the date in YYYY-MM-DD format.
   * @param isSquashServiceUserCall false if call is for backup/restore or application of rules.
   * @throws Exception when the booking retrieval fails.
   */
  List<Booking> getBookings(String date, boolean isSquashServiceUserCall) throws Exception;

  /**
   * Returns all court bookings for all dates.
   * 
   * @param isSquashServiceUserCall false if call is for backup/restore or application of rules.
   * @throws Exception when the booking retrieval fails.
   */
  List<Booking> getAllBookings(boolean isSquashServiceUserCall) throws Exception;

  /**
   * Deletes a court booking.
   * 
   * @param isSquashServiceUserCall false if call is for backup/restore or application of rules.
   * @return All bookings for the same day as the deleted booking, excluding the deleted booking.
   * @throws Exception when the booking deletion fails.
   */
  List<Booking> deleteBooking(Booking booking, boolean isSquashServiceUserCall) throws Exception;

  /**
   * Deletes all bookings for the previous day.
   * 
   * @param isSquashServiceUserCall false if call is for backup/restore or application of rules.
   * @throws Exception when the deletion fails.
   */
  void deleteYesterdaysBookings(boolean isSquashServiceUserCall) throws Exception;

  /**
   * Deletes all bookings for all dates.
   * 
   * @param isSquashServiceUserCall false if call is for backup/restore or application of rules.
   * @throws Exception 
   */
  void deleteAllBookings(boolean isSquashServiceUserCall) throws Exception;
}