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

/* global AWS, AWSCognito, sjcl */

'use strict'

angular.module('squashApp.identityService', [])
  .factory('IdentityService', ['$q', function ($q) {
    var comSquashRegion = 'identityregiontobereplaced' // will be replaced at stack creation time
    var comSquashIdentityPoolId = 'identitypoolidtobereplaced' // will be replaced at stack creation time
    var comSquashUserPoolId = 'identityuserpoolidtobereplaced' // will be replaced at stack creation time
    var comSquashUserPoolIdentityProviderName = 'identityprovidernametobereplaced' // will be replaced at stack creation time
    var comSquashClientAppId = 'identityappidtobereplaced' // will be replaced at stack creation time
    var isAuthenticated = false
    AWS.config.region = comSquashRegion // Region
    AWSCognito.config.region = comSquashRegion // Region

    // We create this provider once only, so that other AWS service clients never need their
    // provider replacing after they are constructed. We need only to update the logins
    // array on this provider as we log in/out and as the id- and refresh-tokens expire.
    AWS.config.credentials = new AWS.CognitoIdentityCredentials({
      IdentityPoolId: comSquashIdentityPoolId
    })

    var doGetUserPoolSession = function () {
      // If we have a user in local storage, this gets their user pool session,
      // refreshing the user pool tokens if necessary. N.B. It will fail if the
      // user pool 'refresh token' itself has expired.
      return $q(function (resolve, reject) {
        var poolData = {
          UserPoolId: comSquashUserPoolId,
          ClientId: comSquashClientAppId,
          Paranoia: 7
        }
        var userPool = new AWSCognito.CognitoIdentityServiceProvider.CognitoUserPool(poolData)
        var cognitoUser = userPool.getCurrentUser()
        if (cognitoUser !== null) {
          cognitoUser.getSession(function (err, userPoolSession) {
            // Return null unless we have a valid user pool session
            if ((userPoolSession !== null) && userPoolSession.isValid()) {
              resolve(userPoolSession)
            } else {
              if (err) {
                console.log('getSession gave error: ' + err)
              }
              resolve()
            }
          })
        } else {
          resolve()
        }
      })
    }

    var doUpdateAwsTemporaryCredentials = function () {
      // Updates our temporary credentials (access key id, secret access key, and session
      // token) if they've expired, or the user has just logged in/out. N.B. This 'session'
      // is different to the user pool 'session'.

      // Before refreshing the credentials, ensure the logins array on the credentials object
      // is up-to-date. It is either null (for guest users), or has the user pool id token
      // (for authenticated users).
      var newLogins = {}
      var newAuthenticatedState = false
      return doGetUserPoolSession().then(function (userPoolSession) {
        if ((userPoolSession !== undefined) && (userPoolSession !== null)) {
          // We're authenticated with the user pool
          newAuthenticatedState = true
          newLogins[comSquashUserPoolIdentityProviderName] = userPoolSession.getIdToken().getJwtToken()
        } else {
          newLogins = null
        }
      }).catch(function (err) {
        console.log('Swallowing errors whilst updating credentials: ' + err)
      }).then(function () {
        // Ensure the credentials get refreshed if the logins have changed
        if (loginsAreDifferent(newLogins, AWS.config.credentials.params.Logins)) {
          // We've just launched and/or we've just changed the logins array
          AWS.config.credentials.params.Logins = newLogins
          isAuthenticated = newAuthenticatedState
          if (AWS.config.credentials.params.Logins === null) {
            // We've just launched, or we've just logged out - so ensure we don't try to refresh credentials for
            // an authenticated id when we have no logins. Clearing the cached Cognito id means the refresh call
            // will get a new guest id before getting new credentials for it. N.B. we can't reuse an old id:
            // Cognito will have promoted or disabled it when we last logged-in.
            AWS.config.credentials.clearCachedId()
          }

          // Don't expire for now - or else we end up get-ting new creds twice, since we currently do it manually here...
          // AWS.config.credentials.expired = true
          // Get new credentials - need to do this manually as the generated ApiGateway client does not support CognitoIdentityCredentials.
          // Should subclass it so that it does...
          return $q(function (resolve, reject) {
            AWS.config.credentials.refresh(function (error) {
              if (error) {
                reject(error)
              } else {
                resolve()
              }
            })
          })
        } else {
          // We have suitable, but possibly-expired, credentials - so refresh only if they've expired
          return $q(function (resolve, reject) {
            AWS.config.credentials.get(function (error) {
              if (error) {
                reject(error)
              } else {
                resolve()
              }
            })
          })
        }
      }).catch(function (err) {
        throw err
      })
    }

    var loginsAreDifferent = function (logins1, logins2) {
      // Detect if the two logins maps differ
      if (logins1 === undefined) {
        return logins2 !== undefined
      } else if (logins1 === null) {
        return logins2 !== null
      } else {
        if ((logins2 === undefined) || (logins2 === null)) {
          return true
        }
        var logins1Value = logins1[comSquashUserPoolIdentityProviderName]
        var logins2Value = logins2[comSquashUserPoolIdentityProviderName]
        return logins1Value !== logins2Value
      }
    }

    return {
      updateCredentials: function () {
        return doUpdateAwsTemporaryCredentials()
      },
      isLoggedIn: function () {
        return isAuthenticated
      },
      login: function (username, password) {
        return $q(function (resolve, reject) {
          sjcl.random.startCollectors()
          var authenticationData = {
            Username: username,
            Password: password
          }
          var authenticationDetails = new AWSCognito.CognitoIdentityServiceProvider.AuthenticationDetails(authenticationData)
          var poolData = {
            UserPoolId: comSquashUserPoolId,
            ClientId: comSquashClientAppId,
            Paranoia: 7
          }
          var userPool = new AWSCognito.CognitoIdentityServiceProvider.CognitoUserPool(poolData)
          var userData = {
            Username: username,
            Pool: userPool
          }
          var cognitoUser = new AWSCognito.CognitoIdentityServiceProvider.CognitoUser(userData)
          cognitoUser.authenticateUser(authenticationDetails, {
            onSuccess: function (result) {
              doUpdateAwsTemporaryCredentials().then(resolve())
            },
            onFailure: function (err) {
              doUpdateAwsTemporaryCredentials().then(reject(err))
            }
          })
        })
      },
      logout: function () {
        return $q(function (resolve, reject) {
          var poolData = {
            UserPoolId: comSquashUserPoolId,
            ClientId: comSquashClientAppId,
            Paranoia: 7
          }
          var userPool = new AWSCognito.CognitoIdentityServiceProvider.CognitoUserPool(poolData)
          var cognitoUser = userPool.getCurrentUser()
          if (cognitoUser !== null) {
            cognitoUser.getSession(function (err, userPoolSession) {
              if (err) {
                doUpdateAwsTemporaryCredentials().then(reject(err))
              } else {
                // FIXME: do only if session valid?
                cognitoUser.globalSignOut({
                  onSuccess: function (result) {
                    doUpdateAwsTemporaryCredentials().then(resolve())
                  },
                  onFailure: function (err) {
                    doUpdateAwsTemporaryCredentials().then(reject(err))
                  }
                })
              }
            })
          }
          doUpdateAwsTemporaryCredentials().then(resolve())
        })
      }
    }
  }])
