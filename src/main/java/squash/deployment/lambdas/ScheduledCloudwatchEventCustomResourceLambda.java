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
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEvents;
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEventsClient;
import com.amazonaws.services.cloudwatchevents.model.DeleteRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleResult;
import com.amazonaws.services.cloudwatchevents.model.PutTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.RemoveTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.RuleState;
import com.amazonaws.services.cloudwatchevents.model.Target;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Manages the AWS Cloudformation CloudwatchEvents custom resource.
 * 
 * <p>The Cloudwatch scheduled event rules are created and deleted by
 *    Cloudformation using a custom resource backed by this lambda function.
 *    
 * <p>Scheduled events are used to:
 * <ul>
 *    <li>Move the website forward one day every midnight.</li>
 *    <li>Keep the lambda functions warm by running them every 5 minutes.</li>
 * </ul>
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class ScheduledCloudwatchEventCustomResourceLambda implements
    RequestHandler<Map<String, Object>, Object> {

  /**
   * Implementation for the AWS Lambda function backing the CloudwatchEvents resource.
   * 
   * <p>This lambda has the following keys in its request map (in addition
   *    to the standard ones) provided via the Cloudformation stack template:
   * <ul>
   *    <li>ApiGatewayBaseUrl - base Url of the ApiGateway Api.</li>
   *    <li>UpdateBookingsLambdaArn - arn of the lambda function to move the site forward by a day.</li>
   *    <li>CreateOrDeleteBookingsLambdaArn - arn of the lambda function to keep warm.</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   * </ul>
   *
   * <p>On success, it returns the following outputs to Cloudformation:
   * <ul>
   *    <li>MidnightScheduledCloudwatchEventRuleArn - arn of the midnight rule.</li>
   *    <li>PrewarmerScheduledCloudwatchEventRuleArn - arn of the lambda prewarmer rule.</li>
   * </ul>
   *   
   * @param request request parameters as provided by the CloudFormation service
   * @param context context as provided by the CloudFormation service
   */
  @Override
  public Object handleRequest(Map<String, Object> request, Context context) {

    LambdaLogger logger = context.getLogger();
    logger.log("Starting ScheduledCloudwatchEvent custom resource handleRequest");

    // Handle standard request parameters
    Map<String, String> standardRequestParameters = LambdaInputLogger.logStandardRequestParameters(
        request, logger);
    String requestType = standardRequestParameters.get("RequestType");

    // Handle custom request parameters
    logger.log("Logging custom input parameters to custom resource request");
    @SuppressWarnings("unchecked")
    Map<String, Object> resourceProps = (Map<String, Object>) request.get("ResourceProperties");
    String apiGatewayBaseUrl = (String) resourceProps.get("ApiGatewayBaseUrl");
    String updateBookingsLambdaArn = ((String) resourceProps.get("UpdateBookingsLambdaArn"));
    String createOrDeleteBookingsLambdaArn = ((String) resourceProps
        .get("CreateOrDeleteBookingsLambdaArn"));
    String region = (String) resourceProps.get("Region");

    // Log out our custom request parameters
    logger.log("ApiGatewayBaseUrl: " + apiGatewayBaseUrl);
    logger.log("UpdateBookingsLambdaArn: " + updateBookingsLambdaArn);
    logger.log("CreateOrDeleteBookingsLambdaArn: " + createOrDeleteBookingsLambdaArn);
    logger.log("Region: " + region);

    // Prepare our response to be sent in the finally block
    CloudFormationResponder cloudFormationResponder = new CloudFormationResponder(
        standardRequestParameters, "DummyPhysicalResourceId");
    // Initialise failure response, which will be changed on success
    String responseStatus = "FAILED";
    PutRuleResult putMidnightRuleResult = null;
    PutRuleResult putPrewarmerRuleResult = null;
    // Ensure unique names, so multiple stacks do not clash
    // Stack id is like:
    // "arn:aws:cloudformation:us-west-2:EXAMPLE/stack-name/guid". We
    // want the last guid section.
    String stackId = standardRequestParameters.get("StackId");
    String guid = stackId.substring(stackId.lastIndexOf('/') + 1);
    String midnight_rule_name = "MidnightRunner_" + guid;
    String midnight_target_id = "MidnightTarget_" + guid;
    String prewarmer_rule_name = "Prewarmer_" + guid;
    String prewarmer_target_id = "PrewarmerTarget_" + guid;
    try {
      if (requestType.equals("Create")) {

        AmazonCloudWatchEvents amazonCloudWatchEventsClient = new AmazonCloudWatchEventsClient();
        amazonCloudWatchEventsClient.setRegion(Region.getRegion(Regions.fromName(region)));

        // Create midnight rule with Cron expression
        PutRuleRequest putMidnightRuleRequest = new PutRuleRequest();
        // Put just after midnight to avoid any timing glitch i.e. somehow still
        // thinking it's the previous day when it runs. Will this run at 1am in
        // BST? (Not a massive problem if it does not update till 1am.)
        putMidnightRuleRequest.setScheduleExpression("cron(1 0 * * ? *)");
        putMidnightRuleRequest.setName(midnight_rule_name);
        putMidnightRuleRequest.setState(RuleState.ENABLED);
        putMidnightRuleRequest
            .setDescription("This runs just after midnight every day to refresh all the squash booking pages in S3");
        putMidnightRuleResult = amazonCloudWatchEventsClient.putRule(putMidnightRuleRequest);

        // Create target with updateBookings lambda, and attach rule to it
        Target midnightTarget = new Target();
        midnightTarget.setArn(updateBookingsLambdaArn);
        midnightTarget.setInput("{\"apiGatewayBaseUrl\" : \"" + apiGatewayBaseUrl + "\"}");
        midnightTarget.setId(midnight_target_id);
        Collection<Target> midnightTargets = new ArrayList<>();
        midnightTargets.add(midnightTarget);
        PutTargetsRequest putMidnightTargetsRequest = new PutTargetsRequest();
        putMidnightTargetsRequest.setRule(midnight_rule_name);
        putMidnightTargetsRequest.setTargets(midnightTargets);
        amazonCloudWatchEventsClient.putTargets(putMidnightTargetsRequest);

        // Create prewarmer rule with Rate expression
        PutRuleRequest putPrewarmerRuleRequest = new PutRuleRequest();
        putPrewarmerRuleRequest.setScheduleExpression("rate(5 minutes)");
        putPrewarmerRuleRequest.setName(prewarmer_rule_name);
        putPrewarmerRuleRequest.setState(RuleState.ENABLED);
        putPrewarmerRuleRequest
            .setDescription("This runs every 5 minutes to prewarm the squash bookings lambdas");
        putPrewarmerRuleResult = amazonCloudWatchEventsClient.putRule(putPrewarmerRuleRequest);

        // Create target with updateBookings lambda, and attach rule to it
        Target prewarmerTarget = new Target();
        prewarmerTarget.setArn(createOrDeleteBookingsLambdaArn);
        prewarmerTarget.setInput("{\"slot\" : \"-1\"}");
        prewarmerTarget.setId(prewarmer_target_id);
        Collection<Target> prewarmerTargets = new ArrayList<>();
        prewarmerTargets.add(prewarmerTarget);
        PutTargetsRequest putPrewarmerTargetsRequest = new PutTargetsRequest();
        putPrewarmerTargetsRequest.setRule(prewarmer_rule_name);
        putPrewarmerTargetsRequest.setTargets(prewarmerTargets);
        amazonCloudWatchEventsClient.putTargets(putPrewarmerTargetsRequest);

      } else if (requestType.equals("Delete")) {
        logger.log("Delete request - so deleting the scheduled cloudwatch rules");

        AmazonCloudWatchEvents amazonCloudWatchEventsClient = new AmazonCloudWatchEventsClient();
        amazonCloudWatchEventsClient.setRegion(Region.getRegion(Regions.fromName(region)));

        // Delete target from midnight rule
        logger.log("Removing lambda target from MidnightRunner rule");
        RemoveTargetsRequest removeMidnightTargetsRequest = new RemoveTargetsRequest();
        removeMidnightTargetsRequest.setRule(midnight_rule_name);
        Collection<String> midnightTargetIds = new ArrayList<>();
        midnightTargetIds.add(midnight_target_id);
        removeMidnightTargetsRequest.setIds(midnightTargetIds);
        amazonCloudWatchEventsClient.removeTargets(removeMidnightTargetsRequest);
        logger.log("Removed lambda target from MidnightRunner rule");

        // Delete midnight scheduled rule
        logger.log("Deleting MidnightRunner rule");
        DeleteRuleRequest deleteMidnightRuleRequest = new DeleteRuleRequest();
        deleteMidnightRuleRequest.setName(midnight_rule_name);
        amazonCloudWatchEventsClient.deleteRule(deleteMidnightRuleRequest);
        logger.log("Deleted MidnightRunner rule");

        // Delete target from prewarmer rule
        logger.log("Removing lambda target from Prewarmer rule");
        RemoveTargetsRequest removePrewarmerTargetsRequest = new RemoveTargetsRequest();
        removePrewarmerTargetsRequest.setRule(prewarmer_rule_name);
        Collection<String> prewarmerTargetIds = new ArrayList<>();
        prewarmerTargetIds.add(prewarmer_target_id);
        removePrewarmerTargetsRequest.setIds(prewarmerTargetIds);
        amazonCloudWatchEventsClient.removeTargets(removePrewarmerTargetsRequest);
        logger.log("Removed lambda target from Prewarmer rule");

        // Delete prewarmer scheduled rule
        logger.log("Deleting Prewarmer rule");
        DeleteRuleRequest deletePrewarmerRuleRequest = new DeleteRuleRequest();
        deletePrewarmerRuleRequest.setName(prewarmer_rule_name);
        amazonCloudWatchEventsClient.deleteRule(deletePrewarmerRuleRequest);
        logger.log("Deleted Prewarmer rule");

        logger.log("Finished removing the scheduled cloudwatch rules");
      }
      // Do not handle Updates for now

      responseStatus = "SUCCESS";
      return null;
    } catch (AmazonServiceException ase) {
      ExceptionUtils.logAmazonServiceException(ase, logger);
      return null;
    } catch (AmazonClientException ace) {
      ExceptionUtils.logAmazonClientException(ace, logger);
      return null;
    } catch (Exception e) {
      logger.log("Exception caught in the scheduled cloudwatch event Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      // Prepare a memory stream to append error messages to
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteArrayOutputStream);
      JSONObject outputs;
      try {
        outputs = new JSONObject();
        outputs.put("MidnightScheduledCloudwatchEventRuleArn",
            putMidnightRuleResult == null ? "Not available" : putMidnightRuleResult.getRuleArn());
        outputs.put("PrewarmerScheduledCloudwatchEventRuleArn",
            putPrewarmerRuleResult == null ? "Not available" : putPrewarmerRuleResult.getRuleArn());
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