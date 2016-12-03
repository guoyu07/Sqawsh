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
import squash.deployment.lambdas.utils.ZipUtils;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteVersionRequest;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.VersionListing;
import com.amazonaws.services.s3.transfer.TransferManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AWS Cloudformation custom resource to deploy the Angularjs squash app.
 * 
 * <p>
 * The Angularjs app needs to be unzipped and deployed to the website. Since
 * some constants that it uses are not known until the stack is created, this
 * resource modifies these constants to substitute the correct values, and then
 * deploys the unzipped, modified app to the website. After the whole app is
 * unzipped, all js included via script tags, css, and html files are
 * individually zipped prior to upload to the website. This is bc S3 does
 * not automatically serve gzip-ed content, so we need to do the zip-ing.
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
   * <li>CognitoUserPoolId - id of the Cognito User Pool.</li>
   * <li>CognitoUserPoolIdentityProviderName - Name of user pool identity provider.</li>
   * <li>JavascriptClientAppId - id of the Cognito User Pool app to use from javascript.</li>
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
    String cognitoUserPoolId = (String) resourceProps.get("CognitoUserPoolId");
    String cognitoUserPoolIdentityProviderName = (String) resourceProps
        .get("CognitoUserPoolIdentityProviderName");
    String javascriptClientAppId = (String) resourceProps.get("JavascriptClientAppId");
    String apiGatewayBaseUrl = (String) resourceProps.get("ApiGatewayBaseUrl");
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");

    // Log out our custom request parameters
    logger.log("WebsiteBucket: " + websiteBucket);
    logger.log("AngularjsZipBucket: " + angularjsZipBucket);
    logger.log("CognitoIdentityPoolId: " + cognitoIdentityPoolId);
    logger.log("CognitoUserPoolId: " + cognitoUserPoolId);
    logger.log("CognitoUserPoolIdentityProviderName: " + cognitoUserPoolIdentityProviderName);
    logger.log("JavascriptClientAppId: " + javascriptClientAppId);
    logger.log("ApiGatewayBaseUrl: " + apiGatewayBaseUrl);
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

    String websiteURL = null;
    try {
      cloudFormationResponder.initialise();

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

          // Modify the Bookings and Identity Service files to point to the
          // correct Cognito data, ApiGateway base url, and region.
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
              .log("Modifying the Bookings and Identity Services to point to the correct ApiGatewayBaseUrl, Cognito data, and region");
          String fileContent;
          String filePath = extractPath + "/app/sqawsh.min.js";
          try (FileInputStream inputStream = new FileInputStream(filePath)) {
            fileContent = IOUtils.toString(inputStream);
          }
          fileContent = fileContent.replace("bookingregiontobereplaced", region)
              .replace("bookingurltobereplaced", apiGatewayBaseUrl)
              .replace("bookingbuckettobereplaced", websiteBucket)
              .replace("identityregiontobereplaced", region)
              .replace("identitypoolidtobereplaced", cognitoIdentityPoolId)
              .replace("identityuserpoolidtobereplaced", cognitoUserPoolId)
              .replace("identityprovidernametobereplaced", cognitoUserPoolIdentityProviderName)
              .replace("identityappidtobereplaced", javascriptClientAppId);

          FileUtils.writeStringToFile(new File(filePath), fileContent);
          logger
              .log("Modified the Bookings and Identity Services to point to the correct ApiGatewayBaseUrl, Cognito data, and region");

          // GZIP all js, css, and html files within app folder
          logger.log("GZip-ing files in app folder to enable serving gzip-ed from S3");
          ZipUtils.gzip(Arrays.asList(new File("/tmp/app")), Collections.emptyList(), logger);
          logger.log("GZip-ed files in app folder to enable serving gzip-ed from S3");

          // Upload the modified app to the S3 website bucket
          logger.log("Uploading modified Angularjs app to S3 website bucket");
          // Will produce <S3BucketRoot>/app/sqawsh.min.js etc
          TransferUtils.waitForS3Transfer(transferManager.uploadDirectory(websiteBucket, "app",
              new File(extractPath + "/app"), true), logger);
          logger.log("Uploaded modified Angularjs app to S3 website bucket");

          // Add gzip content-encoding metadata to zip-ed files
          logger.log("Updating metadata on modified Angularjs app in S3 bucket");
          TransferUtils.addGzipContentEncodingMetadata(websiteBucket, Optional.of("app"), logger);
          logger.log("Updated metadata on modified Angularjs app in S3 bucket");

          // Upload Cognito SDKs and their dependencies - these should all be
          // zipped first.
          logger.log("About to upload Cognito libraries");
          List<ImmutableTriple<String, String, byte[]>> cognitoLibraries = new ArrayList<>();
          cognitoLibraries
              .add(new ImmutableTriple<>(
                  "Cognito SDK",
                  "aws-cognito-sdk.min.js",
                  IOUtils
                      .toByteArray(new URL(
                          "https://raw.githubusercontent.com/aws/amazon-cognito-identity-js/master/dist/aws-cognito-sdk.min.js"))));
          cognitoLibraries
              .add(new ImmutableTriple<>(
                  "Cognito Identity SDK",
                  "amazon-cognito-identity.min.js",
                  IOUtils
                      .toByteArray(new URL(
                          "https://raw.githubusercontent.com/aws/amazon-cognito-identity-js/master/dist/amazon-cognito-identity.min.js"))));
          cognitoLibraries.add(new ImmutableTriple<>("Big Integer Library", "jsbn.js", IOUtils
              .toByteArray(new URL("http://www-cs-students.stanford.edu/~tjw/jsbn/jsbn.js"))));
          cognitoLibraries.add(new ImmutableTriple<>("Big Integer Library 2", "jsbn2.js", IOUtils
              .toByteArray(new URL("http://www-cs-students.stanford.edu/~tjw/jsbn/jsbn2.js"))));

          // The SJCL still seems to need configuring to include the bytes
          // codec, despite 1.0 of Cognito Idp saying it had removed this
          // dependency. So for now we get this bytes-codec-configured version
          // from our resources.
          String sjcl_library;
          try {
            sjcl_library = IOUtils.toString(AngularjsAppCustomResourceLambda.class
                .getResourceAsStream("/squash/deployment/lambdas/sjcl.js"));
          } catch (IOException e) {
            logger.log("Exception caught reading sjcl.js file: " + e.getMessage());
            throw new Exception("Exception caught reading sjcl.js file");
          }
          logger.log("Read modified SJCL library from resources");
          cognitoLibraries.add(new ImmutableTriple<>("Stanford Javascript Crypto Library",
              "sjcl.js", sjcl_library.getBytes(Charset.forName("UTF-8"))));

          for (ImmutableTriple<String, String, byte[]> cognitoLibrary : cognitoLibraries) {
            logger.log("Uploading a Cognito library to S3 website bucket. Library name: "
                + cognitoLibrary.left);

            byte[] zippedLibrary = ZipUtils.gzip(cognitoLibrary.right, logger);
            ByteArrayInputStream libraryAsGzippedStream = new ByteArrayInputStream(zippedLibrary);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(zippedLibrary.length);
            metadata.setContentEncoding("gzip");
            String keyName = "app/components/identity/cognito/" + cognitoLibrary.middle;
            logger.log("Uploading to key: " + keyName);
            PutObjectRequest putObjectRequest = new PutObjectRequest(websiteBucket, keyName,
                libraryAsGzippedStream, metadata);
            TransferUtils.waitForS3Transfer(transferManager.upload(putObjectRequest), logger);
            logger.log("Uploaded a Cognito library to S3 website bucket: " + cognitoLibrary.left);
          }

          // App content must be public so it can be served from the website
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
      cloudFormationResponder.addKeyValueOutputsPair("WebsiteURL", websiteURL);
      cloudFormationResponder.sendResponse(responseStatus, logger);
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