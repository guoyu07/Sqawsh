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

package squash.deployment.lambdas.utils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Retrieves and logs Cloudformation custom resource request parameters.
 * 
 * <p>All lambda-backed Cloudformation custom resource lambdas use this class
 *    to retrieve and log their standard request parameters received from
 *    Cloudformation.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class LambdaInputLogger {

  /**
   *  Retrieves and logs Cloudformation custom resource request parameters.
   *  
   *  <p>Cloudformation custom resource requests provide their parameters
   *     via a map. Most of these are standard (and present in all requests),
   *     but some are specific to each custom resource. This method retrieves
   *     and logs only the standard parameters.
   * 
   *  @param request the Cloudformation custom resource request.
   *  @param logger a CloudwatchLogs logger.
   *  @return the retrieved Cloudformation standard request parameters.
   */
  public static Map<String, String> logStandardRequestParameters(Map<String, Object> request,
      LambdaLogger logger) {
    logger.log("Logging standard input parameters to custom resource request");

    // Create map of params lambda actually uses
    Map<String, String> standardRequestParameters = new HashMap<>();
    String requestType = (String) request.get("RequestType");
    standardRequestParameters.put("RequestType", requestType);
    String requestId = (String) request.get("RequestId");
    standardRequestParameters.put("RequestId", requestId);
    String stackId = (String) request.get("StackId");
    standardRequestParameters.put("StackId", stackId);
    String logicalResourceId = (String) request.get("LogicalResourceId");
    standardRequestParameters.put("LogicalResourceId", logicalResourceId);
    String physicalResourceId = (String) request.get("PhysicalResourceId");
    if (physicalResourceId != null) {
      // Create RequestTypes do not have the physical id
      standardRequestParameters.put("PhysicalResourceId", physicalResourceId);
    }
    String responseURL = (String) request.get("ResponseURL");
    standardRequestParameters.put("ResponseURL", responseURL);

    // Log out our request parameters
    logger.log("RequestType: " + requestType);
    logger.log("RequestId: " + requestId);
    logger.log("StackId: " + stackId);
    logger.log("LogicalResourceId: " + logicalResourceId);
    if (physicalResourceId != null) {
      logger.log("PhysicalResourceId: " + physicalResourceId);
    }
    logger.log("ResponseURL: " + responseURL);

    return standardRequestParameters;
  }
}