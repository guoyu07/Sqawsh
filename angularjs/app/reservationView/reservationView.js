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

angular.module('squashApp.reservationView', ['ngRoute', 'squashApp.bookingsService', 'squashApp.identityService'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/reservations', {
      templateUrl: 'reservationView/reservationView.html',
      controller: 'ReservationViewCtrl as ctrl'
    })
  }])

  .controller('ReservationViewCtrl', ['$scope', '$location', '$timeout', 'BookingService', 'IdentityService', function ($scope, $location, $timeout, BookingService, IdentityService) {
    var self = this

    self.rowSpan = 1
    self.colSpan = 1

    self.activeCourt = BookingService.activeCourt
    self.activeSlot = BookingService.activeSlot
    self.activeSlotIndex = BookingService.activeSlotIndex
    self.activeDate = BookingService.activeDate
    self.famousPlayer1 = BookingService.famousPlayer1
    self.famousPlayer2 = BookingService.famousPlayer2

    self.returnToBookings = function () {
      $location.url('/bookings')
    }

    self.namesLength = function () {
      // Used to ensure players' names input box is big enough to show all of placeholder text
      var placeholder = 'e.g. ' + self.famousPlayer1 + '/' + self.famousPlayer2
      return placeholder.length
    }

    self.submitReservation = function (form) {
      if (form.$invalid) {
        return
      }

      // The form is valid - so let's create the booking
      // If no block booking values entered, assume not a block booking:
      if (!self.rowSpan) {
        self.rowSpan = 1
      }
      if (!self.colSpan) {
        self.colSpan = 1
      }
      self.passwordIncorrect = false
      self.unauthenticatedBlockBookingError = false
      self.bookingCreationFailed = false
      self.bookingFailed = false
      BookingService.reserveCourt(self.activeCourt, self.colSpan, self.activeSlotIndex + 1, self.rowSpan, self.activeDate, self.name, self.password)
        .then(function (result) {
          self.returnToBookings()
          updateUi()
        })
        .catch(function (error) {
          if (typeof error.data !== 'undefined' && error.data.indexOf('The password is incorrect') > -1) {
            self.passwordIncorrect = true
          } else if (typeof error.data !== 'undefined' && error.data.indexOf('You must login to manage block bookings') > -1) {
            self.unauthenticatedBlockBookingError = true
          } else if (typeof error.data !== 'undefined' && error.data.indexOf('Booking creation failed') > -1) {
            self.bookingCreationFailed = true
          }
          self.bookingFailed = true
          updateUi()
        })
    }

    self.showAdminUi = function () {
      return IdentityService.isLoggedIn()
    }

    function updateUi () {
      // Wrap in timeout to avoid '$digest already in progress' error.
      $timeout(function () {
        $scope.$apply()
      })
    }
  }])
