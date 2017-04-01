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

package customsamplers;

import squash.booking.Squash;
import squash.booking.model.BookingMutationInputModel;
import squash.booking.model.PutBookingsRequest;
import squash.booking.model.PutBookingsResult;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.opensdk.SdkRequestConfig;

/**
 * JMeter custom sampler to invoke methods on our ApiGateway API.
 *
 * This sampler requires the following parameter inputs from JMeter:
 * <ul>
 * <li>ACCESS_KEY_ID</li>
 * <li>SECRET_KEY</li>
 * <li>SESSION_TOKEN</li>
 * <li>APIGATEWAY_BASE_URL</li>
 * <li>REGION</li>
 * <li>BOOKING_NAME</li>
 * <li>COURT</li>
 * <li>COURTSPAN</li>
 * <li>SLOT</li>
 * <li>SLOTSPAN</li>
 * <li>DATE</li>
 * <li>PUT_OR_DELETE</li>
 * </ul>
 */
public class SquashBookingApiCustomSampler extends AbstractJavaSamplerClient {

  private Squash client;
  private String apigatewayBaseUrl;
  private String accessKeyId;
  private String secretKey;
  private String sessionToken;
  private String bookingName;
  private String court;
  private String courtSpan;
  private String slot;
  private String slotSpan;
  private String date;
  private String putOrDelete;
  private String samplerName;

  /**
   * Default constructor.
   *
   * The Java Sampler uses the default constructor to instantiate an instance
   * of the client class.
   */
  public SquashBookingApiCustomSampler() {
  }

  /**
   * Do any initialization required by this client. In this case,
   * initialization consists of getting the value of the AWS region
   * and credentials parameters.
   *
   * @param context
   *            the context to run with. This provides access to
   *            initialization parameters.
   */
  @Override
  public void setupTest(JavaSamplerContext context) {
    apigatewayBaseUrl = context.getParameter("APIGATEWAY_BASE_URL");
    accessKeyId = context.getParameter("ACCESS_KEY_ID");
    secretKey = context.getParameter("SECRET_KEY");
    sessionToken = context.getParameter("SESSION_TOKEN");

    AWSCredentials awsCredentials = new BasicSessionCredentials(accessKeyId, secretKey,
        sessionToken);
    AWSStaticCredentialsProvider awsStaticCredentialsProvider = new AWSStaticCredentialsProvider(
        awsCredentials);

    String region = context.getParameter("REGION");
    client = Squash.builder().iamRegion(region)
        .iamCredentials((AWSCredentialsProvider) awsStaticCredentialsProvider).build();
    samplerName = context.getParameter(TestElement.NAME);
    bookingName = context.getParameter("BOOKING_NAME");
    court = context.getParameter("COURT");
    courtSpan = context.getParameter("COURTSPAN");
    slot = context.getParameter("SLOT");
    slotSpan = context.getParameter("SLOTSPAN");
    date = context.getParameter("DATE");
    putOrDelete = context.getParameter("PUT_OR_DELETE");
  }

  /**
   * Perform a single sample. In this case, this will book or cancel the
   * specified court(s). This method returns a <code>SampleResult</code>
   * object. <code>SampleResult</code> has many fields which can be used. At a
   * minimum, the test should use <code>SampleResult.sampleStart</code> and
   * <code>SampleResult.sampleEnd</code>to set the time that the test
   * required to execute. It is also a good idea to set the sampleLabel and
   * the successful flag.
   *
   * @see org.apache.jmeter.samplers.SampleResult#sampleStart()
   * @see org.apache.jmeter.samplers.SampleResult#sampleEnd()
   * @see org.apache.jmeter.samplers.SampleResult#setSuccessful(boolean)
   * @see org.apache.jmeter.samplers.SampleResult#setSampleLabel(String)
   *
   * @param context
   *            the context to run with. This provides access to
   *            initialization parameters.
   *
   * @return a SampleResult giving the results of this sample.
   */
  @Override
  public SampleResult runTest(JavaSamplerContext context) {
    SampleResult result = new SampleResult();
    result.setSampleLabel(samplerName);

    try {
      // Record sample start time.
      result.sampleStart();

      // Attempt to book a court.
      PutBookingsRequest putBookingsRequest = new PutBookingsRequest();
      BookingMutationInputModel bookingMutationInputModel = new BookingMutationInputModel()
          .putOrDelete(putOrDelete).court(court).courtSpan(courtSpan).slot(slot).slotSpan(slotSpan)
          .name(bookingName).date(date).password("pAssw0rd").apiGatewayBaseUrl(apigatewayBaseUrl)
          .redirecUrl("http://dummy");
      PutBookingsResult putBookingsResult = client.putBookings(putBookingsRequest
          .bookingMutationInputModel(bookingMutationInputModel).sdkRequestConfig(
              SdkRequestConfig.builder().httpRequestTimeout(10000).totalExecutionTimeout(10000)
                  .build()));

      result.setResponseCode(Integer.toString(putBookingsResult.sdkResponseMetadata()
          .httpStatusCode()));
      result.setResponseMessage("OK");
      result.setSuccessful(true);
    } catch (SdkClientException e) {
      // For now we use this as marker that court slot was already booked. (As
      // can't yet work out how to get Apigateway generated SDK to return custom
      // exceptions).
      result.setSuccessful(true);
      result.setResponseMessage(e.getMessage());
      result.setResponseCode("400");
    } catch (Exception e) {
      // Do not expect to get here.
      result.setSuccessful(false);
      result.setResponseMessage(e.toString());
    } finally {
      result.sampleEnd();
    }

    return result;
  }

  /**
   * Provide a list of parameters which this test supports. Any parameter
   * names and associated values returned by this method will appear in the
   * GUI by default so the user doesn't have to remember the exact names. The
   * user can add other parameters which are not listed here. If this method
   * returns null then no parameters will be listed. If the value for some
   * parameter is null then that parameter will be listed in the GUI with an
   * empty value.
   *
   * @return a specification of the parameters used by this test which should
   *         be listed in the GUI, or null if no parameters should be listed.
   */
  @Override
  public Arguments getDefaultParameters() {
    Arguments params = new Arguments();
    params.addArgument("APIGATEWAY_BASE_URL", "${APIGATEWAY_BASE_URL}");
    params.addArgument("ACCESS_KEY_ID", "${ACCESS_KEY_ID}");
    params.addArgument("SECRET_KEY", "${SECRET_KEY}");
    params.addArgument("SESSION_TOKEN", "${SESSION_TOKEN}");
    params.addArgument("REGION", "${REGION}");
    params.addArgument("BOOKING_NAME", "${BOOKING_NAME}");
    params.addArgument("COURT", "${COURT}");
    params.addArgument("COURTSPAN", "${COURTSPAN}");
    params.addArgument("SLOT", "${SLOT}");
    params.addArgument("SLOTSPAN", "${SLOTSPAN}");
    params.addArgument("DATE", "${DATE}");
    params.addArgument("PUT_OR_DELETE", "${PUT_OR_DELETE}");
    return params;
  }
}