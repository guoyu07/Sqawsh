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

angular.module('squashApp.loginView', ['ngRoute', 'squashApp.identityService'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/login', {
      templateUrl: 'loginView/loginView.html',
      controller: 'LoginViewCtrl as ctrl'
    })
  }])

  .controller('LoginViewCtrl', ['$scope', '$location', 'IdentityService', function ($scope, $location, IdentityService) {
    var self = this
    self.loginFailed = false
    self.forcedPasswordChangeFailed = false
    self.forgotPasswordFlowFailed = false
    self.newPasswordsDontMatch = false
    self.newPasswordRequired = false
    self.passwordNotStrongEnough = false

    self.setUiVisibilities = function (login, forcedPasswordChange, forgotPassword, forgotPasswordChange, passwordChangeSucceeded) {
      self.showLoginForm = login
      self.showForcedPasswordChangeForm = forcedPasswordChange
      self.showForgotPasswordForm = forgotPassword
      self.showForgotPasswordChangeForm = forgotPasswordChange
      self.passwordChangeSucceeded = passwordChangeSucceeded
    }
    self.setUiVisibilities(true, false, false, false, false)

    self.returnToBookings = function () {
      $location.url('/bookings')
    }

    self.login = function (form) {
      if (form.$invalid) {
        return
      }

      self.loginFailed = false
      self.newPasswordRequired = false
      self.userAttributes = null

      // The form is valid - so let's try to login
      IdentityService.login(self.username, self.password)
        .then(function (userAttributes) {
          if (typeof userAttributes === 'undefined') {
            // User has logged in before - and changed their initial password -
            // so they're now authenticated
            self.returnToBookings()
          } else {
            // First login for this user - so they need to enter a new password
            self.userAttributes = userAttributes
            self.newPasswordRequired = true
            self.setUiVisibilities(false, true, false, false, false)
          }
        })
        .catch(function (err) {
          self.loginFailed = true
          console.log('login failed with error: ' + err)
        })
    }

    self.respondToNewPasswordChallenge = function (form) {
      // This is called after user logs in with their provided random initial password
      // and then supplies a password of their own.
      if (form.$invalid) {
        return
      }
      self.forcedPasswordChangeFailed = false
      self.passwordNotStrongEnough = false
      self.newPasswordsDontMatch = false

      // Check the password and its copy match:
      if (self.newPassword !== self.newPasswordCopy) {
        self.newPasswordsDontMatch = true
        return
      }

      // The form is valid- so let's complete the password challenge
      IdentityService.completeNewPasswordChallenge(self.newPassword, self.userAttributes)
        .then(function () {
          // We are now authenticated
          self.newPasswordRequired = false
          self.setUiVisibilities(false, false, false, false, true)
        })
        .catch(function (err) {
          self.forcedPasswordChangeFailed = true
          if (err.hasOwnProperty('message') && (err.message.indexOf('Password does not conform to policy') !== -1)) {
            self.passwordNotStrongEnough = true
          }
          console.log('Failed to provide new password with error: ' + err)
        })
    }

    self.forgotPassword = function (form) {
      self.setUiVisibilities(false, false, true, false, false)
    }

    self.requestForgotPasswordCode = function (form) {
      // This is called after user enters their username to reset their password.
      if (form.$invalid) {
        return
      }
      self.forgotPasswordFlowFailed = false

      // The form is valid- so let's retrieve the password reset code
      IdentityService.requestForgotPasswordCode(self.username)
        .then(function () {
          // Inform the user that they should be receiving an email with a password reset code
          self.setUiVisibilities(false, false, false, true, false)
        })
        .catch(function (err) {
          console.log('Error requesting password reset code: ' + err)
          self.forgotPasswordFlowFailed = true
        })
    }

    self.resetPassword = function (form) {
      // This is called after user enters their new password and reset code.
      if (form.$invalid) {
        return
      }

      self.forgotPasswordFlowFailed = false
      self.passwordNotStrongEnough = false

      // Check the password and its copy match:
      self.newPasswordsDontMatch = false
      if (self.newPassword !== self.newPasswordCopy) {
        self.newPasswordsDontMatch = true
        return
      }

      // The form is valid- so let's reset the password
      IdentityService.resetPassword(self.verificationCode, self.newPassword)
        .then(function () {
          self.setUiVisibilities(false, false, false, false, true)
        })
        .catch(function (err) {
          self.forgotPasswordFlowFailed = true
          if (err.hasOwnProperty('message') && (err.message.indexOf('failed to satisfy constraint') !== -1)) {
            self.passwordNotStrongEnough = true
          }
          console.log('Password reset failed: ' + err)
        })
    }
  }])
