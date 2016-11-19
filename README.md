Sqawsh
======

**License:** [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) (except where explicitly stated otherwise)

[![Build Status](https://travis-ci.org/robinsteel/Sqawsh.svg?branch=master)](https://travis-ci.org/robinsteel/Sqawsh)
[![Built with Grunt](https://cdn.gruntjs.com/builtwith.svg)](http://gruntjs.com/)

Sqawsh is a toy project to test drive [AWS microservices](https://aws.amazon.com/blogs/compute/microservices-without-the-servers/) - implementing a basic serverless squash court booking service.

Building (tested on Mac, Amazon Linux, and Windows)
----------------------------------------
* Ensure Java 8 JDK is installed
* Clone the repo
* From the repo's top level, run `./gw build` (or `gw build` on Windows) to:
 * download [gradle](http://gradle.org/)
 * build the project
 * run the [Junit](http://junit.org/) tests
 * create the JavaDocs in the `build/docs/javadoc` folder
 * create `Squash.zip` and `AngularjsApp.zip` in the `build/distributions` folder
 
Launching the booking service on AWS
------------------------------------
* Create a new [S3](https://aws.amazon.com/s3/) bucket and upload into it:
 * `Squash.zip` and `AngularjsApp.zip` from the `build/distributions` folder
 * `Squash.template` from the `src/main/resources/squash/deployment/templates` folder
* Go to the [Cloudformation](https://aws.amazon.com/cloudformation/) console in your chosen region
* Launch a stack:
 * using the uploaded `Squash.template`
 * specifying the parameters appropriately to point to the uploaded `Squash.zip` and `AngularjsApp.zip`
* The stack should complete in a few minutes
* The URL of the new court booking service will be provided as a stack output
* Check logs in [CloudwatchLogs](https://aws.amazon.com/cloudwatch/) to troubleshoot any problems
* Delete the stack when no longer needed to avoid excess charges!

Running the Acceptance tests
----------------------------
* There are rudimentary [Cucumber](https://cucumber.io/) WebDriver acceptance tests for several browsers
* These are intended to be run against a real booking service as created above
* You will also need to install:
 * the necessary webdrivers (see Selenium's [instructions](http://www.seleniumhq.org/projects/webdriver/))
 * [Appium](http://appium.io/) if you want to run the iPad tests
* For example, to run the Chrome tests, edit `RunChromeAcceptanceTests.java` to set the `SquashWebsiteBaseUrl` property to the booking service's URL as specified in the stack output
* Ensure there are no existing bookings on the service
* Run `RunChromeAcceptanceTests.java` as a Junit project

IDE support
-----------
* From the repo's top level, run `./gw eclipse` (or `gw eclipse` on Windows) to generate Eclipse project and classpath files

[![js-standard-style](https://cdn.rawgit.com/feross/standard/master/badge.svg)](https://github.com/feross/standard)