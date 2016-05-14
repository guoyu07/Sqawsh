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

package squash.booking.lambdas;

import squash.booking.lambdas.utils.IBookingManager;
import squash.booking.lambdas.utils.IPageManager;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
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

/**
 * Tests the {@link UpdateBookingsLambda UpdateBookings} lambda.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class UpdateBookingsLambdaTest {
  Mockery mockery = new Mockery();
  TestUpdateBookingsLambda updateBookingsLambda;
  Context mockContext;
  LambdaLogger mockLogger;
  LocalDate fakeCurrentDate;
  String fakeCurrentDateString;
  List<String> validDates;
  String apiGatewayBaseUrl;
  String updateBookingsExceptionMessage;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() {
    mockery = new Mockery();
    updateBookingsLambda = new TestUpdateBookingsLambda();
    updateBookingsLambda.setPageManager(mockery.mock(IPageManager.class));
    updateBookingsLambda.setBookingManager(mockery.mock(IBookingManager.class));

    // Set up the valid date range
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    fakeCurrentDateString = fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    updateBookingsLambda.setCurrentLocalDate(fakeCurrentDate);
    validDates = new ArrayList<>();
    validDates.add(fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    validDates.add(fakeCurrentDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    updateBookingsLambda.setValidDates(validDates);

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
    apiGatewayBaseUrl = "apiGatewayBaseUrl";

    // updateBookings is never called from apigateway - so the message does not
    // have a redirecturl
    updateBookingsExceptionMessage = "Apologies - something has gone wrong. Please try again.";
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test sublass with some overrides to facilitate testing
  public class TestUpdateBookingsLambda extends UpdateBookingsLambda {
    private IBookingManager bookingManager;
    private IPageManager pageManager;
    private LocalDate currentLocalDate;
    private List<String> validDates;

    public void setBookingManager(IBookingManager bookingManager) {
      this.bookingManager = bookingManager;
    }

    @Override
    protected IBookingManager getBookingManager(LambdaLogger logger) throws Exception {
      return bookingManager;
    }

    public void setPageManager(IPageManager pageManager) {
      this.pageManager = pageManager;
    }

    @Override
    protected IPageManager getPageManager(LambdaLogger logger) throws Exception {
      return pageManager;
    }

    public void setCurrentLocalDate(LocalDate localDate) {
      currentLocalDate = localDate;
    }

    @Override
    public LocalDate getCurrentLocalDate() {
      return currentLocalDate;
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
  public void testUpdateBookingsThrowsCorrectExceptionWhenPageManagerThrowsAce() throws Exception {

    doTestUpdateBookingsThrowsCorrectExceptionWhenPageManagerThrows(new AmazonClientException(
        "Grrr.."));
  }

  @Test
  public void testUpdateBookingsThrowsCorrectExceptionWhenPageManagerThrowsAse() throws Exception {

    doTestUpdateBookingsThrowsCorrectExceptionWhenPageManagerThrows(new AmazonServiceException(
        "Grrr.."));
  }

  @Test
  public void testUpdateBookingsThrowsCorrectExceptionWhenPageManagerThrowsSomethingElse()
      throws Exception {

    doTestUpdateBookingsThrowsCorrectExceptionWhenPageManagerThrows(new IllegalAccessException(
        "Grrr.."));
  }

  public void doTestUpdateBookingsThrowsCorrectExceptionWhenPageManagerThrows(Exception exception)
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(updateBookingsExceptionMessage);

    // Make updateBookings call throw.
    mockery.checking(new Expectations() {
      {
        oneOf(updateBookingsLambda.getPageManager(mockLogger)).refreshAllPages(with(anything()),
            with(aNonNull(String.class)));
        will(throwException(exception));
      }
    });

    // ACT
    UpdateBookingsLambdaRequest request = new UpdateBookingsLambdaRequest();
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);

    updateBookingsLambda.updateBookings(request, mockContext);
  }

  @Test
  public void testUpdateBookingsThrowsCorrectExceptionWhenBookingManagerThrowsAce()
      throws Exception {

    doTestUpdateBookingsThrowsCorrectExceptionWhenBookingManagerThrows(new AmazonClientException(
        "Grrr.."));
  }

  @Test
  public void testUpdateBookingsThrowsCorrectExceptionWhenBookingManagerThrowsAse()
      throws Exception {

    doTestUpdateBookingsThrowsCorrectExceptionWhenBookingManagerThrows(new AmazonServiceException(
        "Grrr.."));
  }

  @Test
  public void testUpdateBookingsThrowsCorrectExceptionWhenBookingManagerThrowsSomethingElse()
      throws Exception {

    doTestUpdateBookingsThrowsCorrectExceptionWhenBookingManagerThrows(new IllegalAccessException(
        "Grrr.."));
  }

  public void doTestUpdateBookingsThrowsCorrectExceptionWhenBookingManagerThrows(Exception exception)
      throws Exception {
    // N.B. The precise exceptions thrown by the lambdas are important as
    // they get matched by a regex in ApiGateway to be reported to the user.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(updateBookingsExceptionMessage);

    // Make updateBookings call throw.
    mockery.checking(new Expectations() {
      {
        ignoring(updateBookingsLambda.getPageManager(mockLogger));
        oneOf(updateBookingsLambda.getBookingManager(mockLogger)).deleteYesterdaysBookings();
        will(throwException(exception));
      }
    });

    // ACT
    UpdateBookingsLambdaRequest request = new UpdateBookingsLambdaRequest();
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);

    updateBookingsLambda.updateBookings(request, mockContext);
  }

  @Test
  public void testUpdateBookingsCorrectlyCallsTheManagers() throws Exception {

    // Test happy path for updateBookings: we verify the IPageManager
    // is asked to refresh all booking pages, and then the IBookingManager
    // is asked to delete the previous day's bookings.

    // ARRANGE
    final Sequence refreshSequence = mockery.sequence("refresh");
    mockery.checking(new Expectations() {
      {
        oneOf(updateBookingsLambda.getPageManager(mockLogger)).refreshAllPages(with(validDates),
            with(apiGatewayBaseUrl));
        inSequence(refreshSequence);

        oneOf(updateBookingsLambda.getBookingManager(mockLogger)).deleteYesterdaysBookings();
        inSequence(refreshSequence);
      }
    });

    // ACT
    UpdateBookingsLambdaRequest request = new UpdateBookingsLambdaRequest();
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);

    updateBookingsLambda.updateBookings(request, mockContext);
  }

  @Test
  public void testUpdateBookingsThrowsWhenApiGatewayIsNull() throws Exception {

    // If the request has a null apiGatewayBaseUrl, then we should throw

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(updateBookingsExceptionMessage);

    UpdateBookingsLambdaRequest request = new UpdateBookingsLambdaRequest();
    request.setApiGatewayBaseUrl(null);

    // ACT
    updateBookingsLambda.updateBookings(request, mockContext);
  }
}