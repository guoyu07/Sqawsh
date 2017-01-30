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

package squash.booking.lambdas;

import squash.booking.lambdas.core.BackupManager;
import squash.booking.lambdas.core.Booking;
import squash.booking.lambdas.core.BookingManager;
import squash.booking.lambdas.core.BookingRule;
import squash.booking.lambdas.core.IBackupManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.ILifecycleManager;
import squash.booking.lambdas.core.IRuleManager;
import squash.booking.lambdas.core.LifecycleManager;
import squash.booking.lambdas.core.RuleManager;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.List;
import java.util.Optional;

/**
 * AWS Lambda function to backup all bookings and booking rules from the database.
 * 
 * <p>This is usually invoked by a scheduled event to provide regular
 *    full backups to email and S3. However it can also be manually
 *    invoked at the lambda console (e.g. you might do this
 *    immediately before a stack update, just in case...)
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BackupBookingsAndBookingRulesLambda {

  private Optional<IBackupManager> backupManager;
  private Optional<IRuleManager> ruleManager;
  private Optional<ILifecycleManager> lifecycleManager;
  private Optional<IBookingManager> bookingManager;

  public BackupBookingsAndBookingRulesLambda() {
    backupManager = Optional.empty();
    ruleManager = Optional.empty();
    lifecycleManager = Optional.empty();
    bookingManager = Optional.empty();
  }

  /**
   * Returns the {@link squash.booking.lambdas.core.IRuleManager}.
   */
  protected IRuleManager getRuleManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!ruleManager.isPresent()) {
      ruleManager = Optional.of(new RuleManager());
      ruleManager.get().initialise(getBookingManager(logger), getLifecycleManager(logger), logger);
    }
    return ruleManager.get();
  }

  /**
   * Returns the {@link squash.booking.lambdas.core.ILifecycleManager}.
   */
  protected ILifecycleManager getLifecycleManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!lifecycleManager.isPresent()) {
      lifecycleManager = Optional.of(new LifecycleManager());
      lifecycleManager.get().initialise(logger);
    }
    return lifecycleManager.get();
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
   * Returns the {@link squash.booking.lambdas.core.IBackupManager}.
   */
  protected IBackupManager getBackupManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!backupManager.isPresent()) {
      backupManager = Optional.of(new BackupManager());
      backupManager.get().initialise(getBookingManager(logger), getRuleManager(logger), logger);
    }
    return backupManager.get();
  }

  /**
  * Backs up all bookings and booking rules in the database.
  */
  public BackupBookingsAndBookingRulesLambdaResponse backupBookingsAndBookingRules(
      BackupBookingsAndBookingRulesLambdaRequest request, Context context) throws Exception {
    LambdaLogger logger = context.getLogger();
    logger.log("Backup bookings and booking rules for request: " + request.toString());

    // Backup all bookings and booking rules
    ImmutablePair<List<Booking>, List<BookingRule>> bookingAndRules = getBackupManager(logger)
        .backupAllBookingsAndBookingRules();
    logger.log("Backed up bookings and booking rules");

    BackupBookingsAndBookingRulesLambdaResponse response = new BackupBookingsAndBookingRulesLambdaResponse();
    response.setBookings(bookingAndRules.left);
    response.setBookingRules(bookingAndRules.right);
    response.setClearBeforeRestore(true);
    return response;
  }
}