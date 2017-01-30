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

package squash.booking.lambdas;

import squash.booking.lambdas.core.ILifecycleManager;

/**
 * Request parameter for the {@link UpdateLifecycleStateLambda UpdateLifecycleState} lambda function.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class UpdateLifecycleStateLambdaRequest {
  String lifecycleState;
  String forwardingUrl;

  public String getForwardingUrl() {
    return forwardingUrl;
  }

  /**
   * Sets the forwarding Url to the updated booking site.
   * 
   * This should be provided only when setting the lifecycle state to RETIRED,
   * and the url should be for the Angularjs version of the new site, e.g.
   * http://newsquashwebsite.s3-website-eu-west-1.amazonaws.com/app/index.html
   * 
   */
  public void setForwardingUrl(String forwardingUrl) {
    this.forwardingUrl = forwardingUrl;
  }

  public String getLifecycleState() {
    return lifecycleState;
  }

  /**
   * Sets the lifecycle state of the current booking site.
   * 
   * Can be one of the states defined by the {@link ILifecycleManager LifecycleState} manager.
   */
  public void setLifecycleState(String lifecycleState) {
    this.lifecycleState = lifecycleState;
  }
}