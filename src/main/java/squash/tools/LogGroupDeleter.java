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

package squash.tools;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.DeleteLogGroupRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.LogGroup;

import java.util.List;

/**
 * Helper to clean up CloudwatchLogs by removing all log groups.
 * 
 * <p>CAUTION: This will delete *ALL* log groups on your account for the specified region!!!
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class LogGroupDeleter {

  public static void main(String[] args) {
    AWSLogs client = new AWSLogsClient();
    client.setRegion(Region.getRegion(Regions.EU_WEST_1));

    // Harvest all the log groups in this region
    DescribeLogGroupsRequest describeLogGroupsRequest = new DescribeLogGroupsRequest();
    DescribeLogGroupsResult describeLogGroupsResult = client
        .describeLogGroups(describeLogGroupsRequest);
    List<LogGroup> logGroups = describeLogGroupsResult.getLogGroups();
    String token = describeLogGroupsResult.getNextToken();
    int index = 0;
    while (token != null) {
      index++;
      describeLogGroupsRequest.setNextToken(token);
      describeLogGroupsResult = client.describeLogGroups(describeLogGroupsRequest);
      logGroups.addAll(describeLogGroupsResult.getLogGroups());
      token = describeLogGroupsResult.getNextToken();
      System.out.println(index);
      System.out.println(token);
    }

    // Delete each log group
    logGroups.stream().forEach(
        (logGroup) -> {
          DeleteLogGroupRequest deleteLogGroupRequest = new DeleteLogGroupRequest(logGroup
              .getLogGroupName());
          client.deleteLogGroup(deleteLogGroupRequest);
        });
  }
}