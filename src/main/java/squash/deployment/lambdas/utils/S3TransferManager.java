/**
 * Copyright 2015-2017 Robin Steel
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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import java.io.File;

/**
 * Very thin wrapper for AWS S3 TransferManager.
 *
 * <p>Class exists solely so we can mock out the S3 TransferManager in unit tests.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class S3TransferManager implements IS3TransferManager {
  private TransferManager transferManager;

  public S3TransferManager() {
    transferManager = TransferManagerBuilder.defaultTransferManager();
  }

  @Override
  public Transfer copy(CopyObjectRequest copyObjectRequest) {
    return transferManager.copy(copyObjectRequest);
  }

  @Override
  public Transfer download(String bucketName, String keyName, File target) {
    return transferManager.download(bucketName, keyName, target);
  }

  @Override
  public Transfer upload(PutObjectRequest putObjectRequest) {
    return transferManager.upload(putObjectRequest);
  }

  @Override
  public Transfer upload(String bucketName, String keyName, File target) {
    return transferManager.upload(bucketName, keyName, target);
  }

  @Override
  public Transfer uploadDirectory(String bucketName, String virtualDirectoryKeyPrefix,
      File targetDirectory, boolean includeSubdirectories) {
    return transferManager.uploadDirectory(bucketName, virtualDirectoryKeyPrefix, targetDirectory,
        includeSubdirectories);
  }

  @Override
  public AmazonS3 getAmazonS3Client() {
    return transferManager.getAmazonS3Client();
  }
}