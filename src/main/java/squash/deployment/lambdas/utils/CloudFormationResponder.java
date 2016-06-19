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

package squash.deployment.lambdas.utils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sends response to AWS Cloudformation from custom resources.
 * 
 * <p>All lambda-backed Cloudformation custom resource lambdas use this class
 *    to send their response to Cloudformation after completing their work
 *    to create, update, or delete the custom resource.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class CloudFormationResponder {

  private Map<String, String> requestParameters;
  private String physicalResourceId;
  private JsonNodeFactory factory;
  private JsonFactory jsonFactory;
  private ByteArrayOutputStream cloudFormationJsonResponse;
  private PrintStream printStream;
  private JsonGenerator generator;
  private ObjectMapper mapper;
  private ObjectNode rootNode;
  private ObjectNode dataOutputsNode;
  private boolean initialised;

  /**
   *  Constructs the responder.
   *  
   *  <p>The request parameters provide, inter alia, the presigned
   *     Url to which to send the response.
   *  
   *  @param requestParameters the parameters provided by Cloudformation in its request.
   *  @param physicalResourceId the physical id of the custom resource.
   */
  public CloudFormationResponder(Map<String, String> requestParameters, String physicalResourceId) {
    this.requestParameters = requestParameters;
    this.physicalResourceId = physicalResourceId;
    this.initialised = false;
  }

  /**
   *  Initialises the responder.
   *  
   *  <p>This must be called before adding properties to the response or sending the response
   */
  public void initialise() throws IOException {
    // Create the node factory that gives us nodes.
    this.factory = new JsonNodeFactory(false);
    // create a json factory to write the treenode as json.
    this.jsonFactory = new JsonFactory();
    this.cloudFormationJsonResponse = new ByteArrayOutputStream();
    this.printStream = new PrintStream(cloudFormationJsonResponse);
    this.generator = jsonFactory.createGenerator(printStream);
    this.mapper = new ObjectMapper();
    this.rootNode = factory.objectNode();
    this.dataOutputsNode = factory.objectNode();
    this.rootNode.set("Data", this.dataOutputsNode);
    this.initialised = true;
  }

  public void setPhysicalResourceId(String physicalResourceId) {
    this.physicalResourceId = physicalResourceId;
  }

  /**
   *  Adds a key-value property to the CloudFormation response.
   *  
   *  @param key the property key.
   *  @param value the property value.
   *  @throws IllegalStateException when the responder is uninitialised.
   */
  public void addKeyValueOutputsPair(String key, String value) {
    if (!initialised) {
      throw new IllegalStateException("The responder has not been initialised");
    }

    dataOutputsNode.put(key, value);
  }

  /**
   *  Sends the custom resource response to the Cloudformation service.
   *  
   *  <p>The response is returned indirectly to Cloudformation via the
   *     presigned Url it provided in its request.
   *  
   *  @param status whether the call succeeded - must be either SUCCESS or FAILED.
   *  @param logger a CloudwatchLogs logger.
   *  @throws IllegalStateException when the responder is uninitialised.
   */
  public void sendResponse(String status, LambdaLogger logger) {
    if (!initialised) {
      throw new IllegalStateException("The responder has not been initialised");
    }

    try {
      rootNode.put("Status", status);
      rootNode.put("RequestId", requestParameters.get("RequestId"));
      rootNode.put("StackId", requestParameters.get("StackId"));
      rootNode.put("LogicalResourceId", requestParameters.get("LogicalResourceId"));
      rootNode.put("PhysicalResourceId", physicalResourceId);
    } catch (Exception e) {
      // Can do nothing more than log the error and return. Must rely on
      // CloudFormation timing-out since it won't get a response from us.
      logger.log("Exception caught whilst constructing response: " + e.toString());
      return;
    }

    // Send the response to CloudFormation via the provided presigned S3 URL
    logger.log("About to send response to presigned URL: " + requestParameters.get("ResponseURL"));
    try {
      URL url = new URL(requestParameters.get("ResponseURL"));
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("PUT");
      OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());

      mapper.writeTree(generator, rootNode);
      String output = cloudFormationJsonResponse.toString(StandardCharsets.UTF_8.name());

      logger.log("Response about to be sent: " + output);
      out.write(output);
      out.close();
      logger.log("Sent response to presigned URL");
      int responseCode = connection.getResponseCode();
      logger.log("Response Code returned from presigned URL: " + responseCode);
    } catch (IOException e) {
      // Can do nothing more than log the error and return.
      logger.log("Exception caught whilst replying to presigned URL: " + e.toString());
      return;
    }
  }
}