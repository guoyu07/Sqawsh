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

import squash.booking.lambdas.utils.Booking;
import squash.booking.lambdas.utils.IBackupManager;
import squash.booking.lambdas.utils.IBookingManager;
import squash.booking.lambdas.utils.IPageManager;

import org.hamcrest.CoreMatchers;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link PutDeleteBookingLambda PutDeleteBooking} lambda.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PutDeleteBookingLambdaTest {
  Mockery mockery = new Mockery();
  TestPutDeleteBookingLambda putDeleteBookingLambda;
  Context mockContext;
  LambdaLogger mockLogger;
  String player1Name;
  String player2Name;
  String playersNames;
  Integer court;
  Integer slot;
  Booking booking;
  List<Booking> bookings;
  String suffix;
  LocalDate fakeCurrentDate;
  String fakeCurrentDateString;
  List<String> validDates;
  String apiGatewayBaseUrl;
  String redirectUrl;
  String password;
  String genericExceptionMessage;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() throws IOException {
    mockery = new Mockery();
    putDeleteBookingLambda = new TestPutDeleteBookingLambda();
    putDeleteBookingLambda.setBackupManager(mockery.mock(IBackupManager.class));
    putDeleteBookingLambda.setPageManager(mockery.mock(IPageManager.class));
    putDeleteBookingLambda.setBookingManager(mockery.mock(IBookingManager.class));

    // Set up the valid date range
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    fakeCurrentDateString = fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    validDates = new ArrayList<>();
    validDates.add(fakeCurrentDateString);
    validDates.add(fakeCurrentDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    putDeleteBookingLambda.setCurrentLocalDate(fakeCurrentDate);
    putDeleteBookingLambda.setValidDates(validDates);

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

    // Set up some typical bookings data that the tests can use
    player1Name = "A.Playera";
    player2Name = "B.Playerb";
    playersNames = player1Name + "/" + player2Name;
    court = 5;
    slot = 3;
    booking = new Booking(court, slot, playersNames);
    bookings = new ArrayList<>();
    bookings.add(booking);
    suffix = "suffix";
    apiGatewayBaseUrl = "apiGatewayBaseUrl";
    redirectUrl = "redirectUrl.html";
    password = "pAssw0rd";

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
  public class TestPutDeleteBookingLambda extends PutDeleteBookingLambda {
    private IBackupManager backupManager;
    private IBookingManager bookingManager;
    private IPageManager pageManager;
    private LocalDate currentLocalDate;
    private List<String> validDates;

    public void setBackupManager(IBackupManager backupManager) {
      this.backupManager = backupManager;
    }

    @Override
    protected IBackupManager getBackupManager(LambdaLogger logger) throws Exception {
      return backupManager;
    }

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
  public void testCreateBookingThrowsIfCourtBelowValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        "0", // Invalid
        slot.toString(), playersNames, player1Name, player2Name, fakeCurrentDateString, password,
        apiGatewayBaseUrl, redirectUrl,
        "The booking court number is outside the valid range (1-5). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfCourtAboveValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        "6", // Invalid
        slot.toString(), playersNames, player1Name, player2Name, fakeCurrentDateString, password,
        apiGatewayBaseUrl, redirectUrl,
        "The booking court number is outside the valid range (1-5). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfTimeSlotBelowValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        court.toString(),
        "0", // Invalid
        playersNames, player1Name, player2Name, fakeCurrentDateString, password, apiGatewayBaseUrl,
        redirectUrl,
        "The booking time slot is outside the valid range (1-16). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfTimeSlotAboveValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        court.toString(),
        "17", // Invalid
        playersNames, player1Name, player2Name, fakeCurrentDateString, password, apiGatewayBaseUrl,
        redirectUrl,
        "The booking time slot is outside the valid range (1-16). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfPlayersNamesInWrongFormat_NoInitialPlayer1()
      throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        court.toString(),
        slot.toString(),
        "Playera/" + player2Name,
        "Playera", // Invalid - no initial for first player
        player2Name,
        fakeCurrentDateString,
        password,
        apiGatewayBaseUrl,
        redirectUrl,
        "The players names should have a format like J.Power i.e. Initial.Surname. Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfPlayersNamesInWrongFormat_NoInitialPlayer2()
      throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        court.toString(),
        slot.toString(),
        player1Name + "/Playerb",
        player1Name,
        "Playerb", // Invalid - no initial for second player
        fakeCurrentDateString,
        password,
        apiGatewayBaseUrl,
        redirectUrl,
        "The players names should have a format like J.Power i.e. Initial.Surname. Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfPlayersNamesInWrongFormat_NoPlayer1() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(), "/"
        + player2Name,
        "", // Invalid - no first player
        player2Name, fakeCurrentDateString, password, apiGatewayBaseUrl, redirectUrl,
        "Names of both players should be given. Please try again.redirectUrl", true);
  }

  @Test
  public void testCreateBookingThrowsIfPlayersNamesInWrongFormat_NoPlayer2() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(), player1Name
        + "/", player1Name,
        "", // Invalid - no second player
        fakeCurrentDateString, password, apiGatewayBaseUrl, redirectUrl,
        "Names of both players should be given. Please try again.redirectUrl", true);
  }

  @Test
  public void testCreateBookingThrowsIfDateOutsideValidRange() throws Exception {

    // Just have one test of this for now
    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(), playersNames,
        player1Name, player2Name,
        "2015-10-08", // Invalid - too far into the future
        password, apiGatewayBaseUrl, redirectUrl,
        "The booking date is outside the valid range. Please try again.redirectUrl", true);
  }

  @Test
  public void testCreateBookingThrowsIfPasswordIncorrect() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(), playersNames,
        player1Name, player2Name, fakeCurrentDateString,
        "pAssw0Rd", // Wrong password
        apiGatewayBaseUrl, redirectUrl, "The password is incorrect. Please try again.redirectUrl",
        true);
  }

  private void doTestPutDeleteBookingThrowsIfParameterInvalid(String court, String slot,
      String players, String player1name, String player2name, String date, String password,
      String apiGatewayBaseUrl, String redirectUrl, String message, boolean create)
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(message);

    // Try to mutate a booking for an invalid parameter - which should throw
    PutDeleteBookingLambdaRequest request = new PutDeleteBookingLambdaRequest();
    request.setPutOrDelete(create ? "PUT" : "DELETE");
    request.setCourt(court);
    request.setSlot(slot);
    request.setPlayers(players);
    request.setPlayer1name(player1name);
    request.setPlayer2name(player2name);
    request.setDate(date);
    request.setPassword(password);
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);
    request.setRedirectUrl(redirectUrl);

    // The booking manager should not be called
    mockery.checking(new Expectations() {
      {
        never(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(with(anything()));
        never(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(with(anything()));
      }
    });
    // The backup manager should not be called
    mockery.checking(new Expectations() {
      {
        never(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(
            with(anything()), with(anything()));
        never(putDeleteBookingLambda.getBackupManager(mockLogger)).backupAllBookings();
      }
    });

    // Act
    putDeleteBookingLambda.createOrDeleteBooking(request, mockContext);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheBookingManager() throws Exception {
    // Test createBooking makes the correct calls to the Booking Manager

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(
            with(equal(booking)));
        will(returnValue(bookings));
        // Not interested in PageManager or BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testCreateBookingCorrectlyCallsThePageManager() throws Exception {
    // Test createBooking makes the correct calls to the page manager - it
    // should refresh the modified bookings page.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        // The BookingManager returns the bookings that are passed to
        // refreshPage.
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(with(anything()));
        will(returnValue(bookings));
        oneOf(putDeleteBookingLambda.getPageManager(mockLogger)).refreshPage(fakeCurrentDateString,
            validDates, apiGatewayBaseUrl, true, bookings);
        will(returnValue(suffix));
        // Not interested in BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, true);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheBackupManager() throws Exception {
    // Test createBooking makes the correct calls to the backup manager - it
    // should backup each booking that is created or deleted.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(
            with(equal(booking)));
        will(returnValue(bookings));
        // Not interested in PageManager calls in this test
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        oneOf(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(booking,
            true);
      }
    });

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testCreateBookingThrowsWhenTheBookingManagerThrows() throws Exception {
    // Test createBooking throws when the Booking manager reports it has not
    // made the booking in the database, by throwing.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(
            with(any(Booking.class)));
        will(throwException(new Exception("Booking creation failed")));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage(genericExceptionMessage);

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testCreateBookingThrowsWhenThePageManagerThrows() throws Exception {
    // Test createBooking throws when the Page manager reports it has not
    // refreshed the page, by throwing.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        oneOf(putDeleteBookingLambda.getPageManager(mockLogger)).refreshPage(with(anything()),
            with(anything()), with(anything()), with(anything()), with(anything()));
        will(throwException(new Exception("Booking creation failed")));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage(genericExceptionMessage);

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testCreateBookingThrowsWhenTheBackupManagerThrows() throws Exception {
    // Test createBooking throws when the Backup manager throws.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        oneOf(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(
            with(anything()), with(anything()));
        will(throwException(new Exception("Booking backup failed")));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage(genericExceptionMessage);

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  private void doTestCreateBooking(String date, String attributeName, String playersNames,
      String player1Name, String player2Name, String court, String slot, String password,
      String apiGatewayBaseUrl, Boolean checkRedirectUrl) throws Exception {

    // ACT
    // Call create booking with valid parameters
    PutDeleteBookingLambdaRequest request = new PutDeleteBookingLambdaRequest();
    request.setPutOrDelete("PUT");
    request.setCourt(court);
    request.setSlot(slot);
    request.setPlayers(playersNames);
    request.setPlayer1name(player1Name);
    request.setPlayer2name(player2Name);
    request.setDate(date);
    request.setPassword(password);
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);
    request.setRedirectUrl(redirectUrl);
    PutDeleteBookingLambdaResponse response = putDeleteBookingLambda.createOrDeleteBooking(request,
        mockContext);
    if (checkRedirectUrl) {
      Assert.assertThat(response.getRedirectUrl(),
          CoreMatchers.startsWith(redirectUrl.replace(".html", "")));
      Assert.assertTrue("Redirect Url has not had uid suffix appended by createBooking", response
          .getRedirectUrl().length() > redirectUrl.length());
    }
  }

  @Test
  public void testDeleteBookingThrowsIfCourtBelowValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        "0", // Invalid
        slot.toString(), playersNames, player1Name, player2Name, fakeCurrentDateString, password,
        apiGatewayBaseUrl, redirectUrl,
        "The booking court number is outside the valid range (1-5)", false);
  }

  @Test
  public void testDeleteBookingThrowsIfCourtAboveValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        "6", // Invalid
        slot.toString(), playersNames, player1Name, player2Name, fakeCurrentDateString, password,
        apiGatewayBaseUrl, redirectUrl,
        "The booking court number is outside the valid range (1-5)", false);
  }

  @Test
  public void testDeleteBookingThrowsIfTimeSlotBelowValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(),
        "0", // Invalid
        playersNames, player1Name, player2Name, fakeCurrentDateString, password, apiGatewayBaseUrl,
        redirectUrl, "The booking time slot is outside the valid range (1-16)", false);
  }

  @Test
  public void testDeleteBookingThrowsIfTimeSlotAboveValidRange() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(),
        "17", // Invalid
        playersNames, player1Name, player2Name, fakeCurrentDateString, password, apiGatewayBaseUrl,
        redirectUrl, "The booking time slot is outside the valid range (1-16)", false);
  }

  @Test
  public void testDeleteBookingThrowsIfPlayersNamesInWrongFormat_NoInitialPlayer1()
      throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        court.toString(),
        slot.toString(),
        "Playera/B.Playerb", // Invalid - no initial for first player
        "Playera",
        player2Name,
        fakeCurrentDateString,
        password,
        apiGatewayBaseUrl,
        redirectUrl,
        "The players names should have a format like J.Power i.e. Initial.Surname. Please try again.redirectUrl",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfPlayersNamesInWrongFormat_NoInitialPlayer2()
      throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(
        court.toString(),
        slot.toString(),
        "A.Playera/Playerb", // Invalid - no initial for second player
        player1Name,
        "Playerb",
        fakeCurrentDateString,
        password,
        apiGatewayBaseUrl,
        redirectUrl,
        "The players names should have a format like J.Power i.e. Initial.Surname. Please try again.redirectUrl",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfPlayersNamesInWrongFormat_NoPlayer1() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(),
        "/B.Playerb", // Invalid - no first player
        "", "Playerb", fakeCurrentDateString, password, apiGatewayBaseUrl, redirectUrl,
        "Names of both players should be given. Please try again.redirectUrl", false);
  }

  @Test
  public void testDeleteBookingThrowsIfPlayersNamesInWrongFormat_NoPlayer2() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(),
        "A.Playera/", // Invalid - no second player
        player1Name, "", fakeCurrentDateString, password, apiGatewayBaseUrl, redirectUrl,
        "Names of both players should be given. Please try again.redirectUrl", false);
  }

  @Test
  public void testDeleteBookingThrowsIfDateOutsideValidRange() throws Exception {

    // Just have one test of this for now
    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(), playersNames,
        player1Name, player2Name,
        "2015-10-08", // Invalid - too far into the future,
        password, apiGatewayBaseUrl, redirectUrl, "The booking date is outside the valid range",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfPasswordIncorrect() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), slot.toString(), playersNames,
        player1Name, player2Name, fakeCurrentDateString, "pAssword", // Wrong
                                                                     // password
        apiGatewayBaseUrl, redirectUrl, "The password is incorrect", false);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheBookingManager() throws Exception {
    // Test deleteBooking makes the correct calls to the Booking Manager

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(
            with(equal(booking)));
        will(returnValue(bookings));
        // Not interested in PageManager or BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsThePageManager() throws Exception {
    // Test deleteBooking makes the correct calls to the page manager - it
    // should refresh the modified bookings page.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        // The BookingManager returns the bookings that are passed to
        // refreshPage.
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(with(anything()));
        will(returnValue(bookings));
        oneOf(putDeleteBookingLambda.getPageManager(mockLogger)).refreshPage(fakeCurrentDateString,
            validDates, apiGatewayBaseUrl, true, bookings);
        will(returnValue(suffix));
        // Not interested in BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, true);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheBackupManager() throws Exception {
    // Test deleteBooking makes the correct calls to the backup manager - it
    // should backup the booking just deleted.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        // The BookingManager returns the bookings that are passed to
        // refreshPage.
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(with(anything()));
        will(returnValue(bookings));
        // Not interested in PageManager calls in this test
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        oneOf(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(booking,
            false);
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testDeleteBookingThrowsWhenTheBookingManagerThrows() throws Exception {
    // Test deleteBooking throws when the Booking manager reports it has not
    // deleted the booking in the database, by throwing.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        oneOf(putDeleteBookingLambda.getPageManager(mockLogger)).refreshPage(with(anything()),
            with(anything()), with(anything()), with(anything()), with(anything()));
        will(throwException(new Exception("Booking deletion failed")));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage(genericExceptionMessage);

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testDeleteBookingThrowsWhenThePageManagerThrows() throws Exception {
    // Test deleteBooking throws when the Page manager reports it has not
    // refreshed the page, by throwing.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(
            with(any(Booking.class)));
        will(throwException(new Exception("Booking deletion failed")));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage(genericExceptionMessage);

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  @Test
  public void testDeleteBookingThrowsWhenTheBackupManagerThrows() throws Exception {
    // Test deleteBooking throws when the Backup manager throws.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(
            with(any(Booking.class)), with(any(Boolean.class)));
        will(throwException(new Exception("Booking backup failed")));
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage(genericExceptionMessage);

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, court.toString() + "-" + slot.toString(),
        playersNames, player1Name, player2Name, court.toString(), slot.toString(), password,
        apiGatewayBaseUrl, false);
  }

  private void doTestDeleteBooking(String date, String attributeName, String playersNames,
      String player1Name, String player2Name, String court, String slot, String password,
      String apiGatewayBaseUrl, Boolean checkRedirectUrl) throws Exception {

    // ACT
    // Call create booking with valid parameters
    PutDeleteBookingLambdaRequest request = new PutDeleteBookingLambdaRequest();
    request.setPutOrDelete("DELETE");
    request.setCourt(court);
    request.setSlot(slot);
    request.setPlayers(playersNames);
    // Deletion requests pass the players' names only in concatenated form
    request.setPlayer1name(null);
    request.setPlayer2name(null);
    request.setDate(date);
    request.setPassword(password);
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);
    request.setRedirectUrl(redirectUrl);
    PutDeleteBookingLambdaResponse response = putDeleteBookingLambda.createOrDeleteBooking(request,
        mockContext);
    if (checkRedirectUrl) {
      Assert.assertThat(response.getRedirectUrl(),
          CoreMatchers.startsWith(redirectUrl.replace(".html", "")));
      Assert.assertTrue("Redirect Url has not had uid suffix appended by deleteBooking", response
          .getRedirectUrl().length() > redirectUrl.length());
    }
  }
}