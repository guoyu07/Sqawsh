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

/* global alert, AWS, AWSCognito, sjcl*/

'use strict'

angular.module('squashApp.identityService', [])
  .factory('IdentityService', [function () {
    var isAuthenticated = false
    var comSquashRegion = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashIdentityPoolId = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashUserPoolId = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashUserPoolIdentityProviderName = 'stringtobereplaced' // will be replaced at stack creation time
    var comSquashClientAppId = 'stringtobereplaced' // will be replaced at stack creation time
    AWS.config.region = comSquashRegion // Region
    AWSCognito.config.region = comSquashRegion // Region

    return {
      setUpGuestCredentials: function () {
        AWS.config.credentials = new AWS.CognitoIdentityCredentials({
          IdentityPoolId: comSquashIdentityPoolId
        })
      },
      isAuthenticated: function () {
        return isAuthenticated
      },
      authenticate: function (username, password) {
        sjcl.random.startCollectors()

        console.log('Starting authenticate...')
        var authenticationData = {
          Username: username,
          Password: password
        }
        var authenticationDetails = new AWSCognito.CognitoIdentityServiceProvider.AuthenticationDetails(authenticationData)
        console.log('Got authenticatation details...')
        var poolData = {
          UserPoolId: comSquashUserPoolId,
          ClientId: comSquashClientAppId,
          Paranoia: 7
        }
        var userPool = new AWSCognito.CognitoIdentityServiceProvider.CognitoUserPool(poolData)
        console.log('Got user pool...')
        var userData = {
          Username: username,
          Pool: userPool
        }
        var cognitoUser = new AWSCognito.CognitoIdentityServiceProvider.CognitoUser(userData)
        console.log('Got cognito user...')
        return cognitoUser.authenticateUser(authenticationDetails, {
          onSuccess: function (result) {
            console.log('Authenticated with user pool...')
            console.log('User pool access token + ' + result.getAccessToken().getJwtToken())

            var loginsValue = {}
            loginsValue[comSquashUserPoolIdentityProviderName] = result.getIdToken().getJwtToken()
            AWS.config.credentials = new AWS.CognitoIdentityCredentials({
              IdentityPoolId: comSquashIdentityPoolId,
              Logins: loginsValue
            })
            AWS.config.credentials.get(function (error) {
              if (error) {
                console.log('Failed to get temporary credentials...')
                isAuthenticated = false
                alert(error)
              }
              console.log('Got temporary credentials...')
              isAuthenticated = true
              console.log('Access key: ' + AWS.config.credentials.accessKeyId)
              console.log('Secret access key: ' + AWS.config.credentials.secretAccessKey)
              console.log('Session token: ' + AWS.config.credentials.sessionToken)
            })
          },
          onFailure: function (err) {
            console.log('Failed to authenticate user...')
            isAuthenticated = false
            alert(err)
            this.setUpGuestCredentials()
          }
        })
      },
      logout: function () {
        var poolData = {
          UserPoolId: comSquashUserPoolId,
          ClientId: comSquashClientAppId,
          Paranoia: 7
        }
        var userPool = new AWSCognito.CognitoIdentityServiceProvider.CognitoUserPool(poolData)
        var cognitoUser = userPool.getCurrentUser()
        if (cognitoUser != null) {
          cognitoUser.signOut()
          isAuthenticated = false
          this.setUpGuestCredentials()
        }
      }
    }
  }])
