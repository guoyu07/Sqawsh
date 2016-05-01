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

angular.module('squashApp.bookingView', ['ngRoute', 'squashApp.bookingsService'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/bookings', {
      templateUrl: 'bookingView/bookingView.html',
      controller: 'BookingViewCtrl as ctrl'
    })
  }])

  .controller('BookingViewCtrl', ['$scope', '$location', '$timeout', '$route', 'BookingService', function ($scope, $location, $timeout, $route, BookingService) {
    var self = this
    self.bookings = []

    // Prevent gradual booking table rendering
    self.bookingsLoaded = false

    // Boolean used to show error ui when loading of the bookings fails
    self.loadFailure = false

    // Data needed to draw booking table row and column labels
    self.courtNumbers = BookingService.getCourtNumbers() // e.g. [1,2,3,4,5]
    self.timeSlots = BookingService.getTimeSlots() // e.g. ["10:00 AM", ..., "9:15 PM"]

    // Get the dates for which bookings can currently be viewed/made
    BookingService.getValidDates()
      .then(function (validDates) {
        self.validDates = validDates // array of dates in YYYY-MM-DD format
        // If we were previously viewing bookings for a particular day, and that day is still valid, view
        // that day again, otherwise view the first day
        if ((typeof BookingService.activeDate !== 'undefined') &&
          (self.validDates.indexOf(BookingService.activeDate) > -1)
        ) {
          self.selectedDate = BookingService.activeDate
        } else {
          self.selectedDate = self.validDates[0]
        }

        // Get the bookings for the selected date
        return BookingService.getBookings(self.selectedDate)
      })
      .then(applyBookingsIfDateStillValid)
      .catch(function (error) {
        // Set flag so UI can display a general failure message...
        console.log('Rendering bookings failed with error: ')
        console.dir(error)
        // Reset some variables in case they were set before the error we're handling
        reset()
        self.loadFailure = true
        updateUi()
      })

    function reset () {
      self.selectedDate = undefined
      self.validDates = undefined
      self.bookings = []
    }

    // Helper functions
    function updateBookedPlayersArray () {
      // Initialise array holding who, if anyone, has a court/time booked
      self.bookedPlayers = new Array(self.timeSlots.length)
      for (var slotIndex = 0; slotIndex < self.bookedPlayers.length; slotIndex++) {
        self.bookedPlayers[slotIndex] = new Array(self.courtNumbers.length)
      }

      // Iterate over bookings, updating corresponding bookedPlayers entry
      for (var i = 0; i < self.bookings.length; i++) {
        self.bookedPlayers[self.bookings[i].slot - 1][self.bookings[i].court - 1] = self.bookings[i].players
      }
    }

    function applyBookingsIfDateStillValid (result) {
      // It is possible the user could have switched to a different date since this result's async
      // request was triggered - so we apply the bookings only if the date agrees.
      if (result.date === self.selectedDate) {
        self.bookings = result.bookings
        updateBookedPlayersArray()

        // Update the UI with these new bookings
        self.bookingsLoaded = true
        updateUi()
      }
    }

    self.courtIsReserved = function (timeSlotIndex, courtNumberIndex) {
      return (
      (typeof self.bookedPlayers !== 'undefined') &&
      (typeof self.bookedPlayers[timeSlotIndex][courtNumberIndex] !== 'undefined')
      )
    }

    self.buttonStyle = function (timeSlotIndex, courtNumberIndex) {
      if (self.courtIsReserved(timeSlotIndex, courtNumberIndex)) {
        return 'cancellationButton'
      }
      return 'reservationButton'
    }

    self.buttonText = function (timeSlotIndex, courtNumberIndex) {
      if (self.courtIsReserved(timeSlotIndex, courtNumberIndex)) {
        return self.bookedPlayers[timeSlotIndex][courtNumberIndex]
      }
      return 'Reserve'
    }

    self.showForm = function (timeSlotIndex, courtNumberIndex) {
      // Transfer state to the form view via the booking service
      BookingService.activeCourt = self.courtNumbers[courtNumberIndex]
      BookingService.activeSlot = self.timeSlots[timeSlotIndex]
      BookingService.activeSlotIndex = timeSlotIndex
      BookingService.activeDate = self.selectedDate
      BookingService.player1 = ''
      BookingService.player2 = ''
      BookingService.players = self.buttonText(timeSlotIndex, courtNumberIndex)

      // Show either the reservation or cancellation form
      if (self.courtIsReserved(timeSlotIndex, courtNumberIndex)) {
        $location.url('/cancellations')
      } else {
        $location.url('/reservations')
      }
    }

    // Date-related helper functions
    self.incrementOrDecrementDate = function (stepSize) {
      // Use Date instead? Update validDates first?
      var i = self.validDates.length
      while (i--) {
        if (self.validDates[i] === self.selectedDate) {
          i = i + stepSize

          // Clamp
          if (i < 0) {
            i = 0
          }
          if (i >= self.validDates.length) {
            i = self.validDates.length - 1
          }

          self.selectedDate = self.validDates[i]
          self.selectedDateChanged()
          break
        }
      }
    }

    self.selectedDateChanged = function () {
      self.loadFailure = false
      BookingService.getBookings(self.selectedDate)
        .then(applyBookingsIfDateStillValid)
        .catch(function (error) {
          // If the failure is bc the selectedDate is no longer valid, trigger a full reload
          // to re-get the valid dates from the backend. This can happen if you use a browser
          // left open overnight.
          if (error.data.indexOf('The booking date is outside the valid range') > -1) {
            // This should reload and show bookings for the first valid date.
            $route.reload()
          } else {
            // Don't know how to recover from this one...
            // Set flag so UI can display a general failure message...
            console.log('Changing date failed with error: ')
            console.dir(error)
            reset()
            self.loadFailure = true
            updateUi()
          }
        })
    }

    self.isEarliestDate = function () {
      if (typeof self.validDates === 'undefined') {
        return true
      }
      return self.selectedDate === self.validDates[0]
    }

    self.isLatestDate = function () {
      if (typeof self.validDates === 'undefined') {
        return true
      }
      return self.selectedDate === self.validDates[self.validDates.length - 1]
    }

    function updateUi () {
      // Wrap in timeout to avoid '$digest already in progress' error.
      $timeout(function () {
        $scope.$apply()
      })
    }
  }])
