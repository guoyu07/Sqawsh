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

package squash.booking.lambdas.core;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a court booking rule.
 * 
 * Rules can be:
 * <ul>
 * <li>recurring - in which case they are applied at the same time every week, and can have an array of
 * dates on which to skip applying the rule.
 * <li>non-recurring - in which case they expire after their initial booking is made
 * </ul>
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
@JsonPropertyOrder({ "booking", "isRecurring", "datesToExclude" })
public class BookingRule {
  Booking booking;
  Boolean isRecurring;
  String[] datesToExclude;

  public Booking getBooking() {
    return booking;
  }

  /**
   *  Sets the booking.
   */
  public void setBooking(Booking booking) {
    this.booking = booking;
  }

  public Boolean getIsRecurring() {
    return isRecurring;
  }

  /**
  *  Sets whether the booking rule is recurring.
  *  
  */
  public void setIsRecurring(Boolean isRecurring) {
    this.isRecurring = isRecurring;
  }

  public String[] getDatesToExclude() {
    return datesToExclude;
  }

  /**
  *  Sets the dates on which the rule should not be applied.
  *  
  */
  public void setDatesToExclude(String[] datesToExclude) {
    this.datesToExclude = datesToExclude;
  }

  /**
   * Constructs a BookingRule.
   *
   * @param booking the initial booking to be made by this rule.
   * @param isRecurring whether this rule should be re-applied every week after the initial booking.
   * @param datesToExclude the dates on which to skip applying this rule.
   */
  public BookingRule(Booking booking, Boolean isRecurring, String[] datesToExclude) {
    this.booking = new Booking(booking);
    this.isRecurring = isRecurring;
    this.datesToExclude = datesToExclude.clone();
  }

  /**
   * Copy constructor.
   */
  public BookingRule(BookingRule bookingRule) {
    this.booking = new Booking(bookingRule.getBooking());
    this.isRecurring = bookingRule.getIsRecurring();
    this.datesToExclude = bookingRule.getDatesToExclude().clone();
  }

  public BookingRule() {
    // Required so AWS Lambda can construct a BookingRule.
    this.booking = new Booking();
    this.isRecurring = false;
    this.datesToExclude = new String[0];
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final BookingRule other = (BookingRule) obj;

    return Objects.equals(this.booking, other.booking)
        && Objects.equals(this.isRecurring, other.isRecurring)
        && Objects.deepEquals(this.datesToExclude, other.datesToExclude);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.booking, this.isRecurring, Arrays.hashCode(this.datesToExclude));
  }

  @Override
  public String toString() {
    return com.google.common.base.MoreObjects.toStringHelper(this).addValue(this.booking)
        .addValue(this.isRecurring).addValue(this.datesToExclude).toString();
  }
}