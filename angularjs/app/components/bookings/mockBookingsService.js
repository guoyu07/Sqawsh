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
      getCourtNumbers: function () { return [1, 2, 3, 4, 5] },
      getTimeSlots: function () { return ['9:15 AM', '10:15 AM', '11:15 AM', '12:15 PM', '1:15 PM'] },
      getTwoFamousPlayers: function () {
        return ['A.Shabana', 'J.Power']
      },
      getCachedValidDates: function (builder) {
        return $q(function (resolve, reject) {
          builder.setValidDates(['2016-04-23', '2016-04-24'])
          resolve(builder)
        })
      },
      getValidDates: function (builder) {
        return $q(function (resolve, reject) {
          builder.setValidDates(['2016-04-23', '2016-04-24'])
          resolve(builder)
        })
      },
      getCachedBookings: function (builder) {
        return $q(function (resolve, reject) {
          var date = builder.getSelectedDate()
          if (date === '2016-04-23') {
            builder.setBookings([{'court': 1, 'courtSpan': 1, 'slot': 2, 'slotSpan': 1, 'players': 'H.Ashour/H.AckerTDog'}, {'court': 1, 'courtSpan': 1, 'slot': 3, 'slotSpan': 2, 'players': 'A.Booking/B.Lock'}])
            builder.setSelectedDate('2016-04-23')
            resolve(builder)
          } else {
            builder.setBookings([{'court': 2, 'courtSpan': 1, 'slot': 4, 'slotSpan': 1, 'players': 'J.Khan/J.Barrington'}, {'court': 1, 'courtSpan': 3, 'slot': 5, 'slotSpan': 1, 'players': 'A.Different/B.Ooking'}])
            builder.setSelectedDate('2016-04-24')
            resolve(builder)
          }
        })
      },
      getBookings: function (builder) {
        return $q(function (resolve, reject) {
          var date = builder.getSelectedDate()
          if (date === '2016-04-23') {
            builder.setBookings([{'court': 2, 'courtSpan': 1, 'slot': 3, 'slotSpan': 1, 'players': 'R.Ashour/J.Power'}, {'court': 3, 'courtSpan': 3, 'slot': 2, 'slotSpan': 3, 'players': 'A.Block/B.Ooking'}])
            builder.setSelectedDate('2016-04-23')
            resolve(builder)
          } else {
            builder.setBookings([{'court': 3, 'courtSpan': 1, 'slot': 1, 'slotSpan': 1, 'players': 'R.Ashour/G.Gaultier'}, {'court': 1, 'courtSpan': 2, 'slot': 1, 'slotSpan': 1, 'players': 'A.Nother/B.Lock'}])
            builder.setSelectedDate('2016-04-24')
            resolve(builder)
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
