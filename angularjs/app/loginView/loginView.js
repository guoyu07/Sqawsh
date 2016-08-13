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

angular.module('squashApp.loginView', ['ngRoute', 'squashApp.identityService'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/login', {
      templateUrl: 'loginView/loginView.html',
      controller: 'LoginViewCtrl as ctrl'
    })
  }])

  .controller('LoginViewCtrl', ['$scope', '$location', 'IdentityService', function ($scope, $location, IdentityService) {
    var self = this

    self.returnToBookings = function () {
      $location.url('/bookings')
    }

    self.authenticate = function (form) {
      if (form.$invalid) {
        return
      }

      // The form is valid - so let's try to authenticate
      IdentityService.authenticate(self.username, self.password)
      console.log('called authenticate')
    }
  }])
