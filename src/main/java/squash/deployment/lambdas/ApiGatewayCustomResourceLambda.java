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

import squash.booking.lambdas.GetBookingsLambda;
import squash.deployment.lambdas.utils.CloudFormationResponder;
import squash.deployment.lambdas.utils.ExceptionUtils;
import squash.deployment.lambdas.utils.IS3TransferManager;
import squash.deployment.lambdas.utils.LambdaInputLogger;
import squash.deployment.lambdas.utils.S3TransferManager;
import squash.deployment.lambdas.utils.TransferUtils;

import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.model.CreateDeploymentRequest;
import com.amazonaws.services.apigateway.model.CreateDeploymentResult;
import com.amazonaws.services.apigateway.model.CreateResourceRequest;
import com.amazonaws.services.apigateway.model.CreateResourceResult;
import com.amazonaws.services.apigateway.model.CreateRestApiRequest;
import com.amazonaws.services.apigateway.model.CreateRestApiResult;
import com.amazonaws.services.apigateway.model.DeleteResourceRequest;
import com.amazonaws.services.apigateway.model.DeleteRestApiRequest;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.GetResourcesResult;
import com.amazonaws.services.apigateway.model.GetRestApiRequest;
import com.amazonaws.services.apigateway.model.GetRestApiResult;
import com.amazonaws.services.apigateway.model.GetRestApisRequest;
import com.amazonaws.services.apigateway.model.GetRestApisResult;
import com.amazonaws.services.apigateway.model.GetSdkRequest;
import com.amazonaws.services.apigateway.model.GetSdkResult;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodResult;
import com.amazonaws.services.apigateway.model.RestApi;
import com.amazonaws.services.kinesisfirehose.model.InvalidArgumentException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteVersionRequest;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.VersionListing;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.google.common.io.CharStreams;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.security.InvalidParameterException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Manages the AWS Cloudformation ApiGateway custom resource.
 * 
 * <p>The ApiGateway Api is created, updated, and deleted by Cloudformation
 *    using a custom resource backed by this lambda function.
 *    
 * <p>After it creates/updates the Api, it generates a corresponding Javascript
 *    SDK for it and uploads this SDK to the website bucket.
 *    
 * <p>N.B. It would be cleaner to create this Api from an AWS-extended Swagger spec, but
 *    the java sdk does not yet support this.
 * 
 * <p>Stack updates will deploy the new api to the same stage as the old api.
 * 
 * <p>N.B. You should create at most one ApiGateway custom resource per stack.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class ApiGatewayCustomResourceLambda implements RequestHandler<Map<String, Object>, Object> {

  String squashWebsiteBucket;

  /**
   * Returns an IS3TransferManager.
   * 
   * <p>This method is provided so unit tests can mock out S3.
   */
  public IS3TransferManager getS3TransferManager() {
    return new S3TransferManager();
  }

  /**
   * Implementation for the AWS Lambda function backing the ApiGateway resource.
   * 
   * <p>This lambda has the following keys in its request map (in addition
   *    to the standard ones) provided via the Cloudformation stack template:
   * 
   * <p>Keys suppling arn of other AWS lambda functions that the Api invokes:
   * <ul>
   *    <li>ValidDatesGETLambdaURI</li>
   *    <li>BookingsGETLambdaURI</li>
   *    <li>BookingsPUTDELETELambdaURI</li>
   * </ul>
   * 
   * <p>Other keys:
   * <ul>
   *    <li>BookingsApiGatewayInvocationRole - role allowing Api to invoke these three lambda functions.</li>
   *    <li>WebsiteBucket - name of S3 bucket serving the booking website.</li>
   *    <li>Stage Name - the name to give to the Api's stage.</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   *    <li>Revision - integer incremented to force stack updates to update this resource.</li>
   * </ul>
   * 
   * <p>On success, it returns the following output to Cloudformation:
   * <ul>
   *    <li>ApiGatewayBaseUrl - base Url of the created ApiGateway Api.</li>
   * </ul>
   *
   * @param request request parameters as provided by the CloudFormation service
   * @param context context as provided by the CloudFormation service
   */
  @Override
  public Object handleRequest(Map<String, Object> request, Context context) {

    LambdaLogger logger = context.getLogger();
    logger.log("Starting ApiGateway custom resource handleRequest");

    // Handle standard request parameters
    Map<String, String> standardRequestParameters = LambdaInputLogger.logStandardRequestParameters(
        request, logger);
    String requestType = standardRequestParameters.get("RequestType");

    // Handle custom request parameters
    logger.log("Logging custom input parameters to custom resource request");
    @SuppressWarnings("unchecked")
    Map<String, Object> resourceProps = (Map<String, Object>) request.get("ResourceProperties");
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");
    String validDatesGETLambdaURI = wrapURI(((String) resourceProps.get("ValidDatesGETLambdaURI")),
        region);
    String bookingsGETLambdaURI = wrapURI(((String) resourceProps.get("BookingsGETLambdaURI")),
        region);
    String bookingsPUTDELETELambdaURI = wrapURI(
        ((String) resourceProps.get("BookingsPUTDELETELambdaURI")), region);
    String bookingsApiGatewayInvocationRole = (String) resourceProps
        .get("BookingsApiGatewayInvocationRole");
    squashWebsiteBucket = (String) resourceProps.get("WebsiteBucket");
    String stageName = (String) resourceProps.get("Stage Name");

    // Log out our custom request parameters
    logger.log("Logging custom parameters to ApiGateway custom resource request:");
    logger.log("ValidDatesGETLambdaURI: " + validDatesGETLambdaURI);
    logger.log("BookingsGETLambdaURI: " + bookingsGETLambdaURI);
    logger.log("BookingsPUTDELETELambdaURI: " + bookingsPUTDELETELambdaURI);
    logger.log("BookingsApiGatewayInvocationRole: " + bookingsApiGatewayInvocationRole);
    logger.log("Squash website bucket: " + squashWebsiteBucket);
    logger.log("Stage Name: " + stageName);
    logger.log("Region: " + region);
    logger.log("Revision: " + revision);

    // Prepare our response to be sent in the finally block
    CloudFormationResponder cloudFormationResponder = new CloudFormationResponder(
        standardRequestParameters, "DummyPhysicalResourceId");
    // Initialise failure response, which will be changed on success
    String responseStatus = "FAILED";
    String apiGatewayBaseUrl = null;

    try {
      // Create ApiGateway client
      AmazonApiGateway apiGatewayClient = new AmazonApiGatewayClient();
      apiGatewayClient.setRegion(Region.getRegion(Regions.fromName(region)));

      String apiName = "SquashApi" + standardRequestParameters.get("StackId");
      logger.log("Setting api name to: " + apiName);
      if (requestType.equals("Create")) {

        // Ensure an API of the same name does not already exist - can happen
        // e.g. if wrongly put two of these custom resources in the stack
        // template.
        logger.log("Verifying an api with name: " + apiName + " does not already exist.");
        GetRestApisRequest getRestApisRequest = new GetRestApisRequest();
        GetRestApisResult apis = apiGatewayClient.getRestApis(getRestApisRequest);
        List<RestApi> apiList = apis.getItems();
        Boolean apiExists = apiList.stream().filter(api -> api.getName().equals(apiName))
            .findFirst().isPresent();
        if (apiExists) {
          logger.log(apiName
              + " exists already - creating another api with this name is not allowed");
          // Change physical id in responder - this is bc CloudFormation will
          // follow up this failed creation with a Delete request to clean up
          // - and we want to ignore that delete call - but not delete calls
          // for our original resource - and we use the PhysicalId to tell these
          // two cases apart.
          cloudFormationResponder.setPhysicalResourceId("DuplicatePhysicalResourceId");

          return null;
        }

        // Api does not already exist - so create it
        logger.log("Creating API");
        CreateRestApiRequest createRestApiRequest = new CreateRestApiRequest();
        createRestApiRequest.setName(apiName);
        createRestApiRequest.setDescription("Api for managing squash court bookings");
        CreateRestApiResult createRestApiResult = apiGatewayClient
            .createRestApi(createRestApiRequest);
        String restApiId = createRestApiResult.getId();
        cloudFormationResponder.setPhysicalResourceId(restApiId);

        // Add all resources and methods to the Api, then upload its SDK to S3.
        constructApiAndUploadSdk(restApiId, apiGatewayClient, region, validDatesGETLambdaURI,
            bookingsGETLambdaURI, bookingsPUTDELETELambdaURI, bookingsApiGatewayInvocationRole,
            stageName, logger);

        apiGatewayBaseUrl = getApiGatewayBaseUrl(restApiId, region, stageName);
        logger.log("Created API with base url: " + apiGatewayBaseUrl);

      } else if (requestType.equals("Update")) {
        // We update an Api by removing all existing resources from it and then
        // adding them back, whilst retaining the original ApiId. This ensures
        // that its deployment creates a new entry in the deployment history of
        // the Api - i.e. it mimics editing and re-deploying the api in the
        // console.

        String restApiId = standardRequestParameters.get("PhysicalResourceId");
        // Keep same physical id - otherwise CloudFormation thinks it needs to
        // follow up with a Delete request on the 'previous' physical resource.
        cloudFormationResponder.setPhysicalResourceId(restApiId);
        logger.log("Updating Api for apiId: " + restApiId);

        // Remove all existing resources (except the root) from this Api
        logger.log("Removing existing resources from Api with apiId: " + restApiId);
        GetResourcesRequest getResourcesRequest = new GetResourcesRequest();
        getResourcesRequest.setRestApiId(restApiId);
        GetResourcesResult getResourcesResult = apiGatewayClient.getResources(getResourcesRequest);
        getResourcesResult.getItems().stream().filter(r -> !r.getPath().equals("/"))
            .forEach(resource -> {
              logger.log("About to delete resource: " + resource.getPath());
              DeleteResourceRequest deleteResourceRequest = new DeleteResourceRequest();
              deleteResourceRequest.setRestApiId(restApiId);
              deleteResourceRequest.setResourceId(resource.getId());
              apiGatewayClient.deleteResource(deleteResourceRequest);
              logger.log("Successfully deleted resource: " + resource.getPath());
            });

        // Remove the existing SDK from the S3 website bucket
        removeSdkFromS3(logger);

        // And add back the updated set of resources and SDK
        logger.log("Adding back updated resources to Api with apiId: " + restApiId);
        constructApiAndUploadSdk(restApiId, apiGatewayClient, region, validDatesGETLambdaURI,
            bookingsGETLambdaURI, bookingsPUTDELETELambdaURI, bookingsApiGatewayInvocationRole,
            stageName, logger);

        apiGatewayBaseUrl = getApiGatewayBaseUrl(restApiId, region, stageName);
        logger.log("Updated API with base url: " + apiGatewayBaseUrl);

      } else if (requestType.equals("Delete")) {

        String restApiId = standardRequestParameters.get("PhysicalResourceId");
        logger.log("Deleting Api for apiId: " + restApiId);

        // Early-out if this is a Delete corresponding to a failed attempt to
        // create a duplicate API, otherwise we will end up wrongly deleting
        // our (valid) original API instead.
        if (restApiId.equals("DuplicatePhysicalResourceId")) {
          logger.log("Ignoring delete request as it's for a non-existent duplicate API");
        } else {
          // Delete the API
          GetRestApiRequest getRestApiRequest = new GetRestApiRequest();
          getRestApiRequest.setRestApiId(restApiId);
          // This will throw if the api does not exist
          GetRestApiResult api = apiGatewayClient.getRestApi(getRestApiRequest);
          DeleteRestApiRequest deleteRestApiRequest = new DeleteRestApiRequest();
          deleteRestApiRequest.setRestApiId(api.getId());
          apiGatewayClient.deleteRestApi(deleteRestApiRequest);

          // Remove the sdk from the website bucket
          removeSdkFromS3(logger);
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
      logger.log("Exception caught in ApiGateway Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      // Prepare a memory stream to append error messages to
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteArrayOutputStream);
      JSONObject outputs;
      try {
        outputs = new JSONObject().put("ApiGatewayBaseUrl", apiGatewayBaseUrl);
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

  String getApiGatewayBaseUrl(String restApiId, String region, String stageName) {
    return "https://" + restApiId + ".execute-api." + region + ".amazonaws.com/" + stageName;
  }

  // This method is factored out so it can be called both when first creating
  // the api, and when recreating it during stack updates.
  void constructApiAndUploadSdk(String restApiId, AmazonApiGateway apiGatewayClient, String region,
      String validDatesGETLambdaURI, String bookingsGETLambdaURI,
      String bookingsPUTDELETELambdaURI, String bookingsApiGatewayInvocationRole, String stageName,
      LambdaLogger logger) throws Exception {
    // Create the API's resources
    logger.log("Creating API resources");
    String validDates = createTopLevelResourceOnApi("validdates", restApiId, apiGatewayClient,
        logger).getId();
    String bookings = createTopLevelResourceOnApi("bookings", restApiId, apiGatewayClient, logger)
        .getId();
    String reservationForm = createTopLevelResourceOnApi("reservationform", restApiId,
        apiGatewayClient, logger).getId();
    String cancellationForm = createTopLevelResourceOnApi("cancellationform", restApiId,
        apiGatewayClient, logger).getId();

    // Create the API's methods
    logger.log("Creating API methods");
    Map<String, String> extraParameters = new HashMap<>();

    // Methods on the validdates resource
    logger.log("Creating methods on validdates resource");
    extraParameters.put("ValidDatesGETLambdaURI", validDatesGETLambdaURI);
    extraParameters.put("BookingsGETLambdaURI", bookingsGETLambdaURI);
    extraParameters.put("BookingsPUTDELETELambdaURI", bookingsPUTDELETELambdaURI);
    extraParameters.put("BookingsApiGatewayInvocationRole", bookingsApiGatewayInvocationRole);
    createMethodOnResource("ValidDatesGET", validDates, restApiId, extraParameters,
        apiGatewayClient, region, logger);
    createMethodOnResource("ValidDatesOPTIONS", validDates, restApiId, extraParameters,
        apiGatewayClient, region, logger);

    // Methods on the bookings resource
    logger.log("Creating methods on bookings resource");
    createMethodOnResource("BookingsGET", bookings, restApiId, extraParameters, apiGatewayClient,
        region, logger);
    createMethodOnResource("BookingsDELETE", bookings, restApiId, extraParameters,
        apiGatewayClient, region, logger);
    createMethodOnResource("BookingsPUT", bookings, restApiId, extraParameters, apiGatewayClient,
        region, logger);
    createMethodOnResource("BookingsPOST", bookings, restApiId, extraParameters, apiGatewayClient,
        region, logger);
    createMethodOnResource("BookingsOPTIONS", bookings, restApiId, extraParameters,
        apiGatewayClient, region, logger);

    // Methods on the reservationform resource
    logger.log("Creating methods on reservationform resource");
    createMethodOnResource("ReservationformGET", reservationForm, restApiId, extraParameters,
        apiGatewayClient, region, logger);
    createMethodOnResource("ReservationformOPTIONS", reservationForm, restApiId, extraParameters,
        apiGatewayClient, region, logger);

    // Methods on the cancellationform resource
    logger.log("Creating methods on cancellationform resource");
    createMethodOnResource("CancellationformGET", cancellationForm, restApiId, extraParameters,
        apiGatewayClient, region, logger);
    createMethodOnResource("CancellationformOPTIONS", cancellationForm, restApiId, extraParameters,
        apiGatewayClient, region, logger);

    // Deploy the api to a stage (with default throttling settings)
    logger.log("Deploying API to stage: " + stageName);
    CreateDeploymentRequest createDeploymentRequest = new CreateDeploymentRequest();
    createDeploymentRequest.setCacheClusterEnabled(false);
    createDeploymentRequest.setDescription("A deployment of the Squash api");
    createDeploymentRequest.setStageDescription("A stage for the Squash api");
    createDeploymentRequest.setStageName(stageName);
    createDeploymentRequest.setRestApiId(restApiId);
    CreateDeploymentResult createDeploymentResult = apiGatewayClient
        .createDeployment(createDeploymentRequest);
    logger.log("Deployed to stage with ID: " + createDeploymentResult.getId());

    // FIXME
    // Throttle all methods on this stage - does not seem to work yet?
    // logger.log("Throttling all of stage's methods");
    // GetStagesRequest getStagesRequest = new GetStagesRequest();
    // getStagesRequest.setRestApiId(restApiId);
    // GetStagesResult getStagesResult =
    // apiGatewayClient.getStages(getStagesRequest);
    // List<Stage> stages = getStagesResult.getItem();
    // Stage stage = stages.stream().filter(s ->
    // s.getStageName().equals(stageName)).findFirst().get();
    // MethodSetting methodSetting = new MethodSetting();
    // methodSetting.setThrottlingBurstLimit(10);
    // methodSetting.setThrottlingRateLimit(1.0);
    // stage.addMethodSettingsEntry("*/*", methodSetting); // Adds to all
    // methods
    // logger.log("Throttling completed");

    // Download javascript sdk and upload it to the S3 bucket serving the
    // squash site
    logger.log("Downloading Javascript SDK");
    GetSdkRequest getSdkRequest = new GetSdkRequest();
    getSdkRequest.setRestApiId(restApiId);
    getSdkRequest.setStageName(stageName);
    getSdkRequest.setSdkType("JavaScript");
    // This is for Android sdks but it crashes if the map is empty - so set
    // to something
    Map<String, String> paramsMap = new HashMap<>();
    paramsMap.put("GroupID", "Dummy");
    getSdkRequest.setParameters(paramsMap);
    GetSdkResult getSdkResult = apiGatewayClient.getSdk(getSdkRequest);

    // Copy the sdk to S3 via AWS lambda's temporary file system
    logger.log("Copying Javascript SDK to S3");
    try {
      logger.log("Saving SDK to lambda's temporary file system");
      ByteBuffer sdkBuffer = getSdkResult.getBody().asReadOnlyBuffer();
      try (WritableByteChannel channel = Channels.newChannel(new FileOutputStream("/tmp/sdk.zip"))) {
        channel.write(sdkBuffer);
      }
      // Unzip the sdk
      logger.log("SDK saved. Now unzipping");
      String outputFolder = "/tmp/extractedSdk";
      ZipFile zipFile = new ZipFile("/tmp/sdk.zip");
      try {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          logger.log("Unzipping next entry: " + entry.getName());
          File entryDestination = new File(outputFolder, entry.getName());
          if (entry.isDirectory()) {
            entryDestination.mkdirs();
          } else {
            entryDestination.getParentFile().mkdirs();
            InputStream in = zipFile.getInputStream(entry);
            OutputStream out = new FileOutputStream(entryDestination);
            IOUtils.copy(in, out);
            IOUtils.closeQuietly(in);
            out.close();
          }
        }
      } finally {
        zipFile.close();
      }

      // Upload the sdk from the temporary filesystem to S3.
      logger.log("Uploading unzipped Javascript SDK to S3 bucket: " + squashWebsiteBucket);
      TransferUtils.waitForS3Transfer(new TransferManager().uploadDirectory(squashWebsiteBucket,
          "", new File("/tmp/extractedSdk/apiGateway-js-sdk"), true), logger);
      logger.log("Uploaded sdk successfully to S3");

      logger.log("Setting public read permission on uploaded sdk");
      TransferUtils.setPublicReadPermissionsOnBucket(squashWebsiteBucket, Optional.empty(), logger);
      logger.log("Finished setting public read permissions on uploaded sdk");
    } catch (Exception e) {
      logger.log("Exception caught whilst copying Javascript SDK to S3: " + e.getMessage());
      throw e;
    }
  }

  void removeSdkFromS3(LambdaLogger logger) {
    logger.log("About to remove apigateway sdk from website versioned S3 bucket");
    // We need to delete every version of every key
    ListVersionsRequest listVersionsRequest = new ListVersionsRequest()
        .withBucketName(squashWebsiteBucket);
    VersionListing versionListing;
    IS3TransferManager transferManager = getS3TransferManager();
    AmazonS3 client = transferManager.getAmazonS3Client();
    do {
      versionListing = client.listVersions(listVersionsRequest);
      versionListing
          .getVersionSummaries()
          .stream()
          .filter(
              k -> !(k.getKey().startsWith("20") || k.getKey().equals("today.html") || k.getKey()
                  .equals("bookings.html")))
          .forEach(
              k -> {
                logger.log("About to delete version: " + k.getVersionId() + " of API SDK: "
                    + k.getKey());
                DeleteVersionRequest deleteVersionRequest = new DeleteVersionRequest(
                    squashWebsiteBucket, k.getKey(), k.getVersionId());
                client.deleteVersion(deleteVersionRequest);
                logger.log("Successfully deleted version: " + k.getVersionId()
                    + " of API SDK key: " + k.getKey());
              });

      listVersionsRequest.setKeyMarker(versionListing.getNextKeyMarker());
    } while (versionListing.isTruncated());

    logger.log("Finished remove apigateway sdk from website S3 bucket");
  }

  void pause(LambdaLogger logger) {
    // Short sleep - this avoids the Too Many Requests error in this
    // custom resource when creating the cloudformation stack.
    try {
      Thread.sleep(1000); // ms
    } catch (InterruptedException e) {
      logger.log("Sleep interrupted in createMethodOnResource");
    }
  }

  CreateResourceResult createTopLevelResourceOnApi(String resourceName, String restApiId,
      AmazonApiGateway client, LambdaLogger logger) {
    logger.log("Creating top-level resource: " + resourceName);
    // Short sleep - this avoids the Too Many Requests error in this
    // custom resource when creating the cloudformation stack.
    pause(logger);
    CreateResourceRequest createResourceRequest = new CreateResourceRequest();
    createResourceRequest.setRestApiId(restApiId);
    if (resourceName.equals("bookings")) {
      createResourceRequest.setPathPart("bookings");
    } else if (resourceName.equals("validdates")) {
      createResourceRequest.setPathPart("validdates");
    } else if (resourceName.equals("reservationform")) {
      createResourceRequest.setPathPart("reservationform");
    } else if (resourceName.equals("cancellationform")) {
      createResourceRequest.setPathPart("cancellationform");
    } else {
      throw new InvalidParameterException("Invalid resource name: " + resourceName);
    }
    // Get the id of the parent resource
    GetResourcesRequest getResourcesRequest = new GetResourcesRequest();
    // High enough limit for now
    getResourcesRequest.setLimit(10);
    getResourcesRequest.setRestApiId(restApiId);
    GetResourcesResult resourcesResult = client.getResources(getResourcesRequest);
    String rootResourceId = resourcesResult.getItems().stream()
        .filter(resource -> resource.getPath().equals("/")).findFirst().get().getId();
    logger.log("Parent(root) resource id: " + rootResourceId);
    createResourceRequest.setParentId(rootResourceId);

    return client.createResource(createResourceRequest);
  }

  PutMethodResult createMethodOnResource(String methodName, String resourceId, String restApiId,
      Map<String, String> extraParameters, AmazonApiGateway client, String region,
      LambdaLogger logger) throws IOException {
    logger.log("Creating method: " + methodName + " on resource with id: " + resourceId);
    // Short sleep - this avoids the Too Many Requests error in this
    // custom resource when creating the cloudformation stack.
    pause(logger);
    // Variables for method request
    PutMethodRequest putMethodRequest = new PutMethodRequest();
    putMethodRequest.setAuthorizationType("None");
    putMethodRequest.setApiKeyRequired(false);
    putMethodRequest.setRestApiId(restApiId);
    putMethodRequest.setResourceId(resourceId);
    Map<String, Boolean> methodRequestParameters = new HashMap<>();

    // Variables for the happy-path method response
    PutMethodResponseRequest putMethod200ResponseRequest = new PutMethodResponseRequest();
    putMethod200ResponseRequest.setRestApiId(restApiId);
    putMethod200ResponseRequest.setResourceId(resourceId);
    putMethod200ResponseRequest.setStatusCode("200");

    // Variables for the server-error 500 method response. This response has the
    // same parameters as the 200 response.
    PutMethodResponseRequest putMethod500ResponseRequest = new PutMethodResponseRequest();
    putMethod500ResponseRequest.setRestApiId(restApiId);
    putMethod500ResponseRequest.setResourceId(resourceId);
    putMethod500ResponseRequest.setStatusCode("500");

    // Variables for the bad-request 400 method response. This response has the
    // same parameters as the 200 response.
    PutMethodResponseRequest putMethod400ResponseRequest = new PutMethodResponseRequest();
    putMethod400ResponseRequest.setRestApiId(restApiId);
    putMethod400ResponseRequest.setResourceId(resourceId);
    putMethod400ResponseRequest.setStatusCode("400");

    // Response models are used to specify the response Body schema - so we can
    // populate the values using the integration response template (when we have
    // one)
    Map<String, String> methodResponseModels = new HashMap<>();
    // Response parameters allows us to specify response headers
    Map<String, Boolean> methodResponseParameters = new HashMap<>();
    // Add CORS response headers to all method responses
    methodResponseParameters.put("method.response.header.access-control-allow-headers",
        Boolean.valueOf("true"));
    methodResponseParameters.put("method.response.header.access-control-allow-methods",
        Boolean.valueOf("true"));
    methodResponseParameters.put("method.response.header.access-control-allow-origin",
        Boolean.valueOf("true"));
    methodResponseParameters.put("method.response.header.content-type", Boolean.valueOf("true"));
    // Add header to prevent caching of booking pages
    methodResponseParameters.put("method.response.header.cache-control", Boolean.valueOf("true"));
    // Variables for integration input
    PutIntegrationRequest putIntegrationRequest = new PutIntegrationRequest();
    putIntegrationRequest.setRestApiId(restApiId);
    putIntegrationRequest.setResourceId(resourceId);
    // Request parameters follow pattern like:
    // "requestParameters" : {
    // "integration.request.path.integrationPathParam" :
    // "method.request.querystring.latitude",
    // "integration.request.querystring.integrationQueryParam" :
    // "method.request.querystring.longitude"
    // }
    Map<String, String> requestParameters = new HashMap<>();

    // Request templates follow pattern like:
    // "requestTemplates" : {
    // "application/json" : "json request template 2",
    // "application/xml" : "xml request template 2"
    // }
    Map<String, String> requestTemplates = new HashMap<>();

    // Variables for integration response
    // Configure the integration response for the happy-path case
    PutIntegrationResponseRequest putIntegration200ResponseRequest = new PutIntegrationResponseRequest();
    putIntegration200ResponseRequest.setRestApiId(restApiId);
    putIntegration200ResponseRequest.setResourceId(resourceId);
    putIntegration200ResponseRequest.setStatusCode("200");

    // Configure the integration response for the server-error 500 case. This
    // response has the same parameters as the 200 response.
    PutIntegrationResponseRequest putIntegration500ResponseRequest = new PutIntegrationResponseRequest();
    putIntegration500ResponseRequest.setRestApiId(restApiId);
    putIntegration500ResponseRequest.setResourceId(resourceId);
    putIntegration500ResponseRequest.setStatusCode("500");

    // Configure the integration response for the bad-request 400 case. This
    // response has the same parameters as the 200 response.
    PutIntegrationResponseRequest putIntegration400ResponseRequest = new PutIntegrationResponseRequest();
    putIntegration400ResponseRequest.setRestApiId(restApiId);
    putIntegration400ResponseRequest.setResourceId(resourceId);
    putIntegration400ResponseRequest.setStatusCode("400");
    // Response parameters follow pattern like:
    // "responseParameters" : {
    // "method.response.header.test-method-response-header" :
    // "integration.response.header.integrationResponseHeaderParam1"
    // }
    Map<String, String> responseParameters = new HashMap<>();
    // Add CORS response headers to all method responses
    responseParameters
        .put("method.response.header.access-control-allow-headers",
            "'content-type,x-amz-date,authorization,accept,x-amz-security-token,location,cache-control'");
    responseParameters.put("method.response.header.access-control-allow-origin", "'*'");
    responseParameters.put("method.response.header.content-type", "'text/html; charset=utf-8'");
    // Add no-cache header
    responseParameters.put("method.response.header.cache-control",
        "'no-store, no-cache, must-revalidate'");

    // Response templates follow pattern like:
    // "responseTemplates" : {
    // "application/json" : "json 200 response template",
    // "application/xml" : "xml 200 response template"
    // }
    Map<String, String> response200Templates = new HashMap<>();
    Map<String, String> response500Templates = new HashMap<>();
    Map<String, String> response400Templates = new HashMap<>();
    // Set as default response unless covered by other
    // PutIntegrationResponseInput-s
    putIntegration200ResponseRequest.setSelectionPattern(".*");

    // N.B. For now we encode the redirect Url after an error as part of the
    // errorMessage in exceptions thrown from Lambda. The templates below then
    // parse this Url out from the errorMessage, display the message alone for a
    // short time, then go to the redirect Url.
    String errorResponseMappingTemplate = "#set($inputRoot = $input.path('$'))\n"
        + "#set($httpIndex = $inputRoot.errorMessage.lastIndexOf('http'))\n"
        + "#set($redirectUrl = $inputRoot.errorMessage.substring($httpIndex))\n"
        + "<head>\n"
        + "<title>Grrr</title>\n"
        + "<meta http-equiv=\"refresh\" content=\"5;URL='$redirectUrl'\" />\n"
        + "</head>\n"
        + "<body>\n"
        + "$inputRoot.errorMessage.substring(0, $httpIndex)\n"
        + "<p align='left'>\n"
        + "<a href= '$redirectUrl'>Please click here if you are not redirected automatically within a few seconds</a>\n"
        + "</p>\n" + "</body>\n";

    PutMethodResult method = null;
    if (methodName.equals("ValidDatesGET")) {
      putMethodRequest.setHttpMethod("GET");
      putMethod200ResponseRequest.setHttpMethod("GET");
      putIntegration200ResponseRequest.setHttpMethod("GET");
      putMethod500ResponseRequest.setHttpMethod("GET");
      putIntegration500ResponseRequest.setHttpMethod("GET");
      method = client.putMethod(putMethodRequest);
      // N.B. Using LAMBDA type here is not yet supported by this sdk - so use
      // AWS instead.
      putIntegrationRequest.setType(IntegrationType.AWS);
      putIntegrationRequest.setUri(extraParameters.get("ValidDatesGETLambdaURI"));
      // N.B. Lambda uses POST even for GET methods
      putIntegrationRequest.setHttpMethod("GET");
      putIntegrationRequest.setCredentials(extraParameters.get("BookingsApiGatewayInvocationRole"));
      response200Templates.put("application/json", "#set($inputRoot = $input.path('$'))\n"
          + "{ \"dates\": " + "#foreach($elem in $inputRoot)\n" + "    $elem\n"
          + "#if($foreach.hasNext),#end\n" + "#end\n" + "}");
      response500Templates.put("application/json", errorResponseMappingTemplate);
      responseParameters
          .put("method.response.header.access-control-allow-methods", "'GET,OPTIONS'");
      // Lambda exception message regex that we want mapped to the 500 response
      // .*The players names.*|.*password.*
      putIntegration500ResponseRequest
          .setSelectionPattern("Apologies - something has gone wrong. Please try again.");
    } else if (methodName.equals("ValidDatesOPTIONS")) {
      // OPTIONS method is required for CORS.
      putMethodRequest.setHttpMethod("OPTIONS");
      putMethod200ResponseRequest.setHttpMethod("OPTIONS");
      putIntegration200ResponseRequest.setHttpMethod("OPTIONS");
      method = client.putMethod(putMethodRequest);
      putIntegrationRequest.setType(IntegrationType.MOCK);
      putIntegrationRequest.setHttpMethod("OPTIONS");
      requestTemplates.put("application/json", "{\"statusCode\": 200}");
      responseParameters
          .put("method.response.header.access-control-allow-methods", "'GET,OPTIONS'");
    } else if (methodName.equals("BookingsGET")) {
      methodRequestParameters.put("method.request.querystring.date", Boolean.valueOf("true"));
      putMethodRequest.setRequestParameters(methodRequestParameters);
      putMethodRequest.setHttpMethod("GET");
      putMethod200ResponseRequest.setHttpMethod("GET");
      putMethod500ResponseRequest.setHttpMethod("GET");
      putMethod400ResponseRequest.setHttpMethod("GET");
      putIntegration500ResponseRequest.setHttpMethod("GET");
      putIntegration200ResponseRequest.setHttpMethod("GET");
      putIntegration400ResponseRequest.setHttpMethod("GET");

      method = client.putMethod(putMethodRequest);
      // N.B. Using LAMBDA type here is not yet supported by this sdk - so use
      // AWS instead.
      putIntegrationRequest.setType(IntegrationType.AWS);
      putIntegrationRequest.setUri(extraParameters.get("BookingsGETLambdaURI"));
      // N.B. Lambda uses POST even for GET methods
      putIntegrationRequest.setHttpMethod("GET");
      putIntegrationRequest.setCredentials(extraParameters.get("BookingsApiGatewayInvocationRole"));
      requestTemplates.put("application/json", "#set($inputRoot = $input.path('$'))\n" + "{\n"
          + "\"date\" : \"$input.params('date')\",\n" + "\"redirectUrl\" : \"http://"
          + squashWebsiteBucket + ".s3-website-" + region
          + ".amazonaws.com?selectedDate=${input.params('date')}.html\"\n" + "}");
      responseParameters.put("method.response.header.access-control-allow-methods",
          "'GET,PUT,DELETE,POST,OPTIONS'");
      response500Templates.put("application/json", errorResponseMappingTemplate);
      response400Templates.put("application/json", errorResponseMappingTemplate);
      putIntegration500ResponseRequest
          .setSelectionPattern("Apologies - something has gone wrong. Please try again.");
      putIntegration400ResponseRequest.setSelectionPattern("The booking date.*");
    } else if (methodName.equals("BookingsPUT")) {
      putMethodRequest.setHttpMethod("PUT");
      putMethod200ResponseRequest.setHttpMethod("PUT");
      putMethod500ResponseRequest.setHttpMethod("PUT");
      putMethod400ResponseRequest.setHttpMethod("PUT");
      putIntegration500ResponseRequest.setHttpMethod("PUT");
      putIntegration200ResponseRequest.setHttpMethod("PUT");
      putIntegration400ResponseRequest.setHttpMethod("PUT");
      method = client.putMethod(putMethodRequest);
      // N.B. Using LAMBDA type here is not yet supported by this sdk - so use
      // AWS instead.
      putIntegrationRequest.setType(IntegrationType.AWS);
      putIntegrationRequest.setUri(extraParameters.get("BookingsPUTDELETELambdaURI"));
      // N.B. Lambda uses POST even for GET methods
      putIntegrationRequest.setHttpMethod("PUT");
      putIntegrationRequest.setCredentials(extraParameters.get("BookingsApiGatewayInvocationRole"));
      response500Templates.put("application/json", errorResponseMappingTemplate);
      response400Templates.put("application/json", errorResponseMappingTemplate);
      responseParameters.put("method.response.header.access-control-allow-methods",
          "'GET,PUT,DELETE,POST,OPTIONS'");
      putIntegration500ResponseRequest
          .setSelectionPattern("Apologies - something has gone wrong. Please try again.");
      putIntegration400ResponseRequest
          .setSelectionPattern("The booking court.*|The booking time.*|The players names.*|The booking date.*|The password is incorrect.*|Booking creation failed.*|Booking cancellation failed.*");
    } else if (methodName.equals("BookingsDELETE")) {
      putMethodRequest.setHttpMethod("DELETE");
      putMethod200ResponseRequest.setHttpMethod("DELETE");
      putMethod500ResponseRequest.setHttpMethod("DELETE");
      putMethod400ResponseRequest.setHttpMethod("DELETE");
      putIntegration500ResponseRequest.setHttpMethod("DELETE");
      putIntegration200ResponseRequest.setHttpMethod("DELETE");
      putIntegration400ResponseRequest.setHttpMethod("DELETE");
      method = client.putMethod(putMethodRequest);
      // N.B. Using LAMBDA type here is not yet supported by this sdk - so use
      // AWS instead.
      putIntegrationRequest.setType(IntegrationType.AWS);
      putIntegrationRequest.setUri(extraParameters.get("BookingsPUTDELETELambdaURI"));
      // N.B. Lambda uses POST even for GET methods
      putIntegrationRequest.setHttpMethod("DELETE");
      putIntegrationRequest.setCredentials(extraParameters.get("BookingsApiGatewayInvocationRole"));
      response500Templates.put("application/json", errorResponseMappingTemplate);
      response400Templates.put("application/json", errorResponseMappingTemplate);
      responseParameters.put("method.response.header.access-control-allow-methods",
          "'GET,PUT,DELETE,POST,OPTIONS'");
      putIntegration500ResponseRequest
          .setSelectionPattern("Apologies - something has gone wrong. Please try again.");
      putIntegration400ResponseRequest
          .setSelectionPattern("The booking court.*|The booking time.*|The players names.*|The booking date.*|The password is incorrect.*|Booking creation failed.*|Booking cancellation failed.*");
    } else if (methodName.equals("BookingsPOST")) {
      // Redirect to the mutated booking page after creating or cancelling a
      // booking
      putMethod200ResponseRequest.setStatusCode("303");
      putMethodRequest.setHttpMethod("POST");
      putMethod500ResponseRequest.setHttpMethod("POST");
      putMethod400ResponseRequest.setHttpMethod("POST");
      putIntegration500ResponseRequest.setHttpMethod("POST");
      putMethod200ResponseRequest.setHttpMethod("POST");
      putIntegration400ResponseRequest.setHttpMethod("POST");
      // Define the 303 integration response (which by default will match any
      // response from lambda)
      putIntegration200ResponseRequest.setStatusCode("303");
      putIntegration200ResponseRequest.setHttpMethod("POST");
      method = client.putMethod(putMethodRequest);
      // N.B. Using LAMBDA type here is not yet supported by this sdk - so use
      // AWS instead.
      putIntegrationRequest.setType(IntegrationType.AWS);
      putIntegrationRequest.setUri(extraParameters.get("BookingsPUTDELETELambdaURI"));
      // N.B. Lambda uses POST even for GET methods
      putIntegrationRequest.setHttpMethod("POST");
      putIntegrationRequest.setCredentials(extraParameters.get("BookingsApiGatewayInvocationRole"));
      response500Templates.put("application/json", errorResponseMappingTemplate);
      response400Templates.put("application/json", errorResponseMappingTemplate);
      // Need to convert html-post body from url-encoded string to json (which
      // lambda likes)
      requestTemplates.put("application/x-www-form-urlencoded",
          getBookingPostRequestTemplate(logger));
      responseParameters.put("method.response.header.access-control-allow-methods",
          "'GET,PUT,DELETE,POST,OPTIONS'");
      methodResponseParameters.put("method.response.header.location", Boolean.valueOf("true"));
      // Redirect to the newly-mutated booking page in S3 if we have it in the
      // json from our lambda function which will have put the appropriate
      // redirectUrl into the body, unless there was an error. Recognised
      // errors should have a redirectUrl appended to the error message by my
      // lambdas(and so be handled by other status codes above). For errors I'm
      // not expecting, there will be no redirectUrl in the json from lambda.
      // In this last case, the template below will insert its own redirectUrl
      // into the body pointing to today's page - as a last-ditch failsafe.
      response200Templates
          .put(
              "application/json",
              "## Use quotes to get body into a string here (input.path('$') by itself would create a POJO in error cases)\n"
                  + "#set($lambdaBody = \"$input.path('$')\")\n"
                  + "#set($countRedirects = $lambdaBody.length() - $lambdaBody.replace(\"redirectUrl\", \"\").length())\n"
                  + "#if ($countRedirects != 0)\n"
                  + "## Success case with a redirectUrl - the Location header mapping will find it.\n"
                  + "## But just in case not, put in some redirecting html also.\n"
                  + "#set($redirectUrl = $input.path('$.redirectUrl'))\n"
                  + "#else\n"
                  + "## Unhandled error case: no redirect url from lambda, the Location header mapping will thus not find it.\n"
                  + "## We thus need to redirect to today's page by providing suitable html instead\n"
                  + "#set($redirectUrl = \"http://"
                  + squashWebsiteBucket
                  + ".s3-website-"
                  + region
                  + ".amazonaws.com/today.html\")\n"
                  + "#end\n"
                  + "<head>\n"
                  + "<title>Grrr</title>\n"
                  + "<meta http-equiv=\"refresh\" content=\"5;URL='$redirectUrl'\" />\n"
                  + "</head>\n"
                  + "<body>\n"
                  + "Apologies - something has gone wrong. Please try again.\n"
                  + "<p align='left'>\n"
                  + "<a href= '$redirectUrl'>Please click here if you are not redirected automatically within a few seconds</a>\n"
                  + "</p>\n" + "</body>\n");
      responseParameters.put("method.response.header.location",
          "integration.response.body.redirectUrl");
      putIntegration500ResponseRequest
          .setSelectionPattern("Apologies - something has gone wrong. Please try again.");
      putIntegration400ResponseRequest
          .setSelectionPattern("The booking court.*|The booking time.*|The players names.*|The booking date.*|The password is incorrect.*|Booking creation failed.*|Booking cancellation failed.*");
    } else if (methodName.equals("BookingsOPTIONS")) {
      // OPTIONS method is required for CORS.
      putMethodRequest.setHttpMethod("OPTIONS");
      putMethod200ResponseRequest.setHttpMethod("OPTIONS");
      putIntegration200ResponseRequest.setHttpMethod("OPTIONS");
      method = client.putMethod(putMethodRequest);
      putIntegrationRequest.setType(IntegrationType.MOCK);
      putIntegrationRequest.setHttpMethod("OPTIONS");
      requestTemplates.put("application/json", "{\"statusCode\": 200}");
      responseParameters.put("method.response.header.access-control-allow-methods",
          "'GET,PUT,DELETE,POST,OPTIONS'");
    } else if (methodName.equals("ReservationformGET")) {
      methodRequestParameters.put("method.request.querystring.court", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.slot", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.slotLong", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.date", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.dateLong", Boolean.valueOf("true"));
      putMethodRequest.setRequestParameters(methodRequestParameters);
      putMethodRequest.setHttpMethod("GET");
      putMethod200ResponseRequest.setHttpMethod("GET");
      putIntegration200ResponseRequest.setHttpMethod("GET");
      method = client.putMethod(putMethodRequest);
      putIntegrationRequest.setType(IntegrationType.MOCK);
      putIntegrationRequest.setHttpMethod("GET");

      requestTemplates.put("application/json", "{\"statusCode\": 200}");
      response200Templates.put("text/html", getReservationformResponseTemplate(region, logger));
      responseParameters
          .put("method.response.header.access-control-allow-methods", "'GET,OPTIONS'");
    } else if (methodName.equals("ReservationformOPTIONS")) {
      // OPTIONS method is required for CORS.
      putMethodRequest.setHttpMethod("OPTIONS");
      putMethod200ResponseRequest.setHttpMethod("OPTIONS");
      putIntegration200ResponseRequest.setHttpMethod("OPTIONS");
      method = client.putMethod(putMethodRequest);
      putIntegrationRequest.setType(IntegrationType.MOCK);
      putIntegrationRequest.setHttpMethod("OPTIONS");
      requestTemplates.put("application/json", "{\"statusCode\": 200}");
      responseParameters
          .put("method.response.header.access-control-allow-methods", "'GET,OPTIONS'");
    } else if (methodName.equals("CancellationformGET")) {
      methodRequestParameters.put("method.request.querystring.court", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.slot", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.slotLong", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.players", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.date", Boolean.valueOf("true"));
      methodRequestParameters.put("method.request.querystring.dateLong", Boolean.valueOf("true"));
      putMethodRequest.setRequestParameters(methodRequestParameters);
      putMethodRequest.setHttpMethod("GET");
      putMethod200ResponseRequest.setHttpMethod("GET");
      putIntegration200ResponseRequest.setHttpMethod("GET");
      method = client.putMethod(putMethodRequest);
      putIntegrationRequest.setType(IntegrationType.MOCK);
      putIntegrationRequest.setHttpMethod("GET");
      requestTemplates.put("application/json", "{\"statusCode\": 200}");
      response200Templates.put("text/html", getCancellationformResponseTemplate(region, logger));
      responseParameters
          .put("method.response.header.access-control-allow-methods", "'GET,OPTIONS'");
    } else if (methodName.equals("CancellationformOPTIONS")) {
      // OPTIONS method is required for CORS.
      putMethodRequest.setHttpMethod("OPTIONS");
      putMethod200ResponseRequest.setHttpMethod("OPTIONS");
      putIntegration200ResponseRequest.setHttpMethod("OPTIONS");
      method = client.putMethod(putMethodRequest);
      putIntegrationRequest.setType(IntegrationType.MOCK);
      putIntegrationRequest.setHttpMethod("OPTIONS");
      requestTemplates.put("application/json", "{\"statusCode\": 200}");
      responseParameters
          .put("method.response.header.access-control-allow-methods", "'GET,OPTIONS'");
    } else {
      throw new InvalidArgumentException("Invalid method name: " + methodName);
    }

    // Method response
    putMethod200ResponseRequest.setResponseModels(methodResponseModels);
    putMethod200ResponseRequest.setResponseParameters(methodResponseParameters);
    client.putMethodResponse(putMethod200ResponseRequest);
    // Integration input
    putIntegrationRequest.setRequestParameters(requestParameters);
    putIntegrationRequest.setRequestTemplates(requestTemplates);
    putIntegrationRequest.setIntegrationHttpMethod("POST");
    client.putIntegration(putIntegrationRequest);
    // Integration response
    putIntegration200ResponseRequest.setResponseParameters(responseParameters);
    putIntegration200ResponseRequest.setResponseTemplates(response200Templates);
    putIntegration200ResponseRequest.setSelectionPattern(".*");
    client.putIntegrationResponse(putIntegration200ResponseRequest);

    if (methodName.equals("ValidDatesGET")) {
      putMethod500ResponseRequest.setResponseModels(methodResponseModels);
      putMethod500ResponseRequest.setResponseParameters(methodResponseParameters);
      client.putMethodResponse(putMethod500ResponseRequest);
      putIntegration500ResponseRequest.setResponseParameters(responseParameters);
      putIntegration500ResponseRequest.setResponseTemplates(response500Templates);
      client.putIntegrationResponse(putIntegration500ResponseRequest);
    } else if (methodName.equals("BookingsGET") || methodName.equals("BookingsPUT")
        || methodName.equals("BookingsPOST") || methodName.equals("BookingsDELETE")) {
      putMethod500ResponseRequest.setResponseModels(methodResponseModels);
      putMethod500ResponseRequest.setResponseParameters(methodResponseParameters);
      client.putMethodResponse(putMethod500ResponseRequest);
      putMethod400ResponseRequest.setResponseModels(methodResponseModels);
      putMethod400ResponseRequest.setResponseParameters(methodResponseParameters);
      client.putMethodResponse(putMethod400ResponseRequest);
      putIntegration500ResponseRequest.setResponseParameters(responseParameters);
      putIntegration500ResponseRequest.setResponseTemplates(response500Templates);
      client.putIntegrationResponse(putIntegration500ResponseRequest);
      putIntegration400ResponseRequest.setResponseParameters(responseParameters);
      putIntegration400ResponseRequest.setResponseTemplates(response400Templates);
      client.putIntegrationResponse(putIntegration400ResponseRequest);
    }
    return method;
  }

  String getBookingPostRequestTemplate(LambdaLogger logger) throws IOException {
    // This template transforms the url-encoded POST body to JSON for lambda
    logger.log("About to add request template to transform POST body to JSON");

    // / Get the mapping template from our resources
    String mappingTemplate = null;
    try (InputStream stream = GetBookingsLambda.class
        .getResourceAsStream("/squash/booking/lambdas/BookingPostMappingTemplate.vm")) {
      logger.log("Reading BookingPostMappingTemplate.vm from resources");
      mappingTemplate = CharStreams.toString(new InputStreamReader(stream, "UTF-8"));
      logger.log("Mapping template read from resources: " + mappingTemplate);
    } catch (IOException e) {
      logger.log("Exception caught reading mapping template from resources: " + e.getMessage());
      throw e;
    }

    return mappingTemplate;
  }

  String getReservationformResponseTemplate(String region, LambdaLogger logger) {
    // This template constructs the reservation form using the URLs and the
    // request query parameters.

    logger.log("About to render reservation form response template");

    // Create the template by merging the data with the velocity template
    VelocityEngine engine = new VelocityEngine();
    // Use the classpath loader so Velocity finds our template
    Properties properties = new Properties();
    properties.setProperty("resource.loader", "class");
    properties.setProperty("class.resource.loader.class",
        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
    engine.init(properties);

    VelocityContext context = new VelocityContext();
    context.put("squashWebsiteBucket", squashWebsiteBucket);
    context.put("region", region);

    // Render the template
    StringWriter writer = new StringWriter();
    Template template = engine.getTemplate("squash/booking/lambdas/ReservationForm.vm", "utf-8");
    template.merge(context, writer);
    logger.log("Rendered reservation form response template: " + writer);

    return writer.toString();
  }

  String getCancellationformResponseTemplate(String region, LambdaLogger logger) {
    // This template constructs the cancellation form using the URLs and the
    // request query parameters.

    logger.log("About to render cancellation form response template");

    // Create the template by merging the data with the velocity template
    VelocityEngine engine = new VelocityEngine();
    // Use the classpath loader so Velocity finds our template
    Properties properties = new Properties();
    properties.setProperty("resource.loader", "class");
    properties.setProperty("class.resource.loader.class",
        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
    engine.init(properties);

    VelocityContext context = new VelocityContext();
    context.put("squashWebsiteBucket", squashWebsiteBucket);
    context.put("region", region);

    // Render the template
    StringWriter writer = new StringWriter();
    Template template = engine.getTemplate("squash/booking/lambdas/CancellationForm.vm", "utf-8");
    template.merge(context, writer);
    logger.log("Rendered cancellation form response template: " + writer);

    return writer.toString();
  }

  String wrapURI(String rawURI, String region) {
    return "arn:aws:apigateway:" + region + ":lambda:path/2015-03-31/functions/" + rawURI
        + "/invocations";
  }
}