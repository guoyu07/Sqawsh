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
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
   * This lambda requires the following environment variables:
   * <ul>
   * <li>WebsiteBucket - name of S3 bucket serving the booking website.</li>
   * <li>AngularjsZipBucket - S3 bucket holding the Angularjs app zip file.</li>
   * <li>CognitoIdentityPoolId - id of the Cognito Identity Pool.</li>
   * <li>CognitoUserPoolId - id of the Cognito User Pool.</li>
   * <li>CognitoUserPoolIdentityProviderName - Name of user pool identity provider.</li>
   * <li>JavascriptClientAppId - id of the Cognito User Pool app to use from javascript.</li>
   * <li>ApiGatewayBaseUrl - base Url of the ApiGateway Api.</li>
   * <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   * <li>Revision - integer incremented to force stack updates to update this resource.</li>
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

    // Handle required environment variables
    logger.log("Logging required environment variables for custom resource request");
    String websiteBucket = System.getenv("WebsiteBucket");
    String angularjsZipBucket = System.getenv("AngularjsZipBucket");
    String cognitoIdentityPoolId = System.getenv("CognitoIdentityPoolId");
    String cognitoUserPoolId = System.getenv("CognitoUserPoolId");
    String cognitoUserPoolIdentityProviderName = System
        .getenv("CognitoUserPoolIdentityProviderName");
    String javascriptClientAppId = System.getenv("JavascriptClientAppId");
    String apiGatewayBaseUrl = System.getenv("ApiGatewayBaseUrl");
    String region = System.getenv("AWS_REGION");
    String revision = System.getenv("Revision");

    // Log out our required environment variables
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

          // We will later modify the gzip-ed filenames to add a revving suffix.
          // But before we gzip, we need to modify the revved file links in
          // index.html
          String revvingSuffix = System.getenv("RevvingSuffix");
          File appPath = new File("/tmp/app");
          logger.log("Modifying links to revved files in index.html");
          Path indexPath = new File(appPath, "index.html").toPath();
          Charset charset = StandardCharsets.UTF_8;
          List<String> newLines = new ArrayList<>();
          for (String line : Files.readAllLines(indexPath, charset)) {
            if (line.contains("googleapis") || line.contains("cloudflare")
                || line.contains("maxcdn")) {
              // Don't alter lines linking to cdn-s. They are already revved.
              newLines.add(line);
            } else {
              newLines.add(line.replace(".js", "_" + revvingSuffix + ".js").replace(".css",
                  "_" + revvingSuffix + ".css"));
            }
          }
          Files.write(indexPath, newLines, charset);
          logger.log("Modified links to revved files in index.html");

          // GZIP all js, css, and html files within app folder
          logger.log("GZip-ing files in app folder to enable serving gzip-ed from S3");
          squash.deployment.lambdas.utils.FileUtils.gzip(Arrays.asList(appPath),
              Collections.emptyList(), logger);
          logger.log("GZip-ed files in app folder to enable serving gzip-ed from S3");

          // Rev the js and css files by appending revving-suffix to names - for
          // cache-ing
          logger.log("Appending revving suffix to js and css files in app folder");
          squash.deployment.lambdas.utils.FileUtils.appendRevvingSuffix(revvingSuffix,
              appPath.toPath(), logger);
          logger.log("Appended revving suffix to js and css files in app folder");

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
          // zipped first. N.B. We also append filenames with the revving
          // suffix.
          logger.log("About to upload Cognito libraries");
          List<ImmutableTriple<String, String, byte[]>> cognitoLibraries = new ArrayList<>();
          cognitoLibraries
              .add(new ImmutableTriple<>(
                  "Cognito SDK",
                  "aws-cognito-sdk.min_" + revvingSuffix + ".js",
                  IOUtils
                      .toByteArray(new URL(
                          "https://raw.githubusercontent.com/aws/amazon-cognito-identity-js/master/dist/aws-cognito-sdk.min.js"))));
          cognitoLibraries
              .add(new ImmutableTriple<>(
                  "Cognito Identity SDK",
                  "amazon-cognito-identity.min_" + revvingSuffix + ".js",
                  IOUtils
                      .toByteArray(new URL(
                          "https://raw.githubusercontent.com/aws/amazon-cognito-identity-js/master/dist/amazon-cognito-identity.min.js"))));
          cognitoLibraries.add(new ImmutableTriple<>("Big Integer Library", "jsbn_" + revvingSuffix
              + ".js", IOUtils.toByteArray(new URL(
              "http://www-cs-students.stanford.edu/~tjw/jsbn/jsbn.js"))));
          cognitoLibraries.add(new ImmutableTriple<>("Big Integer Library 2", "jsbn2_"
              + revvingSuffix + ".js", IOUtils.toByteArray(new URL(
              "http://www-cs-students.stanford.edu/~tjw/jsbn/jsbn2.js"))));

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
          cognitoLibraries.add(new ImmutableTriple<>("Stanford Javascript Crypto Library", "sjcl_"
              + revvingSuffix + ".js", sjcl_library.getBytes(Charset.forName("UTF-8"))));

          for (ImmutableTriple<String, String, byte[]> cognitoLibrary : cognitoLibraries) {
            logger.log("Uploading a Cognito library to S3 website bucket. Library name: "
                + cognitoLibrary.left);

            byte[] zippedLibrary = squash.deployment.lambdas.utils.FileUtils.gzip(
                cognitoLibrary.right, logger);
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

          // Add cache-control metadata to files. Css and js files will have
          // 1-year cache validity, since they are rev-ved.
          logger.log("Updating cache-control metadata on angular app in S3 bucket");
          TransferUtils.addCacheControlHeader("max-age=31536000", websiteBucket,
              Optional.of("app"), ".js", logger);
          TransferUtils.addCacheControlHeader("max-age=31536000", websiteBucket,
              Optional.of("app"), ".css", logger);
          // All html must revalidate every time
          TransferUtils.addCacheControlHeader("no-cache, must-revalidate", websiteBucket,
              Optional.of("app"), ".html", logger);
          logger.log("Updated cache-control metadata on angular app in S3 bucket");

          // App content must be public so it can be served from the website
          logger.log("Modifying Angularjs app ACL in S3 website bucket");
          TransferUtils
              .setPublicReadPermissionsOnBucket(websiteBucket, Optional.of("app/"), logger);
          logger.log("Modified Angularjs app ACL in S3 website bucket");

        } catch (MalformedInputException mie) {
          logger.log("Caught a MalformedInputException: " + mie.getMessage());
          throw mie;
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