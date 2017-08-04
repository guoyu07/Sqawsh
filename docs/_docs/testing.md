---
title: Testing
layout: docs
---

The tests are mainly [Junit](http://junit.org/) and [Karma](https://github.com/karma-runner/karma) unit tests, but there is a small number of [Cucumber](https://cucumber.io/) WebDriver acceptance tests and a single [JMeter](http://jmeter.apache.org/) load test.
## Unit tests
These are run by the [autobuild](https://travis-ci.org/robinsteel/Sqawsh), but can also be run locally.
#### Junit
Being serverless, the backend functionality is implemented using AWS Lambda functions - in this case all Java. These are fairly well covered by Junit unit tests, with JMock mocking out the AWS dependencies. To run locally use e.g. the Eclipse Junit runner or the gradle `test` task.
#### Karma
The Javascript version of the service frontend uses AngularJs and the controllers are all tested with Karma unit tests. To run these locally use `npm test` from the `angularjs` folder.
## Acceptance tests
Before starting on the AngularJs frontend I had a no-script frontend that had some end-to-end [Cucumber](https://cucumber.io/) WebDriver acceptance tests. Clients without javascript enabled can still use this frontend, but it has less functionality e.g. all admin features are javascript-only.
* These tests are intended to be run against a real booking service
* You will also need to install:
  * the necessary webdrivers (see Selenium's [instructions](http://www.seleniumhq.org/projects/webdriver/))
  * [Appium](http://appium.io/) if you want to run the iPad tests
* For example, to run the Chrome tests, edit `RunChromeAcceptanceTests.java` to set the `SquashWebsiteBaseUrl` property to the booking service's URL as specified in the stack output
* Ensure there are no existing bookings on the service
* Run `RunChromeAcceptanceTests.java` as a Junit project

## Load tests
There is a single JMeter load test that tests the service behaves when multiple players attempt to book the same free court simultaneously. It tests via the API directly as it's testing only the backend. It's slightly tricky to run as:
* it uses the generated java SDK for the booking service's ApiGateway API - so this must first be manually downloaded
* it uses custom JMeter samplers to invoke the API using this java SDK and to avoid having to manually sign requests with AWS credentials - so these need to be built and deployed where JMeter can find them

To run the load test:
* Install [JMeter](http://jmeter.apache.org/) - I installed via Homebrew on a Mac.
* Stand up the version of the booking service to be tested
* From the ApiGateway console, download the Java SDK for the booking service just created, setting options `Squash` as the 'Service Name' and `squash.booking` as the 'Java Package Name'
* Unzip this SDK to `build/apigateway-sdk/`
* Run `/gradlew build shadowJar` on this downloaded sdk
* Ensure `build.gradle` lists this generated shadow jar as a file dependency of the `loadtestCompile` configuration (this should already be done)
* Run `./gw deployJMeterCustomSamplersAndDependenciesJars` to build the custom samplers and deploy their jars to the correct JMeter folders
* Open `src\loadtest\resources\TestMultiplePlayersBookingSameCourt.jmx` using the JMeter GUI (from, e.g., `\usr\local\bin\Cellar\jmeter\3.1\bin`)
* Update the `APIGATEWAY_BASE_URL`, `COGNITO_IDENTITY_POOL_ID`, and `REGION` user-defined variables of the test plan to point to the actual values for the service being tested
* Run the JMeter test either from the JMeter GUI or (preferably) command-line
* Check the 'Overall results' Summary Report - this should show 0% error rate.