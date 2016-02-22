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

package steps.hooks;

import static org.junit.Assert.fail;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.events.EventFiringWebDriver;

import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import io.appium.java_client.ios.IOSDriver;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Based on the SharedDriver provided with CucumberJVM.
 * 
 * <p>The drivers take an age to launch - hence why they are shared
 *    between scenarios and not relaunched each time.
 */
public class SharedDriver extends EventFiringWebDriver {

  private static WebDriver REAL_CHROME_DRIVER;
  private static WebDriver REAL_FIREFOX_DRIVER;
  private static WebDriver REAL_HTMLUNIT_DRIVER;
  private static WebDriver REAL_SAFARI_DRIVER;
  private static WebDriver REAL_IPAD_DRIVER;
  private static final Thread CLOSE_THREAD;

  static {
    // N.B. The order of these statements is important. In particular we must
    // set the property for where to find the chrome driver before instantiating
    // it.
    System.setProperty("webdriver.chrome.driver", "/Users/Rob/Documents/Home/chromedriver");
    CLOSE_THREAD = new Thread() {
      @Override
      public void run() {
        if (REAL_CHROME_DRIVER != null) {
          REAL_CHROME_DRIVER.close();
        }
        if (REAL_FIREFOX_DRIVER != null) {
          REAL_FIREFOX_DRIVER.close();
        }
        if (REAL_IPAD_DRIVER != null) {
          REAL_IPAD_DRIVER.close();
        }
        if (REAL_HTMLUNIT_DRIVER != null) {
          REAL_HTMLUNIT_DRIVER.close();
        }
        if (REAL_SAFARI_DRIVER != null) {
          REAL_SAFARI_DRIVER.close();
        }
      }
    };
    Runtime.getRuntime().addShutdownHook(CLOSE_THREAD);
  }

  public SharedDriver() {
    super(getCurrentDriver());
  }

