/**
 * Copyright 2016-2017 Robin Steel
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
            builder.setBookings([{'court': 1, 'courtSpan': 1, 'slot': 2, 'slotSpan': 1, 'name': 'H.Ashour/H.AckerTDog'}, {'court': 1, 'courtSpan': 1, 'slot': 3, 'slotSpan': 2, 'name': 'A.Booking/B.Lock'}])
            builder.setSelectedDate('2016-04-23')
          } else {
            builder.setBookings([{'court': 2, 'courtSpan': 1, 'slot': 4, 'slotSpan': 1, 'name': 'J.Khan/J.Barrington'}, {'court': 1, 'courtSpan': 3, 'slot': 5, 'slotSpan': 1, 'name': 'A.Different/B.Ooking'}])
            builder.setSelectedDate('2016-04-24')
          }
          builder.setLifecycleState('ACTIVE')
          builder.setForwardingUrl('http://www.bbc.co.uk')
          resolve(builder)
        })
      },
      getBookings: function (builder) {
        return $q(function (resolve, reject) {
          var date = builder.getSelectedDate()
          if (date === '2016-04-23') {
            builder.setBookings([{'court': 2, 'courtSpan': 1, 'slot': 3, 'slotSpan': 1, 'name': 'R.Ashour/J.Power'}, {'court': 3, 'courtSpan': 3, 'slot': 2, 'slotSpan': 3, 'name': 'A.Block/B.Ooking'}])
            builder.setSelectedDate('2016-04-23')
          } else {
            builder.setBookings([{'court': 3, 'courtSpan': 1, 'slot': 1, 'slotSpan': 1, 'name': 'R.Ashour/G.Gaultier'}, {'court': 1, 'courtSpan': 2, 'slot': 1, 'slotSpan': 1, 'name': 'A.Nother/B.Lock'}])
            builder.setSelectedDate('2016-04-24')
          }
          builder.setLifecycleState('ACTIVE')
          builder.setForwardingUrl('http://www.bbc.co.uk')
          resolve(builder)
        })
      },
      getBookingRules: function () {
        return $q(function (resolve, reject) {
          var bookingRules = [
            {
              booking: {name: 'Sunday recurring rule', court: 3, courtSpan: 2, slot: 1, slotSpan: 5, date: '2016-10-02'},
              isRecurring: true,
              datesToExclude: ['2016-07-02']
            },
            {
              booking: {name: 'Tuesday non-recurring rule', court: 1, courtSpan: 2, slot: 10, slotSpan: 3, date: '2016-10-04'},
              isRecurring: false,
              datesToExclude: []
            }
          ]
          resolve({
            'bookingRules': bookingRules,
            'lifecycleState': 'ACTIVE',
            'forwardingUrl': ''
          })
        })
      },
      createBookingRule: function (name, court, courtSpan, timeSlot, timeSlotSpan, date, isRecurring) {
        return $q(function (resolve, reject) {
          resolve()
        })
      },
      addRuleExclusion: function (bookingRule, dateToExclude) {
        return $q(function (resolve, reject) {
          resolve()
        })
      },
      deleteBookingRule: function (bookingRuleToDelete) {
        return $q(function (resolve, reject) {
          resolve()
        })
      },
      deleteRuleExclusion: function (bookingRule, dateToExclude) {
        return $q(function (resolve, reject) {
          resolve()
        })
      },
      reserveCourt: function (court, slot, date, name, password) {
        return $q(function (resolve, reject) {
          resolve()
        })
      },
      cancelCourt: function (court, slot, date, name, password) {
        return $q(function (resolve, reject) {
          resolve()
        })
      }
    }
  }])
