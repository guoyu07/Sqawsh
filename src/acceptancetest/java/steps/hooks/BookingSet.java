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

import java.util.HashSet;

/**
 * Container for bookings made during a scenario.
 * 
 * <p>This class exists solely to keep PicoContainer happy by forcing it to use
 *    the default HashSet constructor. Otherwise it chooses the 'greediest' ctor
 *    to use i.e. the one with the most arguments - and fails saying it can't
 *    create an (abstract) collection.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingSet extends HashSet<Booking> {

  private static final long serialVersionUID = 1L;

  public BookingSet() {
    super(); // Force default HashSet ctor
  }
}