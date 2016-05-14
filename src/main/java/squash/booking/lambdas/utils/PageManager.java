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

package squash.booking.lambdas.utils;

import squash.deployment.lambdas.utils.ExceptionUtils;
import squash.deployment.lambdas.utils.IS3TransferManager;
import squash.deployment.lambdas.utils.S3TransferManager;
import squash.deployment.lambdas.utils.TransferUtils;

import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.google.common.collect.Lists;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Manages all interactions with the website pages in the S3 bucket.
 *
 * <p>This manages all modifications to the website pages - which are currently
 *    served from an S3 bucket.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PageManager implements IPageManager {

  private String websiteBucketName;
  private Region region;
  private IBookingManager bookingManager;
  private LambdaLogger logger;

  @Override
  public void Initialise(IBookingManager bookingManager, LambdaLogger logger) throws Exception {
    this.logger = logger;
    websiteBucketName = getStringProperty("s3websitebucketname");
    region = Region.getRegion(Regions.fromName(getStringProperty("region")));
    this.bookingManager = bookingManager;
  }

  @Override
  public String refreshPage(String date, List<String> validDates, String apiGatewayBaseUrl,
      Boolean createDuplicate, List<Booking> bookings) throws Exception {
    // To workaround S3 ReadAfterUpdate and ReadAfterDelete being only
    // eventually-consistent, we save new booking page and also a duplicate
    // with a unique name - and we redirect to this duplicate - which _will_
    // have ReadAfterWrite consistency, since it is a new key.
    String pageGuid = UUID.randomUUID().toString();

    logger.log("About to create booking page with guid: " + pageGuid);
    String newPage = createBookingPage(date, validDates, apiGatewayBaseUrl + "/reservationform",
        apiGatewayBaseUrl + "/cancellationform", "http://" + websiteBucketName + ".s3-website-"
            + region + ".amazonaws.com", bookings, pageGuid);
    logger.log("Created booking page with guid: " + pageGuid);

    logger.log("About to copy booking page to S3");
    copyUpdatedBookingPageToS3(date, newPage, createDuplicate ? pageGuid : "");
    logger.log("Copied booking page to S3");

    // Create cached booking data as JSON for the Angularjs app to use
    logger.log("About to create and upload cached booking data to S3");
    copyJsonDataToS3(date, createCachedBookingData(date, validDates, bookings));
    logger.log("Uploaded cached booking data to S3");

    return pageGuid;
  }

  @Override
  public void refreshAllPages(List<String> validDates, String apiGatewayBaseUrl) throws Exception {
    // Upload all bookings pages, cached booking data, famous players data, and
    // the index page to the S3 bucket. N.B. This should upload for the
    // most-future date first to ensure all links are valid during the several
    // seconds the update takes to complete.
    logger.log("About to refresh S3 website at midnight");
    logger.log("Using valid dates: " + validDates);
    logger.log("Using ApigatewayBaseUrl: " + apiGatewayBaseUrl);

    // Log time to sanity check it does occur at midnight. (_Think_ this
    // accounts for BST?)
    logger.log("Current London time is: "
        + Calendar.getInstance().getTime().toInstant()
            .atZone(TimeZone.getTimeZone("Europe/London").toZoneId())
            .format(DateTimeFormatter.ofPattern("h:mm a")));

    uploadBookingsPagesToS3(validDates, apiGatewayBaseUrl);
    logger.log("Uploaded new set of bookings pages to S3 at midnight");

    // Save the valid dates in JSON form
    logger.log("About to create and upload cached valid dates data to S3");
    copyJsonDataToS3("validdates", createValidDatesData(validDates));
    logger.log("Uploaded cached valid dates data to S3");

    logger.log("About to upload famous players data to S3");
    uploadFamousPlayers();
    logger.log("Uploaded famous players data to S3");

    // Remove the now-previous day's bookings page and cached data from S3.
    // (If this page does not exist then this is a no-op.)
    String yesterdaysDate = getCurrentLocalDate().minusDays(1).format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    logger.log("About to remove yesterday's booking page and cached data from S3 bucket: "
        + websiteBucketName + " and key: " + yesterdaysDate + ".html");
    IS3TransferManager transferManager = getS3TransferManager();
    DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(websiteBucketName,
        yesterdaysDate + ".html");
    AmazonS3 client = transferManager.getAmazonS3Client();
    client.deleteObject(deleteObjectRequest);
    deleteObjectRequest = new DeleteObjectRequest(websiteBucketName, yesterdaysDate + ".json");
    client.deleteObject(deleteObjectRequest);
    logger.log("Removed yesterday's booking page and cached data successfully from S3");
  }

  @Override
  public void uploadFamousPlayers() throws Exception {
    String famousPlayers;
    try {
      famousPlayers = IOUtils.toString(PageManager.class
          .getResourceAsStream("/squash/booking/lambdas/utils/FamousPlayers.json"));
    } catch (IOException e) {
      logger.log("Exception caught reading FamousPlayers.json file: " + e.getMessage());
      throw new Exception("Exception caught reading FamousPlayers.json file");
    }
    logger.log("Uploading famousplayers.json to S3");
    copyJsonDataToS3("famousplayers", famousPlayers);
    logger.log("Uploaded famousplayers.json to S3 successfully");
  }

  /**
   * Creates and returns the website's booking page for a specified date.
   * 
   * <p>This is not private only so that it can be unit-tested.
   * 
   * @param date the date in YYYY-MM-DD format.
   * @param validDates the dates for which bookings can be made, in YYYY-MM-DD format.
   * @param reservationFormGetUrl the Url from which to get a reservation form
   * @param cancellationFormGetUrl the Url from which to get a cancellation form.
   * @param s3WebsiteUrl the base Url of the bookings website bucket.
   * @param bookings the bookings for the specified date.
   * @param pageGuid the guid to embed within the page - used by AATs.
   */
  protected String createBookingPage(String date, List<String> validDates,
      String reservationFormGetUrl, String cancellationFormGetUrl, String s3WebsiteUrl,
      List<Booking> bookings, String pageGuid) throws IllegalArgumentException {

    // N.B. we assume that the date is known to be a valid date
    logger.log("About to create booking page");

    Integer numCourts = 5;

    // Get dates in longhand format for display on the dropdown
    DateTimeFormatter longFormatter = DateTimeFormatter.ofPattern("EE, d MMM, yyyy");
    DateTimeFormatter shortFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    List<String> validDatesLong = new ArrayList<>();
    validDates.stream().forEach((validDate) -> {
      LocalDate localDate = java.time.LocalDate.parse(validDate, shortFormatter);
      validDatesLong.add(localDate.format(longFormatter));
    });

    // In order to merge the day's bookings with our velocity template, we need
    // to create an object with bookings on a grid corresponding to the html
    // table. For each grid cell, we need to know whether the cell is booked,
    // and if it is, the names of the players who've booked it.
    logger.log("About to set up velocity context");
    List<ArrayList<Boolean>> bookedState = new ArrayList<>();
    List<ArrayList<String>> players = new ArrayList<>();
    // First set up default arrays for case of no bookings
    for (int slot = 1; slot <= 16; slot++) {
      bookedState.add(new ArrayList<>());
      players.add(new ArrayList<>());
      for (int court = 1; court <= numCourts; court++) {
        bookedState.get(slot - 1).add(false);
        players.get(slot - 1).add("");
      }
    }
    // Mutate cells which are in fact booked
    for (Booking booking : bookings) {
      bookedState.get(booking.slot - 1).set(booking.court - 1, true);
      players.get(booking.slot - 1).set(booking.court - 1, booking.players);
    }

    // Create the page by merging the data with the page template
    VelocityEngine engine = new VelocityEngine();
    // Use the classpath loader so Velocity finds our template
    Properties properties = new Properties();
    properties.setProperty("resource.loader", "class");
    properties.setProperty("class.resource.loader.class",
        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
    engine.init(properties);

    VelocityContext context = new VelocityContext();
    context.put("pageGuid", pageGuid);
    context.put("s3WebsiteUrl", s3WebsiteUrl);
    context.put("reservationFormGetUrl", reservationFormGetUrl);
    context.put("cancellationFormGetUrl", cancellationFormGetUrl);
    context.put("pagesDate", date);
    context.put("validDates", validDates);
    context.put("validDatesLong", validDatesLong);
    context.put("numCourts", numCourts);
    context.put("timeSlots", getTimeSlotLabels());
    context.put("bookedState", bookedState);
    context.put("players", players);
    logger.log("Set up velocity context");

    // TODO assert some sensible invariants on data sizes?

    // Render the page
    logger.log("About to render booking page");
    StringWriter writer = new StringWriter();
    Template template = engine.getTemplate("squash/booking/lambdas/BookingPage.vm", "utf-8");
    template.merge(context, writer);
    logger.log("Rendered booking page: " + writer);
    return writer.toString();
  }

  /**
   * Returns JSON-encoded booking data for a specified date.
   * 
   * <p>This is not private only so that it can be unit-tested.
   * 
   * @param date the date in YYYY-MM-DD format.
   * @param validDates the dates for which bookings can be made, in YYYY-MM-DD format.
   * @param bookings the bookings for the specified date.
   * @throws JSONException 
   */
  protected String createCachedBookingData(String date, List<String> validDates,
      List<Booking> bookings) throws IllegalArgumentException, JSONException {

    // N.B. we assume that the date is known to be a valid date
    logger.log("About to create cached booking data");

    // Encode bookings as JSON
    JSONObject bookingsJson = new JSONObject();
    bookingsJson.put("date", date);
    bookingsJson.put("validdates", new ArrayList<String>());
    for (int i = 0; i < validDates.size(); i++) {
      bookingsJson.accumulate("validdates", validDates.get(i));
    }
    bookingsJson.put("bookings", new ArrayList<String>());
    for (int i = 0; i < bookings.size(); i++) {
      Booking booking = bookings.get(i);
      JSONObject bookingJson = new JSONObject();
      bookingJson.put("court", booking.getCourt());
      bookingJson.put("slot", booking.getSlot());
      bookingJson.put("players", booking.getPlayers());
      bookingsJson.accumulate("bookings", bookingJson);
    }

    String bookingDataString = bookingsJson.toString();
    logger.log("Created cached booking data: " + bookingDataString);

    return bookingDataString;
  }

  /**
   * Returns JSON-encoded valid-dates data for a specified date.
   * 
   * <p>This is not private only so that it can be unit-tested.
   * 
   * @param validDates the dates for which bookings can be made, in YYYY-MM-DD format.
   * @throws JSONException 
   */
  protected String createValidDatesData(List<String> validDates) throws IllegalArgumentException,
      JSONException {

    // N.B. we assume that the date is known to be a valid date
    logger.log("About to create cached valid dates data");

    // Encode valid dates as JSON
    JSONObject validDatesJson = new JSONObject();
    validDatesJson.put("dates", new ArrayList<String>());
    for (int i = 0; i < validDates.size(); i++) {
      validDatesJson.accumulate("dates", validDates.get(i));
    }
    String validDatesDataString = validDatesJson.toString();
    logger.log("Created cached valid dates data: " + validDatesDataString);

    return validDatesDataString;
  }

  private List<String> getTimeSlotLabels() {

    // First time slot of the day is 10am...
    // ...so initialise to one time slot (i.e. 45 minutes) earlier
    logger.log("About to get time slot labels");
    LocalTime time = LocalTime.of(9, 15);
    List<String> timeSlots = new ArrayList<>();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
    for (int slots = 1; slots <= 16; slots++) {
      time = time.plusMinutes(45);
      timeSlots.add(time.format(formatter));
    }
    logger.log("Got slot labels: " + timeSlots);

    return timeSlots;
  }

  private void copyJsonDataToS3(String keyName, String jsonToCopy) throws Exception {

    logger.log("About to copy cached json data to S3");

    try {
      logger.log("Uploading json data to S3 bucket: " + websiteBucketName + " and key: " + keyName
          + ".json");
      byte[] jsonAsBytes = jsonToCopy.getBytes(StandardCharsets.UTF_8);
      ByteArrayInputStream jsonAsStream = new ByteArrayInputStream(jsonAsBytes);
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(jsonAsBytes.length);
      metadata.setContentType("application/json");
      // Direct caches not to satisfy future requests with this data and
      // not to store it - at least for HTTP 1.1 - we want agents to get
      // the latest data from S3 on every request.
      metadata.setCacheControl("no-cache, no-store, must-revalidate");
      PutObjectRequest putObjectRequest = new PutObjectRequest(websiteBucketName,
          keyName + ".json", jsonAsStream, metadata);
      // Data must be public so it can be served from the website
      putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);
      IS3TransferManager transferManager = getS3TransferManager();
      TransferUtils.waitForS3Transfer(transferManager.upload(putObjectRequest), logger);
      logger.log("Uploaded cached json data to S3 bucket");
    } catch (AmazonServiceException ase) {
      ExceptionUtils.logAmazonServiceException(ase, logger);
      throw new Exception("Exception caught while copying json data to S3");
    } catch (AmazonClientException ace) {
      ExceptionUtils.logAmazonClientException(ace, logger);
      throw new Exception("Exception caught while copying json data to S3");
    } catch (InterruptedException e) {
      logger.log("Caught interrupted exception: ");
      logger.log("Error Message: " + e.getMessage());
      throw new Exception("Exception caught while copying json data to S3");
    }
  }

  private void copyUpdatedBookingPageToS3(String pageBaseName, String page, String uidSuffix)
      throws Exception {

    logger.log("About to copy booking page to S3");

    try {
      logger.log("Uploading booking page to S3 bucket: " + websiteBucketName
          + "s3websitebucketname" + " and key: " + pageBaseName + uidSuffix + ".html");
      byte[] pageAsBytes = page.getBytes(StandardCharsets.UTF_8);
      ByteArrayInputStream pageAsStream = new ByteArrayInputStream(pageAsBytes);
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(pageAsBytes.length);
      metadata.setContentType("text/html");
      // Direct caches not to satisfy future requests with this data and
      // not to store it - at least for HTTP 1.1 - we want agents to get
      // the latest bookings from S3 on every request.
      metadata.setCacheControl("no-cache, no-store, must-revalidate");
      PutObjectRequest putObjectRequest = new PutObjectRequest(websiteBucketName, pageBaseName
          + uidSuffix + ".html", pageAsStream, metadata);
      // Page must be public so it can be served from the website
      putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);
      IS3TransferManager transferManager = getS3TransferManager();
      TransferUtils.waitForS3Transfer(transferManager.upload(putObjectRequest), logger);
      logger.log("Uploaded booking page to S3 bucket");

      if (uidSuffix.equals("")) {
        // Nothing to copy - so return
        logger.log("UidSuffix is empty - so not creating duplicate page");
        return;
      }

      // N.B. We copy from hashed key to non-hashed (and not vice versa)
      // to ensure consistency
      logger.log("Copying booking page in S3 bucket: " + websiteBucketName + " and key: "
          + pageBaseName + ".html");
      CopyObjectRequest copyObjectRequest = new CopyObjectRequest(websiteBucketName, pageBaseName
          + uidSuffix + ".html", websiteBucketName, pageBaseName + ".html");
      copyObjectRequest.setCannedAccessControlList(CannedAccessControlList.PublicRead);
      // N.B. Copied object will get same metadata as the source (e.g. the
      // cache-control header)
      TransferUtils.waitForS3Transfer(transferManager.copy(copyObjectRequest), logger);
      logger.log("Copied booking page successfully in S3");
    } catch (AmazonServiceException ase) {
      ExceptionUtils.logAmazonServiceException(ase, logger);
      throw new Exception("Exception caught while copying booking page to S3");
    } catch (AmazonClientException ace) {
      ExceptionUtils.logAmazonClientException(ace, logger);
      throw new Exception("Exception caught while copying booking page to S3");
    } catch (InterruptedException e) {
      logger.log("Caught interrupted exception: ");
      logger.log("Error Message: " + e.getMessage());
      throw new Exception("Exception caught while copying booking page to S3");
    }
  }

  private void uploadBookingsPagesToS3(List<String> validDates, String apiGatewayBaseUrl)
      throws Exception {
    logger.log("About to upload booking page for each valid date");

    String currentDate = validDates.get(0);
    logger.log("About to refresh index page for: " + currentDate);
    refreshIndexPage(currentDate);
    logger.log("Refreshed index page");

    // Dates will be in time order. We want to iterate in reverse time order to
    // ensure that we refresh the most-future page first, which ensures all
    // links remain valid during the update process.
    List<Booking> bookings;
    for (String validDate : Lists.reverse(validDates)) {
      logger.log("About to upload booking page for: " + validDate);
      bookings = bookingManager.getBookings(validDate);
      refreshPage(validDate, validDates, // Still in forward time order
          apiGatewayBaseUrl, false, bookings);
    }
    logger.log("Uploaded booking page for each valid date");
  }

  private void refreshIndexPage(String currentDate) throws Exception {
    // This page will redirect to the current day's page. It is there
    // to handle case where a client has a booking page open which
    // has a link to an earlier page that has now expired (which means
    // at least one midnight must have passed since they fetched the
    // page). It also handles other generally-messed-up urls.
    String indexPage = createIndexPage("http://" + websiteBucketName + ".s3-website-" + region
        + ".amazonaws.com?selectedDate=" + currentDate + ".html");

    logger.log("About to upload index page");
    copyUpdatedBookingPageToS3("today", indexPage, "");
    logger.log("Uploaded index page");
  }

  /**
   * Creates and returns the website's index page.
   * 
   * <p>This is not private only so that it can be unit-tested.
   * 
   * @param redirectUrl the Url of the booking page for the current date.
   */
  protected String createIndexPage(String redirectUrl) {

    logger.log("About to create the index page");
    // Create the page by merging the data with the page template
    VelocityEngine engine = new VelocityEngine();
    // Use the classpath loader so Velocity finds our template
    Properties properties = new Properties();
    properties.setProperty("resource.loader", "class");
    properties.setProperty("class.resource.loader.class",
        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
    engine.init(properties);

    VelocityContext context = new VelocityContext();
    context.put("redirectUrl", redirectUrl);

    // Render the page
    StringWriter writer = new StringWriter();
    Template template = engine.getTemplate("squash/booking/lambdas/IndexPage.vm", "utf-8");
    template.merge(context, writer);
    logger.log("Rendered index page: " + writer);
    return writer.toString();
  }

  /**
   * Returns a named property from the SquashCustomResource settings file.
   */
  protected String getStringProperty(String propertyName) throws Exception {
    // Use a getter here so unit tests can substitute a mock value.
    // We get the value from a settings file so that
    // CloudFormation can substitute the actual value when the
    // stack is created, by replacing the settings file.

    Properties properties = new Properties();
    try (InputStream stream = PageManager.class
        .getResourceAsStream("/squash/booking/lambdas/SquashCustomResource.settings")) {
      properties.load(stream);
    } catch (IOException e) {
      logger.log("Exception caught reading SquashCustomResource.settings properties file: "
          + e.getMessage());
      throw new Exception("Exception caught reading SquashCustomResource.settings properties file");
    }
    return properties.getProperty(propertyName);
  }

  /**
   * Returns an IS3TransferManager.
   * 
   * <p>This method is provided so unit tests can mock out S3.
   */
  protected IS3TransferManager getS3TransferManager() {
    // Use a getter here so unit tests can substitute a mock transfermanager
    return new S3TransferManager();
  }

  /**
   * Returns the current London local date.
   */
  protected LocalDate getCurrentLocalDate() {
    // Use a getter here so unit tests can substitute a different date.

    // This gets the correct local date no matter what the user's device
    // system time may say it is, and no matter where in AWS we run.
    return BookingsUtilities.getCurrentLocalDate();
  }
}