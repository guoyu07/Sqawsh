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

package squash.booking.lambdas.core;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Represents a court booking at a specified date and time with a specified name.
 *
 * <p>Bookings can be for:
 *    <ul>
 *     <li>a single court for a single time slot</li>
 *     <li>a contiguous block of courts over a contiguous block of time slots</li>
 *    </ul>
 * <p>Time slots are all of equal length, with each slot beginning when the previous slot finishes.
 *    For example:
 *    <ul>
 *     <li>slot 1 goes from 10 a.m. to 10.45 a.m.</li>
 *     <li>slot 2 goes from 10.45 a.m. to 11.30 a.m.</li>
 *     <li>...</li>
 *     <li>slot 16, the last of the day, goes from 9.15 p.m. to 10.00 p.m.</li>
 *    </ul>
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
@JsonPropertyOrder({ "date", "court", "courtSpan", "slot", "slotSpan", "name" })
public class Booking {
  Integer court;
  Integer courtSpan;
  Integer slot;
  Integer slotSpan;
  String date;
  String name;

  public Integer getCourt() {
    return court;
  }

  /**
   *  Sets the booked court, starting at 1.
   *  
   *  For a block booking, this is the first court of the block.
   */
  public void setCourt(Integer court) {
    this.court = court;
  }

  public Integer getCourtSpan() {
    return courtSpan;
  }

  /**
   *  Sets the number of courts per time slot in a block booking.
   */
  public void setCourtSpan(Integer courtSpan) {
    this.courtSpan = courtSpan;
  }

  public Integer getSlot() {
    return slot;
  }

  /**
   *  Sets the booked time slot, starting at 1.
   * 
   *  For a block booking, this is the first time slot of the block.
   */
  public void setSlot(Integer slot) {
    this.slot = slot;
  }

  public Integer getSlotSpan() {
    return slotSpan;
  }

  /**
   *  Sets the number of time slots per court in a block booking.
   */
  public void setSlotSpan(Integer slotSpan) {
    this.slotSpan = slotSpan;
  }

  public String getDate() {
    return date;
  }

  /**
  *  Sets the date of the booking, e.g. 2016-02-15 is 15th February 2016.
  */
  public void setDate(String date) {
    this.date = date;
  }

  public String getName() {
    return name;
  }

  /**
   *  Sets the booking name, e.g. A.Shabana/J.Power.
   */
  public void setName(String name) {
    this.name = name;
  }

  public Booking() {
    this.court = 3;
    this.courtSpan = 1;
    this.slot = 2;
    this.slotSpan = 1;
    this.name = "DefaultBooking";
  }

  /**
   * Copy constructor.
   */
  public Booking(Booking booking) {
    this.date = booking.getDate();
    this.court = booking.getCourt();
    this.courtSpan = booking.getCourtSpan();
    this.slot = booking.getSlot();
    this.slotSpan = booking.getSlotSpan();
    this.name = booking.getName();
  }

  /**
   * Constructs booking for the specified court and time with the specified name.
   *
   * @param court the 1-based number of the booked court.
   * @param slot the 1-based time slot of the booked court.
   * @param name the name of the booking, e.g. A.Shabana/J.Power
   */
  public Booking(Integer court, Integer slot, String name) {
    this.court = court;
    this.courtSpan = 1;
    this.slot = slot;
    this.slotSpan = 1;
    this.name = name;
  }

  /**
   * Constructs a block booking for the specified court and time spans with the specified name.
   *
   * @param court the 1-based number of the booked court.
   * @param courtSpan the number of courts per time slot
   * @param slot the 1-based time slot of the booked court.
   * @param slotSpan the number of time slots per court
   * @param name the name of the booking, e.g. Team Training
   */
  public Booking(Integer court, Integer courtSpan, Integer slot, Integer slotSpan, String name) {
    this.court = court;
    this.courtSpan = courtSpan;
    this.slot = slot;
    this.slotSpan = slotSpan;
    this.name = name;
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
        && Objects.equals(this.courtSpan, other.courtSpan) && Objects.equals(this.slot, other.slot)
        && Objects.equals(this.slotSpan, other.slotSpan) && Objects.equals(this.name, other.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.date, this.court, this.courtSpan, this.slot, this.slotSpan, this.name);
  }

  @Override
  public String toString() {
    return com.google.common.base.MoreObjects.toStringHelper(this).addValue(this.court)
        .addValue(this.courtSpan).addValue(this.slot).addValue(this.slotSpan).addValue(this.name)
        .addValue(this.date).toString();
  }
}