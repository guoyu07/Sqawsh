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

import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

import java.util.Properties;

/**
 * Runs the acceptance tests using the Chrome browser.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
// @formatter:off
@RunWith(Cucumber.class)
@CucumberOptions(
    plugin = {"pretty", "html:cucumberchromereport"},
    features="src/acceptanceTest/resources/features/squash",
    glue="steps", // So step definitions/hooks can be found
    tags = {"~@ignore"}, // So ignore tags are honoured
    monochrome=true, // So don't get spurious colour escape code noise in Eclipse console output
    strict=true // So scenarios fail if they have pending steps
)
// @formatter:on
public class RunChromeAcceptanceTests {

  // This will run before the entire test run - but only when using the
  // junit runner - rather than the cucumber command-line runner (To support the
  // Cucumber runner we'd need to use the Cucumber-specific Before annotation).
  // Idea is that we stand up a test version of the website on AWS, enter the
  // url here, and then run these AATs against it.
  @BeforeClass
  public static void setupSquashWebsiteUrl() {
    Properties p = new Properties(System.getProperties());
    p.setProperty("SquashWebsiteBaseUrl",
        "http://squashwebsite.s3-website-eu-west-1.amazonaws.com/?selectedDate=2016-07-03.html");
    // This will be read before each scenario to set up the webdriver
    p.setProperty("WebDriverType", "Chrome");
    p.setProperty("WebDriverJavascriptEnabled", "true");
    System.setProperties(p);
  }
}