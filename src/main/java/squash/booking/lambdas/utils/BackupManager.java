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

import squash.deployment.lambdas.utils.IS3TransferManager;
import squash.deployment.lambdas.utils.S3TransferManager;
import squash.deployment.lambdas.utils.TransferUtils;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * Manages backups of the bookings database.
 * 
 * <p>This manages backups of the bookings database.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BackupManager implements IBackupManager {

  private IBookingManager bookingManager;
  private Region region;
  private String databaseBackupBucketName;
  private String databaseBackupSnsTopicArn;
  private LambdaLogger logger;

  @Override
  public final void initialise(IBookingManager bookingManager, LambdaLogger logger)
      throws IOException {
    this.bookingManager = bookingManager;
    this.logger = logger;
    databaseBackupBucketName = getStringProperty("databasebackupbucketname");
    databaseBackupSnsTopicArn = getStringProperty("databasebackupsnstopicarn");
    region = Region.getRegion(Regions.fromName(getStringProperty("region")));
  }

  @Override
  public final void backupSingleBooking(Booking booking, Boolean isCreation)
      throws InterruptedException, JsonProcessingException {
    // Backup to the S3 bucket. This method will typically be called every time
    // a booking is mutated. We upload the booking to the same key, so the
    // versions of this key should provide a timeline of all individual bookings
    // in the sequence they were made.

    // Encode booking as JSON
    ObjectMapper mapper = new ObjectMapper();
    mapper.setSerializationInclusion(Include.NON_EMPTY);
    mapper.setSerializationInclusion(Include.NON_NULL);
    String backupString = (isCreation ? "Booking created: " : "Booking deleted: ")
        + System.getProperty("line.separator") + mapper.writeValueAsString(booking);

    logger.log("Backing up single booking mutation to S3 bucket");
    IS3TransferManager transferManager = getS3TransferManager();
    byte[] bookingAsBytes = backupString.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream bookingAsStream = new ByteArrayInputStream(bookingAsBytes);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(bookingAsBytes.length);
    PutObjectRequest putObjectRequest = new PutObjectRequest(databaseBackupBucketName,
        "LatestBooking", bookingAsStream, metadata);
    TransferUtils.waitForS3Transfer(transferManager.upload(putObjectRequest), logger);
    logger.log("Backed up single booking mutation to S3 bucket: " + backupString);

    // Backup to the SNS topic
    logger.log("Backing up single booking mutation to SNS topic: " + databaseBackupSnsTopicArn);
    getSNSClient().publish(databaseBackupSnsTopicArn, backupString, "Sqawsh single booking backup");
  }

  @Override
  public final List<Booking> backupAllBookings() throws Exception {
    List<Booking> bookings = bookingManager.getAllBookings();

    // Encode bookings as JSON
    ObjectMapper mapper = new ObjectMapper();
    mapper.setSerializationInclusion(Include.NON_EMPTY);
    mapper.setSerializationInclusion(Include.NON_NULL);
    String backupString = mapper.writeValueAsString(bookings);

    logger.log("Backing up all bookings to S3 bucket");
    IS3TransferManager transferManager = getS3TransferManager();
    byte[] bookingAsBytes = backupString.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream bookingAsStream = new ByteArrayInputStream(bookingAsBytes);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(bookingAsBytes.length);
    PutObjectRequest putObjectRequest = new PutObjectRequest(databaseBackupBucketName,
        "AllBookings", bookingAsStream, metadata);
    TransferUtils.waitForS3Transfer(transferManager.upload(putObjectRequest), logger);
    logger.log("Backed up all bookings to S3 bucket: " + backupString);

    // Backup to the SNS topic
    logger.log("Backing up all bookings to SNS topic: " + databaseBackupSnsTopicArn);
    getSNSClient().publish(databaseBackupSnsTopicArn, backupString, "Sqawsh all-bookings backup");

    return bookings;
  }

  @Override
  public final void restoreBookings(List<Booking> bookings, Boolean clearBeforeRestore)
      throws Exception {
    if (clearBeforeRestore) {
      // It is possible that not all bookings can be restored within the
      // execution time limit of lambda functions, whilst avoiding 'Too many
      // requests' errors. This boolean allows for doing the restore in multiple
      // parts to workaround this.
      logger.log("About to delete all bookings from the database");
      bookingManager.deleteAllBookings();
    }

    // Restore bookings
    logger.log("About to restore the provided bookings to the database");
    logger.log("Got " + bookings.size() + " bookings to restore");
    for (Booking booking : bookings) {
      bookingManager.createBooking(booking);
      // sleep to avoid Too Many Requests error
      Thread.sleep(500);
    }
    logger.log("Restored all bookings to the database");
  }

  /**
   * Returns a named property from the SquashCustomResource settings file.
   */
  protected String getStringProperty(String propertyName) throws IOException {
    // Use a getter here so unit tests can substitute a mock value.
    // We get the value from a settings file so that
    // CloudFormation can substitute the actual value when the
    // stack is created, by replacing the settings file.

    Properties properties = new Properties();
    try (InputStream stream = BookingManager.class
        .getResourceAsStream("/squash/booking/lambdas/SquashCustomResource.settings")) {
      properties.load(stream);
    } catch (IOException e) {
      logger.log("Exception caught reading SquashCustomResource.settings properties file: "
          + e.getMessage());
      throw e;
    }
    return properties.getProperty(propertyName);
  }

  /**
   * Returns an SNS client.
   *
   * <p>This method is provided so unit tests can mock out SNS.
   */
  protected AmazonSNS getSNSClient() {

    // Use a getter here so unit tests can substitute a mock client
    AmazonSNS client = new AmazonSNSClient();
    client.setRegion(region);
    return client;
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
}