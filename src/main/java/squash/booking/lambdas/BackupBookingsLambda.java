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

import squash.booking.lambdas.utils.BackupManager;
import squash.booking.lambdas.utils.Booking;
import squash.booking.lambdas.utils.BookingManager;
import squash.booking.lambdas.utils.IBackupManager;
import squash.booking.lambdas.utils.IBookingManager;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.List;
import java.util.Optional;

/**
 * AWS Lambda function to backup all bookings from the database.
 * 
 * <p>This is usually invoked by a scheduled event to provide regular
 *    full backups to email and S3. However it can also be manually
 *    invoked at the lambda console (e.g. you might do this
 *    immediately before a stack update, just in case...)
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BackupBookingsLambda {

  private Optional<IBackupManager> backupManager;
  private Optional<IBookingManager> bookingManager;

  public BackupBookingsLambda() {
    backupManager = Optional.empty();
    bookingManager = Optional.empty();
  }

  /**
   * Returns the {@link squash.booking.lambdas.utils.IBookingManager}.
   */
  protected IBookingManager getBookingManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!bookingManager.isPresent()) {
      bookingManager = Optional.of(new BookingManager());
      bookingManager.get().Initialise(logger);
    }
    return bookingManager.get();
  }

  /**
   * Returns the {@link squash.booking.lambdas.utils.IBackupManager}.
   */
  protected IBackupManager getBackupManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!backupManager.isPresent()) {
      backupManager = Optional.of(new BackupManager());
      backupManager.get().Initialise(getBookingManager(logger), logger);
    }
    return backupManager.get();
  }

  /**
  * Backs up all bookings in the database.
  * 
  * @param request.
  * @return response containing all bookings from the database.
  */
  public BackupBookingsLambdaResponse backupBookings(BackupBookingsLambdaRequest request,
      Context context) throws Exception {
    LambdaLogger logger = context.getLogger();
    logger.log("Backup bookings for request: " + request.toString());

    // Backup all bookings
    List<Booking> bookings = getBackupManager(logger).backupAllBookings();
    logger.log("Got " + bookings.size() + " bookings from backup manager");

    BackupBookingsLambdaResponse backupBookingsLambdaResponse = new BackupBookingsLambdaResponse();
    backupBookingsLambdaResponse.setAllBookings(bookings);
    return backupBookingsLambdaResponse;
  }
}