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

/* global AWS, AWSCognito, sjcl */

'use strict'

angular.module('squashApp.identityService', [])
  .factory('IdentityService', ['$q', '$location', function ($q, $location) {
    var self = this
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
      // refreshing the user pool tokens if necessary. If the user pool 'refresh token'
      // itself has expired, this routes the user to the login screen to re-authenticate.
      // N.B. If the user has logged out, they (the LastAuthUser key), as well as their
      // three user pool session tokens, will no longer be in local storage (see
      // CognitoUser::clearCachedTokens), so this will, correctly, not attempt to refresh
      // their (non-existent) user pool session in that case.
      return $q(function (resolve, reject) {
        var poolData = {
          UserPoolId: comSquashUserPoolId,
          ClientId: comSquashClientAppId,
          Paranoia: 7
        }
        var userPool = new AWSCognito.CognitoIdentityServiceProvider.CognitoUserPool(poolData)
        var cognitoUser = userPool.getCurrentUser()
        if (cognitoUser !== null) {
          // User must have authenticated - though some of their tokens may since have expired. If
          // necessary, this will attempt to use the refresh token to refresh the user pool session.
          cognitoUser.getSession(function (err, userPoolSession) {
            if ((userPoolSession !== null) && userPoolSession.isValid()) {
              resolve(userPoolSession)
            } else {
              // Assume re-authentication is always required here, whether err is true or
              // not - though think err will always be true here.
              if (err) {
                // Think the user pool refresh token will have expired if we get here.
                console.log('getSession gave error: ' + err)
              }
              // FIXME: Should really show dialog here before routing
              $location.url('/login')
              resolve()
            }
          })
        } else {
          // User has either never logged in, or has since logged out - so we have no user pool session.
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
        // Should not get here...
        console.log('Caught error getting user pool session: ' + err)
      }).then(function () {
        // Ensure the credentials get refreshed if the logins have changed
        if (loginsAreDifferent(newLogins, AWS.config.credentials.params.Logins)) {
          // We've just launched and/or we've just changed the logins array
          AWS.config.credentials.params.Logins = newLogins
          isAuthenticated = newAuthenticatedState
        }
        return $q(function (resolve, reject) {
          AWS.config.credentials.get(function (error) {
            if (error) {
              reject(error)
            } else {
              resolve()
            }
          })
        })
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
        self.cognitoUserSettingPassword = null
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
              if (typeof AWS.config.credentials !== 'undefined') {
                // Expire so credentials are refreshed (getting authenticated credentials) before next use
                AWS.config.credentials.expired = true
              }
              doUpdateAwsTemporaryCredentials().then(function () {
                resolve()
              })
            },
            onFailure: function (err) {
              doUpdateAwsTemporaryCredentials().then(function () {
                reject(err)
              })
            },
            newPasswordRequired: function (userAttributes, requiredAttributes) {
              // User was signed up by an admin and must provide new password to complete authentication.
              // userAttributes: object, which is the user's current profile. It will list all attributes
              // that are associated with the user. Required attributes according to schema, which don’t
              // have any values yet, will have blank values. requiredAttributes: list of attributes that
              // must be set by the user along with new password to complete the sign-in.
              self.cognitoUserSettingPassword = cognitoUser
              resolve(userAttributes)
            }
          })
        })
      },
      completeNewPasswordChallenge: function (newPassword, userAttributes) {
        // Called to complete the very first login
        return $q(function (resolve, reject) {
          sjcl.random.startCollectors()
          self.cognitoUserSettingPassword.completeNewPasswordChallenge(newPassword, null, {
            onSuccess: function (result) {
              if (typeof AWS.config.credentials !== 'undefined') {
                // Expire so credentials are refreshed (getting authenticated credentials) before next use
                AWS.config.credentials.expired = true
              }
              self.cognitoUserSettingPassword = null
              doUpdateAwsTemporaryCredentials().then(function () {
                resolve(false)
              })
            },
            onFailure: function (err) {
              doUpdateAwsTemporaryCredentials().then(function () {
                reject(err)
              })
            }
          })
        })
      },
      requestForgotPasswordCode: function (username) {
        // Called to begin a forgot-password flow
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
        return $q(function (resolve, reject) {
          cognitoUser.forgotPassword({
            onSuccess: function (result) {
              self.cognitoUserSettingPassword = cognitoUser
              resolve()
            },
            onFailure: function (err) {
              reject(err)
            }
          })
        })
      },
      resetPassword: function (verificationCode, newPassword) {
        // Called to complete a forgot-password flow
        return $q(function (resolve, reject) {
          self.cognitoUserSettingPassword.confirmPassword(verificationCode, newPassword, {
            onSuccess: function (result) {
              self.cognitoUserSettingPassword = null
              resolve()
            },
            onFailure: function (err) {
              reject(err)
            }
          })
        })
      },
      logout: function () {
        return $q(function (resolve, reject) {
          // Whenever a user logs in, the CognitoIdentityCredentials will ensure they get the same Cognito id
          // (or at least some previous id already associated with their Login). When they use the system when logged
          // out, we also want them to reuse a single guest Cognito id as much as possible (i.e. until they next log
          // in - at which point their guest id gets promoted/disabled by Cognito), rather than repeatedly getting new
          // guest ids, which works, but is wasteful. The CognitoIdentityCredentials will also handle this guest id reuse
          // - but only if it doesn't have a cached authenticated id (if it does - it will try to refresh credentials for
          // the cached authenticated id without supplying any Logins - and so throw an error). So we clear the cached
          // authenticated id here when we logout - allowing CognitoIdentityCredentials to do its job for guests too.
          if (typeof AWS.config.credentials !== 'undefined') {
            AWS.config.credentials.clearCachedId()
            // Expire so credentials are refreshed (for the new guest id) before next use
            AWS.config.credentials.expired = true
          }
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
                // Could be bc refresh token expired, or other reason.
                // Clear session and cached user - so we don't ask the user to login again immediately...
                cognitoUser.clearCachedTokens()
              }
              // Attempt to revoke access and refresh tokens. If getSession gave an error, it is
              // unlikely globalSignOut will succeed - as need to be authenticated to call it. This
              // is most likely bc refresh token has expired - in which case it doesn't really matter
              // if globalSignOut fails - as we are effectively signed-out anyway.
              cognitoUser.globalSignOut({
                onSuccess: function (result) {
                  doUpdateAwsTemporaryCredentials().then(function () {
                    resolve()
                  })
                },
                onFailure: function (err) {
                  // May have failed to revoke tokens, but at least clear them from local storage.
                  cognitoUser.clearCachedTokens()
                  doUpdateAwsTemporaryCredentials().then(function () {
                    reject(err)
                  })
                }
              })
            })
          } else {
            // User has either never logged in, or has since logged out - so we have no user pool session.
            // Think we should never hit this - as a not-logged-in user could not call Logout.
            doUpdateAwsTemporaryCredentials().then(function () {
              resolve()
            })
          }
        })
      }
    }
  }])
