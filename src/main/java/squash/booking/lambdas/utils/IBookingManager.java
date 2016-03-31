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

import java.io.IOException;
import java.util.List;

/**
 * Interface for all classes managing interactions with the bookings database.
 * 
 * <p>All database interactions should be performed by a class implementing this interface.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IBookingManager {

  /**
   * Initialises the manager with a CloudwatchLogs logger.
   */
  void Initialise(LambdaLogger logger) throws IOException;

  /**
   * Creates a court booking.
   * 
   * @return All bookings for the same day as the created booking, including the created booking.
   * @throws Exception when the booking creation fails.
   */
  List<Booking> createBooking(Booking booking) throws Exception;

  /**
   * Returns all court bookings for a given date.
   * 
   * @param date the date in YYYY-MM-DD format.
   */
  List<Booking> getBookings(String date);

  /**
   * Returns all court bookings for all dates.
   * 
   */
  List<Booking> getBookings();

  /**
   * Deletes a court booking.
   * 
   * @return All bookings for the same day as the deleted booking, excluding the deleted booking.
   * @throws Exception when the booking deletion fails.
   */
  List<Booking> deleteBooking(Booking booking) throws Exception;

  /**
   * Deletes all bookings for the previous day.
   */
  void deleteYesterdaysBookings();

  /**
   * Deletes all bookings.
   * @throws Exception 
   */
  void deleteAllBookings() throws Exception;
}