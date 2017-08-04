---
title: Deployment
layout: docs
---
To create your own booking service:

#### Building (tested on Mac, Amazon Linux, and Windows)
* Ensure Java 8 JDK is installed
* Clone the repo
* From the repo's top level, run `./gw build` (or `gw build` on Windows) to:
 * download [gradle](http://gradle.org/)
 * build the project
 * run the [Junit](http://junit.org/) tests
 * create the JavaDocs in the `build/docs/javadoc` folder
 * create `Squash.zip` and `AngularjsApp.zip` in the `build/distributions` folder
 
#### Launching the booking service on AWS
* Create a new [S3](https://aws.amazon.com/s3/) bucket and upload into it:
  * `Squash.zip` and `AngularjsApp.zip` from the `build/distributions` folder
  * `Squash.template` from the `src/main/resources/squash/deployment/templates` folder
* Go to the [Cloudformation](https://aws.amazon.com/cloudformation/) console in your chosen region
* Launch a stack using the uploaded `Squash.template`, providing (via stack parameters):
  * the S3 bucket with the uploaded `Squash.zip` and `AngularjsApp.zip`
  * the admin user's email address (to receive backups and notifications of various error events)
  * the S3 bucket to use for serving the booking website
  * the S3 bucket to use for booking and booking rule backups
* The stack should complete in a few minutes
* The URL of the new court booking service will be provided as a stack output
* Check logs in [CloudwatchLogs](https://aws.amazon.com/cloudwatch/) to troubleshoot any problems
* Delete the stack when no longer needed to avoid excess charges!

Any number of independent booking services can be created by repeating the above (with different S3 buckets).