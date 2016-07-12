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

package squash.booking.lambdas.core;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.List;

/**
 * Interface for all classes managing backups of the bookings/rules database.
 * 
 * <p>All bookings/rules backup and restore operations should use this interface.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IBackupManager {

  /**
   * Initialises the manager.
   */
  void initialise(IBookingManager bookingManager, IRuleManager ruleManager, LambdaLogger logger)
      throws IOException;

  /**
   * Backup a single court booking.
   * 
   * @param booking the booking just created or deleted
   * @param isCreation true if the booking was created, false if it was deleted
   * @throws Exception when the booking backup fails.
   */
  void backupSingleBooking(Booking booking, Boolean isCreation) throws Exception;

  /**
   * Backup a single booking rule mutation.
   * 
   * @param bookingRule the booking rule just updated or deleted
   * @param isNotDeletion true if the booking rule was created or updated, false if it was deleted
   */
  void backupSingleBookingRule(BookingRule bookingRule, Boolean isNotDeletion)
      throws InterruptedException, JsonProcessingException;

  /**
   * Backup all bookings and booking rules.
   * 
   * @return the bookings and booking rules.
   * @throws Exception when the backup fails.
   */
  ImmutablePair<List<Booking>, List<BookingRule>> backupAllBookingsAndBookingRules()
      throws Exception;

  /**
  * Restore bookings and booking rules.
  * 
  * @param bookings the bookings to restore.
  * @param bookingRules the booking rules to restore.
  * @param clearBeforeRestore whether to clear existing bookings and rules before restoring with the supplied bookings and rules.
  * @throws Exception when the restore fails.
  */
  void restoreAllBookingsAndBookingRules(List<Booking> bookings, List<BookingRule> bookingRules,
      Boolean clearBeforeRestore) throws Exception;
}