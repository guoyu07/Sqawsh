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

package squash.booking.lambdas;

import static org.junit.Assert.assertTrue;

import squash.booking.lambdas.core.Booking;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.ILifecycleManager;
import squash.booking.lambdas.core.ILifecycleManager.LifecycleState;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tests the {@link GetBookingsLambda GetBookings} lambda.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class GetBookingsLambdaTest {
  Mockery mockery = new Mockery();
  TestGetBookingsLambda getBookingsLambda;
  Context mockContext;
  ILifecycleManager mockLifecycleManager;
  List<Booking> expectedBookings;
  LambdaLogger mockLogger;
  String name;
  Integer court;
  Integer slot;
  Booking booking;
  List<Booking> bookings;
  LocalDate fakeCurrentDate;
  String fakeCurrentDateString;
  String redirectUrl;
  String genericExceptionMessage;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() throws Exception {
    mockery = new Mockery();
    getBookingsLambda = new TestGetBookingsLambda();
    getBookingsLambda.setBookingManager(mockery.mock(IBookingManager.class));

    // Set up the valid date range
    List<String> validDates = new ArrayList<>();
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    fakeCurrentDateString = fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    validDates.add(fakeCurrentDateString);
    validDates.add(fakeCurrentDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    getBookingsLambda.setValidDates(validDates);

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

    // Set up mock lifecycle manager
    mockLifecycleManager = mockery.mock(ILifecycleManager.class);
    mockery.checking(new Expectations() {
      {
        allowing(mockLifecycleManager).getLifecycleState();
        will(returnValue(new ImmutablePair<LifecycleState, Optional<String>>(LifecycleState.ACTIVE,
            Optional.empty())));
      }
    });
    getBookingsLambda.setLifecycleManager(mockLifecycleManager);

    // Set up some typical bookings data that the tests can use
    name = "A.Playera/B.Playerb";
    court = 5;
    slot = 3;
    booking = new Booking(court, slot, name);
    bookings = new ArrayList<>();
    bookings.add(booking);
    redirectUrl = "redirectUrl.html";

    // Exception message thrown to apigateway invocations has a redirecturl
    // appended
    genericExceptionMessage = "Apologies - something has gone wrong. Please try again."
        + redirectUrl;
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test sublass with some overrides to facilitate testing
  public class TestGetBookingsLambda extends GetBookingsLambda {
    private IBookingManager bookingManager;
    private ILifecycleManager lifecycleManager;
    private List<String> validDates;

    public void setBookingManager(IBookingManager bookingManager) {
      this.bookingManager = bookingManager;
    }

    @Override
    protected IBookingManager getBookingManager(LambdaLogger logger) throws Exception {
      return bookingManager;
    }

    public void setLifecycleManager(ILifecycleManager lifecycleManager) {
      this.lifecycleManager = lifecycleManager;
    }

    @Override
    protected ILifecycleManager getLifecycleManager(LambdaLogger logger) throws Exception {
      return lifecycleManager;
    }

    public void setValidDates(List<String> validDates) {
      this.validDates = validDates;
    }

    @Override
    protected List<String> getValidDates() {
      return validDates;
    }
  }

  @Test
  public void testGetBookingsThrowsCorrectExceptionIfDateOutsideValidRange() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown
        .expectMessage("The booking date is outside the valid range. Please try again.redirectUrl");

    // ACT
    // Ask for the bookings for an invalid date - which should throw
    GetBookingsLambdaRequest request = new GetBookingsLambdaRequest();
    request.setDate("2015-10-08"); // Invalid - too far in future
    request.setRedirectUrl(redirectUrl);

    getBookingsLambda.getBookings(request, mockContext);
  }

  @Test
  public void testGetBookingsThrowsCorrectExceptionWhenBookingManagerThrowsAce() throws Exception {
    // N.B. The precise exceptions thrown by the lambdas are important as they
    // get matched by a regex in ApiGateway to be reported to the user.

    doTestGetBookingsThrowsCorrectExceptionWhenBookingManagerThrows(new AmazonClientException(
        "Grrr.."));
  }

  @Test
  public void testGetBookingsThrowsCorrectExceptionWhenBookingManagerThrowsAse() throws Exception {
    // N.B. The precise exceptions thrown by the lambdas are important as
    // they get matched by a regex in ApiGateway to be reported to the user.

    doTestGetBookingsThrowsCorrectExceptionWhenBookingManagerThrows(new AmazonServiceException(
        "Grrr.."));
  }

  @Test
  public void testGetBookingsThrowsCorrectExceptionWhenBookingManagerThrowsSomethingElse()
      throws Exception {
    // When any exception other than those explicitly checked for is thrown
    // we should convert it as described here.

    doTestGetBookingsThrowsCorrectExceptionWhenBookingManagerThrows(new IllegalAccessException(
        "Grrr.."));
  }

  public void doTestGetBookingsThrowsCorrectExceptionWhenBookingManagerThrows(Exception exception)
      throws Exception {
    // N.B. The precise exceptions thrown by the lambdas are important as they
    // get matched by a regex in ApiGateway to be reported to the user.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(genericExceptionMessage);

    // Make getBookings call throw
    mockery.checking(new Expectations() {
      {
        allowing(getBookingsLambda.getBookingManager(mockLogger)).getBookings(
            with(aNonNull(String.class)), with.booleanIs(anything()));
        will(throwException(exception));
      }
    });

    // ACT
    // Ask for the bookings for a valid date
    GetBookingsLambdaRequest request = new GetBookingsLambdaRequest();
    request.setDate(fakeCurrentDateString);
    request.setRedirectUrl(redirectUrl);

    getBookingsLambda.getBookings(request, mockContext);
  }

  @Test
  public void testGetBookingsCorrectlyCallsBookingManager() throws Exception {

    // Test happy path for getBookings: we verify the call gets forwarded
    // to the IBookingManager, and the returned bookings are wired back.

    // ARRANGE
    // Set up some expected bookings, so we can check they get forwarded on back
    // to us
    mockery.checking(new Expectations() {
      {
        oneOf(getBookingsLambda.getBookingManager(mockLogger)).getBookings(
            with(equal(fakeCurrentDateString)), with.booleanIs(anything()));
        will(returnValue(bookings));
      }
    });

    // ACT
    // Ask for the bookings for a valid date
    GetBookingsLambdaRequest request = new GetBookingsLambdaRequest();
    request.setDate(fakeCurrentDateString);

    GetBookingsLambdaResponse response = getBookingsLambda.getBookings(request, mockContext);

    // Assert
    // Check returned bookings are wired back
    assertTrue("Bookings returned by getBookings were not as expected",
        response.getBookings() == bookings);
  }
}