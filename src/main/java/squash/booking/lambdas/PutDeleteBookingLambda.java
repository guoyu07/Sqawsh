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
import squash.booking.lambdas.utils.BookingsUtilities;
import squash.booking.lambdas.utils.IBackupManager;
import squash.booking.lambdas.utils.IBookingManager;
import squash.booking.lambdas.utils.IPageManager;
import squash.booking.lambdas.utils.PageManager;
import squash.deployment.lambdas.utils.ExceptionUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * AWS Lambda function to create or delete a court booking.
 * 
 * <p>This is usually invoked by AWS Lambda.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PutDeleteBookingLambda {

  private Optional<IBackupManager> backupManager;
  private Optional<IBookingManager> bookingManager;
  private Optional<IPageManager> pageManager;

  public PutDeleteBookingLambda() {
    backupManager = Optional.empty();
    bookingManager = Optional.empty();
    pageManager = Optional.empty();
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
   * Returns the {@link squash.booking.lambdas.utils.IPageManager}.
   */
  protected IPageManager getPageManager(LambdaLogger logger) throws Exception {
    // Use a getter here so unit tests can substitute a mock manager
    if (!pageManager.isPresent()) {
      pageManager = Optional.of(new PageManager());
      pageManager.get().Initialise(getBookingManager(logger), logger);
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

      logger.log("About to validate booking parameters");
      Booking booking = convertBookingRequest(request);
      String password = request.getPassword();
      String apiGatewayBaseUrl = request.getApiGatewayBaseUrl();
      validateBookingParameters(booking, password, logger);
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
      case "The players names should have a format like J.Power i.e. Initial.Surname":
        throw new Exception(
            "The players names should have a format like J.Power i.e. Initial.Surname. Please try again."
                + redirectUrl, e);
      case "The booking date is outside the valid range":
        throw new Exception("The booking date is outside the valid range. Please try again."
            + redirectUrl, e);
      case "Names of both players should be given":
        throw new Exception("Names of both players should be given. Please try again."
            + redirectUrl, e);
      case "The password is incorrect":
        throw new Exception("The password is incorrect. Please try again." + redirectUrl, e);
      case "Booking creation failed":
        throw new Exception("Booking creation failed. Please try again." + redirectUrl, e);
      case "Booking deletion failed":
        throw new Exception("Booking cancellation failed. Please try again." + redirectUrl, e);
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
    List<Booking> bookings = bookingManager.createBooking(booking);
    logger.log("Created booking");

    // We've created the booking - so update the corresponding booking page
    logger.log("About to refresh booking page in S3 with new booking");
    IPageManager pageManager = getPageManager(logger);
    String pageUidSuffix = pageManager.refreshPage(booking.getDate(), getValidDates(),
        apiGatewayBaseUrl, true, bookings);
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
    List<Booking> bookings = bookingManager.deleteBooking(booking);
    logger.log("Deleted booking");

    // We've deleted the booking - so update the corresponding booking page
    logger.log("About to refresh booking page in S3 after deleting booking");
    IPageManager pageManager = getPageManager(logger);
    String pageUidSuffix = pageManager.refreshPage(booking.getDate(), getValidDates(),
        apiGatewayBaseUrl, true, bookings);
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
    booking.setPlayer1Name(request.getPlayer1name());
    booking.setPlayer2Name(request.getPlayer2name());
    booking.setPlayers(request.getPlayers());

    return booking;
  }

  private void validateBookingParameters(Booking booking, String password, LambdaLogger logger)
      throws Exception {

    logger.log("Validating booking parameters");

    int court = booking.getCourt();
    if ((court < 1) || (court > 5)) {
      logger.log("The booking court number is outside the valid range (1-5)");
      throw new Exception("The booking court number is outside the valid range (1-5)");
    }
    if ((booking.getCourtSpan() < 1) || (booking.getCourtSpan() > (6 - court))) {
      logger.log("The booking court span is outside the valid range (1-(6-court))");
      throw new Exception("The booking court span is outside the valid range (1-(6-court))");
    }

    int slot = booking.getSlot();
    if ((slot < 1) || (slot > 16)) {
      logger.log("The booking time slot is outside the valid range (1-16)");
      throw new Exception("The booking time slot is outside the valid range (1-16)");
    }
    if ((booking.getSlotSpan() < 1) || (booking.getSlotSpan() > (17 - slot))) {
      logger.log("The booking time slot span is outside the valid range (1- (17 - slot))");
      throw new Exception("The booking time slot span is outside the valid range (1- (17 - slot))");
    }

    // Validate the format of the players' names. Delete requests will have only
    // the combined playersnames set, whilst create requests will have only the
    // players' individual names set.
    String player1Name;
    String player2Name;
    String playersNames = booking.getPlayers();
    if (playersNames != null) {
      // This is a delete request. Split out name of each player, so we can
      // validate it.
      String[] players = playersNames.split("/");
      if (players.length != 2) {
        logger.log("Error: at least one of the players names is absent");
        throw new Exception("Names of both players should be given");
      }
      player1Name = players[0];
      player2Name = players[1];
    } else {
      // Create request - so names will be individually set
      player1Name = booking.getPlayer1Name();
      player2Name = booking.getPlayer2Name();
    }
    if ((player1Name.length() == 0) || (player2Name.length() == 0)) {
      logger.log("Error: one of the players names is absent");
      throw new Exception("Names of both players should be given");
    }
    String players = player1Name + "/" + player2Name;
    booking.setPlayers(players);

    Pattern regex = Pattern.compile("^[a-zA-Z]\\.[a-zA-Z]*$");
    boolean player1Ok = regex.matcher(player1Name).matches();
    boolean player2Ok = regex.matcher(player2Name).matches();
    if (!player1Ok || !player2Ok) {
      logger.log("The players names should have a format like J.Power i.e. Initial.Surname");
      throw new Exception(
          "The players names should have a format like J.Power i.e. Initial.Surname");
    }

    // Verify date is valid
    List<String> validDatesList = getValidDates();
    if (!validDatesList.contains(booking.getDate())) {
      logger.log("The booking date is outside the valid range");
      throw new Exception("The booking date is outside the valid range");
    }

    // Verify password is correct
    if (!password.equals("pAssw0rd")) {
      logger.log("The password is incorrect");
      throw new Exception("The password is incorrect");
    }
  }
}