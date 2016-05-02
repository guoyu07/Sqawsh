/**
 * Copyright 2016 Robin Steel
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
import squash.deployment.lambdas.utils.IS3TransferManager;
import squash.deployment.lambdas.utils.LambdaInputLogger;
import squash.deployment.lambdas.utils.S3TransferManager;
import squash.deployment.lambdas.utils.TransferUtils;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;

/**
 * AWS Cloudformation custom resource to deploy the Angularjs squash app.
 * 
 * <p>
 * The Angularjs app needs to be unzipped and deployed to the website. Since
 * some constants that it uses are not known until the stack is created, this
 * resource modifies these constants to substitute the correct values, and then
 * deploys the unzipped, modified app to the website.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class AngularjsAppCustomResourceLambda implements
    RequestHandler<Map<String, Object>, Object> {

  /**
   * Returns an IS3TransferManager.
   * 
   * <p>
   * This method is provided so unit tests can mock out S3.
   */
  protected IS3TransferManager getS3TransferManager() {
    return new S3TransferManager();
  }

  /**
   * Implementation for the AWS Lambda function backing the AngularjsApp
   * resource.
   * 
   * <p>
   * This lambda has the following keys in its request map (in addition to the
   * standard ones) provided via the Cloudformation stack template:
   * <ul>
   * <li>WebsiteBucket - name of S3 bucket serving the booking website.</li>
   * <li>AngularjsZipBucket - S3 bucket holding the Angularjs app zip file.</li>
   * <li>CognitoIdentityPoolId - id of the Cognito Identity Pool.</li>
   * <li>ApiGatewayBaseUrl - base Url of the ApiGateway Api.</li>
   * <li>Region - the AWS region in which the Cloudformation stack is created.
   * </li>
   * <li>Revision - integer incremented to force stack updates to update this
   * resource.</li>
   * </ul>
   *
   * <p>On success, it returns the following output to Cloudformation:
   * <ul>
   *    <li>WebsiteURL - Url of the Angularjs website.</li>
   * </ul>
   *
   * <p>Updates will delete the previous deployment and replace it with the new one.
   *
   * @param request
   *            request parameters as provided by the CloudFormation service
   * @param context
   *            context as provided by the CloudFormation service
   */
  @Override
  public Object handleRequest(Map<String, Object> request, Context context) {

    LambdaLogger logger = context.getLogger();
    logger.log("Starting AngularjsApp custom resource handleRequest");

    // Handle standard request parameters
    Map<String, String> standardRequestParameters = LambdaInputLogger.logStandardRequestParameters(
        request, logger);
    String requestType = standardRequestParameters.get("RequestType");

    // Handle custom request parameters
    logger.log("Logging custom input parameters to custom resource request");
    @SuppressWarnings("unchecked")
    Map<String, Object> resourceProps = (Map<String, Object>) request.get("ResourceProperties");
    String websiteBucket = (String) resourceProps.get("WebsiteBucket");
    String angularjsZipBucket = (String) resourceProps.get("AngularjsZipBucket");
    String cognitoIdentityPoolId = (String) resourceProps.get("CognitoIdentityPoolId");
    String apiGatewayBaseUrl = (String) resourceProps.get("ApiGatewayBaseUrl");
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");

    // Log out our custom request parameters
    logger.log("WebsiteBucket: " + websiteBucket);
    logger.log("AngularjsZipBucket: " + angularjsZipBucket);
    logger.log("CognitoIdentityPoolId: " + cognitoIdentityPoolId);
    logger.log("ApiGatewayBaseUrl: " + apiGatewayBaseUrl);
    logger.log("Region: " + region);
    logger.log("Revision: " + revision);

    // Prepare our response to be sent in the finally block
    CloudFormationResponder cloudFormationResponder = new CloudFormationResponder(
        standardRequestParameters, "DummyPhysicalResourceId");
    // Initialise failure response, which will be changed on success
    String responseStatus = "FAILED";

    String websiteURL = null;
    try {
      if (requestType.equals("Create") || requestType.equals("Update")) {

        // On updates we clear out the app first
        if (requestType.equals("Update")) {
          deleteAngularjsApp(websiteBucket, logger);
        }

        // Get the Angularjs app's zip file
        try {
          logger.log("Downloading Angularjs zip from S3");
          IS3TransferManager transferManager = getS3TransferManager();
          String zipDownloadPath = "/tmp/AngularjsApp.zip";
          File downloadedFile = new File(zipDownloadPath);
          TransferUtils.waitForS3Transfer(
              transferManager.download(angularjsZipBucket, "AngularjsApp.zip", downloadedFile),
              logger);
          logger.log("Downloaded Angularjs zip successfully from S3");

          // Modify the BookingsService file to point to the correct
          // Cognito identity pool, ApiGateway base url, and region.
          logger.log("Extracting Angularjs zip");
          String extractPath = "/tmp";
          try {
            ZipFile zipFile = new ZipFile(zipDownloadPath);
            // Will produce /tmp/app/app.js etc
            zipFile.extractAll(extractPath);
          } catch (ZipException e) {
            logger.log("Caught a ZipException Exception: " + e.getMessage());
            throw e;
          }
          logger.log("Extracted Angularjs zip");

          logger
              .log("Modifying the BookingsService file to point to the correct Cognito identity pool, ApiGatewayBaseUrl, and region");
          String bookingsServiceContent;
          String bookingsServicePath = extractPath + "/app/components/bookings/bookingsService.js";
          try (FileInputStream inputStream = new FileInputStream(bookingsServicePath)) {
            bookingsServiceContent = IOUtils.toString(inputStream);
          }
          bookingsServiceContent = bookingsServiceContent
              .replace("var com_squash_region = 'stringtobereplaced'",
                  "var com_squash_region = '" + region + "'")
              .replace("var com_squash_identityPoolId = 'stringtobereplaced'",
                  "var com_squash_identityPoolId = '" + cognitoIdentityPoolId + "'")
              .replace("var com_squash_apiGatewayBaseUrl = 'stringtobereplaced'",
                  "var com_squash_apiGatewayBaseUrl = '" + apiGatewayBaseUrl + "'");
          FileUtils.writeStringToFile(new File(bookingsServicePath), bookingsServiceContent);
          logger
              .log("Modified the BookingsService file to point to the correct Cognito identity pool, ApiGatewayBaseUrl, and region");

          // Upload the modified app to the S3 website bucket
          logger.log("Uploading modified Angularjs app to S3 website bucket");
          // Will produce <S3BucketRoot>/app/app.js etc
          TransferUtils.waitForS3Transfer(transferManager.uploadDirectory(websiteBucket, "app",
              new File(extractPath + "/app"), true), logger);
          logger.log("Uploaded modified Angularjs app to S3 website bucket");

          // Page must be public so it can be served from the website
          logger.log("Modifying Angularjs app ACL in S3 website bucket");
          TransferUtils
              .setPublicReadPermissionsOnBucket(websiteBucket, Optional.of("app/"), logger);
          logger.log("Modified Angularjs app ACL in S3 website bucket");
        } catch (IOException ioe) {
          logger.log("Caught an IO Exception: " + ioe.getMessage());
          throw ioe;
        }

        websiteURL = "http://" + websiteBucket + ".s3-website-" + region
            + ".amazonaws.com/app/index.html";
      } else if (requestType.equals("Delete")) {
        logger.log("Delete request - so deleting the app");
        deleteAngularjsApp(websiteBucket, logger);
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
      logger.log("Exception caught in AngularjsApp Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      // Prepare a memory stream to append error messages to
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteArrayOutputStream);
      JSONObject outputs;
      try {
        outputs = new JSONObject().put("WebsiteURL", websiteURL);
      } catch (JSONException e) {
        e.printStackTrace(printStream);
        // Can do nothing more than log the error and return. Must rely
        // on CloudFormation timing-out since it won't get a response from
        // us.
        logger.log("Exception caught whilst constructing outputs: "
            + byteArrayOutputStream.toString() + ". Message: " + e.getMessage());
        return null;
      }
      cloudFormationResponder.sendResponse(responseStatus, outputs, logger);
    }
  }

  void deleteAngularjsApp(String websiteBucket, LambdaLogger logger) {
    logger.log("Removing AngularjsApp content from website versioned S3 bucket");

    // We need to delete every version of every key
    ListVersionsRequest listVersionsRequest = new ListVersionsRequest()
        .withBucketName(websiteBucket);
    VersionListing versionListing;

    AmazonS3 client = new TransferManager().getAmazonS3Client();
    do {
      versionListing = client.listVersions(listVersionsRequest);
      versionListing
          .getVersionSummaries()
          .stream()
          .filter(k -> (k.getKey().startsWith("app")))
          .forEach(
              k -> {
                logger.log("About to delete version: " + k.getVersionId()
                    + " of AngularjsApp page: " + k.getKey());
                DeleteVersionRequest deleteVersionRequest = new DeleteVersionRequest(websiteBucket,
                    k.getKey(), k.getVersionId());
                client.deleteVersion(deleteVersionRequest);
                logger.log("Successfully deleted version: " + k.getVersionId()
                    + " of AngularjsApp page: " + k.getKey());
              });

      listVersionsRequest.setKeyMarker(versionListing.getNextKeyMarker());
    } while (versionListing.isTruncated());
    logger.log("Finished removing AngularjsApp content from website S3 bucket");
  }
}