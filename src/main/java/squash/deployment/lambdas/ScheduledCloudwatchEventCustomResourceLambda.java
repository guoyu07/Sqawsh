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

import org.apache.commons.lang3.tuple.ImmutablePair;

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
 * <p>Scheduled events are used by the:
 * <ul>
 *    <li>Bookings service to:
 *    <ul>
 *       <li>Apply the next day's booking rules just before every midnight.</li>
 *       <li>Backup all bookings and booking rules just before every midnight.</li>
 *       <li>Keep the lambda functions warm by running them every 5 minutes.</li>
 *    </ul>
 *    </li>
 *    <li>Front-end service to:
 *    <ul>
 *       <li>Move the website forward one day just after every midnight.</li>
 *    </ul>
 *    </li>
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
   *    <li>ApplyBookingRulesLambdaArn - arn of the lambda function to apply the booking rules.</li>
   *    <li>DatabaseBackupLambdaArn - arn of the lambda function to backup all bookings.</li>
   *    <li>CreateOrDeleteBookingsLambdaArn - arn of the lambda function to keep warm.</li>
   *    <li>UpdateBookingsLambdaArn - arn of the lambda function to move the site forward by a day.</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   *    <li>Revision - integer incremented to force stack updates to update this resource.</li>
   * </ul>
   *
   * <p>On success, it returns the following outputs to Cloudformation:
   * <ul>
   *    <li>UpdateBookingsServiceEventRuleArn - arn of the pre-midnight rule to update the bookings service.</li>
   *    <li>UpdateFrontendServiceEventRuleArn - arn of the post-midnight rule to update the website.</li>
   *    <li>PrewarmerEventRuleArn - arn of the lambda prewarmer rule.</li>
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
    String applyBookingRulesLambdaArn = ((String) resourceProps.get("ApplyBookingRulesLambdaArn"));
    String databaseBackupLambdaArn = ((String) resourceProps.get("DatabaseBackupLambdaArn"));
    String createOrDeleteBookingsLambdaArn = ((String) resourceProps
        .get("CreateOrDeleteBookingsLambdaArn"));
    String updateBookingsLambdaArn = ((String) resourceProps.get("UpdateBookingsLambdaArn"));
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");

    // Log out our custom request parameters
    logger.log("ApiGatewayBaseUrl: " + apiGatewayBaseUrl);
    logger.log("ApplyBookingRulesLambdaArn: " + applyBookingRulesLambdaArn);
    logger.log("DatabaseBackupLambdaArn: " + databaseBackupLambdaArn);
    logger.log("CreateOrDeleteBookingsLambdaArn: " + createOrDeleteBookingsLambdaArn);
    logger.log("UpdateBookingsLambdaArn: " + updateBookingsLambdaArn);
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

    // Ensure unique names, so multiple stacks do not clash
    // Stack id is like:
    // "arn:aws:cloudformation:us-west-2:EXAMPLE/stack-name/guid". We
    // want the last guid section.
    String stackId = standardRequestParameters.get("StackId");
    String guid = stackId.substring(stackId.lastIndexOf('/') + 1);
    String preMidnightRuleName = "PreMidnightRunner_" + guid;
    String preMidnightapplyBookingRulesTargetId = "ApplyRulesTarget_" + guid;
    String preMidnightDatabaseBackupTargetId = "BackupTarget_" + guid;
    String postMidnightRuleName = "PostMidnightRunner_" + guid;
    String postMidnightWebsiteRefreshTargetId = "WebsiteRefreshTarget_" + guid;
    String prewarmerRuleName = "Prewarmer_" + guid;
    String prewarmerTargetId = "PrewarmerTarget_" + guid;
    Map<String, String> ruleArns = null;
    try {
      cloudFormationResponder.initialise();

      AmazonCloudWatchEvents amazonCloudWatchEventsClient = new AmazonCloudWatchEventsClient();
      amazonCloudWatchEventsClient.setRegion(Region.getRegion(Regions.fromName(region)));

      if (requestType.equals("Create")) {

        ruleArns = new HashMap<>();
        ImmutablePair<String, String> ruleArn = setUpPreMidnightRuleAndTargets(preMidnightRuleName,
            preMidnightapplyBookingRulesTargetId, applyBookingRulesLambdaArn,
            preMidnightDatabaseBackupTargetId, databaseBackupLambdaArn,
            amazonCloudWatchEventsClient, logger);
        ruleArns.put(ruleArn.left, ruleArn.right);

        ruleArn = setUpPostMidnightRuleAndTargets(postMidnightRuleName,
            postMidnightWebsiteRefreshTargetId, updateBookingsLambdaArn, apiGatewayBaseUrl,
            amazonCloudWatchEventsClient, logger);
        ruleArns.put(ruleArn.left, ruleArn.right);

        ruleArn = setUpPrewarmerRuleAndTargets(prewarmerRuleName, prewarmerTargetId,
            createOrDeleteBookingsLambdaArn, amazonCloudWatchEventsClient, logger);
        ruleArns.put(ruleArn.left, ruleArn.right);

      } else if (requestType.equals("Update")) {
        // First remove any existing targets from the rules
        logger.log("Removing existing targets from rules");
        ListTargetsByRuleRequest listTargetsByRuleRequest = new ListTargetsByRuleRequest();
        listTargetsByRuleRequest.setRule(preMidnightRuleName);
        ListTargetsByRuleResult listTargetsByRuleResult = amazonCloudWatchEventsClient
            .listTargetsByRule(listTargetsByRuleRequest);
        List<String> targets = listTargetsByRuleResult.getTargets().stream().map(Target::getId)
            .collect(Collectors.toList());
        RemoveTargetsRequest removeTargetsRequest = new RemoveTargetsRequest();
        removeTargetsRequest.setRule(preMidnightRuleName);
        removeTargetsRequest.setIds(targets);
        amazonCloudWatchEventsClient.removeTargets(removeTargetsRequest);
        logger.log("Successfully removed targets from pre-midnight rule");

        listTargetsByRuleRequest.setRule(postMidnightRuleName);
        listTargetsByRuleResult = amazonCloudWatchEventsClient
            .listTargetsByRule(listTargetsByRuleRequest);
        targets = listTargetsByRuleResult.getTargets().stream().map(Target::getId)
            .collect(Collectors.toList());
        removeTargetsRequest = new RemoveTargetsRequest();
        removeTargetsRequest.setRule(postMidnightRuleName);
        removeTargetsRequest.setIds(targets);
        amazonCloudWatchEventsClient.removeTargets(removeTargetsRequest);
        logger.log("Successfully removed targets from post-midnight rule");

        listTargetsByRuleRequest.setRule(prewarmerRuleName);
        listTargetsByRuleResult = amazonCloudWatchEventsClient
            .listTargetsByRule(listTargetsByRuleRequest);
        targets = listTargetsByRuleResult.getTargets().stream().map(Target::getId)
            .collect(Collectors.toList());
        removeTargetsRequest = new RemoveTargetsRequest();
        removeTargetsRequest.setRule(prewarmerRuleName);
        removeTargetsRequest.setIds(targets);
        amazonCloudWatchEventsClient.removeTargets(removeTargetsRequest);
        logger.log("Successfully removed targets from prewarmer rule");

        // Re-put the rules and then add back updated targets to them
        logger.log("Adding back updated rules and their targets");
        ruleArns = new HashMap<>();
        ImmutablePair<String, String> ruleArn = setUpPreMidnightRuleAndTargets(preMidnightRuleName,
            preMidnightapplyBookingRulesTargetId, applyBookingRulesLambdaArn,
            preMidnightDatabaseBackupTargetId, databaseBackupLambdaArn,
            amazonCloudWatchEventsClient, logger);
        ruleArns.put(ruleArn.left, ruleArn.right);

        ruleArn = setUpPostMidnightRuleAndTargets(postMidnightRuleName,
            postMidnightWebsiteRefreshTargetId, updateBookingsLambdaArn, apiGatewayBaseUrl,
            amazonCloudWatchEventsClient, logger);
        ruleArns.put(ruleArn.left, ruleArn.right);

        ruleArn = setUpPrewarmerRuleAndTargets(prewarmerRuleName, prewarmerTargetId,
            createOrDeleteBookingsLambdaArn, amazonCloudWatchEventsClient, logger);
        ruleArns.put(ruleArn.left, ruleArn.right);

      } else if (requestType.equals("Delete")) {
        logger.log("Delete request - so deleting the scheduled cloudwatch rules");

        // Delete target from pre-midnight rule
        logger.log("Removing lambda targets from pre-midnight rule");
        RemoveTargetsRequest removePreMidnightTargetsRequest = new RemoveTargetsRequest();
        removePreMidnightTargetsRequest.setRule(preMidnightRuleName);
        Collection<String> preMidnightTargetIds = new ArrayList<>();
        preMidnightTargetIds.add(preMidnightapplyBookingRulesTargetId);
        preMidnightTargetIds.add(preMidnightDatabaseBackupTargetId);
        removePreMidnightTargetsRequest.setIds(preMidnightTargetIds);
        amazonCloudWatchEventsClient.removeTargets(removePreMidnightTargetsRequest);
        logger.log("Removed lambda target from pre-midnight rule");

        // Delete pre-midnight scheduled rule
        logger.log("Deleting pre-midnight rule");
        DeleteRuleRequest deletePreMidnightRuleRequest = new DeleteRuleRequest();
        deletePreMidnightRuleRequest.setName(preMidnightRuleName);
        amazonCloudWatchEventsClient.deleteRule(deletePreMidnightRuleRequest);
        logger.log("Deleted pre-midnight rule");

        // Delete target from post-midnight rule
        logger.log("Removing lambda targets from post-midnight rule");
        RemoveTargetsRequest removePostMidnightTargetsRequest = new RemoveTargetsRequest();
        removePostMidnightTargetsRequest.setRule(postMidnightRuleName);
        Collection<String> postMidnightTargetIds = new ArrayList<>();
        postMidnightTargetIds.add(postMidnightWebsiteRefreshTargetId);
        removePostMidnightTargetsRequest.setIds(postMidnightTargetIds);
        amazonCloudWatchEventsClient.removeTargets(removePostMidnightTargetsRequest);
        logger.log("Removed lambda target from post-midnight rule");

        // Delete post-midnight scheduled rule
        logger.log("Deleting post-midnight rule");
        DeleteRuleRequest deletePostMidnightRuleRequest = new DeleteRuleRequest();
        deletePostMidnightRuleRequest.setName(postMidnightRuleName);
        amazonCloudWatchEventsClient.deleteRule(deletePostMidnightRuleRequest);
        logger.log("Deleted post-midnight rule");

        // Delete target from prewarmer rule
        logger.log("Removing lambda target from Prewarmer rule");
        RemoveTargetsRequest removePrewarmerTargetsRequest = new RemoveTargetsRequest();
        removePrewarmerTargetsRequest.setRule(prewarmerRuleName);
        Collection<String> prewarmerTargetIds = new ArrayList<>();
        prewarmerTargetIds.add(prewarmerTargetId);
        removePrewarmerTargetsRequest.setIds(prewarmerTargetIds);
        amazonCloudWatchEventsClient.removeTargets(removePrewarmerTargetsRequest);
        logger.log("Removed lambda target from Prewarmer rule");

        // Delete prewarmer scheduled rule
        logger.log("Deleting Prewarmer rule");
        DeleteRuleRequest deletePrewarmerRuleRequest = new DeleteRuleRequest();
        deletePrewarmerRuleRequest.setName(prewarmerRuleName);
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
      cloudFormationResponder.addKeyValueOutputsPair(
          "UpdateBookingsServiceEventRuleArn",
          (requestType.equals("Delete") || (ruleArns == null)) ? "Not available" : ruleArns
              .get("UpdateBookingsServiceEventRuleArn"));
      cloudFormationResponder.addKeyValueOutputsPair(
          "UpdateFrontendServiceEventRuleArn",
          (requestType.equals("Delete") || (ruleArns == null)) ? "Not available" : ruleArns
              .get("UpdateFrontendServiceEventRuleArn"));
      cloudFormationResponder.addKeyValueOutputsPair(
          "PrewarmerEventRuleArn",
          (requestType.equals("Delete") || (ruleArns == null)) ? "Not available" : ruleArns
              .get("PrewarmerEventRuleArn"));
      cloudFormationResponder.sendResponse(responseStatus, logger);
    }
  }

  ImmutablePair<String, String> setUpPreMidnightRuleAndTargets(String ruleName,
      String applyBookingRulesTargetId, String applyBookingRulesLambdaArn,
      String databaseBackupTargetId, String databaseBackupLambdaArn,
      AmazonCloudWatchEvents amazonCloudWatchEventsClient, LambdaLogger logger) {

    // Create pre-midnight rule with Cron expression
    logger.log("Creating pre-midnight rule");
    PutRuleRequest putRuleRequest = new PutRuleRequest();
    // Put just before midnight to allow rule-based bookings to be created
    // before anyone else has a chance to create bookings that might clash.
    putRuleRequest.setScheduleExpression("cron(0 22 * * ? *)");
    putRuleRequest.setName(ruleName);
    putRuleRequest.setState(RuleState.ENABLED);
    putRuleRequest
        .setDescription("This runs just before midnight every day to apply booking rules for the following day and to backup all bookings.");
    ImmutablePair<String, String> ruleArn = new ImmutablePair<>(
        "UpdateBookingsServiceEventRuleArn", amazonCloudWatchEventsClient.putRule(putRuleRequest)
            .getRuleArn());

    // Create target with applyBookingRules and backupBookings lambdas, and
    // attach rule to it.
    logger.log("Attaching applyBookingRules lambda to the pre-midnight rule");
    Target applyBookingRulesTarget = new Target();
    applyBookingRulesTarget.setArn(applyBookingRulesLambdaArn);
    applyBookingRulesTarget.setId(applyBookingRulesTargetId);
    Collection<Target> midnightTargets = new ArrayList<>();
    midnightTargets.add(applyBookingRulesTarget);
    logger.log("Attaching database backup lambda to the pre-midnight rule");
    Target databaseBackupTarget = new Target();
    databaseBackupTarget.setArn(databaseBackupLambdaArn);
    databaseBackupTarget.setId(databaseBackupTargetId);
    midnightTargets.add(databaseBackupTarget);
    PutTargetsRequest putTargetsRequest = new PutTargetsRequest();
    putTargetsRequest.setRule(ruleName);
    putTargetsRequest.setTargets(midnightTargets);
    amazonCloudWatchEventsClient.putTargets(putTargetsRequest);
    logger.log("Targets attached to the pre-midnight rule");

    return ruleArn;
  }

  ImmutablePair<String, String> setUpPostMidnightRuleAndTargets(String ruleName,
      String websiteRefreshTargetId, String updateBookingsLambdaArn, String apiGatewayBaseUrl,
      AmazonCloudWatchEvents amazonCloudWatchEventsClient, LambdaLogger logger) {

    // Create post-midnight rule with Cron expression
    logger.log("Creating post-midnight rule");
    PutRuleRequest putRuleRequest = new PutRuleRequest();
    // Put just after midnight to avoid any timing glitch i.e. somehow still
    // thinking it's the previous day when it runs. Will this run at 1am in
    // BST? (Not a massive problem if it does not update till 1am.)
    putRuleRequest.setScheduleExpression("cron(1 0 * * ? *)");
    putRuleRequest.setName(ruleName);
    putRuleRequest.setState(RuleState.ENABLED);
    putRuleRequest
        .setDescription("This runs just after midnight every day to refresh all the squash booking pages in S3");
    ImmutablePair<String, String> ruleArn = new ImmutablePair<>(
        "UpdateFrontendServiceEventRuleArn", amazonCloudWatchEventsClient.putRule(putRuleRequest)
            .getRuleArn());

    // Create target with updateBookings lambda, and attach rule to it.
    logger.log("Attaching updataBookings lambda to the post-midnight rule");
    Target updateBookingsTarget = new Target();
    updateBookingsTarget.setArn(updateBookingsLambdaArn);
    updateBookingsTarget.setInput("{\"apiGatewayBaseUrl\" : \"" + apiGatewayBaseUrl + "\"}");
    updateBookingsTarget.setId(websiteRefreshTargetId);
    Collection<Target> midnightTargets = new ArrayList<>();
    midnightTargets.add(updateBookingsTarget);
    PutTargetsRequest putMidnightTargetsRequest = new PutTargetsRequest();
    putMidnightTargetsRequest.setRule(ruleName);
    putMidnightTargetsRequest.setTargets(midnightTargets);
    amazonCloudWatchEventsClient.putTargets(putMidnightTargetsRequest);

    return ruleArn;
  }

  ImmutablePair<String, String> setUpPrewarmerRuleAndTargets(String ruleName,
      String prewarmerTargetId, String createOrDeleteBookingsLambdaArn,
      AmazonCloudWatchEvents amazonCloudWatchEventsClient, LambdaLogger logger) {

    // Create prewarmer rule with Rate expression
    logger.log("Creating prewarmer rule");
    PutRuleRequest putRuleRequest = new PutRuleRequest();
    putRuleRequest.setScheduleExpression("rate(5 minutes)");
    putRuleRequest.setName(ruleName);
    putRuleRequest.setState(RuleState.ENABLED);
    putRuleRequest
        .setDescription("This runs every 5 minutes to prewarm the squash bookings lambdas");
    ImmutablePair<String, String> ruleArn = new ImmutablePair<>("PrewarmerEventRuleArn",
        amazonCloudWatchEventsClient.putRule(putRuleRequest).getRuleArn());

    // Create target with bookings lambda, and attach rule to it
    logger.log("Attaching bookings lambda to the prewarmer rule");
    Target prewarmerTarget = new Target();
    prewarmerTarget.setArn(createOrDeleteBookingsLambdaArn);
    prewarmerTarget.setInput("{\"slot\" : \"-1\"}");
    prewarmerTarget.setId(prewarmerTargetId);
    Collection<Target> prewarmerTargets = new ArrayList<>();
    prewarmerTargets.add(prewarmerTarget);
    PutTargetsRequest putPrewarmerTargetsRequest = new PutTargetsRequest();
    putPrewarmerTargetsRequest.setRule(ruleName);
    putPrewarmerTargetsRequest.setTargets(prewarmerTargets);
    amazonCloudWatchEventsClient.putTargets(putPrewarmerTargetsRequest);

    return ruleArn;
  }
}