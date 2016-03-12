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
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentity;
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentityClient;
import com.amazonaws.services.cognitoidentity.model.CreateIdentityPoolRequest;
import com.amazonaws.services.cognitoidentity.model.CreateIdentityPoolResult;
import com.amazonaws.services.cognitoidentity.model.DeleteIdentityPoolRequest;
import com.amazonaws.services.cognitoidentity.model.IdentityPoolShortDescription;
import com.amazonaws.services.cognitoidentity.model.ListIdentityPoolsRequest;
import com.amazonaws.services.cognitoidentity.model.SetIdentityPoolRolesRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.UpdateAssumeRolePolicyRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the AWS Cloudformation Cognito custom resource.
 * 
 * <p>The Cognito identity pool is created and deleted by Cloudformation
 *    using a custom resource backed by this lambda function.
 *    
 * <p>The identity pool is created and provided with both unauthenticated and
 *    authenticated roles. This allows provision of temporary fine-grained AWS
 *    credentials to both guest users and authenticated users.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class CognitoCustomResourceLambda implements RequestHandler<Map<String, Object>, Object> {

  /**
   * Returns a Cognito service client.
   * 
   * <p>This method is provided so unit tests can mock out Cognito.
   */
  public AmazonCognitoIdentity getAmazonCognitoIdentityClient(String region) {
    AmazonCognitoIdentity client = new AmazonCognitoIdentityClient();
    client.setRegion(Region.getRegion(Regions.fromName(region)));
    return client;
  }

  /**
   * Implementation for the AWS Lambda function backing the Cognito resource.
   * 
   * <p>This lambda has the following keys in its request map (in addition
   *    to the standard ones) provided via the Cloudformation stack template:
   * <ul>
   *    <li>StackName - name of Cloudformation stack - used as the name of the identity pool.</li>
   *    <li>AuthenticatedRole - arn of role specifying permissions for authenticated users.</li>
   *    <li>AuthenticatedRoleName - name of the authenticated role.</li>
   *    <li>UnauthenticatedRole - arn of role specifying permissions for guest users.</li>
   *    <li>UnauthenticatedRoleName - name of the unauthenticated role.</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   * </ul>
   *
   * <p>On success, it returns the following output to Cloudformation:
   * <ul>
   *    <li>CognitoIdentityPoolId - id of the created Cognito Identity Pool.</li>
   * </ul>
   *
   * @param request request parameters as provided by the CloudFormation service
   * @param context context as provided by the CloudFormation service
   */
  @Override
  public Object handleRequest(Map<String, Object> request, Context context) {

    LambdaLogger logger = context.getLogger();
    logger.log("Starting Cognito custom resource handleRequest");

    // Handle standard request parameters
    Map<String, String> standardRequestParameters = LambdaInputLogger.logStandardRequestParameters(
        request, logger);
    String requestType = standardRequestParameters.get("RequestType");

    // Handle custom request parameters
    logger.log("Logging custom input parameters to custom resource request");
    @SuppressWarnings("unchecked")
    Map<String, Object> resourceProps = (Map<String, Object>) request.get("ResourceProperties");
    String stackName = (String) resourceProps.get("StackName");
    String authenticatedRole = (String) resourceProps.get("AuthenticatedRole");
    String authenticatedRoleName = (String) resourceProps.get("AuthenticatedRoleName");
    String unauthenticatedRole = (String) resourceProps.get("UnauthenticatedRole");
    String unauthenticatedRoleName = (String) resourceProps.get("UnauthenticatedRoleName");
    String region = (String) resourceProps.get("Region");

    // Log out our custom request parameters
    logger.log("StackName: " + stackName);
    logger.log("Authenticated role: " + authenticatedRole);
    logger.log("Authenticated role name: " + authenticatedRoleName);
    logger.log("Unauthenticated role: " + unauthenticatedRole);
    logger.log("Unauthenticated role name: " + unauthenticatedRoleName);
    logger.log("Region: " + region);

    // Prepare our response to be sent in the finally block
    CloudFormationResponder cloudFormationResponder = new CloudFormationResponder(
        standardRequestParameters, "DummyPhysicalResourceId");
    // Initialise failure response, which will be changed on success
    String responseStatus = "FAILED";

    String identityPoolName = stackName;
    logger.log("Setting Cognito Identity Pool name to: " + identityPoolName);

    String identityPoolId = null;
    try {
      AmazonCognitoIdentity client = getAmazonCognitoIdentityClient(region);
      ListIdentityPoolsRequest listIdentityPoolsRequest = new ListIdentityPoolsRequest();
      // Set some reasonable maximum (must be >=1 and <= 60)
      listIdentityPoolsRequest.setMaxResults(50);
      Optional<IdentityPoolShortDescription> identityPool = client
          .listIdentityPools(listIdentityPoolsRequest).getIdentityPools().stream()
          .filter(pool -> pool.getIdentityPoolName().equals(identityPoolName)).findFirst();

      if (requestType.equals("Create")) {

        // Check this Cognito Identity Pool does not exist already
        Boolean identityPoolAlreadyExists = identityPool.isPresent();

        if (identityPoolAlreadyExists.booleanValue()) {
          logger.log("identityPoolAlreadyExists: " + identityPoolAlreadyExists.toString() + " : "
              + identityPool.get().getIdentityPoolName());
          logger.log("Error: Cognito identity pool with name: " + identityPoolName
              + " already exists");
          return null;
        }
        // Create the Cognito identity pool with the specified name
        logger.log("Cognito identity pool with name: " + identityPoolName
            + " does not exist, so creating it");
        CreateIdentityPoolRequest createIdentityPoolRequest = new CreateIdentityPoolRequest();
        createIdentityPoolRequest.setIdentityPoolName(identityPoolName);
        createIdentityPoolRequest.setAllowUnauthenticatedIdentities(true);
        CreateIdentityPoolResult pool = client.createIdentityPool(createIdentityPoolRequest);
        identityPoolId = pool.getIdentityPoolId();

        // Add roles to the pool
        // First update the roles to use the actual pool id in their conditions
        logger
            .log("Updating authenticated and unauthenticated roles to use the actual identity pool id");
        AmazonIdentityManagement iamClient = new AmazonIdentityManagementClient();
        UpdateAssumeRolePolicyRequest updateAssumeRolePolicyRequest = new UpdateAssumeRolePolicyRequest();
        updateAssumeRolePolicyRequest.setRoleName(unauthenticatedRoleName);
        updateAssumeRolePolicyRequest.setPolicyDocument(getAssumeRolePolicyDocument(false,
            identityPoolId, logger));
        iamClient.updateAssumeRolePolicy(updateAssumeRolePolicyRequest);
        updateAssumeRolePolicyRequest.setRoleName(authenticatedRoleName);
        updateAssumeRolePolicyRequest.setPolicyDocument(getAssumeRolePolicyDocument(true,
            identityPoolId, logger));
        iamClient.updateAssumeRolePolicy(updateAssumeRolePolicyRequest);

        // And add the updated roles to the pool
        logger.log("Adding updated authenticated and unauthenticated roles to the identity pool");
        SetIdentityPoolRolesRequest setIdentityPoolRolesRequest = new SetIdentityPoolRolesRequest();
        setIdentityPoolRolesRequest.addRolesEntry("authenticated", authenticatedRole);
        setIdentityPoolRolesRequest.addRolesEntry("unauthenticated", unauthenticatedRole);
        setIdentityPoolRolesRequest.setIdentityPoolId(identityPoolId);
        client.setIdentityPoolRoles(setIdentityPoolRolesRequest);

      } else if (requestType.equals("Delete")) {

        // Check the Cognito Identity Pool does exist
        if (!identityPool.isPresent()) {
          logger.log("Error: Cognito identity pool with name: " + identityPoolName
              + " does not exist");
          return null;
        }
        // Delete the Cognito identity pool with the specified name
        logger.log("Deleting Cognito identity pool with name: " + identityPoolName);
        DeleteIdentityPoolRequest deleteIdentityPoolRequest = new DeleteIdentityPoolRequest();
        deleteIdentityPoolRequest.setIdentityPoolId(identityPool.get().getIdentityPoolId());
        client.deleteIdentityPool(deleteIdentityPoolRequest);
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
      logger.log("Exception caught in Cognito Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      // Prepare a memory stream to append error messages to
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteArrayOutputStream);
      JSONObject outputs;
      try {
        outputs = new JSONObject().put("CognitoIdentityPoolId", identityPoolId);
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

  String getAssumeRolePolicyDocument(Boolean isAuthenticatedRole, String identityPoolId,
      LambdaLogger logger) {
    // N.B. We have to add this here rather than in the CloudFormation
    // template since we don't know the identity pool id until here.
    String amrLine = isAuthenticatedRole ? "          \"cognito-identity.amazonaws.com:amr\": \"authenticated\"\n"
        : "          \"cognito-identity.amazonaws.com:amr\": \"unauthenticated\"\n";

    String assumeRolePolicyDocument = "{" + "  \"Version\" : \"2012-10-17\",\n"
        + "  \"Statement\": [ {\n" + "    \"Effect\": \"Allow\",\n" + "    \"Principal\": {\n"
        + "      \"Federated\": \"cognito-identity.amazonaws.com\"\n" + "     },\n"
        + "    \"Action\": \"sts:AssumeRoleWithWebIdentity\",\n" + "    \"Condition\": {\n"
        + "      \"StringEquals\": {\n" + "        \"cognito-identity.amazonaws.com:aud\":\n"
        + "           \"" + identityPoolId + "\"\n" + "        },\n"
        + "        \"ForAnyValue:StringLike\": {\n" + amrLine + "        }\n" + "      }\n"
        + "    }]\n" + "  }";

    logger.log("Assume role policy document: ");
    logger.log(assumeRolePolicyDocument);

    return assumeRolePolicyDocument;
  }
}