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

import java.util.Objects;

/**
 * Represents a single court booking at a specified date and time for specified players.
 *
 * <p>Time slots are all of equal length, with each slot beginning when the previous slot finishes.
 *    For example:
 *    <ul>
 *     <li>slot 1 goes from 10 a.m. to 10.45 a.m.</li>
 *     <li>slot 2 goes from 10.45 a.m. to 11.30 a.m.</li>
 *     <li>...</li>
 *     <li>slot 16, the last of the day, goes from 9.15 p.m. to 10.00 p.m.</li>
 * </ul>
 *
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class Booking {
  Integer court;
  Integer slot;
  String date;
  String players;
  String player1Name;
  String player2Name;

  public Integer getCourt() {
    return court;
  }

  /**
   *  Sets the booked court, starting at 1.
   */
  public void setCourt(Integer court) {
    this.court = court;
  }

  public Integer getSlot() {
    return slot;
  }

  /**
   *  Sets the booked time slot, starting at 1.
   */
  public void setSlot(Integer slot) {
    this.slot = slot;
  }

  public String getDate() {
    return date;
  }

  /**
  *  Sets the date of the booking, e.g. 2016-02-15 is 15th February 2016.
  */
  public void setDate(String date) {
    this.date = date;
  }

  public String getPlayers() {
    return players;
  }

  /**
   *  Sets the players names, separated by a forward slash, e.g. A.Shabana/J.Power.
   */
  public void setPlayers(String players) {
    this.players = players;
  }

  public String getPlayer1Name() {
    return player1Name;
  }

  /**
   *  Sets the name of the first player, e.g. A.Shabana.
   */
  public void setPlayer1Name(String player1Name) {
    this.player1Name = player1Name;
  }

  public String getPlayer2Name() {
    return player2Name;
  }

  /**
   *  Sets the name of the second player, e.g. J.Power.
   */
  public void setPlayer2Name(String player2Name) {
    this.player2Name = player2Name;
  }

  /**
   * Constructs booking for the specified court and time for the specified players.
   *
   * @param court the 1-based number of the booked court.
   * @param slot the 1-based time slot of the booked court.
   * @param players the names of the players, separated by a forward slash, e.g. A.Shabana/J.Power
   */
  public Booking(Integer court, Integer slot, String players) {
    this.court = court;
    this.slot = slot;
    this.players = players;
  }

  public Booking() {
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Booking other = (Booking) obj;

    return Objects.equals(this.court, other.court) && Objects.equals(this.slot, other.slot)
        && Objects.equals(this.players, other.players);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.court, this.slot, this.players);
  }

  @Override
  public String toString() {
    return com.google.common.base.MoreObjects.toStringHelper(this).addValue(this.court)
        .addValue(this.slot).addValue(this.players).toString();
  }
}