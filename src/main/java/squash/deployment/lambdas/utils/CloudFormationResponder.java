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
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Sends response to AWS Cloudformation from custom resources.
 * 
 * <p>All lambda-backed Cloudformation custom resource lambdas use this class
 *    to send their response to Cloudformation after completing their work
 *    to create, update, or delete the custom resource.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class CloudFormationResponder {

  private Map<String, String> requestParameters;
  private String physicalResourceId;

  /**
   *  Constructs the responder.
   *  
   *  <p>The request parameters provide, inter alia, the presigned
   *     Url to which to send the response.
   *  
   *  @param requestParameters the parameters provided by Cloudformation in its request.
   *  @param physicalResourceId the physical id of the custom resource.
   */
  public CloudFormationResponder(Map<String, String> requestParameters, String physicalResourceId) {
    this.requestParameters = requestParameters;
    this.physicalResourceId = physicalResourceId;
  }

  public void setPhysicalResourceId(String physicalResourceId) {
    this.physicalResourceId = physicalResourceId;
  }

  /**
   *  Sends the custom resource response to the Cloudformation service.
   *  
   *  <p>The response is returned indirectly to Cloudformation via the
   *     presigned Url it provided in its request.
   *  
   *  @param status whether the call succeeded - must be either SUCCESS or FAILED.
   *  @param outputs any outputs the custom resource returns to Cloudformation
   *  @param logger a CloudwatchLogs logger.
   */
  public void sendResponse(String status, JSONObject outputs, LambdaLogger logger) {
    // Prepare a memory stream to append error messages to
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(byteArrayOutputStream);

    // Construct the response body
    JSONObject cloudFormationJsonResponse = new JSONObject();
    try {
      cloudFormationJsonResponse.put("Status", status);
      cloudFormationJsonResponse.put("RequestId", requestParameters.get("RequestId"));
      cloudFormationJsonResponse.put("StackId", requestParameters.get("StackId"));
      cloudFormationJsonResponse.put("LogicalResourceId",
          requestParameters.get("LogicalResourceId"));
      cloudFormationJsonResponse.put("PhysicalResourceId", physicalResourceId);
      cloudFormationJsonResponse.put("Data", outputs);
    } catch (JSONException e) {
      e.printStackTrace(printStream);
      // Can do nothing more than log the error and return. Must rely on
      // CloudFormation timing-out since it won't get a response from us.
      logger.log("Exception caught whilst constructing response: "
          + byteArrayOutputStream.toString());
      return;
    }

    // Send the response to CloudFormation via the provided presigned S3 URL
    logger.log("About to send response to presigned URL: " + requestParameters.get("ResponseURL"));
    try {
      URL url = new URL(requestParameters.get("ResponseURL"));
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("PUT");
      OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
      try {
        cloudFormationJsonResponse.put("Status", status);
      } catch (JSONException e) {
        e.printStackTrace(printStream);
        // Can do nothing more than log the error and return. Must rely on
        // CloudFormation timing-out since it won't get a response from us.
        logger.log("Exception caught whilst constructing response: "
            + byteArrayOutputStream.toString());
        return;
      }
      logger.log("Response about to be sent: " + cloudFormationJsonResponse.toString(2));
      out.write(cloudFormationJsonResponse.toString());
      out.close();
      logger.log("Sent response to presigned URL");
      int responseCode = connection.getResponseCode();
      logger.log("Response Code returned from presigned URL: " + responseCode);
    } catch (IOException | JSONException e) {
      e.printStackTrace(printStream);
      // Can do nothing more than log the error and return.
      logger.log("Exception caught whilst replying to presigned URL: "
          + byteArrayOutputStream.toString());
      return;
    }
  }
}