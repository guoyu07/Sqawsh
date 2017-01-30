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
import squash.booking.lambdas.core.BookingsUtilities;
import squash.booking.lambdas.core.IBackupManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.ILifecycleManager;
import squash.booking.lambdas.core.IPageManager;
import squash.booking.lambdas.core.IRuleManager;
import squash.booking.lambdas.core.LifecycleManager;
import squash.booking.lambdas.core.PageManager;
import squash.booking.lambdas.core.RuleManager;
import squash.deployment.lambdas.utils.ExceptionUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * AWS Lambda function to create or delete a court booking.
 * 
 * <p>This is usually invoked by AWS Lambda.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PutDeleteBookingLambda {

  private Optional<IBackupManager> backupManager;
  private Optional<ILifecycleManager> lifecycleManager;
  private Optional<IRuleManager> ruleManager;
  private Optional<IBookingManager> bookingManager;
  private Optional<IPageManager> pageManager;

  public PutDeleteBookingLambda() {
    backupManager = Optional.empty();
    lifecycleManager = Optional.empty();
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
   * Returns the {@link squash.booking.lambdas.core.IPageManager}.
   */
  protected IPageManager getPageManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!pageManager.isPresent()) {
      pageManager = Optional.of(new PageManager());
      pageManager.get().initialise(getBookingManager(logger), getLifecycleManager(logger), logger);
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
   * Creates or deletes a court booking.
   * 
   * @param request with details of the booking being mutated.
   * @return response containing the Url to redirect the caller to.
   * @throws AmazonServiceException when the method fails owing to an error in an AWS service.
   * @throws AmazonClientException when the method fails owing to a client error.
   * @throws Exception when the method fails for some other reason.
   */
  public PutDeleteBookingLambdaResponse createOrDeleteBooking(
      PutDeleteBookingLambdaRequest request, Context context) throws Exception {
    LambdaLogger logger = context.getLogger();
    String redirectUrl = request.redirectUrl;
    try {
      logger.log("CreateOrDelete booking for request: " + request.toString());

      // Early-out if this is a prewarming call
      if (request.slot.equals("-1")) {
        // Prewarm call
        logger.log("Early-outing as this is a prewarmer call");
        return new PutDeleteBookingLambdaResponse();
      }

      logger.log("ApiGateway request Id: " + request.getRequestId());

      logger.log("About to validate booking parameters");
      Booking booking = convertBookingRequest(request);
      String password = request.getPassword();
      String apiGatewayBaseUrl = request.getApiGatewayBaseUrl();
      checkAuthenticationAndDate(booking, request.getCognitoIdentityPoolId(),
          request.getCognitoAuthenticationType(), password, logger);
      IBookingManager bookingManager = getBookingManager(logger);
      bookingManager.validateBooking(booking);
      logger.log("Validated booking parameters");

      String putOrDelete = request.getPutOrDelete();
      if (putOrDelete.equals("PUT")) {
        logger.log("PUT booking request so calling createBooking");
        return createBooking(request, booking, apiGatewayBaseUrl, context);
      } else if (putOrDelete.equals("DELETE")) {
        logger.log("DELETE booking request so calling deleteBooking");
        return deleteBooking(request, booking, apiGatewayBaseUrl, context);
      } else {
        logger.log("Unrecognised booking request type so throwing: " + putOrDelete);
        throw new Exception("Unrecognised booking request type: " + putOrDelete);
      }
    } catch (AmazonServiceException ase) {
      ExceptionUtils.logAmazonServiceException(ase, logger);
      throw new Exception("Apologies - something has gone wrong. Please try again." + redirectUrl,
          ase);
    } catch (AmazonClientException ace) {
      ExceptionUtils.logAmazonClientException(ace, logger);
      throw new Exception("Apologies - something has gone wrong. Please try again." + redirectUrl,
          ace);
    } catch (InterruptedException e) {
      logger.log("Caught interrupted exception: " + e.getMessage());
      throw new Exception("Apologies - something has gone wrong. Please try again." + redirectUrl,
          e);
    } catch (Exception e) {
      if ((e.getMessage() != null)
          && e.getMessage()
              .contains(
                  "Cannot access bookings or rules - there is an updated version of the booking service.")
          && !e.getMessage().contains("UrlNotPresent")) {
        // In case where service is RETIRED and we have a valid forwarding url,
        // use that url instead of our generic redirectUrl. If we have no valid
        // forwarding url (which should never happen), we fall through to
        // showing the user a general error message.
        throw new Exception(e.getMessage().replace(" Forwarding Url:", ""), e);
      }

      switch (e.getMessage()) {
      // For now, we add the request.redirectUrl to the message. ApiGateway will
      // parse this out.
      case "The booking court number is outside the valid range (1-5)":
        throw new Exception(
            "The booking court number is outside the valid range (1-5). Please try again."
                + redirectUrl, e);
      case "The booking court span is outside the valid range (1-(6-court))":
        throw new Exception(
            "The booking court span is outside the valid range (1-(6-court)). Please try again."
                + redirectUrl, e);
      case "The booking time slot is outside the valid range (1-16)":
        throw new Exception(
            "The booking time slot is outside the valid range (1-16). Please try again."
                + redirectUrl, e);
      case "The booking time slot span is outside the valid range (1- (17 - slot))":
        throw new Exception(
            "The booking time slot span is outside the valid range (1- (17 - slot)). Please try again."
                + redirectUrl, e);
      case "The booking name must have a valid format":
        throw new Exception(
            "The booking name must have a valid format e.g. J.Power/A.Shabana. Please try again."
                + redirectUrl, e);
      case "The booking date is outside the valid range":
        throw new Exception("The booking date is outside the valid range. Please try again."
            + redirectUrl, e);
      case "The password is incorrect":
        throw new Exception("The password is incorrect. Please try again." + redirectUrl, e);
      case "Attempting to mutate a block booking without authenticated credentials from the correct Cognito pool":
        throw new Exception("You must login to manage block bookings. Please try again."
            + redirectUrl, e);
      case "Booking creation failed":
        throw new Exception("Booking creation failed. Please try again." + redirectUrl, e);
      case "Booking deletion failed":
        throw new Exception("Booking cancellation failed. Please try again." + redirectUrl, e);
      case "Cannot mutate bookings or rules - booking service is temporarily readonly whilst site maintenance is in progress":
        throw new Exception(
            "Cannot mutate bookings or rules - booking service is temporarily readonly whilst site maintenance is in progress."
                + redirectUrl, e);
      default:
        throw new Exception(
            "Apologies - something has gone wrong. Please try again." + redirectUrl, e);
      }
    }
  }

  private PutDeleteBookingLambdaResponse createBooking(PutDeleteBookingLambdaRequest request,
      Booking booking, String apiGatewayBaseUrl, Context context) throws Exception {

    LambdaLogger logger = context.getLogger();
    logger.log("About to create booking for request: " + request.toString());
    IBookingManager bookingManager = getBookingManager(logger);
    List<Booking> bookings = bookingManager.createBooking(booking, true);
    logger.log("Created booking");

    // We've created the booking - so update the corresponding booking page
    logger.log("About to refresh booking page in S3 with new booking");
    IPageManager pageManager = getPageManager(logger);
    String pageUidSuffix = pageManager.refreshPage(booking.getDate(), getValidDates(),
        apiGatewayBaseUrl, true, bookings, getEnvironmentVariable("RevvingSuffix", logger));
    logger.log("Refreshed booking page in S3 with new booking");

    // Backup this booking
    getBackupManager(logger).backupSingleBooking(booking, true);

    // We redirect to the uid-suffixed booking page to ensure ReadAfterWrite
    // consistency
    PutDeleteBookingLambdaResponse response = new PutDeleteBookingLambdaResponse();
    response
        .setRedirectUrl(request.getRedirectUrl().replaceAll("\\.html", pageUidSuffix + ".html"));

    return response;
  }

  private PutDeleteBookingLambdaResponse deleteBooking(PutDeleteBookingLambdaRequest request,
      Booking booking, String apiGatewayBaseUrl, Context context) throws Exception {

    LambdaLogger logger = context.getLogger();
    logger.log("About to delete booking for request: " + request.toString());
    IBookingManager bookingManager = getBookingManager(logger);
    List<Booking> bookings = bookingManager.deleteBooking(booking, true);
    logger.log("Deleted booking");

    // We've deleted the booking - so update the corresponding booking page
    logger.log("About to refresh booking page in S3 after deleting booking");
    IPageManager pageManager = getPageManager(logger);
    String pageUidSuffix = pageManager.refreshPage(booking.getDate(), getValidDates(),
        apiGatewayBaseUrl, true, bookings, getEnvironmentVariable("RevvingSuffix", logger));
    logger.log("Refreshed booking page in S3 after deleting booking");

    // Backup this booking deletion
    getBackupManager(logger).backupSingleBooking(booking, false);

    PutDeleteBookingLambdaResponse response = new PutDeleteBookingLambdaResponse();
    // We redirect to the suffixed booking page to ensure ReadAfterWrite
    // consistency
    response
        .setRedirectUrl(request.getRedirectUrl().replaceAll("\\.html", pageUidSuffix + ".html"));

    return response;
  }

  private Booking convertBookingRequest(PutDeleteBookingLambdaRequest request) throws Exception {
    Booking booking = new Booking();
    booking.setCourt(Integer.parseInt(request.getCourt()));
    booking.setCourtSpan(Integer.parseInt(request.getCourtSpan()));
    booking.setSlot(Integer.parseInt(request.getSlot()));
    booking.setSlotSpan(Integer.parseInt(request.getSlotSpan()));
    booking.setDate(request.getDate());
    booking.setName(request.getName().trim());

    return booking;
  }

  private void checkAuthenticationAndDate(Booking booking, String cognitoIdentityPoolId,
      String authenticationType, String password, LambdaLogger logger) throws Exception {

    logger.log("Checking authentication and date");

    // Check user is authenticated with the correct pool if a block booking is
    // being mutated.
    if ((booking.getCourtSpan() > 1) || (booking.getSlotSpan() > 1)) {
      String validCognitoIdentityPoolId = getEnvironmentVariable("CognitoIdentityPoolId", logger);
      if ((!cognitoIdentityPoolId.equals(validCognitoIdentityPoolId))
          || (!authenticationType.equals("authenticated"))) {
        logger
            .log("Attempting to mutate a block booking without authenticated credentials from the correct Cognito pool");
        logger.log("Cognito pool id used by request: " + cognitoIdentityPoolId);
        logger.log("Correct Cognito pool id: " + validCognitoIdentityPoolId);
        logger.log("Cognito authentication type: " + authenticationType);
        throw new Exception(
            "Attempting to mutate a block booking without authenticated credentials from the correct Cognito pool");
      }
    }

    // Verify password is correct
    if (!password.equals("pAssw0rd")) {
      logger.log("The password is incorrect");
      throw new Exception("The password is incorrect");
    }

    // Verify date is valid
    List<String> validDatesList = getValidDates();
    if (!validDatesList.contains(booking.getDate())) {
      logger.log("The booking date is outside the valid range");
      throw new Exception("The booking date is outside the valid range");
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