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

import java.util.List;

/**
 * Response for the {@link GetBookingsLambda GetBookings} lambda function.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class GetBookingsLambdaResponse {
  String date;
  List<Booking> bookings;
  String lifecycleState;
  String forwardingUrl;

  /**
   *  Returns the date to which the bookings correspond, in YYYY-MM-DD format.
   */
  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  /**
   *  Returns the {@link squash.booking.lambdas.core.Booking bookings} for the specified date.
   */
  public List<Booking> getBookings() {
    return bookings;
  }

  public void setBookings(List<Booking> bookings) {
    this.bookings = bookings;
  }

  public String getForwardingUrl() {
    return forwardingUrl;
  }

  /**
   * Sets the forwarding Url to the updated booking site - if in RETIRED lifecycle state.
   * 
   */
  public void setForwardingUrl(String forwardingUrl) {
    this.forwardingUrl = forwardingUrl;
  }

  public String getLifecycleState() {
    return lifecycleState;
  }

  /**
   * Sets the lifecycle state of the current booking site.
   * 
   */
  public void setLifecycleState(String lifecycleState) {
    this.lifecycleState = lifecycleState;
  }
}