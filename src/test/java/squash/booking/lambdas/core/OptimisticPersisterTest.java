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

import static org.junit.Assert.assertTrue;

import squash.booking.lambdas.core.OptimisticPersister;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.services.simpledb.model.UpdateCondition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tests the {@link OptimisticPersister}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class OptimisticPersisterTest {
  squash.booking.lambdas.core.OptimisticPersisterTest.TestOptimisticPersister optimisticPersister;

  String testItemName = "itemName";
  String testSimpleDBDomainName = "testSimpleDbDomainName";

  // Some database attributes for testing
  Set<Attribute> allAttributes;
  Set<Attribute> nonVersionAttributes;
  Set<Attribute> activeNonVersionAttributes;
  int testVersionNumber = 42; // Arbitrary
  String versionAttributeName = "VersionNumber";

  // Mocks
  Mockery mockery = new Mockery();
  LambdaLogger mockLogger;
  AmazonSimpleDB mockSimpleDBClient;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() {

    optimisticPersister = new TestOptimisticPersister();

    // Set up the mocks
    mockLogger = mockery.mock(LambdaLogger.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockLogger);
      }
    });
    mockSimpleDBClient = mockery.mock(AmazonSimpleDB.class);

    optimisticPersister.setSimpleDBClient(mockSimpleDBClient);

    // Set up some typical test attributes
    allAttributes = new HashSet<>();
    nonVersionAttributes = new HashSet<>();
    activeNonVersionAttributes = new HashSet<>();
    Attribute versionAttribute = new Attribute();
    versionAttribute.setName(versionAttributeName);
    versionAttribute.setValue(Integer.toString(testVersionNumber));
    Attribute activeAttribute = new Attribute();
    activeAttribute.setName("ActiveAttribute");
    activeAttribute.setValue("Active");
    Attribute inactiveAttribute = new Attribute();
    inactiveAttribute.setName("InactiveAttribute");
    inactiveAttribute.setValue("Inactive");
    allAttributes.add(versionAttribute);
    allAttributes.add(activeAttribute);
    allAttributes.add(inactiveAttribute);
    nonVersionAttributes.add(activeAttribute);
    nonVersionAttributes.add(inactiveAttribute);
    activeNonVersionAttributes.add(activeAttribute);
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  private void initialiseOptimisticPersister() throws IOException {
    optimisticPersister.initialise(42, mockLogger);
  }

  // Define a test optimistic persister with some overrides to facilitate
  // testing
  public class TestOptimisticPersister extends OptimisticPersister {
    private AmazonSimpleDB simpleDBClient;

    public void setSimpleDBClient(AmazonSimpleDB simpleDBClient) {
      this.simpleDBClient = simpleDBClient;
    }

    @Override
    public AmazonSimpleDB getSimpleDBClient() {
      return simpleDBClient;
    }

    @Override
    protected String getStringProperty(String propertyName) throws IOException {
      if (propertyName.equals("simpledbdomainname")) {
        return testSimpleDBDomainName;
      }
      if (propertyName.equals("region")) {
        return "eu-west-1";
      }
      return null;
    }
  }

  @Test
  public void testInitialiseThrowsWhenOptimisticPersisterAlreadyInitialised() throws IOException {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The optimistic persister has already been initialised");

    int maxNumberOfAttributes = 32; // Arbitrary
    optimisticPersister.initialise(maxNumberOfAttributes, mockLogger);

    // ACT
    // A second initialise call should throw
    optimisticPersister.initialise(maxNumberOfAttributes, mockLogger);
  }

  @Test
  public void testGetThrowsWhenOptimisticPersisterUninitialised() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The optimistic persister has not been initialised");

    // ACT
    // Do not initialise the optimistic persister first - so get should throw
    optimisticPersister.get(testItemName);
  }

  @Test
  public void testGetCorrectlyCallsSimpleDB() throws Exception {

    // ARRANGE
    initialiseOptimisticPersister();
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);

    GetAttributesResult getAttributesResult = new GetAttributesResult();
    getAttributesResult.setAttributes(allAttributes);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // ACT
    optimisticPersister.get(testItemName);
  }

  @Test
  public void testGetReturnsTheCorrectVersionNumberAndAttributes() throws Exception {
    // Get should not return the version-number attribute (but should return the
    // version number as part of its result pair) and it should not return
    // inactive attributes.

    // ARRANGE
    initialiseOptimisticPersister();
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);

    // First assert that our allAttribute set has both version and inactive
    // attributes (which should get filtered out).
    assertTrue("AllAttribute list should contain an inactive attribute", allAttributes.stream()
        .filter(attribute -> attribute.getValue().startsWith("Inactive")).count() > 0);
    assertTrue("AllAttribute list should contain a version number attribute", allAttributes
        .stream().filter(attribute -> attribute.getName().equals(versionAttributeName)).count() > 0);

    GetAttributesResult getAttributesResult = new GetAttributesResult();
    getAttributesResult.setAttributes(allAttributes);
    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // ACT
    ImmutablePair<Optional<Integer>, Set<Attribute>> result = optimisticPersister.get(testItemName);

    // ASSERT
    assertTrue("OptimisticPersister should return the correct attributes. Actual: " + result.right
        + ", Expected: " + activeNonVersionAttributes,
        result.right.equals(activeNonVersionAttributes));
    assertTrue("OptimisticPersister should return a version number", result.left.isPresent());
    assertTrue("OptimisticPersister should return the correct version number", result.left.get()
        .equals(testVersionNumber));
  }

  @Test
  public void testGetReturnsAnEmptyVersionNumberAndNoAttributesWhenThereAreNoAttributesInTheDatabase()
      throws Exception {

    // ARRANGE
    initialiseOptimisticPersister();
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);

    // First assert that our allAttribute set has both version and inactive
    // attributes (which should get filtered out).
    assertTrue("AllAttribute list should contain an inactive attribute", allAttributes.stream()
        .filter(attribute -> attribute.getValue().startsWith("Inactive")).count() > 0);
    assertTrue("AllAttribute list should contain a version number attribute", allAttributes
        .stream().filter(attribute -> attribute.getName().equals(versionAttributeName)).count() > 0);

    // Mimic database with no attributes
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // ACT
    ImmutablePair<Optional<Integer>, Set<Attribute>> result = optimisticPersister.get(testItemName);

    // ASSERT
    assertTrue("OptimisticPersister should return no attributes", result.right.size() == 0);
    assertTrue("OptimisticPersister should return an empty version number",
        !result.left.isPresent());
  }

  @Test
  public void testGetAllItemsThrowsWhenOptimisticPersisterUninitialised() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The optimistic persister has not been initialised");

    // ACT
    // Do not initialise the optimistic persister first - so getAllItems should
    // throw
    optimisticPersister.getAllItems();
  }

  @Test
  public void testGetAllItemsCorrectlyCallsSimpleDB() throws Exception {

    // ARRANGE
    initialiseOptimisticPersister();

    SelectRequest selectRequest = new SelectRequest();
    selectRequest.setConsistentRead(true);
    selectRequest.setSelectExpression("select * from `" + testSimpleDBDomainName + "`");

    // Configure select result with an item to be returned:
    SelectResult selectResult = new SelectResult();
    Set<Item> items = new HashSet<>();
    Item item = new Item();
    String itemDate = "2016-07-23";
    item.setName(itemDate);
    item.setAttributes(allAttributes);
    items.add(item);
    selectResult.setItems(items);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).select(with(equal(selectRequest)));
        will(returnValue(selectResult));
      }
    });

    List<ImmutablePair<String, List<Attribute>>> expectedItems = new ArrayList<>();
    ImmutablePair<String, List<Attribute>> pair = new ImmutablePair<>(itemDate, new ArrayList<>(
        activeNonVersionAttributes));
    expectedItems.add(pair);

    // ACT
    List<ImmutablePair<String, List<Attribute>>> actualItems = optimisticPersister.getAllItems();

    // ASSERT
    assertTrue("OptimisticPersister should return the correct items. Actual: " + actualItems
        + ", Expected: " + expectedItems, actualItems.equals(expectedItems));
  }

  @Test
  public void testPutThrowsWhenOptimisticPersisterUninitialised() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The optimistic persister has not been initialised");

    // ACT
    // Do not initialise the optimistic persister first - so put should throw
    ReplaceableAttribute testAttribute = new ReplaceableAttribute();
    testAttribute.setName("Name");
    testAttribute.setValue("Value");
    optimisticPersister.put(testItemName, Optional.of(42), testAttribute);
  }

  @Test
  public void testPutThrowsWhenMaximumNumberOfAttributesIsAlreadyPresent() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("Database put failed");

    // Initialiser persister with a max number of attributes equal to the
    // current number (1 as there is only one active attribute) - so new put's
    // will be rejected.
    optimisticPersister.initialise(1, mockLogger);

    // Configure attributes for database to return - the get is used for logging
    // only, so does not really matter.
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    getAttributesResult.setAttributes(allAttributes);
    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // ACT
    ReplaceableAttribute testAttribute = new ReplaceableAttribute();
    testAttribute.setName("Name");
    testAttribute.setValue("Value");
    // This should throw since we already have the max number of attributes.
    optimisticPersister.put(testItemName, Optional.of(42), testAttribute);
  }

  @Test
  public void testPutDoesNotThrowWhenMaximumNumberOfAttributesIsAlreadyPresentIfPutIsToInactivate()
      throws Exception {
    // Our 2-stage deletion process involves an initial put to 'inactivate' the
    // attribute being deleted. We must allow such put's to exceed the limit -
    // or else we could never delete attributes when we're over the limit.

    // ARRANGE

    // Initialiser persister with a max number of attributes equal to the
    // current number (1 as there is only one active attribute) - so new put's
    // will be rejected, unless they're inactivating puts.
    optimisticPersister.initialise(1, mockLogger);

    // Configure attributes for database to return - the get is used for logging
    // only, so does not really matter.
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    getAttributesResult.setAttributes(allAttributes);
    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // Don't care about further calls to SimpleDB.
    mockery.checking(new Expectations() {
      {
        ignoring(mockSimpleDBClient);
      }
    });

    // ACT
    // Use an 'inactivating' put i.e. value prefixed with 'Inactive'
    ReplaceableAttribute testAttribute = new ReplaceableAttribute();
    testAttribute.setName("Name");
    testAttribute.setValue("InactiveValue");
    // This should not throw even though we already have the max number of
    // attributes.
    optimisticPersister.put(testItemName, Optional.of(42), testAttribute);
  }

  @Test
  public void testPutUsesCorrectVersionNumberWhenCallingTheDatabase_EmptyVersionNumber()
      throws Exception {
    // In order for the persister to verify that the relevant item has not been
    // changed by someone else since we did a get, we need to provide the
    // version number when we call put. If noone had written to the item when we
    // called get on it, then we will have an empty version number, otherwise it
    // will have some finite value. This test verifies that put uses the
    // version-number we supply when it calls on to the database, and tests for
    // the empty version number case.

    doTestPutUsesCorrectVersionWhenCallingTheDatabase(Optional.empty());
  }

  @Test
  public void testPutUsesCorrectVersionNumberWhenCallingTheDatabase_NonEmptyVersionNumber()
      throws Exception {
    // In order for the persister to verify that the relevant item has not been
    // changed by someone else since we did a get, we need to provide the
    // version number when we call put. If noone had written to the item when we
    // called get on it, then we will have an empty version number, otherwise it
    // will have some finite value. This test verifies that put uses the
    // version-number we supply when it calls on to the database, and tests for
    // the nonempty version number case.

    // N.B. 51 is arbitrary here - but not empty!
    doTestPutUsesCorrectVersionWhenCallingTheDatabase(Optional.of(51));
  }

  private void doTestPutUsesCorrectVersionWhenCallingTheDatabase(Optional<Integer> version)
      throws Exception {

    // ARRANGE
    initialiseOptimisticPersister();

    // Configure attributes for database to return - the get is used for logging
    // only, so does not really matter.
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // Configure expectations for the put:
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(versionAttributeName);
    ReplaceableAttribute versionAttribute = new ReplaceableAttribute();
    versionAttribute.setName(versionAttributeName);
    versionAttribute.setReplace(true);
    if (!version.isPresent()) {
      // A version number attribute did not exist - so it still should not
      updateCondition.setExists(false);
      // Set initial value for our version number attribute
      versionAttribute.setValue("0");
    } else {
      // A version number attribute exists - so it should be unchanged
      updateCondition.setValue(Integer.toString(version.get()));
      // Bump up our version number attribute
      versionAttribute.setValue(Integer.toString(version.get() + 1));
    }

    List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
    replaceableAttributes.add(versionAttribute);

    // Add the new attribute
    ReplaceableAttribute testAttribute = new ReplaceableAttribute();
    testAttribute.setName("Name");
    testAttribute.setValue("Value");
    replaceableAttributes.add(testAttribute);

    PutAttributesRequest simpleDBPutRequest = new PutAttributesRequest(testSimpleDBDomainName,
        testItemName, replaceableAttributes, updateCondition);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).putAttributes(with(equal(simpleDBPutRequest)));
      }
    });

    // ACT
    optimisticPersister.put(testItemName, version, testAttribute);
  }

  @Test
  public void testPutHandlesConditionalCheckFailedExceptionCorrectly() throws Exception {
    // The persister should forward all simpleDB exceptions to us, but it should
    // convert ConditionalCheckFailed exceptions before forwarding, as clients
    // will likely want to handle that case differently. This tests conversion
    // of ConditionalCheckFailed exceptions.

    doTestPutHandlesExceptionsCorrectly(true);
  }

  @Test
  public void testPutHandlesOtherExceptionsCorrectly() throws Exception {
    // The persister should forward all simpleDB exceptions to us, but it should
    // convert ConditionalCheckFailed exceptions before forwarding, as clients
    // will likely want to handle that case differently. This tests forwarding
    // of exceptions other than the ConditionalCheckFailed exception.

    doTestPutHandlesExceptionsCorrectly(false);
  }

  private void doTestPutHandlesExceptionsCorrectly(Boolean isConditionalCheckFailedException)
      throws Exception {
    // The persister should forward all simpleDB exceptions to us, but it should
    // convert ConditionalCheckFailed exceptions before forwarding, as clients
    // will likely want to handle that case differently.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage(isConditionalCheckFailedException ? "Database put failed" : "Boom!");

    initialiseOptimisticPersister();

    // Configure attributes for database to return - the get is used for logging
    // only, so does not really matter.
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // Make the simpleDB call throw the correct exception
    AmazonServiceException exception = new AmazonServiceException("Boom!");
    exception.setErrorCode(isConditionalCheckFailedException ? "ConditionalCheckFailed"
        : "SomeOtherArbitraryCode");
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).putAttributes(with(anything()));
        will(throwException(exception));
      }
    });

    ReplaceableAttribute testAttribute = new ReplaceableAttribute();
    testAttribute.setName("Name");
    testAttribute.setValue("Value");

    // ACT
    optimisticPersister.put(testItemName, Optional.of(42), testAttribute);
  }

  @Test
  public void testPutReturnsCorrectVersion() throws Exception {
    // A successful put should return the version that the put-to item has after
    // the put - i.e. one higher than it had initially.

    // ARRANGE
    initialiseOptimisticPersister();

    // Configure attributes for database to return - the get is used for logging
    // only, so does not really matter.
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    mockery.checking(new Expectations() {
      {
        allowing(mockSimpleDBClient).putAttributes(with(anything()));
      }
    });

    ReplaceableAttribute testAttribute = new ReplaceableAttribute();
    testAttribute.setName("Name");
    testAttribute.setValue("Value");

    int initialVersion = 42; // Arbitrary

    // ACT
    int finalVersion = optimisticPersister.put(testItemName, Optional.of(initialVersion),
        testAttribute);

    // ASSERT
    assertTrue("The returned version should be one higher than the initial version",
        finalVersion == (initialVersion + 1));
  }

  @Test
  public void testDeleteThrowsWhenOptimisticPersisterUninitialised() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The optimistic persister has not been initialised");

    // ACT
    // Do not initialise the optimistic persister first - so delete should throw
    Attribute testAttribute = new Attribute();
    testAttribute.setName("Name");
    testAttribute.setValue("Value");
    optimisticPersister.delete(testItemName, testAttribute);
  }

  @Test
  public void testDeleteReturnsEarlyIfAttributeBeingDeletedDoesNotExist_NoVersionNumber()
      throws Exception {
    // We should return early (without calling delete on simpleDB) if the
    // attribute we're deleting no longer exists. This tests for when the item
    // does not even have a version attribute.
    doTestDeleteReturnsEarlyIfAttributeBeingDeletedDoesNotExist(false);
  }

  @Test
  public void testDeleteReturnsEarlyIfAttributeBeingDeletedDoesNotExist_VersionNumber()
      throws Exception {
    // We should return early (without calling delete on simpleDB) if the
    // attribute we're deleting no longer exists. This tests for when the item
    // has a version attribute, but does not have the attribute we're deleting.
    doTestDeleteReturnsEarlyIfAttributeBeingDeletedDoesNotExist(true);
  }

  private void doTestDeleteReturnsEarlyIfAttributeBeingDeletedDoesNotExist(
      Boolean versionNumberExists) throws Exception {

    // ARRANGE
    initialiseOptimisticPersister();

    // Configure database to return no attributes.
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    if (versionNumberExists) {
      // Ensure we return at least the version number attribute
      getAttributesResult.setAttributes(allAttributes);
    }
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // Ensure we return early.
    mockery.checking(new Expectations() {
      {
        never(mockSimpleDBClient).deleteAttributes(with(anything()));
      }
    });

    // ACT
    Attribute attributeToDelete = new Attribute();
    attributeToDelete.setName("Name");
    attributeToDelete.setValue("Value");
    optimisticPersister.delete(testItemName, attributeToDelete);
  }

  @Test
  public void testDeleteMarksAttributeAsInactiveBeforeDeletingIt() throws Exception {
    // Deletion is currently a 2-stage process (to ensure the deletion is
    // concurrency-safe). This tests that this 2-stage process is followed.
    // This is a horrible test!

    testDelete(false, Optional.empty(), true);
  }

  @Test
  public void testDeleteWorksEvenWhenTheMaximumNumberOfAttributesAlreadyExists() throws Exception {
    // Deletion is currently a 2-stage process (to ensure the deletion is
    // concurrency-safe). This tests that this 2-stage process is followed.
    // This is a horrible test!

    // Initialise with no space for more attributes
    optimisticPersister.initialise(1, mockLogger);
    testDelete(false, Optional.empty(), false);
  }

  private void testDelete(Boolean expectToThrow, Optional<Exception> exceptionToThrow,
      Boolean doInitialise) throws Exception {

    // ARRANGE
    if (exceptionToThrow.isPresent() && expectToThrow) {
      thrown.expect(Exception.class);
      thrown.expectMessage(exceptionToThrow.get().getMessage());
    }
    if (doInitialise) {
      initialiseOptimisticPersister();
    }

    // Configure database to return attributes - including that being deleted.
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(testSimpleDBDomainName,
        testItemName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult getAttributesResult = new GetAttributesResult();
    Set<Attribute> allAttributesCopy = new HashSet<>();
    allAttributesCopy.addAll(allAttributes);
    getAttributesResult.setAttributes(allAttributesCopy);
    mockery.checking(new Expectations() {
      {
        // Two calls as inactivating the attribute also does a get.
        exactly(1).of(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(getAttributesResult));
      }
    });

    // Ensure we first mark the to-be-deleted attribute as inactive.
    // We will delete the 'active' attribute.
    Attribute inactivatedAttribute = new Attribute();
    inactivatedAttribute.setName("ActiveAttribute");
    // An inactivated attribute should get an 'Inactive' prefix:
    inactivatedAttribute.setValue("InactiveActive");
    ReplaceableAttribute toBeInactivatedAttribute = new ReplaceableAttribute();
    toBeInactivatedAttribute.setName(inactivatedAttribute.getName());
    toBeInactivatedAttribute.setValue(inactivatedAttribute.getValue());
    toBeInactivatedAttribute.setReplace(true);
    ReplaceableAttribute versionNumberAttribute = new ReplaceableAttribute();
    versionNumberAttribute.setName(versionAttributeName);
    versionNumberAttribute.setValue(Integer.toString(testVersionNumber + 1));
    versionNumberAttribute.setReplace(true);
    List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
    replaceableAttributes.add(versionNumberAttribute);
    replaceableAttributes.add(toBeInactivatedAttribute);
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(versionAttributeName);
    updateCondition.setValue(Integer.toString(testVersionNumber));
    PutAttributesRequest simpleDBPutRequest = new PutAttributesRequest(testSimpleDBDomainName,
        testItemName, replaceableAttributes, updateCondition);

    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).putAttributes(with(equal(simpleDBPutRequest)));
      }
    });

    // Cannot reuse earlier expectation as the persister removes the
    // version-number attribute.
    GetAttributesResult secondGetAttributesResult = new GetAttributesResult();
    secondGetAttributesResult.setAttributes(allAttributes);
    mockery.checking(new Expectations() {
      {
        // Two calls as inactivating the attribute also does a get.
        exactly(1).of(mockSimpleDBClient).getAttributes(with(equal(simpleDBRequest)));
        will(returnValue(secondGetAttributesResult));
      }
    });

    // Finally, ensure we delete the now-inactivated attribute.
    Attribute inactivatedAttributeToDelete = new Attribute();
    inactivatedAttributeToDelete.setName(toBeInactivatedAttribute.getName());
    inactivatedAttributeToDelete.setValue(toBeInactivatedAttribute.getValue());
    List<Attribute> attributesToDelete = new ArrayList<>();
    attributesToDelete.add(inactivatedAttributeToDelete);
    UpdateCondition deleteUpdateCondition = new UpdateCondition();
    deleteUpdateCondition.setName(inactivatedAttributeToDelete.getName());
    deleteUpdateCondition.setValue(inactivatedAttributeToDelete.getValue());
    deleteUpdateCondition.setExists(true);
    DeleteAttributesRequest simpleDBDeleteRequest = new DeleteAttributesRequest(
        testSimpleDBDomainName, testItemName, attributesToDelete, deleteUpdateCondition);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).deleteAttributes(with(equal(simpleDBDeleteRequest)));
        if (exceptionToThrow.isPresent()) {
          will(throwException(exceptionToThrow.get()));
        }
      }
    });

    Attribute attributeToDelete = new Attribute();
    attributeToDelete.setName("ActiveAttribute");
    attributeToDelete.setValue("Active");

    // ACT
    optimisticPersister.delete(testItemName, attributeToDelete);
  }

  @Test
  public void testDeleteHandlesDoesNotExistExceptionCorrectly() throws Exception {
    // The persister should forward all simpleDB exceptions to us except
    // DoesNotExist exceptions which it should swallow. This tests swallowing
    // of DoesNotExist exceptions.

    AmazonServiceException exception = new AmazonServiceException("Boom!");
    exception.setErrorCode("AttributeDoesNotExist");
    testDelete(false, Optional.of(exception), true);
  }

  @Test
  public void testDeleteHandlesOtherExceptionsCorrectly() throws Exception {
    // The persister should forward all simpleDB exceptions to us except
    // DoesNotExist exceptions which it should swallow. This tests forwarding
    // of other exceptions.

    AmazonServiceException exception = new AmazonServiceException("Boom!");
    exception.setErrorCode("SomeOtherArbitraryCode");
    testDelete(true, Optional.of(exception), true);
  }

  @Test
  public void testDeleteAllAttributesThrowsWhenOptimisticPersisterUninitialised() throws Exception {
    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The optimistic persister has not been initialised");

    // ACT
    // Do not initialise the optimistic persister first - so delete should throw
    optimisticPersister.deleteAllAttributes(testItemName);
  }

  public void testDeleteAllAttributesCorrectlyCallsTheDatabase() throws IOException {
    // ARRANGE
    initialiseOptimisticPersister();

    DeleteAttributesRequest deleteAttributesRequest = new DeleteAttributesRequest(
        testSimpleDBDomainName, testItemName);
    mockery.checking(new Expectations() {
      {
        oneOf(mockSimpleDBClient).deleteAttributes(with(equal(deleteAttributesRequest)));
      }
    });

    // ACT
    optimisticPersister.deleteAllAttributes(testItemName);
  }
}