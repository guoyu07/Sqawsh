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

package applicationdriverlayer.pageobjects.squash.booking;

import steps.hooks.SharedDriver;

import org.junit.Assert;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import applicationdriverlayer.pageobjects.squash.SquashBasePage;

/**
 * Page object to manage the generic error page.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class ErrorPage extends SquashBasePage<ErrorPage> {

  public ErrorPage(SharedDriver driver) {
    super(driver);
  }

  @Override
  protected void waitForLoadToComplete() {
    new WebDriverWait(driver, explicitWaitTimeoutSeconds).until(ExpectedConditions.titleIs("Grrr"));
  }

  @Override
  protected void assertIsLoaded() throws Error {
    super.assertIsLoaded();

    Assert.assertTrue("The error page is not visible", driver.getTitle().equals("Grrr"));
  }
}