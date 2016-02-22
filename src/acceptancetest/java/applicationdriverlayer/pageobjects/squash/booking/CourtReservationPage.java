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
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.How;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import applicationdriverlayer.pageobjects.squash.SquashBasePage;

import java.util.Optional;

/**
 * Page object to manage the booking reservation form.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class CourtReservationPage extends SquashBasePage<CourtReservationPage> {

  @FindBy(how = How.CSS, css = "input[name = 'player1name']")
  public WebElement player1NameTextBox;

  @FindBy(how = How.CSS, css = "input[name = 'player2name']")
  public WebElement player2NameTextBox;

  @FindBy(how = How.CSS, css = "input[name = 'password']")
  public WebElement passwordTextBox;

  @FindBy(how = How.ID, id = "submitreservation")
  public WebElement submitReservationButton;

  public CourtReservationPage(SharedDriver driver) {
    super(driver);
  }

  @Override
  protected void waitForLoadToComplete() {
    new WebDriverWait(driver, explicitWaitTimeoutSeconds).until(ExpectedConditions
        .visibilityOfElementLocated(By.className("reservationForm")));
    new WebDriverWait(driver, explicitWaitTimeoutSeconds).until(ExpectedConditions
        .visibilityOfElementLocated(By.cssSelector("input[name = 'player2name']")));
  }

  @Override
  protected void assertIsLoaded() throws Error {
    super.assertIsLoaded();

    Assert.assertTrue("The reservation form is not visible",
        driver.findElement(By.className("reservationForm")).isDisplayed());
  }

  public void submitBookingDetails(String player1, String player2, String password,
      boolean expectBookingToSucceed) {
    player1NameTextBox.sendKeys(player1);
    player2NameTextBox.sendKeys(player2);
    passwordTextBox.sendKeys(password);
    submitReservationButton.click();

    // Wait for the next page to fully load before returning
    if (expectBookingToSucceed) {
      new CourtAndTimeSlotChooserPage((SharedDriver) driver).get(true, Optional.empty(),
          Optional.of(true));
    } else {
      // The HtmlUnitDriver redirects immediately - so even in the error,
      // case it will go straight back to the booking page.
      String webDriverType = System.getProperty("WebDriverType");
      if (webDriverType.equals("HtmlUnit")) {
        new CourtAndTimeSlotChooserPage((SharedDriver) driver).get(true, Optional.empty(),
            Optional.of(false));
      } else {
        new ErrorPage((SharedDriver) driver).get(true, Optional.empty(), Optional.empty());
      }
    }
  }
}