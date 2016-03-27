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

package squash.deployment.lambdas;

import squash.deployment.lambdas.utils.CloudFormationResponder;
import squash.deployment.lambdas.utils.ExceptionUtils;
import squash.deployment.lambdas.utils.LambdaInputLogger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.CreateLogGroupRequest;
import com.amazonaws.services.logs.model.PutRetentionPolicyRequest;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Manages the AWS Cloudformation CloudwatchLogs custom resource.
 * 
 * <p>All logging by stack resources is to CloudwatchLogs log groups.
 *    The Cloudformation custom resource backed by this lambda function
 *    reduces the log retention period on those log groups to 3 days (
 *    from the default perpetual retention), so as to reduce costs.
 *    
 * <p>The logs are deliberately not explicitly deleted on stack deletion.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class LogGroupsCustomResourceLambda implements RequestHandler<Map<String, Object>, Object> {

  /**
   * Returns a CloudwatchLogs service client.
   * 
   * <p>This method is provided so unit tests can mock out CloudwatchLogs.
   */
  public AWSLogs getAWSLogsClient(String region) {
    // Use a getter here so unit tests can substitute a mock client
    AWSLogs client = new AWSLogsClient();
    client.setRegion(Region.getRegion(Regions.fromName(region)));
    return client;
  }

  /**
   * Implementation for the AWS Lambda function backing the CloudwatchLogs resource.
   * 
   * <p>This lambda has the following keys in its request map (in addition
   *    to the standard ones) provided via the Cloudformation stack template:
   * 
   * <p>Keys suppling names of other AWS lambda functions that log to CloudwatchLogs:
   * <ul>
   *    <li>ValidDatesLambdaName.</li>
   *    <li>BookingsGETLambdaName.</li>
   *    <li>BookingsPUTDELETELambdaName.</li>
   *    <li>SquashApiGatewayCustomResourceLambdaName.</li>
   *    <li>SquashSettingsCustomResourceLambdaName.</li>
   *    <li>SquashBookingsHtmlCustomResourceLambdaName.</li>
   *    <li>SquashCognitoCustomResourceLambdaName.</li>
   *    <li>SquashScheduledCloudwatchEventCustomResourceLambdaName.</li>
   *    <li>UpdateBookingsLambdaName.</li>
   *    <li>DatabaseBackupLambdaName.</li>
   * </ul>
   * 
   * <p>Other keys:
   * <ul>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   *    <li>Revision - integer incremented to force stack updates to update this resource.</li>
   * </ul>
   *
   * @param request request parameters as provided by the CloudFormation service
   * @param context context as provided by the CloudFormation service
   */
  @Override
  public Object handleRequest(Map<String, Object> request, Context context) {

    LambdaLogger logger = context.getLogger();
    logger.log("Starting AWS Logs custom resource handleRequest");

    // Handle standard request parameters
    Map<String, String> standardRequestParameters = LambdaInputLogger.logStandardRequestParameters(
        request, logger);
    String requestType = standardRequestParameters.get("RequestType");

    // Handle custom request parameters
    logger.log("Logging custom input parameters to custom resource request");
    @SuppressWarnings("unchecked")
    Map<String, Object> resourceProps = (Map<String, Object>) request.get("ResourceProperties");
    String validDatesLambdaName = (String) resourceProps.get("ValidDatesLambdaName");
    String bookingsGETLambdaName = (String) resourceProps.get("BookingsGETLambdaName");
    String bookingsPUTDELETELambdaName = (String) resourceProps.get("BookingsPUTDELETELambdaName");
    String squashApiGatewayCustomResourceLambdaName = (String) resourceProps
        .get("SquashApiGatewayCustomResourceLambdaName");
    String squashSettingsCustomResourceLambdaName = (String) resourceProps
        .get("SquashSettingsCustomResourceLambdaName");
    String squashBookingsHtmlCustomResourceLambdaName = (String) resourceProps
        .get("SquashBookingsHtmlCustomResourceLambdaName");
    String squashCognitoCustomResourceLambdaName = (String) resourceProps
        .get("SquashCognitoCustomResourceLambdaName");
    String squashScheduledCloudwatchEventCustomResourceLambdaName = (String) resourceProps
        .get("SquashScheduledCloudwatchEventCustomResourceLambdaName");
    String updateBookingsLambdaName = (String) resourceProps.get("UpdateBookingsLambdaName");
    String databaseBackupLambdaName = (String) resourceProps.get("DatabaseBackupLambdaName");
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");

    // Log out our custom request parameters
    logger.log("ValidDatesLambdaName: " + validDatesLambdaName);
    logger.log("BookingsGETLambdaName: " + bookingsGETLambdaName);
    logger.log("BookingsPUTDELETELambdaName: " + bookingsPUTDELETELambdaName);
    logger.log("SquashApiGatewayCustomResourceLambdaName: "
        + squashApiGatewayCustomResourceLambdaName);
    logger.log("SquashSettingsCustomResourceLambdaName: " + squashSettingsCustomResourceLambdaName);
    logger.log("SquashBookingsHtmlCustomResourceLambdaName: "
        + squashBookingsHtmlCustomResourceLambdaName);
    logger.log("SquashCognitoCustomResourceLambdaName: " + squashCognitoCustomResourceLambdaName);
    logger.log("SquashScheduledCloudwatchEventCustomResourceLambdaName: "
        + squashScheduledCloudwatchEventCustomResourceLambdaName);
    logger.log("UpdateBookingsLambdaName: " + updateBookingsLambdaName);
    logger.log("DatabaseBackupLambdaName: " + databaseBackupLambdaName);
    logger.log("Region: " + region);
    logger.log("Revision: " + revision);

    // Prepare our response to be sent in the finally block
    CloudFormationResponder cloudFormationResponder = new CloudFormationResponder(
        standardRequestParameters, "DummyPhysicalResourceId");
    // Initialise failure response, which will be changed on success
    String responseStatus = "FAILED";

    try {
      AWSLogs client = getAWSLogsClient(region);

      if (requestType.equals("Create")) {

        // Create and set the specified retention period on other lambdas' log
        // groups (these lambdas will not have run and created their log groups
        // yet)
        List<String> lambdaNames = new ArrayList<>(Arrays.asList(validDatesLambdaName,
            bookingsGETLambdaName, bookingsPUTDELETELambdaName, updateBookingsLambdaName,
            databaseBackupLambdaName));

        for (String lambdaName : lambdaNames) {
          logger.log("Creating log group: " + lambdaName);
          String logGroupName = "/aws/lambda/" + lambdaName;
          CreateLogGroupRequest createLogGroupRequest = new CreateLogGroupRequest(logGroupName);
          try {
            client.createLogGroup(createLogGroupRequest);
          } catch (AmazonServiceException e) {
            if (!lambdaName.equals(bookingsPUTDELETELambdaName)) {
              // Rethrow unless it's PUTDELETE - this group can exist if the
              // prewarmer has managed to run before this point in the stack
              // creation.
              throw e;
            }
          }
        }

        // Add in the lambdas which have already run and created their log
        // groups
        lambdaNames.add(squashApiGatewayCustomResourceLambdaName);
        lambdaNames.add(squashSettingsCustomResourceLambdaName);
        lambdaNames.add(squashCognitoCustomResourceLambdaName);
        lambdaNames.add(squashBookingsHtmlCustomResourceLambdaName);
        lambdaNames.add(squashScheduledCloudwatchEventCustomResourceLambdaName);
        for (String lambdaName : lambdaNames) {
          logger.log("Setting log retention period for log group: " + lambdaName);
          String logGroupName = "/aws/lambda/" + lambdaName;
          PutRetentionPolicyRequest putRetentionPolicyRequest = new PutRetentionPolicyRequest(
              logGroupName, 3);
          client.putRetentionPolicy(putRetentionPolicyRequest);
        }

        // Set the specified retention period on this log group lambda
        logger.log("Setting log stream custom resource lambda log retention period");
        PutRetentionPolicyRequest putRetentionPolicyRequest = new PutRetentionPolicyRequest(
            context.getLogGroupName(), 3);
        client.putRetentionPolicy(putRetentionPolicyRequest);
      }
      // Do not handle Delete or Updates for now (we need to keep the logs
      // on stack deletion so we can see what went wrong).

      responseStatus = "SUCCESS";
      return null;
    } catch (AmazonServiceException ase) {
      ExceptionUtils.logAmazonServiceException(ase, logger);
      return null;
    } catch (AmazonClientException ace) {
      ExceptionUtils.logAmazonClientException(ace, logger);
      return null;
    } catch (Exception e) {
      logger.log("Exception caught in LogGroups Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      // Prepare a memory stream to append error messages to
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteArrayOutputStream);
      JSONObject outputs;
      try {
        outputs = new JSONObject().put("Result", "Hello from LogGroups!!!");
      } catch (JSONException e) {
        e.printStackTrace(printStream);
        // Can do nothing more than log the error and return. Must rely on
        // CloudFormation timing-out since it won't get a response from us.
        logger.log("Exception caught whilst constructing outputs: "
            + byteArrayOutputStream.toString() + ". Message: " + e.getMessage());
        return null;
      }
      cloudFormationResponder.sendResponse(responseStatus, outputs, logger);
    }
  }
}