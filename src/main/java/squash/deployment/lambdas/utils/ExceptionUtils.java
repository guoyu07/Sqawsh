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

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * Sundry exception-handling utilities.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class ExceptionUtils {

  /**
   *  Logs an AmazonClientException to a lambda logger.
   */
  public static void logAmazonClientException(AmazonClientException ace, LambdaLogger logger) {
    logger.log("Caught an AmazonClientException, which means " + "the client encountered "
        + "an internal error while trying to " + "communicate with AWS, "
        + "such as not being able to access the network.");
    logger.log("Error Message: " + ace.getMessage());
  }

  /**
   *  Logs an AmazonServiceException to a lambda logger.
   */
  public static void logAmazonServiceException(AmazonServiceException ase, LambdaLogger logger) {
    logger.log("Caught an AmazonServiceException, which " + "means the request made it "
        + "to AWS, but was rejected with an error response " + "for some reason.");
    logger.log("Error Message:    " + ase.getMessage());
    logger.log("HTTP Status Code: " + ase.getStatusCode());
    logger.log("AWS Error Code:   " + ase.getErrorCode());
    logger.log("Error Type:       " + ase.getErrorType());
    logger.log("Request ID:       " + ase.getRequestId());
  }
}