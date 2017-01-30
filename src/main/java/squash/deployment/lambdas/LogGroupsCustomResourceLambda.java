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

package squash.deployment.lambdas;

import squash.deployment.lambdas.utils.CloudFormationResponder;
import squash.deployment.lambdas.utils.ExceptionUtils;
import squash.deployment.lambdas.utils.LambdaInputLogger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.CreateLogGroupRequest;
import com.amazonaws.services.logs.model.PutRetentionPolicyRequest;

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
    AWSLogs client = AWSLogsClientBuilder.standard().withRegion(region).build();
    return client;
  }

  /**
   * Implementation for the AWS Lambda function backing the CloudwatchLogs resource.
   * 
   * <p>This lambda requires the following environment variables:
   * 
   * <p>Variables suppling names of other AWS lambda functions that log to CloudwatchLogs:
   * <ul>
   *    <li>SquashAngularjsAppCustomResourceLambdaName.</li>
   *    <li>ValidDatesLambdaName.</li>
   *    <li>BookingsGETLambdaName.</li>
   *    <li>BookingRulesGETLambdaName.</li>
   *    <li>BookingsPUTDELETELambdaName.</li>
   *    <li>BookingRuleOrExclusionPUTDELETELambdaName.</li>
   *    <li>SquashApiGatewayCustomResourceLambdaName.</li>
   *    <li>SquashSettingsCustomResourceLambdaName.</li>
   *    <li>SquashNoScriptAppCustomResourceLambdaName.</li>
   *    <li>SquashCognitoCustomResourceLambdaName.</li>
   *    <li>SquashScheduledCloudwatchEventCustomResourceLambdaName.</li>
   *    <li>UpdateBookingsLambdaName.</li>
   *    <li>DatabaseBackupLambdaName.</li>
   *    <li>DatabaseRestoreLambdaName.</li>
   * </ul>
   * 
   * <p>Other Variables:
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

    // Handle required environment variables
    logger.log("Logging required environment variables for custom resource request");
    String squashAngularjsAppCustomResourceLambdaName = System
        .getenv("SquashAngularjsAppCustomResourceLambdaName");
    String applyBookingRulesLambdaName = System.getenv("ApplyBookingRulesLambdaName");
    String validDatesLambdaName = System.getenv("ValidDatesLambdaName");
    String bookingsGETLambdaName = System.getenv("BookingsGETLambdaName");
    String bookingRulesGETLambdaName = System.getenv("BookingRulesGETLambdaName");
    String bookingsPUTDELETELambdaName = System.getenv("BookingsPUTDELETELambdaName");
    String bookingRuleOrExclusionPUTDELETELambdaName = System
        .getenv("BookingRuleOrExclusionPUTDELETELambdaName");
    String squashApiGatewayCustomResourceLambdaName = System
        .getenv("SquashApiGatewayCustomResourceLambdaName");
    String squashNoScriptAppCustomResourceLambdaName = System
        .getenv("SquashNoScriptAppCustomResourceLambdaName");
    String squashCognitoCustomResourceLambdaName = System
        .getenv("SquashCognitoCustomResourceLambdaName");
    String squashScheduledCloudwatchEventCustomResourceLambdaName = System
        .getenv("SquashScheduledCloudwatchEventCustomResourceLambdaName");
    String updateBookingsLambdaName = System.getenv("UpdateBookingsLambdaName");
    String databaseBackupLambdaName = System.getenv("DatabaseBackupLambdaName");
    String databaseRestoreLambdaName = System.getenv("DatabaseRestoreLambdaName");
    String region = System.getenv("AWS_REGION");
    String revision = System.getenv("Revision");

    // Log out our required environment variables
    logger.log("SquashAngularjsAppCustomResourceLambdaName: "
        + squashAngularjsAppCustomResourceLambdaName);
    logger.log("ApplyBookingRulesLambdaName: " + applyBookingRulesLambdaName);
    logger.log("ValidDatesLambdaName: " + validDatesLambdaName);
    logger.log("BookingsGETLambdaName: " + bookingsGETLambdaName);
    logger.log("BookingRulesGETLambdaName: " + bookingRulesGETLambdaName);
    logger.log("BookingsPUTDELETELambdaName: " + bookingsPUTDELETELambdaName);
    logger.log("BookingRuleOrExclusionPUTDELETELambdaName: "
        + bookingRuleOrExclusionPUTDELETELambdaName);
    logger.log("SquashApiGatewayCustomResourceLambdaName: "
        + squashApiGatewayCustomResourceLambdaName);
    logger.log("SquashNoScriptAppCustomResourceLambdaName: "
        + squashNoScriptAppCustomResourceLambdaName);
    logger.log("SquashCognitoCustomResourceLambdaName: " + squashCognitoCustomResourceLambdaName);
    logger.log("SquashScheduledCloudwatchEventCustomResourceLambdaName: "
        + squashScheduledCloudwatchEventCustomResourceLambdaName);
    logger.log("UpdateBookingsLambdaName: " + updateBookingsLambdaName);
    logger.log("DatabaseBackupLambdaName: " + databaseBackupLambdaName);
    logger.log("DatabaseRestoreLambdaName: " + databaseRestoreLambdaName);
    logger.log("Region: " + region);
    logger.log("Revision: " + revision);

    // API calls below can sometimes give access denied errors during stack
    // creation which I think is bc required new roles have not yet propagated
    // across AWS. We sleep here to allow time for this propagation.
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      logger.log("Sleep to allow new roles to propagate has been interrupted.");
    }

    // Prepare our response to be sent in the finally block
    CloudFormationResponder cloudFormationResponder = new CloudFormationResponder(
        standardRequestParameters, "DummyPhysicalResourceId");
    // Initialise failure response, which will be changed on success
    String responseStatus = "FAILED";

    try {
      cloudFormationResponder.initialise();

      AWSLogs client = getAWSLogsClient(region);

      if (requestType.equals("Create")) {

        // Create and set the specified retention period on other lambdas' log
        // groups (these lambdas will not have run and created their log groups
        // yet)
        List<String> lambdaNames = new ArrayList<>(Arrays.asList(applyBookingRulesLambdaName,
            validDatesLambdaName, bookingsGETLambdaName, bookingRulesGETLambdaName,
            bookingsPUTDELETELambdaName, bookingRuleOrExclusionPUTDELETELambdaName,
            updateBookingsLambdaName, databaseBackupLambdaName, databaseRestoreLambdaName));

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
        lambdaNames.add(squashAngularjsAppCustomResourceLambdaName);
        lambdaNames.add(squashApiGatewayCustomResourceLambdaName);
        lambdaNames.add(squashCognitoCustomResourceLambdaName);
        lambdaNames.add(squashNoScriptAppCustomResourceLambdaName);
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
      cloudFormationResponder.addKeyValueOutputsPair("Result", "Hello from LogGroups!!!");
      cloudFormationResponder.sendResponse(responseStatus, logger);
    }
  }
}