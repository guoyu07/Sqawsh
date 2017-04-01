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

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;

import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentity;
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentityClientBuilder;
import com.amazonaws.services.cognitoidentity.model.Credentials;
import com.amazonaws.services.cognitoidentity.model.GetCredentialsForIdentityRequest;
import com.amazonaws.services.cognitoidentity.model.GetCredentialsForIdentityResult;
import com.amazonaws.services.cognitoidentity.model.GetIdRequest;
import com.amazonaws.services.cognitoidentity.model.GetIdResult;

/**
 * JMeter custom sampler to get temporary AWS credentials from a
 * Cognito identity pool. The credentials are needed for later
 * sampling of the ApiGateway API. This could perhaps be in the
 * setup method of that sampler instead?
 *
 * This sampler requires the following parameter inputs from JMeter:
 * <ul>
 * <li>COGNITO_IDENTITY_POOL_ID - id of the Cognito Identity Pool to query for credentials.</li>
 * <li>REGION - the AWS region where this identity pool resides, e.g. eu-west-1.</li>
 * </ul>
 * 
 * And it returns a JMeter sample with a Json body with the following properties:
 * <ul>
 * <li>AccessKeyId - AWS temporary Access Key Id.</li>
 * <li>SecretKey - AWS temporary Secret Key.</li>
 * <li>SessionToken - AWS temporary Session Token.</li>
 * </ul>
 *
 */
public class CognitoIdentityPoolCustomSampler extends AbstractJavaSamplerClient {

  private AmazonCognitoIdentity client;
  private String region;
  private String cognitoIdentityPoolId;
  private String samplerName;

  /**
   * Default constructor.
   *
   * The Java Sampler uses the default constructor to instantiate an instance
   * of the client class.
   */
  public CognitoIdentityPoolCustomSampler() {
  }

  /**
   * Do any initialization required by this client. In this case,
   * initialization consists of getting the value of the pool id and AWS region
   * parameter. It is generally recommended to do any initialization such as
   * getting parameter values in the setupTest method rather than the runTest
   * method in order to add as little overhead as possible to the test.
   *
   * @param context
   *            the context to run with. This provides access to
   *            initialization parameters.
   */
  @Override
  public void setupTest(JavaSamplerContext context) {
    region = context.getParameter("REGION");
    cognitoIdentityPoolId = context.getParameter("COGNITO_IDENTITY_POOL_ID");
    client = AmazonCognitoIdentityClientBuilder.standard().withRegion(region).build();
    samplerName = context.getParameter(TestElement.NAME);
  }

  /**
   * Perform a single sample. In this case, this method will query
   * the provided Cognito identity pool for temporary credentials. This
   * method returns a <code>SampleResult</code> object.
   * <code>SampleResult</code> has many fields which can be used. At a
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

      // Set these credentials into the response body in Json format:
      String responseBodyJson = "{\"SecretKey\":" + secretKey + ", \"AccessKeyID\":" + accessKeyId
          + ", \"SessionToken\":" + sessionToken + "}";
      result.setResponseData(responseBodyJson, "UTF-8");

      // Set message to "OK", response code to "200", and result state to
      // successful.
      result.setResponseOK();
    } catch (Exception e) {
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
    params.addArgument("COGNITO_IDENTITY_POOL_ID", "${COGNITO_IDENTITY_POOL_ID}");
    params.addArgument("REGION", "${REGION}");
    return params;
  }
}