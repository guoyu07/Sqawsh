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

describe('squashApp.loginView module', function () {
  var identityService

  beforeEach(function () {
    // Load mock identity service module: this avoids AWS dependencies...
    module('squashApp.identityService')
  })

  // Module under test
  beforeEach(module('squashApp.loginView'))

  var loginViewCtrl
  beforeEach(inject(function ($rootScope, $controller, IdentityService) {
    identityService = IdentityService

    // Create the controller
    var scope = $rootScope.$new()
    loginViewCtrl = $controller('LoginViewCtrl', {$scope: scope})
  }))

  describe('LoginViewCtrl controller returnToBookings function', function () {
    it('should route to the bookings view', inject(function ($location) {
      // ARRANGE
      spyOn($location, 'url')

      // ACT
      loginViewCtrl.returnToBookings()

      // ASSERT
      expect($location.url).toHaveBeenCalledWith('/bookings')
    }))
  })

  describe('LoginViewCtrl controller login function', function () {
    it('should do nothing if the form is invalid', inject(function ($location) {
      // ARRANGE
      spyOn($location, 'url')
      spyOn(identityService, 'login').and.callThrough()

      // ACT
      // Try to submit an invalid form
      loginViewCtrl.login({'$invalid': true})

      // ASSERT
      expect(identityService.login).not.toHaveBeenCalled()
      expect($location.url).not.toHaveBeenCalled()
      expect(loginViewCtrl.loginFailed).toBe(false)
    }))

    it('should call login on the identity service with the supplied username and password if the form is valid', inject(function ($rootScope, $location) {
      // ARRANGE
      spyOn($location, 'url')
      spyOn(identityService, 'login').and.callThrough()
      loginViewCtrl.username = 'bogieman'
      loginViewCtrl.password = 'hackerTDog'

      // ACT
      loginViewCtrl.login({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.login).toHaveBeenCalledWith('bogieman', 'hackerTDog')
      expect($location.url).toHaveBeenCalledWith('/bookings')
      expect(loginViewCtrl.loginFailed).toBe(false)
    }))

    it('should not navigate away from the form if the login call fails', inject(function ($rootScope, $location, $q) {
      // ARRANGE
      spyOn($location, 'url')
      spyOn(identityService, 'login').and.returnValue($q(function (resolve, reject) { reject('Boom') }))
      loginViewCtrl.username = 'bogieman'
      loginViewCtrl.password = 'hackerTDog'
      expect(loginViewCtrl.loginFailed).toBe(false)

      // ACT
      loginViewCtrl.login({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.login).toHaveBeenCalledWith('bogieman', 'hackerTDog')
      expect($location.url).not.toHaveBeenCalled()
      expect(loginViewCtrl.loginFailed).toBe(true)
    }))
  })
})
