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

package tools;

import squash.booking.Squash;
import squash.booking.model.BookingMutationInputModel;
import squash.booking.model.PutBookingsRequest;
import squash.booking.model.PutBookingsResult;
import squash.booking.model.SquashException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.opensdk.SdkRequestConfig;
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentity;
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentityClientBuilder;
import com.amazonaws.services.cognitoidentity.model.Credentials;
import com.amazonaws.services.cognitoidentity.model.GetCredentialsForIdentityRequest;
import com.amazonaws.services.cognitoidentity.model.GetCredentialsForIdentityResult;
import com.amazonaws.services.cognitoidentity.model.GetIdRequest;
import com.amazonaws.services.cognitoidentity.model.GetIdResult;

/**
 * Helper to call apigateway api to check what response is in error cases.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class DebugApiCaller {

  public static void main(String[] args) {

    String region = "eu-west-1";
    String cognitoIdentityPoolId = "eu-west-1:123ed37a-98b3-4d43-995b-8e3f1e00f9a7";
    AmazonCognitoIdentity client = AmazonCognitoIdentityClientBuilder.standard().withRegion(region)
        .build();

    // Query Cognito for temporary AWS credentials.
    GetIdRequest getIdRequest = new GetIdRequest();
    getIdRequest.setIdentityPoolId(cognitoIdentityPoolId);
    GetIdResult getIdResult = client.getId(getIdRequest);
    GetCredentialsForIdentityRequest getCredentialsForIdentityRequest = new GetCredentialsForIdentityRequest();
    getCredentialsForIdentityRequest.setIdentityId(getIdResult.getIdentityId());
    GetCredentialsForIdentityResult getCredentialsForIdentityResult = client
        .getCredentialsForIdentity(getCredentialsForIdentityRequest);
    Credentials credentials = getCredentialsForIdentityResult.getCredentials();
    String secretKey = credentials.getSecretKey();
    String accessKeyId = credentials.getAccessKeyId();
    String sessionToken = credentials.getSessionToken();

    // Attempt to book a court.
    AWSCredentials awsCredentials = new BasicSessionCredentials(accessKeyId, secretKey,
        sessionToken);
    AWSStaticCredentialsProvider awsStaticCredentialsProvider = new AWSStaticCredentialsProvider(
        awsCredentials);
    Squash squashClient = Squash.builder().iamRegion(region)
        .iamCredentials((AWSCredentialsProvider) awsStaticCredentialsProvider).build();
    PutBookingsRequest putBookingsRequest = new PutBookingsRequest();
    BookingMutationInputModel bookingMutationInputModel = new BookingMutationInputModel()
        .putOrDelete("PUT").court("1").courtSpan("1").slot("1").slotSpan("1").name("Team Training")
        .date("2017-05-13").password("pAssw0rd")
        .apiGatewayBaseUrl("https://zhu6ahqac9.execute-api.eu-west-1.amazonaws.com/Squash")
        // .apiGatewayBaseUrl("https://r2y30kosw2.execute-api.eu-west-1.amazonaws.com/Squash")
        .redirecUrl("http://dummy").redirecUrl2("fred");
    PutBookingsResult putBookingsResult = new PutBookingsResult();
    try {
      putBookingsResult = squashClient.putBookings(putBookingsRequest.bookingMutationInputModel(
          bookingMutationInputModel)
          .sdkRequestConfig(
              SdkRequestConfig.builder().httpRequestTimeout(10000).totalExecutionTimeout(10000)
                  .build()));
      putBookingsResult.sdkResponseMetadata().toString();
    } catch (SquashException e) {
      String msg = e.getMessage();
      e.printStackTrace();
    } catch (Exception e) {
      String msg = e.getMessage();
      String h = putBookingsResult.toString();
      e.printStackTrace();
    }
  }
}