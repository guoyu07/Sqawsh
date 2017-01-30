/**
 * Copyright 2017 Robin Steel
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
import squash.booking.lambdas.core.ILifecycleManager.LifecycleState;
import squash.booking.lambdas.core.IPageManager;
import squash.booking.lambdas.core.LifecycleManager;
import squash.booking.lambdas.core.PageManager;

import org.owasp.encoder.Encode;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.List;
import java.util.Optional;

/**
 * AWS Lambda function to update the lifecycle state of the booking service.
 * 
 * This is usually invoked from AWS Lambda console to manage the process
 * of upgrading to a new version of the bookings service - or reverting
 * back to an old version if the upgrade has problems.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class UpdateLifecycleStateLambda {

  private Optional<IBookingManager> bookingManager;
  private Optional<ILifecycleManager> lifecycleManager;
  private Optional<IPageManager> pageManager;

  public UpdateLifecycleStateLambda() {
    bookingManager = Optional.empty();
    lifecycleManager = Optional.empty();
    pageManager = Optional.empty();
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
   *  Returns the dates for which bookings can be made, in YYYY-MM-DD format.
   */
  protected List<String> getValidDates() {
    // Use a getter here so unit tests can substitute a different method.

    return new GetValidDatesLambda().getValidDates(new GetValidDatesLambdaRequest()).getDates();
  }

  /**
   * Updates the lifecycle state of the booking service.
   * 
   * <p>Updates the lifecycle state by:
   * <ul>
   *    <li>updating the booking service lifecycle state</li>
   *    <li>refreshing all booking pages and bookings json to account for the new lifecycle state</li>
   * </ul>
   *
   * @param request with details of the new lifecycle state.
   * @throws Exception when the method fails.
   */
  public UpdateLifecycleStateLambdaResponse updateLifecycleState(
      UpdateLifecycleStateLambdaRequest request, Context context) throws Exception {
    LambdaLogger logger = context.getLogger();

    try {
      logger.log("About to update lifecycle state to: " + request.getLifecycleState());
      LifecycleState lifecycleState = Enum.valueOf(LifecycleState.class,
          request.getLifecycleState());
      Optional<String> forwardingUrl = request.getForwardingUrl() == null ? Optional.empty()
          : Optional.of(request.getForwardingUrl());
      if (forwardingUrl.isPresent()
          && !Encode.forHtmlContent(forwardingUrl.get()).equals(forwardingUrl.get())) {
        // Check url ok as-is for e.g. XSS safety
        logger.log("The forwarding url must pass OWASP checks");
        throw new Exception("The forwarding url must pass OWASP checks");
      }
      getLifecycleManager(logger).setLifecycleState(lifecycleState, forwardingUrl);
      logger.log("Updated lifecycle state");

      logger.log("About to refresh all bookings pages and bookings json");
      IPageManager pageManager = getPageManager(logger);

      String apiGatewayBaseUrl = getEnvironmentVariable("ApiGatewayBaseUrl", logger);
      logger.log("Using apigatewayBaseUrl: " + apiGatewayBaseUrl);
      String revvingSuffix = getEnvironmentVariable("RevvingSuffix", logger);
      logger.log("Using revvingSuffix: " + revvingSuffix);

      pageManager.refreshAllPages(getValidDates(), apiGatewayBaseUrl, revvingSuffix);
      logger.log("Refreshed all bookings pages and bookings json");

    } catch (Exception e) {
      logger.log("Exception caught in updateLifecycleState Lambda: " + e.getMessage());
      throw new Exception("Apologies - something has gone wrong. Please try again.", e);
    }

    UpdateLifecycleStateLambdaResponse updateLifecycleStateLambdaResponse = new UpdateLifecycleStateLambdaResponse();
    return updateLifecycleStateLambdaResponse;
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