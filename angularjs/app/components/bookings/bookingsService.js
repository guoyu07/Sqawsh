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

/* global AWS, apigClientFactory */

'use strict'

angular.module('squashApp.bookingsService', ['squashApp.identityService'])
  .factory('BookingService', ['$q', '$filter', 'IdentityService', function ($q, $filter, IdentityService) {
    // Set up court numbers and booking time slots
    var numCourts = 5
    var courtNumbers = Array(numCourts).fill(0).map((x, i, a) => (i + 1))

    var numSlots = 16
    var timeSlots = Array(numSlots).fill(new Date(2000, 1, 1, 10, 0, 0))
    for (var i = 1; i < timeSlots.length; i++) {
      timeSlots[i] = new Date(timeSlots[i - 1])
      timeSlots[i].setMinutes(timeSlots[i].getMinutes() + 45)
    }

    // Initialize the Amazon Cognito credentials provider for calling AWS ApiGateway
    var comSquashRegion = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashApiGatewayBaseUrl = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashWebsiteBucket = 'stringtobereplaced' // will be replaced at stack creation time
    AWS.config.region = comSquashRegion // Region

    // Workaround for occasional 504 timeouts from API Gateway bc 10-second timeout.
    // N.B. Lambda can be sluggish on cold-starts. May need tweaking.
    AWS.config.maxRetries = 4

    // AWS ApiGateway client
    var apigClient
    var getApigClient = function () {
      return $q(function (resolve, reject) {
        IdentityService.updateCredentials().then(function () {
          // For now, create new client each time to ensure credentials are current.
          // N.B. This client does not support credentials key in its config.
          apigClient = apigClientFactory.newClient({
            accessKey: AWS.config.credentials.accessKeyId,
            secretKey: AWS.config.credentials.secretAccessKey,
            sessionToken: AWS.config.credentials.sessionToken,
            region: comSquashRegion
          })
          resolve(apigClient)
        })
      })
    }

    // AWS S3 client
    var s3Client
    var getS3Client = function () {
      return $q(function (resolve, reject) {
        IdentityService.updateCredentials().then(function () {
          // Return existing client if we have one...
          if (s3Client) {
            resolve(s3Client)
          }

          // ...otherwise create a new one with credentials from Cognito
          s3Client = new AWS.S3({
            credentials: AWS.config.credentials,
            region: comSquashRegion
          })
          resolve(s3Client)
        })
      })
    }

    // Start asynchronous load of famous players list
    var allFamousPlayers = 'undefined'
    getS3Client()
      .then(function (client) {
        // Query AWS for the list of famous players
        return client.getObject({Bucket: comSquashWebsiteBucket, Key: 'famousplayers.json'}).promise()
      })
      .then(function (players) {
        allFamousPlayers = JSON.parse(players.Body.toString()).famousplayers
      })
      .catch(function (error) {
        // Just swallow any errors - we can use the default famous players instead
        console.dir(error)
        return
      })

    // Our custom error type - allowing us to pass back the builder as well as the error
    function BookingServiceError (error, builder) {
      this.name = 'BookingServiceError'
      this.message = 'Booking service error'
      this.stack = (new Error()).stack
      this.error = error
      this.builder = builder
    }
    BookingServiceError.prototype = Object.create(Error.prototype)
    BookingServiceError.prototype.constructor = BookingServiceError

    return {
      getCourtNumbers: function () { return courtNumbers },
      getTimeSlots: function () { return timeSlots },
      getTwoFamousPlayers: function () {
        if (typeof allFamousPlayers === 'undefined') {
          // Full list not yet loaded - so return default players
          return ['A.Shabana', 'J.Power']
        }
        // Choose 2 famous players at random - ensuring they're different
        var index1 = Math.floor(Math.random() * allFamousPlayers.length)
        var index2
        do {
          index2 = Math.floor(Math.random() * allFamousPlayers.length)
        } while (index2 === index1)
        return [allFamousPlayers[index1], allFamousPlayers[index2]]
      },
      getCachedValidDates: function (builder) {
        return getS3Client()
          .then(function (client) {
            // Query AWS for the currently valid dates for viewing/mutating bookings
            return client.getObject({Bucket: comSquashWebsiteBucket, Key: 'validdates.json'}).promise()
          })
          .then(function (response) {
            // Array of valid dates in YYYY-MM-DD format
            builder.setValidDates(JSON.parse(response.Body.toString()).dates)
            return builder
          })
          .catch(function (error) {
            throw new BookingServiceError(error, builder)
          })
      },
      getValidDates: function (builder) {
        return getApigClient()
          .then(function (client) {
            // Query AWS for the currently valid dates for viewing/mutating bookings
            var params = {}
            var body = {}
            var additionalParams = {}
            return client.validdatesGet(params, body, additionalParams)
          })
          .then(function (response) {
            // Array of valid dates in YYYY-MM-DD format
            builder.setValidDates(response.data.dates)
            return builder
          })
          .catch(function (error) {
            throw new BookingServiceError(error, builder)
          })
      },
      getCachedBookings: function (builder) {
        // Return the bookings for the specified date
        return getS3Client()
          .then(function (client) {
            return client.getObject({Bucket: comSquashWebsiteBucket, Key: builder.getSelectedDate() + '.json'}).promise()
          })
          .then(function (response) {
            builder.setBookings(JSON.parse(response.Body.toString()).bookings)
            return builder
          })
          .catch(function (error) {
            throw new BookingServiceError(error, builder)
          })
      },
      getBookings: function (builder) {
        // Return the bookings for the specified date
        return getApigClient()
          .then(function (client) {
            var params = {'date': builder.getSelectedDate()}
            var body = {}
            var additionalParams = {}
            return client.bookingsGet(params, body, additionalParams)
          })
          .then(function (response) {
            builder.setBookings(response.data.bookings)
            return builder
          })
          .catch(function (error) {
            throw new BookingServiceError(error, builder)
          })
      },
      getBookingRules: function () {
        // Return the booking rules
        return getApigClient()
          .then(function (client) {
            var params = {}
            var body = {}
            var additionalParams = {}
            return client.bookingrulesGet(params, body, additionalParams)
          })
          .then(function (response) {
            if ((response.data !== null) && (response.data.hasOwnProperty('errorMessage'))) {
              // The booking rule fetch failed
              throw response.data.errorMessage
            }
            return response.data.bookingRules
          })
          .catch(function (error) {
            throw error
          })
      },
      createBookingRule: function (name, court, courtSpan, timeSlot, timeSlotSpan, date, isRecurring) {
        var bookingRule = {
          'booking': {
            'players': name,
            'court': court,
            'courtSpan': courtSpan,
            'slot': (timeSlots.indexOf(timeSlot) + 1),
            'slotSpan': timeSlotSpan,
            'date': $filter('date')(date, 'yyyy-MM-dd')
          },
          'isRecurring': isRecurring,
          'datesToExclude': []
        }
        var params = {}
        var body = {
          'putOrDelete': 'PUT',
          'bookingRule': bookingRule,
          'dateToExclude': ''
        }
        var additionalParams = {}
        return getApigClient()
          .then(function (client) {
            return client.bookingrulesPut(params, body, additionalParams)
          })
          .then(function (result) {
            if ((result.data !== null) && (result.data.hasOwnProperty('errorMessage'))) {
              // The booking rule creation failed
              throw result.data.errorMessage
            }
          })
          .catch(function (error) {
            throw error
          })
      },
      addRuleExclusion: function (bookingRule, dateToExclude) {
        var params = {}
        var body = {
          'putOrDelete': 'PUT',
          'bookingRule': bookingRule,
          'dateToExclude': dateToExclude
        }
        var additionalParams = {}
        return getApigClient()
          .then(function (client) {
            return client.bookingrulesPut(params, body, additionalParams)
          })
          .then(function (result) {
            if ((result.data !== null) && (result.data.hasOwnProperty('errorMessage'))) {
              // The booking rule exclusion addition failed
              throw result.data.errorMessage
            }
          })
          .catch(function (error) {
            throw error
          })
      },
      deleteBookingRule: function (bookingRuleToDelete) {
        var params = {}
        var body = {
          'putOrDelete': 'DELETE',
          'bookingRule': bookingRuleToDelete,
          'dateToExclude': ''
        }
        var additionalParams = {}
        return getApigClient()
          .then(function (client) {
            return client.bookingrulesDelete(params, body, additionalParams)
          })
          .then(function (result) {
            if ((result.data !== null) && (result.data.hasOwnProperty('errorMessage'))) {
              // The booking rule deletion failed
              throw result.data.errorMessage
            }
          })
          .catch(function (error) {
            throw error
          })
      },
      deleteRuleExclusion: function (bookingRule, dateToExclude) {
        var params = {}
        var body = {
          'putOrDelete': 'DELETE',
          'bookingRule': bookingRule,
          'dateToExclude': dateToExclude
        }
        var additionalParams = {}
        return getApigClient()
          .then(function (client) {
            return client.bookingrulesDelete(params, body, additionalParams)
          })
          .then(function (result) {
            if ((result.data !== null) && (result.data.hasOwnProperty('errorMessage'))) {
              // The booking rule exclusion deletion failed
              throw result.data.errorMessage
            }
          })
          .catch(function (error) {
            throw error
          })
      },
      reserveCourt: function (court, courtSpan, slot, slotSpan, date, player1, player2, password) {
        var booking = {
          'putOrDelete': 'PUT',
          'court': court,
          'courtSpan': courtSpan,
          'slot': slot,
          'slotSpan': slotSpan,
          'player1name': player1,
          'player2name': player2,
          'date': date,
          'password': password,
          'apiGatewayBaseUrl': comSquashApiGatewayBaseUrl,
          'redirectUrl': 'http://dummy'
        }
        var params = {}
        var body = booking
        var additionalParams = {}
        return getApigClient()
          .then(function (client) {
            return client.bookingsPut(params, body, additionalParams)
          })
          .then(function (result) {
            if ((result.data !== null) && (result.data.hasOwnProperty('errorMessage'))) {
              // The booking failed
              throw result.data.errorMessage
            }
          })
          .catch(function (error) {
            throw error
          })
      },
      cancelCourt: function (court, courtSpan, slot, slotSpan, date, players, password) {
        var booking = {
          'putOrDelete': 'DELETE',
          'court': court,
          'courtSpan': courtSpan,
          'slot': slot,
          'slotSpan': slotSpan,
          'players': players,
          'date': date,
          'password': password,
          'apiGatewayBaseUrl': comSquashApiGatewayBaseUrl,
          'redirectUrl': 'http://dummy'
        }
        var params = {}
        var body = booking
        var additionalParams = {}
        return getApigClient()
          .then(function (client) {
            return client.bookingsDelete(params, body, additionalParams)
          })
          .then(function (result) {
            if ((result.data !== null) && (result.data.hasOwnProperty('errorMessage'))) {
              // The delete failed
              throw result.data.errorMessage
            }
          })
          .catch(function (error) {
            throw error
          })
      }
    }
  }])
