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

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a booking.
 * 
 * <p>Used to keep track of bookings made by a scenario, so they can be
 *    removed in an after-hook.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class Booking {

  Integer court;
  LocalTime time;
  LocalDate date;

  public Booking(Integer court, LocalTime time, LocalDate date) {
    this.court = court;
    this.time = time;
    this.date = date;
  }

  public Integer getCourt() {
    return court;
  }

  public LocalTime getTime() {
    return time;
  }

  public LocalDate getDate() {
    return date;
  }

}