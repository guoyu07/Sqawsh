/**
 * Copyright 2015-2017 Robin Steel
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

package squash.booking.lambdas.core;

import squash.deployment.lambdas.utils.IS3TransferManager;
import squash.deployment.lambdas.utils.RetryHelper;
import squash.deployment.lambdas.utils.S3TransferManager;
import squash.deployment.lambdas.utils.TransferUtils;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Manages backups of the bookings/rules database.
 * 
 * <p>This manages backups of the bookings/rules database.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BackupManager implements IBackupManager {

  private IRuleManager ruleManager;
  private IBookingManager bookingManager;
  private Region region;
  private String databaseBackupBucketName;
  private String adminSnsTopicArn;
  private ObjectMapper mapper;
  private LambdaLogger logger;
  private Boolean initialised = false;

  @Override
  public final void initialise(IBookingManager bookingManager, IRuleManager ruleManager,
      LambdaLogger logger) throws Exception {
    this.ruleManager = ruleManager;
    this.bookingManager = bookingManager;
    this.logger = logger;
    databaseBackupBucketName = getEnvironmentVariable("DatabaseBackupBucket");
    adminSnsTopicArn = getEnvironmentVariable("AdminSNSTopicArn");
    region = Region.getRegion(Regions.fromName(getEnvironmentVariable("AWS_REGION")));

    // Prepare to serialise bookings and booking rules as JSON.
    mapper = new ObjectMapper();
    mapper.setSerializationInclusion(Include.NON_EMPTY);
    mapper.setSerializationInclusion(Include.NON_NULL);

    initialised = true;
  }

  @Override
  public final void backupSingleBooking(Booking booking, Boolean isCreation)
      throws InterruptedException, JsonProcessingException {
    // Backup to the S3 bucket. This method will typically be called every time
    // a booking is mutated. We upload the booking to the same key, so the
    // versions of this key should provide a timeline of all individual bookings
    // in the sequence (or close to it) that they were made.

    if (!initialised) {
      throw new IllegalStateException("The backup manager has not been initialised");
    }

    // Encode booking as JSON
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
    logger.log("Backing up single booking mutation to SNS topic: " + adminSnsTopicArn);
    getSNSClient().publish(adminSnsTopicArn, backupString, "Sqawsh single booking backup");
  }

  @Override
  public final void backupSingleBookingRule(BookingRule bookingRule, Boolean isNotDeletion)
      throws InterruptedException, JsonProcessingException {
    // Backup to the S3 bucket. This method will typically be called every time
    // a booking rule is mutated. We upload the booking rule to the same key, so
    // the versions of this key should provide a timeline of all individual
    // booking rules in the sequence (or close to it) that they were made.

    if (!initialised) {
      throw new IllegalStateException("The backup manager has not been initialised");
    }

    // Encode booking rule as JSON
    String backupString = (isNotDeletion ? "Booking rule updated: " : "Booking rule deleted: ")
        + System.getProperty("line.separator") + mapper.writeValueAsString(bookingRule);

    logger.log("Backing up single booking rule mutation to S3 bucket");
    IS3TransferManager transferManager = getS3TransferManager();
    byte[] bookingRuleAsBytes = backupString.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream bookingRuleAsStream = new ByteArrayInputStream(bookingRuleAsBytes);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(bookingRuleAsBytes.length);
    PutObjectRequest putObjectRequest = new PutObjectRequest(databaseBackupBucketName,
        "LatestBookingRule", bookingRuleAsStream, metadata);
    TransferUtils.waitForS3Transfer(transferManager.upload(putObjectRequest), logger);
    logger.log("Backed up single booking rule mutation to S3 bucket: " + backupString);

    // Backup to the SNS topic
    logger.log("Backing up single booking rule mutation to SNS topic: " + adminSnsTopicArn);
    getSNSClient().publish(adminSnsTopicArn, backupString, "Sqawsh single booking rule backup");
  }

  @Override
  public final ImmutablePair<List<Booking>, List<BookingRule>> backupAllBookingsAndBookingRules()
      throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The backup manager has not been initialised");
    }

    // Encode bookings and booking rules as JSON
    JsonNodeFactory factory = new JsonNodeFactory(false);
    // Create a json factory to write the treenode as json.
    JsonFactory jsonFactory = new JsonFactory();
    ObjectNode rootNode = factory.objectNode();

    ArrayNode bookingsNode = rootNode.putArray("bookings");
    List<Booking> bookings = bookingManager.getAllBookings(false);
    for (Booking booking : bookings) {
      bookingsNode.add((JsonNode) (mapper.valueToTree(booking)));
    }

    ArrayNode bookingRulesNode = rootNode.putArray("bookingRules");
    List<BookingRule> bookingRules = ruleManager.getRules(false);
    for (BookingRule bookingRule : bookingRules) {
      bookingRulesNode.add((JsonNode) (mapper.valueToTree(bookingRule)));
    }

    // Add this, as will be needed for restore in most common case.
    rootNode.put("clearBeforeRestore", true);

    ByteArrayOutputStream backupDataStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(backupDataStream);
    try (JsonGenerator generator = jsonFactory.createGenerator(printStream)) {
      mapper.writeTree(generator, rootNode);
    }
    String backupString = backupDataStream.toString(StandardCharsets.UTF_8.name());

    logger.log("Backing up all bookings and booking rules to S3 bucket");
    IS3TransferManager transferManager = getS3TransferManager();
    byte[] backupAsBytes = backupString.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream backupAsStream = new ByteArrayInputStream(backupAsBytes);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(backupAsBytes.length);
    PutObjectRequest putObjectRequest = new PutObjectRequest(databaseBackupBucketName,
        "AllBookingsAndBookingRules", backupAsStream, metadata);
    TransferUtils.waitForS3Transfer(transferManager.upload(putObjectRequest), logger);
    logger.log("Backed up all bookings and booking rules to S3 bucket: " + backupString);

    // Backup to the SNS topic
    logger.log("Backing up all bookings and booking rules to SNS topic: " + adminSnsTopicArn);
    getSNSClient().publish(adminSnsTopicArn, backupString,
        "Sqawsh all-bookings and booking rules backup");

    return new ImmutablePair<>(bookings, bookingRules);
  }

  @Override
  public final void restoreAllBookingsAndBookingRules(List<Booking> bookings,
      List<BookingRule> bookingRules, Boolean clearBeforeRestore) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The backup manager has not been initialised");
    }

    if (clearBeforeRestore) {
      // It is possible that not all bookings and booking rules can be restored
      // within the execution time limit of lambda functions, whilst avoiding
      // 'Too many requests' errors. This boolean allows for doing the restore
      // in multiple parts to workaround this.
      logger.log("About to delete all bookings from the database");
      bookingManager.deleteAllBookings(false);
      logger.log("Deleted all bookings from the database");
      logger.log("About to delete all booking rules from the database");
      ruleManager.deleteAllBookingRules(false);
      logger.log("Deleted all booking rules from the database");
    }

    // Restore bookings
    logger.log("About to restore the provided bookings to the database");
    logger.log("Got " + bookings.size() + " bookings to restore");
    for (Booking booking : bookings) {
      validateDates(Arrays.asList(booking.getDate()));
      bookingManager.validateBooking(booking);

      RetryHelper.DoWithRetries(() -> bookingManager.createBooking(booking, false),
          AmazonServiceException.class, Optional.of("429"), logger);
    }
    logger.log("Restored all bookings to the database");

    // Restore booking rules
    logger.log("About to restore the provided booking rules to the database");
    logger.log("Got " + bookingRules.size() + " booking rules to restore");
    for (BookingRule bookingRule : bookingRules) {
      // Verify dates are valid dates.
      List<String> datesToCheck = new ArrayList<>();
      datesToCheck.add(bookingRule.getBooking().getDate());
      Arrays.stream(bookingRule.getDatesToExclude()).forEach(
          (dateToExclude) -> datesToCheck.add(dateToExclude));
      validateDates(datesToCheck);
      bookingManager.validateBooking(bookingRule.getBooking());

      RetryHelper.DoWithRetries(() -> ruleManager.createRule(bookingRule, false),
          AmazonServiceException.class, Optional.of("429"), logger);

    }
    logger.log("Restored all booking rules to the database");
  }

  private void validateDates(List<String> datesToCheck) throws Exception {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    sdf.setLenient(false);
    if (datesToCheck.stream().filter((dateToCheck) -> {
      try {
        sdf.parse(dateToCheck);
      } catch (ParseException e) {
        logger.log("The date has an invalid format: " + dateToCheck);
        return true;
      }
      return false;
    }).count() > 0) {
      throw new Exception("One of the dates has an invalid format");
    }
  }

  /**
   * Returns a named environment variable.
   * @throws Exception 
   */
  protected String getEnvironmentVariable(String variableName) throws Exception {
    // Use a getter here so unit tests can substitute a mock value.
    // We get the value from an environment variable so that CloudFormation can
    // set the actual value when the stack is created.

    String environmentVariable = System.getenv(variableName);
    if (environmentVariable == null) {
      logger.log("Environment variable: " + variableName + " is not defined, so throwing.");
      throw new Exception("Environment variable: " + variableName + " should be defined.");
    }
    return environmentVariable;
  }

  /**
   * Returns an SNS client.
   *
   * <p>This method is provided so unit tests can mock out SNS.
   */
  protected AmazonSNS getSNSClient() {

    // Use a getter here so unit tests can substitute a mock client
    AmazonSNS client = AmazonSNSClientBuilder.standard().withRegion(region.getName()).build();
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