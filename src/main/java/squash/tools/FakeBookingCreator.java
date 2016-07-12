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

package squash.tools;

import squash.booking.lambdas.core.Booking;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helper to create a large number of bookings for testing.
 * 
 * <p>Used to check performance etc in the presence of a lot of bookings.
 * 
 * <p>To create fake bookings:
 * <ul>
 * <li>Stand up a Sqawsh stack using CloudFormation.</li>
 * <li>Set up parameters at start of main as required.</li>
 * <li>Set name of file to save the json bookings to.</li>
 * <li>Run this program to produce the json file with the fake bookings.</li>
 * <li>In the AWS Lambda console, paste this json verbatim into the test event for the DatabaseRestoreLambda.</li>
 * <li>Run the DatabaseRestoreLambda from the AWS console.</li>
 * <li>In the AWS console, configure the test event for the UpdateBookingsLambda with the apiGatewayBaseUrl property e.g.
 * <pre>
 * {@code
 * {
 *  "apiGatewayBaseUrl": "https://xx62rd12l7.execute-api.eu-west-1.amazonaws.com/Squash"
 * }
 * }
 * </pre></li>
 * <li>Run the UpdateBookingsLambda from the AWS console, so the new bookings are reflected in the S3 website content.</li>
 * <li>Browse the Sqawsh website to see the new bookings.</li>
 * </ul>
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class FakeBookingCreator {

  public static void main(String[] args) throws IOException {
    int numberOfDays = 21;
    int numberOfCourts = 5;
    int maxCourtSpan = 5;
    int numberOfSlots = 16;
    int maxSlotSpan = 3;
    int minSurnameLength = 2;
    int maxSurnameLength = 20;
    int minBookingsPerDay = 0;
    int maxBookingsPerDay = 8;
    LocalDate startDate = LocalDate.of(2016, 7, 5);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    List<Booking> bookings = new ArrayList<>();
    for (LocalDate date = startDate; date.isBefore(startDate.plusDays(numberOfDays)); date = date
        .plusDays(1)) {
      int numBookings = ThreadLocalRandom.current().nextInt(minBookingsPerDay,
          maxBookingsPerDay + 1);
      List<Booking> daysBookings = new ArrayList<>();
      for (int bookingIndex = 0; bookingIndex < numBookings; bookingIndex++) {
        String player1 = RandomStringUtils.randomAlphabetic(1)
            + "."
            + RandomStringUtils.randomAlphabetic(ThreadLocalRandom.current().nextInt(
                minSurnameLength, maxSurnameLength + 1));
        String player2 = RandomStringUtils.randomAlphabetic(1)
            + "."
            + RandomStringUtils.randomAlphabetic(ThreadLocalRandom.current().nextInt(
                minSurnameLength, maxSurnameLength + 1));

        Set<ImmutablePair<Integer, Integer>> bookedCourts = new HashSet<>();
        daysBookings.forEach((booking) -> {
          addBookingToSet(booking, bookedCourts);
        });

        Booking booking;
        Set<ImmutablePair<Integer, Integer>> courtsToBook = new HashSet<>();
        do {
          // Loop until we create a booking of free courts
          int court = ThreadLocalRandom.current().nextInt(1, numberOfCourts + 1);
          int courtSpan = ThreadLocalRandom.current().nextInt(1,
              Math.min(maxCourtSpan + 1, numberOfCourts - court + 2));
          int slot = ThreadLocalRandom.current().nextInt(1, numberOfSlots + 1);
          int slotSpan = ThreadLocalRandom.current().nextInt(1,
              Math.min(maxSlotSpan + 1, numberOfSlots - slot + 2));
          booking = new Booking(court, courtSpan, slot, slotSpan, player1 + "/" + player2);
          booking.setDate(date.format(formatter));
          courtsToBook.clear();
          addBookingToSet(booking, courtsToBook);
        } while (Boolean.valueOf(Sets.intersection(courtsToBook, bookedCourts).size() > 0));

        daysBookings.add(booking);
      }
      bookings.addAll(daysBookings);
    }

    // Encode bookings as JSON
    // Create the node factory that gives us nodes.
    JsonNodeFactory factory = new JsonNodeFactory(false);
    // Create a json factory to write the treenode as json.
    JsonFactory jsonFactory = new JsonFactory();
    ObjectNode rootNode = factory.objectNode();

    ArrayNode bookingsNode = rootNode.putArray("bookings");
    for (int i = 0; i < bookings.size(); i++) {
      Booking booking = bookings.get(i);
      ObjectNode bookingNode = factory.objectNode();
      bookingNode.put("court", booking.getCourt());
      bookingNode.put("courtSpan", booking.getCourtSpan());
      bookingNode.put("slot", booking.getSlot());
      bookingNode.put("slotSpan", booking.getSlotSpan());
      bookingNode.put("players", booking.getPlayers());
      bookingNode.put("date", booking.getDate());
      bookingsNode.add(bookingNode);
    }
    rootNode.put("clearBeforeRestore", true);

    try (JsonGenerator generator = jsonFactory.createGenerator(new File("FakeBookings.json"),
        JsonEncoding.UTF8)) {
      ObjectMapper mapper = new ObjectMapper();
      mapper.setSerializationInclusion(Include.NON_EMPTY);
      mapper.setSerializationInclusion(Include.NON_NULL);
      mapper.writeTree(generator, rootNode);
    }
  }

  private static void addBookingToSet(Booking booking,
      Set<ImmutablePair<Integer, Integer>> bookedCourts) {
    for (int court = booking.getCourt(); court < booking.getCourt() + booking.getCourtSpan(); court++) {
      for (int slot = booking.getSlot(); slot < booking.getSlot() + booking.getSlotSpan(); slot++) {
        bookedCourts.add(new ImmutablePair<>(court, slot));
      }
    }
  }
}