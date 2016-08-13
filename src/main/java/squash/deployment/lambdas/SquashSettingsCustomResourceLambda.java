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
import squash.deployment.lambdas.utils.IS3TransferManager;
import squash.deployment.lambdas.utils.LambdaInputLogger;
import squash.deployment.lambdas.utils.S3TransferManager;
import squash.deployment.lambdas.utils.TransferUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * AWS Cloudformation custom resource to update the java properties file used by the lambda functions.
 * 
 * <p>Some settings that the lambda functions need are not known until the stack is created. To
 *    'inject' them into the lambda functions, this resource, which must run before the lambda
 *    functions are created, modifies the java zip of their source code so as to substitute the
 *    necessary values into their properties file resource.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class SquashSettingsCustomResourceLambda implements
    RequestHandler<Map<String, Object>, Object> {

  /**
   * Returns an IS3TransferManager.
   * 
   * <p>This method is provided so unit tests can mock out S3.
   */
  protected IS3TransferManager getS3TransferManager() {
    return new S3TransferManager();
  }

  /**
   * Implementation for the AWS Lambda function backing the SquashSettings resource.
   * 
   * <p>This lambda has the following keys in its request map (in addition
   *    to the standard ones) provided via the Cloudformation stack template:
   * <ul>
   *    <li>SimpleDBDomainName - name of SimpleDb domain used to store squash bookings.</li>
   *    <li>WebsiteBucket - name of S3 bucket serving the booking website.</li>
   *    <li>DatabaseBackupBucket - name of S3 bucket in which to store bookings database backups.</li>
   *    <li>DatabaseBackupSNSTopic - arn of SNS topic to notify with bookings database backups.</li>
   *    <li>LambdaZipsBucket - S3 bucket holding the lambda-function java source code zip.</li>
   *    <li>S3InputKey - key for the java source code zip in the LambdaZipsBucket.</li>
   *    <li>S3OutputKey - key to save the modified java source code zip to in the LambdaZipsBucket.</li>
   *    <li>Region - the AWS region in which the Cloudformation stack is created.</li>
   *    <li>Revision - integer incremented to force stack updates to update this resource.</li>
   * </ul>
   *
   * @param request request parameters as provided by the CloudFormation service
   * @param context context as provided by the CloudFormation service
   */
  @Override
  public Object handleRequest(Map<String, Object> request, Context context) {

    LambdaLogger logger = context.getLogger();
    logger.log("Starting SquashSettings custom resource handleRequest");

    // Handle standard request parameters
    Map<String, String> standardRequestParameters = LambdaInputLogger.logStandardRequestParameters(
        request, logger);
    String requestType = standardRequestParameters.get("RequestType");

    // Handle custom request parameters
    logger.log("Logging custom input parameters to custom resource request");
    @SuppressWarnings("unchecked")
    Map<String, Object> resourceProps = (Map<String, Object>) request.get("ResourceProperties");
    String simpleDBDomainName = (String) resourceProps.get("SimpleDBDomainName");
    String websiteBucket = (String) resourceProps.get("WebsiteBucket");
    String databaseBackupBucket = (String) resourceProps.get("DatabaseBackupBucket");
    String databaseBackupSNSTopic = (String) resourceProps.get("DatabaseBackupSNSTopic");
    String lambdaZipsBucket = (String) resourceProps.get("LambdaZipsBucket");
    String s3InputKey = (String) resourceProps.get("S3InputKey");
    String s3OutputKey = (String) resourceProps.get("S3OutputKey");
    String region = (String) resourceProps.get("Region");
    String revision = (String) resourceProps.get("Revision");

    // Log out our custom request parameters
    logger.log("SimpleDBDomainName: " + simpleDBDomainName);
    logger.log("WebsiteBucket: " + websiteBucket);
    logger.log("DatabaseBackupBucket: " + databaseBackupBucket);
    logger.log("DatabaseBackupSNSTopic: " + databaseBackupSNSTopic);
    logger.log("LambdaZipsBucket: " + lambdaZipsBucket);
    logger.log("S3InputKey: " + s3InputKey);
    logger.log("S3OutputKey: " + s3OutputKey);
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

    try {
      cloudFormationResponder.initialise();

      if (requestType.equals("Create") || requestType.equals("Update")) {

        // Get the zip file that will be used to create the Bookings lambda
        try {
          logger.log("Downloading bookings zip from S3");
          IS3TransferManager transferManager = getS3TransferManager();
          String zipDownloadPath = "/tmp/bookings.zip";
          File downloadedFile = new File(zipDownloadPath);
          TransferUtils.waitForS3Transfer(
              transferManager.download(lambdaZipsBucket, s3InputKey, downloadedFile), logger);
          logger.log("Downloaded bookings zip successfully from S3");

          // Modify the resources file to point to the correct simpleDB domain
          // name, the region, and S3 bucket.
          logger
              .log("Modifying the resources file to point to the correct simpleDB domain name, region, S3 buckets, and SNS topic");
          String zipOutputPath = "/tmp/bookingsOut.zip";
          try (ZipFile zipFile = new ZipFile(downloadedFile)) {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOutputPath))) {
              for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
                ZipEntry entryIn = e.nextElement();
                if (!entryIn.getName().equalsIgnoreCase(
                    "squash/booking/lambdas/SquashCustomResource.settings")) {
                  logger.log("Entry name: " + entryIn.getName());
                  zos.putNextEntry(entryIn);
                  try (InputStream is = zipFile.getInputStream(entryIn)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = (is.read(buf))) > 0) {
                      zos.write(buf, 0, len);
                    }
                  }
                } else {
                  logger.log("Entry name to modify: " + entryIn.getName());
                  zos.putNextEntry(new ZipEntry(
                      "squash/booking/lambdas/SquashCustomResource.settings"));

                  try (InputStream is = zipFile.getInputStream(entryIn)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = (is.read(buf))) > 0) {
                      String s = new String(buf);
                      if (s
                          .contains("simpledbdomainname=stringtobereplaced\ns3websitebucketname=stringtobereplaced\ndatabasebackupbucketname=stringtobereplaced\ndatabasebackupsnstopicarn=stringtobereplaced\nregion=stringtobereplaced")) {
                        String modified = "simpledbdomainname=" + simpleDBDomainName
                            + "\ns3websitebucketname=" + websiteBucket
                            + "\ndatabasebackupbucketname=" + databaseBackupBucket
                            + "\ndatabasebackupsnstopicarn=" + databaseBackupSNSTopic + "\nregion="
                            + region;
                        byte[] buf_check = modified.getBytes();
                        len = buf_check.length;
                        if (len > 1024) {
                          logger
                              .log("Error: modified settings file is longer than 1024-length buffer - increase buffer length and retry");
                          return null;
                        }
                        buf = modified.getBytes();
                      }
                      zos.write(buf, 0, (len < buf.length) ? len : buf.length);
                    }
                  }
                }
                zos.closeEntry();
              }
            }
          }

          // Upload the modified zip file to S3 again but with a different key
          // name - this ensures all reads will be consistent (so bookings
          // lambdas will never get created using the unmodified zip file)
          logger.log("Uploading modified bookings zip to S3");
          File modifiedFile = new File(zipOutputPath);
          TransferUtils.waitForS3Transfer(
              transferManager.upload(lambdaZipsBucket, s3OutputKey, modifiedFile), logger);
          logger.log("Uploaded modified bookings zip successfully to S3");
        } catch (IOException ioe) {
          logger.log("Caught an IO Exception: " + ioe.getMessage());
          throw ioe;
        }
      } else if (requestType.equals("Delete")) {
        logger.log("Delete request - so doing nothing");
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
      logger.log("Exception caught in SquashSettings Lambda: " + e.getMessage());
      return null;
    } finally {
      // Send response to CloudFormation
      cloudFormationResponder.addKeyValueOutputsPair("Result", "Hello from SquashSettings!!!");
      cloudFormationResponder.sendResponse(responseStatus, logger);

    }
  }
}