/**
 * Copyright 2016 Robin Steel
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
import squash.booking.lambdas.core.BookingsUtilities;
import squash.booking.lambdas.core.IBackupManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.IPageManager;
import squash.booking.lambdas.core.IRuleManager;
import squash.booking.lambdas.core.PageManager;
import squash.booking.lambdas.core.RuleManager;
import squash.deployment.lambdas.utils.ExceptionUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * AWS Lambda function to create or delete a court booking rule or rule exclusion.
 * 
 * <p>This is usually invoked by AWS Lambda.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PutDeleteBookingRuleOrExclusionLambda {

  private Optional<IBackupManager> backupManager;
  private Optional<IRuleManager> ruleManager;
  private Optional<IBookingManager> bookingManager;
  private Optional<IPageManager> pageManager;

  public PutDeleteBookingRuleOrExclusionLambda() {
    backupManager = Optional.empty();
    ruleManager = Optional.empty();
    bookingManager = Optional.empty();
    pageManager = Optional.empty();
  }

  /**
   * Returns the {@link squash.booking.lambdas.core.IRuleManager}.
   */
  protected IRuleManager getRuleManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!ruleManager.isPresent()) {
      ruleManager = Optional.of(new RuleManager());
      ruleManager.get().initialise(getBookingManager(logger), logger);
    }
    return ruleManager.get();
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
   * Returns the current London local date.
   */
  protected LocalDate getCurrentLocalDate() {
    // Use a getter here so unit tests can substitute a different date.

    // This gets the correct local date no matter what the user's device
    // system time may say it is, and no matter where in AWS we run.
    return BookingsUtilities.getCurrentLocalDate();
  }

  /**
   * Returns all dates for which bookings can currently be made.
   */
  protected List<String> getValidDates() {
    // Use a getter here so unit tests can substitute a different method.

    return new GetValidDatesLambda().getValidDates(new GetValidDatesLambdaRequest()).getDates();
  }

  /**
   * Creates or deletes a booking rule or exclusion.
   * 
   * @param request with details of the rule or exclusion being mutated.
   * @return response.
   * @throws AmazonServiceException when the method fails owing to an error in an AWS service.
   * @throws AmazonClientException when the method fails owing to a client error.
   * @throws Exception when the method fails for some other reason.
   */
  public PutDeleteBookingRuleOrExclusionLambdaResponse createOrDeleteBookingRuleOrExclusion(
      PutDeleteBookingRuleOrExclusionLambdaRequest request, Context context) throws Exception {
    LambdaLogger logger = context.getLogger();
    try {
      logger.log("CreateOrDelete booking rule or exclusion for request: " + request.toString());

      logger.log("About to validate booking parameters");
      Booking booking = request.getBookingRule().getBooking();
      validateBookingParameters(booking, request.getCognitoIdentityPoolId(),
          request.getCognitoAuthenticationType(), logger);
      logger.log("Validated booking parameters");

      String putOrDelete = request.getPutOrDelete();
      if (putOrDelete.equals("PUT")) {
        if (request.getDateToExclude().equals("")) {
          logger.log("PUT booking rule request so calling createBookingRule");
          return createBookingRule(request, context);
        }
        logger.log("PUT booking rule exclusion request so calling createBookingRuleExclusion");
        return createBookingRuleExclusion(request, context);
      } else if (putOrDelete.equals("DELETE")) {
        if (request.getDateToExclude().equals("")) {
          logger.log("DELETE booking rule request so calling deleteBookingRule");
          return deleteBookingRule(request, context);
        }
        logger.log("DELETE booking rule exclusion request so calling deleteBookingRuleExclusion");
        return deleteBookingRuleExclusion(request, context);
      } else {
        logger.log("Unrecognised booking rule or exclusion request type so throwing: "
            + putOrDelete);
        throw new Exception("Unrecognised booking rule or exclusion request type: " + putOrDelete);
      }
    } catch (AmazonServiceException ase) {
      ExceptionUtils.logAmazonServiceException(ase, logger);
      throw new Exception("Apologies - something has gone wrong. Please try again.", ase);
    } catch (AmazonClientException ace) {
      ExceptionUtils.logAmazonClientException(ace, logger);
      throw new Exception("Apologies - something has gone wrong. Please try again.", ace);
    } catch (InterruptedException e) {
      logger.log("Caught interrupted exception: " + e.getMessage());
      throw new Exception("Apologies - something has gone wrong. Please try again.", e);
    } catch (Exception e) {
      switch (e.getMessage()) {
      case "Booking rule creation failed":
        throw new Exception("Booking rule creation failed. Please try again.", e);
      case "Booking rule deletion failed":
        throw new Exception("Booking rule deletion failed. Please try again.", e);
      case "Database put failed - too many attributes":
        throw new Exception("Booking rule addition failed - too many rules. Please try again.", e);
      case "Booking rule exclusion addition failed - too many exclusions":
        throw new Exception(
            "Booking rule exclusion addition failed - too many exclusions. Please try again.", e);
      case "Booking rule creation failed - rule would clash":
        throw new Exception(
            "Booking rule creation failed - new rule would clash. Please try again.", e);
      case "Booking rule exclusion deletion failed - latent clash exists":
        throw new Exception(
            "Booking rule exclusion deletion failed - latent clash exists. Please try again.", e);
      case "Attempting to mutate a booking rule without authenticated credentials from the correct Cognito pool":
        throw new Exception("You must login to manage booking rules. Please try again.", e);
      default:
        throw new Exception("Apologies - something has gone wrong. Please try again.", e);
      }
    }
  }

  private PutDeleteBookingRuleOrExclusionLambdaResponse createBookingRule(
      PutDeleteBookingRuleOrExclusionLambdaRequest request, Context context) throws Exception {

    LambdaLogger logger = context.getLogger();
    logger.log("About to create booking rule for request: " + request.toString());
    IRuleManager ruleManager = getRuleManager(logger);
    ruleManager.createRule(request.getBookingRule());
    logger.log("Created booking rule");

    // Backup this booking rule creation
    getBackupManager(logger).backupSingleBookingRule(request.getBookingRule(), true);

    return new PutDeleteBookingRuleOrExclusionLambdaResponse();
  }

  private PutDeleteBookingRuleOrExclusionLambdaResponse deleteBookingRule(
      PutDeleteBookingRuleOrExclusionLambdaRequest request, Context context) throws Exception {

    LambdaLogger logger = context.getLogger();
    logger.log("About to delete booking rule for request: " + request.toString());
    IRuleManager ruleManager = getRuleManager(logger);
    ruleManager.deleteRule(request.getBookingRule());

    // Backup this booking rule deletion
    getBackupManager(logger).backupSingleBookingRule(request.getBookingRule(), false);

    return new PutDeleteBookingRuleOrExclusionLambdaResponse();
  }

  private PutDeleteBookingRuleOrExclusionLambdaResponse createBookingRuleExclusion(
      PutDeleteBookingRuleOrExclusionLambdaRequest request, Context context) throws Exception {

    LambdaLogger logger = context.getLogger();
    logger.log("About to create booking rule exclusion for request: " + request.toString());
    IRuleManager ruleManager = getRuleManager(logger);
    Optional<BookingRule> updatedRule = ruleManager.addRuleExclusion(request.getDateToExclude(),
        request.getBookingRule());
    logger.log("Created booking rule exclusion");

    // Backup this updated booking rule - if a change was necessary
    if (updatedRule.isPresent()) {
      getBackupManager(logger).backupSingleBookingRule(updatedRule.get(), true);
    }

    return new PutDeleteBookingRuleOrExclusionLambdaResponse();
  }

  private PutDeleteBookingRuleOrExclusionLambdaResponse deleteBookingRuleExclusion(
      PutDeleteBookingRuleOrExclusionLambdaRequest request, Context context) throws Exception {

    LambdaLogger logger = context.getLogger();
    logger.log("About to delete booking rule exclusion for request: " + request.toString());
    IRuleManager ruleManager = getRuleManager(logger);
    Optional<BookingRule> updatedRule = ruleManager.deleteRuleExclusion(request.getDateToExclude(),
        request.getBookingRule());
    logger.log("Deleted booking rule exclusion");

    // Backup this updated booking rule - if a change was necessary
    if (updatedRule.isPresent()) {
      getBackupManager(logger).backupSingleBookingRule(updatedRule.get(), true);
    }

    return new PutDeleteBookingRuleOrExclusionLambdaResponse();
  }

  private void validateBookingParameters(Booking booking, String cognitoIdentityPoolId,
      String authenticationType, LambdaLogger logger) throws Exception {

    logger.log("Validating booking parameters");

    String validCognitoIdentityPoolId = getStringProperty("cognitoidentitypoolid", logger);
    if ((!cognitoIdentityPoolId.equals(validCognitoIdentityPoolId))
        || (!authenticationType.equals("authenticated"))) {
      logger
          .log("Attempting to mutate a booking rule without authenticated credentials from the correct Cognito pool");
      logger.log("Cognito pool id used by request: " + cognitoIdentityPoolId);
      logger.log("Correct Cognito pool id: " + validCognitoIdentityPoolId);
      logger.log("Cognito authentication type: " + authenticationType);
      throw new Exception(
          "Attempting to mutate a booking rule without authenticated credentials from the correct Cognito pool");
    }

    int court = booking.getCourt();
    if ((court < 1) || (court > 5)) {
      logger.log("The booking court number is outside the valid range (1-5): "
          + Integer.toString(court));
      throw new Exception("The booking court number is outside the valid range (1-5)");
    }
    if ((booking.getCourtSpan() < 1) || (booking.getCourtSpan() > (6 - court))) {
      logger.log("The booking court span is outside the valid range (1-(6-court)): "
          + Integer.toString(booking.getCourtSpan()));
      throw new Exception("The booking court span is outside the valid range (1-(6-court))");
    }

    int slot = booking.getSlot();
    if ((slot < 1) || (slot > 16)) {
      logger.log("The booking time slot is outside the valid range (1-16): "
          + Integer.toString(slot));
      throw new Exception("The booking time slot is outside the valid range (1-16)");
    }
    if ((booking.getSlotSpan() < 1) || (booking.getSlotSpan() > (17 - slot))) {
      logger.log("The booking time slot span is outside the valid range (1- (17 - slot)): "
          + Integer.toString(booking.getSlotSpan()));
      throw new Exception("The booking time slot span is outside the valid range (1- (17 - slot))");
    }

    // Verify name length is within allowed range
    if ((booking.getName().length() == 0) || (booking.getName().length() > 30)) {
      logger
          .log("The booking name length is outside the valid range (1- 30): " + booking.getName());
      throw new Exception("The booking name length is outside the valid range (1- 30)");
    }

    // Verify date is a valid date.
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    sdf.setLenient(false);
    try {
      sdf.parse(booking.getDate());

    } catch (ParseException e) {
      logger.log("The booking date has an invalid format: " + booking.getDate());
      throw new Exception("The booking date has an invalid format");
    }
  }

  /**
   * Returns a named property from the SquashCustomResource settings file.
   */
  protected String getStringProperty(String propertyName, LambdaLogger logger) throws IOException {
    // Use a getter here so unit tests can substitute a mock value.
    // We get the value from a settings file so that
    // CloudFormation can substitute the actual value when the
    // stack is created, by replacing the settings file.

    Properties properties = new Properties();
    try (InputStream stream = BookingManager.class
        .getResourceAsStream("/squash/booking/lambdas/SquashCustomResource.settings")) {
      properties.load(stream);
    } catch (IOException e) {
      logger.log("Exception caught reading SquashCustomResource.settings properties file: "
          + e.getMessage());
      throw e;
    }
    return properties.getProperty(propertyName);
  }
}