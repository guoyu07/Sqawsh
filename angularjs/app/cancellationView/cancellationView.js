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

angular.module('squashApp.cancellationView', ['ngRoute', 'squashApp.bookingsService'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/cancellations', {
      templateUrl: 'cancellationView/cancellationView.html',
      controller: 'CancellationViewCtrl as ctrl'
    })
  }])

  .controller('CancellationViewCtrl', ['$scope', '$location', '$timeout', 'BookingService', function ($scope, $location, $timeout, BookingService) {
    var self = this

    self.activeCourt = BookingService.activeCourt
    self.activeSlot = BookingService.activeSlot
    self.activeSlotIndex = BookingService.activeSlotIndex
    self.activeDate = BookingService.activeDate
    self.player1 = BookingService.player1
    self.player2 = BookingService.player2
    self.players = BookingService.players

    self.returnToBookings = function () {
      $location.url('/bookings')
    }

    self.submitCancellation = function (form) {
      if (form.$invalid) {
        return
      }

      // The form is valid - so let's cancel the booking
      self.passwordIncorrect = false
      self.bookingCancellationFailed = false
      self.cancellationFailed = false
      BookingService.cancelCourt(self.activeCourt, self.activeSlotIndex + 1, self.activeDate, self.players, self.password)
        .then(function (result) {
          self.returnToBookings()
          updateUi()
        })
        .catch(function (error) {
          console.log('Cancellation failed with error: ')
          console.dir(error)
          if (typeof error.data !== 'undefined' && error.data.indexOf('The password is incorrect') > -1) {
            self.passwordIncorrect = true
          } else if (typeof error.data !== 'undefined' && error.data.indexOf('Booking cancellation failed') > -1) {
            self.bookingCancellationFailed = true
          }
          self.cancellationFailed = true
          updateUi()
        })
    }

    function updateUi () {
      // Wrap in timeout to avoid '$digest already in progress' error.
      $timeout(function () {
        $scope.$apply()
      })
    }
  }])
