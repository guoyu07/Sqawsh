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
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.UpdateCondition;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tests the {@link BookingManager}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingManagerTest {
  // Variables for setting up subclass of class under test
  String simpleDBDomainName;
  LocalDate fakeCurrentDate;
  String fakeCurrentDateString;
  squash.booking.lambdas.utils.BookingManagerTest.TestBookingManager bookingManager;

  // Mocks
  Mockery mockery = new Mockery();
  final States database = mockery.states("database");
  Context mockContext;
  LambdaLogger mockLogger;
  AmazonSimpleDB mockSimpleDbClient;

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
  public void beforeTest() throws IOException {
    simpleDBDomainName = "simpleDBDomainName";
    // Set up the valid date range
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    fakeCurrentDateString = fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    bookingManager = new squash.booking.lambdas.utils.BookingManagerTest.TestBookingManager();
    bookingManager.setSimpleDBDomainName(simpleDBDomainName);
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
    mockSimpleDbClient = mockery.mock(AmazonSimpleDB.class);

    bookingManager.initialise(mockLogger);

    // Database uses a Read-Modify-Write pattern. This requires an initial call
    // to getBookings(i.e. the 'Read'). Not part of the pattern, but we follow
    // this by a final call after making the booking to verify it created ok. We
    // thus need the mock getBookings to return different bookings for these two
    // calls. This state is used to allow that.
    database.startsAs("BeforeBookingAttempted");
  }

  private void expectCreateBookingToReturnBookingsOrThrow(List<Booking> initialBookings,
      Booking bookingToCreate, List<Booking> expectedFinalBookings,
      Optional<Exception> exceptionToThrow) {

    String valueForUpdateCondition;
    String versionForPutAttributes;
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName("VersionNumber");
    ReplaceableAttribute versionAttribute = new ReplaceableAttribute();
    versionAttribute.setName("VersionNumber");
    versionAttribute.setReplace(true);
    if (initialBookings.size() == 0) {
      updateCondition.setExists(false);
      valueForUpdateCondition = "0";
      versionForPutAttributes = "1";
    } else {
      // The value we use here should not matter - what matters is that the call
      // to create uses a value 1 higher (e.g. 5 versus 4)
      updateCondition.setValue("4");
      valueForUpdateCondition = "4";
      versionForPutAttributes = "5";
    }
    versionAttribute.setValue(versionForPutAttributes);
    // Add the version number attribute
    List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
    replaceableAttributes.add(versionAttribute);
    // Add booking attribute for the booking being created
    String attributeName = bookingToCreate.getCourt().toString() + "-"
        + bookingToCreate.getCourtSpan().toString() + "-" + bookingToCreate.getSlot() + "-"
        + bookingToCreate.getSlotSpan().toString();
    ReplaceableAttribute bookingAttribute = new ReplaceableAttribute();
    bookingAttribute.setName(attributeName);
    bookingAttribute.setValue(newPlayersNames);
    replaceableAttributes.add(bookingAttribute);

    PutAttributesRequest simpleDbPutRequest = new PutAttributesRequest(simpleDBDomainName,
        fakeCurrentDateString, replaceableAttributes, updateCondition);

    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).putAttributes(with(simpleDbPutRequest));
        }
      });
      // Booking creation calls getBookings before trying to make the booking...
      expectGetBookingsToReturnBookingsOrThrow(Optional.of(valueForUpdateCondition),
          Optional.of("BeforeBookingAttempted"), Optional.of("AfterBookingAttempted"),
          initialBookings, Optional.empty());
      // ...and after trying to make the booking
      expectGetBookingsToReturnBookingsOrThrow(Optional.of(versionForPutAttributes),
          Optional.of("AfterBookingAttempted"), Optional.empty(), expectedFinalBookings,
          Optional.empty());
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).putAttributes(with(simpleDbPutRequest));
          will(throwException(exceptionToThrow.get()));
        }
      });
      // Booking creation calls getBookings before trying to make the booking
      expectGetBookingsToReturnBookingsOrThrow(Optional.of(valueForUpdateCondition),
          Optional.of("BeforeBookingAttempted"), Optional.empty(), initialBookings,
          Optional.empty());
    }

    bookingManager.setSimpleDBClient(mockSimpleDbClient);
  }

  private void expectGetBookingsToReturnBookingsOrThrow(Optional<String> versionNumber,
      Optional<String> databaseWhenState, Optional<String> databaseThenState,
      List<Booking> expectedBookings, Optional<Exception> exceptionToThrow) {
    GetAttributesRequest requestForCurrentDaysBookings = new GetAttributesRequest(
        simpleDBDomainName, fakeCurrentDateString);
    requestForCurrentDaysBookings.setConsistentRead(true);
    List<Attribute> attributes = new ArrayList<>();
    if (versionNumber.isPresent()) {
      attributes.add(new Attribute("VersionNumber", versionNumber.get()));
    }
    expectedBookings.stream().forEach(
        booking -> {
          attributes.add(new Attribute(booking.getCourt().toString() + "-"
              + booking.getCourtSpan().toString() + "-" + booking.getSlot().toString() + "-"
              + booking.getSlotSpan().toString(), booking.getPlayers()));
        });
    GetAttributesResult currentDaysAttributes = new GetAttributesResult();
    currentDaysAttributes.setAttributes(attributes);

    // Set up mock simpleDB client to return these bookings - or to throw
    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).getAttributes(with(requestForCurrentDaysBookings));
          when(database.is(databaseWhenState.get()));
          if (databaseThenState.isPresent()) {
            then(database.is(databaseThenState.get()));
          }
          will(returnValue(currentDaysAttributes));
        }
      });
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).getAttributes(with(requestForCurrentDaysBookings));
          will(throwException(exceptionToThrow.get()));
        }
      });
    }
    bookingManager.setSimpleDBClient(mockSimpleDbClient);
  }

  private void expectDeleteBookingToReturnBookingsOrThrow(Booking bookingToDelete,
      Optional<List<Booking>> expectedBookingsAfterDelete, Optional<Exception> exceptionToThrow) {

    String attributeName;
    attributeName = bookingToDelete.getCourt().toString() + "-"
        + bookingToDelete.getCourtSpan().toString() + "-" + bookingToDelete.getSlot().toString()
        + "-" + bookingToDelete.getSlotSpan().toString();

    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(attributeName);
    updateCondition.setValue(bookingToDelete.getPlayers());
    updateCondition.setExists(true);
    Attribute attribute = new Attribute();
    attribute.setName(attributeName);
    attribute.setValue(bookingToDelete.getPlayers());
    List<Attribute> attributes = new ArrayList<>();
    attributes.add(attribute);

    DeleteAttributesRequest simpleDbDeleteRequest = new DeleteAttributesRequest(simpleDBDomainName,
        fakeCurrentDateString, attributes, updateCondition);
    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).deleteAttributes(with(simpleDbDeleteRequest));
        }
      });

      // We expect call to getBookings after the delete to verify delete
      // succeeded.
      expectGetBookingsToReturnBookingsOrThrow(Optional.of("42"),
          Optional.of("AfterBookingAttempted"), Optional.empty(),
          expectedBookingsAfterDelete.get(), Optional.empty());
      database.become("AfterBookingAttempted");
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).deleteAttributes(with(simpleDbDeleteRequest));
          will(throwException(exceptionToThrow.get()));
        }
      });
      if (expectedBookingsAfterDelete.isPresent()) {
        expectGetBookingsToReturnBookingsOrThrow(Optional.of("42"),
            Optional.of("AfterBookingAttempted"), Optional.empty(),
            expectedBookingsAfterDelete.get(), Optional.empty());
        database.become("AfterBookingAttempted");
      }
    }
    bookingManager.setSimpleDBClient(mockSimpleDbClient);
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test booking manager with some overrides to facilitate testing
  public class TestBookingManager extends BookingManager {
    private LocalDate currentLocalDate;
    private AmazonSimpleDB amazonSimpleDBClient;
    private String mockSimpleDBDomainName;

    public void setSimpleDBClient(AmazonSimpleDB client) {
      amazonSimpleDBClient = client;
    }

    @Override
    public AmazonSimpleDB getSimpleDBClient() {
      return amazonSimpleDBClient;
    }

    public void setSimpleDBDomainName(String simpleDBDomainName) {
      this.mockSimpleDBDomainName = simpleDBDomainName;
    }

    @Override
    protected String getStringProperty(String propertyName) {
      if (propertyName.equals("simpledbdomainname")) {
        return this.mockSimpleDBDomainName;
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
  public void testGetBookingsThrowsWhenTheDatabaseThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Test SimpleDB exception");

    expectGetBookingsToReturnBookingsOrThrow(Optional.empty(),
        Optional.of("BeforeBookingAttempted"), Optional.empty(), bookingsBeforeCall,
        Optional.of(new AmazonServiceException("Test SimpleDB exception")));

    // ACT
    // Ask for the bookings - which should throw
    bookingManager.getBookings(fakeCurrentDateString);
  }

  @Test
  public void testGetBookingsCorrectlyCallsTheDatabase() throws Exception {

    // ARRANGE
    // Set up mock simpleDB client to expect the database call
    // (the version number must be set here but its value is irrelevant, as
    // we're not testing what getBookings returns in this test)
    expectGetBookingsToReturnBookingsOrThrow(Optional.of("1"),
        Optional.of("BeforeBookingAttempted"), Optional.empty(), bookingsBeforeCall,
        Optional.empty());

    // ACT
    bookingManager.getBookings(fakeCurrentDateString);
  }

  @Test
  public void testGetBookingsReturnsCorrectBookings() throws Exception {

    // Test happy path for getBookings: we query for a valid date and verify
    // that we get back the bookings we expect

    // ARRANGE
    // (the version number must be set here but its value is irrelevant)
    expectGetBookingsToReturnBookingsOrThrow(Optional.of("42"),
        Optional.of("BeforeBookingAttempted"), Optional.empty(), bookingsBeforeCall,
        Optional.empty());

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
  public void testCreateBookingThrowsWhenTheDatabaseThrows() throws Exception {
    // (But see exception to this rule in later test)

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Test SimpleDB exception");

    // Set up simpleDB to throw
    AmazonServiceException ase = new AmazonServiceException("Test SimpleDB exception");
    // Mark this as not being a ConditionalCheckFailed error
    ase.setErrorCode("SomeOtherError");
    expectCreateBookingToReturnBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        bookingsBeforeCall, Optional.of(ase));

    // ACT
    // Try to create a booking - which should throw
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingThrowsNewExceptionWhenTheDatabaseThrowsBecauseConditionalCheckFailed()
      throws Exception {
    // This particular error we convert and throw with a
    // "Booking creation failed" message instead - it probably means more than
    // one person was trying to book the slot at once.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking creation failed");

    AmazonServiceException ase = new AmazonServiceException("Test SimpleDB exception");
    // Set the error code that identifies this particular case
    ase.setErrorCode("ConditionalCheckFailed");
    expectCreateBookingToReturnBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        bookingsBeforeCall, Optional.of(ase));

    // ACT
    // Try to create a booking - which should throw
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheDatabaseForSingleBooking() throws Exception {
    // Test createBooking makes the correct calls to SimpleDB when booking a
    // single court

    // ARRANGE

    // Set up mock simpledb to expect a single booking to be created
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    expectedBookingsAfterCall.add(singleBookingOfFreeCourt);
    expectCreateBookingToReturnBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        expectedBookingsAfterCall, Optional.empty());

    // Act
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheDatabaseForBlockBooking() throws Exception {
    // Test createBooking makes the correct calls to SimpleDB when creating a
    // block booking.

    // ARRANGE

    // Set up mock simpledb to expect a block booking to be created
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    expectedBookingsAfterCall.add(blockBookingOfFreeCourts);
    expectCreateBookingToReturnBookingsOrThrow(bookingsBeforeCall, blockBookingOfFreeCourts,
        expectedBookingsAfterCall, Optional.empty());

    // Act
    bookingManager.createBooking(blockBookingOfFreeCourts);
  }

  @Test
  public void testCreateBookingReturnsCorrectBookings() throws Exception {
    // ARRANGE

    // Set up mock simpledb to expect a booking to be created
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    expectedBookingsAfterCall.add(singleBookingOfFreeCourt);
    expectCreateBookingToReturnBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        expectedBookingsAfterCall, Optional.empty());

    // Act
    List<Booking> actualBookings = bookingManager.createBooking(singleBookingOfFreeCourt);

    // ASSERT
    // Verify the returned list of bookings is same as that returned from the
    // database
    for (Booking expectedBooking : expectedBookingsAfterCall) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testCreateBookingThrowsIfDatabaseWriteFails() throws Exception {
    // Test createBooking throws when SimpleDB reports it has not
    // made the booking in the database.

    // ARRANGE
    // Set up mock simpledb to expect a booking to be created
    // Don't add the booking we're creating to the after-call list
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    expectCreateBookingToReturnBookingsOrThrow(bookingsBeforeCall, singleBookingOfFreeCourt,
        expectedBookingsAfterCall, Optional.empty());

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking creation failed");

    // ACT and ASSERT
    bookingManager.createBooking(singleBookingOfFreeCourt);
  }

  @Test
  public void testCreateBookingThrowsIfSingleBookingClashesWithExistingSingleBooking()
      throws Exception {
    // Test createBooking throws when we try to book a single court that is
    // already booked.

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(existingSingleBooking);
  }

  @Test
  public void testCreateBookingThrowsIfSingleBookingClashesWithExistingBlockBooking()
      throws Exception {
    // Test createBooking throws when we try to book a single court that is
    // part of an existing block booking.

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(singleBookingWithinExistingBlockBooking);
  }

  @Test
  public void testCreateBookingThrowsIfBlockBookingClashesWithExistingBlockBooking()
      throws Exception {
    // Test createBooking throws when we try to create a block booking that
    // overlaps an existing block booking.

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(blockBookingOverlappingExistingBlockBooking);
  }

  @Test
  public void testCreateBookingThrowsIfBlockBookingClashesWithExistingSingleBooking()
      throws Exception {
    // Test createBooking throws when we try to create a block booking that
    // overlaps an existing single booking.

    doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(blockBookingOverlappingExistingSingleBooking);
  }

  private void doTestCreateBookingThrowsIfBookingClashesWithExistingBooking(Booking bookingToCreate)
      throws Exception {
    // Test createBooking throws when we try to create a booking that
    // overlaps an existing booking.

    // ARRANGE
    // Set up mock simpledb to expect a booking to be created
    expectCreateBookingToReturnBookingsOrThrow(bookingsBeforeCall, bookingToCreate,
        bookingsBeforeCall, Optional.empty());

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking creation failed");

    // ACT and ASSERT
    bookingManager.createBooking(bookingToCreate);
  }

  @Test
  public void testDeleteBookingThrowsWhenTheDatabaseThrows() throws Exception {
    // (But see exception to this rule in later test)

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Test SimpleDB exception");
    AmazonServiceException ase = new AmazonServiceException("Test SimpleDB exception");
    // Mark this as not being an AttributeDoesNotExist error
    ase.setErrorCode("SomeOtherError");
    expectDeleteBookingToReturnBookingsOrThrow(existingSingleBooking, Optional.empty(),
        Optional.of(ase));

    // ACT
    // Try to delete a booking - which should throw
    bookingManager.deleteBooking(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingDoesNotThrowWhenTheDatabaseThrowsBecauseAttributeDoesNotExist()
      throws Exception {
    // This particular error we regard as ok, and swallow - it probably just
    // means more than one person was trying to delete the booking at once.

    // ARRANGE
    AmazonServiceException ase = new AmazonServiceException("Test SimpleDB exception");
    // Set the error code that identifies this particular case
    ase.setErrorCode("AttributeDoesNotExist");
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    expectedBookingsAfterCall.removeIf(booking -> booking.equals(existingSingleBooking));
    expectDeleteBookingToReturnBookingsOrThrow(existingSingleBooking,
        Optional.of(expectedBookingsAfterCall), Optional.of(ase));

    // ACT
    // Try to delete a booking - which should not throw
    bookingManager.deleteBooking(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheDatabase() throws Exception {
    // Test deleteBooking makes the correct calls to SimpleDB

    // ARRANGE

    // Set up mock simpledb to expect a booking to be deleted
    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    // Remove the booking we're deleting - so the manager thinks the delete is
    // successful
    expectedBookingsAfterCall.removeIf(booking -> booking.equals(existingSingleBooking));
    expectDeleteBookingToReturnBookingsOrThrow(existingSingleBooking,
        Optional.of(expectedBookingsAfterCall), Optional.empty());

    // Act
    bookingManager.deleteBooking(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingReturnsCorrectBookings() throws Exception {
    // ARRANGE

    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    // Remove the booking we're deleting - so the manager thinks the delete is
    // successful
    expectedBookingsAfterCall.removeIf(booking -> booking.equals(existingSingleBooking));
    expectDeleteBookingToReturnBookingsOrThrow(existingSingleBooking,
        Optional.of(expectedBookingsAfterCall), Optional.empty());

    // Act
    List<Booking> actualBookings = bookingManager.deleteBooking(existingSingleBooking);

    // ASSERT
    // Verify the returned list of bookings is same as that returned from
    // the database
    for (Booking expectedBooking : expectedBookingsAfterCall) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testDeleteBookingThrowsIfDatabaseWriteFailsSingleBooking() throws Exception {
    // Test deleteBooking throws when SimpleDB reports it has not
    // deleted the single booking in the database.

    // ARRANGE
    doTestDeleteBookingThrowsIfDatabaseWriteFails(existingSingleBooking);
  }

  @Test
  public void testDeleteBookingThrowsIfDatabaseWriteFailsBlockBooking() throws Exception {
    // Test deleteBooking throws when SimpleDB reports it has not
    // deleted the block booking in the database.

    doTestDeleteBookingThrowsIfDatabaseWriteFails(existingBlockBooking);
  }

  private void doTestDeleteBookingThrowsIfDatabaseWriteFails(Booking bookingToDelete)
      throws Exception {
    // Test deleteBooking throws when SimpleDB reports it has not
    // deleted the booking in the database.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Booking deletion failed");

    expectedBookingsAfterCall.addAll(bookingsBeforeCall);
    // Do not remove the booking we're deleting - so the manager thinks the
    // delete has failed.
    expectDeleteBookingToReturnBookingsOrThrow(bookingToDelete,
        Optional.of(expectedBookingsAfterCall), Optional.empty());

    // ACT and ASSERT
    bookingManager.deleteBooking(bookingToDelete);
  }

  @Test
  public void testDeleteYesterdaysBookingsCorrectlyCallsTheDatabase() throws Exception {
    // Set up mock simpledb to expect yesterday's bookings to be deleted
    DeleteAttributesRequest simpleDbDeleteRequest = new DeleteAttributesRequest(simpleDBDomainName,
        fakeCurrentDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDbClient).deleteAttributes(with(simpleDbDeleteRequest));
      }
    });
    bookingManager.setSimpleDBClient(mockSimpleDbClient);

    // Act
    bookingManager.deleteYesterdaysBookings();
  }
}