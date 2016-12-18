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
import com.amazonaws.services.cognitoidentity.model.CognitoIdentityProvider;
import com.amazonaws.services.cognitoidentity.model.CreateIdentityPoolRequest;
import com.amazonaws.services.cognitoidentity.model.CreateIdentityPoolResult;
import com.amazonaws.services.cognitoidentity.model.DeleteIdentityPoolRequest;
import com.amazonaws.services.cognitoidentity.model.IdentityPoolShortDescription;
import com.amazonaws.services.cognitoidentity.model.ListIdentityPoolsRequest;
import com.amazonaws.services.cognitoidentity.model.SetIdentityPoolRolesRequest;
import com.amazonaws.services.cognitoidentity.model.UpdateIdentityPoolRequest;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClient;
import com.amazonaws.services.cognitoidp.model.AdminConfirmSignUpRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.CreateUserPoolClientRequest;
import com.amazonaws.services.cognitoidp.model.CreateUserPoolClientResult;
import com.amazonaws.services.cognitoidp.model.CreateUserPoolRequest;
import com.amazonaws.services.cognitoidp.model.CreateUserPoolResult;
import com.amazonaws.services.cognitoidp.model.DeleteUserPoolRequest;
import com.amazonaws.services.cognitoidp.model.ListUserPoolsRequest;
import com.amazonaws.services.cognitoidp.model.PasswordPolicyType;
import com.amazonaws.services.cognitoidp.model.SignUpRequest;
import com.amazonaws.services.cognitoidp.model.UserPoolDescriptionType;
import com.amazonaws.services.cognitoidp.model.UserPoolPolicyType;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.UpdateAssumeRolePolicyRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the AWS Cloudformation Cognito custom resource.
 * 
 * <p>The Cognito identity and user pools are created and deleted by Cloudformation
 *    using a custom resource backed by this lambda function.
 *    
 * <p>The identity pool is created and provided with both unauthenticated and
 *    authenticated roles. This allows provision of temporary fine-grained AWS
 *    credentials to both guest users and authenticated users. The user pool
 *    is currently created with a single admin user, intended to be used
 *    for administering the bookings service e.g. creating booking rules etc.
 *    The authenticated role should provide whatever permissions are needed
 *    by this admin user.
 *    
 * <p>Stack updates will just replace these roles.
 * 
 * <p>N.B. You should create at most one Cognito custom resource per stack.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class CognitoCustomResourceLambda implements RequestHandler<Map<String, Object>, Object> {

  /**
   * Returns a Cognito identity service client.
   * 
   * <p>This method is provided so unit tests can mock out Cognito.
   */
  public AmazonCognitoIdentity getAmazonCognitoIdentityClient(String region) {
    AmazonCognitoIdentity client = new AmazonCognitoIdentityClient();
    client.setRegion(Region.getRegion(Regions.fromName(region)));
    return client;
  }

  /**
   * Returns a Cognito identity provider service client.
   * 
   * <p>This method is provided so unit tests can mock out Cognito.
   */
  public AWSCognitoIdentityProvider getAmazonCognitoIdentityProviderClient(String region) {
    AWSCognitoIdentityProvider client = new AWSCognitoIdentityProviderClient();
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
   *    <li>AdminEmail - initial email address for the admin user</li>
   *    <li>AdminPassword - initial password for the admin user</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   *    <li>Revision - integer incremented to force stack updates to update this resource.</li>
   * </ul>
   *
   * <p>On success, it returns the following outputs to Cloudformation:
   * <ul>
   *    <li>CognitoIdentityPoolId - id of the created Cognito identity pool.</li>
   *    <li>CognitoUserPoolId - id of the created Cognito user pool.</li>
   *    <li>CognitoUserPoolIdentityProviderName - name of the Cognito user pool identity provider to use from javascript.</li>
   *    <li>JavascriptClientAppId - id of user pool client app to use from javascript</li>
   * </ul>
   *
   * <p>Updates will re-set the pool unauthenticated and authenticated roles
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
    String adminEmail = (String) resourceProps.get("AdminEmail");
    String adminPassword = (String) resourceProps.get("AdminPassword");
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");

    // Log out our custom request parameters
    logger.log("StackName: " + stackName);
    logger.log("Authenticated role: " + authenticatedRole);
    logger.log("Authenticated role name: " + authenticatedRoleName);
    logger.log("Unauthenticated role: " + unauthenticatedRole);
    logger.log("Unauthenticated role name: " + unauthenticatedRoleName);
    logger.log("Initial email address of admin user: " + adminEmail);
    logger.log("Initial password of the admin user: <redacted>");
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

    String identityPoolName = stackName;
    logger.log("Setting Cognito Identity Pool name to: " + identityPoolName);

    String userPoolId = null;
    String identityPoolId = null;
    String providerName = null;
    String javascriptClientAppId = null;
    try {
      cloudFormationResponder.initialise();

      AmazonCognitoIdentity identityClient = getAmazonCognitoIdentityClient(region);
      ListIdentityPoolsRequest listIdentityPoolsRequest = new ListIdentityPoolsRequest();
      // Set some reasonable maximum (must be >=1 and <= 60)
      listIdentityPoolsRequest.setMaxResults(50);
      Optional<IdentityPoolShortDescription> identityPool = identityClient
          .listIdentityPools(listIdentityPoolsRequest).getIdentityPools().stream()
          .filter(pool -> pool.getIdentityPoolName().equals(identityPoolName)).findFirst();

      AWSCognitoIdentityProvider providerClient = getAmazonCognitoIdentityProviderClient(region);
      ListUserPoolsRequest listUserPoolsRequest = new ListUserPoolsRequest();
      listUserPoolsRequest.setMaxResults(50);
      Optional<UserPoolDescriptionType> userPool = providerClient
          .listUserPools(listUserPoolsRequest).getUserPools().stream()
          .filter(pool -> pool.getName().equals(identityPoolName)).findFirst();

      if (requestType.equals("Create")) {

        // Check the Cognito Identity or User Pools do not exist already
        Boolean identityPoolAlreadyExists = identityPool.isPresent();
        Boolean userPoolAlreadyExists = userPool.isPresent();

        if (identityPoolAlreadyExists.booleanValue() || userPoolAlreadyExists.booleanValue()) {
          if (identityPoolAlreadyExists.booleanValue()) {
            logger.log("identityPoolAlreadyExists: " + identityPoolAlreadyExists.toString() + " : "
                + identityPool.get().getIdentityPoolName());
            logger.log("Error: Cognito identity pool with name: " + identityPoolName
                + " already exists");
          } else {
            logger.log("userPoolAlreadyExists: " + userPoolAlreadyExists.toString() + " : "
                + userPool.get().getName());
            logger.log("Error: Cognito user pool with name: " + identityPoolName
                + " already exists");
          }
          // Change physical id in responder - this is bc CloudFormation will
          // follow up this failed creation with a Delete request to clean up
          // - and we want to ignore that delete call - but not delete calls
          // for our original resource - and we use the PhysicalId to tell these
          // two cases apart.
          cloudFormationResponder.setPhysicalResourceId("DuplicatePhysicalResourceId");

          return null;
        }

        // Create the Cognito user pool with the specified name
        logger.log("Cognito user pool with name: " + identityPoolName
            + " does not exist, so creating it");
        CreateUserPoolRequest createUserPoolRequest = new CreateUserPoolRequest();
        createUserPoolRequest.setPoolName(identityPoolName);
        // Allow user to sign in using their email
        List<String> aliasAttributes = new ArrayList<>();
        aliasAttributes.add("email");
        createUserPoolRequest.setAliasAttributes(aliasAttributes);
        // Don't force password to include symbols
        PasswordPolicyType passwordPolicy = new PasswordPolicyType();
        passwordPolicy.setMinimumLength(6);
        passwordPolicy.setRequireSymbols(false);
        UserPoolPolicyType userPoolPolicy = new UserPoolPolicyType();
        userPoolPolicy.setPasswordPolicy(passwordPolicy);
        createUserPoolRequest.setPolicies(userPoolPolicy);
        // Require email to be verified by entering a code
        List<String> autoVerifiedAttributes = new ArrayList<>();
        autoVerifiedAttributes.add("email");
        createUserPoolRequest.setAutoVerifiedAttributes(autoVerifiedAttributes);
        // Customise the email verification message
        createUserPoolRequest.setEmailVerificationSubject("Your squash signup code");
        createUserPoolRequest
            .setEmailVerificationMessage("Welcome! Please use the following code to verify your new squash account: {####}. The code is valid for 24 hours.");
        CreateUserPoolResult result = providerClient.createUserPool(createUserPoolRequest);
        userPoolId = result.getUserPool().getId();
        logger.log("Created user pool with id: " + userPoolId);

        // Create admin app for use without SRP in this custom resource
        logger.log("Adding admin client app to user pool");
        CreateUserPoolClientRequest createUserPoolClientRequestAdmin = new CreateUserPoolClientRequest();
        createUserPoolClientRequestAdmin.setClientName("CutomResourceAdminClient");
        createUserPoolClientRequestAdmin.setGenerateSecret(false);
        // Do not require SRP use when called from this custom resource
        List<String> explicitAuthFlows = new ArrayList<>();
        explicitAuthFlows.add("ADMIN_NO_SRP_AUTH");
        createUserPoolClientRequestAdmin.setExplicitAuthFlows(explicitAuthFlows);
        createUserPoolClientRequestAdmin.setUserPoolId(userPoolId);
        CreateUserPoolClientResult resultAdmin = providerClient
            .createUserPoolClient(createUserPoolClientRequestAdmin);
        String adminClientAppId = resultAdmin.getUserPoolClient().getClientId();
        logger.log("Added admin client app to user pool. Client app id: " + adminClientAppId);

        // Create web app for use with SRP from Angular app
        logger.log("Adding javascript client app to user pool");
        CreateUserPoolClientRequest createUserPoolClientRequestJavascript = new CreateUserPoolClientRequest();
        createUserPoolClientRequestJavascript.setClientName("JavascriptClient");
        // Javascript sdk does not use a client secret:
        createUserPoolClientRequestJavascript.setGenerateSecret(false);
        createUserPoolClientRequestJavascript.setUserPoolId(userPoolId);
        CreateUserPoolClientResult resultJavascript = providerClient
            .createUserPoolClient(createUserPoolClientRequestJavascript);
        javascriptClientAppId = resultJavascript.getUserPoolClient().getClientId();
        logger.log("Added javascript client app to user pool. Client app id: "
            + javascriptClientAppId);

        // Create the Cognito identity pool with the specified name
        logger.log("Cognito identity pool with name: " + identityPoolName
            + " does not exist, so creating it");
        CreateIdentityPoolRequest createIdentityPoolRequest = new CreateIdentityPoolRequest();
        createIdentityPoolRequest.setIdentityPoolName(identityPoolName);
        createIdentityPoolRequest.setAllowUnauthenticatedIdentities(true);
        CreateIdentityPoolResult pool = identityClient
            .createIdentityPool(createIdentityPoolRequest);
        identityPoolId = pool.getIdentityPoolId();
        // Add roles to the pool
        addRolesToIdentityPool(unauthenticatedRoleName, unauthenticatedRole, authenticatedRoleName,
            authenticatedRole, identityPoolId, identityClient, logger);
        logger.log("Created identity pool with id: " + identityPoolId);

        // Sign-up and confirm our Admin user with this user pool
        logger.log("Signing-up admin user with user pool");
        SignUpRequest signUpRequest = new SignUpRequest();
        signUpRequest.setClientId(adminClientAppId);
        // Set username and password
        signUpRequest.setUsername("admin");
        signUpRequest.setPassword(adminPassword);
        // Set some attributes for the admin user
        List<AttributeType> userAttributes = new ArrayList<>();
        AttributeType nameAttribute = new AttributeType();
        nameAttribute.setName("given_name");
        nameAttribute.setValue("Squash bookings service administrator");
        userAttributes.add(nameAttribute);
        AttributeType emailAttribute = new AttributeType();
        emailAttribute.setName("email");
        emailAttribute.setValue(adminEmail);
        userAttributes.add(emailAttribute);
        signUpRequest.setUserAttributes(userAttributes);
        providerClient.signUp(signUpRequest);
        logger.log("Signed-up admin user with user pool");

        logger.log("Confirming admin user with user pool");
        // N.B. May also need to verify email to enable forgotten password flow?
        AdminConfirmSignUpRequest adminConfirmSignUpRequest = new AdminConfirmSignUpRequest();
        adminConfirmSignUpRequest.setUserPoolId(userPoolId);
        adminConfirmSignUpRequest.setUsername("admin");
        providerClient.adminConfirmSignUp(adminConfirmSignUpRequest);
        logger.log("Confirmed admin user with user pool");

        // Set user pool identity providers in the identity pool. This allows
        // Cognito to dish out temporary credentials for users who are
        // authenticated with our user pool (although currently only the admin
        // user will exist).
        logger.log("Setting user pool identity providers with the identity pool");
        List<CognitoIdentityProvider> cognitoIdentityProviders = new ArrayList<>();
        providerName = "cognito-idp." + region + ".amazonaws.com/" + userPoolId;
        logger.log("Using identity provider name: " + providerName);
        CognitoIdentityProvider cognitoIdentityProviderAdmin = new CognitoIdentityProvider();
        cognitoIdentityProviderAdmin.setClientId(adminClientAppId);
        cognitoIdentityProviderAdmin.setProviderName(providerName);
        cognitoIdentityProviders.add(cognitoIdentityProviderAdmin);
        CognitoIdentityProvider cognitoIdentityProviderJavascript = new CognitoIdentityProvider();
        cognitoIdentityProviderJavascript.setClientId(javascriptClientAppId);
        cognitoIdentityProviderJavascript.setProviderName(providerName);
        cognitoIdentityProviders.add(cognitoIdentityProviderJavascript);
        // Add these providers to the identity pool
        UpdateIdentityPoolRequest updateIdentityPoolRequest = new UpdateIdentityPoolRequest();
        updateIdentityPoolRequest.setCognitoIdentityProviders(cognitoIdentityProviders);
        updateIdentityPoolRequest.setAllowUnauthenticatedIdentities(true);
        updateIdentityPoolRequest.setIdentityPoolId(identityPoolId);
        updateIdentityPoolRequest.setIdentityPoolName(identityPoolName);
        identityClient.updateIdentityPool(updateIdentityPoolRequest);
        logger.log("Set user pool identity providers with the identity pool");

        // Encode identities of both our pools in the physical resource id
        String physicalResourceId = "Identity pool id: " + identityPoolId + ", User pool id: "
            + userPoolId;
        logger.log("Setting physical resource id to: " + physicalResourceId);
        cloudFormationResponder.setPhysicalResourceId(physicalResourceId);

      } else if (requestType.equals("Update")) {
        // Updates will only ever be to the 2 pool roles - so just replace them
        logger.log("Updating the Cognito identity pool roles");
        String physicalResourceId = standardRequestParameters.get("PhysicalResourceId");
        cloudFormationResponder.setPhysicalResourceId(physicalResourceId);

        Map<String, String> poolIds = getPoolIdsFromPhysicalResourceId(physicalResourceId, logger);
        identityPoolId = poolIds.get("IdentityPoolId");
        userPoolId = poolIds.get("UserPoolId");
        providerName = "cognito-idp." + region + ".amazonaws.com/" + userPoolId;
        addRolesToIdentityPool(unauthenticatedRoleName, unauthenticatedRole, authenticatedRoleName,
            authenticatedRole, identityPoolId, identityClient, logger);

      } else if (requestType.equals("Delete")) {

        // Early-out if this is a Delete corresponding to a failed attempt to
        // create a duplicate pool, otherwise we will end up wrongly deleting
        // our (valid) original pool instead.
        String physicalResourceId = standardRequestParameters.get("PhysicalResourceId");
        if (physicalResourceId.equals("DuplicatePhysicalResourceId")) {
          logger.log("Ignoring delete request as it's for a non-existent duplicate pool");
        } else {

          Map<String, String> poolIds = getPoolIdsFromPhysicalResourceId(physicalResourceId, logger);
          identityPoolId = poolIds.get("IdentityPoolId");
          userPoolId = poolIds.get("UserPoolId");

          // Check the Cognito Identity Pool does exist
          if (!identityPool.isPresent()) {
            logger.log("Error: Cognito identity pool with name: " + identityPoolName
                + " does not exist");
            return null;
          }
          // Delete the Cognito identity pool with the specified name
          logger.log("Deleting Cognito identity pool with name: " + identityPoolName);
          DeleteIdentityPoolRequest deleteIdentityPoolRequest = new DeleteIdentityPoolRequest();
          deleteIdentityPoolRequest.setIdentityPoolId(identityPoolId);
          identityClient.deleteIdentityPool(deleteIdentityPoolRequest);
          logger.log("Deleted Cognito identity pool");

          // Check the Cognito User Pool does exist
          if (!userPool.isPresent()) {
            logger.log("Error: Cognito user pool with name: " + identityPoolName
                + " does not exist");
            return null;
          }
          // Delete the Cognito user pool with the specified name
          logger.log("Deleting Cognito user pool with name: " + identityPoolName);
          DeleteUserPoolRequest deleteUserPoolRequest = new DeleteUserPoolRequest();
          deleteUserPoolRequest.setUserPoolId(userPoolId);
          providerClient.deleteUserPool(deleteUserPoolRequest);
          logger.log("Deleted Cognito user pool");

        }
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
      logger.log("Exception caught in Cognito Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      cloudFormationResponder.addKeyValueOutputsPair("CognitoIdentityPoolId", identityPoolId);
      cloudFormationResponder.addKeyValueOutputsPair("CognitoUserPoolId", userPoolId);
      cloudFormationResponder.addKeyValueOutputsPair("CognitoUserPoolIdentityProviderName",
          providerName);
      cloudFormationResponder
          .addKeyValueOutputsPair("JavascriptClientAppId", javascriptClientAppId);
      cloudFormationResponder.sendResponse(responseStatus, logger);
    }
  }

  Map<String, String> getPoolIdsFromPhysicalResourceId(String physicalResourceId,
      LambdaLogger logger) {
    // Extract identity and user pool ids from the physical resource id
    String identityPoolId = physicalResourceId.substring("Identity pool id: ".length(),
        physicalResourceId.indexOf(", User pool id: "));
    logger.log("Using identity pool id: " + identityPoolId);
    String userPoolIdMarker = ", User pool id: ";
    String userPoolId = physicalResourceId.substring(physicalResourceId.indexOf(userPoolIdMarker)
        + userPoolIdMarker.length(), physicalResourceId.length());
    logger.log("Using user pool id: " + userPoolId);
    Map<String, String> poolIds = new HashMap<>();
    poolIds.put("IdentityPoolId", identityPoolId);
    poolIds.put("UserPoolId", userPoolId);
    return poolIds;
  }

  void addRolesToIdentityPool(String unauthenticatedRoleName, String unauthenticatedRole,
      String authenticatedRoleName, String authenticatedRole, String identityPoolId,
      AmazonCognitoIdentity client, LambdaLogger logger) {
    // First update the roles to use the actual pool id in their conditions
    logger
        .log("Updating authenticated and unauthenticated roles to use the actual identity pool id: "
            + identityPoolId);
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