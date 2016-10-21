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

describe('squashApp.cancellationView module', function () {
  var bookingService
  var cancelCourtSpy

  beforeEach(function () {
    // Load mock booking and identity service modules: this avoids AWS dependencies...
    module('squashApp.identityService')
    module('squashApp.bookingsService')
  })

  // Module under test
  beforeEach(module('squashApp.cancellationView'))

  var cancellationViewCtrl
  beforeEach(inject(function ($rootScope, $controller, BookingService) {
    // Configure mock response from the BookingService
    cancelCourtSpy = spyOn(BookingService, 'cancelCourt')
      .and.callThrough()
    bookingService = BookingService

    // Set up some mock properties on the bookings service
    bookingService.activeCourt = 2
    bookingService.activeCourtSpan = 1
    bookingService.activeSlot = 1
    bookingService.activeSlotSpan = 1
    bookingService.activeSlotIndex = 0
    bookingService.activeDate = '2016-04-24'
    bookingService.activeName = 'J.Wilstrop/N.Mathew'

    // Create the controller now that the mock is set up
    var scope = $rootScope.$new()
    cancellationViewCtrl = $controller('CancellationViewCtrl', {$scope: scope})
  }))

  describe('cancellationViewCtrl controller', function () {
    it('should use the properties of the booking that are transferred via the bookings service', function () {
      // Details of the court to cancel etc are transferred via properties on the bookings service
      expect(cancellationViewCtrl.activeCourt).toEqual(2)
      expect(cancellationViewCtrl.activeCourtSpan).toEqual(1)
      expect(cancellationViewCtrl.activeSlot).toEqual(1)
      expect(cancellationViewCtrl.activeSlotSpan).toEqual(1)
      expect(cancellationViewCtrl.activeSlotIndex).toEqual(0)
      expect(cancellationViewCtrl.activeDate).toEqual('2016-04-24')
      expect(cancellationViewCtrl.activeName).toEqual('J.Wilstrop/N.Mathew')
    })

    it('should not submit a court cancellation form that is invalid', function () {
      // Try to submit an invalid form
      cancellationViewCtrl.submitCancellation({'$invalid': true})
      expect(bookingService.cancelCourt).not.toHaveBeenCalled()
    })

    it('should submit a court cancellation form that is valid', function () {
      // Try to submit a valid form
      cancellationViewCtrl.password = 'TheBogieman'
      expect(bookingService.cancelCourt).not.toHaveBeenCalled()
      cancellationViewCtrl.submitCancellation({'$invalid': false})
      expect(bookingService.cancelCourt).toHaveBeenCalledWith(
        2, 1, 1, 1, '2016-04-24', 'J.Wilstrop/N.Mathew', 'TheBogieman'
      )
    })

    it('should submit a court cancellation form with the correct block booking spans', inject(function ($rootScope, $controller) {
      // The cancellation call should use the row and column spans as passed via the Booking service

      // Set up non-unit row and column spans
      bookingService.activeCourtSpan = 3
      bookingService.activeSlotSpan = 11

      // Create a new controller so that these values are picked up
      var scope = $rootScope.$new()
      cancellationViewCtrl = $controller('CancellationViewCtrl', {$scope: scope})

      cancellationViewCtrl.password = 'TheBogieman'
      expect(bookingService.cancelCourt).not.toHaveBeenCalled()
      cancellationViewCtrl.submitCancellation({'$invalid': false})
      expect(bookingService.cancelCourt).toHaveBeenCalledWith(
        2, 3, 1, 11, '2016-04-24', 'J.Wilstrop/N.Mathew', 'TheBogieman'
      )
    }))

    it('should return to the booking view after an error-free submission of a court cancellation', inject(function ($rootScope, $location) {
      // Perform an error-free submission
      cancellationViewCtrl.password = 'TheBogieman'
      cancellationViewCtrl.submitCancellation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect($location.url).not.toHaveBeenCalled()
      $rootScope.$apply()

      // Verify we return to the booking view
      expect($location.url).toHaveBeenCalledWith('/bookings')
    }))

    it('should set the password error flag when a cancellation is submitted with an invalid password', inject(function ($rootScope, $location, $q) {
      // Configure the cancelCourt mock to return the wrong-password error
      cancelCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'The password is incorrect'}) })
      )

      // Submit a valid cancellation
      cancellationViewCtrl.password = 'TheBogieman'
      cancellationViewCtrl.submitCancellation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(false)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(false)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(cancellationViewCtrl.cancellationFailed).toBe(false)
      $rootScope.$apply()

      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(false)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(true)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(cancellationViewCtrl.cancellationFailed).toBe(true)

      // Verify we do not navigate away from the cancellation form
      expect($location.url).not.toHaveBeenCalled()
    }))

    it('should set the unauthenticated block booking error flag when a block-booking cancellation is submitted whilst unauthenticated', inject(function ($rootScope, $location, $q) {
      // Configure the cancelCourt mock to return the unauthenticated block booking error
      cancelCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'You must login to manage block bookings'}) })
      )

      // Submit a valid cancellation
      cancellationViewCtrl.password = 'TheBogieman'
      cancellationViewCtrl.submitCancellation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(false)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(false)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(cancellationViewCtrl.cancellationFailed).toBe(false)
      $rootScope.$apply()

      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(false)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(false)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(true)
      expect(cancellationViewCtrl.cancellationFailed).toBe(true)

      // Verify we do not navigate away from the cancellation form
      expect($location.url).not.toHaveBeenCalled()
    }))

    it('should set the bookingCancellationFailed error flag when a cancellation is submitted which could not be made at the backend', inject(function ($rootScope, $location, $q) {
      // This is case where everything is ok, except the database failed to cancel the booking. Expect the cause of
      // this will almost always be that two browsers are trying to cancel the same booking almost simultaneously.

      // Configure the cancelCourt mock to return the cancellation-failed error
      cancelCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'Booking cancellation failed'}) })
      )

      // Submit a valid cancellation
      cancellationViewCtrl.password = 'TheBogieman'
      cancellationViewCtrl.submitCancellation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(false)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(false)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(cancellationViewCtrl.cancellationFailed).toBe(false)
      $rootScope.$apply()

      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(true)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(false)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(cancellationViewCtrl.cancellationFailed).toBe(true)

      // Verify we do not navigate away from the cancellation form
      expect($location.url).not.toHaveBeenCalled()
    }))

    it('should set only the cancellationFailed error flag when a cancellation is submitted but returns an error', inject(function ($rootScope, $location, $q) {
      // This is case where everything is ok, except the backend returned an error for
      // some reason other than the special cases just considered.

      // Configure the cancelCourt mock to return a general error
      cancelCourtSpy.and.returnValue(
        $q(function (resolve, reject) { reject({'data': 'Apologies - something has gone wrong'}) })
      )

      // Submit a valid cancellation
      cancellationViewCtrl.password = 'TheBogieman'
      cancellationViewCtrl.submitCancellation({'$invalid': false})

      // Trigger the promise chain
      spyOn($location, 'url')
      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(false)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(false)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(cancellationViewCtrl.cancellationFailed).toBe(false)
      $rootScope.$apply()

      expect(cancellationViewCtrl.bookingCancellationFailed).toBe(false)
      expect(cancellationViewCtrl.passwordIncorrect).toBe(false)
      expect(cancellationViewCtrl.unauthenticatedBlockBookingError).toBe(false)
      expect(cancellationViewCtrl.cancellationFailed).toBe(true)

      // Verify we do not navigate away from the cancellation form
      expect($location.url).not.toHaveBeenCalled()
    }))
  })
})
