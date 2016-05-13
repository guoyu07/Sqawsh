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
import com.amazonaws.services.cloudwatchevents.model.ListTargetsByRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.ListTargetsByRuleResult;
import com.amazonaws.services.cloudwatchevents.model.PutRuleRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the AWS Cloudformation CloudwatchEvents custom resource.
 * 
 * <p>The Cloudwatch scheduled event rules are created and deleted by
 *    Cloudformation using a custom resource backed by this lambda function.
 *    
 * <p>Scheduled events are used to:
 * <ul>
 *    <li>Move the website forward one day every midnight.</li>
 *    <li>Backup all bookings every midnight.</li>
 *    <li>Keep the lambda functions warm by running them every 5 minutes.</li>
 * </ul>
 * 
 * <p>N.B. You should create at most one CloudwatchEvents custom resource per stack.
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
   *    <li>DatabaseBackupLambdaArn - arn of the lambda function to backup all bookings.</li>
   *    <li>CreateOrDeleteBookingsLambdaArn - arn of the lambda function to keep warm.</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   *    <li>Revision - integer incremented to force stack updates to update this resource.</li>
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
    String databaseBackupLambdaArn = ((String) resourceProps.get("DatabaseBackupLambdaArn"));
    String createOrDeleteBookingsLambdaArn = ((String) resourceProps
        .get("CreateOrDeleteBookingsLambdaArn"));
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");

    // Log out our custom request parameters
    logger.log("ApiGatewayBaseUrl: " + apiGatewayBaseUrl);
    logger.log("UpdateBookingsLambdaArn: " + updateBookingsLambdaArn);
    logger.log("DatabaseBackupLambdaArn: " + databaseBackupLambdaArn);
    logger.log("CreateOrDeleteBookingsLambdaArn: " + createOrDeleteBookingsLambdaArn);
    logger.log("Region: " + region);
    logger.log("Revision: " + revision);

    // Prepare our response to be sent in the finally block
    CloudFormationResponder cloudFormationResponder = new CloudFormationResponder(
        standardRequestParameters, "DummyPhysicalResourceId");
    // Initialise failure response, which will be changed on success
    String responseStatus = "FAILED";

    // Ensure unique names, so multiple stacks do not clash
    // Stack id is like:
    // "arn:aws:cloudformation:us-west-2:EXAMPLE/stack-name/guid". We
    // want the last guid section.
    String stackId = standardRequestParameters.get("StackId");
    String guid = stackId.substring(stackId.lastIndexOf('/') + 1);
    String midnight_rule_name = "MidnightRunner_" + guid;
    String midnight_refresh_target_id = "MidnightRefreshTarget_" + guid;
    String midnight_backup_target_id = "MidnightBackupTarget_" + guid;
    String prewarmer_rule_name = "Prewarmer_" + guid;
    String prewarmer_target_id = "PrewarmerTarget_" + guid;
    Map<String, String> ruleArns = null;
    try {
      AmazonCloudWatchEvents amazonCloudWatchEventsClient = new AmazonCloudWatchEventsClient();
      amazonCloudWatchEventsClient.setRegion(Region.getRegion(Regions.fromName(region)));

      if (requestType.equals("Create")) {

        ruleArns = setUpRulesAndTargets(midnight_rule_name, midnight_refresh_target_id,
            midnight_backup_target_id, prewarmer_rule_name, prewarmer_target_id, apiGatewayBaseUrl,
            createOrDeleteBookingsLambdaArn, updateBookingsLambdaArn, databaseBackupLambdaArn,
            amazonCloudWatchEventsClient, logger);

      } else if (requestType.equals("Update")) {
        // First remove any existing targets from the rules
        logger.log("Removing existing targets from rules");
        ListTargetsByRuleRequest listTargetsByRuleRequest = new ListTargetsByRuleRequest();
        listTargetsByRuleRequest.setRule(midnight_rule_name);
        ListTargetsByRuleResult listTargetsByRuleResult = amazonCloudWatchEventsClient
            .listTargetsByRule(listTargetsByRuleRequest);
        List<String> targets = listTargetsByRuleResult.getTargets().stream().map(Target::getId)
            .collect(Collectors.toList());
        RemoveTargetsRequest removeTargetsRequest = new RemoveTargetsRequest();
        removeTargetsRequest.setRule(midnight_rule_name);
        removeTargetsRequest.setIds(targets);
        amazonCloudWatchEventsClient.removeTargets(removeTargetsRequest);
        logger.log("Successfully removed targets from midnight rule");

        listTargetsByRuleRequest.setRule(prewarmer_rule_name);
        listTargetsByRuleResult = amazonCloudWatchEventsClient
            .listTargetsByRule(listTargetsByRuleRequest);
        targets = listTargetsByRuleResult.getTargets().stream().map(Target::getId)
            .collect(Collectors.toList());
        removeTargetsRequest = new RemoveTargetsRequest();
        removeTargetsRequest.setRule(prewarmer_rule_name);
        removeTargetsRequest.setIds(targets);
        amazonCloudWatchEventsClient.removeTargets(removeTargetsRequest);
        logger.log("Successfully removed targets from prewarmer rule");

        // Re-put the rules and then add back updated targets to them
        logger.log("Adding back updated rules and their targets");
        ruleArns = setUpRulesAndTargets(midnight_rule_name, midnight_refresh_target_id,
            midnight_backup_target_id, prewarmer_rule_name, prewarmer_target_id, apiGatewayBaseUrl,
            createOrDeleteBookingsLambdaArn, updateBookingsLambdaArn, databaseBackupLambdaArn,
            amazonCloudWatchEventsClient, logger);

      } else if (requestType.equals("Delete")) {
        logger.log("Delete request - so deleting the scheduled cloudwatch rules");

        // Delete target from midnight rule
        logger.log("Removing lambda target from MidnightRunner rule");
        RemoveTargetsRequest removeMidnightTargetsRequest = new RemoveTargetsRequest();
        removeMidnightTargetsRequest.setRule(midnight_rule_name);
        Collection<String> midnightTargetIds = new ArrayList<>();
        midnightTargetIds.add(midnight_refresh_target_id);
        midnightTargetIds.add(midnight_backup_target_id);
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
        outputs.put(
            "MidnightScheduledCloudwatchEventRuleArn",
            (requestType.equals("Delete") || (ruleArns == null)) ? "Not available" : ruleArns
                .get("MidnightScheduledCloudwatchEventRuleArn"));
        outputs.put(
            "PrewarmerScheduledCloudwatchEventRuleArn",
            (requestType.equals("Delete") || (ruleArns == null)) ? "Not available" : ruleArns
                .get("PrewarmerScheduledCloudwatchEventRuleArn"));
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

  Map<String, String> setUpRulesAndTargets(String midnight_rule_name,
      String midnight_refresh_target_id, String midnight_backup_target_id,
      String prewarmer_rule_name, String prewarmer_target_id, String apiGatewayBaseUrl,
      String createOrDeleteBookingsLambdaArn, String updateBookingsLambdaArn,
      String databaseBackupLambdaArn, AmazonCloudWatchEvents amazonCloudWatchEventsClient,
      LambdaLogger logger) {

    // Create midnight rule with Cron expression
    logger.log("Creating midnight rule");
    PutRuleRequest putMidnightRuleRequest = new PutRuleRequest();
    // Put just after midnight to avoid any timing glitch i.e. somehow still
    // thinking it's the previous day when it runs. Will this run at 1am in
    // BST? (Not a massive problem if it does not update till 1am.)
    putMidnightRuleRequest.setScheduleExpression("cron(1 0 * * ? *)");
    putMidnightRuleRequest.setName(midnight_rule_name);
    putMidnightRuleRequest.setState(RuleState.ENABLED);
    putMidnightRuleRequest
        .setDescription("This runs just after midnight every day to backup bookings and refresh all the squash booking pages in S3");
    Map<String, String> ruleArns = new HashMap<String, String>();
    ruleArns.put("MidnightScheduledCloudwatchEventRuleArn",
        amazonCloudWatchEventsClient.putRule(putMidnightRuleRequest).getRuleArn());

    // Create target with updateBookings and backupBookings lambdas, and attach
    // rule to it
    logger.log("Attaching updataBookings lambda to the midnight rule");
    Target midnightRefreshTarget = new Target();
    midnightRefreshTarget.setArn(updateBookingsLambdaArn);
    midnightRefreshTarget.setInput("{\"apiGatewayBaseUrl\" : \"" + apiGatewayBaseUrl + "\"}");
    midnightRefreshTarget.setId(midnight_refresh_target_id);
    Collection<Target> midnightTargets = new ArrayList<>();
    midnightTargets.add(midnightRefreshTarget);
    Target midnightBackupTarget = new Target();
    midnightBackupTarget.setArn(databaseBackupLambdaArn);
    midnightBackupTarget.setId(midnight_backup_target_id);
    midnightTargets.add(midnightBackupTarget);
    PutTargetsRequest putMidnightTargetsRequest = new PutTargetsRequest();
    putMidnightTargetsRequest.setRule(midnight_rule_name);
    putMidnightTargetsRequest.setTargets(midnightTargets);
    amazonCloudWatchEventsClient.putTargets(putMidnightTargetsRequest);

    // Create prewarmer rule with Rate expression
    logger.log("Creating prewarmer rule");
    PutRuleRequest putPrewarmerRuleRequest = new PutRuleRequest();
    putPrewarmerRuleRequest.setScheduleExpression("rate(5 minutes)");
    putPrewarmerRuleRequest.setName(prewarmer_rule_name);
    putPrewarmerRuleRequest.setState(RuleState.ENABLED);
    putPrewarmerRuleRequest
        .setDescription("This runs every 5 minutes to prewarm the squash bookings lambdas");
    ruleArns.put("PrewarmerScheduledCloudwatchEventRuleArn",
        amazonCloudWatchEventsClient.putRule(putPrewarmerRuleRequest).getRuleArn());

    // Create target with bookings lambda, and attach rule to it
    logger.log("Attaching bookings lambda to the prewarmer rule");
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

    return ruleArns;
  }
}