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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.HashMap;
import java.util.Optional;

/**
 * Base class for all Page Objects.
 * 
 * <p>Provides functionality that allows page objects to wait correctly
 *    for asynchronous loading of web pages during acceptance tests.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public abstract class SquashBasePage<T> {

  /**
   * Allows correct waiting for a web element to disappear on an old page and reappear on a new one.
   * 
   * <p>A cached web element can be used to defeat a race when a web element is
   *    getting replaced by a similar element during page mutation/navigation.
   * 
   * <p>To wait for load to complete we first wait for the cached element to be
   *    stale and then wait for its replacement to appear. It is static so it's
   *    shared between all page object instances and also across scenarios (
   *    since we do not re-get a page at scenario-start if we're already on the
   *    correct page).
   * 
   * <p>A map is used as some 'page's managed by a page object are in reality
   *    multiple pages - each with their own cached web element. e.g. the booking
   *    'page' has a separate page for each day, but all these pages use the same
   *    page object class - so the map's key would be chosen to distinguish the days.
   */
  protected static HashMap<String, WebElement> cachedWebElementHelper;

  /**
   * Allows retrieval of a web page from S3 in its eventually-consistent state.
   */
  protected static S3ConsistencyHelper s3ConsistencyHelper;

  protected WebDriver driver;

  /**
   * The timeout used for all WebDriver explicit waits.
   */
  protected Integer explicitWaitTimeoutSeconds = 30;

  // Used to limit the number of reload attempts to achieve S3 consistency
  private int loadRecursionDepth;

  /**
   * Constructor accepting a WebDriver.
   */
  protected SquashBasePage(WebDriver driver) {
    this.driver = driver;
    if (s3ConsistencyHelper == null) {
      s3ConsistencyHelper = new S3ConsistencyHelper();
    }
    if (cachedWebElementHelper == null) {
      cachedWebElementHelper = new HashMap<>();
    }
    PageFactory.initElements(driver, this);

    loadRecursionDepth = 0;
  }

  /**
   * Gets the webpage corresponding to this page object.
   * 
   * <p>This initiates a get and accepts an optional cached web element.
   *    If a cached web element is provided, the method will initiate the
   *    get and then wait for the cached element to become stale before
   *    starting to wait for the new page to complete loading.
   *
   * @return the page object corresponding to the fetched page.
   */
  public T get(Optional<WebElement> cachedWebElement) {
    return get(false, cachedWebElement, Optional.of(false));
  }

  /**
   * Gets the webpage corresponding to this page object.
   * 
   * <ul>
   * <li>If alreadyLoading is false, this method will initiate a get of the
   *    webpage. Otherwise it will simple wait for the page to complete
   *    loading.</li>
   * <li>If a cached web element is provided, it will wait for the cached
   *    element to become stale before starting to wait for the new page
   *    to complete loading.</li>
   * <li>The expectChangedS3Page parameter should be provided only for pages
   *    fetched from S3. If you are expecting the content of the S3 page
   *    to have changed since it was last fetched, this should be set to
   *    true. It is used to ensure we wait correctly for the page to reach
   *    its eventually-consistent state.</li>
   * </ul>
   *
   * @return the page object corresponding to the fetched page.
   */
  @SuppressWarnings("unchecked")
  public T get(boolean alreadyLoading, Optional<WebElement> cachedWebElement,
      Optional<Boolean> expectChangedS3Page) {
    load(alreadyLoading, cachedWebElement, expectChangedS3Page);

    assertTrue("Page should be fully loaded", isLoaded());

    return (T) this;
  }

  private final void load(boolean alreadyLoading, Optional<WebElement> cachedWebElement,
      Optional<Boolean> expectChangedS3Page) {
    loadRecursionDepth++;
    if (!alreadyLoading) {
      Optional<String> url = getUrl();
      if (url.isPresent()) {
        // We have an URL and the page is not already loading, so let's initiate
        // page retrieval.
        driver.get(url.get());
      } else {
        fail("Cannot initiate a page get without an URL");
      }
    }

    if (cachedWebElement.isPresent()) {
      // We have a cached web element, so first wait for it to become stale
      new WebDriverWait(driver, explicitWaitTimeoutSeconds).until(ExpectedConditions
          .stalenessOf(cachedWebElement.get()));
    }

    // Now wait for the page load to complete
    waitForLoadToComplete();

    // Give our derived class a chance to update its cached web element
    Optional<WebElement> updatedCachedWebElement = updateCachedWebElement();

    if (expectChangedS3Page.isPresent() && !isS3PageConsistent(expectChangedS3Page.get())) {
      // If page not in its eventually-consistent state, re-get the current url
      // N.B. current url may differ from result of getUrl() e.g. by having
      // different query params (such as the date in the case of bookings pages)
      if (loadRecursionDepth > 20) {
        fail("Load recursion depth limit exceeded in SquashBasePage");
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        System.out.println("Sleep before re-get-ing for S3 consistency was interrupted");
      }
      driver.get(driver.getCurrentUrl());
      load(true, updatedCachedWebElement, expectChangedS3Page);
    }

    loadRecursionDepth = 0;
  }

  /**
   * Returns whether the webpage corresponding to this page object has completed loading.
   */
  public boolean isLoaded() {
    try {
      try {
        assertIsLoaded();
        return true;
      } catch (Error e) {
        e.printStackTrace();
      }
    } catch (NoSuchElementException e) {
      e.printStackTrace();
    }

    return false;
  }

  /**
   * Returns the cached web element for this page - if there is one.
   */
  protected Optional<WebElement> getCachedWebElement() {
    WebElement cachedWebElement = cachedWebElementHelper.get(getCachedWebElementAndGuidKey());

    if (cachedWebElement == null) {
      return Optional.empty();
    }
    return Optional.of(cachedWebElement);
  }

  /**
   * Returns the key used for both the cached web element and S3 consistency helper maps.
   * 
   * <p>Derived classes may override this method to provide the key name used to look
   *    up both the page's cached web element (if it has one) and the page's guid
   *    (if it is an S3-served page). Both maps use the same key.
   */
  protected String getCachedWebElementAndGuidKey() {
    return "dummyKeyName";
  }

  /**
   * For S3 pages, returns whether the page is in its eventually-consistent state.
   * 
   * @param expectChangedPage whether we expect the page to have changed.
   */
  protected boolean isS3PageConsistent(boolean expectChangedPage) {
    return true;
  }

  /**
   * Returns the Url of the webpage, if it is directly fetchable.
   * 
   * <p>Derived classes may override this method to return the URL from which to
   *    load the page. If it is Optional.empty(), no attempt will be made to
   *    initiate a page load, and get(true, *) will simply wait for the page
   *    to load (assuming something external has initiated it). e.g. the court
   *    reservation page's getUrl returns Optional.empty(), as it is always got
   *    by clicking a 'Reserve' button.
   */
  protected Optional<String> getUrl() {
    return Optional.empty();
  }

  /**
   * Updates the page's cached web element.
   * 
   * <p>Derived classes may override this method to update their cached
   *    web element. If present, this cached element is used when the page
   *    refreshes itself or navigates away from itself. It passes the
   *    cached element to the new page object which waits for it to become
   *    stale, so it can then safely start waiting for elements on the new
   *    page, without fear that these waits will wrongly be satisfied by
   *    similar elements on the old page.
   *
   * @return the new cached web element for the page.
   */
  protected Optional<WebElement> updateCachedWebElement() {
    return Optional.empty();
  }

  /**
   * Waits for the page to complete loading.
   * 
   * <p>Derived classes may override this method to wait for page loading to
   *    complete. They may assume that the previously loaded page will already
   *    be stale when this method is called (as we will already have waited
   *    for staleness of any cached web element).
   */
  protected void waitForLoadToComplete() {
    return;
  }

  /**
   * Asserts that the page has completed loading.
   * 
   * <p>Derived classes may override this method to add any assertions required
   *    to validate that the page is fully-loaded.
   */
  protected void assertIsLoaded() {
    return;
  }
}