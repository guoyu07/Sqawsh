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

import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertTrue;

import squash.deployment.lambdas.utils.IS3TransferManager;

import org.apache.commons.io.FilenameUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.sns.AmazonSNS;
import com.google.common.io.CharStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link PageManager}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PageManagerTest {
  // Variables for setting up subclass of class under test
  LocalDate fakeCurrentDate;
  String fakeCurrentDateString;
  List<String> validDates;
  squash.booking.lambdas.core.PageManagerTest.TestPageManager pageManager;

  String adminSnsTopicArn;

  String apiGatewayBaseUrl;
  String websiteBucketName;

  // Mocks
  Mockery mockery = new Mockery();
  Context mockContext;
  LambdaLogger mockLogger;
  IS3TransferManager mockTransferManager;
  IBookingManager mockBookingManager;
  AmazonS3 mockS3Client;
  AmazonSNS mockSNSClient;

  Integer court;
  Integer courtSpan;
  Integer slot;
  Integer slotSpan;
  String name;
  Booking booking;
  List<Booking> bookings;

  String revvingSuffix;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() throws Exception {
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    fakeCurrentDateString = fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    validDates = new ArrayList<>();
    validDates.add(fakeCurrentDateString);
    validDates.add(fakeCurrentDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

    pageManager = new squash.booking.lambdas.core.PageManagerTest.TestPageManager();
    pageManager.setCurrentLocalDate(fakeCurrentDate);

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
    // Set up mock booking manager
    mockBookingManager = mockery.mock(IBookingManager.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockBookingManager);
      }
    });

    websiteBucketName = "websiteBucketName";
    pageManager.setS3WebsiteBucketName(websiteBucketName);

    // Use a single booking for most of the tests
    court = 5;
    courtSpan = 1;
    slot = 12;
    slotSpan = 1;
    name = "D.Playerd/F.Playerf";
    booking = new Booking(court, courtSpan, slot, slotSpan, name);
    bookings = new ArrayList<>();
    bookings.add(booking);

    revvingSuffix = "revvingSuffix";

    apiGatewayBaseUrl = "apiGatewayBaseUrl";
    adminSnsTopicArn = "adminSnsTopicArn";
    pageManager.setAdminSnsTopicArn(adminSnsTopicArn);
  }

  private void initialisePageManager() throws Exception {
    // Call this to initialise the page manager in tests where this
    // initialisation is not the subject of the test.

    pageManager.initialise(mockBookingManager, mockLogger);
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test page manager with some overrides to facilitate testing
  public class TestPageManager extends PageManager {
    private AmazonSNS snsClient;
    private String adminSnsTopicArn;
    private LocalDate currentLocalDate;
    private String websiteBucket;
    private IS3TransferManager transferManager;

    public void setS3TransferManager(IS3TransferManager transferManager) {
      this.transferManager = transferManager;
    }

    @Override
    public IS3TransferManager getS3TransferManager() {
      return transferManager;
    }

    public void setS3WebsiteBucketName(String websiteBucketName) {
      websiteBucket = websiteBucketName;
    }

    public void setSNSClient(AmazonSNS snsClient) {
      this.snsClient = snsClient;
    }

    @Override
    public AmazonSNS getSNSClient() {
      return snsClient;
    }

    public void setAdminSnsTopicArn(String adminSnsTopicArn) {
      this.adminSnsTopicArn = adminSnsTopicArn;
    }

    @Override
    public String getStringProperty(String propertyName) {
      if (propertyName.equals("adminsnstopicarn")) {
        return adminSnsTopicArn;
      }
      if (propertyName.equals("s3websitebucketname")) {
        return websiteBucket;
      }
      if (propertyName.equals("region")) {
        return "eu-west-1";
      }
      return null;
    }

    public void setCurrentLocalDate(LocalDate localDate) {
      currentLocalDate = localDate;
    }

    @Override
    public LocalDate getCurrentLocalDate() {
      return currentLocalDate;
    }
  }

  @Test
  public void testRefreshPageThrowsWhenPageManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The page manager has not been initialised");

    // ACT
    // Do not initialise the page manager first - so we should throw
    pageManager.refreshPage(fakeCurrentDateString, validDates, apiGatewayBaseUrl, true, bookings,
        revvingSuffix);
  }

  @Test
  public void testUploadFamousPlayersThrowsWhenPageManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The page manager has not been initialised");

    // ACT
    // Do not initialise the page manager first - so we should throw
    pageManager.uploadFamousPlayers();
  }

  @Test
  public void testRefreshPageWithDuplicateCorrectlyCallsS3() throws Exception {

    // Refresh page when Cloudformation creates the stack should just
    // upload pages to S3 but not duplicate them. Duplicating is done as
    // bookings are later mutated - and is a workaround to ensure
    // ReadAfterWrite consistency. This tests that this duplication
    // does happen when we ask for it.

    initialisePageManager();

    // Set up S3 expectations for copy:
    // Transfer interface is implemented by Uploads, Downloads, and Copies
    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);
    // Just check S3 methods called correct number of times - don't bother
    // checking argument details.
    mockery.checking(new Expectations() {
      {
        // We have one upload for the page and one for the cached data
        exactly(2).of(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(returnValue(mockTransfer));
        // We _do_ have the copy in this case - for the page, but not for the
        // cached data
        oneOf(mockTransferManager).copy(with(any(CopyObjectRequest.class)));
        will(returnValue(mockTransfer));
      }
    });
    pageManager.setS3TransferManager(mockTransferManager);

    // ACT
    pageManager.refreshPage(fakeCurrentDateString, validDates, apiGatewayBaseUrl, true, bookings,
        revvingSuffix);
  }

  @Test
  public void testRefreshPageWithoutDuplicateCorrectlyCallsS3() throws Exception {

    // Refresh page when Cloudformation creates the stack should just
    // upload pages to S3 but not duplicate them. Duplicating is done as
    // bookings are later mutated - and is a workaround to ensure
    // ReadAfterWrite consistency. This tests that this duplication
    // does not happen when we do not ask for it (i.e. on stack creation).

    initialisePageManager();

    // Set up S3 expectations for no copy:
    // Transfer interface is implemented by Uploads, Downloads, and Copies
    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);
    // Just check S3 methods called correct number of times - don't bother
    // checking argument details.
    mockery.checking(new Expectations() {
      {
        // We have one upload for the page and one for the cached data
        exactly(2).of(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(returnValue(mockTransfer));
        // We do _not_ have the copy in this case
        never(mockTransferManager).copy(with(anything()));
      }
    });
    pageManager.setS3TransferManager(mockTransferManager);

    // ACT
    pageManager.refreshPage(fakeCurrentDateString, validDates, apiGatewayBaseUrl, false, bookings,
        revvingSuffix);
  }

  @Test
  public void testRefreshPageThrowsWhenS3Throws() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Exception caught while copying booking page to S3");

    initialisePageManager();

    // Make S3 throw:
    // Transfer interface is implemented by Uploads, Downloads, and Copies
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
        oneOf(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(throwException(new AmazonServiceException("Grrr...")));
        // Should throw before copy is called
        never(mockTransferManager).copy(with(any(CopyObjectRequest.class)));
      }
    });
    pageManager.setS3TransferManager(mockTransferManager);

    // ACT - this should throw
    pageManager.refreshPage(fakeCurrentDateString, validDates, apiGatewayBaseUrl, false, bookings,
        revvingSuffix);
  }

  @Test
  public void testRefreshAllPagesCorrectlyCallsS3() throws Exception {

    initialisePageManager();

    // Set up S3 expectations for upload (without copy) for each valid date:
    // Transfer interface is implemented by Uploads, Downloads, and Copies
    Transfer mockTransfer = mockery.mock(Transfer.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockTransfer).isDone();
        will(returnValue(true));
        allowing(mockTransfer).waitForCompletion();
      }
    });
    mockTransferManager = mockery.mock(IS3TransferManager.class);
    // Just check S3 methods called correct number of times - don't bother
    // checking argument details.
    final Sequence refreshSequence = mockery.sequence("refresh");
    mockery.checking(new Expectations() {
      {
        // 2 uploads for each date + 2 uploads for the index pages + 1 upload
        // for the validdates json + 1 upload for the famous players json.
        exactly(2 * validDates.size() + 4).of(mockTransferManager).upload(
            with(any(PutObjectRequest.class)));
        will(returnValue(mockTransfer));
        inSequence(refreshSequence);

        // We do _not_ have the copy in this case
        never(mockTransferManager).copy(with(anything()));
      }
    });
    // Delete previous day's bookings and cached data at end
    mockS3Client = mockery.mock(AmazonS3.class);
    mockery.checking(new Expectations() {
      {
        exactly(2).of(mockS3Client).deleteObject(with(aNonNull(DeleteObjectRequest.class)));
        // Ensures this delete occurs after uploads of new pages and cached data
        inSequence(refreshSequence);

        exactly(1).of(mockTransferManager).getAmazonS3Client();
        will(returnValue(mockS3Client));
      }
    });
    pageManager.setS3TransferManager(mockTransferManager);

    // ACT
    pageManager.refreshAllPages(validDates, apiGatewayBaseUrl, revvingSuffix);
  }

  @Test
  public void testRefreshAllPagesThrowsWhenPageManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The page manager has not been initialised");

    // ACT
    // Do not initialise the page manager first - so we should throw
    pageManager.refreshAllPages(validDates, apiGatewayBaseUrl, revvingSuffix);
  }

  @Test
  public void testRefreshAllPagesThrowsWhenS3Throws() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Exception caught while copying booking page to S3");

    initialisePageManager();

    // Make S3 throw:
    // Transfer interface is implemented by Uploads, Downloads, and Copies
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
        oneOf(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(throwException(new AmazonServiceException("Grrr...")));
        // Should throw before copy is called
        never(mockTransferManager).copy(with(any(CopyObjectRequest.class)));
      }
    });
    pageManager.setS3TransferManager(mockTransferManager);

    mockSNSClient = mockery.mock(AmazonSNS.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockSNSClient);
      }
    });
    pageManager.setSNSClient(mockSNSClient);

    // ACT - this should throw
    pageManager.refreshAllPages(validDates, apiGatewayBaseUrl, revvingSuffix);
  }

  @Test
  public void testRefreshAllPagesNotifiesTheSnsTopicWhenItThrows() throws Exception {
    // It is useful for the admin user to be notified whenever the refreshing
    // of booking pages does not succeed - so that they can update the pages
    // manually instead. This tests that whenever the page manager catches an
    // exception while refreshing pages, it notifies the admin SNS topic.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Exception caught while copying booking page to S3";
    thrown.expectMessage(message);

    initialisePageManager();

    // Make S3 throw:
    // Transfer interface is implemented by Uploads, Downloads, and Copies
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
        oneOf(mockTransferManager).upload(with(any(PutObjectRequest.class)));
        will(throwException(new AmazonServiceException("Grrr...")));
        // Should throw before copy is called
        never(mockTransferManager).copy(with(any(CopyObjectRequest.class)));
      }
    });
    pageManager.setS3TransferManager(mockTransferManager);

    // Set up mock SNS client to expect a notification
    mockSNSClient = mockery.mock(AmazonSNS.class);
    String partialMessage = "Apologies - but there was an error refreshing the booking pages in S3";
    mockery.checking(new Expectations() {
      {
        oneOf(mockSNSClient).publish(with(equal(adminSnsTopicArn)),
            with(startsWith(partialMessage)),
            with(equal("Sqawsh booking pages in S3 failed to refresh")));
      }
    });
    pageManager.setSNSClient(mockSNSClient);

    // ACT - this should throw - and notify the SNS topic
    pageManager.refreshAllPages(validDates, apiGatewayBaseUrl, revvingSuffix);
  }

  @Test
  public void testCreateTodayIndexPageReturnsCorrectPage() throws Exception {
    doTestCreateTodayIndexPageReturnsCorrectPage(true);
  }

  @Test
  public void testCreateNoscriptIndexPageReturnsCorrectPage() throws Exception {
    doTestCreateTodayIndexPageReturnsCorrectPage(false);
  }

  private void doTestCreateTodayIndexPageReturnsCorrectPage(Boolean showRedirectMessage)
      throws Exception {

    // We verify against a previously-saved regression file.

    // ARRANGE
    initialisePageManager();

    String redirectionUrl = "http://squashwebsite42.s3-website-eu-west-1.amazonaws.com";
    // Load in the expected page
    String expectedIndexPage;
    String indextype = showRedirectMessage ? "Today" : "Noscript";
    try (InputStream stream = PageManagerTest.class
        .getResourceAsStream("/squash/booking/lambdas/TestCreate" + indextype
            + "IndexPageReturnsCorrectPage.html")) {
      expectedIndexPage = CharStreams.toString(new InputStreamReader(stream, "UTF-8"));
    }

    // ACT
    String actualIndexPage = pageManager.createIndexPage(redirectionUrl, showRedirectMessage);

    // ASSERT
    boolean pageIsCorrect = actualIndexPage.equals(expectedIndexPage);
    if (!pageIsCorrect) {
      // Save the generated page only in the error case
      // Get path to resource so can save attempt alongside it
      URL regressionPage = PageManagerTest.class.getResource("/squash/booking/lambdas/TestCreate"
          + indextype + "IndexPageReturnsCorrectPage.html");
      String regressionPagePath = regressionPage.getPath();
      String pathMinusFilename = FilenameUtils.getPath(regressionPagePath);
      File outputPage = new File("/" + pathMinusFilename, indextype + "PageFailingTestResult.html");
      try (PrintStream out = new PrintStream(new FileOutputStream(outputPage))) {
        out.print(actualIndexPage);
      }
    }
    assertTrue("Created " + indextype + " index page is incorrect: Actual: " + actualIndexPage
        + " Expected: " + expectedIndexPage, pageIsCorrect);
  }

  @Test
  public void testCreateBookingPageReturnsCorrectPage() throws Exception {

    // We create two single bookings, and 2 block bookings, and verify resulting
    // html directly against a previously-saved regression file.

    // ARRANGE
    initialisePageManager();

    // Set some values that will get embedded into the booking page
    String s3WebsiteUrl = "http://squashwebsite.s3-website-eu-west-1.amazonaws.com";
    String reservationFormGetUrl = "reserveUrl";
    String cancellationFormGetUrl = "cancelUrl";
    // Set up 2 bookings
    Booking booking1 = new Booking();
    booking1.setSlot(3);
    booking1.setSlotSpan(1);
    booking1.setCourt(5);
    booking1.setCourtSpan(1);
    booking1.setName("A.Playera/B.Playerb");
    Booking booking2 = new Booking();
    booking2.setSlot(4);
    booking2.setSlotSpan(1);
    booking2.setCourt(3);
    booking2.setCourtSpan(1);
    booking2.setName("C.Playerc/D.Playerd");
    Booking booking3 = new Booking();
    booking3.setSlot(10);
    booking3.setSlotSpan(3);
    booking3.setCourt(2);
    booking3.setCourtSpan(2);
    booking3.setName("E.Playere/F.Playerf");
    Booking booking4 = new Booking();
    booking4.setSlot(13);
    booking4.setSlotSpan(4);
    booking4.setCourt(1);
    booking4.setCourtSpan(5);
    booking4.setName("Club Night");
    List<Booking> bookingsForPage = new ArrayList<>();
    bookingsForPage.add(booking1);
    bookingsForPage.add(booking2);
    bookingsForPage.add(booking3);
    bookingsForPage.add(booking4);

    // Load in the expected page
    String expectedBookingPage;
    try (InputStream stream = PageManagerTest.class
        .getResourceAsStream("/squash/booking/lambdas/TestCreateBookingPageReturnsCorrectPage.html")) {
      expectedBookingPage = CharStreams.toString(new InputStreamReader(stream, "UTF-8"));
    }

    // ACT
    String actualBookingPage = pageManager.createBookingPage(fakeCurrentDateString, validDates,
        reservationFormGetUrl, cancellationFormGetUrl, s3WebsiteUrl, bookingsForPage, "DummyGuid",
        revvingSuffix);

    // ASSERT
    boolean pageIsCorrect = actualBookingPage.equals(expectedBookingPage);
    if (!pageIsCorrect) {
      // Save the generated page only in the error case
      // Get path to resource so can save attempt alongside it
      URL regressionPage = PageManagerTest.class
          .getResource("/squash/booking/lambdas/TestCreateBookingPageReturnsCorrectPage.html");
      String regressionPagePath = regressionPage.getPath();
      String pathMinusFilename = FilenameUtils.getPath(regressionPagePath);
      File outputPage = new File("/" + pathMinusFilename, "BookingPageFailingTestResult.html");
      try (PrintStream out = new PrintStream(new FileOutputStream(outputPage))) {
        out.print(actualBookingPage);
      }
    }
    assertTrue("Created booking page is incorrect: " + actualBookingPage, pageIsCorrect);
  }

  @Test
  public void testCreateCachedBookingDataCreatesCorrectData() throws Exception {

    // We create two single bookings, and 1 block booking, and verify resulting
    // json directly against regression data.

    // ARRANGE
    initialisePageManager();

    // Set up 2 bookings
    Booking booking1 = new Booking();
    booking1.setSlot(3);
    booking1.setCourt(5);
    booking1.setName("A.Playera/B.Playerb");
    Booking booking2 = new Booking();
    booking2.setSlot(4);
    booking2.setCourt(3);
    booking2.setName("C.Playerc/D.Playerd");
    Booking booking3 = new Booking();
    booking3.setSlot(10);
    booking3.setSlotSpan(3);
    booking3.setCourt(2);
    booking3.setCourtSpan(2);
    booking3.setName("E.Playere/F.Playerf");
    List<Booking> bookingsForDate = new ArrayList<>();
    bookingsForDate.add(booking1);
    bookingsForDate.add(booking2);
    bookingsForDate.add(booking3);

    // Set up the expected cached data
    String expectedCachedBookingData = "{\"date\":\"2015-10-06\",\"validdates\":[\"2015-10-06\",\"2015-10-07\"],\"bookings\":[{\"court\":5,\"courtSpan\":1,\"slot\":3,\"slotSpan\":1,\"name\":\"A.Playera/B.Playerb\"},{\"court\":3,\"courtSpan\":1,\"slot\":4,\"slotSpan\":1,\"name\":\"C.Playerc/D.Playerd\"},{\"court\":2,\"courtSpan\":2,\"slot\":10,\"slotSpan\":3,\"name\":\"E.Playere/F.Playerf\"}]}";

    // ACT
    String actualCachedBookingData = pageManager.createCachedBookingData(fakeCurrentDateString,
        validDates, bookingsForDate);

    // ASSERT
    boolean dataIsCorrect = actualCachedBookingData.equals(expectedCachedBookingData);
    assertTrue("Created cached booking data is incorrect: " + actualCachedBookingData + " versus "
        + expectedCachedBookingData, dataIsCorrect);
  }

  @Test
  public void testCreateCachedBookingDataHasBookingsArrayWhenThereIsOneBooking() throws Exception {

    // Aim here is to check that a single booking is encoded as a 1-element
    // array rather than degenerating to a non-array object.

    // ARRANGE
    initialisePageManager();

    // We create 1 bookings, and verify resulting json directly
    // against regression data.
    Booking booking = new Booking();
    booking.setSlot(3);
    booking.setCourt(5);
    booking.setName("A.Playera/B.Playerb");
    List<Booking> bookingsForDate = new ArrayList<>();
    bookingsForDate.add(booking);

    // Set up the expected cached data
    String expectedCachedBookingData = "{\"date\":\"2015-10-06\",\"validdates\":[\"2015-10-06\",\"2015-10-07\"],\"bookings\":[{\"court\":5,\"courtSpan\":1,\"slot\":3,\"slotSpan\":1,\"name\":\"A.Playera/B.Playerb\"}]}";

    // ACT
    String actualCachedBookingData = pageManager.createCachedBookingData(fakeCurrentDateString,
        validDates, bookingsForDate);

    // ASSERT
    boolean dataIsCorrect = actualCachedBookingData.equals(expectedCachedBookingData);
    assertTrue("Created cached booking data is incorrect: " + actualCachedBookingData + " versus "
        + expectedCachedBookingData, dataIsCorrect);
  }

  @Test
  public void testCreateCachedBookingDataHasEmptyBookingsArrayWhenThereAreNoBookings()
      throws Exception {

    // Aim here is to check that no bookings is encoded as an empty array
    // rather than being dropped altogether from the JSON.

    // We create no bookings, and verify resulting json directly
    // against regression data.

    // ARRANGE
    initialisePageManager();

    // Create empty bookings array
    List<Booking> bookingsForDate = new ArrayList<>();

    // Set up the expected cached data
    String expectedCachedBookingData = "{\"date\":\"2015-10-06\",\"validdates\":[\"2015-10-06\",\"2015-10-07\"],\"bookings\":[]}";

    // ACT
    String actualCachedBookingData = pageManager.createCachedBookingData(fakeCurrentDateString,
        validDates, bookingsForDate);

    // ASSERT
    boolean dataIsCorrect = actualCachedBookingData.equals(expectedCachedBookingData);
    assertTrue("Created cached booking data is incorrect: " + actualCachedBookingData + " versus "
        + expectedCachedBookingData, dataIsCorrect);
  }

  @Test
  public void testCreateCachedValidDatesDataCreatesCorrectData() throws Exception {

    // ARRANGE
    initialisePageManager();

    // Set up the expected cached data
    String expectedCachedValidDatesData = "{\"dates\":[\"2015-10-06\",\"2015-10-07\"]}";

    // ACT
    String actualCachedValidDatesData = pageManager.createValidDatesData(validDates);

    // ASSERT
    boolean dataIsCorrect = actualCachedValidDatesData.equals(expectedCachedValidDatesData);
    assertTrue("Created cached valid dates data is incorrect: " + actualCachedValidDatesData
        + " versus " + expectedCachedValidDatesData, dataIsCorrect);
  }

  @Test
  public void testCreateCachedValidDatesDataWhenThereIsOneValidDate() throws Exception {

    // Aim here is to check that a single date is encoded as a 1-element
    // array rather than degenerating to a non-array object.

    // ARRANGE
    initialisePageManager();

    // We create a single valid date, and verify resulting json directly
    // against regression data.
    validDates = new ArrayList<>();
    validDates.add(fakeCurrentDateString);

    // Set up the expected cached data
    String expectedCachedValidDatesData = "{\"dates\":[\"2015-10-06\"]}";

    // ACT
    String actualCachedValidDatesData = pageManager.createValidDatesData(validDates);

    // ASSERT
    boolean dataIsCorrect = actualCachedValidDatesData.equals(expectedCachedValidDatesData);
    assertTrue("Created cached valid dates data is incorrect: " + actualCachedValidDatesData
        + " versus " + expectedCachedValidDatesData, dataIsCorrect);
  }

  @Test
  public void testCreateCachedValidDatesDataWhenThereAreNoValidDates() throws Exception {

    // Aim here is to check that no valid dates is encoded as an empty array
    // rather than being dropped altogether from the JSON.

    // ARRANGE
    initialisePageManager();

    // We create no valid dates, and verify resulting json directly
    // against regression data.
    validDates = new ArrayList<>();

    // Set up the expected cached data
    String expectedCachedValidDatesData = "{\"dates\":[]}";

    // ACT
    String actualCachedValidDatesData = pageManager.createValidDatesData(validDates);

    // ASSERT
    boolean dataIsCorrect = actualCachedValidDatesData.equals(expectedCachedValidDatesData);
    assertTrue("Created cached valid dates data is incorrect: " + actualCachedValidDatesData
        + " versus " + expectedCachedValidDatesData, dataIsCorrect);
  }
}