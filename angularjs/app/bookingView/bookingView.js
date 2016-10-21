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

angular.module('squashApp.bookingView', ['ngRoute', 'squashApp.bookingsService', 'squashApp.identityService'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/bookings', {
      templateUrl: 'bookingView/bookingView.html',
      controller: 'BookingViewCtrl as ctrl'
    })
  }])

  .controller('BookingViewCtrl', ['$scope', '$location', '$timeout', '$route', 'BookingService', 'IdentityService', function ($scope, $location, $timeout, $route, BookingService, IdentityService) {
    var self = this
    self.bookings = []

    // Prevent gradual booking table rendering on initial load
    self.initialLoadSucceeded = false

    // Boolean used to show error ui when loading of the bookings fails
    self.loadFailure = false

    // Data needed to draw booking table row and column labels
    self.courtNumbers = BookingService.getCourtNumbers() // e.g. [1,2,3,4,5]
    self.timeSlots = BookingService.getTimeSlots() // e.g. ["10:00 AM", ..., "9:15 PM"]

    // Create promise chain to render the bookings for the selected date
    BookingService.getCachedValidDates(new BookingDataBuilder())
      .then(function (builder) {
        return getBookingsForSelectedDate(builder, true)
      })
      .then(function (builder) {
        return commitBookingsIfDateStillValid(builder)
      })
      .catch(function (e) {
        // If we've failed to render from cached data, we carry on
        // to see if we can render from backend data instead
        return new BookingDataBuilder()
      })
      // If we successfully rendered from cached data, we also carry
      // on to get backend data in case the cache was out-of-date.
      .then(function (builder) {
        return BookingService.getValidDates(builder)
      })
      .then(function (builder) {
        return getBookingsForSelectedDate(builder, false)
      })
      .then(function (builder) {
        return commitBookingsIfDateStillValid(builder)
      })
      .catch(function (e) {
        // Set flag so UI can display a general failure message only if we also
        // failed to render from cached data
        if ((typeof e.builder === 'undefined') ||
          ((typeof e.builder !== 'undefined') && (e.builder.getRenderFromCacheFailed() === true))
        ) {
          // Don't know how to recover from this one...
          // Set flag so UI can display a general failure message...
          self.loadFailure = true
          reset()
          updateUi()
        }
      })

    // Helper functions
    function updateBookingArrays (builder) {
      // Initialise array holding who, if anyone, has a court/time booked
      var bookingNames = new Array(self.timeSlots.length)
      // Initialise arrays holding size of bookings
      var rowspans = new Array(self.timeSlots.length)
      var colspans = new Array(self.timeSlots.length)
      // Initialise array holding whether a table cell is within a block booking
      var cellIsBlockBookingInterior = new Array(self.timeSlots.length)
      for (var timeSlotIndex = 0; timeSlotIndex < bookingNames.length; timeSlotIndex++) {
        bookingNames[timeSlotIndex] = new Array(self.courtNumbers.length)
        rowspans[timeSlotIndex] = new Array(self.courtNumbers.length)
        colspans[timeSlotIndex] = new Array(self.courtNumbers.length)
        cellIsBlockBookingInterior[timeSlotIndex] = new Array(self.courtNumbers.length)
      }

      // Iterate over bookings, updating corresponding array entries
      var bookings = builder.getBookings()
      for (var i = 0; i < bookings.length; i++) {
        var slotIndex = bookings[i].slot - 1
        var courtIndex = bookings[i].court - 1
        bookingNames[slotIndex][courtIndex] = bookings[i].name
        rowspans[slotIndex][courtIndex] = bookings[i].slotSpan
        colspans[slotIndex][courtIndex] = bookings[i].courtSpan
        for (var courtOffset = 0; courtOffset < bookings[i].courtSpan; courtOffset++) {
          for (var slotOffset = 0; slotOffset < bookings[i].slotSpan; slotOffset++) {
            cellIsBlockBookingInterior[slotIndex + slotOffset][courtIndex + courtOffset] = true
          }
        }
        // Reset the cell at the top left of each booking
        cellIsBlockBookingInterior[slotIndex][courtIndex] = undefined
      }
      builder.setBookingNames(bookingNames)
      builder.setRowspans(rowspans)
      builder.setColspans(colspans)
      builder.setCellIsBlockBookingInterior(cellIsBlockBookingInterior)
    }

    function getBookingsForSelectedDate (builder, useCache) {
      // If we were previously viewing bookings for a particular day, and that day is
      // still valid, view that day again, otherwise view the first day
      var validDates = builder.getValidDates()
      if ((typeof BookingService.activeDate !== 'undefined') &&
        (validDates.indexOf(BookingService.activeDate) > -1)
      ) {
        builder.setSelectedDate(BookingService.activeDate)
      } else {
        builder.setSelectedDate(validDates[0])
      }

      // Get the bookings for the selected date
      if (useCache === true) {
        return BookingService.getCachedBookings(builder)
      } else {
        return BookingService.getBookings(builder)
      }
    }

    function commitBookingsIfDateStillValid (builder) {
      // It is possible the user could have switched to a different date since this result's async
      // request was triggered - so we apply the bookings only if the date agrees, or if this is the
      // first call to this function.
      var selectedDate = builder.getSelectedDate()
      if ((typeof self.selectedDate === 'undefined') || (selectedDate === self.selectedDate)) {
        updateBookingArrays(builder)

        // Update variables on self now that the transaction has succeeded
        self.validDates = angular.copy(builder.getValidDates())
        self.selectedDate = angular.copy(builder.getSelectedDate())
        self.bookings = angular.copy(builder.getBookings())
        self.bookingNames = angular.copy(builder.getBookingNames())
        self.rowspans = angular.copy(builder.getRowspans())
        self.colspans = angular.copy(builder.getColspans())
        self.cellIsBlockBookingInterior = angular.copy(builder.getCellIsBlockBookingInterior())
        builder.setRenderFromCacheFailed(false)

        // Update the UI with these new bookings
        self.initialLoadSucceeded = true
        updateUi()
      }

      return builder
    }

    self.cellIsAtBookingTopLeft = function (timeSlotIndex, courtNumberIndex) {
      return (
      (((typeof self.rowspans !== 'undefined') &&
      (typeof self.rowspans[timeSlotIndex][courtNumberIndex] !== 'undefined')) &&
      ((typeof self.colspans !== 'undefined') &&
      (typeof self.colspans[timeSlotIndex][courtNumberIndex] !== 'undefined')))
      )
    }

    self.isBlockBooking = function (timeSlotIndex, courtNumberIndex) {
      return (
      (self.cellIsAtBookingTopLeft(timeSlotIndex, courtNumberIndex)) &&
      ((self.rowspans[timeSlotIndex][courtNumberIndex] !== 1) ||
      (self.colspans[timeSlotIndex][courtNumberIndex] !== 1))
      )
    }

    self.isNotBlockBookingInterior = function (timeSlotIndex) {
      return function (courtNumber, courtNumberIndex, allCourtNumbers) {
        if ((typeof self.cellIsBlockBookingInterior !== 'undefined') &&
          (typeof self.cellIsBlockBookingInterior[timeSlotIndex][courtNumberIndex] !== 'undefined')
        ) {
          return false
        }
        return true
      }
    }

    self.rowSpan = function (timeSlotIndex, courtNumberIndex) {
      if (self.cellIsAtBookingTopLeft(timeSlotIndex, courtNumberIndex)) {
        return self.rowspans[timeSlotIndex][courtNumberIndex]
      }
      return '1'
    }

    self.colSpan = function (timeSlotIndex, courtNumberIndex) {
      if (self.cellIsAtBookingTopLeft(timeSlotIndex, courtNumberIndex)) {
        return self.colspans[timeSlotIndex][courtNumberIndex]
      }
      return '1'
    }

    self.buttonStyle = function (timeSlotIndex, courtNumberIndex) {
      if (self.cellIsAtBookingTopLeft(timeSlotIndex, courtNumberIndex)) {
        return 'cancellation-button'
      }
      return 'reservation-button'
    }

    self.cellClass = function (timeSlotIndex, courtNumberIndex) {
      return self.isBlockBooking(timeSlotIndex, courtNumberIndex) ? 'block-booking-cell' : 'non-block-booking-cell'
    }

    self.bookingText = function (timeSlotIndex, courtNumberIndex) {
      if (self.cellIsAtBookingTopLeft(timeSlotIndex, courtNumberIndex)) {
        return self.bookingNames[timeSlotIndex][courtNumberIndex]
      }
      return 'Reserve'
    }

    self.tooltip = function (timeSlotIndex, courtNumberIndex) {
      if (self.cellIsAtBookingTopLeft(timeSlotIndex, courtNumberIndex)) {
        return self.bookingNames[timeSlotIndex][courtNumberIndex]
      }
      // No tooltip for unbooked courts
      return ''
    }

    self.showForm = function (timeSlotIndex, courtNumberIndex) {
      // Get famous players from the booking service
      var famousPlayers = BookingService.getTwoFamousPlayers()

      // Transfer state to the form view via the booking service
      BookingService.activeCourt = self.courtNumbers[courtNumberIndex]
      BookingService.activeCourtSpan = self.colSpan(timeSlotIndex, courtNumberIndex)
      BookingService.activeSlot = self.timeSlots[timeSlotIndex]
      BookingService.activeSlotSpan = self.rowSpan(timeSlotIndex, courtNumberIndex)
      BookingService.activeSlotIndex = timeSlotIndex
      BookingService.activeDate = self.selectedDate
      BookingService.activeName = self.bookingText(timeSlotIndex, courtNumberIndex)
      BookingService.famousPlayer1 = famousPlayers[0]
      BookingService.famousPlayer2 = famousPlayers[1]

      // Show either the reservation or cancellation form
      if (self.cellIsAtBookingTopLeft(timeSlotIndex, courtNumberIndex)) {
        $location.url('/cancellations')
      } else {
        $location.url('/reservations')
      }
    }

    self.manageBookingRules = function () {
      // Show the booking rule editor
      $location.url('/bookingrules')
    }

    self.loginOrOut = function () {
      if (IdentityService.isLoggedIn()) {
        IdentityService.logout().then(function () { updateUi() })
      } else {
        $location.url('/login')
      }
    }

    self.showAdminUi = function () {
      return IdentityService.isLoggedIn()
    }

    self.loginOrOutButtonText = function () {
      return self.showAdminUi() ? 'Logout' : 'Admin'
    }

    function reset () {
      self.selectedDate = undefined
      self.validDates = undefined
      self.bookings = []
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
      var builder = new BookingDataBuilder()
      builder.setSelectedDate(self.selectedDate)
      builder.setValidDates(self.validDates)

      BookingService.getCachedBookings(builder)
        .then(function (builder) {
          return commitBookingsIfDateStillValid(builder)
        })
        .catch(function (e) {
          // If we've failed to render from cached data, we carry on
          // to see if we can render from backend data instead
          var builder = new BookingDataBuilder()
          builder.setSelectedDate(self.selectedDate)
          builder.setValidDates(self.validDates)
          return builder
        })
        // If we successfully rendered from cached data, we also carry
        // on to get backend data in case the cache was out-of-date.
        .then(function (builder) {
          return BookingService.getBookings(builder)
        })
        .then(function (builder) {
          return commitBookingsIfDateStillValid(builder)
        })
        .catch(function (e) {
          if ((typeof e.builder !== 'undefined') && (e.builder.getRenderFromCacheFailed() === false)
          ) {
            // Don't fail here if we rendered ok from cached data
            return
          }

          // If the failure is bc the selectedDate is no longer valid, trigger a full reload
          // to re-get the valid dates from the backend. This can happen if you use a browser
          // left open overnight.
          if ((typeof e.error !== 'undefined') &&
            (typeof e.error.data !== 'undefined') &&
            (e.error.data.indexOf('The booking date is outside the valid range') > -1)
          ) {
            // This should reload and show bookings for the first valid date.
            $route.reload()
          } else {
            // Don't know how to recover from this one...
            // Set flag so UI can display a general failure message...
            self.loadFailure = true
            reset()
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

    // Helper object to accumulate bookings-related data during the asynchronous
    // execution of each promise chain. This prevents chains that fail before
    // completion, or execute interleaved with other chains, from corrupting the
    // data on self, or on each other.
    function BookingDataBuilder () {
      this.validDates = undefined
      this.selectedDate = undefined
      this.bookings = undefined
      this.bookingRules = undefined
      this.bookingNames = undefined
      this.rowspans = undefined
      this.colspans = undefined
      this.cellIsBlockBookingInterior = undefined
      this.renderFromCacheFailed = true // Will be set to false on success

      this.setValidDates = function (validDates) {
        this.validDates = angular.copy(validDates)
      }
      this.getValidDates = function () {
        return this.validDates
      }
      this.setSelectedDate = function (selectedDate) {
        this.selectedDate = angular.copy(selectedDate)
      }
      this.getSelectedDate = function () {
        return this.selectedDate
      }
      this.setBookings = function (bookings) {
        this.bookings = angular.copy(bookings)
      }
      this.getBookings = function () {
        return this.bookings
      }
      this.setBookingRules = function (bookingRules) {
        this.bookingRules = angular.copy(bookingRules)
      }
      this.getBookingRules = function () {
        return this.bookingRules
      }
      this.setBookingNames = function (bookingNames) {
        this.bookingNames = angular.copy(bookingNames)
      }
      this.getBookingNames = function () {
        return this.bookingNames
      }
      this.setRowspans = function (rowspans) {
        this.rowspans = angular.copy(rowspans)
      }
      this.getRowspans = function () {
        return this.rowspans
      }
      this.setColspans = function (colspans) {
        this.colspans = angular.copy(colspans)
      }
      this.getColspans = function () {
        return this.colspans
      }
      this.setCellIsBlockBookingInterior = function (cellIsBlockBookingInterior) {
        this.cellIsBlockBookingInterior = angular.copy(cellIsBlockBookingInterior)
      }
      this.getCellIsBlockBookingInterior = function () {
        return this.cellIsBlockBookingInterior
      }
      this.setRenderFromCacheFailed = function (renderFromCacheFailed) {
        this.renderFromCacheFailed = angular.copy(renderFromCacheFailed)
      }
      this.getRenderFromCacheFailed = function () {
        return this.renderFromCacheFailed
      }
    }
  }])
