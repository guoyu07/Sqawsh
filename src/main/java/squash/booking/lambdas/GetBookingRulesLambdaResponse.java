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

package squash.booking.lambdas;

import squash.booking.lambdas.core.BookingRule;

import java.util.List;

/**
 * Response for the {@link GetBookingRulesLambda GetBookingRules} lambda function.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class GetBookingRulesLambdaResponse {
  List<BookingRule> bookingRules;

  /**
   *  Returns the {@link squash.booking.lambdas.core.BookingRule booking rules}.
   */
  public List<BookingRule> getBookingRules() {
    return bookingRules;
  }

  public void setBookingRules(List<BookingRule> bookingRules) {
    this.bookingRules = bookingRules;
  }
}