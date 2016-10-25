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

import squash.deployment.lambdas.utils.RetryHelper;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.services.simpledb.model.UpdateCondition;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages reading, creating, and deleting attributes of a simpleDB item.
 *
 * <p>This manages all interactions with SimpleDB items. SimpleDB holds all data
 * for bookings and booking rules.
 *
 * <p>We use optimistic concurrency control when mutating the database to ensure multiple
 * clients do not overwrite each other. Each item in the database has an associated version
 * number, and we employ a Read-Modify-Write pattern. A downside to this is that we open the
 * door to losing availability. See, e.g.:
 * http://www.allthingsdistributed.com/2010/02/strong_consistency_simpledb.html, and:
 * https://aws.amazon.com/blogs/aws/amazon-simpledb-consistency-enhancements.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class OptimisticPersister implements IOptimisticPersister {

  private String simpleDbDomainName;
  private String versionAttributeName;
  private Integer maxNumberOfAttributes;
  private Region region;
  private LambdaLogger logger;
  private Boolean initialised = false;

  @Override
  public final void initialise(int maxNumberOfAttributes, LambdaLogger logger) throws IOException {

    if (initialised) {
      throw new IllegalStateException("The optimistic persister has already been initialised");
    }

    this.logger = logger;
    simpleDbDomainName = getStringProperty("simpledbdomainname");
    versionAttributeName = "VersionNumber";
    this.maxNumberOfAttributes = maxNumberOfAttributes;
    region = Region.getRegion(Regions.fromName(getStringProperty("region")));
    initialised = true;
  }

  @Override
  public ImmutablePair<Optional<Integer>, Set<Attribute>> get(String itemName) {

    if (!initialised) {
      throw new IllegalStateException("The optimistic persister has not been initialised");
    }

    logger.log("About to get all active attributes from simpledb item: " + itemName);

    AmazonSimpleDB client = getSimpleDBClient();

    // Do a consistent read - to ensure we get correct version number
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(simpleDbDomainName, itemName);
    logger.log("Using simpleDB domain: " + simpleDbDomainName);

    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult result = client.getAttributes(simpleDBRequest);
    List<Attribute> attributes = result.getAttributes();

    // Get the version number and other attributes.
    Optional<Integer> version = Optional.empty();
    Set<Attribute> nonVersionAttributes = new HashSet<>();
    if (attributes.size() > 0) {
      // If we have any attributes, we'll always have a version number
      Attribute versionNumberAttribute = attributes.stream()
          .filter(attribute -> attribute.getName().equals(versionAttributeName)).findFirst().get();
      version = Optional.of(Integer.parseInt(versionNumberAttribute.getValue()));
      logger.log("Retrieved version number: " + versionNumberAttribute.getValue());
      attributes.remove(versionNumberAttribute);

      // Add all active attributes (i.e. those not pending deletion)
      nonVersionAttributes.addAll(attributes.stream()
          .filter(attribute -> !attribute.getValue().startsWith("Inactive"))
          .collect(Collectors.toSet()));
    }
    logger.log("Got all attributes from simpledb");

    return new ImmutablePair<>(version, nonVersionAttributes);
  }

  @Override
  public List<ImmutablePair<String, List<Attribute>>> getAllItems() {

    if (!initialised) {
      throw new IllegalStateException("The optimistic persister has not been initialised");
    }

    // Query database to get items
    List<ImmutablePair<String, List<Attribute>>> items = new ArrayList<>();
    AmazonSimpleDB client = getSimpleDBClient();

    SelectRequest selectRequest = new SelectRequest();
    // N.B. Think if results are paged, second and subsequent pages will always
    // be eventually-consistent only. This is currently used only to back up the
    // database - so being eventually-consistent is good enough - after all -
    // even if we were fully consistent, someone could still add a new booking
    // right after our call anyway.
    selectRequest.setConsistentRead(true);
    // Query all items in the domain
    selectRequest.setSelectExpression("select * from `" + simpleDbDomainName + "`");
    String nextToken = null;
    do {
      SelectResult selectResult = client.select(selectRequest);
      selectResult.getItems().forEach(
          item -> {
            List<Attribute> attributes = new ArrayList<>();
            item.getAttributes()
                .stream()
                // Do not return the version attribute or inactive attributes
                .filter(
                    attribute -> (!attribute.getName().equals(versionAttributeName) && !attribute
                        .getValue().startsWith("Inactive"))).forEach(attribute -> {
                  attributes.add(attribute);
                });
            items.add(new ImmutablePair<>(item.getName(), attributes));
          });
      nextToken = selectResult.getNextToken();
      selectRequest.setNextToken(nextToken);
    } while (nextToken != null);

    return items;
  }

  @Override
  public int put(String itemName, Optional<Integer> version, ReplaceableAttribute attribute)
      throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The optimistic persister has not been initialised");
    }

    logger.log("About to add attrbutes to simpledb item: " + itemName);

    AmazonSimpleDB client = getSimpleDBClient();

    // Check the put will not take us over the maximum number of attributes:
    // N.B. if (replace == true) then this check could be over-eager, but not
    // worth refining it, since this effectively just alters the limit by one.
    ImmutablePair<Optional<Integer>, Set<Attribute>> versionedAttributes = get(itemName);

    if (versionedAttributes.left.isPresent()) {
      logger.log("Retrieved versioned attributes(Count: " + versionedAttributes.right.size()
          + ")  have version number: " + versionedAttributes.left.get());
    } else {
      // There should be no attributes in this case.
      logger.log("Retrieved versioned attributes(Count: " + versionedAttributes.right.size()
          + ") have no version number");
    }

    Boolean tooManyAttributes = versionedAttributes.right.size() >= maxNumberOfAttributes;
    if (tooManyAttributes && !attribute.getValue().startsWith("Inactive")) {
      // We allow puts to inactivate attributes even when on the limit -
      // otherwise we could never delete when we're on the limit.
      logger.log("Cannot create attribute - the maximum number of attributes already exists ("
          + maxNumberOfAttributes
          + ") so throwing a 'Database put failed - too many attributes' exception");
      throw new Exception("Database put failed - too many attributes");
    }

    // Do a conditional put - so we don't overwrite someone else's attributes
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(versionAttributeName);
    ReplaceableAttribute versionAttribute = new ReplaceableAttribute();
    versionAttribute.setName(versionAttributeName);
    versionAttribute.setReplace(true);
    // Update will proceed unless the version number has changed
    if (version.isPresent()) {
      // A version number attribute exists - so it should be unchanged
      updateCondition.setValue(Integer.toString(version.get()));
      // Bump up our version number attribute
      versionAttribute.setValue(Integer.toString(version.get() + 1));
    } else {
      // A version number attribute did not exist - so it still should not
      updateCondition.setExists(false);
      // Set initial value for our version number attribute
      versionAttribute.setValue("0");
    }

    List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
    replaceableAttributes.add(versionAttribute);

    // Add the new attribute
    replaceableAttributes.add(attribute);

    PutAttributesRequest simpleDBPutRequest = new PutAttributesRequest(simpleDbDomainName,
        itemName, replaceableAttributes, updateCondition);

    try {
      client.putAttributes(simpleDBPutRequest);
    } catch (AmazonServiceException ase) {
      if (ase.getErrorCode().contains("ConditionalCheckFailed")) {
        // Someone else has mutated an attribute since we read them. For now,
        // assume this is rare and do not retry, just convert
        // this to a database put failed exception.
        logger.log("Caught AmazonServiceException for ConditionalCheckFailed whilst creating"
            + " attribute(s) so throwing as 'Database put failed' instead");
        throw new Exception("Database put failed - conditional check failed");
      }
      throw ase;
    }

    logger.log("Created attribute(s) in simpledb");

    return Integer.parseInt(versionAttribute.getValue());
  }

  @Override
  public void delete(String itemName, Attribute attribute) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The optimistic persister has not been initialised");
    }

    logger.log("About to delete attribute from simpledb item: " + itemName);

    AmazonSimpleDB client = getSimpleDBClient();

    // We retry the delete if necessary if we get a
    // ConditionalCheckFailed exception, i.e. if someone else modifies the
    // database between us reading and writing it.
    RetryHelper
        .DoWithRetries(() -> {
          try {
            // Get existing attributes (and version number), via consistent
            // read:
            ImmutablePair<Optional<Integer>, Set<Attribute>> versionedAttributes = get(itemName);

            if (!versionedAttributes.left.isPresent()) {
              logger
                  .log("A version number attribute did not exist - this means no attributes exist, so we have nothing to delete.");
              return null;
            }
            if (!versionedAttributes.right.contains(attribute)) {
              logger.log("The attribute did not exist - so we have nothing to delete.");
              return null;
            }

            // Since it seems impossible to update the version number while
            // deleting an attribute, we first mark the attribute as inactive,
            // and then delete it.
            ReplaceableAttribute inactiveAttribute = new ReplaceableAttribute();
            inactiveAttribute.setName(attribute.getName());
            inactiveAttribute.setValue("Inactive" + attribute.getValue());
            inactiveAttribute.setReplace(true);
            put(itemName, versionedAttributes.left, inactiveAttribute);

            // Now we can safely delete the attribute, as other readers will now
            // ignore it.
            UpdateCondition updateCondition = new UpdateCondition();
            updateCondition.setName(inactiveAttribute.getName());
            updateCondition.setValue(inactiveAttribute.getValue());
            // Update will proceed unless the attribute no longer exists
            updateCondition.setExists(true);

            Attribute attributeToDelete = new Attribute();
            attributeToDelete.setName(inactiveAttribute.getName());
            attributeToDelete.setValue(inactiveAttribute.getValue());
            List<Attribute> attributesToDelete = new ArrayList<>();
            attributesToDelete.add(attributeToDelete);
            DeleteAttributesRequest simpleDBDeleteRequest = new DeleteAttributesRequest(
                simpleDbDomainName, itemName, attributesToDelete, updateCondition);
            client.deleteAttributes(simpleDBDeleteRequest);
            logger.log("Deleted attribute from simpledb");
            return null;
          } catch (AmazonServiceException ase) {
            if (ase.getErrorCode().contains("AttributeDoesNotExist")) {
              // Case of trying to delete an attribute that no longer exists -
              // that's ok - it probably just means more than one person was
              // trying to delete the attribute at once. So swallow this
              // exception
              logger
                  .log("Caught AmazonServiceException for AttributeDoesNotExist whilst deleting attribute so"
                      + " swallowing and continuing");
              return null;
            } else {
              throw ase;
            }
          }
        }, Exception.class, Optional.of("Database put failed - conditional check failed"), logger);
  }

  @Override
  public void deleteAllAttributes(String itemName) {

    if (!initialised) {
      throw new IllegalStateException("The optimistic persister has not been initialised");
    }

    logger.log("About to delete all attributes from simpledb item: " + itemName);

    DeleteAttributesRequest deleteAttributesRequest = new DeleteAttributesRequest(
        simpleDbDomainName, itemName);
    AmazonSimpleDB client = getSimpleDBClient();
    client.deleteAttributes(deleteAttributesRequest);

    logger.log("Deleted all attributes from simpledb item.");
  }

  /**
   * Returns a named property from the SquashCustomResource settings file.
   */
  protected String getStringProperty(String propertyName) throws IOException {
    // Use a getter here so unit tests can substitute a mock value.
    // We get the value from a settings file so that
    // CloudFormation can substitute the actual value when the
    // stack is created, by replacing the settings file.

    Properties properties = new Properties();
    try (InputStream stream = RuleManager.class
        .getResourceAsStream("/squash/booking/lambdas/SquashCustomResource.settings")) {
      properties.load(stream);
    } catch (IOException e) {
      logger.log("Exception caught reading SquashCustomResource.settings properties file: "
          + e.getMessage());
      throw e;
    }
    return properties.getProperty(propertyName);
  }

  /**
   * Returns a SimpleDB database client.
   */
  protected AmazonSimpleDB getSimpleDBClient() {

    // Use a getter here so unit tests can substitute a mock client
    AmazonSimpleDB client = new AmazonSimpleDBClient();
    client.setRegion(region);
    return client;
  }
}