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

Feature: Courts can be booked

    As a squash player
    In order to ensure a court is available
    I want to be able to book courts online

  Scenario: Free courts can be booked at any time

      Happy path of booking a court at a 'random' time

      Given I have navigated to the squash booking page
      When I view bookings for today
      Then court 3 should not be booked at 12:15 PM today

      When I book court 3 at 12:15 PM today
      Then court 3 should be booked at 12:15 PM today
      # Verify this is the only booked court
      And there should be 1 booked court today

  Scenario: Booking a court with the correct password succeeds

      Given I have navigated to the squash booking page
      When I view bookings for today
      Then court 4 should not be booked at 6:15 PM today

      # Use the correct password
      When I book court 4 at 6:15 PM today using password pAssw0rd
      Then I should be taken to the squash booking page
      And court 4 should be booked at 6:15 PM today

  Scenario: Booking a court with an incorrect password fails

      Given I have navigated to the squash booking page
      When I view bookings for today
      Then court 4 should not be booked at 6:15 PM today

      # Use an incorrect password
      When I attempt to book court 4 at 6:15 PM today using password PASSword
      Then I should be taken to the error page
      And court 4 should not be booked at 6:15 PM today

  Scenario: Booking a court with player names in the correct format succeeds

      Given I have navigated to the squash booking page
      And I have viewed bookings for today

      # Use player names in correct format
      When I book court 4 at 6:15 PM today for A.Shabana and J.Power
      Then I should be taken to the squash booking page
      And court 4 should be booked at 6:15 PM today

  Scenario Outline: Booking a court with player names in an incorrect format fails

      Given I have navigated to the squash booking page
      And I have viewed bookings for today

      When I attempt to book court 4 at 6:15 PM today for <player1> and <player2>
      Then I should be taken to the error page
      And court 4 should not be booked at 6:15 PM today

      Examples:
      |Name                  |  player1  |  player2  |
      |No surnames           |    Amr    | Jonathan  |
      |Only one surname      | A.Shabana |   Power   |
      |Second Name missing   |   Guest   |           |
      |First Name missing    |           |   League  |

  @ignore
  Scenario: Already booked courts cannot be booked again
  
  #At least not without cancelling them first

  Scenario: Start times of court booking slots are correct

      Given I have navigated to the squash booking page
      Then court start times should be every 45 minutes between 10:00 AM and 9:15 PM inclusive

  Scenario: Courts cannot be booked for dates in the past

      Given I have navigated to the squash booking page
      When I attempt to view bookings for the earliest date
      Then I should be shown bookings for today

  Scenario: Courts cannot be booked for dates too far in the future

      Given I have navigated to the squash booking page
      When I attempt to view bookings for the most future date
      Then I should be shown bookings for a date 20 days in the future

  Scenario: Booking a court does not alter existing bookings

      Book two courts and verify booking second court does not unbook the first

      Given I have navigated to the squash booking page
      And I have viewed bookings for today
      And I have booked court 3 at 12:15 PM today

      # Make the second booking that should not affect the first one
      When I attempt to view bookings for a date 4 days in the future
      And I book court 5 at 10:00 AM

      # Verify first booking is unaltered...
      When I view bookings for today
      Then court 3 should be booked at 12:15 PM
      And there should be 1 booked court

      # ...and second booking is still present
      When I attempt to view bookings for a date 4 days in the future
      Then court 5 should be booked at 10:00 AM
      And there should be 1 booked court

  @ignore
  Scenario: Something about trying to book a crt that someone else has booked since last page refresh

  @ignore
  Scenario: Court time slots are coloured to indicate if they are booked

  @ignore
  Scenario: Court time slots display text to indicate if they are booked