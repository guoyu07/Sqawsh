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

import squash.booking.lambdas.core.Booking;
import squash.booking.lambdas.core.BookingRule;

import java.util.List;

/**
 * Response for the {@link BackupBookingsAndBookingRulesLambda BackupBookingsAndBookingRules} lambda function.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BackupBookingsAndBookingRulesLambdaResponse {
  List<Booking> bookings;
  List<BookingRule> bookingRules;
  Boolean clearBeforeRestore;

  /**
   *  Returns the list of all bookings from the database.
   */
  public List<Booking> getBookings() {
    return bookings;
  }

  public void setBookings(List<Booking> bookings) {
    this.bookings = bookings;
  }

  /**
   *  Returns the list of all booking rules from the database.
   */
  public List<BookingRule> getBookingRules() {
    return bookingRules;
  }

  public void setBookingRules(List<BookingRule> bookingRules) {
    this.bookingRules = bookingRules;
  }

  /**
   *  Returns whether to clear the database before restoring.
   *  
   *  This is included in the backup purely as an aid for restoring
   *  so the backup json in the lambda console can be pasted into
   *  the restore event without alteration.
   */
  public Boolean getClearBeforeRestore() {
    return clearBeforeRestore;
  }

  public void setClearBeforeRestore(Boolean clearBeforeRestore) {
    this.clearBeforeRestore = clearBeforeRestore;
  }
}