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

describe('squashApp.bookingView module', function () {
  var bookingService
  var getValidDatesSpy
  var getBookingsSpy

  beforeEach(function () {
    // Load mock booking service module: this avoids AWS dependencies...
    module('squashApp.bookingsService')
  })

  // Module under test
  beforeEach(module('squashApp.bookingView'))

  var bookingViewCtrl
  beforeEach(inject(function ($rootScope, $controller, BookingService) {
    // Configure mock response from the BookingService
    getValidDatesSpy = spyOn(BookingService, 'getValidDates')
      .and.callThrough()
    getBookingsSpy = spyOn(BookingService, 'getBookings')
      .and.callThrough()
    bookingService = BookingService

    // Create the controller now that the mock is set up
    var scope = $rootScope.$new()
    bookingViewCtrl = $controller('BookingViewCtrl', {$scope: scope})
  }))

  describe('BookingViewCtrl controller courtIsReserved function', function () {
    it('should return false if there are no booked courts', inject(function ($rootScope) {
      // Reset bookedPlayers to its uninitialised state
      bookingViewCtrl.bookedPlayers = undefined

      // Trigger the promise chain
      $rootScope.$apply()

      // Should return false since we have no bookings
      expect(bookingViewCtrl.courtIsReserved(0, 0)).toBe(false)
      expect(bookingViewCtrl.courtIsReserved(4, 2)).toBe(false)
    }))

    it('should return false if there are booked courts but an unbooked court is queried', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.courtIsReserved(0, 0)).toBe(false)
    }))

    it('should return true if a booked court is queried', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.courtIsReserved(2, 1)).toBe(true)
    }))
  })

  describe('BookingViewCtrl controller buttonStyle function', function () {
    it('should return the cancellation button style for booked courts', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.buttonStyle(2, 1)).toBe('cancellationButton')
    }))
    it('should return the reservation button style for unbooked courts', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.buttonStyle(3, 2)).toBe('reservationButton')
    }))
  })

  describe('BookingViewCtrl controller buttonText function', function () {
    it('should return the players names for booked courts', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.buttonText(2, 1)).toBe('R.Ashour/J.Power')
    }))
    it('should return the "Reserve" for unbooked courts', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.buttonText(3, 2)).toBe('Reserve')
    }))
  })

  describe('BookingViewCtrl controller constructor', function () {
    it('should get its valid dates from the bookings service', inject(function ($rootScope) {
      expect(bookingService.getValidDates).toHaveBeenCalled()
      expect(bookingService.getValidDates.calls.count()).toEqual(1)
      expect(bookingViewCtrl.validDates).toBeUndefined()

      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.validDates).toEqual(['2016-04-23', '2016-04-24'])
    }))

    it('should set the selected date to the first valid date when the bookings service has no active date', inject(function ($rootScope, $controller) {
      // We store an 'active date' on the bookings service to record the date for which bookings were last requested.
      // This is used to ensure e.g. we return to the correct day's bookings after reserving/cancelling a court.
      // But if we've not viewed any bookings yet we should see those for the first valid date i.e. for today.

      // Create a new controller so we can configure the activeDate first
      bookingService.activeDate = undefined
      var scope = $rootScope.$new()
      bookingViewCtrl = $controller('BookingViewCtrl', {$scope: scope})
      expect(bookingViewCtrl.selectedDate).toBeUndefined()

      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')
    }))

    it('should set the selected date to the active date when the bookings service has a valid active date', inject(function ($rootScope, $controller) {
      // Create a new controller so we can configure the activeDate first
      bookingService.activeDate = '2016-04-24' // Use the second valid date.
      var scope = $rootScope.$new()
      bookingViewCtrl = $controller('BookingViewCtrl', {$scope: scope})
      expect(bookingViewCtrl.selectedDate).toBeUndefined()

      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-24')
    }))

    it('should set the selected date to the first valid date when the active date is present but invalid', inject(function ($rootScope, $controller) {
      // If we have an active date, but it's now invalid - e.g. if midnight has now passed, we should not use it.

      // Create a new controller so we can configure the activeDate first
      bookingService.activeDate = '2016-04-22' // Use an invalid date.
      var scope = $rootScope.$new()
      bookingViewCtrl = $controller('BookingViewCtrl', {$scope: scope})
      expect(bookingViewCtrl.selectedDate).toBeUndefined()

      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')
    }))

    it('should get its bookings for the selected date from the bookings service', inject(function ($rootScope, $controller) {
      expect(bookingViewCtrl.bookings).toEqual([])
      expect(bookingService.getBookings).not.toHaveBeenCalled()

      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-23')
      expect(bookingService.getBookings.calls.count()).toEqual(1)
      expect(bookingViewCtrl.bookings).toEqual([{'court': 2, 'slot': 3, 'players': 'R.Ashour/J.Power'}])
    }))

    it('should not use bookings if the selected date has changed since they were requested', inject(function ($rootScope, $controller) {
      // If the user has browsed to a different date since the bookings were requested, we should not use them.
      // This should not be possible on initial load, but could be when flicking between dates. Without this check
      // it might be possible for the user to be shown bookings for a date not matching the dropdown.

      // Trigger the promise chain to return initial set of bookings for 2016-04-23
      $rootScope.$apply()
      expect(bookingViewCtrl.bookingsLoaded).toBe(true)
      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-23')
      expect(bookingService.getBookings.calls.count()).toEqual(1)
      expect(bookingViewCtrl.bookings).toEqual([{'court': 2, 'slot': 3, 'players': 'R.Ashour/J.Power'}])

      // Now change the selectedDate
      bookingViewCtrl.selectedDate = '2016-04-24'
      // Trigger fetch of bookings for this new date
      bookingViewCtrl.selectedDateChanged()
      // But before bookings are returned, change the date again - to mimic user-browsing
      bookingViewCtrl.selectedDate = '2016-04-25'
      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-24')
      expect(bookingService.getBookings.calls.count()).toEqual(2)
      // Because the date has changed, the bookings should not have changed
      $rootScope.$apply()
      expect(bookingViewCtrl.bookings).toEqual([{'court': 2, 'slot': 3, 'players': 'R.Ashour/J.Power'}])

      // Repeat but with the date now agreeing - so bookings should now be updated
      bookingViewCtrl.selectedDate = '2016-04-24'
      bookingViewCtrl.selectedDateChanged()
      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-24')
      expect(bookingService.getBookings.calls.count()).toEqual(3)
      // Because the date has not changed, the bookings should have changed
      $rootScope.$apply()
      expect(bookingViewCtrl.bookings).toEqual([{'court': 3, 'slot': 1, 'players': 'R.Ashour/G.Gaultier'}])
    }))

    it('should set the bookedPlayers array based on the bookings from the bookings service', inject(function ($rootScope) {
      // Checks we use the returned bookings correctly
      expect(bookingViewCtrl.bookedPlayers).toBeUndefined()

      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.bookedPlayers[2][1]).toEqual('R.Ashour/J.Power')
    }))

    it('should set the loaded flag once bookings have been loaded at least once', inject(function ($rootScope) {
      expect(bookingViewCtrl.bookingsLoaded).toBe(false)

      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.bookingsLoaded).toBe(true)
    }))
  })

  describe('BookingViewCtrl controller selectedDateChanged function', function () {
    it('should get its bookings for the new selected date from the bookings service', inject(function ($rootScope, $location, $controller, $q) {
      // Trigger the promise chain to complete initial load correctly
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')

      // Call selectedDateChanged and check this gives a loadFailure error
      bookingViewCtrl.selectedDate = '2016-04-24'
      bookingViewCtrl.selectedDateChanged()
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-24')
      expect(bookingService.getBookings.calls.count()).toEqual(2)
      expect(bookingViewCtrl.bookings).toEqual([{'court': 3, 'slot': 1, 'players': 'R.Ashour/G.Gaultier'}])
    }))

    it('should set the loadFailure flag if the BookingsService getBookings returns any error other than an invalid-date error', inject(function ($rootScope, $location, $controller, $q) {
      // Trigger the promise chain to complete initial load correctly
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')

      // Reconfigure getBookings to return an error
      getBookingsSpy.and.returnValue($q(function (resolve, reject) { reject({'data': 'Boom!'}) }))

      // Call selectedDateChanged and check this gives a loadFailure error
      bookingViewCtrl.selectedDateChanged()
      // Trigger the promise chain so the 'catch' block gets executed
      $rootScope.$apply()

      expect(bookingViewCtrl.loadFailure).toBe(true)
      expect(bookingViewCtrl.validDates).toBeUndefined()
      expect(bookingViewCtrl.bookings).toEqual([])
    }))

    it('should reload the bookings route if the selected date is no longer valid', inject(function ($rootScope, $route, $q) {
      // This can happen if the browser is left open overnight - so the date dropdown shows date(s) in the past
      // and if one of these past dates is then selected.

      // Trigger the promise chain to complete initial load correctly
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')

      // Configure getBookings to return the 'invalid date' error
      getBookingsSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'The booking date is outside the valid range'}) })
      )
      spyOn($route, 'reload').and.callThrough()

      // Call selectedDateChanged and check this triggers a reload of the route
      bookingViewCtrl.selectedDateChanged()
      // Trigger the promise chain so the 'catch' block gets executed
      $rootScope.$apply()

      expect($route.reload).toHaveBeenCalled()
      expect($route.reload.calls.count()).toEqual(1)
    }))
  })

  describe('BookingViewCtrl controller incrementOrDecrementDate function', function () {
    it('should advance the date by one day only if the current date is not the last valid date', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')
      expect(bookingService.getBookings).not.toHaveBeenCalledWith('2016-04-24')
      expect(bookingViewCtrl.bookings).toEqual([{'court': 2, 'slot': 3, 'players': 'R.Ashour/J.Power'}])

      bookingViewCtrl.incrementOrDecrementDate(1)
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-24')
      // And it should have retrieved the bookings for the new date also
      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-24')
      expect(bookingViewCtrl.bookings).toEqual([{'court': 3, 'slot': 1, 'players': 'R.Ashour/G.Gaultier'}])

      // Stepping again should be noop, as we are already on the last valid date
      bookingViewCtrl.incrementOrDecrementDate(1)
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-24')
    }))

    it('should decrement the date by one day only if the current date is not the first valid date', inject(function ($rootScope) {
      // Trigger the promise chain
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')
      expect(bookingService.getBookings).not.toHaveBeenCalledWith('2016-04-24')
      expect(bookingViewCtrl.bookings).toEqual([{'court': 2, 'slot': 3, 'players': 'R.Ashour/J.Power'}])

      // Advance the date - ready for a step backwards...and trigger update
      bookingViewCtrl.selectedDate = '2016-04-24'
      bookingViewCtrl.selectedDateChanged()
      $rootScope.$apply()
      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-24')
      expect(bookingViewCtrl.bookings).toEqual([{'court': 3, 'slot': 1, 'players': 'R.Ashour/G.Gaultier'}])

      // Step backwards
      bookingViewCtrl.incrementOrDecrementDate(-1)
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')
      // And it should have retrieved the bookings for the new date also
      expect(bookingService.getBookings).toHaveBeenCalledWith('2016-04-23')
      expect(bookingViewCtrl.bookings).toEqual([{'court': 2, 'slot': 3, 'players': 'R.Ashour/J.Power'}])

      // Stepping again should be noop, as we are already on the first valid date
      bookingViewCtrl.incrementOrDecrementDate(-1)
      $rootScope.$apply()
      expect(bookingViewCtrl.selectedDate).toEqual('2016-04-23')
    }))
  })

  describe('BookingViewCtrl controller isEarliestDate function', function () {
    it('should return true if the selected date is the first valid date', inject(function ($rootScope) {
      // Trigger the promise chain to update the valid dates
      $rootScope.$apply()
      // Set to first valid date
      bookingViewCtrl.selectedDate = '2016-04-23'
      expect(bookingViewCtrl.isEarliestDate()).toBe(true)
      // Set to something other than first valid date
      bookingViewCtrl.selectedDate = '2016-04-24'
      expect(bookingViewCtrl.isEarliestDate()).toBe(false)
      // Set validDates to undefined - this should also return true to disable the button
      bookingViewCtrl.selectedDate = '2016-04-24'
      bookingViewCtrl.validDates = undefined
      expect(bookingViewCtrl.isEarliestDate()).toBe(true)
    }))
  })

  describe('BookingViewCtrl controller isLatestDate function', function () {
    it('should return true if the selected date is the last valid date', inject(function ($rootScope) {
      // Trigger the promise chain to update the valid dates
      $rootScope.$apply()
      // Set to last valid date
      bookingViewCtrl.selectedDate = '2016-04-24'
      expect(bookingViewCtrl.isLatestDate()).toBe(true)
      // Set to something other than last valid date
      bookingViewCtrl.selectedDate = '2016-04-23'
      expect(bookingViewCtrl.isLatestDate()).toBe(false)
      // Set validDates to undefined - this should also return true to disable the button
      bookingViewCtrl.selectedDate = '2016-04-23'
      bookingViewCtrl.validDates = undefined
      expect(bookingViewCtrl.isLatestDate()).toBe(true)
    }))
  })

  describe('BookingViewCtrl controller showForm function', function () {
    it('should show reservation form if court is not booked', inject(function ($rootScope, $location) {
      // Trigger the promise chain
      $rootScope.$apply()

      spyOn($location, 'url')
      bookingViewCtrl.showForm(2, 2)
      expect($location.url).toHaveBeenCalledWith('/reservations')
      expect($location.url).not.toHaveBeenCalledWith('/cancellations')
    }))

    it('should show cancellation form if court is booked', inject(function ($rootScope, $location) {
      // Trigger the promise chain
      $rootScope.$apply()

      spyOn($location, 'url')
      bookingViewCtrl.showForm(2, 1)
      expect($location.url).toHaveBeenCalledWith('/cancellations')
      expect($location.url).not.toHaveBeenCalledWith('/reservations')
    }))
  })

  describe('BookingViewCtrl controller constructor error handling', function () {
    it('should set the loadFailure flag if the BookingsService getValidDates returns an error', inject(function ($rootScope, $location, $controller, $q) {
      // Create a new controller so we can configure getValidDates to return an error
      getValidDatesSpy.and.returnValue($q(function (resolve, reject) { reject('Boom!') }))

      var scope = $rootScope.$new()
      bookingViewCtrl = $controller('BookingViewCtrl', {$scope: scope})
      expect(bookingViewCtrl.loadFailure).toBe(false)

      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.loadFailure).toBe(true)
      expect(bookingViewCtrl.validDates).toBeUndefined()
      expect(bookingViewCtrl.bookings).toEqual([])
    }))

    it('should set the loadFailure flag if the BookingsService getBookings returns an error', inject(function ($rootScope, $location, $controller, $q) {
      // Create a new controller so we can configure getBookings to return an error
      getBookingsSpy.and.returnValue($q(function (resolve, reject) { reject('Boom!') }))

      var scope = $rootScope.$new()
      bookingViewCtrl = $controller('BookingViewCtrl', {$scope: scope})
      expect(bookingViewCtrl.loadFailure).toBe(false)

      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingViewCtrl.loadFailure).toBe(true)
      expect(bookingViewCtrl.validDates).toBeUndefined()
      expect(bookingViewCtrl.bookings).toEqual([])
    }))
  })
})
