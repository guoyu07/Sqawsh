/**
 * Copyright 2017 Robin Steel
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

import java.util.Optional;
import java.util.Set;

/**
 * Manages the lifecycle state of the bookings service.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class LifecycleManager implements ILifecycleManager {

  private Integer maxNumberOfBookingsPerDay = 100;
  protected IOptimisticPersister optimisticPersister;
  private LambdaLogger logger;
  private Boolean initialised = false;
  private String lifecycleItemName;

  @Override
  public final void initialise(LambdaLogger logger) throws Exception {
    this.logger = logger;
    lifecycleItemName = "LifecycleState";
    initialised = true;
  }

  @Override
  public void throwIfOperationInvalidForCurrentLifecycleState(boolean operationIsReadOnly,
      boolean isSquashServiceUserCall) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The lifecycle manager has not been initialised");
    }

    logger.log("Checking lifecycle state before accessing the database");
    if (!isSquashServiceUserCall) {
      // Rules-based bookings and backups/restores continue to work when service
      // is no longer ACTIVE - in case we later want to bring it back into
      // service.
      logger.log("This call is not from a service end-user - so it's valid.");
      return;
    }

    ImmutablePair<LifecycleState, Optional<String>> lifecycleState = getLifecycleState();

    if (lifecycleState.left.equals(LifecycleState.ACTIVE)) {
      // All operations valid in ACTIVE state.
      logger.log("Lifecycle state is ACTIVE so operation is valid.");
      return;
    }
    if (lifecycleState.left.equals(LifecycleState.READONLY)) {
      if (operationIsReadOnly) {
        logger.log("Lifecycle state is READONLY and we are only reading - so operation is valid.");
        return;
      } else {
        logger
            .log("Lifecycle state is READONLY and we are writing - so throwing as operation is invalid.");
        throw new Exception(
            "Cannot mutate bookings or rules - booking service is temporarily readonly whilst site maintenance is in progress");
      }
    }

    // All end-user operations invalid in RETIRED state.
    logger.log("Lifecycle state is RETIRED - so throwing as operation is invalid.");
    // Should always have forwarding url in RETIRED state - but guard it just in
    // case...
    throw new Exception(
        "Cannot access bookings or rules - there is an updated version of the booking service. Forwarding Url: "
            + lifecycleState.right.orElse("UrlNotPresent"));
  }

  @Override
  public void setLifecycleState(LifecycleState lifecycleState, Optional<String> newServiceUrl)
      throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The lifecycle manager has not been initialised");
    }

    if (lifecycleState.equals(LifecycleState.RETIRED)) {
      String message = null;
      if (!newServiceUrl.isPresent() || !newServiceUrl.get().startsWith("http")) {
        if (!newServiceUrl.isPresent()) {
          message = "Throwing exception as url to new service has not been provided when setting lifecycle state to RETIRED";
        } else if (!newServiceUrl.get().startsWith("http")) {
          // Insist on correct format for the forwarding url.
          message = "Throwing exception as url to new service when setting lifecycle state to RETIRED does not start with http";
        }
        logger.log(message);
        throw new Exception(
            "Must provide valid url to new service when setting lifecycle state to RETIRED");
      }
    }

    // Get attribute - so can set version correctly. We assume only one person
    // (i.e. the site admin) will ever be calling this at one time, but we
    // nevertheless supply the version - so we can use the optimistic persister.
    ImmutablePair<Optional<Integer>, Set<Attribute>> lifecycleStateItem = getOptimisticPersister()
        .get(lifecycleItemName);

    logger.log("About to set lifecycle state in database to: " + lifecycleState.name());
    ReplaceableAttribute lifecycleAttribute = new ReplaceableAttribute();
    lifecycleAttribute.setName("State");
    lifecycleAttribute.setValue(lifecycleState.name());
    lifecycleAttribute.setReplace(true);
    int newVersion = getOptimisticPersister().put(lifecycleItemName, lifecycleStateItem.left,
        lifecycleAttribute);
    logger.log("Updated lifecycle state in database to: " + lifecycleState.name());

    if (newServiceUrl.isPresent()) {
      logger.log("About to set lifecycle state url in database to: " + newServiceUrl.get());
      ReplaceableAttribute urlAttribute = new ReplaceableAttribute();
      urlAttribute.setName("Url");
      urlAttribute.setValue(newServiceUrl.get());
      urlAttribute.setReplace(true);
      getOptimisticPersister().put(lifecycleItemName, Optional.of(newVersion), urlAttribute);
      logger.log("Updated lifecycle state url in database to: " + newServiceUrl.get());
    }
  }

  @Override
  public ImmutablePair<LifecycleState, Optional<String>> getLifecycleState() throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The lifecycle manager has not been initialised");
    }

    ImmutablePair<Optional<Integer>, Set<Attribute>> lifecycleStateItem = getOptimisticPersister()
        .get(lifecycleItemName);
    Optional<Attribute> stateAttribute = lifecycleStateItem.right.stream()
        .filter(attribute -> attribute.getName().equals("State")).findFirst();
    Optional<Attribute> urlAttribute = lifecycleStateItem.right.stream()
        .filter(attribute -> attribute.getName().equals("Url")).findFirst();
    // If we've not set the lifecycle state yet, then we must be ACTIVE.
    LifecycleState state = stateAttribute.isPresent() ? Enum.valueOf(LifecycleState.class,
        stateAttribute.get().getValue()) : LifecycleState.ACTIVE;
    // Populate url only if in RETIRED state.
    Optional<String> url = (urlAttribute.isPresent() && state.equals(LifecycleState.RETIRED)) ? Optional
        .of(urlAttribute.get().getValue()) : Optional.empty();
    return new ImmutablePair<LifecycleState, Optional<String>>(state, url);
  }

  /**
   * Returns an optimistic persister.
   * @throws Exception 
   */
  protected IOptimisticPersister getOptimisticPersister() throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The lifecycle manager has not been initialised");
    }

    if (optimisticPersister == null) {
      optimisticPersister = new OptimisticPersister();
      optimisticPersister.initialise(maxNumberOfBookingsPerDay, logger);
    }

    return optimisticPersister;
  }
}