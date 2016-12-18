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

import squash.booking.lambdas.core.Booking;
import squash.booking.lambdas.core.IBackupManager;
import squash.booking.lambdas.core.IBookingManager;
import squash.booking.lambdas.core.IPageManager;

import org.hamcrest.CoreMatchers;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.ExpectedException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

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
  String name;
  Integer court;
  Integer courtSpan;
  Integer slot;
  Integer slotSpan;
  Booking booking;
  List<Booking> bookings;
  String suffix;
  LocalDate fakeCurrentDate;
  String fakeCurrentDateString;
  List<String> validDates;
  String apiGatewayBaseUrl;
  String revvingSuffix;
  String redirectUrl;
  String password;
  String genericExceptionMessage;

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() {
    mockery = new Mockery();
    revvingSuffix = "revvingSuffix";
    environmentVariables.set("RevvingSuffix", revvingSuffix);
    putDeleteBookingLambda = new TestPutDeleteBookingLambda();
    putDeleteBookingLambda.setBackupManager(mockery.mock(IBackupManager.class));
    putDeleteBookingLambda.setPageManager(mockery.mock(IPageManager.class));
    putDeleteBookingLambda.setBookingManager(mockery.mock(IBookingManager.class));
    putDeleteBookingLambda.setCognitoIdentityPoolId("poolId");

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
    name = "A.Playera/B.Playerb";
    court = 5;
    courtSpan = 1;
    slot = 3;
    slotSpan = 1;
    booking = new Booking(court, courtSpan, slot, slotSpan, name);
    booking.setDate(fakeCurrentDateString);
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

  // Define a test subclass with some overrides to facilitate testing
  public class TestPutDeleteBookingLambda extends PutDeleteBookingLambda {
    private IBackupManager backupManager;
    private IBookingManager bookingManager;
    private IPageManager pageManager;
    private LocalDate currentLocalDate;
    private List<String> validDates;
    private String cognitoIdentityPoolId;

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

    public void setCognitoIdentityPoolId(String cognitoIdentityPoolId) {
      this.cognitoIdentityPoolId = cognitoIdentityPoolId;
    }

    public String getCognitoIdentityPoolId() {
      return cognitoIdentityPoolId;
    }

    @Override
    public String getStringProperty(String propertyName, LambdaLogger logger) {
      if (propertyName.equals("cognitoidentitypoolid")) {
        return cognitoIdentityPoolId;
      }
      return null;
    }
  }

  @Test
  public void testCreateBookingThrowsIfCourtOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking court number is outside the valid range (1-5)",
        "The booking court number is outside the valid range (1-5). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfCourtSpanOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking court span is outside the valid range (1-(6-court))",
        "The booking court span is outside the valid range (1-(6-court)). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfTimeSlotOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking time slot is outside the valid range (1-16)",
        "The booking time slot is outside the valid range (1-16). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfTimeSlotSpanOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking time slot span is outside the valid range (1- (17 - slot))",
        "The booking time slot span is outside the valid range (1- (17 - slot)). Please try again.redirectUrl",
        true);
  }

  @Test
  public void testCreateBookingThrowsIfNameInvalid() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking name must have a valid format",
        "The booking name must have a valid format e.g. J.Power/A.Shabana. Please try again.redirectUrl",
        true);
  }

  private void doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
      String validationExceptionMessage, String tweakedExceptionMessage, Boolean create)
      throws Exception {
    // If booking validation fails, we should throw an appropriately tweaked
    // exception.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(tweakedExceptionMessage);

    // Try to mutate a booking when booking validation fails - which should
    // throw. The values set here are mainly arbitrary - important point is that
    // the booking validation throws.
    PutDeleteBookingLambdaRequest request = new PutDeleteBookingLambdaRequest();
    request.setPutOrDelete(create ? "PUT" : "DELETE");
    request.setCourt("1");
    request.setCourtSpan("2");
    request.setSlot("3");
    request.setSlotSpan("4");
    request.setName("Arbitrary");
    request.setDate(validDates.get(0));
    request.setPassword("pAssw0rd");
    request.setCognitoAuthenticationType("authenticated");
    request.setCognitoIdentityPoolId(putDeleteBookingLambda.getCognitoIdentityPoolId());
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);
    request.setRedirectUrl(redirectUrl);

    // Setup the booking manager validation call to throw
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(anything()));
        will(throwException(new Exception(validationExceptionMessage)));
        // Should not get as far as called create or delete
        never(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(with(anything()));
        never(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(with(anything()));
      }
    });
    // The backup manager should not be called
    mockery.checking(new Expectations() {
      {
        never(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(
            with(anything()), with(anything()));
        never(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBookingRule(
            with(anything()), with(anything()));
        never(putDeleteBookingLambda.getBackupManager(mockLogger))
            .backupAllBookingsAndBookingRules();
      }
    });

    // ACT
    putDeleteBookingLambda.createOrDeleteBooking(request, mockContext);
  }

  @Test
  public void testCreateBookingThrowsIfDateOutsideValidRange() throws Exception {

    // Just have one test of this for now
    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(),
        name,
        "2015-10-08", // Invalid - too far into the future
        password, "authenticated", putDeleteBookingLambda.getCognitoIdentityPoolId(),
        apiGatewayBaseUrl, redirectUrl,
        "The booking date is outside the valid range. Please try again.redirectUrl", true);
  }

  @Test
  public void testCreateBookingThrowsIfPasswordIncorrect() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), name, fakeCurrentDateString,
        "pAssw0Rd", // Wrong password
        "authenticated", putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl,
        redirectUrl, "The password is incorrect. Please try again.redirectUrl", true);
  }

  @Test
  public void testCreateBlockBookingThrowsIfNotAuthenticated() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), "2", slot.toString(), "2",
        name, fakeCurrentDateString, password,
        "unauthenticated", // Not authenticated
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, redirectUrl,
        "You must login to manage block bookings. Please try again.redirectUrl", true);
  }

  @Test
  public void testCreateBlockBookingThrowsIfAuthenticatedWithWrongCognitoPool() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), "2", slot.toString(), "2",
        name, fakeCurrentDateString, password,
        "authenticated", // Authenticated...
        "wrong identity pool", // ...but with wrong pool
        apiGatewayBaseUrl, redirectUrl,
        "You must login to manage block bookings. Please try again.redirectUrl", true);
  }

  @Test
  public void testCreateSingleBookingDoesNotThrowIfNotAuthenticated() throws Exception {

    // Lack of authentication should be irrelevant for non-block bookings

    // ARRANGE
    // Don't care about manager calls in this test
    mockery.checking(new Expectations() {
      {
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), "1", slot.toString(), "1",
        password, "unauthenticated", // Not
                                     // authenticated
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  @Test
  public void testCreateSingleBookingDoesNotThrowIfAuthenticatedWithWrongPool() throws Exception {

    // Lack of authentication should be irrelevant for non-block bookings

    // ARRANGE
    // Don't care about manager calls in this test
    mockery.checking(new Expectations() {
      {
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), "1", slot.toString(), "1",
        password, "authenticated", // Authenticated...
        "wrong identity pool", // ...but with wrong pool
        apiGatewayBaseUrl, false);
  }

  private void doTestPutDeleteBookingThrowsIfParameterInvalid(String court, String courtSpan,
      String slot, String slotSpan, String name, String date, String password,
      String cognitoAuthenticationType, String cognitoIdentityPoolId, String apiGatewayBaseUrl,
      String redirectUrl, String message, boolean create) throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(message);

    // Try to mutate a booking for an invalid parameter - which should throw
    PutDeleteBookingLambdaRequest request = new PutDeleteBookingLambdaRequest();
    request.setPutOrDelete(create ? "PUT" : "DELETE");
    request.setCourt(court);
    request.setCourtSpan(courtSpan);
    request.setSlot(slot);
    request.setSlotSpan(slotSpan);
    request.setName(name);
    request.setDate(date);
    request.setPassword(password);
    request.setCognitoAuthenticationType(cognitoAuthenticationType);
    request.setCognitoIdentityPoolId(cognitoIdentityPoolId);
    request.setApiGatewayBaseUrl(apiGatewayBaseUrl);
    request.setRedirectUrl(redirectUrl);

    // The booking manager should not be called
    mockery.checking(new Expectations() {
      {
        never(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(anything()));
        never(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(with(anything()));
        never(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(with(anything()));
      }
    });
    // The backup manager should not be called
    mockery.checking(new Expectations() {
      {
        never(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(
            with(anything()), with(anything()));
        never(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBookingRule(
            with(anything()), with(anything()));
        never(putDeleteBookingLambda.getBackupManager(mockLogger))
            .backupAllBookingsAndBookingRules();
      }
    });

    // Act
    putDeleteBookingLambda.createOrDeleteBooking(request, mockContext);
  }

  // Test we call the booking manager correctly, including for boundary cases
  @Test
  public void testCreateBookingCorrectlyCallsTheBookingManagerLowerBoundary() throws Exception {
    // Test for all values on their lower boundary
    doTestCreateBookingCorrectlyCallsTheBookingManager(1, 1, 1, 1);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheBookingManagerUpperBoundary() throws Exception {
    // Test for all values on their upper boundary
    doTestCreateBookingCorrectlyCallsTheBookingManager(5, 1, 16, 1);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheBookingManagerBlockBooking() throws Exception {
    // Test for genuine block booking i.e. spans > 1
    doTestCreateBookingCorrectlyCallsTheBookingManager(3, 2, 15, 2);
  }

  public void doTestCreateBookingCorrectlyCallsTheBookingManager(Integer court, Integer courtSpan,
      Integer slot, Integer slotSpan) throws Exception {
    // Test createBooking makes the correct calls to the Booking Manager

    // ARRANGE
    // Modify the test booking
    booking.setCourt(court);
    booking.setCourtSpan(courtSpan);
    booking.setSlot(slot);
    booking.setSlotSpan(slotSpan);
    bookings.clear();
    bookings.add(booking);
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(equal(booking)));
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(
            with(equal(booking)));
        will(returnValue(bookings));
        // Not interested in PageManager or BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  // Test we call the page manager correctly, including for boundary cases
  @Test
  public void testCreateBookingCorrectlyCallsThePageManagerLowerBoundary() throws Exception {
    // Test for all values on their lower boundary
    doTestCreateBookingCorrectlyCallsThePageManager(1, 1, 1, 1);
  }

  @Test
  public void testCreateBookingCorrectlyCallsThePageManagerUpperBoundary() throws Exception {
    // Test for all values on their upper boundary
    doTestCreateBookingCorrectlyCallsThePageManager(5, 1, 16, 1);
  }

  @Test
  public void testCreateBookingCorrectlyCallsThePageManagerBlockBooking() throws Exception {
    // Test for genuine block booking i.e. court and slot spans > 1
    doTestCreateBookingCorrectlyCallsThePageManager(3, 2, 15, 2);
  }

  public void doTestCreateBookingCorrectlyCallsThePageManager(Integer court, Integer courtSpan,
      Integer slot, Integer slotSpan) throws Exception {
    // Test createBooking makes the correct calls to the page manager - it
    // should refresh the modified bookings page.

    // ARRANGE
    // Set up a test booking
    // Modify the test booking
    booking.setCourt(court);
    booking.setCourtSpan(courtSpan);
    booking.setSlot(slot);
    booking.setSlotSpan(slotSpan);
    bookings.clear();
    bookings.add(booking);
    mockery.checking(new Expectations() {
      {
        // The BookingManager returns the bookings that are passed to
        // refreshPage.
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(anything()));
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(with(anything()));
        will(returnValue(bookings));
        oneOf(putDeleteBookingLambda.getPageManager(mockLogger)).refreshPage(fakeCurrentDateString,
            validDates, apiGatewayBaseUrl, true, bookings, revvingSuffix);
        will(returnValue(suffix));
        // Not interested in BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, true);
  }

  // Test we call the backup manager correctly, including for boundary cases
  @Test
  public void testCreateBookingCorrectlyCallsTheBackupManagerLowerBoundary() throws Exception {
    // Test for all values on their lower boundary
    doTestCreateBookingCorrectlyCallsTheBackupManager(1, 1, 1, 1);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheBackupManagerUpperBoundary() throws Exception {
    // Test for all values on their upper boundary
    doTestCreateBookingCorrectlyCallsTheBackupManager(5, 1, 16, 1);
  }

  @Test
  public void testCreateBookingCorrectlyCallsTheBackupManagerBlockBooking() throws Exception {
    // Test for genuine block booking i.e. court and slot spans > 1
    doTestCreateBookingCorrectlyCallsTheBackupManager(3, 2, 15, 2);
  }

  public void doTestCreateBookingCorrectlyCallsTheBackupManager(Integer court, Integer courtSpan,
      Integer slot, Integer slotSpan) throws Exception {
    // Test createBooking makes the correct calls to the backup manager - it
    // should backup each booking that is created or deleted.

    // ARRANGE
    // Set up a test booking// Modify the test booking
    booking.setCourt(court);
    booking.setCourtSpan(courtSpan);
    booking.setSlot(slot);
    booking.setSlotSpan(slotSpan);
    bookings.clear();
    bookings.add(booking);
    mockery.checking(new Expectations() {
      {
        allowing(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(anything()));
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
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  @Test
  public void testCreateBookingThrowsWhenTheBookingManagerThrows() throws Exception {
    // Test createBooking throws when the Booking manager reports it has not
    // made the booking in the database, by throwing.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        allowing(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(any(Booking.class)));
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).createBooking(
            with(any(Booking.class)));
        will(throwException(new Exception("Booking creation failed")));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking creation failed. Please try again." + redirectUrl);

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
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
            with(anything()), with(anything()), with(anything()), with(anything()),
            with(anything()));
        will(throwException(new Exception("Booking creation failed")));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking creation failed. Please try again." + redirectUrl);

    // ACT and ASSERT
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
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
    doTestCreateBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  private void doTestCreateBooking(String date, String name, String court, String courtSpan,
      String slot, String slotSpan, String password, String cognitoAuthenticationType,
      String cognitoIdentityPoolId, String apiGatewayBaseUrl, Boolean checkRedirectUrl)
      throws Exception {

    // ACT
    // Call create booking with valid parameters
    PutDeleteBookingLambdaRequest request = new PutDeleteBookingLambdaRequest();
    request.setPutOrDelete("PUT");
    request.setCourt(court);
    request.setCourtSpan(courtSpan);
    request.setSlot(slot);
    request.setSlotSpan(slotSpan);
    request.setName(name);
    request.setDate(date);
    request.setPassword(password);
    request.setCognitoAuthenticationType(cognitoAuthenticationType);
    request.setCognitoIdentityPoolId(cognitoIdentityPoolId);
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

  // Repeat tests for Deleting bookings:
  @Test
  public void testDeleteBookingThrowsIfCourtOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking court number is outside the valid range (1-5)",
        "The booking court number is outside the valid range (1-5). Please try again.redirectUrl",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfCourtSpanOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking court span is outside the valid range (1-(6-court))",
        "The booking court span is outside the valid range (1-(6-court)). Please try again.redirectUrl",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfTimeSlotOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking time slot is outside the valid range (1-16)",
        "The booking time slot is outside the valid range (1-16). Please try again.redirectUrl",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfTimeSlotSpanOutsideValidRange() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking time slot span is outside the valid range (1- (17 - slot))",
        "The booking time slot span is outside the valid range (1- (17 - slot)). Please try again.redirectUrl",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfNameInvalid() throws Exception {
    doTestCreateBookingTransformsExceptionIfBookingValidationThrows(
        "The booking name must have a valid format",
        "The booking name must have a valid format e.g. J.Power/A.Shabana. Please try again.redirectUrl",
        false);
  }

  @Test
  public void testDeleteBookingThrowsIfDateOutsideValidRange() throws Exception {

    // Just have one test of this for now
    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), name,
        "2015-10-08", // Invalid - too far into the future,
        password, "authenticated", putDeleteBookingLambda.getCognitoIdentityPoolId(),
        apiGatewayBaseUrl, redirectUrl, "The booking date is outside the valid range", false);
  }

  @Test
  public void testDeleteBookingThrowsIfPasswordIncorrect() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), name,
        fakeCurrentDateString,
        "pAssword", // Wrong
                    // password
        "authenticated", putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl,
        redirectUrl, "The password is incorrect", false);
  }

  @Test
  public void testDeleteBlockBookingThrowsIfNotAuthenticated() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), "2", slot.toString(), "2",
        name, fakeCurrentDateString, password,
        "unauthenticated", // Not authenticated
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, redirectUrl,
        "You must login to manage block bookings. Please try again.redirectUrl", true);
  }

  @Test
  public void testDeleteBlockBookingThrowsIfAuthenticatedWithWrongCognitoPool() throws Exception {

    doTestPutDeleteBookingThrowsIfParameterInvalid(court.toString(), "2", slot.toString(), "2",
        name, fakeCurrentDateString, password,
        "authenticated", // Authenticated...
        "wrong identity pool", // ...but with wrong pool
        apiGatewayBaseUrl, redirectUrl,
        "You must login to manage block bookings. Please try again.redirectUrl", true);
  }

  @Test
  public void testDeleteSingleBookingDoesNotThrowIfNotAuthenticated() throws Exception {

    // Lack of authentication should be irrelevant for non-block bookings

    // ARRANGE
    // Don't care about manager calls in this test
    mockery.checking(new Expectations() {
      {
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), "1", slot.toString(), "1",
        password, "unauthenticated", // Not
        // authenticated
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  @Test
  public void testDeleteSingleBookingDoesNotThrowIfAuthenticatedWithWrongPool() throws Exception {

    // Lack of authentication should be irrelevant for non-block bookings

    // ARRANGE
    // Don't care about manager calls in this test
    mockery.checking(new Expectations() {
      {
        ignoring(putDeleteBookingLambda.getBookingManager(mockLogger));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), "1", slot.toString(), "1",
        password, "authenticated", // Authenticated...
        "wrong identity pool", // ...but with wrong pool
        apiGatewayBaseUrl, false);
  }

  // Test we call the booking manager correctly, including for boundary cases
  @Test
  public void testDeleteBookingCorrectlyCallsTheBookingManagerLowerBoundary() throws Exception {
    // Test for all values on their lower boundary
    doTestDeleteBookingCorrectlyCallsTheBookingManager(1, 1, 1, 1);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheBookingManagerUpperBoundary() throws Exception {
    // Test for all values on their upper boundary
    doTestDeleteBookingCorrectlyCallsTheBookingManager(5, 1, 16, 1);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheBookingManagerBlockBooking() throws Exception {
    // Test for genuine block booking i.e. spans > 1
    doTestDeleteBookingCorrectlyCallsTheBookingManager(3, 2, 15, 2);
  }

  public void doTestDeleteBookingCorrectlyCallsTheBookingManager(Integer court, Integer courtSpan,
      Integer slot, Integer slotSpan) throws Exception {
    // Test deleteBooking makes the correct calls to the Booking Manager

    // ARRANGE
    // Modify the test booking
    booking.setCourt(court);
    booking.setCourtSpan(courtSpan);
    booking.setSlot(slot);
    booking.setSlotSpan(slotSpan);
    bookings.clear();
    bookings.add(booking);
    mockery.checking(new Expectations() {
      {
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(equal(booking)));
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(
            with(equal(booking)));
        will(returnValue(bookings));
        // Not interested in PageManager or BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  // Test we call the page manager correctly, including for boundary cases
  @Test
  public void testDeleteBookingCorrectlyCallsThePageManagerLowerBoundary() throws Exception {
    // Test for all values on their lower boundary
    doTestDeleteBookingCorrectlyCallsThePageManager(1, 1, 1, 1);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsThePageManagerUpperBoundary() throws Exception {
    // Test for all values on their upper boundary
    doTestDeleteBookingCorrectlyCallsThePageManager(5, 1, 16, 1);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsThePageManagerBlockBooking() throws Exception {
    // Test for genuine block booking i.e. court and slot spans > 1
    doTestDeleteBookingCorrectlyCallsThePageManager(3, 2, 15, 2);
  }

  public void doTestDeleteBookingCorrectlyCallsThePageManager(Integer court, Integer courtSpan,
      Integer slot, Integer slotSpan) throws Exception {
    // Test deleteBooking makes the correct calls to the page manager - it
    // should refresh the modified bookings page.

    // ARRANGE
    // Modify the test booking
    booking.setCourt(court);
    booking.setCourtSpan(courtSpan);
    booking.setSlot(slot);
    booking.setSlotSpan(slotSpan);
    bookings.clear();
    bookings.add(booking);
    mockery.checking(new Expectations() {
      {
        // The BookingManager returns the bookings that are passed to
        // refreshPage.
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(equal(booking)));
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(with(anything()));
        will(returnValue(bookings));
        oneOf(putDeleteBookingLambda.getPageManager(mockLogger)).refreshPage(fakeCurrentDateString,
            validDates, apiGatewayBaseUrl, true, bookings, revvingSuffix);
        will(returnValue(suffix));
        // Not interested in BackupManager calls in this test
        ignoring(putDeleteBookingLambda.getBackupManager(mockLogger));
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, true);
  }

  // Test we call the backup manager correctly, including for boundary cases
  @Test
  public void testDeleteBookingCorrectlyCallsTheBackupManagerLowerBoundary() throws Exception {
    // Test for all values on their lower boundary
    doTestDeleteBookingCorrectlyCallsTheBackupManager(1, 1, 1, 1);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheBackupManagerUpperBoundary() throws Exception {
    // Test for all values on their upper boundary
    doTestDeleteBookingCorrectlyCallsTheBackupManager(5, 1, 16, 1);
  }

  @Test
  public void testDeleteBookingCorrectlyCallsTheBackupManagerBlockBooking() throws Exception {
    // Test for genuine block booking i.e. court and slot spans > 1
    doTestDeleteBookingCorrectlyCallsTheBackupManager(3, 2, 15, 2);
  }

  public void doTestDeleteBookingCorrectlyCallsTheBackupManager(Integer court, Integer courtSpan,
      Integer slot, Integer slotSpan) throws Exception {
    // Test deleteBooking makes the correct calls to the backup manager - it
    // should backup the booking just deleted.

    // ARRANGE
    // Set up a test booking// Modify the test booking
    booking.setCourt(court);
    booking.setCourtSpan(courtSpan);
    booking.setSlot(slot);
    booking.setSlotSpan(slotSpan);
    bookings.clear();
    bookings.add(booking);
    mockery.checking(new Expectations() {
      {
        // The BookingManager returns the bookings that are passed to
        // refreshPage.
        allowing(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(equal(booking)));
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(with(anything()));
        will(returnValue(bookings));
        // Not interested in PageManager calls in this test
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
        oneOf(putDeleteBookingLambda.getBackupManager(mockLogger)).backupSingleBooking(booking,
            false);
      }
    });

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
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
            with(anything()), with(anything()), with(anything()), with(anything()),
            with(anything()));
        will(throwException(new Exception("Booking deletion failed")));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking cancellation failed. Please try again." + redirectUrl);

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  @Test
  public void testDeleteBookingThrowsWhenThePageManagerThrows() throws Exception {
    // Test deleteBooking throws when the Page manager reports it has not
    // refreshed the page, by throwing.

    // ARRANGE
    // Set up a test booking
    mockery.checking(new Expectations() {
      {
        allowing(putDeleteBookingLambda.getBookingManager(mockLogger)).validateBooking(
            with(any(Booking.class)));
        oneOf(putDeleteBookingLambda.getBookingManager(mockLogger)).deleteBooking(
            with(any(Booking.class)));
        will(throwException(new Exception("Booking deletion failed")));
        ignoring(putDeleteBookingLambda.getPageManager(mockLogger));
      }
    });

    thrown.expect(Exception.class);
    thrown.expectMessage("Booking cancellation failed. Please try again." + redirectUrl);

    // ACT and ASSERT
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
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
    doTestDeleteBooking(fakeCurrentDateString, name, court.toString(), courtSpan.toString(),
        slot.toString(), slotSpan.toString(), password, "authenticated",
        putDeleteBookingLambda.getCognitoIdentityPoolId(), apiGatewayBaseUrl, false);
  }

  private void doTestDeleteBooking(String date, String name, String court, String courtSpan,
      String slot, String slotSpan, String password, String cognitoAuthenticationType,
      String cognitoIdentityPoolId, String apiGatewayBaseUrl, Boolean checkRedirectUrl)
      throws Exception {

    // ACT
    // Call create booking with valid parameters
    PutDeleteBookingLambdaRequest request = new PutDeleteBookingLambdaRequest();
    request.setPutOrDelete("DELETE");
    request.setCourt(court);
    request.setCourtSpan(courtSpan);
    request.setSlot(slot);
    request.setSlotSpan(slotSpan);
    request.setName(name);
    request.setDate(date);
    request.setPassword(password);
    request.setCognitoAuthenticationType(cognitoAuthenticationType);
    request.setCognitoIdentityPoolId(cognitoIdentityPoolId);
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