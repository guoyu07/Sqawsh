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

import static org.junit.Assert.assertTrue;

import squash.deployment.lambdas.utils.IS3TransferManager;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.sns.AmazonSNS;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link BackupManager}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BackupManagerTest {
  // Variables for setting up subclass of class under test
  squash.booking.lambdas.utils.BackupManagerTest.TestBackupManager backupManager;

  String databaseBackupSnsTopicArn;
  String databaseBackupBucketName;

  // Mocks
  Mockery mockery = new Mockery();
  Context mockContext;
  LambdaLogger mockLogger;
  IS3TransferManager mockTransferManager;
  IBookingManager mockBookingManager;
  AmazonSNS mockSNSClient;

  Integer court;
  Integer courtSpan;
  Integer slot;
  Integer slotSpan;
  String playersNames;
  Booking booking;
  String date = "2016-01-12";
  List<Booking> bookings;

  @Before
  public void beforeTest() throws Exception {

    backupManager = new squash.booking.lambdas.utils.BackupManagerTest.TestBackupManager();

    mockery = new Mockery();
    // Set up mock context
    mockContext = mockery.mock(Context.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockContext);
      }
    });
    // Set up mock logger
    mockLogger = mockery.mock(LambdaLogger.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockLogger);
      }
    });

    databaseBackupBucketName = "databaseBackupBucketName";
    backupManager.setDatabaseBackupBucketName(databaseBackupBucketName);
    databaseBackupSnsTopicArn = "databaseBackupSnsTopicArn";
    backupManager.setDatabaseBackupSnsTopicArn(databaseBackupSnsTopicArn);

    // Use a single booking for most of the tests
    court = 2;
    courtSpan = 4;
    slot = 12;
    slotSpan = 3;
    playersNames = "D.Playerd/F.Playerf";
    booking = new Booking(court, courtSpan, slot, slotSpan, playersNames);
    booking.setDate(date);
    bookings = new ArrayList<>();
    bookings.add(booking);
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test backup manager with some overrides to facilitate testing
  public class TestBackupManager extends BackupManager {
    private AmazonSNS snsClient;
    private IS3TransferManager transferManager;
    private String databaseBackupBucketName;
    private String databaseBackupSnsTopicArn;

    public void setS3TransferManager(IS3TransferManager transferManager) {
      this.transferManager = transferManager;
    }

    @Override
    public IS3TransferManager getS3TransferManager() {
      return transferManager;
    }

    public void setSNSClient(AmazonSNS snsClient) {
      this.snsClient = snsClient;
    }

    @Override
    public AmazonSNS getSNSClient() {
      return snsClient;
    }

    public void setDatabaseBackupBucketName(String databaseBackupBucketName) {
      this.databaseBackupBucketName = databaseBackupBucketName;
    }

    public void setDatabaseBackupSnsTopicArn(String databaseBackupSnsTopicArn) {
      this.databaseBackupSnsTopicArn = databaseBackupSnsTopicArn;
    }

    @Override
    public String getStringProperty(String propertyName) {
      if (propertyName.equals("databasebackupbucketname")) {
        return databaseBackupBucketName;
      }
      if (propertyName.equals("databasebackupsnstopicarn")) {
        return databaseBackupSnsTopicArn;
      }
      if (propertyName.equals("region")) {
        return "eu-west-1";
      }
      return null;
    }
  }

  @Test
  public void testBackupSingleBookingCorrectlyCallsS3() throws Exception {

    doTestBackupBookingsCorrectlyCallsS3(false);
  }

  @Test
  public void testBackupAllBookingsCorrectlyCallsS3() throws Exception {

    doTestBackupBookingsCorrectlyCallsS3(true);
  }

  public void doTestBackupBookingsCorrectlyCallsS3(Boolean backupAllBookings) throws Exception {

    // Set up mock booking manager
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockery.checking(new Expectations() {
      {
        if (!backupAllBookings) {
          ignoring(mockBookingManager);
        } else {
          oneOf(mockBookingManager).getAllBookings();
          will(returnValue(bookings));
        }
      }
    });
    backupManager.initialise(mockBookingManager, mockLogger);

    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);
    // Just check S3 upload called - don't bother checking argument details.
    mockery.checking(new Expectations() {
      {
        oneOf(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(returnValue(mockTransfer));
      }
    });
    backupManager.setS3TransferManager(mockTransferManager);

    // Set up mock SNS client
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockSNSClient);
      }
    });
    backupManager.setSNSClient(mockSNSClient);

    // ACT
    if (backupAllBookings) {
      backupManager.backupAllBookings();
    } else {
      backupManager.backupSingleBooking(booking, true);
    }
  }

  @Test
  public void testBackupSingleBookingCreationCorrectlyCallsSNS() throws Exception {

    doTestBackupSingleBookingCorrectlyCallsSNS(true);
  }

  @Test
  public void testBackupSingleBookingDeletionCorrectlyCallsSNS() throws Exception {

    doTestBackupSingleBookingCorrectlyCallsSNS(false);
  }

  public void doTestBackupSingleBookingCorrectlyCallsSNS(Boolean isCreation) throws Exception {

    // Set up mock booking manager
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockBookingManager);
      }
    });
    backupManager.initialise(mockBookingManager, mockLogger);

    // Not interested in S3 calls in this test
    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(returnValue(mockTransfer));
      }
    });
    backupManager.setS3TransferManager(mockTransferManager);

    // Set up expectation to publish to our SNS topic
    // Encode booking as JSON
    JsonNodeFactory factory = new JsonNodeFactory(false);
    // Create a json factory to write the treenode as json.
    JsonFactory jsonFactory = new JsonFactory();
    ByteArrayOutputStream backupStringStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(backupStringStream);
    try (JsonGenerator generator = jsonFactory.createGenerator(printStream)) {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode rootNode = factory.objectNode();
      rootNode.put("date", booking.getDate());
      rootNode.put("court", booking.getCourt());
      rootNode.put("courtSpan", booking.getCourtSpan());
      rootNode.put("slot", booking.getSlot());
      rootNode.put("slotSpan", booking.getSlotSpan());
      rootNode.put("players", booking.getPlayers());
      mapper.writeTree(generator, rootNode);
    }

    String backupString = (isCreation ? "Booking created: " : "Booking deleted: ")
        + System.getProperty("line.separator")
        + backupStringStream.toString(StandardCharsets.UTF_8.name());

    // Set up mock SNS client
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSNSClient).publish(with(equal(databaseBackupSnsTopicArn)),
            with(equal(backupString)), with(equal("Sqawsh single booking backup")));
      }
    });
    backupManager.setSNSClient(mockSNSClient);

    // ACT
    backupManager.backupSingleBooking(booking, isCreation);
  }

  @Test
  public void testBackupAllBookingCorrectlyCallsSNS() throws Exception {

    // Set up mock booking manager
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockBookingManager).getAllBookings();
        will(returnValue(bookings));
      }
    });
    backupManager.initialise(mockBookingManager, mockLogger);

    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(returnValue(mockTransfer));
      }
    });
    backupManager.setS3TransferManager(mockTransferManager);

    // Set up mock SNS client
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSNSClient).publish(with(equal(databaseBackupSnsTopicArn)),
            with(any(String.class)), with(equal("Sqawsh all-bookings backup")));
      }
    });
    backupManager.setSNSClient(mockSNSClient);

    // ACT
    backupManager.backupAllBookings();
  }

  @Test
  public void testBackupAllBookingsReturnsCorrectBookings() throws Exception {

    // Add a second booking to our test bookings list
    court = 7;
    slot = 11;
    playersNames = "A.Playera/B.Playerb";
    Booking booking2 = new Booking(court, slot, playersNames);
    booking2.setDate(date);
    bookings.add(booking2);

    // Set up mock booking manager
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockBookingManager).getAllBookings();
        will(returnValue(bookings));
      }
    });
    backupManager.initialise(mockBookingManager, mockLogger);

    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(returnValue(mockTransfer));
      }
    });
    backupManager.setS3TransferManager(mockTransferManager);

    // Set up mock SNS client
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockSNSClient).publish(with(equal(databaseBackupSnsTopicArn)),
            with(any(String.class)), with(equal("Sqawsh all-bookings backup")));
      }
    });
    backupManager.setSNSClient(mockSNSClient);

    // ACT
    List<Booking> actualBookings = backupManager.backupAllBookings();

    // ASSERT
    assertTrue(actualBookings.equals(bookings));
  }

  @Test
  public void testRestoreBookingsBookingCorrectlyCallsTheBookingManager_DeleteExistingBookings()
      throws Exception {

    // Add a second booking to our test bookings list
    court = 7;
    slot = 11;
    playersNames = "A.Playera/B.Playerb";
    Booking booking2 = new Booking(court, slot, playersNames);
    booking2.setDate(date);
    bookings.add(booking2);

    // Set up mock booking manager
    mockBookingManager = mockery.mock(IBookingManager.class);
    final Sequence restoreSequence = mockery.sequence("restore");
    mockery.checking(new Expectations() {
      {
        // Delete any existing bookings before restoring the new ones
        oneOf(mockBookingManager).deleteAllBookings();
        inSequence(restoreSequence);
        oneOf(mockBookingManager).createBooking(bookings.get(0));
        inSequence(restoreSequence);
        oneOf(mockBookingManager).createBooking(bookings.get(1));
        inSequence(restoreSequence);
      }
    });
    backupManager.initialise(mockBookingManager, mockLogger);

    // ACT
    backupManager.restoreBookings(bookings, true);
  }

  @Test
  public void testRestoreBookingsBookingCorrectlyCallsTheBookingManager_PreserveExistingBookings()
      throws Exception {

    // Add a second booking to our test bookings list
    court = 7;
    slot = 11;
    playersNames = "A.Playera/B.Playerb";
    Booking booking2 = new Booking(court, slot, playersNames);
    booking2.setDate(date);
    bookings.add(booking2);

    // Set up mock booking manager
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockery.checking(new Expectations() {
      {
        // Do not delete any existing bookings
        never(mockBookingManager).deleteAllBookings();
        oneOf(mockBookingManager).createBooking(bookings.get(0));
        oneOf(mockBookingManager).createBooking(bookings.get(1));
      }
    });
    backupManager.initialise(mockBookingManager, mockLogger);

    // ACT
    backupManager.restoreBookings(bookings, false);
  }
}