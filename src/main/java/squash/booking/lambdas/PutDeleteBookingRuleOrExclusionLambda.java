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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

      logger.log("ApiGateway request Id: " + request.getRequestId());

      logger.log("About to validate booking parameters");
      checkAuthenticationAndDates(request.getBookingRule(), request.getCognitoIdentityPoolId(),
          request.getCognitoAuthenticationType(), logger);
      IBookingManager bookingManager = getBookingManager(logger);
      bookingManager.validateBooking(request.getBookingRule().getBooking());
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

  private void checkAuthenticationAndDates(BookingRule bookingRule, String cognitoIdentityPoolId,
      String authenticationType, LambdaLogger logger) throws Exception {

    logger.log("Checking authentication and dates");

    String validCognitoIdentityPoolId = getEnvironmentVariable("CognitoIdentityPoolId", logger);
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

    // Verify dates are valid dates.
    List<String> datesToCheck = new ArrayList<>();
    datesToCheck.add(bookingRule.getBooking().getDate());
    Arrays.stream(bookingRule.getDatesToExclude()).forEach(
        (dateToExclude) -> datesToCheck.add(dateToExclude));

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    sdf.setLenient(false);
    if (datesToCheck.stream().filter((dateToCheck) -> {
      try {
        sdf.parse(dateToCheck);
      } catch (ParseException e) {
        logger.log("The date has an invalid format: " + dateToCheck);
        return true;
      }
      return false;
    }).count() > 0) {
      throw new Exception("One of the booking rule dates has an invalid format");
    }
  }

  /**
   * Returns a named environment variable.
   * @throws Exception 
   */
  protected String getEnvironmentVariable(String variableName, LambdaLogger logger)
      throws Exception {
    // Use a getter here so unit tests can substitute a mock value.
    // We get the value from an environment variable so that CloudFormation can
    // set the actual value when the stack is created.

    String environmentVariable = System.getenv(variableName);
    if (environmentVariable == null) {
      logger.log("Environment variable: " + variableName + " is not defined, so throwing.");
      throw new Exception("Environment variable: " + variableName + " should be defined.");
    }
    return environmentVariable;
  }
}