  private static WebDriver getCurrentDriver() {
    // We allow different drivers to be used in the same test run.
    String webDriverType = System.getProperty("WebDriverType");
    if (webDriverType.equals("Chrome")) {
      if (REAL_FIREFOX_DRIVER != null) {
        REAL_FIREFOX_DRIVER.close();
        REAL_FIREFOX_DRIVER = null;
      }
      if (REAL_HTMLUNIT_DRIVER != null) {
        REAL_HTMLUNIT_DRIVER.close();
        REAL_HTMLUNIT_DRIVER = null;
      }
      if (REAL_SAFARI_DRIVER != null) {
        REAL_SAFARI_DRIVER.close();
        REAL_SAFARI_DRIVER = null;
      }
      if (REAL_IPAD_DRIVER != null) {
        REAL_IPAD_DRIVER.close();
        REAL_IPAD_DRIVER = null;
      }
      if (REAL_CHROME_DRIVER == null) {
        REAL_CHROME_DRIVER = new ChromeDriver();
      }
      return REAL_CHROME_DRIVER;
    } else if (webDriverType.equals("Firefox")) {
      if (REAL_CHROME_DRIVER != null) {
        REAL_CHROME_DRIVER.close();
        REAL_CHROME_DRIVER = null;
      }
      if (REAL_HTMLUNIT_DRIVER != null) {
        REAL_HTMLUNIT_DRIVER.close();
        REAL_HTMLUNIT_DRIVER = null;
      }
      if (REAL_SAFARI_DRIVER != null) {
        REAL_SAFARI_DRIVER.close();
        REAL_SAFARI_DRIVER = null;
      }
      if (REAL_IPAD_DRIVER != null) {
        REAL_IPAD_DRIVER.close();
        REAL_IPAD_DRIVER = null;
      }
      if (REAL_FIREFOX_DRIVER == null) {
        REAL_FIREFOX_DRIVER = new FirefoxDriver();
      }
      return REAL_FIREFOX_DRIVER;
    } else if (webDriverType.equals("HtmlUnit")) {
      if (REAL_CHROME_DRIVER != null) {
        REAL_CHROME_DRIVER.close();
        REAL_CHROME_DRIVER = null;
      }
      if (REAL_FIREFOX_DRIVER != null) {
        REAL_FIREFOX_DRIVER.close();
        REAL_FIREFOX_DRIVER = null;
      }
      if (REAL_SAFARI_DRIVER != null) {
        REAL_SAFARI_DRIVER.close();
        REAL_SAFARI_DRIVER = null;
      }
      if (REAL_IPAD_DRIVER != null) {
        REAL_IPAD_DRIVER.close();
        REAL_IPAD_DRIVER = null;
      }
      if (REAL_HTMLUNIT_DRIVER == null) {
        String javascriptEnabled = System.getProperty("WebDriverJavascriptEnabled");
        REAL_HTMLUNIT_DRIVER = new HtmlUnitDriver(javascriptEnabled.equals("true") ? true : false);
      }
      return REAL_HTMLUNIT_DRIVER;
    } else if (webDriverType.equals("Safari")) {
      if (REAL_CHROME_DRIVER != null) {
        REAL_CHROME_DRIVER.close();
        REAL_CHROME_DRIVER = null;
      }
      if (REAL_FIREFOX_DRIVER != null) {
        REAL_FIREFOX_DRIVER.close();
        REAL_FIREFOX_DRIVER = null;
      }
      if (REAL_HTMLUNIT_DRIVER != null) {
        REAL_HTMLUNIT_DRIVER.close();
        REAL_HTMLUNIT_DRIVER = null;
      }
      if (REAL_IPAD_DRIVER != null) {
        REAL_IPAD_DRIVER.close();
        REAL_IPAD_DRIVER = null;
      }
      if (REAL_SAFARI_DRIVER == null) {
        REAL_SAFARI_DRIVER = new SafariDriver();
      }
      return REAL_SAFARI_DRIVER;
    } else if (webDriverType.equals("Ipad")) {
      if (REAL_CHROME_DRIVER != null) {
        REAL_CHROME_DRIVER.close();
        REAL_CHROME_DRIVER = null;
      }
      if (REAL_HTMLUNIT_DRIVER != null) {
        REAL_HTMLUNIT_DRIVER.close();
        REAL_HTMLUNIT_DRIVER = null;
      }
      if (REAL_SAFARI_DRIVER != null) {
        REAL_SAFARI_DRIVER.close();
        REAL_SAFARI_DRIVER = null;
      }
      if (REAL_FIREFOX_DRIVER != null) {
        REAL_FIREFOX_DRIVER.close();
        REAL_FIREFOX_DRIVER = null;
      }
      if (REAL_IPAD_DRIVER == null) {
        // This uses Appium as a remote webdriver
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability("deviceName", "iPad 2");
        // Or can use, e.g., iPhone via:
        // desiredCapabilities.setCapability("deviceName", "iPhone 6");
        desiredCapabilities.setCapability("platformName", "iOS");
        desiredCapabilities.setCapability("platformVersion", "9.2");
        desiredCapabilities.setCapability("browserName", "safari");
        URL url = null;
        try {
          // Url of the Appium server
          url = new URL("http://127.0.0.1:4723/wd/hub");
        } catch (MalformedURLException e) {
          e.printStackTrace();
        }
        REAL_IPAD_DRIVER = new IOSDriver<WebElement>(url, desiredCapabilities);
      }
      return REAL_IPAD_DRIVER;
    } else {
      fail("Invalid WebDriverType: " + webDriverType);
    }

    // Should not get here
    return null;
  }

  @Override
  public void close() {
    if (Thread.currentThread() != CLOSE_THREAD) {
      throw new UnsupportedOperationException(
          "You shouldn't close this WebDriver. It's shared and will close when the JVM exits.");
    }
    super.close();
  }

  @Before
  public void deleteAllCookies() {
    // iPad tests fail if we delete cookies - so don't
    String webDriverType = System.getProperty("WebDriverType");
    if (!webDriverType.equals("Ipad")) {
      manage().deleteAllCookies();
    }
  }

  @After
  public void embedScreenshot(Scenario scenario) {
    try {
      if (!scenario.isFailed()) {
        // Take a screenshot only in the failure case
        return;
      }

      String webDriverType = System.getProperty("WebDriverType");
      if (!webDriverType.equals("HtmlUnit")) {
        // HtmlUnit does not support screenshots
        byte[] screenshot = getScreenshotAs(OutputType.BYTES);
        scenario.embed(screenshot, "image/png");
      }
    } catch (WebDriverException somePlatformsDontSupportScreenshots) {
      scenario.write(somePlatformsDontSupportScreenshots.getMessage());
    }
  }
}