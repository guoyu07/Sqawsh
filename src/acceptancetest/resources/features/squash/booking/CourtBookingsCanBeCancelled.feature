# Copyright 2015-2016 Robin Steel
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

Feature: Court bookings can be cancelled

As a squash player
In order to ensure I don't stop others using courts
I want to be able to cancel courts I've booked

  Scenario: Cancelling a court with the correct password succeeds

      Given I have navigated to the squash booking page
      And I have booked court 3 at 12:15 PM today
      Then court 3 should be booked at 12:15 PM today

      # Use the correct password
      When I cancel court 3 at 12:15 PM today using password pAssw0rd
      Then I should be taken to the squash booking page
      And court 3 should not be booked at 12:15 PM today

  Scenario: Cancelling a court with an incorrect password fails

      Given I have navigated to the squash booking page
      And I have booked court 3 at 12:15 PM today
      Then court 3 should be booked at 12:15 PM today

      # Use an incorrect password
      When I attempt to cancel court 3 at 12:15 PM today using password PASSword
      Then I should be taken to the error page
      And court 3 should be booked at 12:15 PM today

  Scenario Outline: Players names must be reentered to cancel a booking

      This is to avoid accidentally cancelling the wrong booking

      Given I have navigated to the squash booking page
      And I have booked court 3 at 12:15 PM today
      Then court 3 should be booked at 12:15 PM today

      When I <attempt_to_cancel> court 3 at 12:15 PM today for <players_names>
      Then I <should_or_not> receive feedback that the cancellation details were invalid
      And court 3 <should_or_not> be booked at 12:15 PM today

      Examples:
      |name         |attempt_to_cancel |players_names      |should_or_not|
      |correct names|cancel            |A.Shabana/J.Power  |should not   |
      |wrong names  |attempt to cancel |A.Shabs/J.Powerless|should       |