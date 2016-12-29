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

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface for reading, creating, and deleting attributes of a simpleDB item.
 *
 * Intended for implementation using Optimistic Concurrency Control.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public interface IOptimisticPersister {

  /**
   * Initialises the persister with the maximum number of attributes the item is allowed.
   */
  void initialise(int maxNumberOfAttributes, LambdaLogger logger) throws Exception;

  /**
   * Performs consistent read of all item's attributes.
   * 
   * @param itemName the name of the item to query
   * @return Pair with item's version and all the item's attributes.
   * @throws Exception when the read fails.
   */
  ImmutablePair<Optional<Integer>, Set<Attribute>> get(String itemName) throws Exception;

  /**
   * Performs consistent read of all items.
   * 
   * N.B. Think if the database query is paged (i.e. if there are many items), second and
   * subsequent pages will be eventually-consistent only.
   * 
   * @return list of pairs of item-names and the item's attributes.
   */
  List<ImmutablePair<String, List<Attribute>>> getAllItems();

  /**
   * Writes a new attribute to an item.
   * 
   * This performs the write only if the item's version attribute has the specified value.
   * 
   * @param itemName the name of the item to put the attribute to.
   * @param version the version of the item if the write is to proceed.
   * @param attribute the attribute to add to the item.
   * @return the version number of the item after the put.
   * @throws Exception when the put fails.
   */
  int put(String itemName, Optional<Integer> version, ReplaceableAttribute attribute)
      throws Exception;

  /**
   * Deletes an attribute from an item.
   * 
   * @param itemName the name of the item to delete the attribute from.
   * @param attribute the attribute to delete from the item.
   * @throws Exception when the delete fails.
   */
  void delete(String itemName, Attribute attribute) throws Exception;

  /**
   * Deletes all attributes from an item.
   * 
   * @param itemName the name of the item to delete the attributes from.
   */
  void deleteAllAttributes(String itemName);
}