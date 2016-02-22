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

package applicationdriverlayer.pageobjects.squash.booking;

import static org.junit.Assert.fail;
import steps.hooks.SharedDriver;

import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.How;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import applicationdriverlayer.pageobjects.squash.SquashBasePage;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Page object to manage the main booking pages for each date.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class CourtAndTimeSlotChooserPage extends SquashBasePage<CourtAndTimeSlotChooserPage> {

  @FindBy(how = How.CLASS_NAME, className = "courtHeader")
  public List<WebElement> courtHeaders;

  @FindBy(how = How.CLASS_NAME, className = "timeHeader")
  public List<WebElement> timeSlotHeaders;

  @FindBy(how = How.CLASS_NAME, className = "timeLabel")
  public List<WebElement> timeSlotLabels;

  @FindBy(how = How.CLASS_NAME, className = "reservationButton")
  public List<WebElement> reservationButtons;

  @FindBy(how = How.CLASS_NAME, className = "cancellationButton")
  public List<WebElement> cancellationButtons;

  @FindBy(how = How.CLASS_NAME, className = "dateDropdown")
  public WebElement dateDropdown;

  @FindBy(how = How.ID, id = "goButton")
  public WebElement dateGoButton;

  @FindBy(how = How.ID, id = "pageGuid")
  public WebElement pageGuid;

  public CourtAndTimeSlotChooserPage(SharedDriver driver) {
    super(driver);
  }

  @Override
  protected String getCachedWebElementAndGuidKey() {
    String currentUrl = driver.getCurrentUrl();
    // Get last bit e.g. 2015-10-22.html or 2015-10-22[some guid].html and
    // remove [some guid].html
    String date = currentUrl.substring(currentUrl.lastIndexOf("/")).substring(1, 11);
    return CourtAndTimeSlotChooserPage.class.getSimpleName() + date;
  }

  @Override
  protected boolean isS3PageConsistent(boolean expectChangedPage) {
    // When this method is called, we will have a newly-loaded page from S3 -
    // and we just have to decide if it's the same as it will be once S3
    // reaches eventual-consistency.

    // Is there a guid for this page already?
    String guidKey = getCachedWebElementAndGuidKey();
    // Try to get the guid. Will be null if there is no guid.
    String guid = s3ConsistencyHelper.getGuid(guidKey);
    String pagesGuid = (String) ((JavascriptExecutor) driver).executeScript(
        "return document.getElementById('pageGuid').innerHTML", pageGuid);
    if (guid == null) {
      // First time we've been asked to judge consistency of the booking page
      // for this date in this scenario. If we're not expecting a changed page,
      // we decide it's consistent only if there are no bookings (since all
      // scenarios should begin with no bookings).
      // If we are expecting a changed page, we do the opposite and assume
      // consistent only if there are bookings (this can happen e.g. if we're
      // already at the page when the scenario starts, so we don't get here
      // till we've made a booking).
      if ((cancellationButtons.size() > 0) != expectChangedPage) {
        System.out
            .println("Saying not consistent as Guid is null but there are either cancellation buttons when not expecting a changed page or vice versa.");
        return false;
      }
      // We are consistent, so record the guid
      System.out
          .println("Guid is null and there are either cancellation buttons when not expecting a changed page or vice versa, so saying consistent, and updating guid to: "
              + pagesGuid);
      s3ConsistencyHelper.updateGuid(guidKey, pagesGuid);
      return true;
    }

    // We've seen this page before in this scenario, so we decide if consistent
    // based on whether we are expecting a change since we last saw the page.
    if (expectChangedPage != guid.equals(pagesGuid)) {
      System.out.println("Guid is: " + guid + " and member Guid is: " + pagesGuid
          + " and expectChangedPage is: " + expectChangedPage + " so saying consistent");
      // We are consistent
      if (expectChangedPage) {
        System.out.println("Updating guid to: " + pagesGuid);
        s3ConsistencyHelper.updateGuid(guidKey, pagesGuid);
      }
      return true;
    } else {
      // We are not consistent, no need to update the guid
      System.out.println("Guid is: " + guid + " and member Guid is: " + pagesGuid
          + " and expectChangedPage is: " + expectChangedPage + " so saying not consistent");
      return false;
    }
  }

  @Override
  protected Optional<String> getUrl() {
    try {
      return Optional.of(new URL(System.getProperty("SquashWebsiteBaseUrl")).toString());
    } catch (MalformedURLException e) {
      e.printStackTrace();
      fail("Exception thrown when constructing URL: " + e.getMessage());
    }

    // Should not get here
    return null;
  }

  @Override
  protected Optional<WebElement> updateCachedWebElement() {
    // Set this somewhat arbitrarily to an element that gets replaced on
    // redrawing and on navigation away from this page.
    WebElement cachedWebElement = timeSlotLabels.get(0);
    cachedWebElementHelper.put(getCachedWebElementAndGuidKey(), cachedWebElement);
    return Optional.of(cachedWebElement);
  }

  @Override
  protected void waitForLoadToComplete() {
    new WebDriverWait(driver, explicitWaitTimeoutSeconds).until(ExpectedConditions
        .visibilityOfAllElementsLocatedBy(By.className("bookingTable")));
    new WebDriverWait(driver, explicitWaitTimeoutSeconds).until(ExpectedConditions
        .visibilityOf(dateDropdown));
  }

  @Override
  protected void assertIsLoaded() {
    Assert.assertTrue("The CourtAndTimeSlotBooking page is not loaded (booking table not visible)",
        driver.findElement(By.className("bookingTable")).isDisplayed());
    Assert.assertTrue(
        "The CourtAndTimeSlotBooking page is not loaded (date selector dropdown not visible)",
        dateDropdown.isDisplayed());
  }

  public List<java.time.LocalTime> getAllPossibleBookingStartTimes() {

    // Use HashSet to avoid duplicate entries from right and left of screen
    HashSet<java.time.LocalTime> startTimes = new HashSet<java.time.LocalTime>();
    for (WebElement element : timeSlotLabels) {
      startTimes.add(java.time.LocalTime.parse(element.getText(),
          DateTimeFormatter.ofPattern("h:mm a")));
    }

    // Ensure we return the times in earlier-to-later order
    List<java.time.LocalTime> startTimesList = new ArrayList<java.time.LocalTime>();
    startTimesList.addAll(startTimes);
    Collections.sort(startTimesList,
        (java.time.LocalTime t1, java.time.LocalTime t2) -> t1.compareTo(t2));

    return startTimesList;
  }

  public List<java.time.LocalDate> getAllPossibleBookingDates() {

    Select dateSelect = new Select(dateDropdown);
    List<LocalDate> bookingDates = dateSelect
        .getOptions()
        .stream()
        .map(
            (option) -> {
              return java.time.LocalDate.parse(option.getText(),
                  DateTimeFormatter.ofPattern("EE, d MMM, yyyy"));
            }).collect(Collectors.toList());

    // Ensure we return the dates in earlier-to-later order
    Collections.sort(bookingDates, (LocalDate d1, LocalDate d2) -> d1.compareTo(d2));

    return bookingDates;
  }

  public boolean isCourtBookedAtTime(Integer court, java.time.LocalTime startTime) {

    Collection<LocalTime> bookedTimes = getBookedStartTimes().get(court);
    return bookedTimes.contains(startTime);
  }

  public boolean isStartTimeValid(java.time.LocalTime startTime) {

    List<java.time.LocalTime> possibleStartTimes = getAllPossibleBookingStartTimes();
    return possibleStartTimes.contains(startTime);
  }

  private java.time.LocalTime getStartTimeFromTimeSlot(Integer timeSlot,
      List<java.time.LocalTime> startTimes) {
    // This overload is provided purely to avoid getting start times repeatedly
    return startTimes.get(timeSlot - 1);
  }

  public boolean isCourtNumberValid(Integer court) {
    return Range.closed(1, 5).contains(court);
  }

  public void bookCourt(Integer court, java.time.LocalTime time, String player1, String player2,
      String password, boolean expectBookingToSucceed) throws Exception {

    // Find correct reservation button to book this court at this time
    List<java.time.LocalTime> startTimes = getAllPossibleBookingStartTimes();
    Optional<WebElement> reservationButton = reservationButtons
        .stream()
        .filter(
            (element) -> {
              java.time.LocalTime reservationTime = getStartTimeFromTimeSlot(
                  Integer.parseInt(element.getAttribute("data-time_slot")), startTimes);
              return (Integer.parseInt(element.getAttribute("data-court")) == court)
                  && (reservationTime.equals(time));
            }).findFirst();

    if (!reservationButton.isPresent()) {
      throw new Exception("Could not find matching reservation button");
    }

    // Click to open the reservation page
    reservationButton.get().click();
    CourtReservationPage reservationPage = new CourtReservationPage((SharedDriver) driver).get(
        true, getCachedWebElement(), Optional.empty());
    reservationPage.submitBookingDetails(player1, player2, password, expectBookingToSucceed);
  }

  public void cancelCourt(Integer court, LocalTime time, LocalDate date, String password,
      boolean expectCancellationToSucceed) throws Exception {

    selectDate(date);

    // Find correct cancellation button
    List<java.time.LocalTime> startTimes = getAllPossibleBookingStartTimes();
    Optional<WebElement> cancellationButton = cancellationButtons
        .stream()
        .filter(
            (element) -> {
              java.time.LocalTime reservationTime = getStartTimeFromTimeSlot(
                  Integer.parseInt(element.getAttribute("data-time_slot")), startTimes);
              return (Integer.parseInt(element.getAttribute("data-court")) == court)
                  && (reservationTime.equals(time));
            }).findFirst();

    if (!cancellationButton.isPresent()) {
      throw new Exception("Could not find matching cancellation button");
    }

    // Click to open the cancellation page
    cancellationButton.get().click();
    CourtCancellationPage cancellationPage = new CourtCancellationPage((SharedDriver) driver).get(
        true, getCachedWebElement(), Optional.empty());

    cancellationPage.submitCancellationDetails(password, expectCancellationToSucceed);
  }

  public Multimap<Integer, java.time.LocalTime> getBookedStartTimes() {

    // Iterate over cancellation buttons to find booked courts
    Multimap<Integer, java.time.LocalTime> bookedStartTimes = ArrayListMultimap.create();
    List<java.time.LocalTime> startTimes = getAllPossibleBookingStartTimes();
    for (WebElement element : cancellationButtons) {
      Integer court = Integer.parseInt(element.getAttribute("data-court"));
      java.time.LocalTime time = getStartTimeFromTimeSlot(
          Integer.parseInt(element.getAttribute("data-time_slot")), startTimes);

      bookedStartTimes.put(court, time);
    }
    return bookedStartTimes;
  }

  public java.time.LocalDate getDate() {
    // Read the date from the DateDropdown
    Select dateSelect = new Select(dateDropdown);
    // datePage will be like '2015-12-23.html'
    String datePage = dateSelect.getAllSelectedOptions().get(0).getAttribute("value");
    // Strip html suffix
    String trimmedDatePage = datePage.substring(0, datePage.indexOf(".html"));
    return java.time.LocalDate.parse(trimmedDatePage);
  }

  public CourtAndTimeSlotChooserPage selectDate(java.time.LocalDate date) {

    Select dateSelect = new Select(dateDropdown);
    if (java.time.LocalDate.parse(dateSelect.getFirstSelectedOption().getText(),
        DateTimeFormatter.ofPattern("EE, d MMM, yyyy")).equals(date)) {
      // Date is already selected so just return
      return this;
    }
    dateSelect.selectByValue(date.toString() + ".html");

    // Click Go if javascript is disabled (but not otherwise or might get
    // staleref exception)
    String javascriptEnabled = System.getProperty("WebDriverJavascriptEnabled");
    if (javascriptEnabled.equals(false)) {
      dateGoButton.click();
    }

    // Wait for booking page for new date to load fully
    return this.get(true, getCachedWebElement(), Optional.of(false));
  }
}