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

angular.module('squashApp.bookingsService', [])

  .factory('BookingService', ['$q', function ($q) {
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
    var comSquashIdentityPoolId = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashApiGatewayBaseUrl = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashWebsiteBucket = 'stringtobereplaced' // will be replaced at stack creation time
    AWS.config.region = comSquashRegion // Region
    AWS.config.credentials = new AWS.CognitoIdentityCredentials({
      IdentityPoolId: comSquashIdentityPoolId
    })
    // Workaround for occasional 504 timeouts from API Gateway bc 10-second timeout.
    // N.B. Lambda can be sluggish on cold-starts. May need tweaking.
    AWS.config.maxRetries = 4

    // AWS ApiGateway client
    var apigClient
    var getApigClient = function () {
      return $q(function (resolve, reject) {
        // Return existing client if we have one...
        if (apigClient) {
          resolve(apigClient)
        }

        // ...otherwise create a new one with new guest credentials from Cognito
        AWS.config.credentials.get(function (error) {
          if (error) {
            reject(error)
          }
          resolve(apigClientFactory.newClient({
            accessKey: AWS.config.credentials.accessKeyId,
            secretKey: AWS.config.credentials.secretAccessKey,
            sessionToken: AWS.config.credentials.sessionToken,
            region: comSquashRegion
          }))
        })
      })
    }

    // AWS S3 client
    var s3Client
    var getS3Client = function () {
      return $q(function (resolve, reject) {
        // Return existing client if we have one...
        if (s3Client) {
          resolve(s3Client)
        }

        // ...otherwise create a new one with new guest credentials from Cognito
        AWS.config.credentials.get(function (error) {
          if (error) {
            reject(error)
          }
          resolve(new AWS.S3({
            accessKeyId: AWS.config.credentials.accessKeyId,
            secretKey: AWS.config.credentials.secretAccessKey,
            sessionToken: AWS.config.credentials.sessionToken,
            region: comSquashRegion
          }))
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
      reserveCourt: function (court, slot, date, player1, player2, password) {
        var booking = {
          'putOrDelete': 'PUT',
          'court': court,
          'courtSpan': 1,
          'slot': slot,
          'slotSpan': 1,
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
            if ((result.data != null) && (result.data.hasOwnProperty('errorMessage'))) {
              // The booking failed
              throw result.data.errorMessage
            }
          })
          .catch(function (error) {
            throw error
          })
      },
      cancelCourt: function (court, slot, date, players, password) {
        var booking = {
          'putOrDelete': 'DELETE',
          'court': court,
          'courtSpan': 1,
          'slot': slot,
          'slotSpan': 1,
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
            if ((result.data != null) && (result.data.hasOwnProperty('errorMessage'))) {
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
