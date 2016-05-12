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
import com.amazonaws.util.json.JSONException;
import com.google.common.io.CharStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
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
  squash.booking.lambdas.utils.PageManagerTest.TestPageManager pageManager;

  String apiGatewayBaseUrl;
  String websiteBucketName;

  // Mocks
  Mockery mockery = new Mockery();
  Context mockContext;
  LambdaLogger mockLogger;
  IS3TransferManager mockTransferManager;
  IBookingManager mockBookingManager;
  AmazonS3 mockS3Client;

  Integer court;
  Integer slot;
  String playersNames;
  Booking booking;
  List<Booking> bookings;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() throws Exception {
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    fakeCurrentDateString = fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    validDates = new ArrayList<>();
    validDates.add(fakeCurrentDateString);
    validDates.add(fakeCurrentDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

    pageManager = new squash.booking.lambdas.utils.PageManagerTest.TestPageManager();
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
    slot = 12;
    playersNames = "D.Playerd/F.Playerf";
    booking = new Booking(court, slot, playersNames);
    bookings = new ArrayList<Booking>();
    bookings.add(booking);

    apiGatewayBaseUrl = "apiGatewayBaseUrl";

    pageManager.Initialise(mockBookingManager, mockLogger);
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test page manager with some overrides to facilitate testing
  public class TestPageManager extends PageManager {
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

    @Override
    public String getStringProperty(String propertyName) {
      if (propertyName.equals("s3websitebucketname")) {
        return websiteBucket;
      }
      if (propertyName.equals("region")) {
        return "eu-west-1";
      } else {
        return null;
      }
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
  public void testRefreshPageWithDuplicateCorrectlyCallsS3() throws Exception {

    // Refresh page when Cloudformation creates the stack should just
    // upload pages to S3 but not duplicate them. Duplicating is done as
    // bookings are later mutated - and is a workaround to ensure
    // ReadAfterWrite consistency. This tests that this duplication
    // does happen when we ask for it.

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
    pageManager.refreshPage(fakeCurrentDateString, validDates, apiGatewayBaseUrl, true, bookings);
  }

  @Test
  public void testRefreshPageWithoutDuplicateCorrectlyCallsS3() throws Exception {

    // Refresh page when Cloudformation creates the stack should just
    // upload pages to S3 but not duplicate them. Duplicating is done as
    // bookings are later mutated - and is a workaround to ensure
    // ReadAfterWrite consistency. This tests that this duplication
    // does not happen when we do not ask for it (i.e. on stack creation).

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
    pageManager.refreshPage(fakeCurrentDateString, validDates, apiGatewayBaseUrl, false, bookings);
  }

  @Test
  public void testRefreshPageThrowsWhenS3Throws() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Exception caught while copying booking page to S3");

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
    pageManager.refreshPage(fakeCurrentDateString, validDates, apiGatewayBaseUrl, false, bookings);
  }

  @Test
  public void testRefreshAllPagesCorrectlyCallsS3() throws Exception {

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
        // 2 uploads for each date + 1 upload for the index page + 1 upload for
        // the validdates json
        exactly(2 * validDates.size() + 2).of(mockTransferManager).upload(
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
    pageManager.refreshAllPages(validDates, apiGatewayBaseUrl);
  }

  @Test
  public void testRefreshAllPagesThrowsWhenS3Throws() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Exception caught while copying booking page to S3");

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
    pageManager.refreshAllPages(validDates, apiGatewayBaseUrl);
  }

  @Test
  public void testCreateIndexPageReturnsCorrectPage() throws UnsupportedEncodingException,
      IOException {

    // We verify against a previously-saved regression file.

    // ARRANGE
    String redirectionUrl = "http://squashwebsite42.s3-website-eu-west-1.amazonaws.com";
    // Load in the expected page
    String expectedIndexPage;
    try (InputStream stream = PageManagerTest.class
        .getResourceAsStream("/squash/booking/lambdas/TestCreateIndexPageReturnsCorrectPage.html")) {
      expectedIndexPage = CharStreams.toString(new InputStreamReader(stream, "UTF-8"));
    }

    // ACT
    String actualIndexPage = pageManager.createIndexPage(redirectionUrl);

    // ASSERT
    boolean pageIsCorrect = actualIndexPage.equals(expectedIndexPage);
    if (!pageIsCorrect) {
      // Save the generated page only in the error case
      // Get path to resource so can save attempt alongside it
      URL regressionPage = PageManagerTest.class
          .getResource("/squash/booking/lambdas/TestCreateIndexPageReturnsCorrectPage.html");
      String regressionPagePath = regressionPage.getPath();
      String pathMinusFilename = FilenameUtils.getPath(regressionPagePath);
      File outputPage = new File("/" + pathMinusFilename, "IndexPageFailingTestResult.html");
      try (PrintStream out = new PrintStream(new FileOutputStream(outputPage))) {
        out.print(actualIndexPage);
      }
    }
    assertTrue("Created index page is incorrect: " + actualIndexPage, pageIsCorrect);
  }

  @Test
  public void testCreateBookingPageReturnsCorrectPage() throws UnsupportedEncodingException,
      IOException {

    // We create two bookings, and verify resulting html directly
    // against a previously-saved regression file.

    // ARRANGE
    // Set some values that will get embedded into the booking page
    String s3WebsiteUrl = "http://squashwebsite.s3-website-eu-west-1.amazonaws.com";
    String reservationFormGetUrl = "reserveUrl";
    String cancellationFormGetUrl = "cancelUrl";
    // Set up 2 bookings
    Booking booking1 = new Booking();
    booking1.setSlot(3);
    booking1.setCourt(5);
    booking1.setPlayers("A.Playera/B.Playerb");
    Booking booking2 = new Booking();
    booking2.setSlot(4);
    booking2.setCourt(3);
    booking2.setPlayers("C.Playerc/D.Playerd");
    List<Booking> bookingsForPage = new ArrayList<>();
    bookingsForPage.add(booking1);
    bookingsForPage.add(booking2);

    // Load in the expected page
    String expectedBookingPage;
    try (InputStream stream = PageManagerTest.class
        .getResourceAsStream("/squash/booking/lambdas/TestCreateBookingPageReturnsCorrectPage.html")) {
      expectedBookingPage = CharStreams.toString(new InputStreamReader(stream, "UTF-8"));
    }

    // ACT
    String actualBookingPage = pageManager.createBookingPage(fakeCurrentDateString, validDates,
        reservationFormGetUrl, cancellationFormGetUrl, s3WebsiteUrl, bookingsForPage, "DummyGuid");

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
  public void testCreateCachedBookingDataCreatesCorrectData() throws IOException,
      IllegalArgumentException, JSONException {

    // We create two bookings, and verify resulting json directly
    // against regression data.

    // ARRANGE
    // Set up 2 bookings
    Booking booking1 = new Booking();
    booking1.setSlot(3);
    booking1.setCourt(5);
    booking1.setPlayers("A.Playera/B.Playerb");
    Booking booking2 = new Booking();
    booking2.setSlot(4);
    booking2.setCourt(3);
    booking2.setPlayers("C.Playerc/D.Playerd");
    List<Booking> bookingsForDate = new ArrayList<>();
    bookingsForDate.add(booking1);
    bookingsForDate.add(booking2);

    // Set up the expected cached data
    String expectedCachedBookingData = "{\"date\":\"2015-10-06\",\"validdates\":[\"2015-10-06\",\"2015-10-07\"],\"bookings\":[{\"players\":\"A.Playera/B.Playerb\",\"slot\":3,\"court\":5},{\"players\":\"C.Playerc/D.Playerd\",\"slot\":4,\"court\":3}]}";

    // ACT
    String actualCachedBookingData = pageManager.createCachedBookingData(fakeCurrentDateString,
        validDates, bookingsForDate);

    // ASSERT
    boolean dataIsCorrect = actualCachedBookingData.equals(expectedCachedBookingData);
    assertTrue("Created cached booking data is incorrect: " + actualCachedBookingData + " versus "
        + expectedCachedBookingData, dataIsCorrect);
  }

  @Test
  public void testCreateCachedBookingDataHasBookingsArrayWhenThereIsOneBooking()
      throws IOException, IllegalArgumentException, JSONException {

    // Aim here is to check that a single booking is encoded as a 1-element
    // array rather than degenerating to a non-array object.

    // ARRANGE
    // We create 1 bookings, and verify resulting json directly
    // against regression data.
    Booking booking = new Booking();
    booking.setSlot(3);
    booking.setCourt(5);
    booking.setPlayers("A.Playera/B.Playerb");
    List<Booking> bookingsForDate = new ArrayList<>();
    bookingsForDate.add(booking);

    // Set up the expected cached data
    String expectedCachedBookingData = "{\"date\":\"2015-10-06\",\"validdates\":[\"2015-10-06\",\"2015-10-07\"],\"bookings\":[{\"players\":\"A.Playera/B.Playerb\",\"slot\":3,\"court\":5}]}";

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
      throws IOException, IllegalArgumentException, JSONException {

    // Aim here is to check that no bookings is encoded as an empty array
    // rather than being dropped altogether from the JSON.

    // We create no bookings, and verify resulting json directly
    // against regression data.

    // ARRANGE
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
  public void testCreateCachedValidDatesDataCreatesCorrectData() throws IOException,
      IllegalArgumentException, JSONException {

    // ARRANGE
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
  public void testCreateCachedValidDatesDataWhenThereIsOneValidDate() throws IOException,
      IllegalArgumentException, JSONException {

    // Aim here is to check that a single date is encoded as a 1-element
    // array rather than degenerating to a non-array object.

    // ARRANGE
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
  public void testCreateCachedValidDatesDataWhenThereAreNoValidDates() throws IOException,
      IllegalArgumentException, JSONException {

    // Aim here is to check that no valid dates is encoded as an empty array
    // rather than being dropped altogether from the JSON.

    // ARRANGE
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