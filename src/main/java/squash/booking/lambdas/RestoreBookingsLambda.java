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
import squash.booking.lambdas.utils.BookingManager;
import squash.booking.lambdas.utils.IBackupManager;
import squash.booking.lambdas.utils.IBookingManager;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Optional;

/**
 * AWS Lambda function to restore bookings to the database.
 * 
 * <p>This is manually invoked at the lambda console, passing in the set
 *    of bookings to be restored in the same JSON format as provided
 *    by the database backup lambda.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class RestoreBookingsLambda {

  private Optional<IBackupManager> backupManager;
  private Optional<IBookingManager> bookingManager;

  public RestoreBookingsLambda() {
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
      bookingManager.get().initialise(logger);
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
      backupManager.get().initialise(getBookingManager(logger), logger);
    }
    return backupManager.get();
  }

  /**
  * Restore bookings to the database.
  * 
  * @param request containing bookings to restore.
  * @return response.
  */
  public RestoreBookingsLambdaResponse restoreBookings(RestoreBookingsLambdaRequest request,
      Context context) throws Exception {
    LambdaLogger logger = context.getLogger();
    logger.log("Restoring bookings for request: " + request.toString());
    getBackupManager(logger)
        .restoreBookings(request.getBookings(), request.getClearBeforeRestore());
    logger.log("Finished restoring bookings");

    return new RestoreBookingsLambdaResponse();
  }
}