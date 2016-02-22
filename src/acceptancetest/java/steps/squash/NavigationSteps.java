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

import applicationdriverlayer.pageobjects.squash.booking.CourtAndTimeSlotChooserPage;
import cucumber.api.java8.En;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Steps related to page navigation.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class NavigationSteps implements En {

  CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage;

  public NavigationSteps(CourtAndTimeSlotChooserPage courtAndTimeSlotChooserPage) {
    this.courtAndTimeSlotChooserPage = courtAndTimeSlotChooserPage;

    Given("I have navigated to the squash booking page", () -> {
      if (!this.courtAndTimeSlotChooserPage.isLoaded()) {
        this.courtAndTimeSlotChooserPage.get(Optional.empty());
      }
      LocalDate date = java.time.LocalDate.now();
      if (!this.courtAndTimeSlotChooserPage.getDate().equals(date)) {
        this.courtAndTimeSlotChooserPage.selectDate(date);
      }
    });

  }
}
