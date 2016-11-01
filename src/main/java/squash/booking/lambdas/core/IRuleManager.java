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

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for the booking rule manager.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IRuleManager {

  /**
   * Initialises the manager.
   */
  void initialise(IBookingManager bookingManager, LambdaLogger logger) throws IOException;

  /**
   * Creates a booking rule.
   * 
   * @return All booking rules, including the created rule.
   * @throws Exception when the rule creation fails.
   */
  Set<BookingRule> createRule(BookingRule bookingRuleToCreate) throws Exception;

  /**
   * Returns all booking rules.
   * @throws Exception when the rule get fails.
   */
  List<BookingRule> getRules() throws Exception;

  /**
   * Deletes a booking rule.
   * 
   * @throws Exception when the rule deletion fails.
   */
  void deleteRule(BookingRule bookingRuleToDelete) throws Exception;

  /**
   * Deletes all booking rules.
   * @throws Exception 
   */
  void deleteAllBookingRules() throws Exception;

  /**
   * Adds a booking rule exclusion.
   * 
   * @param dateToExclude the new date not to apply the rule on.
   * @param bookingRuleToAddExclusionTo the rule to add the exclusion to.
   * @return the update booking rule - empty if no change was necessary.
   * @throws Exception when adding the rule exclusion fails.
   */
  Optional<BookingRule> addRuleExclusion(String dateToExclude,
      BookingRule bookingRuleToAddExclusionTo) throws Exception;

  /**
   * Deletes a booking rule exclusion.
   * 
   * @param dateNotToExclude the date of the exclusion to delete.
   * @param bookingRuleToDeleteExclusionFrom the rule to delete the exclusion from.
   * @return the update booking rule - empty if no change was necessary.
   * @throws Exception when deleting the rule exclusion fails.
   */
  Optional<BookingRule> deleteRuleExclusion(String dateNotToExclude,
      BookingRule bookingRuleToDeleteExclusionFrom) throws Exception;

  /**
   * Applies all booking rules for the specified date
   * 
   * This will apply the rules (i.e. create the bookings in the database) for the date specified.
   * 
   * @param date the date on which to apply the rules.
   * @return the list of bookings that were created.
   * @throws Exception when the rule application fails.
   */
  List<Booking> applyRules(String date) throws Exception;
}