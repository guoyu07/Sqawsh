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
  Context mockContext;
  LambdaLogger mockLogger;
  AmazonSimpleDB mockSimpleDbClient;

  Integer courtBookedInDatabase;
  Integer courtNotBookedInDatabase;
  Integer slotBookedInDatabase;
  String playersNamesBookedInDatabase;

  Booking bookingThatShouldCreateOk;
  Booking bookingThatShouldFailToCreate;
  Booking bookingThatShouldDeleteOk;
  Booking bookingThatShouldFailToDelete;
  List<Booking> expectedBookings;

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

    // Set up some arbitrary values for the booking in the mock database
    courtBookedInDatabase = 3;
    slotBookedInDatabase = 2;
    playersNamesBookedInDatabase = "A.Playera/B.Playerb";

    // Set up some bookings that are either the same as or different from the
    // booking in the mock database. Booking for this court will appear to
    // fail as it is not in the mock database.

    // Different court to the booking in the mock database
    courtNotBookedInDatabase = 2;

    bookingThatShouldCreateOk = new Booking(courtBookedInDatabase, slotBookedInDatabase,
        playersNamesBookedInDatabase);
    bookingThatShouldCreateOk.setDate(fakeCurrentDateString);
    bookingThatShouldFailToCreate = new Booking(courtNotBookedInDatabase, slotBookedInDatabase,
        playersNamesBookedInDatabase);
    bookingThatShouldFailToCreate.setDate(fakeCurrentDateString);
    bookingThatShouldDeleteOk = bookingThatShouldFailToCreate;
    bookingThatShouldFailToDelete = bookingThatShouldCreateOk;

    bookingManager.Initialise(mockLogger);
  }

  private void expectCreateBookingToReturnBookingsOrThrow(Optional<Exception> exceptionToThrow,
      Boolean expectCreateToSucceed) {
    String attributeName;
    // Use a booking that does (does not) correspond to what our mock
    // getBookings returns so we can mimic a successful (not
    // successful) creation
    if (!expectCreateToSucceed) {
      attributeName = courtNotBookedInDatabase.toString() + "-" + slotBookedInDatabase;
    } else {
      attributeName = courtBookedInDatabase.toString() + "-" + slotBookedInDatabase.toString();
    }
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(attributeName);
    updateCondition.setExists(false);

    ReplaceableAttribute replaceableAttribute = new ReplaceableAttribute();
    replaceableAttribute.setName(attributeName);
    replaceableAttribute.setValue(playersNamesBookedInDatabase);
    List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
    replaceableAttributes.add(replaceableAttribute);
    PutAttributesRequest simpleDbPutRequest = new PutAttributesRequest(simpleDBDomainName,
        fakeCurrentDateString, replaceableAttributes, updateCondition);

    if (!exceptionToThrow.isPresent()) {
      List<Booking> expectedBooking = new ArrayList<Booking>();
      expectedBooking.add(bookingThatShouldCreateOk);
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).putAttributes(with(simpleDbPutRequest));
        }
      });
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).putAttributes(with(simpleDbPutRequest));
          will(throwException(exceptionToThrow.get()));
        }
      });
    }
    bookingManager.setSimpleDBClient(mockSimpleDbClient);
  }

  private void expectGetBookingsToReturnBookingsOrThrow(Optional<Exception> exceptionToThrow) {
    GetAttributesRequest requestForCurrentDaysBookings = new GetAttributesRequest(
        simpleDBDomainName, fakeCurrentDateString);
    requestForCurrentDaysBookings.setConsistentRead(true);
    List<Attribute> attributes = new ArrayList<>();
    attributes.add(new Attribute(courtBookedInDatabase.toString() + "-"
        + slotBookedInDatabase.toString(), playersNamesBookedInDatabase));
    GetAttributesResult currentDaysAttributes = new GetAttributesResult();
    currentDaysAttributes.setAttributes(attributes);

    // Set up mock simpleDB client to return this booking - or to throw
    if (!exceptionToThrow.isPresent()) {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).getAttributes(with(requestForCurrentDaysBookings));
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

    // Set up the expected bookings - this is the booking in our mock database
    expectedBookings = new ArrayList<>();
    expectedBookings.add(bookingThatShouldCreateOk);
  }

  private void expectDeleteBookingToReturnBookingsOrThrow(Optional<Exception> exceptionToThrow,
      Boolean expectDeleteToSucceed) {
    String attributeName;
    UpdateCondition updateCondition = new UpdateCondition();
    // Use a booking that does not (does) correspond to what our mock
    // getBookings returns so we can mimic a successful (not
    // successful) deletion
    if (expectDeleteToSucceed) {
      // Different to getBookings booking
      attributeName = courtNotBookedInDatabase + "-" + slotBookedInDatabase.toString();
    } else {
      attributeName = courtBookedInDatabase.toString() + "-" + slotBookedInDatabase.toString();
    }
    updateCondition.setName(attributeName);
    updateCondition.setValue(playersNamesBookedInDatabase);
    updateCondition.setExists(true);
    Attribute attribute = new Attribute();
    attribute.setName(attributeName);
    attribute.setValue(playersNamesBookedInDatabase);
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
    } else {
      mockery.checking(new Expectations() {
        {
          oneOf(mockSimpleDbClient).deleteAttributes(with(simpleDbDeleteRequest));
          will(throwException(exceptionToThrow.get()));
        }
      });
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
    private String simpleDBDomainName;

    public void setSimpleDBClient(AmazonSimpleDB client) {
      amazonSimpleDBClient = client;
    }

    @Override
    public AmazonSimpleDB getSimpleDBClient() {
      return amazonSimpleDBClient;
    }

    public void setSimpleDBDomainName(String simpleDBDomainName) {
      this.simpleDBDomainName = simpleDBDomainName;
    }

    @Override
    protected String getStringProperty(String propertyName) {
      if (propertyName.equals("simpledbdomainname")) {
        return this.simpleDBDomainName;
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
  public void testGetBookingsThrowsWhenTheDatabaseThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Test SimpleDB exception");
    expectGetBookingsToReturnBookingsOrThrow(Optional.of(new AmazonServiceException(
        "Test SimpleDB exception")));

    // ACT
    // Ask for the bookings - which should throw
    bookingManager.getBookings(fakeCurrentDateString);
  }

  @Test
  public void testGetBookingsCorrectlyCallsTheDatabase() throws Exception {

    // ARRANGE
    // Set up mock simpleDB client to expect the database call
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    // ACT
    bookingManager.getBookings(fakeCurrentDateString);
  }

  @Test
  public void testGetBookingsReturnsCorrectBookings() throws Exception {

    // Test happy path for getBookings: we query for a valid date and verify
    // that we get back the booking we expect

    // ARRANGE
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    // ACT
    // Ask for the bookings for a valid date
    List<Booking> actualBookings = bookingManager.getBookings(fakeCurrentDateString);

    // ASSERT
    for (Booking expectedBooking : expectedBookings) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testCreateBookingThrowsWhenTheDatabaseThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Test SimpleDB exception");

    // Set up simpleDB to throw
    expectCreateBookingToReturnBookingsOrThrow(
        Optional.of(new AmazonServiceException("Test SimpleDB exception")), true);

    // ACT
    // Try to create a booking - which should throw
    bookingManager.createBooking(bookingThatShouldCreateOk);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheDatabase() throws Exception {
    // Test createBooking makes the correct calls to SimpleDB

    // ARRANGE

    // Set up mock simpledb to expect a booking to be created
    expectCreateBookingToReturnBookingsOrThrow(Optional.empty(), true);

    // Set up mock simpledb to expect a call to query the bookings
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    // Act
    bookingManager.createBooking(bookingThatShouldCreateOk);
  }

  @Test
  public void testCreateBookingReturnsCorrectBookings() throws Exception {
    // ARRANGE

    // Set up mock simpledb to expect a booking to be created
    expectCreateBookingToReturnBookingsOrThrow(Optional.empty(), true);

    // Set up mock simpledb to expect a call to query the bookings
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    // Act
    List<Booking> actualBookings = bookingManager.createBooking(bookingThatShouldCreateOk);

    // ASSERT
    // Verify the returned list of bookings is same as that returned from the
    // database
    for (Booking expectedBooking : expectedBookings) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testCreateBookingThrowsIfDatabaseWriteFails() throws Exception {
    // Test createBooking throws when SimpleDB reports it has not
    // made the booking in the database. We try to create a booking
    // that differs to what our mock simpleDB client::getAttributes
    // returns. This booking creation should thus fail.

    // ARRANGE
    // Set up mock simpledb to expect a booking to be created
    expectCreateBookingToReturnBookingsOrThrow(Optional.empty(), false);

    // Set up mock simpledb to expect a call to query the bookings
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking creation failed");

    // ACT and ASSERT
    bookingManager.createBooking(bookingThatShouldFailToCreate);
  }

  @Test
  public void testDeleteBookingThrowsWhenTheDatabaseThrows() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Test SimpleDB exception");
    expectDeleteBookingToReturnBookingsOrThrow(
        Optional.of(new AmazonServiceException("Test SimpleDB exception")), true);

    // ACT
    // Try to delete a booking - which should throw
    bookingManager.deleteBooking(bookingThatShouldDeleteOk);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheDatabase() throws Exception {
    // Test deleteBooking makes the correct calls to SimpleDB

    // ARRANGE

    // Set up mock simpledb to expect a booking to be deleted
    // Use a booking that does not correspond to what our mock
    // simpleDB client::getAttributes returns. This deletion should
    // thus succeed - as it will seem like the booking is absent.
    expectDeleteBookingToReturnBookingsOrThrow(Optional.empty(), true);

    // Set up mock simpledb to expect a call to query the bookings
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    // Act
    bookingManager.deleteBooking(bookingThatShouldDeleteOk);
  }

  @Test
  public void testDeleteBookingReturnsCorrectBookings() throws Exception {
    // ARRANGE

    expectDeleteBookingToReturnBookingsOrThrow(Optional.empty(), true);

    // Set up mock simpledb to expect a call to query the bookings
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    // Act
    List<Booking> actualBookings = bookingManager.deleteBooking(bookingThatShouldDeleteOk);

    // ASSERT
    // Verify the returned list of bookings is same as that returned from
    // the database
    for (Booking expectedBooking : expectedBookings) {
      assertTrue("Expected " + expectedBooking.toString(), actualBookings.contains(expectedBooking));
      actualBookings.removeIf(booking -> booking.equals(expectedBooking));
    }
    assertTrue("More bookings than expected were returned", actualBookings.size() == 0);
  }

  @Test
  public void testDeleteBookingThrowsIfDatabaseWriteFails() throws Exception {
    // Test deleteBooking throws when SimpleDB reports it has not
    // deleted the booking in the database.

    // ARRANGE
    // Try to delete a booking that does not differ from what our mock
    // simpleDB client::getAttributes returns. This deletion should
    // thus appear to fail.
    expectDeleteBookingToReturnBookingsOrThrow(Optional.empty(), false);
    // Set up mock simpledb to expect a call to query the bookings
    expectGetBookingsToReturnBookingsOrThrow(Optional.empty());

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking deletion failed");

    // ACT and ASSERT
    bookingManager.deleteBooking(bookingThatShouldFailToDelete);
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