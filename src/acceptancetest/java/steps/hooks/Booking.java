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
import java.util.Objects;

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
  String name;

  public Booking(String name, Integer court, LocalTime time, LocalDate date) {
    this.name = name;
    this.court = court;
    this.time = time;
    this.date = date;
  }

  public String getName() {
    return name;
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

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Booking other = (Booking) obj;

    return Objects.equals(this.date, other.date) && Objects.equals(this.court, other.court)
        && Objects.equals(this.time, other.time) && Objects.equals(this.name, other.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.date, this.court, this.time, this.name);
  }

  @Override
  public String toString() {
    return com.google.common.base.MoreObjects.toStringHelper(this).addValue(this.court)
        .addValue(this.time).addValue(this.name).addValue(this.date).toString();
  }
}