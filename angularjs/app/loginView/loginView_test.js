/**
 * Copyright 2016-2017 Robin Steel
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

  describe('LoginViewCtrl controller respondToNewPasswordChallenge function', function () {
    // This method is called to complete the forced new password
    // challenge during a user's first login.

    it('should do nothing if the form is invalid', function () {
      // ARRANGE
      spyOn(identityService, 'completeNewPasswordChallenge').and.callThrough()
      // User must change their password during initial authentication
      loginViewCtrl.newPasswordRequired = true
      // This method is called only from the forced password change form
      loginViewCtrl.showForcedPasswordChangeForm = true

      // ACT
      // Try to submit an invalid form
      loginViewCtrl.respondToNewPasswordChallenge({'$invalid': true})

      // ASSERT
      expect(identityService.completeNewPasswordChallenge).not.toHaveBeenCalled()
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      // Flow not completed - so still need to change their password
      expect(loginViewCtrl.newPasswordRequired).toBe(true)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      // Still should be showing form for new password
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(true)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
    })

    it('should call completeNewPasswordChallenge on the identity service with the supplied password and user attributes if the form is valid', inject(function ($rootScope) {
      // During the initial login flow, Cognito supplies us with the user's existing attributes. Think we may not need to
      // return these to Cognito unless we've changed/added to them - but currently we do return them unchanged. We also
      // must supply the user's new password. This tests the happy path.

      // ARRANGE
      spyOn(identityService, 'completeNewPasswordChallenge').and.callThrough()
      loginViewCtrl.userAttributes = {'given name': 'Basil', 'family name': 'Brush'}
      loginViewCtrl.newPassword = 'B00m B00m!'
      loginViewCtrl.newPasswordCopy = 'B00m B00m!'
      // This method is called only from the forced password change form
      loginViewCtrl.showForcedPasswordChangeForm = true

      // ACT
      loginViewCtrl.respondToNewPasswordChallenge({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.completeNewPasswordChallenge).toHaveBeenCalledWith('B00m B00m!', {'given name': 'Basil', 'family name': 'Brush'})
      expect(loginViewCtrl.loginFailed).toBe(false)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(true)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      // Should no longer be showing form for new password
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      // Flow completed - so no longer need to change their password
      expect(loginViewCtrl.newPasswordRequired).toBe(false)
    }))

    it('should return early if the supplied passwords dont match', inject(function ($rootScope) {
      // User needs to enter a new password and then retype it. We should return
      // early if they make a mistake when retyping it.

      // ARRANGE
      spyOn(identityService, 'completeNewPasswordChallenge').and.callThrough()
      // User must change their password during initial authentication
      loginViewCtrl.newPasswordRequired = true
      // This method is called only from the forced password change form
      loginViewCtrl.showForcedPasswordChangeForm = true
      loginViewCtrl.userAttributes = {'given name': 'Basil', 'family name': 'Brush'}
      loginViewCtrl.newPassword = 'B00m B00m!'
      // Make the copy not match
      loginViewCtrl.newPasswordCopy = 'B00m B00m Mr R0y!'

      // ACT
      loginViewCtrl.respondToNewPasswordChallenge({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.completeNewPasswordChallenge).not.toHaveBeenCalled()
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(true)
      expect(loginViewCtrl.loginFailed).toBe(false)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      // Flow not completed - so still need to change their password
      expect(loginViewCtrl.newPasswordRequired).toBe(true)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      // Still should be showing form for new password
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(true)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
    }))

    it('should flag the failure if the supplied password is not strong enough', inject(function ($q, $rootScope) {
      // User currently needs to enter a new password at least 6 letters long - for no particular reason...
      // We should mark the lack of password strength if a shorter password is supplied.

      // ARRANGE
      // Configure identity service to return 'password not strong enough' error
      spyOn(identityService, 'completeNewPasswordChallenge').and.returnValue($q(function (resolve, reject) {
        reject({'message': 'Password does not conform to policy'})
      }))
      // User must change their password during initial authentication
      loginViewCtrl.newPasswordRequired = true
      // This method is called only from the forced password change form
      loginViewCtrl.showForcedPasswordChangeForm = true
      loginViewCtrl.userAttributes = {'given name': 'Basil', 'family name': 'Brush'}
      // Set too-weak password
      loginViewCtrl.newPassword = 'fox'
      loginViewCtrl.newPasswordCopy = 'fox'

      // ACT
      loginViewCtrl.respondToNewPasswordChallenge({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(true)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(true)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      expect(loginViewCtrl.loginFailed).toBe(false)
      // Flow not completed - so still need to change their password
      expect(loginViewCtrl.newPasswordRequired).toBe(true)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      // Still should be showing form for new password
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(true)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
    }))
  })

  describe('LoginViewCtrl controller forgotPassword function', function () {
    // This method is called to show a form to enter your username and request a
    // forgot-password code, i.e. to begin the forgot-password flow.

    it('should show the form for requesting a forgot-password verification code', function () {
      // ACT
      // Begin the forgot-password flow
      loginViewCtrl.forgotPassword()

      // ASSERT
      expect(loginViewCtrl.showForgotPasswordForm).toBe(true)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
    })
  })

  describe('LoginViewCtrl controller requestForgotPasswordCode function', function () {
    // This method is called to continue the forgot-password flow by requesting
    // a verification code to enter with a new password.

    it('should do nothing if the form is invalid', function () {
      // ARRANGE
      spyOn(identityService, 'requestForgotPasswordCode').and.callThrough()
      // This method is called only from the forgot password form
      loginViewCtrl.showForgotPasswordForm = true

      // ACT
      // Try to submit an invalid form
      loginViewCtrl.requestForgotPasswordCode({'$invalid': true})

      // ASSERT
      expect(identityService.requestForgotPasswordCode).not.toHaveBeenCalled()
      expect(loginViewCtrl.showForgotPasswordForm).toBe(true)
      expect(loginViewCtrl.forgotPasswordFlowFailed).toBe(false)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
    })

    it('should call requestForgotPasswordCode on the identity service if the form is valid', inject(function ($rootScope) {
      // This tests the happy path.

      // ARRANGE
      spyOn(identityService, 'requestForgotPasswordCode').and.callThrough()
      // This method is called only from the forgot password form
      loginViewCtrl.showForgotPasswordForm = true

      // ACT
      loginViewCtrl.requestForgotPasswordCode({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.requestForgotPasswordCode).toHaveBeenCalled()
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(true)
      expect(loginViewCtrl.forgotPasswordFlowFailed).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
      expect(loginViewCtrl.loginFailed).toBe(false)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      expect(loginViewCtrl.newPasswordRequired).toBe(false)
    }))

    it('should flag the failure if the verification code request fails', inject(function ($q, $rootScope) {
      // If the call to the identity service fails, we want to set a boolean
      // so that we can give feedback to the user.

      // ARRANGE
      // Configure identity service to return an error
      spyOn(identityService, 'requestForgotPasswordCode').and.returnValue($q(function (resolve, reject) {
        reject('Boom!')
      }))
      // This method is called only from the forgot password form
      loginViewCtrl.showForgotPasswordForm = true

      // ACT
      loginViewCtrl.requestForgotPasswordCode({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.requestForgotPasswordCode).toHaveBeenCalled()
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(true)
      expect(loginViewCtrl.forgotPasswordFlowFailed).toBe(true)
      expect(loginViewCtrl.loginFailed).toBe(false)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      expect(loginViewCtrl.newPasswordRequired).toBe(false)
    }))
  })

  describe('LoginViewCtrl controller resetPassword function', function () {
    // This method is called to complete the forgot password flow. User needs to
    // enter the emailed verification code, and their new password (twice).

    it('should do nothing if the form is invalid', function () {
      // ARRANGE
      spyOn(identityService, 'resetPassword').and.callThrough()
      // This method is called only from the forgot password change form
      loginViewCtrl.showForgotPasswordChangeForm = true

      // ACT
      // Try to submit an invalid form
      loginViewCtrl.resetPassword({'$invalid': true})

      // ASSERT
      expect(identityService.resetPassword).not.toHaveBeenCalled()
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(true)
      expect(loginViewCtrl.forgotPasswordFlowFailed).toBe(false)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
    })

    it('should call resetPassword on the identity service with the verification code and password if the form is valid', inject(function ($rootScope) {
      // This tests the happy path.

      // ARRANGE
      spyOn(identityService, 'resetPassword').and.callThrough()
      // This method is called only from the forgot password change form
      loginViewCtrl.showForgotPasswordChangeForm = true
      loginViewCtrl.verificationCode = '1234'
      loginViewCtrl.newPassword = 'B00m B00m!'
      loginViewCtrl.newPasswordCopy = 'B00m B00m!'

      // ACT
      // Try to complete the forgot-password flow
      loginViewCtrl.resetPassword({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.resetPassword).toHaveBeenCalledWith('1234', 'B00m B00m!')
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(true)
      expect(loginViewCtrl.forgotPasswordFlowFailed).toBe(false)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
    }))

    it('should return early if the supplied passwords dont match', inject(function ($rootScope) {
      // User needs to enter a new password and then retype it. We should return
      // early if they make a mistake when retyping it.

      // ARRANGE
      spyOn(identityService, 'resetPassword').and.callThrough()
      // This method is called only from the forgot password change form
      loginViewCtrl.showForgotPasswordChangeForm = true
      loginViewCtrl.verificationCode = '4321'
      loginViewCtrl.newPassword = 'B00m B00m!'
      // Make the copy not match
      loginViewCtrl.newPasswordCopy = 'B00m B00m Mr R0y!'

      // ACT
      loginViewCtrl.resetPassword({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(identityService.resetPassword).not.toHaveBeenCalled()
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(true)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(true)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.forgotPasswordFlowFailed).toBe(false)
      expect(loginViewCtrl.forcedPasswordChangeFailed).toBe(false)
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(false)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
    }))

    it('should flag the failure if the supplied password is not strong enough', inject(function ($q, $rootScope) {
      // User currently needs to enter a new password at least 6 letters long - for no particular reason...
      // We should mark the lack of password strength if a shorter password is supplied.

      // ARRANGE
      // Configure identity service to return 'password not strong enough' error. Note this message for
      // the forgot-password flow differs from that for the forced-password-change flow.
      spyOn(identityService, 'resetPassword').and.returnValue($q(function (resolve, reject) {
        reject({'message': 'failed to satisfy constraint'})
      }))
      // This method is called only from the forgot password change form
      loginViewCtrl.showForgotPasswordChangeForm = true
      loginViewCtrl.verificationCode = '4321'
      // Set too-weak password
      loginViewCtrl.newPassword = 'fox'
      loginViewCtrl.newPasswordCopy = 'fox'

      // ACT
      loginViewCtrl.resetPassword({'$invalid': false})

      // Trigger the promise chain
      $rootScope.$apply()

      // ASSERT
      expect(loginViewCtrl.passwordNotStrongEnough).toBe(true)
      expect(loginViewCtrl.forgotPasswordFlowFailed).toBe(true)
      expect(loginViewCtrl.newPasswordsDontMatch).toBe(false)
      expect(loginViewCtrl.loginFailed).toBe(false)
      expect(loginViewCtrl.passwordChangeSucceeded).toBe(false)
      expect(loginViewCtrl.showForgotPasswordChangeForm).toBe(true)
      expect(loginViewCtrl.showForcedPasswordChangeForm).toBe(false)
      expect(loginViewCtrl.showForgotPasswordForm).toBe(false)
    }))
  })
})
