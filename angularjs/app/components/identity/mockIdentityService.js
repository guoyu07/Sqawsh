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

angular.module('squashApp.identityService', [])
  .factory('IdentityService', ['$q', function ($q) {
    return {
      updateCredentials: function () {},
      isLoggedIn: function () { return false },
      login: function (username, password) {
        return $q(function (resolve, reject) {
          resolve()
        })
      },
      logout: function () {
        return $q(function (resolve, reject) {
          resolve()
        })
      }
    }
  }])
