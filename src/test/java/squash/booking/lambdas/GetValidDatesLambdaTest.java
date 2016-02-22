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

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link GetValidDatesLambda GetValidDates} lambda.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class GetValidDatesLambdaTest {
  TestGetValidDatesLambda getValidDatesLambda;
  LocalDate fakeCurrentDate;
  List<String> validDates;

  @Before
  public void beforeTest() throws IOException {
    getValidDatesLambda = new TestGetValidDatesLambda();
    // Set up the valid date range
    fakeCurrentDate = LocalDate.of(2015, 10, 6);
    getValidDatesLambda.setCurrentLocalDate(fakeCurrentDate);
    getValidDatesLambda.setBookingWindowLengthInDays(2);

    validDates = new ArrayList<>();
    validDates.add(fakeCurrentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    validDates.add(fakeCurrentDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
  }

  // Define a test sublass with some overrides to facilitate testing
  public class TestGetValidDatesLambda extends GetValidDatesLambda {
    private LocalDate currentLocalDate;
    private Integer bookingWindowLengthInDays;

    public void setCurrentLocalDate(LocalDate localDate) {
      currentLocalDate = localDate;
    }

    @Override
    public LocalDate getCurrentLocalDate() {
      return currentLocalDate;
    }

    public void setBookingWindowLengthInDays(Integer bookingWindowLengthInDays) {
      this.bookingWindowLengthInDays = bookingWindowLengthInDays;
    }

    @Override
    public Integer getBookingWindowLengthInDays() {
      return bookingWindowLengthInDays;
    }
  }

  @Test
  public void testGetValidDatesReturnsCorrectDateRangeTwoDays() {

    // ARRANGE
    List<String> expectedDates = validDates;

    // ACT
    GetValidDatesLambdaRequest request = new GetValidDatesLambdaRequest();
    GetValidDatesLambdaResponse response = getValidDatesLambda.getValidDates(request);
    List<String> actualDates = response.getDates();

    // ASSERT
    for (String expectedDate : expectedDates) {
      assertTrue("Expected " + expectedDate, actualDates.contains(expectedDate));
      actualDates.removeIf(date -> date.equals(expectedDate));
    }
    assertTrue("More dates than expected were returned", actualDates.size() == 0);
  }

  @Test
  public void testGetValidDatesReturnsCorrectDateRangeInLeapYears() {
    // Just to sanity-check that the default fake date and 2-day
    // valid range are not hard-coded into production code...
    // And to verify correct working for a leap year.

    // ARRANGE
    // Choose a leap year
    getValidDatesLambda.setCurrentLocalDate(LocalDate.of(2016, 02, 28));
    getValidDatesLambda.setBookingWindowLengthInDays(3);
    List<String> expectedDates = new ArrayList<>();
    expectedDates.add("2016-02-28");
    expectedDates.add("2016-02-29");
    expectedDates.add("2016-03-01");

    // ACT
    GetValidDatesLambdaRequest request = new GetValidDatesLambdaRequest();
    GetValidDatesLambdaResponse response = getValidDatesLambda.getValidDates(request);
    List<String> actualDates = response.getDates();

    // ASSERT
    for (String expectedDate : expectedDates) {
      assertTrue("Expected " + expectedDate, actualDates.contains(expectedDate));
      actualDates.removeIf(date -> date.equals(expectedDate));
    }
    assertTrue("More dates than expected were returned", actualDates.size() == 0);
  }

  @Test
  public void testGetValidDatesReturnsCorrectDateRangeInNonLeapYears() {
    // Just to sanity-check that the default fake date and 2-day
    // valid range are not hard-coded into production code...
    // And to verify correct working for a non-leap year.

    // ARRANGE
    // Choose a non-leap year
    getValidDatesLambda.setCurrentLocalDate(LocalDate.of(2015, 02, 28));
    // Set different number of days to value used in other tests (2)
    getValidDatesLambda.setBookingWindowLengthInDays(3);
    List<String> expectedDates = new ArrayList<>();
    expectedDates.add("2015-02-28");
    expectedDates.add("2015-03-01");
    expectedDates.add("2015-03-02");

    // ACT
    GetValidDatesLambdaRequest request = new GetValidDatesLambdaRequest();
    GetValidDatesLambdaResponse response = getValidDatesLambda.getValidDates(request);
    List<String> actualDates = response.getDates();

    // ASSERT
    for (String expectedDate : expectedDates) {
      assertTrue("Expected " + expectedDate, actualDates.contains(expectedDate));
      actualDates.removeIf(date -> date.equals(expectedDate));
    }
    assertTrue("More dates than expected were returned", actualDates.size() == 0);
  }
}