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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Transfer;

import java.io.File;

/**
 * Interface for AWS S3 TransferManager.
 * 
 * <p>Interface to wrap methods on an S3 TransferManager so that
 *    unit tests can mock out S3.
 *    
 * @see <a href="http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/transfer/TransferManager.html">S3TransferManager</a>.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IS3TransferManager {
  Transfer copy(CopyObjectRequest copyObjectRequest);

  Transfer download(String bucketName, String keyName, File target);

  Transfer upload(PutObjectRequest putObjectRequest);

  Transfer upload(String bucketName, String keyName, File target);

  Transfer uploadDirectory(String bucketName, String virtualDirectoryKeyPrefix,
      File targetDirectory, boolean includeSubdirectories);

  AmazonS3 getAmazonS3Client();
}