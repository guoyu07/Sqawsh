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

package squash.booking.lambdas;

import squash.booking.lambdas.core.BookingRule;

/**
 * Request parameter for the {@link PutDeleteBookingRuleOrExclusionLambda PutDeleteBookingRuleOrExclusion} lambda function.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class PutDeleteBookingRuleOrExclusionLambdaRequest {
  String putOrDelete;
  BookingRule bookingRule;
  String dateToExclude;
  String cognitoAuthenticationType;
  String cognitoIdentityPoolId;

  public String getPutOrDelete() {
    return putOrDelete;
  }

  /**
   *  Sets whether the booking rule or exclusion should be created or deleted.
   *  
   *  @param putOrDelete is PUT (DELETE) to create (delete) the booking rule or exclusion.
   */
  public void setPutOrDelete(String putOrDelete) {
    this.putOrDelete = putOrDelete;
  }

  public BookingRule getBookingRule() {
    return bookingRule;
  }

  /**
   *  Sets the booking rule being mutated.
   */
  public void setBookingRule(BookingRule bookingRule) {
    this.bookingRule = bookingRule;
  }

  public String getDateToExclude() {
    return dateToExclude;
  }

  /**
   *  Sets the exclude date to change on the booking rule.
   *  
   *  If this is empty, then the booking rule itself will be added or deleted.
   */
  public void setDateToExclude(String dateToExclude) {
    this.dateToExclude = dateToExclude;
  }

  /**
   *  Sets whether the user is authenticated or unauthenticated.
   *  
   *  Will have value 'authenticated' or 'unauthenticated'.
   *
   * @param cognitoAuthenticationType whether the user is authenticated or unauthenticated.
   */
  public void setCognitoAuthenticationType(String cognitoAuthenticationType) {
    this.cognitoAuthenticationType = cognitoAuthenticationType;
  }

  public String getCognitoAuthenticationType() {
    return cognitoAuthenticationType;
  }

  /**
   *  Sets the Cognito identity pool id to which the user belongs.
   *
   * @param cognitoIdentityPoolId the Cognito identity pool id to which the user belongs.
   */
  public void setCognitoIdentityPoolId(String cognitoIdentityPoolId) {
    this.cognitoIdentityPoolId = cognitoIdentityPoolId;
  }

  public String getCognitoIdentityPoolId() {
    return cognitoIdentityPoolId;
  }

  @Override
  public String toString() {
    return com.google.common.base.MoreObjects.toStringHelper(this).addValue(this.putOrDelete)
        .addValue(this.bookingRule).addValue(this.dateToExclude).toString();
  }
}