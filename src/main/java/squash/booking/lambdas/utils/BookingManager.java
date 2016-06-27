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

package squash.booking.lambdas.utils;

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
import com.google.common.collect.Sets;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Manages all interactions with the bookings database.
 *
 * <p>This manages all interactions with the database - which is currently SimpleDB. The
 * database holds all data relating to booked courts.
 *
 * <p>We use optimistic concurrency control when creating bookings to ensure multiple
 * clients do not overwrite each other. Each day's bookings in the database have
 * an associated version number, and we employ a Read-Modify-Write pattern. A downside
 * to this is that we open the door to losing availability. See, e.g.:
 * http://www.allthingsdistributed.com/2010/02/strong_consistency_simpledb.html, and:
 * https://aws.amazon.com/blogs/aws/amazon-simpledb-consistency-enhancements.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingManager implements IBookingManager {

  private String simpleDbDomainName;
  private String versionAttributeName;
  private Region region;
  private LambdaLogger logger;

  @Override
  public final void initialise(LambdaLogger logger) throws IOException {
    this.logger = logger;
    simpleDbDomainName = getStringProperty("simpledbdomainname");
    versionAttributeName = "VersionNumber";
    region = Region.getRegion(Regions.fromName(getStringProperty("region")));
  }

  @Override
  public List<Booking> createBooking(Booking bookingToCreate) throws Exception {

    logger.log("About to create booking in simpledb: " + bookingToCreate);

    AmazonSimpleDB client = getSimpleDBClient();

    String itemName = bookingToCreate.getDate();
    String attributeName = getAttributeNameFromBooking(bookingToCreate);

    // Get today's bookings (and version number), via consistent read:
    ImmutablePair<Optional<Integer>, List<Booking>> versionedBookings = getVersionedBookings(itemName);
    if (versionedBookings.left.isPresent()) {
      logger.log("Retrieved versioned bookings(Count: " + versionedBookings.right.size()
          + ")  have version number: " + versionedBookings.left.get());
    } else {
      logger.log("Retrieved versioned bookings(Count: " + versionedBookings.right.size()
          + ") have no version number");
    }

    // Check that the court(s) we're booking is/are currently free
    // Get individual booked courts as (court, slot) pairs
    Set<ImmutablePair<Integer, Integer>> bookedCourts = new HashSet<>();
    versionedBookings.right.forEach((booking) -> {
      addBookingToSet(booking, bookedCourts);
    });

    // Get courts we're trying to book as (court, slot) pairs
    Set<ImmutablePair<Integer, Integer>> courtsToBook = new HashSet<>();
    addBookingToSet(bookingToCreate, courtsToBook);

    // Does the new booking clash with existing bookings?
    boolean bookingClashes = Boolean
        .valueOf(Sets.intersection(courtsToBook, bookedCourts).size() > 0);

    if (bookingClashes) {
      // Case of trying to book an already-booked slot - this
      // probably means either:
      // - more than one person was trying to book the slot at once, or
      // - not all courts in our block booking are free
      logger
          .log("Cannot book courts which are already booked, so throwing a 'Booking creation failed' exception");
      throw new Exception("Booking creation failed");
    }

    logger.log("Required courts are currently free - so proceeding to make booking");
    // Do a conditional put - so we don't overwrite someone else's booking
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(versionAttributeName);
    ReplaceableAttribute versionAttribute = new ReplaceableAttribute();
    versionAttribute.setName(versionAttributeName);
    versionAttribute.setReplace(true);
    // Update will proceed unless the version number has changed
    if (versionedBookings.left.isPresent()) {
      // A version number attribute exists - so it should be unchanged
      updateCondition.setValue(Integer.toString(versionedBookings.left.get()));
      // Bump up our version number attribute
      versionAttribute.setValue(Integer.toString(versionedBookings.left.get() + 1));
    } else {
      // A version number attribute did not exist - so it still should not
      updateCondition.setExists(false);
      // Set initial value for our version number attribute
      versionAttribute.setValue("0");
    }

    List<ReplaceableAttribute> attributes = new ArrayList<>();
    attributes.add(versionAttribute);

    // Add the booking attribute
    String attributeValue = bookingToCreate.getPlayers();
    logger.log("ItemName: " + itemName);
    logger.log("AttributeName: " + attributeName);
    logger.log("AttributeValue: " + attributeValue);
    ReplaceableAttribute bookingAttribute = new ReplaceableAttribute();
    bookingAttribute.setName(attributeName);
    bookingAttribute.setValue(attributeValue);
    attributes.add(bookingAttribute);

    PutAttributesRequest simpleDBPutRequest = new PutAttributesRequest(simpleDbDomainName,
        itemName, attributes, updateCondition);

    try {
      client.putAttributes(simpleDBPutRequest);
    } catch (AmazonServiceException ase) {
      if (ase.getErrorCode().contains("ConditionalCheckFailed")) {
        // Someone else has made a booking since we read all bookings.
        // For now, assume this is rare and do not retry, just convert this to a
        // booking failed exception.
        logger.log("Caught AmazonServiceException for ConditionalCheckFailed whilst creating"
            + " booking so throwing as 'Booking creation failed' instead");
        throw new Exception("Booking creation failed");
      }
      throw ase;
    }

    logger.log("About to read back bookings from simpledb to check that create succeeded");
    // Do consistent read to see if the booking has succeeded. This will also
    // ensure the refreshed booking page will show the new booking. Conceivably
    // someone else could have deleted the booking after we created it - but
    // we probably want to consider that an error also - so ok as is.
    List<Booking> bookings = getBookings(bookingToCreate.getDate());

    // Check that our new booking is amongst those just read back
    Boolean bookingSucceeded = false;
    for (Booking booking : bookings) {
      if (booking.equals(bookingToCreate)) {
        bookingSucceeded = true;
        break;
      }
    }
    if (!bookingSucceeded) {
      throw new Exception("Booking creation failed");
    }
    logger.log("Created booking in simpledb");

    return bookings;
  }

  private void addBookingToSet(Booking booking, Set<ImmutablePair<Integer, Integer>> bookedCourts) {
    for (int court = booking.getCourt(); court < booking.getCourt() + booking.getCourtSpan(); court++) {
      for (int slot = booking.getSlot(); slot < booking.getSlot() + booking.getSlotSpan(); slot++) {
        bookedCourts.add(new ImmutablePair<>(court, slot));
      }
    }
  }

  @Override
  public List<Booking> getBookings(String date) {
    logger.log("About to get all bookings from simpledb for date: " + date);

    return (getVersionedBookings(date).right);
  }

  @Override
  public List<Booking> getAllBookings() {
    logger.log("About to get all bookings from simpledb for all dates");

    // Query SimpleDB to get bookings
    AmazonSimpleDB client = getSimpleDBClient();

    SelectRequest selectRequest = new SelectRequest();
    // N.B. Think if results are paged, second and subsequent pages will always
    // be eventually-consistent only.
    selectRequest.setConsistentRead(true);
    // Query all items in the domain
    selectRequest.setSelectExpression("select * from `" + simpleDbDomainName + "`");
    String nextToken = null;
    List<Booking> bookings = new ArrayList<>();
    do {
      // Convert items to Booking objects
      SelectResult selectResult = client.select(selectRequest);
      selectResult.getItems().forEach(
          item -> {
            item.getAttributes()
                .stream()
                .filter(attribute -> !attribute.getName().equals(versionAttributeName))
                .forEach(
                    attribute -> {
                      Booking booking = getBookingFromAttribute(attribute);
                      booking.setDate(attribute.getName());

                      logger.log("Adding booking to returned list: Date: " + attribute.getName()
                          + ", Details: " + booking.toString());
                      bookings.add(booking);
                    });
          });
      nextToken = selectResult.getNextToken();
      selectRequest.setNextToken(nextToken);
    } while (nextToken != null);
    logger.log("Got all bookings from simpledb for all dates");

    return bookings;
  }

  private ImmutablePair<Optional<Integer>, List<Booking>> getVersionedBookings(String date) {
    logger.log("About to get all versioned bookings from simpledb for date: " + date);

    // Query SimpleDB to get bookings (if any) for the requested date
    AmazonSimpleDB client = getSimpleDBClient();

    // Need to get all attributes for the item corresponding to the
    // requested date. Each attribute corresponds to a booked court slot

    // Do a consistent read - to ensure we get correct version number
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(simpleDbDomainName, date);
    logger.log("Using simpleDB domain: " + simpleDbDomainName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult result = client.getAttributes(simpleDBRequest);
    List<Attribute> attributes = result.getAttributes();

    // Get the version number and bookings
    Optional<Integer> version = Optional.empty();
    List<Booking> bookings = new ArrayList<>();
    if (attributes.size() > 0) {
      // If we have any attributes, we'll always have a version number
      Attribute versionNumberAttribute = attributes.stream()
          .filter(attribute -> attribute.getName().equals(versionAttributeName)).findFirst().get();
      version = Optional.of(Integer.parseInt(versionNumberAttribute.getValue()));
      logger.log("Retrieved version number: " + versionNumberAttribute.getValue());
      attributes.remove(versionNumberAttribute);

      // Convert any remaining attributes to Booking objects
      for (Attribute attribute : attributes) {
        Booking booking = getBookingFromAttribute(attribute);

        logger.log("Adding booking to returned list for " + date + ": " + booking.toString());
        bookings.add(booking);
      }
    }
    logger.log("Got all versioned bookings from simpledb for date: " + date);

    return new ImmutablePair<>(version, bookings);
  }

  private Booking getBookingFromAttribute(Attribute attribute) {
    // N.B. Attributes have names like <court>-<courtSpan>-<slot>-<slotSpan>
    // e.g. 4-1-7-1 is a single booking for court 4 at time slot 7
    // e.g. 4-2-7-3 is a block booking for courts 4-5 for time slots 7-9
    String[] parts = attribute.getName().split("-");
    Integer court = Integer.parseInt(parts[0]);
    Integer courtSpan = Integer.parseInt(parts[1]);
    Integer slot = Integer.parseInt(parts[2]);
    Integer slotSpan = Integer.parseInt(parts[3]);
    String players = attribute.getValue();
    return new Booking(court, courtSpan, slot, slotSpan, players);
  }

  private String getAttributeNameFromBooking(Booking booking) {
    return booking.getCourt().toString() + "-" + booking.getCourtSpan().toString() + "-"
        + booking.getSlot().toString() + "-" + booking.getSlotSpan().toString();
  }

  @Override
  public List<Booking> deleteBooking(Booking bookingToDelete) throws Exception {
    // N.B. We don't bother with the version number when deleting, as removing
    // bookings will not affect ability to make new bookings.

    logger.log("About to delete booking from simpledb: " + bookingToDelete.toString());
    // SimpleDB conditional delete will delete booking only if it still exists
    AmazonSimpleDB client = getSimpleDBClient();

    String itemName = bookingToDelete.getDate();
    String attributeName = getAttributeNameFromBooking(bookingToDelete);
    String attributeValue = bookingToDelete.getPlayers();

    // Do a conditional delete - so we don't overwrite someone else's
    // delete/re-booking
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(attributeName);
    updateCondition.setValue(attributeValue);
    // Update will proceed unless this booking no longer exists
    updateCondition.setExists(true);

    Attribute attribute = new Attribute();
    attribute.setName(attributeName);
    attribute.setValue(attributeValue);
    List<Attribute> attributes = new ArrayList<>();
    attributes.add(attribute);
    DeleteAttributesRequest simpleDBDeleteRequest = new DeleteAttributesRequest(simpleDbDomainName,
        itemName, attributes, updateCondition);
    try {
      client.deleteAttributes(simpleDBDeleteRequest);
    } catch (AmazonServiceException ase) {
      if (ase.getErrorCode().contains("AttributeDoesNotExist")) {
        // Case of trying to delete a booking that no longer exists - that's ok
        // - it probably just means more than one person was trying to delete
        // the booking at once. So swallow this exception
        logger
            .log("Caught AmazonServiceException for AttributeDoesNotExist whilst deleting booking so"
                + " swallowing and continuing");
      } else {
        throw ase;
      }
    }

    logger.log("About to read back bookings from simpledb to check that delete succeeded");
    // Do consistent read to see if the delete has succeeded. This will also
    // ensure the refreshed booking page will no longer show the deleted
    // booking. Conceivably someone else could have recreated the booking
    // after we deleted it - but we probably want to consider that an error also
    // - so ok as is.
    List<Booking> bookings = getBookings(bookingToDelete.getDate());

    // Check that our deleted booking is not amongst those just read back
    // Someone else could have recreated it super-fast?
    Boolean deleteSucceeded = true;
    for (Booking booking : bookings) {
      if (booking.equals(bookingToDelete)) {
        deleteSucceeded = false;
        break;
      }
    }
    if (!deleteSucceeded) {
      throw new Exception("Booking deletion failed");
    }
    logger.log("Deleted booking from simpledb");

    return bookings;
  }

  @Override
  public void deleteYesterdaysBookings() {
    // Remove the previous day's bookings from simpledb
    AmazonSimpleDB client = getSimpleDBClient();
    String yesterdaysDate = getCurrentLocalDate().minusDays(1).format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    logger.log("About to remove bookings from simpledb for yesterday, i.e. : " + yesterdaysDate);
    DeleteAttributesRequest deleteAttributesRequest = new DeleteAttributesRequest(
        simpleDbDomainName, yesterdaysDate);
    client.deleteAttributes(deleteAttributesRequest);
    logger.log("Removed bookings from simpledb for yesterday");
  }

  @Override
  public void deleteAllBookings() throws Exception {
    logger.log("Getting all bookings to delete");
    List<Booking> bookings = getAllBookings();
    logger.log("Found " + bookings.size() + " bookings to delete");
    logger.log("About to delete all bookings");
    for (Booking booking : bookings) {
      deleteBooking(booking);
      // sleep to avoid Too Many Requests error
      Thread.sleep(500);
    }
    logger.log("Deleted all bookings");
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
    try (InputStream stream = BookingManager.class
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

  /**
   * Returns the current London local date.
   */
  protected LocalDate getCurrentLocalDate() {
    // Use a getter here so unit tests can substitute a different date.

    // This gets the correct local date no matter what the user's device
    // system time may say it is, and no matter where in AWS we run.
    return BookingsUtilities.getCurrentLocalDate();
  }
}