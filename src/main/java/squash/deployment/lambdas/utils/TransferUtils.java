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

package squash.deployment.lambdas.utils;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;

/**
 * Sundry S3 utilities.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class TransferUtils {

  /**
   * Waits for S3 transfers to complete.
   * 
   * <p>S3 transfers via the S3 TransferManager are asynchronous. This can be used
   *    by all transfers (uploads, copies, and downloads) to wait until they have
   *    completed.
   * 
   *    @param transfer returned by the TransferManager when transfer is initiated.
   *    @param logger a CloudwatchLogs logger.
   *    @throws AmazonServiceException if any errors occurred in S3 during the wait.
   *    @throws InterruptedException if the wait is interrupted.
   */
  public static void waitForS3Transfer(Transfer transfer, LambdaLogger logger)
      throws InterruptedException {
    while (transfer.isDone() == false) {
      logger.log("Transfer progress: " + transfer.getProgress().getPercentTransferred() + "%");
      try {
        Thread.sleep(100); // milliseconds
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.log("Transfer thread interrupted");
        throw e;
      }
    }
    logger.log("Transfer is done - now wait for completion to see if transfer succeeded.");
    try {
      transfer.waitForCompletion(); // Will throw if transfer failed
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.log("Transfer thread interrupted");
      throw e;
    }
  }

  /**
   * Sets public read permissions on everything in an S3 bucket.
   * 
   * <p>Web content served from an S3 bucket must have public read permissions.
   * 
   *    @param bucketName the bucket to apply the permissions to.
   *    @param logger a CloudwatchLogs logger.
   */
  public static void setPublicReadPermissionsOnBucket(String bucketName, LambdaLogger logger) {
    // Ensure newly uploaded content has public read permission
    logger.log("Setting public read permission on bucket: " + bucketName);
    ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName);
    ObjectListing objectListing;
    AmazonS3 client = new TransferManager().getAmazonS3Client();
    do {
      objectListing = client.listObjects(listObjectsRequest);
      for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
        logger.log("Setting permissions for S3 object: " + objectSummary.getKey());
        client.setObjectAcl(bucketName, objectSummary.getKey(), CannedAccessControlList.PublicRead);
      }
      listObjectsRequest.setMarker(objectListing.getNextMarker());
    } while (objectListing.isTruncated());
    logger.log("Finished setting public read permissions on bucket");
  }
}