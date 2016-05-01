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
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.services.simpledb.model.UpdateCondition;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Manages all interactions with the bookings database.
 *
 * <p>This manages all interactions with the database - which is currently SimpleDB. The
 * database holds all data relating to booked courts.
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingManager implements IBookingManager {

  private String simpleDbDomainName;
  private Region region;
  private LambdaLogger logger;

  @Override
  public final void Initialise(LambdaLogger logger) throws IOException {
    this.logger = logger;
    simpleDbDomainName = getStringProperty("simpledbdomainname");
    region = Region.getRegion(Regions.fromName(getStringProperty("region")));
  }

  @Override
  public List<Booking> createBooking(Booking bookingToCreate) throws Exception {

    logger.log("About to create booking in simpledb: " + bookingToCreate);
    // SimpleDB conditional put will make the booking only if the court is
    // still free
    AmazonSimpleDB client = getSimpleDBClient();

    String itemName = bookingToCreate.getDate();
    String attributeName = bookingToCreate.getCourt().toString() + "-"
        + bookingToCreate.getSlot().toString();

    // Do a conditional put - so we don't overwrite someone else's booking
    UpdateCondition updateCondition = new UpdateCondition();
    updateCondition.setName(attributeName);
    // Update will proceed unless this booking already exists
    updateCondition.setExists(false);
    String attributeValue = bookingToCreate.getPlayers();
    logger.log("ItemName: " + itemName);
    logger.log("AttributeName: " + attributeName);
    logger.log("AttributeValue: " + attributeValue);
    ReplaceableAttribute attribute = new ReplaceableAttribute();
    attribute.setName(attributeName);
    attribute.setValue(attributeValue);
    List<ReplaceableAttribute> attributes = new ArrayList<>();
    attributes.add(attribute);
    PutAttributesRequest simpleDBPutRequest = new PutAttributesRequest(simpleDbDomainName,
        itemName, attributes, updateCondition);

    try {
      client.putAttributes(simpleDBPutRequest);
    } catch (AmazonServiceException ase) {
      if (ase.getErrorCode().contains("ConditionalCheckFailed")) {
        // Case of trying to book an already-booked slot - this
        // probably means more than one person was trying to book the slot
        // at once. Convert this to a booking failed exception.
        logger.log("Caught AmazonServiceException for ConditionalCheckFailed whilst creating"
            + " booking so throwing as 'Booking creation failed' instead");
        throw new Exception("Booking creation failed");
      } else {
        throw ase;
      }
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
      if ((booking.getCourt() == bookingToCreate.getCourt())
          && (booking.getSlot() == bookingToCreate.getSlot())
          && (booking.getPlayers().equals(bookingToCreate.getPlayers()))) {
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

  @Override
  public List<Booking> getBookings(String date) {
    logger.log("About to get all bookings from simpledb for date: " + date);

    // Query SimpleDB to get bookings (if any) for the requested date
    AmazonSimpleDB client = getSimpleDBClient();

    // Need to get all attributes for the item corresponding to the
    // requested date. Each attribute corresponds to a booked court slot

    // Do a consistent read - so user sees their booking/deletion
    // immediately
    GetAttributesRequest simpleDBRequest = new GetAttributesRequest(simpleDbDomainName, date);
    logger.log("Using simpleDB domain: " + simpleDbDomainName);
    simpleDBRequest.setConsistentRead(true);
    GetAttributesResult result = client.getAttributes(simpleDBRequest);
    List<Attribute> attributes = result.getAttributes();

    // Convert each attribute to a Booking object
    // N.B. Attributes have names like <court>-<slot>
    // e.g. 4-7 is a booking for court 4 at time slot 7
    List<Booking> bookings = new ArrayList<>();
    for (Attribute attribute : attributes) {
      String[] parts = attribute.getName().split("-");
      Integer court = Integer.parseInt(parts[0]);
      Integer slot = Integer.parseInt(parts[1]);
      String players = attribute.getValue();
      Booking booking = new Booking(court, slot, players);

      logger.log("Adding booking to returned list for " + date + ": " + booking.toString());
      bookings.add(booking);
    }
    logger.log("Got all bookings from simpledb for date: " + date);

    return bookings;
  }

  @Override
  public List<Booking> getBookings() {
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
      // N.B. Attributes have names like <court>-<slot>
      // e.g. 4-7 is a booking for court 4 at time slot 7
      SelectResult selectResult = client.select(selectRequest);
      for (Item item : selectResult.getItems()) {
        for (Attribute attribute : item.getAttributes()) {
          String[] parts = attribute.getName().split("-");
          Integer court = Integer.parseInt(parts[0]);
          Integer slot = Integer.parseInt(parts[1]);
          String players = attribute.getValue();
          Booking booking = new Booking(court, slot, players);
          booking.setDate(item.getName());

          logger.log("Adding booking to returned list: Date: " + item.getName() + ", Details: "
              + booking.toString());
          bookings.add(booking);
        }
      }
      nextToken = selectResult.getNextToken();
      selectRequest.setNextToken(nextToken);
    } while (nextToken != null);
    logger.log("Got all bookings from simpledb for all dates");

    return bookings;
  }

  @Override
  public List<Booking> deleteBooking(Booking bookingToDelete) throws Exception {
    logger.log("About to delete booking from simpledb: " + bookingToDelete.toString());
    // SimpleDB conditional delete will delete booking only if it still exists
    AmazonSimpleDB client = getSimpleDBClient();

    String itemName = bookingToDelete.getDate();
    String attributeName = bookingToDelete.getCourt().toString() + "-"
        + bookingToDelete.getSlot().toString();
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
      if ((booking.getCourt() == bookingToDelete.getCourt())
          && (booking.getSlot() == bookingToDelete.getSlot())
          && (booking.getPlayers().equals(bookingToDelete.getPlayers()))) {
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
    List<Booking> bookings = getBookings();
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