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

package squash.booking.lambdas.core;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import squash.booking.lambdas.core.BackupManager;
import squash.booking.lambdas.core.Booking;
import squash.booking.lambdas.core.BookingRule;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.IRuleManager;
import squash.deployment.lambdas.utils.IS3TransferManager;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.sns.AmazonSNS;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link BackupManager}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BackupManagerTest {
  // Variables for setting up subclass of class under test
  squash.booking.lambdas.core.BackupManagerTest.TestBackupManager backupManager;

  String databaseBackupSnsTopicArn;
  String databaseBackupBucketName;

  // Mocks
  Mockery mockery = new Mockery();
  Context mockContext;
  LambdaLogger mockLogger;
  IS3TransferManager mockTransferManager;
  IBookingManager mockBookingManager;
  IRuleManager mockRuleManager;
  AmazonSNS mockSNSClient;

  Integer court;
  Integer courtSpan;
  Integer slot;
  Integer slotSpan;
  String playersNames;
  Booking booking;
  String date = "2016-01-12";
  List<Booking> bookings;

  Boolean isRecurring;
  String excludeDate1 = "2016-02-11";
  String excludeDate2 = "2016-04-10";
  List<String> datesToExclude;
  BookingRule bookingRule;
  List<BookingRule> bookingRules;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() throws Exception {

    backupManager = new squash.booking.lambdas.core.BackupManagerTest.TestBackupManager();

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

    // Set up a booking rule
    isRecurring = true;
    datesToExclude = new ArrayList<>();
    datesToExclude.add(excludeDate1);
    datesToExclude.add(excludeDate2);
    bookingRule = new BookingRule(booking, isRecurring,
        datesToExclude.toArray(new String[datesToExclude.size()]));
    bookingRules = new ArrayList<>();
    bookingRules.add(bookingRule);
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
  public void testBackupSingleBookingCreationThrowsWhenBackupManagerUninitialised()
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The backup manager has not been initialised");

    // ACT
    // Do not initialise the backup manager first - so backup should throw
    backupManager.backupSingleBooking(booking, true);
  }

  @Test
  public void testBackupSingleBookingCreationCorrectlyCallsS3() throws Exception {

    doTestBackupBookingsAndBookingRulesCorrectlyCallsS3(false, true, true);
  }

  @Test
  public void testBackupSingleBookingRuleMutationThrowsWhenBackupManagerUninitialised()
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The backup manager has not been initialised");

    // ACT
    // Do not initialise the backup manager first - so backup should throw
    backupManager.backupSingleBookingRule(bookingRule, true);
  }

  @Test
  public void testBackupSingleBookingRuleMutationCorrectlyCallsS3() throws Exception {

    doTestBackupBookingsAndBookingRulesCorrectlyCallsS3(false, false, true);
  }

  @Test
  public void testBackupSingleBookingDeletionCorrectlyCallsS3() throws Exception {

    doTestBackupBookingsAndBookingRulesCorrectlyCallsS3(false, true, false);
  }

  @Test
  public void testBackupSingleBookingDeletionThrowsWhenBackupManagerUninitialised()
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The backup manager has not been initialised");

    // ACT
    // Do not initialise the backup manager first - so backup should throw
    backupManager.backupSingleBookingRule(bookingRule, false);
  }

  @Test
  public void testBackupSingleBookingRuleDeletionCorrectlyCallsS3() throws Exception {

    doTestBackupBookingsAndBookingRulesCorrectlyCallsS3(false, false, false);
  }

  @Test
  public void testBackupAllBookingsAndBookingRulesCorrectlyCallsS3() throws Exception {

    doTestBackupBookingsAndBookingRulesCorrectlyCallsS3(true, false, true);
  }

  @Test
  public void testBackupAllBookingsAndBookingRuleThrowsWhenBackupManagerUninitialised()
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The backup manager has not been initialised");

    // ACT
    // Do not initialise the backup manager first - so backup should throw
    backupManager.backupAllBookingsAndBookingRules();
  }

  public void doTestBackupBookingsAndBookingRulesCorrectlyCallsS3(
      Boolean backupAllBookingsAndBookingRules, Boolean backupSingleBookingRule,
      Boolean isNotDeletion) throws Exception {

    // Prevent this method being called with invalid combination of parameters
    assertTrue("Either backup all bookingsAndRules or backup a single booking/rule",
        !(backupAllBookingsAndBookingRules && backupSingleBookingRule));

    // Set up mock transfer manager
    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);

    // Set up mock booking and rule managers
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockRuleManager = mockery.mock(IRuleManager.class);
    mockery.checking(new Expectations() {
      {
        if (backupAllBookingsAndBookingRules) {
          // When backing everything up, we call through to the managers:
          oneOf(mockBookingManager).getAllBookings();
          will(returnValue(bookings));
          oneOf(mockRuleManager).getRules();
          will(returnValue(bookingRules));

          // Each type of backup uploads to a different S3 key:
          oneOf(mockTransferManager).upload(
              with(allOf(any(PutObjectRequest.class),
                  hasProperty("key", equal("AllBookingsAndBookingRules")))));
          will(returnValue(mockTransfer));
        } else {
          ignoring(mockBookingManager);
          ignoring(mockRuleManager);

          if (backupSingleBookingRule) {
            oneOf(mockTransferManager).upload(
                with(allOf(any(PutObjectRequest.class),
                    hasProperty("key", equal("LatestBookingRule")))));
            will(returnValue(mockTransfer));
          } else {
            oneOf(mockTransferManager)
                .upload(
                    with(allOf(any(PutObjectRequest.class),
                        hasProperty("key", equal("LatestBooking")))));
            will(returnValue(mockTransfer));
          }
        }
      }
    });
    backupManager.initialise(mockBookingManager, mockRuleManager, mockLogger);
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
    if (backupAllBookingsAndBookingRules) {
      backupManager.backupAllBookingsAndBookingRules();
    } else {
      if (backupSingleBookingRule) {
        backupManager.backupSingleBookingRule(bookingRule, isNotDeletion);
      } else {
        backupManager.backupSingleBooking(booking, isNotDeletion);
      }
    }
  }

  @Test
  public void testBackupSingleBookingCreationCorrectlyCallsSNS() throws Exception {

    doTestBackupSingleBookingOrRuleCorrectlyCallsSNS(true, true);
  }

  @Test
  public void testBackupSingleBookingRuleCreationCorrectlyCallsSNS() throws Exception {

    doTestBackupSingleBookingOrRuleCorrectlyCallsSNS(false, true);
  }

  @Test
  public void testBackupSingleBookingDeletionCorrectlyCallsSNS() throws Exception {

    doTestBackupSingleBookingOrRuleCorrectlyCallsSNS(true, false);
  }

  @Test
  public void testBackupSingleBookingRuleDeletionCorrectlyCallsSNS() throws Exception {

    doTestBackupSingleBookingOrRuleCorrectlyCallsSNS(false, false);
  }

  public void doTestBackupSingleBookingOrRuleCorrectlyCallsSNS(Boolean isBooking, Boolean isCreation)
      throws Exception {

    // Not interested in manager calls in this test
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockRuleManager = mockery.mock(IRuleManager.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockBookingManager);
        ignoring(mockRuleManager);
      }
    });
    backupManager.initialise(mockBookingManager, mockRuleManager, mockLogger);

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

    // Set up expectation to publish to our SNS topic with expected message and
    // subject - both of which differ between bookings and booking rules.
    String backupMessage;
    String backupSubject;
    if (!isBooking) {
      // Encode booking rule as JSON
      backupMessage = (isCreation ? "Booking rule updated: " : "Booking rule deleted: ")
          + System.getProperty("line.separator") + getExpectedBookingRuleJson(bookingRule);
      backupSubject = "Sqawsh single booking rule backup";
    } else {
      // Encode booking as JSON
      backupMessage = (isCreation ? "Booking created: " : "Booking deleted: ")
          + System.getProperty("line.separator") + getExpectedBookingJson(booking);
      backupSubject = "Sqawsh single booking backup";
    }

    // Set up mock SNS client
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSNSClient).publish(with(equal(databaseBackupSnsTopicArn)),
            with(equal(backupMessage)), with(equal(backupSubject)));
      }
    });
    backupManager.setSNSClient(mockSNSClient);

    // ACT
    if (isBooking) {
      backupManager.backupSingleBooking(booking, isCreation);
    } else {
      backupManager.backupSingleBookingRule(bookingRule, isCreation);
    }
  }

  private String getExpectedBookingRuleJson(BookingRule bookingRule) {
    return "{\"booking\":" + getExpectedBookingJson(bookingRule.getBooking()) + ",\"isRecurring\":"
        + bookingRule.getIsRecurring() + ",\"datesToExclude\":[\""
        + bookingRule.getDatesToExclude()[0] + "\",\"" + bookingRule.getDatesToExclude()[1]
        + "\"]}";
  }

  private String getExpectedBookingJson(Booking booking) {
    return "{\"date\":\"" + booking.getDate() + "\",\"court\":" + booking.getCourt()
        + ",\"courtSpan\":" + booking.getCourtSpan() + ",\"slot\":" + booking.getSlot()
        + ",\"slotSpan\":" + booking.getSlotSpan() + ",\"players\":\"" + booking.getPlayers()
        + "\"}";
  }

  @Test
  public void testBackupAllBookingsAndBookingRulesCorrectlyCallsSNS() throws Exception {

    // Add extra booking and booking rule to verify ALL bookings and rules are
    // backed up.
    Booking booking2 = new Booking(booking);
    // Tweak booking2 so it's different to booking
    booking2.setCourt(booking2.getCourt() + 1);
    bookings.add(booking2);
    BookingRule bookingRule2 = new BookingRule(bookingRule);
    // Tweak bookingRule2 so it's different to bookingRule
    bookingRule2.setIsRecurring(!bookingRule2.getIsRecurring());
    bookingRules.add(bookingRule2);

    // Set up mock managers
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockRuleManager = mockery.mock(IRuleManager.class);
    mockery.checking(new Expectations() {
      {
        // When backing everything up, we call through to the managers:
        allowing(mockBookingManager).getAllBookings();
        will(returnValue(bookings));
        allowing(mockRuleManager).getRules();
        will(returnValue(bookingRules));
      }
    });
    backupManager.initialise(mockBookingManager, mockRuleManager, mockLogger);

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

    // Set up mock SNS client
    String backupMessage = "{\"bookings\":[" + getExpectedBookingJson(booking) + ","
        + getExpectedBookingJson(booking2) + "],\"bookingRules\":["
        + getExpectedBookingRuleJson(bookingRule) + "," + getExpectedBookingRuleJson(bookingRule2)
        + "],\"clearBeforeRestore\":true}";
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSNSClient)
            .publish(with(equal(databaseBackupSnsTopicArn)), with(equal(backupMessage)),
                with(equal("Sqawsh all-bookings and booking rules backup")));
      }
    });
    backupManager.setSNSClient(mockSNSClient);

    // ACT
    backupManager.backupAllBookingsAndBookingRules();
  }

  @Test
  public void testBackupAllBookingsAndBookingRulesReturnsCorrectBookingsAndBookingRules()
      throws Exception {

    // Add extra booking and booking rule to verify ALL bookings and rules are
    // returned.
    Booking booking2 = new Booking(booking);
    // Tweak booking2 so it's different to booking
    booking2.setCourt(booking2.getCourt() + 1);
    bookings.add(booking2);
    BookingRule bookingRule2 = new BookingRule(bookingRule);
    // Tweak bookingRule2 so it's different to bookingRule
    bookingRule2.setIsRecurring(!bookingRule2.getIsRecurring());
    bookingRules.add(bookingRule2);

    // Set up mock managers
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockRuleManager = mockery.mock(IRuleManager.class);
    mockery.checking(new Expectations() {
      {
        // When backing everything up, we call through to the managers:
        allowing(mockBookingManager).getAllBookings();
        will(returnValue(bookings));
        allowing(mockRuleManager).getRules();
        will(returnValue(bookingRules));
      }
    });
    backupManager.initialise(mockBookingManager, mockRuleManager, mockLogger);

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

    // Not interested in SNS calls in this test
    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockSNSClient);
      }
    });
    backupManager.setSNSClient(mockSNSClient);

    // ACT
    ImmutablePair<List<Booking>, List<BookingRule>> bookingsAndBookingRules = backupManager
        .backupAllBookingsAndBookingRules();

    // ASSERT
    // Verify we've got the correct bookings and booking rules.
    assertEquals("Unexpected bookings returned", bookingsAndBookingRules.left, bookings);
    assertEquals("Unexpected booking rules returned", bookingsAndBookingRules.right, bookingRules);
  }

  @Test
  public void testRestoreAllBookingsAndBookingRulesThrowsWhenBackupManagerUninitialised()
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The backup manager has not been initialised");

    // ACT
    // Do not initialise the backup manager first - so backup should throw
    backupManager.restoreAllBookingsAndBookingRules(bookings, bookingRules, true);
  }

  @Test
  public void testRestoreAllBookingsAndBookingRulesCorrectlyCallsTheManagers_DeleteExistingBookings()
      throws Exception {

    // Add extra booking and booking rule to verify ALL bookings and rules are
    // restored.
    Booking booking2 = new Booking(booking);
    // Tweak booking2 so it's different to booking
    booking2.setCourt(booking2.getCourt() + 1);
    bookings.add(booking2);
    BookingRule bookingRule2 = new BookingRule(bookingRule);
    // Tweak bookingRule2 so it's different to bookingRule
    bookingRule2.setIsRecurring(!bookingRule2.getIsRecurring());
    bookingRules.add(bookingRule2);

    // Set up mock managers
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockRuleManager = mockery.mock(IRuleManager.class);
    final Sequence restoreSequence = mockery.sequence("restore");
    mockery.checking(new Expectations() {
      {
        // Delete any existing bookings and booking rules before restoring.
        oneOf(mockBookingManager).deleteAllBookings();
        oneOf(mockRuleManager).deleteAllBookingRules();
        inSequence(restoreSequence);
        // Restore everything
        oneOf(mockBookingManager).createBooking(bookings.get(0));
        oneOf(mockBookingManager).createBooking(bookings.get(1));
        inSequence(restoreSequence);
        oneOf(mockRuleManager).createRule(bookingRules.get(0));
        oneOf(mockRuleManager).createRule(bookingRules.get(1));
        inSequence(restoreSequence);
      }
    });
    backupManager.initialise(mockBookingManager, mockRuleManager, mockLogger);

    // ACT
    backupManager.restoreAllBookingsAndBookingRules(bookings, bookingRules, true);
  }

  @Test
  public void testRestoreAllBookingsAndBookingRulesCorrectlyCallsTheManagers_PreserveExistingBookings()
      throws Exception {

    // Add extra booking and booking rule to verify ALL bookings and rules are
    // restored.
    Booking booking2 = new Booking(booking);
    // Tweak booking2 so it's different to booking
    booking2.setCourt(booking2.getCourt() + 1);
    bookings.add(booking2);
    BookingRule bookingRule2 = new BookingRule(bookingRule);
    // Tweak bookingRule2 so it's different to bookingRule
    bookingRule2.setIsRecurring(!bookingRule2.getIsRecurring());
    bookingRules.add(bookingRule2);

    // Set up mock managers
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockRuleManager = mockery.mock(IRuleManager.class);
    mockery.checking(new Expectations() {
      {
        // Do not delete any existing bookings
        never(mockBookingManager).deleteAllBookings();
        never(mockRuleManager).deleteAllBookingRules();
        // Restore everything
        oneOf(mockBookingManager).createBooking(bookings.get(0));
        oneOf(mockBookingManager).createBooking(bookings.get(1));
        oneOf(mockRuleManager).createRule(bookingRules.get(0));
        oneOf(mockRuleManager).createRule(bookingRules.get(1));
      }
    });
    backupManager.initialise(mockBookingManager, mockRuleManager, mockLogger);

    // ACT
    backupManager.restoreAllBookingsAndBookingRules(bookings, bookingRules, false);
  }
}