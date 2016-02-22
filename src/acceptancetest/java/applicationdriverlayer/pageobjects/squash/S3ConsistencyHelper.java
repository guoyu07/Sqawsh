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

package applicationdriverlayer.pageobjects.squash;

import java.util.HashMap;
import java.util.Map;

/**
 * Acceptance test helper to determine when pages served from S3 are consistent.
 * 
 * <p>S3 has only eventual consistency for ReadAfterUpdate. Yet tests must ensure they are
 *    seeing the latest 'consistent' version of any page they view - and the S3 consistency
 *    helper ensures they do.
 * <p>A single instance of the helper is shared between all S3 page objects via
 *    dependency-injection (it is Optional as non-S3 page objects don't need it unless they
 *    ever navigate to S3 page objects). Whenever a page is mutated in S3, the page's guid
 *    in the helper's map must be updated. Rather than using the entire page as a 'guid', we
 *    read a guid embedded within each page by AWS Lambda. Then any subsequent get's of the
 *    page within that scenario will first load the page normally, and then call the
 *    isS3PageConsistent() override on the page object. If it is consistent, it will update
 *    the guid in the map and return true - in which case the load is complete. If it's not
 *    yet consistent, it will ask the webdriver to do another get _to the current url_ (rather
 *    than to what getUrl() returns, so e.g. any date query parameters are retained) and then
 *    call load(false, *) recursively on the base page object. The page object's
 *    isS3PageConsistent() override should make its decision as follows:
 * <ul>
 * <li>if there is no guid, it will assume the page is consistent only if it has no bookings (
 *     pages always start each scenario with no bookings).</li>
 * <li>if there is a guid, then the page we're get-ing will either have changed or not, and
 *     if not, the guid will be the same - so we need to know if we are expecting a changed
 *     page or not (e.g. we could be returning to the booking page from the reservation page
 *     either with or without having made a new booking). This information is passed as a
 *     parameter to isS3PageConsistent.</li>
 * </ul>
 * <p>We assume the helper will be accessed by only one thread at a time (even when the
 *    scenario uses multiple webdrivers).
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class S3ConsistencyHelper {

  private Map<String, String> s3PageGuids;

  public S3ConsistencyHelper() {
    s3PageGuids = new HashMap<String, String>();
  }

  /**
   * Updates the guid for this page for a given key.
   * 
   * @param key distinguishes multiple pages managed by this page object (e.g. booking pages for different dates).
   * @param guid the new guid.
   * 
   * @see SquashBasePage#getCachedWebElementAndGuidKey()
   */
  public void updateGuid(String key, String guid) {
    s3PageGuids.put(key, guid);
  }

  /**
   * Returns the guid for this page for a given key.
   * 
   * @see SquashBasePage#getCachedWebElementAndGuidKey()
   */
  public String getGuid(String key) {
    return s3PageGuids.get(key);
  }
}