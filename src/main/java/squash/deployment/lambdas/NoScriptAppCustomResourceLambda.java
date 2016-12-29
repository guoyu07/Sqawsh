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

import squash.booking.lambdas.UpdateBookingsLambda;
import squash.booking.lambdas.UpdateBookingsLambdaRequest;
import squash.booking.lambdas.UpdateBookingsLambdaResponse;
import squash.deployment.lambdas.utils.CloudFormationResponder;
import squash.deployment.lambdas.utils.ExceptionUtils;
import squash.deployment.lambdas.utils.IS3TransferManager;
import squash.deployment.lambdas.utils.LambdaInputLogger;
import squash.deployment.lambdas.utils.S3TransferManager;

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

import java.util.Map;

/**
 * AWS Cloudformation custom resource to upload the initial noscript website content.
 * 
 * <p>The javascript-disabled website content is created and deleted by Cloudformation
 *    using a custom resource backed by this lambda function.
 *    
 * <p>It uploads a booking page, and a bookings json file for each bookable day to the website.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class NoScriptAppCustomResourceLambda implements RequestHandler<Map<String, Object>, Object> {

  /**
   * Returns an IS3TransferManager.
   * 
   * <p>This method is provided so unit tests can mock out S3.
   */
  protected IS3TransferManager getS3TransferManager() {
    return new S3TransferManager();
  }

  /**
   * Implementation for the AWS Lambda function backing the NoScriptApp custom resource.
   * 
   * <p>This lambda requires the following environment variables:
   * <ul>
   *    <li>SimpleDBDomainName - name of the simpleDB domain for bookings and booking rules.</li>
   *    <li>WebsiteBucket - name of S3 bucket serving the booking website.</li>
   *    <li>ApiGatewayBaseUrl - base Url of the ApiGateway Api.</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   *    <li>Revision - integer incremented to force stack updates to update this resource.</li>
   * </ul>
   * 
   * <p>On success, it returns the following output to Cloudformation:
   * <ul>
   *    <li>WebsiteURL - Url of the website's first booking page.</li>
   * </ul>
   *
   * @param request request parameters as provided by the CloudFormation service
   * @param context context as provided by the CloudFormation service
   */
  @Override
  public Object handleRequest(Map<String, Object> request, Context context) {

    LambdaLogger logger = context.getLogger();
    logger.log("Starting NoScriptApp custom resource handleRequest");

    // Handle standard request parameters
    Map<String, String> standardRequestParameters = LambdaInputLogger.logStandardRequestParameters(
        request, logger);
    String requestType = standardRequestParameters.get("RequestType");

    // Handle required environment variables
    logger.log("Logging environment variables required by custom resource request");

    String simpleDBDomainName = System.getenv("SimpleDBDomainName");
    String websiteBucket = System.getenv("WebsiteBucket");
    String apiGatewayBaseUrl = System.getenv("ApiGatewayBaseUrl");
    String region = System.getenv("AWS_REGION");
    String revision = System.getenv("Revision");

    // Log out our required environment variables
    logger.log("SimpleDBDomainName: " + simpleDBDomainName);
    logger.log("WebsiteBucket: " + websiteBucket);
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

        // Upload 21 initial bookings pages and index page to the S3 bucket
        UpdateBookingsLambdaRequest updateBookingsLambdaRequest = new UpdateBookingsLambdaRequest();
        updateBookingsLambdaRequest.setApiGatewayBaseUrl(apiGatewayBaseUrl);
        UpdateBookingsLambda updateBookingsLambda = new UpdateBookingsLambda();
        UpdateBookingsLambdaResponse updateBookingsLambdaResponse = updateBookingsLambda
            .updateBookings(updateBookingsLambdaRequest, context);
        String firstDate = updateBookingsLambdaResponse.getCurrentDate();

        websiteURL = "http://" + websiteBucket + ".s3-website-" + region
            + ".amazonaws.com?selectedDate=" + firstDate + ".html";

      } else if (requestType.equals("Delete")) {
        logger.log("Delete request - so removing bookings pages from website versioned S3 bucket");

        // We need to delete every version of every key before the bucket itself
        // can be deleted
        ListVersionsRequest listVersionsRequest = new ListVersionsRequest()
            .withBucketName(websiteBucket);
        VersionListing versionListing;

        AmazonS3 client = new TransferManager().getAmazonS3Client();
        do {
          versionListing = client.listVersions(listVersionsRequest);
          versionListing
              .getVersionSummaries()
              .stream()
              .filter(
              // Maybe a bit slack, but '20' is to include e.g. 2015-10-04.html
                  k -> (k.getKey().startsWith("20") || k.getKey().equals("today.html")))
              .forEach(
                  k -> {
                    logger.log("About to delete version: " + k.getVersionId()
                        + " of booking page: " + k.getKey());
                    DeleteVersionRequest deleteVersionRequest = new DeleteVersionRequest(
                        websiteBucket, k.getKey(), k.getVersionId());
                    client.deleteVersion(deleteVersionRequest);
                    logger.log("Successfully deleted version: " + k.getVersionId()
                        + " of booking page: " + k.getKey());
                  });

          listVersionsRequest.setKeyMarker(versionListing.getNextKeyMarker());
        } while (versionListing.isTruncated());
        logger.log("Finished removing bookings pages from website S3 bucket");
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
      logger.log("Exception caught in NoScriptApp Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      cloudFormationResponder.addKeyValueOutputsPair("WebsiteURL", websiteURL);
      cloudFormationResponder.sendResponse(responseStatus, logger);
    }
  }
}