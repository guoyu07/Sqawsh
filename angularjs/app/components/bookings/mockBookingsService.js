/**
 * Copyright 2016 Robin Steel
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

'use strict'

angular.module('squashApp.bookingsService', [])
  .factory('BookingService', ['$q', function ($q) {
    return {
      getCourtNumbers: function () { return [1, 2, 3, 4] },
      getTimeSlots: function () { return ['9:15 AM', '10:15 AM', '11:15 AM', '12:15 PM', '1:15 PM'] },
      getValidDates: function () {
        return $q(function (resolve, reject) {
          resolve(['2016-04-23', '2016-04-24'])
        })
      },
      getBookings: function (date) {
        return $q(function (resolve, reject) {
          if (date === '2016-04-23') {
            resolve({
              bookings: [{'court': 2, 'slot': 3, 'players': 'R.Ashour/J.Power'}],
              date: '2016-04-23'
            })
          } else {
            resolve({
              bookings: [{'court': 3, 'slot': 1, 'players': 'R.Ashour/G.Gaultier'}],
              date: '2016-04-24'
            })
          }
        })
      },
      reserveCourt: function (court, slot, date, player1, player2, password) {
        return $q(function (resolve, reject) {
          resolve()
        })
      },
      cancelCourt: function (court, slot, date, players, password) {
        return $q(function (resolve, reject) {
          resolve()
        })
      }
    }
  }])
