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

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Manages all bookings.
 *
 * <p>This manages all bookings and their persistence in the database - which is
 * currently SimpleDB. The database interactions are handled using an
 * {@link IOptimisticPersister IOptimisticPersister}.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class BookingManager implements IBookingManager {

  private Integer maxNumberOfBookingsPerDay = 100;
  private IOptimisticPersister optimisticPersister;
  private LambdaLogger logger;
  private Boolean initialised = false;

  @Override
  public final void initialise(LambdaLogger logger) throws IOException {
    this.logger = logger;
    initialised = true;
  }

  @Override
  public List<Booking> createBooking(Booking bookingToCreate) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The booking manager has not been initialised");
    }

    logger.log("About to create booking in database: " + bookingToCreate);

    // Get today's bookings (and version number), via consistent read:
    String itemName = bookingToCreate.getDate();
    ImmutablePair<Optional<Integer>, List<Booking>> versionedBookings = getVersionedBookings(itemName);

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
    String attributeName = getAttributeNameFromBooking(bookingToCreate);
    String attributeValue = bookingToCreate.getName();
    logger.log("ItemName: " + itemName);
    logger.log("AttributeName: " + attributeName);
    logger.log("AttributeValue: " + attributeValue);
    ReplaceableAttribute bookingAttribute = new ReplaceableAttribute();
    bookingAttribute.setName(attributeName);
    bookingAttribute.setValue(attributeValue);

    getOptimisticPersister().put(itemName, versionedBookings.left, bookingAttribute);
    logger.log("Created booking in database");

    // Add the booking we've just made to the pre-existing ones.
    List<Booking> bookings = versionedBookings.right;
    bookings.add(bookingToCreate);

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
  public List<Booking> getBookings(String date) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The booking manager has not been initialised");
    }

    logger.log("About to get all bookings from database for date: " + date);
    return (getVersionedBookings(date).right);
  }

  @Override
  public List<Booking> getAllBookings() throws IOException {

    if (!initialised) {
      throw new IllegalStateException("The booking manager has not been initialised");
    }

    logger.log("About to get all bookings from database for all dates");

    // Query database to get bookings
    List<Booking> bookings = new ArrayList<>();
    getOptimisticPersister().getAllItems()
        .stream()
        // Want only items corresponding to bookings
        .filter(pair -> !pair.left.equals("BookingRulesAndExclusions"))
        .forEach(
            pair -> {
              pair.right.forEach(attribute -> {
                Booking booking = getBookingFromAttribute(attribute, pair.left);

                logger.log("Adding booking to returned list: Date: " + pair.left + ", Details: "
                    + booking.toString());
                bookings.add(booking);
              });
            });
    logger.log("Got all bookings from database for all dates");

    return bookings;
  }

  private ImmutablePair<Optional<Integer>, List<Booking>> getVersionedBookings(String date)
      throws Exception {
    logger.log("About to get all versioned bookings from database for: " + date);

    // Get existing bookings (and version number), via consistent read:
    ImmutablePair<Optional<Integer>, Set<Attribute>> versionedAttributes = getOptimisticPersister()
        .get(date);

    // Convert attributes to Bookings:
    List<Booking> existingBookings = new ArrayList<>();
    versionedAttributes.right.stream().forEach(attribute -> {
      existingBookings.add(getBookingFromAttribute(attribute, date));
    });

    return new ImmutablePair<>(versionedAttributes.left, existingBookings);
  }

  private Booking getBookingFromAttribute(Attribute attribute, String date) {
    // N.B. Attributes have names like <court>-<courtSpan>-<slot>-<slotSpan>
    // e.g. 4-1-7-1 is a single booking for court 4 at time slot 7
    // e.g. 4-2-7-3 is a block booking for courts 4-5 for time slots 7-9
    String[] parts = attribute.getName().split("-");
    Integer court = Integer.parseInt(parts[0]);
    Integer courtSpan = Integer.parseInt(parts[1]);
    Integer slot = Integer.parseInt(parts[2]);
    Integer slotSpan = Integer.parseInt(parts[3]);
    String name = attribute.getValue();
    Booking booking = new Booking(court, courtSpan, slot, slotSpan, name);
    booking.setDate(date);
    return booking;
  }

  private String getAttributeNameFromBooking(Booking booking) {
    return booking.getCourt().toString() + "-" + booking.getCourtSpan().toString() + "-"
        + booking.getSlot().toString() + "-" + booking.getSlotSpan().toString();
  }

  @Override
  public List<Booking> deleteBooking(Booking bookingToDelete) throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The booking manager has not been initialised");
    }

    logger.log("About to delete booking from database: " + bookingToDelete.toString());
    Attribute attribute = new Attribute();
    attribute.setName(getAttributeNameFromBooking(bookingToDelete));
    attribute.setValue(bookingToDelete.getName());
    getOptimisticPersister().delete(bookingToDelete.getDate(), attribute);

    logger.log("Deleted booking from database");

    return getBookings(bookingToDelete.getDate());
  }

  @Override
  public void deleteYesterdaysBookings() throws IOException {

    if (!initialised) {
      throw new IllegalStateException("The booking manager has not been initialised");
    }

    // Remove the previous day's bookings from database
    String yesterdaysDate = getCurrentLocalDate().minusDays(1).format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    logger.log("About to remove bookings from database for yesterday, i.e. : " + yesterdaysDate);
    getOptimisticPersister().deleteAllAttributes(yesterdaysDate);
    logger.log("Removed yesterday's bookings from database");
  }

  @Override
  public void deleteAllBookings() throws Exception {

    if (!initialised) {
      throw new IllegalStateException("The booking manager has not been initialised");
    }

    logger.log("Getting all bookings to delete");
    List<Booking> bookings = getAllBookings();
    logger.log("Found " + bookings.size() + " bookings to delete");
    logger.log("About to delete all bookings");
    for (Booking booking : bookings) {
      deleteBooking(booking);
      // sleep to avoid Too Many Requests errors
      Thread.sleep(500);
    }
    logger.log("Deleted all bookings");
  }

  /**
   * Returns an optimistic persister.
   * @throws IOException 
   */
  protected IOptimisticPersister getOptimisticPersister() throws IOException {

    if (!initialised) {
      throw new IllegalStateException("The booking manager has not been initialised");
    }

    if (optimisticPersister == null) {
      optimisticPersister = new OptimisticPersister();
      optimisticPersister.initialise(maxNumberOfBookingsPerDay, logger);
    }

    return optimisticPersister;
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