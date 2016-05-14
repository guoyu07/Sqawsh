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
 * Interface for all classes managing backups of the bookings database.
 * 
 * <p>All bookings backup and restore operations should use this interface.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IBackupManager {

  /**
   * Initialises the manager.
   */
  void initialise(IBookingManager bookingManager, LambdaLogger logger) throws IOException;

  /**
   * Backup a single court booking.
   * 
   * @param booking the booking just created or deleted
   * @param isCreation true if the booking was created, false if it was deleted
   * @throws Exception when the booking backup fails.
   */
  void backupSingleBooking(Booking booking, Boolean isCreation) throws Exception;

  /**
   * Backup all court bookings.
   * 
   * @return all bookings from the database
   * @throws Exception when the bookings backup fails.
   */
  List<Booking> backupAllBookings() throws Exception;

  /**
  * Restore bookings.
  * 
  * @param bookings the bookings to restore.
  * @param clearBeforeRestore whether to clear existing bookings before restoring with the supplied bookings.
  * @throws Exception when the bookings restore fails.
  */
  void restoreBookings(List<Booking> bookings, Boolean clearBeforeRestore) throws Exception;
}