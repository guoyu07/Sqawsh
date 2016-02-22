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

package steps.hooks;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import applicationdriverlayer.pageobjects.squash.booking.CourtAndTimeSlotChooserPage;
import cucumber.api.java.After;
import cucumber.api.java.Before;

/**
 * Scenario hooks.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class Hooks {
  BookingSet bookingsPotentiallyMadeDuringScenario;
  CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage;

  public Hooks(CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage, BookingSet bookings) {
    this.courtAndTimeSlotChooserPage = courtAndTimeSlotChooserPage;
    bookingsPotentiallyMadeDuringScenario = bookings;
  }

  @Before
  public void beforeScenario() {
    System.setProperty("webdriver.chrome.driver", "/Users/Rob/Documents/Home/chromedriver");

    // Clear out our list of bookings. This is used so scenario teardown can
    // remove any bookings made by the scenario - so each scenario starts
    // cleanly with no bookings.
    bookingsPotentiallyMadeDuringScenario.clear();
  }

  @After
  public void afterScenario() throws Exception {
    // Remove any bookings made by the scenario - so next scenario can start
    // with a 'clean slate'.
    assertTrue("Expect bookings page to be loaded before calling this method",
        courtAndTimeSlotChooserPage.isLoaded());

    BookingSet bookingsThatFailedToCancel = new BookingSet();
    bookingsPotentiallyMadeDuringScenario.stream().forEach(
        b -> {
          try {
            courtAndTimeSlotChooserPage.cancelCourt(b.getCourt(), b.getTime(), b.getDate(),
                "pAssw0rd", true);
          } catch (Exception e) {
            e.printStackTrace();
            // Note error but continue - we'll fail later
            bookingsThatFailedToCancel.add(b);
          }
        });
    if (!bookingsThatFailedToCancel.isEmpty()) {
      fail("Error cancelling bookings in scenario teardown");
    }
  }
}