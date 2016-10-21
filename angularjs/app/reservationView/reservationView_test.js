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

describe('squashApp.reservationView module', function () {
  var bookingService
  var reserveCourtSpy

  beforeEach(function () {
    // Load mock booking and identity service modules: this avoids AWS dependencies...
    module('squashApp.identityService')
    module('squashApp.bookingsService')
  })

  // Module under test
  beforeEach(module('squashApp.reservationView'))

  var reservationViewCtrl
  beforeEach(inject(function ($rootScope, $controller, BookingService) {
    // Configure mock response from the BookingService
    reserveCourtSpy = spyOn(BookingService, 'reserveCourt')
      .and.callThrough()
    bookingService = BookingService

    // Set up some mock properties on the bookings service
    bookingService.activeCourt = 2
    bookingService.activeCourtSpan = 1
    bookingService.activeSlot = 1
    bookingService.activeSlotSpan = 1
    bookingService.activeSlotIndex = 0
    bookingService.activeDate = '2016-04-24'

    // Create the controller now that the mock is set up
    var scope = $rootScope.$new()
    reservationViewCtrl = $controller('ReservationViewCtrl', {$scope: scope})
  }))

  describe('ReservationViewCtrl controller', function () {
    it('should use the properties of the booking that are transferred via the bookings service', function () {
      // Details of the court to book etc are transferred via properties on the bookings service
      expect(reservationViewCtrl.activeCourt).toEqual(2)
      expect(reservationViewCtrl.activeSlot).toEqual(1)
      expect(reservationViewCtrl.activeSlotIndex).toEqual(0)
      expect(reservationViewCtrl.activeDate).toEqual('2016-04-24')
    })

    it('should not submit a court reservation form that is invalid', function () {
      // Try to submit an invalid form
      reservationViewCtrl.submitReservation({'$invalid': true})
      expect(bookingService.reserveCourt).not.toHaveBeenCalled()
    })

    it('should submit a court reservation form that is valid', function () {
      // Try to submit a valid form
      reservationViewCtrl.password = 'TheBogieman'
      reservationViewCtrl.name = 'J.Wilstrop/N.Mathew'
      expect(bookingService.reserveCourt).not.toHaveBeenCalled()
      reservationViewCtrl.submitReservation({'$invalid': false})
      expect(bookingService.reserveCourt).toHaveBeenCalledWith(
        2, 1, 1, 1, '2016-04-24', 'J.Wilstrop/N.Mathew', 'TheBogieman'
      )
    })

    it('should submit a court reservation form with the correct block booking spans', inject(function ($rootScope, $controller) {
      // The reservation call should use the row and column spans as filled out on the reservation form

      // Set up non-unit row and column spans
      bookingService.activeCourtSpan = 4
      bookingService.activeSlotSpan = 7

      reservationViewCtrl.password = 'TheBogieman'
      reservationViewCtrl.name = 'J.Wilstrop/N.Mathew'
      reservationViewCtrl.rowSpan = 7
      reservationViewCtrl.colSpan = 4
      expect(bookingService.reserveCourt).not.toHaveBeenCalled()
      reservationViewCtrl.submitReservation({'$invalid': false})
      expect(bookingService.reserveCourt).toHaveBeenCalledWith(
        2, 4, 1, 7, '2016-04-24', 'J.Wilstrop/N.Mathew', 'TheBogieman'
      )
    }))

    it('should return to the booking view after an error-free submission of a court reservation', inject(function ($rootScope, $location) {
      // Perform an error-free submission
      reservationViewCtrl.password = 'TheBogieman'
      reservationViewCtrl.submitReservation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect($location.url).not.toHaveBeenCalled()
      $rootScope.$apply()

      // Verify we return to the booking view
      expect($location.url).toHaveBeenCalledWith('/bookings')
    }))

    it('should set the password error flag when a booking is submitted with an invalid password', inject(function ($rootScope, $location, $q) {
      // Configure the reserveCourt mock to return the wrong-password error
      reserveCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'The password is incorrect'}) })
      )

      // Submit a valid reservation
      reservationViewCtrl.password = 'TheBogieman'
      reservationViewCtrl.submitReservation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(reservationViewCtrl.bookingCreationFailed).toBe(false)
      expect(reservationViewCtrl.passwordIncorrect).toBe(false)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(reservationViewCtrl.bookingFailed).toBe(false)
      $rootScope.$apply()

      expect(reservationViewCtrl.bookingCreationFailed).toBe(false)
      expect(reservationViewCtrl.passwordIncorrect).toBe(true)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(reservationViewCtrl.bookingFailed).toBe(true)

      // Verify we do not navigate away from the reservation form
      expect($location.url).not.toHaveBeenCalled()
    }))

    it('should set the unauthenticated block booking error flag when a block-booking reservation is submitted whilst unauthenticated', inject(function ($rootScope, $location, $q) {
      // Configure the reserveCourt mock to return the unauthenticated block booking error
      reserveCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'You must login to manage block bookings'}) })
      )

      // Submit a valid reservation
      reservationViewCtrl.password = 'TheBogieman'
      reservationViewCtrl.submitReservation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(reservationViewCtrl.bookingCreationFailed).toBe(false)
      expect(reservationViewCtrl.passwordIncorrect).toBe(false)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(reservationViewCtrl.bookingFailed).toBe(false)
      $rootScope.$apply()

      expect(reservationViewCtrl.bookingCreationFailed).toBe(false)
      expect(reservationViewCtrl.passwordIncorrect).toBe(false)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(true)
      expect(reservationViewCtrl.bookingFailed).toBe(true)

      // Verify we do not navigate away from the reservation form
      expect($location.url).not.toHaveBeenCalled()
    }))

    it('should set the bookingCreationFailed error flag when a booking is submitted which could not be made at the backend', inject(function ($rootScope, $location, $q) {
      // This is case where everything is ok, except the database failed to make the booking. Expect the cause of
      // this will almost always be that two browsers are trying to book the same booking almost simultaneously.

      // Configure the reserveCourt mock to return the booking-failed error
      reserveCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'Booking creation failed'}) })
      )

      // Submit a valid reservation
      reservationViewCtrl.password = 'TheBogieman'
      reservationViewCtrl.submitReservation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(reservationViewCtrl.bookingCreationFailed).toBe(false)
      expect(reservationViewCtrl.passwordIncorrect).toBe(false)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(reservationViewCtrl.bookingFailed).toBe(false)
      $rootScope.$apply()

      expect(reservationViewCtrl.bookingCreationFailed).toBe(true)
      expect(reservationViewCtrl.passwordIncorrect).toBe(false)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(reservationViewCtrl.bookingFailed).toBe(true)

      // Verify we do not navigate away from the reservation form
      expect($location.url).not.toHaveBeenCalled()
    }))

    it('should set only the bookingFailed error flag when a booking is submitted but returns an error', inject(function ($rootScope, $location, $q) {
      // This is case where everything is ok, except the backend returned an error for
      // some reason other than the special cases just considered.

      // Configure the reserveCourt mock to return a general error
      reserveCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'Apologies - something has gone wrong'}) })
      )

      // Submit a valid reservation
      reservationViewCtrl.password = 'TheBogieman'
      reservationViewCtrl.submitReservation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(reservationViewCtrl.bookingCreationFailed).toBe(false)
      expect(reservationViewCtrl.passwordIncorrect).toBe(false)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(reservationViewCtrl.bookingFailed).toBe(false)
      $rootScope.$apply()

      expect(reservationViewCtrl.bookingCreationFailed).toBe(false)
      expect(reservationViewCtrl.passwordIncorrect).toBe(false)
      expect(reservationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(reservationViewCtrl.bookingFailed).toBe(true)

      // Verify we do not navigate away from the reservation form
      expect($location.url).not.toHaveBeenCalled()
    }))
  })
})
