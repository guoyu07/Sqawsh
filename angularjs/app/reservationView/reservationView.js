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

angular.module('squashApp.reservationView', ['ngRoute', 'squashApp.bookingsService'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/reservations', {
      templateUrl: 'reservationView/reservationView.html',
      controller: 'ReservationViewCtrl as ctrl'
    })
  }])

  .controller('ReservationViewCtrl', ['$scope', '$location', '$timeout', 'BookingService', function ($scope, $location, $timeout, BookingService) {
    var self = this

    self.activeCourt = BookingService.activeCourt
    self.activeSlot = BookingService.activeSlot
    self.activeSlotIndex = BookingService.activeSlotIndex
    self.activeDate = BookingService.activeDate
    self.player1 = BookingService.player1
    self.player2 = BookingService.player2

    self.returnToBookings = function () {
      $location.url('/bookings')
    }

    self.submitReservation = function (form) {
      if (form.$invalid) {
        return
      }

      // The form is valid - so let's create the booking
      self.passwordIncorrect = false
      self.bookingCreationFailed = false
      self.bookingFailed = false
      BookingService.reserveCourt(self.activeCourt, self.activeSlotIndex + 1, self.activeDate, self.player1, self.player2, self.password)
        .then(function (result) {
          self.returnToBookings()
          updateUi()
        })
        .catch(function (error) {
          console.log('Reservation failed with error: ')
          console.dir(error)
          if (typeof error.data !== 'undefined' && error.data.indexOf('The password is incorrect') > -1) {
            self.passwordIncorrect = true
          } else if (typeof error.data !== 'undefined' && error.data.indexOf('Booking creation failed') > -1) {
            self.bookingCreationFailed = true
          }
          self.bookingFailed = true
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
