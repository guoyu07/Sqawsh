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

package squash.deployment.lambdas.utils;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Optional;

/**
 * Helper to retry Amazon service calls.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class RetryHelper {

  // Tweak supplier so we can use with lambdas that can throw
  @FunctionalInterface
  public interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  /**
   * Attempts an operation with up to 2 retries.
   * 
   * <p>Will retry an operation up to 2 times if it throws an exception matching that
   *    specified. We assume that:
   * <ul>
   * <li>if the specified exception is an AmazonServiceException, then any specified errorCode
   *    must match the result of getErrorCode() on the AmazonServiceException.</li>
   * <li>otherwise any specified errorCode must match the result of getMessage() on the exception.</li>
   * </ul>
   *    
   *    @param workToDo the operation to (re)try.
   *    @param exceptionToCatch the exception type to catch
   *    @param errorCode an optional error code to match
   *    @param logger a CloudwatchLogs logger.
   *    @return result the result of running the operation
   *    @throws Exception if the operation does not succeed before all retries are attempted.
   */
  public static <T extends Exception, R> R DoWithRetries(ThrowingSupplier<R> workToDo,
      Class<T> exceptionToCatch, Optional<String> errorCode, LambdaLogger logger) throws Exception {
    int retries = 0;
    do {
      try {
        R result = workToDo.get();
        return result;
      } catch (Exception exception) {
        if (!exceptionToCatch.isInstance(exception) || (retries++ >= 2)) {
          // Wrong exception or too many retries - so give up and rethrow
          throw exception;
        }
        // If we have an error code, then we must also match that
        if (errorCode.isPresent()) {
          if (AmazonServiceException.class.isInstance(exception)) {
            if (!((AmazonServiceException) exception).getErrorCode().equals(errorCode.get())) {
              throw exception;
            }
          } else {
            if (!exception.getMessage().equals(errorCode.get())) {
              throw exception;
            }
          }
        }
        logger.log("Caught retry-able exception - so about to retry after short sleep...");
        try {
          Thread.sleep(500);
        } catch (InterruptedException interruptedException) {
          logger.log("Sleep before retrying has been interrupted.");
        }
      }
    } while (true);
  }
}