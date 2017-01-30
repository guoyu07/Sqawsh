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

import static org.junit.Assert.assertTrue;

import squash.booking.lambdas.core.ILifecycleManager.LifecycleState;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tests the {@link LifecycleManager}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class LifecycleManagerTest {
  // Variables for setting up subclass of class under test
  squash.booking.lambdas.core.LifecycleManagerTest.TestLifecycleManager lifecycleManager;
  ImmutablePair<Optional<Integer>, Set<Attribute>> exampleActiveLifecycleItem;
  ImmutablePair<Optional<Integer>, Set<Attribute>> exampleReadonlyLifecycleItem;
  ImmutablePair<Optional<Integer>, Set<Attribute>> exampleRetiredLifecycleItem;
  String exampleForwardingUrl = "http://Some url";
  // Mocks
  Mockery mockery = new Mockery();
  LambdaLogger mockLogger;
  IOptimisticPersister mockOptimisticPersister;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void beforeTest() {

    // Set up a retired lifecycle-state item - with arbitrary version number.
    Attribute retiredStateAttribute = new Attribute();
    retiredStateAttribute.setName("State");
    retiredStateAttribute.setValue("RETIRED");
    Attribute retiredUrlAttribute = new Attribute();
    retiredUrlAttribute.setName("Url");
    retiredUrlAttribute.setValue(exampleForwardingUrl);
    Set<Attribute> retiredAttributes = new HashSet<Attribute>();
    retiredAttributes.add(retiredStateAttribute);
    retiredAttributes.add(retiredUrlAttribute);
    exampleRetiredLifecycleItem = new ImmutablePair<Optional<Integer>, Set<Attribute>>(
        Optional.of(40), retiredAttributes);

    // Set up a readonly lifecycle-state item - with arbitrary version number.
    Attribute readonlyStateAttribute = new Attribute();
    readonlyStateAttribute.setName("State");
    readonlyStateAttribute.setValue("READONLY");
    Set<Attribute> readonlyAttributes = new HashSet<Attribute>();
    readonlyAttributes.add(readonlyStateAttribute);
    exampleReadonlyLifecycleItem = new ImmutablePair<Optional<Integer>, Set<Attribute>>(
        Optional.of(42), readonlyAttributes);

    // Set up an active lifecycle-state item - with arbitrary version number.
    Attribute activeStateAttribute = new Attribute();
    activeStateAttribute.setName("State");
    activeStateAttribute.setValue("ACTIVE");
    Set<Attribute> activeAttributes = new HashSet<Attribute>();
    activeAttributes.add(activeStateAttribute);
    exampleActiveLifecycleItem = new ImmutablePair<Optional<Integer>, Set<Attribute>>(
        Optional.of(43), activeAttributes);

    // Set up mock logger
    mockLogger = mockery.mock(LambdaLogger.class);
    mockery.checking(new Expectations() {
      {
        ignoring(mockLogger);
      }
    });
    mockOptimisticPersister = mockery.mock(IOptimisticPersister.class);

    // Set up the lifecycle manager
    lifecycleManager = new squash.booking.lambdas.core.LifecycleManagerTest.TestLifecycleManager();
    lifecycleManager.setOptimisticPersister(mockOptimisticPersister);
  }

  @After
  public void afterTest() {
    mockery.assertIsSatisfied();
  }

  // Define a test lifecycle manager with some overrides to facilitate testing
  public class TestLifecycleManager extends LifecycleManager {

    public void setOptimisticPersister(IOptimisticPersister optimisticPersister) {
      this.optimisticPersister = optimisticPersister;
    }

    @Override
    public IOptimisticPersister getOptimisticPersister() {
      return optimisticPersister;
    }
  }

  @Test
  public void testSetLifecycleStateThrowsWhenLifecycleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The lifecycle manager has not been initialised");

    // ACT
    // Do not initialise the lifecycle manager first - so we should throw
    // N.B. Parameters are arbitrary here.
    lifecycleManager.setLifecycleState(LifecycleState.RETIRED, Optional.of(exampleForwardingUrl));
  }

  @Test
  public void testSetLifecycleStateCorrectlyCallsTheOptimisticPersister_retired_with_url()
      throws Exception {
    // Tests happy path for setting RETIRED state - where we also supply a
    // forwarding Url for the new site.

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    // Set up a test lifecycle-state item - with an arbitrary version number.
    ImmutablePair<Optional<Integer>, Set<Attribute>> testItem = new ImmutablePair<Optional<Integer>, Set<Attribute>>(
        Optional.of(42), new HashSet<Attribute>());
    ReplaceableAttribute newStateAttribute = new ReplaceableAttribute();
    newStateAttribute.setName("State");
    newStateAttribute.setValue("RETIRED");
    newStateAttribute.setReplace(true);
    ReplaceableAttribute newUrlAttribute = new ReplaceableAttribute();
    newUrlAttribute.setName("Url");
    newUrlAttribute.setValue(exampleForwardingUrl);
    newUrlAttribute.setReplace(true);
    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(testItem));
        exactly(1).of(mockOptimisticPersister).put(with(equal("LifecycleState")),
            with(equal(Optional.of(42))), with(equal(newStateAttribute)));
        will(returnValue(43));
        exactly(1).of(mockOptimisticPersister).put(with(equal("LifecycleState")),
            with(equal(Optional.of(43))), with(equal(newUrlAttribute)));
      }
    });

    // ACT
    lifecycleManager.setLifecycleState(LifecycleState.RETIRED, Optional.of(exampleForwardingUrl));
  }

  @Test
  public void testSetLifecycleStateThrowsWhenSetToRetiredWithoutUrl() throws Exception {
    // When setting state to RETIRED we must also provide a forwarding url to
    // the new site.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Must provide valid url to new service when setting lifecycle state to RETIRED";
    thrown.expectMessage(message);

    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        // Shouldn't get as far as calling the persister.
        never(mockOptimisticPersister).get(with(anything()));
        never(mockOptimisticPersister).put(with(anything()), with(anything()), with(anything()));
      }
    });

    // ACT - this should throw, as we're not providing a forwarding url.
    lifecycleManager.setLifecycleState(LifecycleState.RETIRED, Optional.empty());
  }

  @Test
  public void testSetLifecycleStateThrowsWhenSetToRetiredWithInvalidUrl() throws Exception {
    // When setting state to RETIRED we must also provide a forwarding url to
    // the new site. This url must start with http.

    // ARRANGE
    thrown.expect(Exception.class);
    String message = "Must provide valid url to new service when setting lifecycle state to RETIRED";
    thrown.expectMessage(message);

    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        // Shouldn't get as far as calling the persister.
        never(mockOptimisticPersister).get(with(anything()));
        never(mockOptimisticPersister).put(with(anything()), with(anything()), with(anything()));
      }
    });

    // ACT - this should throw, as we're providing a forwarding url without
    // http.
    lifecycleManager.setLifecycleState(LifecycleState.RETIRED, Optional.of("www.bbc.co.uk"));
  }

  @Test
  public void testSetLifecycleStateCorrectlyCallsTheOptimisticPersister_Active() throws Exception {
    // Tests happy path for setting Active state.
    doTestSetLifecycleStateCorrectlyCallsTheOptimisticPersister(LifecycleState.ACTIVE);
  }

  @Test
  public void testSetLifecycleStateCorrectlyCallsTheOptimisticPersister_Readonly() throws Exception {
    // Tests happy path for setting Readonly state.
    doTestSetLifecycleStateCorrectlyCallsTheOptimisticPersister(LifecycleState.READONLY);
  }

  private void doTestSetLifecycleStateCorrectlyCallsTheOptimisticPersister(
      LifecycleState lifecycleState) throws Exception {

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    // Set up a test lifecycle-state item - with an arbitrary version number.
    ImmutablePair<Optional<Integer>, Set<Attribute>> testItem = new ImmutablePair<Optional<Integer>, Set<Attribute>>(
        Optional.of(42), new HashSet<Attribute>());
    ReplaceableAttribute newStateAttribute = new ReplaceableAttribute();
    newStateAttribute.setName("State");
    newStateAttribute.setValue(lifecycleState.name());
    newStateAttribute.setReplace(true);
    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(testItem));
        exactly(1).of(mockOptimisticPersister).put(with(equal("LifecycleState")),
            with(equal(Optional.of(42))), with(equal(newStateAttribute)));
        // Don't want to put the Url attribute in this case - since we're not
        // retiring.
        never(mockOptimisticPersister).put(with(equal("LifecycleState")),
            with(equal(Optional.of(43))), with(anything()));
      }
    });

    // ACT
    lifecycleManager.setLifecycleState(lifecycleState, Optional.empty());
  }

  @Test
  public void testGetLifecycleStateThrowsWhenLifecycleManagerUninitialised() throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The lifecycle manager has not been initialised");

    // ACT
    // Do not initialise the lifecycle manager first - so we should throw
    // N.B. Parameters are arbitrary here.
    lifecycleManager.getLifecycleState();
  }

  @Test
  public void testGetLifecycleStateCorrectlyCallsTheOptimisticPersister() throws Exception {
    // We should always get the lifecycle state item from the persister.

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    // Set up a test lifecycle-state item - with an arbitrary version number.
    ImmutablePair<Optional<Integer>, Set<Attribute>> testItem = new ImmutablePair<Optional<Integer>, Set<Attribute>>(
        Optional.of(42), new HashSet<Attribute>());

    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(testItem));
      }
    });

    // ACT
    // FIXME verify it returns value got from the persister
    lifecycleManager.getLifecycleState();
  }

  @Test
  public void testGetLifecycleStateReturnsActiveWhenNoStateHasBeenSet() throws Exception {
    // If no LifecycleState has been set then the site must be in the ACTIVE
    // state.

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    // Set up an empty test lifecycle-state item - with an empty version
    // number. This corresponds to the state never having been set.
    ImmutablePair<Optional<Integer>, Set<Attribute>> emptyTestItem = new ImmutablePair<Optional<Integer>, Set<Attribute>>(
        Optional.empty(), new HashSet<Attribute>());

    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(emptyTestItem));
      }
    });

    // ACT
    ImmutablePair<LifecycleState, Optional<String>> lifecycleState = lifecycleManager
        .getLifecycleState();

    // ASSERT
    assertTrue("Lifecycle state should be active if it has not been set",
        lifecycleState.left.equals(LifecycleState.ACTIVE));
  }

  @Test
  public void testGetLifecycleStateAlsoReturnsUrlWhenStateIsRetired() throws Exception {
    // If the state is RETIRED, then we should also be returning a forwarding
    // url.

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(exampleRetiredLifecycleItem));
      }
    });

    // ACT
    ImmutablePair<LifecycleState, Optional<String>> lifecycleState = lifecycleManager
        .getLifecycleState();

    // ASSERT
    assertTrue("Url should be returned when lifecycle state is retired",
        lifecycleState.right.isPresent());
    assertTrue(
        "Correct url as returned from the persister should be returned when lifecycle state is retired",
        lifecycleState.right.get().equals(exampleForwardingUrl));
    assertTrue("Lifecycle state should be retired",
        lifecycleState.left.equals(LifecycleState.RETIRED));
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateThrowsWhenLifecycleManagerUninitialised()
      throws Exception {

    // ARRANGE
    thrown.expect(Exception.class);
    thrown.expectMessage("The lifecycle manager has not been initialised");

    // ACT
    // Do not initialise the lifecycle manager first - so we should throw
    // N.B. Parameters are arbitrary here.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(true, false);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowForReadingCallsNotFromServiceUser()
      throws Exception {
    // All operations should be allowed by the system (e.g. rule-based bookings
    // etc) in any state.

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        // Should not need to query persister in this case.
        never(mockOptimisticPersister).get(with(anything()));
      }
    });

    // ACT
    // Should not throw - as this is a system call.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(true, false);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowForWritingCallsNotFromServiceUser()
      throws Exception {
    // All operations should be allowed by the system (e.g. rule-based bookings
    // etc) in any state.

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        // Should not need to query persister in this case.
        never(mockOptimisticPersister).get(with(anything()));
      }
    });

    // ACT
    // Should not throw - as this is a system call.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(false, false);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowForWritingCallsInActiveState()
      throws Exception {
    // All operations should be allowed by anyone when in the ACTIVE lifecycle
    // state. This tests writing calls by end users.

    testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowInActiveState(false);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowForReadingCallsInActiveState()
      throws Exception {
    // All operations should be allowed by anyone when in the ACTIVE lifecycle
    // state. This tests reading calls by end users.

    testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowInActiveState(true);
  }

  private void testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowInActiveState(
      boolean operationIsReadOnly) throws Exception {

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    // We're in the ACTIVE lifecycle state.
    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(exampleActiveLifecycleItem));
      }
    });

    // ACT
    // Should not throw - as we're ACTIVE.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(operationIsReadOnly, true);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateDoesNotThrowForReadingCallsInReadonlyState()
      throws Exception {
    // Reading operations should be allowed in READONLY state

    // ARRANGE
    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(exampleReadonlyLifecycleItem));
      }
    });

    // ACT
    // Should not throw - as this is a reading call.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(true, true);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateThrowsForWritingCallsInReadonlyState()
      throws Exception {
    // Writing operations should not be allowed in READONLY state

    // ARRANGE
    thrown.expect(Exception.class);
    thrown
        .expectMessage("Cannot mutate bookings or rules - booking service is temporarily readonly whilst site maintenance is in progress");

    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(exampleReadonlyLifecycleItem));
      }
    });

    // ACT
    // Should throw - as we're trying to write when in READONLY state.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(false, true);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateThrowsForEndUserCallsInRetiredState_Reading()
      throws Exception {
    // All end-user operations should not be allowed in RETIRED state. N.B.
    // System operations will still be allowed.

    doTestthrowIfOperationInvalidForCurrentLifecycleStateThrowsForEndUserCallsInRetiredState(true);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateThrowsForEndUserCallsInRetiredState_Writing()
      throws Exception {
    // All end-user operations should not be allowed in RETIRED state. N.B.
    // System operations will still be allowed.

    doTestthrowIfOperationInvalidForCurrentLifecycleStateThrowsForEndUserCallsInRetiredState(false);
  }

  private void doTestthrowIfOperationInvalidForCurrentLifecycleStateThrowsForEndUserCallsInRetiredState(
      boolean operationIsReadOnly) throws Exception {
    // All end-user operations should not be allowed in RETIRED state. N.B.
    // System operations will still be allowed.

    // ARRANGE
    thrown.expect(Exception.class);
    thrown
        .expectMessage("Cannot access bookings or rules - there is an updated version of the booking service. Forwarding Url: "
            + exampleForwardingUrl);

    lifecycleManager.initialise(mockLogger);

    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(exampleRetiredLifecycleItem));
      }
    });

    // ACT
    // Should throw - as its an end-user operation when in RETIRED state.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(operationIsReadOnly, true);
  }

  @Test
  public void testthrowIfOperationInvalidForCurrentLifecycleStateThrowsForEndUserCallsInRetiredStateWithoutUrl()
      throws Exception {
    // All end-user operations should not be allowed in RETIRED state. N.B.
    // System operations will still be allowed. This just adds check that if
    // we're retired but for some reason don't have a forwarding url that we
    // still throw. But we should never get to this state...

    // ARRANGE
    thrown.expect(Exception.class);
    thrown
        .expectMessage("Cannot access bookings or rules - there is an updated version of the booking service. Forwarding Url: UrlNotPresent");

    lifecycleManager.initialise(mockLogger);

    // Set up a defective retired lifecycle-state item - with missing url.
    Attribute retiredStateAttribute = new Attribute();
    retiredStateAttribute.setName("State");
    retiredStateAttribute.setValue("RETIRED");
    Set<Attribute> retiredAttributes = new HashSet<Attribute>();
    retiredAttributes.add(retiredStateAttribute);

    mockery.checking(new Expectations() {
      {
        exactly(1).of(mockOptimisticPersister).get(with(equal("LifecycleState")));
        will(returnValue(new ImmutablePair<Optional<Integer>, Set<Attribute>>(Optional.of(40),
            retiredAttributes)));
      }
    });

    // ACT
    // Should throw - as its an end-user operation when in RETIRED state.
    lifecycleManager.throwIfOperationInvalidForCurrentLifecycleState(true, true);
  }
}