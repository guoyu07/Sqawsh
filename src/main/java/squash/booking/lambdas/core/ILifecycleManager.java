/**
 * Copyright 2017 Robin Steel
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

package squash.booking.lambdas.core;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Optional;

/**
 * Interface for all classes managing lifecycle state of the bookings service.
 * 
 * <p>All lifecycle state management should be performed by a class implementing this interface.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface ILifecycleManager {

  /**
   * Defines the lifecycle states of an instance of the booking service.
   * <ul>
   * <li>{@link #ACTIVE}</li>
   * <li>{@link #READONLY}</li>
   * <li>{@link #RETIRED}</li>
   * </ul>
   */
  public enum LifecycleState {
    /**
     * Normal active state
     */
    ACTIVE,

    /**
     * Maintenance state when bookings and booking rules can be queried but not mutated
     */
    READONLY,

    /**
     * Retired state where the service only provides url to its updated version
     */
    RETIRED
  }

  /**
   * Initialises the manager with a CloudwatchLogs logger.
   */
  void initialise(LambdaLogger logger) throws Exception;

  /**
   * Checks booking service lifecycle state is valid for the operation being attempted.
   *
   * @param operationIsReadOnly true if attempted operation will not mutate the bookings or booking rules.
   * @param isSquashServiceUserCall true if call is from service user, rather than for backup/restore or application of rules.
   * @throws Exception if the operation is invalid in the current lifecycle state.
   */
  public void throwIfOperationInvalidForCurrentLifecycleState(boolean operationIsReadOnly,
      boolean isSquashServiceUserCall) throws Exception;

  /**
   * Sets the lifecycle state.
   * 
   * @param lifecycleState the new state of the service.
   * @param newServiceUrl the url of the updated service - should be provided only when lifecycleState is RETIRED - e.g. https://newsquashwebsite.s3-website-eu-west-1.amazonaws.com
   * @throws Exception 
   */
  void setLifecycleState(LifecycleState lifecycleState, Optional<String> newServiceUrl)
      throws Exception;

  /**
   * Gets the lifecycle state.
   * @throws Exception 
   */
  ImmutablePair<LifecycleState, Optional<String>> getLifecycleState() throws Exception;
}