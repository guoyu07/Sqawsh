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

package steps.argumenttransforms;

import cucumber.api.Transformer;

import java.time.format.DateTimeFormatter;

/**
 * Argument transforms for scenario steps.
 * 
 * @author robinsteel19@outlook.com (Robin Steel)
 */
public class StepArgumentTransforms {

  public static class LocalTimeConverter extends Transformer<java.time.LocalTime> {

    private static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    @Override
    public java.time.LocalTime transform(String value) {
      return java.time.LocalTime.parse(value, FORMATTER);
    }
  }
}
