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
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;

import java.util.Optional;

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
   * Sets public read permissions on content within an S3 bucket.
   * 
   * <p>Web content served from an S3 bucket must have public read permissions.
   * 
   *    @param bucketName the bucket to apply the permissions to.
   *    @param prefix prefix within the bucket, beneath which to apply the permissions.
   *    @param logger a CloudwatchLogs logger.
   */
  public static void setPublicReadPermissionsOnBucket(String bucketName, Optional<String> prefix,
      LambdaLogger logger) {
    // Ensure newly uploaded content has public read permission
    ListObjectsRequest listObjectsRequest;
    if (prefix.isPresent()) {
      logger.log("Setting public read permission on bucket: " + bucketName + " and prefix: "
          + prefix.get());
      listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName).withPrefix(
          prefix.get());
    } else {
      logger.log("Setting public read permission on bucket: " + bucketName);
      listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName);
    }

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
    logger.log("Finished setting public read permissions");
  }

  /**
   * Adds gzip content-encoding metadata to S3 objects.
   * 
   * <p>Adds gzip content-encoding metadata to S3 objects. All objects
   *    beneath the specified prefix (i.e. folder) will have the
   *    metadata added. When the bucket serves objects it will then
   *    add a suitable Content-Encoding header.
   *
   *    @param bucketName the bucket to apply the metadata to.
   *    @param prefix prefix within the bucket, beneath which to apply the metadata.
   *    @param logger a CloudwatchLogs logger.
   */
  public static void addGzipContentEncodingMetadata(String bucketName, Optional<String> prefix,
      LambdaLogger logger) {

    // To add new metadata, we must copy each object to itself.
    ListObjectsRequest listObjectsRequest;
    if (prefix.isPresent()) {
      logger.log("Setting gzip content encoding metadata on bucket: " + bucketName
          + " and prefix: " + prefix.get());
      listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName).withPrefix(
          prefix.get());
    } else {
      logger.log("Setting gzip content encoding metadata on bucket: " + bucketName);
      listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName);
    }

    ObjectListing objectListing;
    AmazonS3 client = new TransferManager().getAmazonS3Client();
    do {
      objectListing = client.listObjects(listObjectsRequest);
      for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
        String key = objectSummary.getKey();
        logger.log("Setting metadata for S3 object: " + key);
        // We must specify ALL metadata - not just the one we're adding.
        ObjectMetadata objectMetadata = client.getObjectMetadata(bucketName, key);
        objectMetadata.setContentEncoding("gzip");
        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName, key, bucketName,
            key).withNewObjectMetadata(objectMetadata).withCannedAccessControlList(
            CannedAccessControlList.PublicRead);
        client.copyObject(copyObjectRequest);
        logger.log("Set metadata for S3 object: " + key);
      }
      listObjectsRequest.setMarker(objectListing.getNextMarker());
    } while (objectListing.isTruncated());
    logger.log("Set gzip content encoding metadata on bucket");
  }

  /**
   * Adds cache-control header to S3 objects.
   * 
   * <p>Adds cache-control header to S3 objects. All objects
   *    beneath the specified prefix (i.e. folder), and with the
   *    specified extension will have the header added. When the
   *    bucket serves objects it will then add a suitable
   *    Cache-Control header.
   *
   *    @param headerValue value of the cache-control header
   *    @param bucketName the bucket to apply the header to.
   *    @param prefix prefix within the bucket, beneath which to apply the header.
   *    @param extension file extension to apply header to
   *    @param logger a CloudwatchLogs logger.
   */
  public static void addCacheControlHeader(String headerValue, String bucketName,
      Optional<String> prefix, String extension, LambdaLogger logger) {

    // To add new metadata, we must copy each object to itself.
    ListObjectsRequest listObjectsRequest;
    if (prefix.isPresent()) {
      logger.log("Setting cache-control metadata: " + headerValue + ", on bucket: " + bucketName
          + " and prefix: " + prefix.get() + " and extension: " + extension);
      listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName).withPrefix(
          prefix.get());
    } else {
      logger.log("Setting cache-control metadata: " + headerValue + ", on bucket: " + bucketName
          + " and extension: " + extension);
      listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName);
    }

    ObjectListing objectListing;
    AmazonS3 client = new TransferManager().getAmazonS3Client();
    do {
      objectListing = client.listObjects(listObjectsRequest);
      for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
        String key = objectSummary.getKey();
        if (!key.endsWith(extension)) {
          continue;
        }
        logger.log("Setting metadata for S3 object: " + key);
        // We must specify ALL metadata - not just the one we're adding.
        ObjectMetadata objectMetadata = client.getObjectMetadata(bucketName, key);
        objectMetadata.setCacheControl(headerValue);
        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName, key, bucketName,
            key).withNewObjectMetadata(objectMetadata).withCannedAccessControlList(
            CannedAccessControlList.PublicRead);
        client.copyObject(copyObjectRequest);
        logger.log("Set metadata for S3 object: " + key);
      }
      listObjectsRequest.setMarker(objectListing.getNextMarker());
    } while (objectListing.isTruncated());
    logger.log("Set cache-control metadata on bucket");
  }
}