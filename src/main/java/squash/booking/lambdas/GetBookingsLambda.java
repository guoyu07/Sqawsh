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

import squash.booking.lambdas.core.BookingManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.ILifecycleManager;
import squash.booking.lambdas.core.LifecycleManager;
import squash.deployment.lambdas.utils.ExceptionUtils;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Optional;

/**
 * AWS Lambda function returning all bookings for a specified date.
 * 
 * <p>This is usually invoked by AWS Lambda.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class GetBookingsLambda {
  private Optional<IBookingManager> bookingManager;
  private Optional<ILifecycleManager> lifecycleManager;

  /**
   *  Constructor.
   */
  public GetBookingsLambda() {
    bookingManager = Optional.empty();
    lifecycleManager = Optional.empty();
  }

  /**
   *  Returns all dates for which bookings can currently be made.
   *  
   *  @return all dates on which bookings can be made, in YYYY-MM-DD format.
   */
  protected List<String> getValidDates() {
    // Use a getter here so unit tests can substitute a different method.

    return new GetValidDatesLambda().getValidDates(new GetValidDatesLambdaRequest()).getDates();
  }

  /**
   *  Returns the {@link squash.booking.lambdas.core.BookingManager}.
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
   * Returns all bookings for the specified date.
   * 
   * @param request specifying, e.g., the date for which to return bookings.
   * @param context provided by AWS Lambda.
   * @return response containing the bookings.
   * @throws AmazonServiceException when the method fails owing to an error in an AWS service.
   * @throws AmazonClientException when the method fails owing to a client error.
   * @throws Exception when the method fails for some other reason.
   */
  public GetBookingsLambdaResponse getBookings(GetBookingsLambdaRequest request, Context context)
      throws Exception {

    LambdaLogger logger = context.getLogger();
    String redirectUrl = request.getRedirectUrl();
    try {
      logger.log("ApiGateway request Id: " + request.getRequestId());

      // Validate the date that bookings are being requested for
      String requestedDate = request.getDate();
      logger.log("About to get bookings for date: " + requestedDate
          + ". Checking if date is valid...");
      if (!getValidDates().contains(requestedDate)) {
        logger.log("Date is not valid");
        throw new InvalidParameterException("The booking date is outside the valid range");
      }
      logger.log("Date is valid");

      // Query for bookings (if any) for the requested day
      logger.log("About to call booking manager to get bookings");
      GetBookingsLambdaResponse response = new GetBookingsLambdaResponse();
      IBookingManager bookingManager = getBookingManager(logger);
      response.setBookings(bookingManager.getBookings(requestedDate, true));
      response.setDate(request.getDate());
      ILifecycleManager lifecycleManager = getLifecycleManager(logger);
      ImmutablePair<ILifecycleManager.LifecycleState, Optional<String>> lifecycleState = lifecycleManager
          .getLifecycleState();
      response.setLifecycleState(lifecycleState.left.name());
      // N.B. getBookings will have thrown already if state is RETIRED, but
      // fill in the url anyway.
      response.setForwardingUrl(lifecycleState.right.orElse(""));

      logger.log("Called booking manager to get bookings");
      return response;
    } catch (AmazonServiceException ase) {
      ExceptionUtils.logAmazonServiceException(ase, logger);
      throw new Exception("Apologies - something has gone wrong. Please try again." + redirectUrl,
          ase);
    } catch (AmazonClientException ace) {
      ExceptionUtils.logAmazonClientException(ace, logger);
      throw new Exception("Apologies - something has gone wrong. Please try again." + redirectUrl,
          ace);
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
      case "The booking date is outside the valid range":
        throw new Exception("The booking date is outside the valid range. Please try again."
            + redirectUrl, e);
      default:
        throw new Exception(
            "Apologies - something has gone wrong. Please try again." + redirectUrl, e);
      }
    }
  }
}