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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.States;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.sns.AmazonSNS;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tests the {@link BookingManager}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingManagerTest {
  // Variables for setting up subclass of class under test
  LocalDate fakeCurrentDate;
  String fakeCurrentDateString;
  squash.booking.lambdas.core.BookingManagerTest.TestBookingManager bookingManager;

  String adminSnsTopicArn;

  // Mocks
  Mockery mockery = new Mockery();
  final States database = mockery.states("database");
  Context mockContext;
  LambdaLogger mockLogger;
  IOptimisticPersister mockOptimisticPersister;
  AmazonSNS mockSNSClient;

  // Create some example bookings to test with
  Booking existingSingleBooking;
  Booking singleBookingOfFreeCourt;
  Booking singleBookingWithinExistingBlockBooking;
  Booking existingBlockBooking;
  Booking blockBookingOfFreeCourts;
  Booking blockBookingOverlappingExistingSingleBooking;
  Booking blockBookingOverlappingExistingBlockBooking;

  String existingName;
  String newName;

  // Bookings before and after we attempt to change them by calling a booking
  // manager method
  List<Booking> bookingsBeforeCall;
  List<Booking> expectedBookingsAfterCall;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() {
    // Set up the valid date range
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    fakeCurrentDateString = fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    bookingManager = new squash.booking.lambdas.core.BookingManagerTest.TestBookingManager();
    bookingManager.setCurrentLocalDate(fakeCurrentDate);

    // Set up the test bookings
    existingName = "A.Shabana/J.Power";
    newName = "J.Willstrop/N.Matthew";
    existingSingleBooking = new Booking(2, 1, 3, 1, existingName);
    existingSingleBooking.setDate(fakeCurrentDateString);
    blockBookingOverlappingExistingSingleBooking = new Booking(1, 3, 3, 2, newName);
    blockBookingOverlappingExistingSingleBooking.setDate(fakeCurrentDateString);
    blockBookingOverlappingExistingBlockBooking = new Booking(2, 2, 9, 2, newName);
    blockBookingOverlappingExistingBlockBooking.setDate(fakeCurrentDateString);
    singleBookingOfFreeCourt = new Booking(4, 1, 12, 1, newName);
    singleBookingOfFreeCourt.setDate(fakeCurrentDateString);
    existingBlockBooking = new Booking(3, 3, 10, 2, existingName);
    existingBlockBooking.setDate(fakeCurrentDateString);
    singleBookingWithinExistingBlockBooking = new Booking(4, 1, 11, 1, newName);
    singleBookingWithinExistingBlockBooking.setDate(fakeCurrentDateString);
    blockBookingOfFreeCourts = new Booking(1, 5, 13, 3, newName);
    blockBookingOfFreeCourts.setDate(fakeCurrentDateString);
    bookingsBeforeCall = new ArrayList<>();
    bookingsBeforeCall.add(existingSingleBooking);
    bookingsBeforeCall.add(existingBlockBooking);
    expectedBookingsAfterCall = new ArrayList<>();

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
    mockOptimisticPersister = mockery.mock(IOptimisticPersister.class);
    bookingManager.setOptimisticPersister(mockOptimisticPersister);
    adminSnsTopicArn = "adminSnsTopicArn";
    bookingManager.setAdminSnsTopicArn(adminSnsTopicArn);
  }

  private void initialiseBookingManager() throws Exception {
    // Call this to initialise the booking manager in tests where this
    // initialisation is not the subject of the test.

    bookingManager.initialise(mockLogger);
  }

  private void expectCreateBookingToReturnUpdatedBookingsOrThrow(List<Booking> initialBookings,
      Booking bookingToCreate, Optional<Exception> exceptionToThrow) throws Exception {
    expectCreateBookingToReturnUpdatedBookingsOrThrow(initialBookings, bookingToCreate,
        exceptionToThrow, 1);
  }

  private void expectCreateBookingToReturnUpdatedBookingsOrThrow(List<Booking> initialBookings,
      Booking bookingToCreate, Optional<Exception> exceptionToThrow, int numCalls) throws Exception {

    // The value we use here is arbitrary - what matters is that the call
    // to put uses the same value that is returned from get - or else we would
    // get a conditional check failed exception from SimpleDB.
    Optional<Integer> expectedVersionNumber = Optional.of(4);

    // Create booking attribute for the booking being created
    String attributeName = bookingToCreate.getCourt().toString() + "-"
        + bookingToCreate.getCourtSpan().toString() + "-" + bookingToCreate.getSlot() + "-"
        + bookingToCreate.getSlotSpan().toString();
    ReplaceableAttribute bookingAttribute = new ReplaceableAttribute();
    bookingAttribute.setName(attributeName);
    bookingAttribute.setValue(newName);

    // Booking creation gets existing bookings before trying to make the new one
    expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(expectedVersionNumber,
        initialBookings, Optional.empty(), numCalls);

    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          exactly(numCalls).of(mockOptimisticPersister).put(with(equal(bookingToCreate.getDate())),
              with(equal(expectedVersionNumber)), with(equal(bookingAttribute)));
        }
      });
    } else {
      mockery.checking(new Expectations() {
        {
          exactly(numCalls).of(mockOptimisticPersister).put(with(equal(bookingToCreate.getDate())),
              with(equal(expectedVersionNumber)), with(equal(bookingAttribute)));
          will(throwException(exceptionToThrow.get()));
        }
      });
    }

    bookingManager.setOptimisticPersister(mockOptimisticPersister);
  }

  private void expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(
      Optional<Integer> expectedVersionNumber, List<Booking> expectedBookings,
      Optional<Exception> exceptionToThrow) throws Exception {
    expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(expectedVersionNumber,
        expectedBookings, exceptionToThrow, 1);
  }

  private void expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(
      Optional<Integer> expectedVersionNumber, List<Booking> expectedBookings,
      Optional<Exception> exceptionToThrow, int numCalls) throws Exception {
    Set<Attribute> attributes = new HashSet<>();
    expectedBookings.stream().forEach(
        booking -> {
          attributes.add(new Attribute(booking.getCourt().toString() + "-"
              + booking.getCourtSpan().toString() + "-" + booking.getSlot().toString() + "-"
              + booking.getSlotSpan().toString(), booking.getName()));
        });

    // Set up mock optimistic persister to return these bookings - or to throw
    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          exactly(numCalls).of(mockOptimisticPersister).get(with(equal(fakeCurrentDateString)));
          will(returnValue(new ImmutablePair<>(expectedVersionNumber, attributes)));
        }
      });
    } else {
      mockery.checking(new Expectations() {
        {
          exactly(numCalls).of(mockOptimisticPersister).get(with(equal(fakeCurrentDateString)));
          will(throwException(exceptionToThrow.get()));
        }
      });
    }
    bookingManager.setOptimisticPersister(mockOptimisticPersister);
  }

  private void expectDeleteBookingToReturnUpdatedBookingsOrThrow(Booking bookingToDelete,
      Optional<List<Booking>> expectedBookingsAfterDelete, Optional<Exception> exceptionToThrow)
      throws Exception {

    String attributeName;
    attributeName = bookingToDelete.getCourt().toString() + "-"
        + bookingToDelete.getCourtSpan().toString() + "-" + bookingToDelete.getSlot().toString()
        + "-" + bookingToDelete.getSlotSpan().toString();

    Attribute attribute = new Attribute();
    attribute.setName(attributeName);
    attribute.setValue(bookingToDelete.getName());

    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          oneOf(mockOptimisticPersister).delete(with(equal(bookingToDelete.getDate())),
              with(equal(attribute)));
        }
      });

      // We expect call to getBookings after the delete
      Integer someArbitraryNumber = 42;
      expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(
          Optional.of(someArbitraryNumber), expectedBookingsAfterDelete.get(), Optional.empty());
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockOptimisticPersister).delete(with(equal(bookingToDelete.getDate())),
              with(equal(attribute)));
          will(throwException(exceptionToThrow.get()));
        }
      });
    }
    bookingManager.setOptimisticPersister(mockOptimisticPersister);
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test booking manager with some overrides to facilitate testing
  public class TestBookingManager extends BookingManager {
    private AmazonSNS snsClient;
    private String adminSnsTopicArn;
    private LocalDate currentLocalDate;
    private IOptimisticPersister optimisticPersister;

    public void setOptimisticPersister(IOptimisticPersister optimisticPersister) {
      this.optimisticPersister = optimisticPersister;
    }

    @Override
    public IOptimisticPersister getOptimisticPersister() {
      return optimisticPersister;
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

    public void setCurrentLocalDate(LocalDate localDate) {
      currentLocalDate = localDate;
    }

    @Override
    public LocalDate getCurrentLocalDate() {
      return currentLocalDate;
    }

    @Override
    public String getStringProperty(String propertyName) {
      if (propertyName.equals("adminsnstopicarn")) {
        return adminSnsTopicArn;
      }
      if (propertyName.equals("region")) {
        return "eu-west-1";
      }
      return null;
    }
  }

  @Test
  public void testGetBookingsThrowsWhenBookingManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The booking manager has not been initialised");

    // ACT
    // Do not initialise the booking manager first - so we should throw
    bookingManager.getBookings(fakeCurrentDateString);
  }

  @Test
  public void testGetAllBookingsThrowsWhenBookingManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The booking manager has not been initialised");

    // ACT
    // Do not initialise the booking manager first - so we should throw
    bookingManager.getAllBookings();
  }

  @Test
  public void testGetAllBookingsReturnsCorrectBookings() throws Exception {

    // ARRANGE
    initialiseBookingManager();

    // (the version number must be set here but its value is irrelevant)
    List<ImmutablePair<String, List<Attribute>>> expectedDateAttributeListPairs = new ArrayList<>();
    List<Attribute> attributes = new ArrayList<>();
    // Add bookings that are not all on a single day.
    List<Booking> bookingsForMoreThanOneDay = new ArrayList<>();
    bookingsForMoreThanOneDay.add(existingSingleBooking);
    Booking bookingForAnotherDay = new Booking(existingSingleBooking);
    bookingForAnotherDay.setDate(LocalDate
        .parse(existingSingleBooking.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        .minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    bookingsForMoreThanOneDay.add(bookingForAnotherDay);

    bookingsForMoreThanOneDay.stream().forEach(
        booking -> {
          attributes.add(new Attribute(booking.getCourt().toString() + "-"
              + booking.getCourtSpan().toString() + "-" + booking.getSlot().toString() + "-"
              + booking.getSlotSpan().toString(), booking.getName()));
          expectedDateAttributeListPairs.add(new ImmutablePair<>(booking.getDate(), attributes));
        });

    // Set up mock optimistic persister to return these bookings - or to throw
    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).getAllItems();
        will(returnValue(expectedDateAttributeListPairs));
      }
    });
    bookingManager.setOptimisticPersister(mockOptimisticPersister);

    // ACT
    List<Booking> actualBookings = bookingManager.getAllBookings();

    // ASSERT
    for (Booking expectedBooking : bookingsForMoreThanOneDay) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testGetBookingsThrowsWhenTheOptimisticPersisterThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    initialiseBookingManager();

    expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(Optional.empty(),
        bookingsBeforeCall, Optional.of(new AmazonServiceException(message)));

    // ACT
    // Ask for the bookings - which should throw
    bookingManager.getBookings(fakeCurrentDateString);
  }

  @Test
  public void testGetBookingsCorrectlyCallsTheOptimisticPersister() throws Exception {

    // ARRANGE
    // Set up mock optimistic persister to expect the call (the version number
    // must be set here but its value is irrelevant, as we're not testing what
    // getBookings returns in this test)
    initialiseBookingManager();

    expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(Optional.of(1),
        bookingsBeforeCall, Optional.empty());

    // ACT
    bookingManager.getBookings(fakeCurrentDateString);
  }

  @Test
  public void testGetBookingsReturnsCorrectBookings() throws Exception {

    // Test happy path for getBookings: we query for a valid date and verify
    // that we get back the bookings we expect

    // ARRANGE
    initialiseBookingManager();

    // (the version number must be set here but its value is irrelevant)
    expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(Optional.of(42),
        bookingsBeforeCall, Optional.empty());

    // ACT
    // Ask for the bookings for a valid date
    List<Booking> actualBookings = bookingManager.getBookings(fakeCurrentDateString);

    // ASSERT
    for (Booking expectedBooking : bookingsBeforeCall) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testCreateBookingThrowsWhenBookingManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The booking manager has not been initialised");

    // ACT
    // Do not initialise the booking manager first - so we should throw
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingThrowsWhenTheOptimisticPersisterThrows() throws Exception {
    // N.B. This applies except when the optimistic persister throws a
    // conditional check failed exclusion, which is covered by other tests.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test OptimisticPersister exception";
    thrown.expectMessage(message);

    initialiseBookingManager();

    // Set up optimistic persister to throw
    AmazonServiceException ase = new AmazonServiceException(message);
    // Mark this as not being a ConditionalCheckFailed error
    ase.setErrorCode("SomeOtherError");
    expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        Optional.of(ase));

    // ACT
    // Try to create a booking - which should throw
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingThrowsIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionThreeTimesRunning()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exception
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if all
    // three tries fail then the rule manager will give up and throw.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Database put failed - conditional check failed";
    thrown.expectMessage(message);

    initialiseBookingManager();

    // Set up optimistic persister to throw three times
    expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        Optional.of(new Exception(message)), 3);

    // ACT
    // Try to create a booking - which should throw - albeit after three tries
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingDoesNotThrowIfTheOptimisticPersisterThrowsAConditionalCheckFailedExceptionOnlyTwice()
      throws Exception {
    // The optimistic persister can throw a conditional check failed exception
    // if two database writes happen to get interleaved. Almost always, a retry
    // should fix this, and we allow up to three tries. This tests that if we
    // throw twice but the third try succeeds, then the rule manager does not
    // throw.

    // ARRANGE
    String message = "Database put failed - conditional check failed";

    initialiseBookingManager();

    mockery.checking(new Expectations() {
      {
        // Two failures...
        // Set up optimistic persister to throw two times
        expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall,
            singleBookingOfFreeCourt, Optional.of(new Exception(message)), 2);
        // ... but third attempt succeeds
        expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall,
            singleBookingOfFreeCourt, Optional.empty());
      }
    });

    // ACT
    // Try to create a booking - which should _not_ throw - we are allowed three
    // tries internally
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheOptimisticPersisterForSingleBooking()
      throws Exception {
    // Test createBooking makes the correct calls to the optimistic persister
    // when booking a single court

    // ARRANGE
    initialiseBookingManager();

    // Set up optimistic persister to expect a single booking to be created
    expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        Optional.empty());

    // Act
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheOptimisticPersisterForBlockBooking()
      throws Exception {
    // Test createBooking makes the correct calls to the optimistic persister
    // when creating a block booking.

    // ARRANGE
    initialiseBookingManager();

    // Set up mock optimistic persister to expect a block booking to be created
    expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall, blockBookingOfFreeCourts,
        Optional.empty());

    // Act
    bookingManager.createBooking(blockBookingOfFreeCourts);
  }

  @Test
  public void testCreateBookingReturnsCorrectBookings() throws Exception {
    // ARRANGE
    initialiseBookingManager();

    // Set up mock optimistic persister to expect a booking to be created
    expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        Optional.empty());
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    expectedBookingsAfterCall.add(singleBookingOfFreeCourt);

    // Act
    List<Booking> actualBookings = bookingManager.createBooking(singleBookingOfFreeCourt);

    // ASSERT
    // Verify the returned list of bookings is same as that returned from the
    // optimistic persister.
    for (Booking expectedBooking : expectedBookingsAfterCall) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testCreateBookingThrowsIfOptimisticPersisterThrows() throws Exception {

    // ARRANGE

    thrown.expect(Exception.class);
    String message = "Test optimistic persister exception";
    thrown.expectMessage(message);

    initialiseBookingManager();

    expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        Optional.of(new Exception(message)));

    // ACT and ASSERT
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingThrowsIfSingleBookingClashesWithExistingSingleBooking()
      throws Exception {
    // Test createBooking throws when we try to book a single court that is
    // already booked.
    initialiseBookingManager();

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(existingSingleBooking);
  }

  @Test
  public void testCreateBookingThrowsIfSingleBookingClashesWithExistingBlockBooking()
      throws Exception {
    // Test createBooking throws when we try to book a single court that is
    // part of an existing block booking.
    initialiseBookingManager();

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(singleBookingWithinExistingBlockBooking);
  }

  @Test
  public void testCreateBookingThrowsIfBlockBookingClashesWithExistingBlockBooking()
      throws Exception {
    // Test createBooking throws when we try to create a block booking that
    // overlaps an existing block booking.
    initialiseBookingManager();

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(blockBookingOverlappingExistingBlockBooking);
  }

  @Test
  public void testCreateBookingThrowsIfBlockBookingClashesWithExistingSingleBooking()
      throws Exception {
    // Test createBooking throws when we try to create a block booking that
    // overlaps an existing single booking.
    initialiseBookingManager();

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(blockBookingOverlappingExistingSingleBooking);
  }

  private void doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(
      Booking clashingBookingToCreate) throws Exception {
    // Test createBooking throws when we try to create a booking that
    // overlaps an existing booking.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking creation failed");

    // Set up mock optimistic persister to return the initial bookings
    expectCreateBookingToReturnUpdatedBookingsOrThrow(bookingsBeforeCall, clashingBookingToCreate,
        Optional.empty());

    // ACT and ASSERT
    bookingManager.createBooking(clashingBookingToCreate);
  }

  @Test
  public void testDeleteAllBookingsThrowsWhenBookingManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The booking manager has not been initialised");

    // ACT
    // Do not initialise the booking manager first - so we should throw
    bookingManager.deleteAllBookings();
  }

  @Test
  public void testDeleteAllBookingsCorrectlyCallsTheOptimisticPersister() throws Exception {

    // ARRANGE
    initialiseBookingManager();

    List<ImmutablePair<String, List<Attribute>>> allDateAttributeListPairs = new ArrayList<>();
    List<Attribute> attributes = new ArrayList<>();

    bookingsBeforeCall.stream().forEach(
        booking -> {
          attributes.add(new Attribute(booking.getCourt().toString() + "-"
              + booking.getCourtSpan().toString() + "-" + booking.getSlot().toString() + "-"
              + booking.getSlotSpan().toString(), booking.getName()));
        });
    allDateAttributeListPairs.add(new ImmutablePair<>(fakeCurrentDateString, attributes));

    // Set up mock optimistic persister to return these bookings
    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).getAllItems();
        will(returnValue(allDateAttributeListPairs));
      }
    });
    bookingManager.setOptimisticPersister(mockOptimisticPersister);

    // Set up mock optimistic persister to expect all bookings to be deleted
    // N.B. expectedBookingsAfterCall is arbitrary here.
    for (Booking booking : bookingsBeforeCall) {
      expectDeleteBookingToReturnUpdatedBookingsOrThrow(booking,
          Optional.of(expectedBookingsAfterCall), Optional.empty());
    }

    // Act
    bookingManager.deleteAllBookings();
  }

  @Test
  public void testDeleteAllBookingsThrowsIfTheBookingManagerThrowsTooManyRequestsExceptionsThreeTimesRunning()
      throws Exception {
    // The booking manager can throw a TooManyRequests exception
    // if there are many bookings being deleted. If this happens we should
    // pause for a short time and then continue deleting. We allow up to three
    // attempts to delete each booking before giving up. This tests that
    // if all three tries fail then the booking manager will give up and throw.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Boom!";
    thrown.expectMessage(message);

    // ACT
    initialiseBookingManager();

    // Set up some bookings to return. These are arbitrary here - what matters
    // here is that the optimistic persister delete call throws three times.
    List<ImmutablePair<String, List<Attribute>>> allDateAttributeListPairs = new ArrayList<>();
    List<Attribute> attributes = new ArrayList<>();

    bookingsBeforeCall.stream().forEach(
        booking -> {
          attributes.add(new Attribute(booking.getCourt().toString() + "-"
              + booking.getCourtSpan().toString() + "-" + booking.getSlot().toString() + "-"
              + booking.getSlotSpan().toString(), booking.getName()));
        });
    allDateAttributeListPairs.add(new ImmutablePair<>(fakeCurrentDateString, attributes));

    // Set up mock optimistic persister to throw too many requests errors
    // Configure the TooManyRequests error (429)
    AmazonServiceException ase = new AmazonServiceException(message);
    ase.setErrorCode("429");
    mockery.checking(new Expectations() {
      {
        exactly(3).of(mockOptimisticPersister).delete(with(anything()), with(anything()));
        will(throwException(ase));
        allowing(mockOptimisticPersister).get(with(anything()));
        allowing(mockOptimisticPersister).getAllItems();
        will(returnValue(allDateAttributeListPairs));
      }
    });
    bookingManager.setOptimisticPersister(mockOptimisticPersister);

    // ACT
    // This should throw - albeit after three tries
    bookingManager.deleteAllBookings();
  }

  @Test
  public void testRestoreAllBookingsAndBookingRulesShouldNotThrowIfTheRuleManagerThrowsTooManyRequestsExceptionsOnlyTwice()
      throws Exception {
    // The rule manager can throw a TooManyRequests exception during restore
    // if there are many booking rules being restored. If this happens we should
    // pause for a short time and then continue restoring. We allow up to three
    // attempts to restore each booking rule before giving up. This tests that
    // if we throw twice but the third try succeeds, then the backup manager
    // does not throw.

    // ARRANGE
    String message = "Boom!";

    // ACT
    initialiseBookingManager();

    // Set up a single booking to return. This is arbitrary here - what matters
    // here is that the optimistic persister delete call throws twice, then
    // succeeds
    List<ImmutablePair<String, List<Attribute>>> allDateAttributeListPairs = new ArrayList<>();
    List<Attribute> attributes = new ArrayList<>();

    Booking booking = bookingsBeforeCall.get(0);
    attributes.add(new Attribute(booking.getCourt().toString() + "-"
        + booking.getCourtSpan().toString() + "-" + booking.getSlot().toString() + "-"
        + booking.getSlotSpan().toString(), booking.getName()));
    allDateAttributeListPairs.add(new ImmutablePair<>(fakeCurrentDateString, attributes));

    Integer someArbitraryNumber = 42;
    expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(
        Optional.of(someArbitraryNumber), Arrays.asList(bookingsBeforeCall.get(0)),
        Optional.empty());

    // Set up mock optimistic persister to throw too many requests errors
    // Configure the TooManyRequests error (429)
    AmazonServiceException ase = new AmazonServiceException(message);
    ase.setErrorCode("429");
    mockery.checking(new Expectations() {
      {
        // Throw twice...
        exactly(2).of(mockOptimisticPersister).delete(with(anything()), with(anything()));
        will(throwException(ase));
        // ...but succeed the third time
        oneOf(mockOptimisticPersister).delete(with(anything()), with(anything()));
        allowing(mockOptimisticPersister).getAllItems();
        will(returnValue(allDateAttributeListPairs));
      }
    });
    bookingManager.setOptimisticPersister(mockOptimisticPersister);

    // ACT
    // This should _not_ throw - we are allowed three tries
    bookingManager.deleteAllBookings();
  }

  @Test
  public void testDeleteBookingThrowsWhenBookingManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The booking manager has not been initialised");

    // ACT
    // Do not initialise the booking manager first - so we should throw
    bookingManager.deleteBooking(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingThrowsWhenTheOptimisticPersisterThrows() throws Exception {
    // (But see exception to this rule in later test)

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test SimpleDB exception";
    thrown.expectMessage(message);

    initialiseBookingManager();

    AmazonServiceException ase = new AmazonServiceException(message);
    // Mark this as not being an AttributeDoesNotExist error
    ase.setErrorCode("SomeOtherError");
    expectDeleteBookingToReturnUpdatedBookingsOrThrow(existingSingleBooking, Optional.empty(),
        Optional.of(ase));

    // ACT
    // Try to delete a booking - which should throw
    bookingManager.deleteBooking(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheOptimisticPersister() throws Exception {
    // Test deleteBooking makes the correct calls to the optimistic persister.

    // ARRANGE
    initialiseBookingManager();

    // Set up mock optimistic persister to expect a booking to be deleted
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    // Remove the booking we're deleting - so the manager thinks the delete is
    // successful
    expectedBookingsAfterCall.removeIf(booking -> booking.equals(existingSingleBooking));
    expectDeleteBookingToReturnUpdatedBookingsOrThrow(existingSingleBooking,
        Optional.of(expectedBookingsAfterCall), Optional.empty());

    // Act
    bookingManager.deleteBooking(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingReturnsCorrectBookings() throws Exception {
    // ARRANGE
    initialiseBookingManager();

    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    // Remove the booking we're deleting - so the manager thinks the delete is
    // successful
    expectedBookingsAfterCall.removeIf(booking -> booking.equals(existingSingleBooking));
    expectDeleteBookingToReturnUpdatedBookingsOrThrow(existingSingleBooking,
        Optional.of(expectedBookingsAfterCall), Optional.empty());

    // Act
    List<Booking> actualBookings = bookingManager.deleteBooking(existingSingleBooking);

    // ASSERT
    // Verify the returned list of bookings is same as that returned from
    // the optimistic persister.
    for (Booking expectedBooking : expectedBookingsAfterCall) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testDeleteBookingThrowsIfTheOptimisticPersisterThrows_SingleBooking()
      throws Exception {

    // ARRANGE
    initialiseBookingManager();

    doTestDeleteBookingThrowsWhenTheOptimisticPersisterThrows(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingThrowsIfTheOptimisticPersisterThrows_BlockBooking() throws Exception {
    initialiseBookingManager();

    doTestDeleteBookingThrowsWhenTheOptimisticPersisterThrows(existingBlockBooking);
  }

  private void doTestDeleteBookingThrowsWhenTheOptimisticPersisterThrows(Booking bookingToDelete)
      throws Exception {

    // Tests that we forward exceptions from the optimistic persister.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test optimistic persister exception";
    thrown.expectMessage(message);

    initialiseBookingManager();

    expectDeleteBookingToReturnUpdatedBookingsOrThrow(bookingToDelete, Optional.empty(),
        Optional.of(new Exception(message)));

    // ACT and ASSERT
    bookingManager.deleteBooking(bookingToDelete);
  }

  @Test
  public void testDeleteYesterdaysBookingsThrowsWhenBookingManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The booking manager has not been initialised");

    // ACT
    // Do not initialise the booking manager first - so we should throw
    bookingManager.deleteYesterdaysBookings();
  }

  @Test
  public void testDeleteYesterdaysBookingsNotifiesTheSnsTopicWhenItThrows() throws Exception {
    // It is useful for the admin user to be notified whenever the deleting
    // of bookings does not succeed - so that they can clean the database
    // manually instead. This tests that whenever the booking manager catches an
    // exception while deleting bookings, it notifies the admin SNS topic.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Test Exception";
    thrown.expectMessage(message);

    initialiseBookingManager();

    // Make method throw
    String yesterday = fakeCurrentDate.minusDays(1).format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    AmazonServiceException ase = new AmazonServiceException(message);
    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).deleteAllAttributes(with(equal(yesterday)));
        will(throwException(ase));
      }
    });
    bookingManager.setOptimisticPersister(mockOptimisticPersister);

    // Set up mock SNS client to expect a notification
    mockSNSClient = mockery.mock(AmazonSNS.class);
    String partialMessage = "Apologies - but there was an error deleting yesterday's bookings from the database";
    mockery.checking(new Expectations() {
      {
        oneOf(mockSNSClient).publish(with(equal(adminSnsTopicArn)),
            with(startsWith(partialMessage)),
            with(equal("Sqawsh bookings for yesterday failed to delete")));
      }
    });
    bookingManager.setSNSClient(mockSNSClient);

    // ACT - this should throw - and notify the SNS topic
    bookingManager.deleteYesterdaysBookings();
  }

  @Test
  public void testDeleteYesterdaysBookingsCorrectlyCallsTheOptimisticPersister() throws Exception {
    // Set up mock optimistic persister to expect yesterday's bookings to be
    // deleted.
    initialiseBookingManager();

    String yesterday = fakeCurrentDate.minusDays(1).format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    mockery.checking(new Expectations() {
      {
        oneOf(mockOptimisticPersister).deleteAllAttributes(with(equal(yesterday)));
      }
    });
    bookingManager.setOptimisticPersister(mockOptimisticPersister);

    // Act
    bookingManager.deleteYesterdaysBookings();
  }

  @Test
  public void testValidateBookingThrowsIfCourtBelowValidRange() throws Exception {
    doTestValidateBookingThrowsIfBookingInvalid(0, // Invalid
        2, 3, 3, "ValidName", "The booking court number is outside the valid range (1-5)");
  }

  @Test
  public void testValidateBookingThrowsIfCourtAboveValidRange() throws Exception {
    doTestValidateBookingThrowsIfBookingInvalid(6, // Invalid
        2, 3, 3, "ValidName", "The booking court number is outside the valid range (1-5)");
  }

  @Test
  public void testValidateBookingThrowsIfCourtSpanBelowValidRange() throws Exception {
    doTestValidateBookingThrowsIfBookingInvalid(1, 0, // Invalid
        3, 3, "ValidName", "The booking court span is outside the valid range (1-(6-court))");
  }

  @Test
  public void testValidateBookingThrowsIfCourtSpanAboveValidRange() throws Exception {
    int court = 1;
    int invalidCourtSpan = 6 - court + 1;
    doTestValidateBookingThrowsIfBookingInvalid(court, invalidCourtSpan, // Invalid
        3, 3, "ValidName", "The booking court span is outside the valid range (1-(6-court))");
  }

  @Test
  public void testValidateBookingThrowsIfTimeSlotBelowValidRange() throws Exception {
    doTestValidateBookingThrowsIfBookingInvalid(1, 2, 0, // Invalid
        2, "ValidName", "The booking time slot is outside the valid range (1-16)");
  }

  @Test
  public void testValidateBookingThrowsIfTimeSlotAboveValidRange() throws Exception {
    doTestValidateBookingThrowsIfBookingInvalid(1, 2, 17, // Invalid
        2, "ValidName", "The booking time slot is outside the valid range (1-16)");
  }

  @Test
  public void testValidateBookingThrowsIfTimeSlotSpanBelowValidRange() throws Exception {
    doTestValidateBookingThrowsIfBookingInvalid(1, 2, 1, 0, // Invalid
        "ValidName", "The booking time slot span is outside the valid range (1- (17 - slot))");
  }

  @Test
  public void testValidateBookingThrowsIfTimeSlotSpanAboveValidRange() throws Exception {
    int slot = 4;
    int invalidSlotSpan = 17 - slot + 1;
    doTestValidateBookingThrowsIfBookingInvalid(1, 2, slot, invalidSlotSpan, // Invalid
        "ValidName", "The booking time slot span is outside the valid range (1- (17 - slot))");
  }

  @Test
  public void testValidateBookingDoesNotThrowIfBookingNameOnLengthLimit() throws Exception {

    // ARRANGE
    initialiseBookingManager();

    Booking booking = new Booking();
    booking.setCourt(1);
    booking.setCourtSpan(2);
    booking.setSlot(3);
    booking.setSlotSpan(4);
    booking.setDate("2001-01-01");
    booking.setName("Right on thirrrrrty characters");

    // ACT
    // Should not throw
    bookingManager.validateBooking(booking);
  }

  @Test
  public void testValidateBookingThrowsIfBookingNameInWrongFormat_TooLong() throws Exception {

    doTestValidateBookingThrowsIfBookingInvalid(1, 2, 3, 4, "Longer than thirty character limit",
        "The booking name must have a valid format");
  }

  @Test
  public void testValidateBookingThrowsIfBookingNameInWrongFormat_InvalidCharacter()
      throws Exception {
    // ? is invalid
    doTestValidateBookingThrowsIfBookingInvalid(1, 2, 3, 4, "Playera/?",
        "The booking name must have a valid format");
  }

  @Test
  public void testValidateBookingThrowsIfBookingNameInWrongFormat_EmptyName() throws Exception {
    doTestValidateBookingThrowsIfBookingInvalid(1, 2, 3, 4, "",
        "The booking name must have a valid format");
  }

  private void doTestValidateBookingThrowsIfBookingInvalid(int court, int courtSpan, int slot,
      int slotSpan, String name, String message) throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(message);

    initialiseBookingManager();

    // Validate a booking with an invalid parameter - which should throw
    Booking booking = new Booking();
    booking.setCourt(court);
    booking.setCourtSpan(courtSpan);
    booking.setSlot(slot);
    booking.setSlotSpan(slotSpan);
    booking.setName(name);
    booking.setDate("2001-01-01");
    bookingManager.validateBooking(booking);
  }
}