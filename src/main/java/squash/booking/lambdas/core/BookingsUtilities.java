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

import java.time.LocalDate;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Sundry utilities.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingsUtilities {

  /**
   * Returns the current London local date.
   */
  public static LocalDate getCurrentLocalDate() {
    // This gets the correct local date no matter what the user's device
    // system time may say it is, and no matter where in AWS we run. This
    // takes BST into account.
    return Calendar.getInstance().getTime().toInstant()
        .atZone(TimeZone.getTimeZone("Europe/London").toZoneId()).toLocalDate();
  }
}