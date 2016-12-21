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

package steps.squash;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import steps.argumenttransforms.StepArgumentTransforms;
import steps.hooks.Booking;
import steps.hooks.BookingSet;
import applicationdriverlayer.pageobjects.squash.booking.CourtAndTimeSlotChooserPage;
import applicationdriverlayer.pageobjects.squash.booking.CourtReservationPage;
import applicationdriverlayer.pageobjects.squash.booking.ErrorPage;

import com.google.common.collect.Multimap;

import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java8.En;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Steps related to bookings.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingSteps implements En {

  private CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage;
  private ErrorPage errorPage;
  private CourtReservationPage courtReservationPage;
  private BookingSet bookingsPotentiallyMadeDuringScenario;
  private Scenario scenario;

  @Before
  public void before(Scenario scenario) {
    // Can use the Scenario object to output text and/or images to the html
    // report
    this.scenario = scenario;
  }

  public BookingSteps(CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage, ErrorPage errorPage,
      CourtReservationPage courtReservationPage, BookingSet bookings) {
    this.courtAndTimeSlotChooserPage = courtAndTimeSlotChooserPage;
    this.errorPage = errorPage;
    this.courtReservationPage = courtReservationPage;
    bookingsPotentiallyMadeDuringScenario = bookings;

    Then(
        "^court start times should be every (\\d{2}) minutes between (\\d{1,2}(?::\\d{2})? (?:AM|PM)) and (\\d{1,2}(?::\\d{2})? (?:AM|PM)) inclusive$",
        (String intervalMinutes, String tFirstS, String tLastS) -> {
          java.time.LocalTime tFirst = new StepArgumentTransforms.LocalTimeConverter()
              .transform(tFirstS);
          java.time.LocalTime tLast = new StepArgumentTransforms.LocalTimeConverter()
              .transform(tLastS);

          assertTrue("Expect bookings page to be loaded before calling this method",
              this.courtAndTimeSlotChooserPage.isLoaded());

          List<java.time.LocalTime> startTimes = this.courtAndTimeSlotChooserPage
              .getAllPossibleBookingStartTimes();

          // Check first start time
          assertTrue("Expected first start time to be at " + tFirst.toString() + " but got: "
              + startTimes.get(0), startTimes.get(0).toString().equals(tFirst.toString()));

          // Check each subsequent start time is <intervalMinutes> later
          Iterator<java.time.LocalTime> it = startTimes.iterator();
          java.time.LocalTime previousTime = it.next();
          while (it.hasNext()) {
            java.time.LocalTime currentTime = it.next();
            assertTrue("Court start times should be " + intervalMinutes
                + " minutes apart. Previous time: " + previousTime.toString() + " Next time: "
                + currentTime.toString(),
                previousTime.plusMinutes(Integer.parseInt(intervalMinutes)).equals(currentTime));
            previousTime = currentTime;
          }

          // Check last start time
          assertTrue("Expected last start time to be at " + tLast.toString() + " but got: "
              + startTimes.get(startTimes.size() - 1), startTimes.get(startTimes.size() - 1)
              .toString().equals(tLast.toString()));
        });

    Given("^I have viewed bookings for today$", () -> {
      viewBookingsForDate(java.time.LocalDate.now());
    });

    When("^I view bookings for today$", () -> {
      viewBookingsForDate(java.time.LocalDate.now());
    });

    When(
        "^I attempt to view bookings for the (earliest|most future) date$",
        (String earliestOrLatest) -> {
          List<LocalDate> bookingDates = this.courtAndTimeSlotChooserPage
              .getAllPossibleBookingDates();

          viewBookingsForDate(earliestOrLatest.equals("earliest") ? bookingDates.get(0)
              : bookingDates.get(bookingDates.size() - 1));
        });

    When("^I attempt to view bookings for a date (\\d+) days in the future$",
        (Integer daysAhead) -> {
          viewBookingsForDate(java.time.LocalDate.now().plusDays(daysAhead));
        });

    When("^I attempt to view bookings for a date (\\d+) day(?:s)? in the past$",
        (Integer daysAgo) -> {
          viewBookingsForDate(java.time.LocalDate.now().minusDays(daysAgo));
        });

    When(
        "^I (attempt to |)book court (\\d) at (\\d{1,2}:\\d{1,2} (?:AM|PM)) today using password (.*)$",
        (String attemptOrNot, Integer court, String timeS, String password) -> {
          java.time.LocalTime time = new StepArgumentTransforms.LocalTimeConverter()
              .transform(timeS);

          attemptToBookCourt(court, time, java.time.LocalDate.now(), "A.Shabana/J.Power", password,
              attemptOrNot.equals("attempt to ") ? false : true);
        });

    When(
        "^I (attempt to |)book court (\\d) at (\\d{1,2}:\\d{1,2} (?:AM|PM)) today for (.*)$",
        (String attemptOrNot, Integer court, String timeS, String name) -> {
          java.time.LocalTime time = new StepArgumentTransforms.LocalTimeConverter()
              .transform(timeS);

          attemptToBookCourt(court, time, java.time.LocalDate.now(), name, "pAssw0rd",
              attemptOrNot.equals("attempt to ") ? false : true);
        });

    Given(
        "^I have booked court (\\d) at (\\d{1,2}:\\d{1,2} (?:AM|PM)) today$",
        (Integer court, String timeS) -> {
          java.time.LocalTime time = new StepArgumentTransforms.LocalTimeConverter()
              .transform(timeS);

          attemptToBookCourt(court, time, java.time.LocalDate.now(), "A.Shabana/J.Power",
              "pAssw0rd", true);
        });

    When(
        "^I book court (\\d) at (\\d{1,2}:\\d{1,2} (?:AM|PM))( today|)$",
        (Integer court, String timeS, String todayOrNot) -> {
          java.time.LocalTime time = new StepArgumentTransforms.LocalTimeConverter()
              .transform(timeS);

          attemptToBookCourt(court, time, todayOrNot.equals(" today") ? java.time.LocalDate.now()
              : this.courtAndTimeSlotChooserPage.getDate(), "A.Shabana/J.Power", "pAssw0rd", true);
        });

    Then(
        "^court (\\d) (should(?:| not)?) be booked at (\\d{1,2}:\\d{1,2} (?:AM|PM))( today|)$",
        (Integer court, String shouldOrNot, String timeS, String todayOrNot) -> {
          java.time.LocalTime time = new StepArgumentTransforms.LocalTimeConverter()
              .transform(timeS);

          assertCourtBooked(court, time, todayOrNot.equals(" today") ? java.time.LocalDate.now()
              : this.courtAndTimeSlotChooserPage.getDate(), shouldOrNot);
        });

    Then(
        "^there should be (\\d{1,2}) booked court(?:s)?(?: today)?$",
        (Integer numBookedCourts) -> {
          LocalDate date = java.time.LocalDate.now();
          if (!this.courtAndTimeSlotChooserPage.getDate().equals(date)) {
            System.out.println("Dates unequal - so selecting date");
            this.courtAndTimeSlotChooserPage.selectDate(date);
          }

          Multimap<Integer, java.time.LocalTime> bookedTimes = this.courtAndTimeSlotChooserPage
              .getBookedStartTimes();
          assertTrue("Expected " + numBookedCourts.toString() + " booked courts, got "
              + bookedTimes.size() + " booked courts", (bookedTimes.size() == numBookedCourts));
        });

    Then("^I should be shown bookings for a date (\\d+) days in the future$",
        this::assertBookingsShownFor);

    Then(
        "^I should be taken to the (squash booking page|error page)$",
        (String pageName) -> {
          // Assert the expected page is loaded
          if (pageName.equals("squash booking page")) {
            assertTrue("Squash booking page should be fully loaded",
                this.courtAndTimeSlotChooserPage.isLoaded());
          } else {
            // HtmlUnit redirects immediately from the error page.
            String webDriverType = System.getProperty("WebDriverType");
            if (!webDriverType.equals("HtmlUnit")) {
              assertTrue("Squash error page should be fully loaded", this.errorPage.isLoaded());
            }
          }
        });

    Then("^I should be shown bookings for today$", this::assertBookingsShownForToday);

    Then(
        "^I should receive feedback that the booking details were invalid$",
        () -> {
          assertTrue("Expected to have received feedback that the booking details were invalid",
              this.courtReservationPage.hasReceivedFeedbackOnInvalidBookingDetails());
        });
  }

  private void assertBookingsShownFor(Integer daysAhead) {

    java.time.LocalDate expectedDate = java.time.LocalDate.now().plusDays(daysAhead);
    java.time.LocalDate actualDate = this.courtAndTimeSlotChooserPage.getDate();
    assertTrue("Expected displayed date to be " + expectedDate.toString() + " but got: "
        + actualDate.toString(), expectedDate.equals(actualDate));
  }

  private void assertBookingsShownForToday() {
    assertBookingsShownFor(0);
  }

  private void viewBookingsForDate(java.time.LocalDate date) {
    assertTrue("Expect bookings page to be loaded before calling this method",
        this.courtAndTimeSlotChooserPage.isLoaded());
    if (this.courtAndTimeSlotChooserPage.getDate().equals(date)) {
      return;
    }

    this.courtAndTimeSlotChooserPage.selectDate(date);
  }

  private void assertCourtBooked(Integer court, java.time.LocalTime time, java.time.LocalDate date,
      String shouldOrNot) {
    if (!this.courtAndTimeSlotChooserPage.isLoaded()) {
      this.courtAndTimeSlotChooserPage.get(Optional.empty());
    }
    if (!this.courtAndTimeSlotChooserPage.getDate().equals(date)) {
      this.courtAndTimeSlotChooserPage.selectDate(date);
    }

    Multimap<Integer, java.time.LocalTime> bookedTimes = this.courtAndTimeSlotChooserPage
        .getBookedStartTimes();
    if (shouldOrNot.equals("should")) {
      assertTrue("Expected court time to be booked: " + time.toString(),
          bookedTimes.containsEntry(court, time));
    } else {
      assertTrue("Expected court time to be unbooked: " + time.toString(),
          !bookedTimes.containsEntry(court, time));
    }
  }

  private void attemptToBookCourt(Integer court, java.time.LocalTime time,
      java.time.LocalDate date, String name, String password, boolean expectBookingToSucceed) {
    assertTrue("Expect bookings page to be loaded before calling this method",
        this.courtAndTimeSlotChooserPage.isLoaded());
    assertTrue("Court number is not valid: " + court.toString(),
        this.courtAndTimeSlotChooserPage.isCourtNumberValid(court));
    assertTrue("Court start time is not valid: " + time.toString(),
        this.courtAndTimeSlotChooserPage.isStartTimeValid(time));

    if (!this.courtAndTimeSlotChooserPage.getDate().equals(date)) {
      this.courtAndTimeSlotChooserPage.selectDate(date);
    }

    assertTrue("Court " + court.toString() + " is already booked at " + time.toString(),
        !this.courtAndTimeSlotChooserPage.isCourtBookedAtTime(court, time));

    try {
      this.courtAndTimeSlotChooserPage.bookCourt(court, time, name, password,
          expectBookingToSucceed);
    } catch (Exception e) {
      fail(e.getMessage());
    }

    // Note this attempted booking so can attempt to remove it on scenario
    // teardown
    if (expectBookingToSucceed) {
      bookingsPotentiallyMadeDuringScenario.add(new Booking(name, court, time, date));
    }
  }
}
