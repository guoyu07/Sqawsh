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

import squash.booking.lambdas.core.BookingManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.IRuleManager;
import squash.booking.lambdas.core.RuleManager;
import squash.deployment.lambdas.utils.ExceptionUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Optional;

/**
 * AWS Lambda function returning all booking rules.
 * 
 * <p>This is usually invoked by AWS Lambda.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class GetBookingRulesLambda {
  private Optional<IRuleManager> ruleManager;
  private Optional<IBookingManager> bookingManager;

  /**
   *  Constructor.
   */
  public GetBookingRulesLambda() {
    ruleManager = Optional.empty();
    bookingManager = Optional.empty();
  }

  /**
   *  Returns the {@link squash.booking.lambdas.core.RuleManager}.
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
   * Returns all booking rules.
   * 
   * @param request.
   * @param context provided by AWS Lambda.
   * @return response containing the booking rules.
   * @throws AmazonServiceException when the method fails owing to an error in an AWS service.
   * @throws AmazonClientException when the method fails owing to a client error.
   * @throws Exception when the method fails for some other reason.
   */
  public GetBookingRulesLambdaResponse getBookingRules(GetBookingRulesLambdaRequest request,
      Context context) throws Exception {

    LambdaLogger logger = context.getLogger();
    String redirectUrl = request.getRedirectUrl();
    try {
      logger.log("About to get booking rules");

      // Query for booking rules (if any)
      logger.log("About to call rule manager to get booking rules");
      GetBookingRulesLambdaResponse response = new GetBookingRulesLambdaResponse();
      IRuleManager ruleManager = getRuleManager(logger);
      response.setBookingRules(ruleManager.getRules());
      logger.log("Called rule manager to get booking rules");
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
      // FIXME: This will need revisiting once the Angular client supports
      // booking rules, when we'll know which exceptional cases it needs to
      // discriminate.
      throw new Exception("Apologies - something has gone wrong. Please try again." + redirectUrl,
          e);
    }
  }
}