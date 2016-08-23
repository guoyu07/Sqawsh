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

import static org.junit.Assert.assertTrue;

import squash.booking.lambdas.core.Booking;
import squash.booking.lambdas.core.BookingManager;
import squash.booking.lambdas.core.IOptimisticPersister;

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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

  // Mocks
  Mockery mockery = new Mockery();
  final States database = mockery.states("database");
  Context mockContext;
  LambdaLogger mockLogger;
  IOptimisticPersister mockOptimisticPersister;

  // Create some example bookings to test with
  Booking existingSingleBooking;
  Booking singleBookingOfFreeCourt;
  Booking singleBookingWithinExistingBlockBooking;
  Booking existingBlockBooking;
  Booking blockBookingOfFreeCourts;
  Booking blockBookingOverlappingExistingSingleBooking;
  Booking blockBookingOverlappingExistingBlockBooking;

  String existingPlayersNames;
  String newPlayersNames;

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
    existingPlayersNames = "A.Shabana/J.Power";
    newPlayersNames = "J.Willstrop/N.Matthew";
    existingSingleBooking = new Booking(2, 1, 3, 1, existingPlayersNames);
    existingSingleBooking.setDate(fakeCurrentDateString);
    blockBookingOverlappingExistingSingleBooking = new Booking(1, 3, 3, 2, newPlayersNames);
    blockBookingOverlappingExistingSingleBooking.setDate(fakeCurrentDateString);
    blockBookingOverlappingExistingBlockBooking = new Booking(2, 2, 9, 2, newPlayersNames);
    blockBookingOverlappingExistingBlockBooking.setDate(fakeCurrentDateString);
    singleBookingOfFreeCourt = new Booking(4, 1, 12, 1, newPlayersNames);
    singleBookingOfFreeCourt.setDate(fakeCurrentDateString);
    existingBlockBooking = new Booking(3, 3, 10, 2, existingPlayersNames);
    existingBlockBooking.setDate(fakeCurrentDateString);
    singleBookingWithinExistingBlockBooking = new Booking(4, 1, 11, 1, newPlayersNames);
    singleBookingWithinExistingBlockBooking.setDate(fakeCurrentDateString);
    blockBookingOfFreeCourts = new Booking(1, 5, 13, 3, newPlayersNames);
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
  }

  private void initialiseBookingManager() throws Exception {
    // Call this to initialise the booking manager in tests where this
    // initialisation is not the subject of the test.

    bookingManager.initialise(mockLogger);
  }

  private void expectCreateBookingToReturnUpdatedBookingsOrThrow(List<Booking> initialBookings,
      Booking bookingToCreate, Optional<Exception> exceptionToThrow) throws Exception {

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
    bookingAttribute.setValue(newPlayersNames);

    // Booking creation gets existing bookings before trying to make the new one
    expectOptimisticPersisterGetToReturnVersionedAttributesOrThrow(expectedVersionNumber,
        initialBookings, Optional.empty());

    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          oneOf(mockOptimisticPersister).put(with(equal(bookingToCreate.getDate())),
              with(equal(expectedVersionNumber)), with(equal(bookingAttribute)));
        }
      });
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockOptimisticPersister).put(with(equal(bookingToCreate.getDate())),
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
    Set<Attribute> attributes = new HashSet<>();
    expectedBookings.stream().forEach(
        booking -> {
          attributes.add(new Attribute(booking.getCourt().toString() + "-"
              + booking.getCourtSpan().toString() + "-" + booking.getSlot().toString() + "-"
              + booking.getSlotSpan().toString(), booking.getPlayers()));
        });

    // Set up mock optimistic persister to return these bookings - or to throw
    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          oneOf(mockOptimisticPersister).get(with(equal(fakeCurrentDateString)));
          will(returnValue(new ImmutablePair<>(expectedVersionNumber, attributes)));
        }
      });
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockOptimisticPersister).get(with(equal(fakeCurrentDateString)));
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
    attribute.setValue(bookingToDelete.getPlayers());

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
    private LocalDate currentLocalDate;
    private IOptimisticPersister optimisticPersister;

    public void setOptimisticPersister(IOptimisticPersister optimisticPersister) {
      this.optimisticPersister = optimisticPersister;
    }

    @Override
    public IOptimisticPersister getOptimisticPersister() {
      return optimisticPersister;
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
              + booking.getSlotSpan().toString(), booking.getPlayers()));
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
    // (But see exception to this rule in later test)

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
              + booking.getSlotSpan().toString(), booking.getPlayers()));
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
}