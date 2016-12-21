/**
 * Copyright 2016 Robin Steel
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

package steps.squash;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import steps.argumenttransforms.StepArgumentTransforms;
import steps.hooks.Booking;
import steps.hooks.BookingSet;
import applicationdriverlayer.pageobjects.squash.booking.CourtAndTimeSlotChooserPage;
import applicationdriverlayer.pageobjects.squash.booking.CourtCancellationPage;
import applicationdriverlayer.pageobjects.squash.booking.ErrorPage;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java8.En;

/**
 * Steps related to cancelling bookings.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class CancellingSteps implements En {

  private CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage;
  private ErrorPage errorPage;
  private CourtCancellationPage courtCancellationPage;
  private BookingSet bookingsPotentiallyMadeDuringScenario;
  private Scenario scenario;

  @Before
  public void before(Scenario scenario) {
    // Can use the Scenario object to output text and/or images to the html
    // report
    this.scenario = scenario;
  }

  public CancellingSteps(CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage,
      ErrorPage errorPage, CourtCancellationPage courtCancellationPage, BookingSet bookings) {
    this.courtAndTimeSlotChooserPage = courtAndTimeSlotChooserPage;
    this.courtCancellationPage = courtCancellationPage;
    bookingsPotentiallyMadeDuringScenario = bookings;
    When(
        "^I (attempt to |)cancel court (\\d) at (\\d{1,2}:\\d{1,2} (?:AM|PM)) today using password (.*)$",
        (String attemptOrNot, Integer court, String timeS, String password) -> {
          java.time.LocalTime time = new StepArgumentTransforms.LocalTimeConverter()
              .transform(timeS);

          attemptToCancelCourt(court, time, java.time.LocalDate.now(), "A.Shabana/J.Power",
              password, attemptOrNot.equals("attempt to ") ? false : true);
        });

    When(
        "^I (attempt to |)cancel court (\\d) at (\\d{1,2}:\\d{1,2} (?:AM|PM)) today for (.*)$",
        (String attemptOrNot, Integer court, String timeS, String name) -> {
          java.time.LocalTime time = new StepArgumentTransforms.LocalTimeConverter()
              .transform(timeS);

          attemptToCancelCourt(court, time, java.time.LocalDate.now(), name, "pAssw0rd",
              attemptOrNot.equals("attempt to ") ? false : true);
        });

    Then(
        "^I should (not |)receive feedback that the cancellation details were invalid$",
        (String receivedOrNot) -> {
          if (!receivedOrNot.equals("not ")) {
            assertTrue(
                "Expected to have received feedback that the cancellation details were invalid",
                this.courtCancellationPage.hasReceivedFeedbackOnInvalidCancellationDetails());
          } else {
            assertTrue(
                "Expected not to have received feedback that the cancellation details were invalid",
                !this.courtCancellationPage.hasReceivedFeedbackOnInvalidCancellationDetails());
          }
        });
  }

  private void attemptToCancelCourt(Integer court, java.time.LocalTime time,
      java.time.LocalDate date, String name, String password, boolean expectCancellationToSucceed) {
    assertTrue("Expect bookings page to be loaded before calling this method",
        this.courtAndTimeSlotChooserPage.isLoaded());
    assertTrue("Court number is not valid: " + court.toString(),
        this.courtAndTimeSlotChooserPage.isCourtNumberValid(court));
    assertTrue("Court start time is not valid: " + time.toString(),
        this.courtAndTimeSlotChooserPage.isStartTimeValid(time));

    if (!this.courtAndTimeSlotChooserPage.getDate().equals(date)) {
      this.courtAndTimeSlotChooserPage.selectDate(date);
    }

    assertTrue("Court " + court.toString() + " is not booked at " + time.toString(),
        this.courtAndTimeSlotChooserPage.isCourtBookedAtTime(court, time));

    try {
      this.courtAndTimeSlotChooserPage.cancelCourt(court, time, date, name, password,
          expectCancellationToSucceed);
    } catch (Exception e) {
      fail(e.getMessage());
    }

    // Note this cancellation so we don't attempt to remove it on scenario
    // teardown
    if (expectCancellationToSucceed) {
      bookingsPotentiallyMadeDuringScenario.remove(new Booking(name, court, time, date));
    }
  }
}
