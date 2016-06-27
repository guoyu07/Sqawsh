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

package squash.booking.lambdas;

/**
 * Request parameter for the {@link PutDeleteBookingLambda PutDeleteBooking} lambda function.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PutDeleteBookingLambdaRequest {
  String putOrDelete;
  String court;
  String courtSpan;
  String slot;
  String slotSpan;
  String players;
  String player1name;
  String player2name;
  String date;
  String password;
  String apiGatewayBaseUrl;
  String redirectUrl;

  public String getPutOrDelete() {
    return putOrDelete;
  }

  /**
   *  Sets whether the booking should be created or deleted.
   *  
   *  @param putOrDelete is PUT (DELETE) to create (delete) the booking.
   */
  public void setPutOrDelete(String putOrDelete) {
    this.putOrDelete = putOrDelete;
  }

  public String getCourt() {
    return court;
  }

  /**
   *  Sets the court for the booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setCourt Booking.
   */
  public void setCourt(String court) {
    this.court = court;
  }

  public String getCourtSpan() {
    return courtSpan;
  }

  /**
   *  Sets the number of courts per time slot in a block booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setCourt Booking.
   */
  public void setCourtSpan(String courtSpan) {
    this.courtSpan = courtSpan;
  }

  public String getSlot() {
    return slot;
  }

  /**
   *  Sets the time slot for the booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setSlot Booking.
   */
  public void setSlot(String slot) {
    this.slot = slot;
  }

  public String getSlotSpan() {
    return slotSpan;
  }

  /**
   *  Sets the number of time slots per court in a block booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setSlot Booking.
   */
  public void setSlotSpan(String slotSpan) {
    this.slotSpan = slotSpan;
  }

  public String getPlayers() {
    return players;
  }

  /**
   *  Sets the names of the players for the booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setPlayers Booking.
   */
  public void setPlayers(String players) {
    this.players = players;
  }

  public String getPlayer1name() {
    return player1name;
  }

  /**
   *  Sets the name of the first player for the booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setPlayer1Name Booking.
   */
  public void setPlayer1name(String player1name) {
    this.player1name = player1name;
  }

  public String getPlayer2name() {
    return player2name;
  }

  /**
   *  Sets the name of the second player for the booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setPlayer2Name Booking.
   */
  public void setPlayer2name(String player2name) {
    this.player2name = player2name;
  }

  public String getDate() {
    return date;
  }

  /**
   *  Sets the date of the booking.
   *  
   *  @see squash.booking.lambdas.utils.Booking#setDate Booking.
   */
  public void setDate(String date) {
    this.date = date;
  }

  public String getPassword() {
    return password;
  }

  /**
   *  Sets the password for the booking.
   */
  public void setPassword(String password) {
    this.password = password;
  }

  public String getApiGatewayBaseUrl() {
    return apiGatewayBaseUrl;
  }

  /**
   *  Sets the base Url of our Apigateway Api.
   */
  public void setApiGatewayBaseUrl(String apiGatewayBaseUrl) {
    this.apiGatewayBaseUrl = apiGatewayBaseUrl;
  }

  public String getRedirectUrl() {
    return redirectUrl;
  }

  /**
   *  Sets the Url to redirect client to after the PutDeleteBooking call.
   *
   * @param redirectUrl the Url to which the client should be redirected.
   */
  public void setRedirectUrl(String redirectUrl) {
    this.redirectUrl = redirectUrl;
  }

  @Override
  public String toString() {
    return com.google.common.base.MoreObjects.toStringHelper(this).addValue(this.putOrDelete)
        .addValue(this.court).addValue(this.courtSpan).addValue(this.slot).addValue(this.slotSpan)
        .addValue(this.players).addValue(this.player1name).addValue(this.player2name)
        .addValue(this.date).addValue(this.apiGatewayBaseUrl).addValue(this.redirectUrl).toString();
  }
}