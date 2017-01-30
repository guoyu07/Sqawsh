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

/* global moment */

'use strict'

angular.module('squashApp.bookingRuleView', ['ngRoute', 'squashApp.bookingsService', 'squashApp.identityService', 'ngAnimate', 'ngSanitize', 'ui.bootstrap'])

  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/bookingrules', {
      templateUrl: 'bookingRuleView/bookingRuleView.html',
      controller: 'BookingRuleViewCtrl as ctrl'
    })
  }])

  .controller('BookingRuleViewCtrl', ['$scope', '$location', '$timeout', 'BookingService', 'IdentityService', function ($scope, $location, $timeout, BookingService, IdentityService) {
    var self = this

    resetErrorState()
    self.courtNumbers = BookingService.getCourtNumbers() // e.g. 1, 2, 3, 4, 5
    self.courtSpans = self.courtNumbers
    self.timeSlots = BookingService.getTimeSlots() // e.g. array of Date objects with minutes set to: 10:00 AM, ..., 9:15 PM
    // Time slot spans in time slot units e.g. 1, 2, ..., 15, 16
    self.timeSlotSpans = Array(self.timeSlots.length).fill(0).map((x, i, a) => (i + 1))

    self.datesOfAddExclusionDatepickers = []

    // Set some defaults for the add-new-rule form
    self.newRuleCourt = 1
    self.newRuleCourtSpan = 1
    self.newRuleDatepickerPopupOpened = false
    self.newRuleIsRecurring = false
    self.newRuleTimeSlot = self.timeSlots[0]
    self.newRuleTimeSlotSpan = 1
    self.addRuleFormVisible = false

    // Prevent gradual rendering on initial load
    self.initialLoadSucceeded = false
    self.isRetired = false
    self.isReadonly = false
    // Boolean used to show error ui when loading of the booking rules fails
    self.loadFailure = false

    // Updates datepickers used to add rule exclusions to existing rules
    self.updateAddExclusionDatepickers = function () {
      // Datepickers should allow exclusion dates only on the same day-of-the-week as their rule start date
      var createDateDisablerFunction = function (ruleIndex) {
        return function (data) {
          var bookingRuleStartDate = moment(self.bookingRules[ruleIndex].booking.date).toDate()
          var dayOfWeek = bookingRuleStartDate.getDay()
          var testDay = data.date.getDay()
          var day = (data.mode === 'day')
          var eq = (testDay !== dayOfWeek)
          return day && eq
        }
      }
      self.datesOfAddExclusionDatepickers = []
      self.addExclusionDatepickerPopupsOpened = []
      self.addExclusionDateOptions = []
      var addExclusionDateOptionsBase = {
        formatYear: 'yy',
        maxDate: moment(new Date()).add(1, 'years').toDate(),
        startingDay: 1 // Monday in left column
      }
      for (var ruleIndex = 0; ruleIndex < self.bookingRules.length; ruleIndex++) {
        self.addExclusionDateOptions[ruleIndex] = angular.copy(addExclusionDateOptionsBase)
        self.addExclusionDateOptions[ruleIndex].dateDisabled = createDateDisablerFunction(ruleIndex)
        self.addExclusionDateOptions[ruleIndex].minDate = self.bookingRules[ruleIndex].booking.date
        self.datesOfAddExclusionDatepickers[ruleIndex] = moment(self.bookingRules[ruleIndex].booking.date).toDate()
        self.addExclusionDatepickerPopupsOpened.push(false)
      }

      self.openAddExclusionDatepickerPopup = function (index) {
        self.addExclusionDatepickerPopupsOpened[index] = true
      }
    }

    self.onExclusionDateSelected = function (ruleIndex) {
      self.addExclusion(ruleIndex)
    }

    self.addExclusion = function (ruleIndex) {
      resetErrorState()
      var newExclusionDate = moment(self.datesOfAddExclusionDatepickers[ruleIndex]).format('YYYY-MM-DD')
      BookingService.addRuleExclusion(
        self.bookingRules[ruleIndex],
        newExclusionDate
      )
        .then(function (result) {
          self.refreshBookingRules()
        })
        .catch(function (error) {
          if (typeof error.data !== 'undefined' && (error.data.hasOwnProperty('errorMessage'))) {
            if (error.data.errorMessage.indexOf('too many exclusions') > -1) {
              self.tooManyExclusions = true
            } else if (error.data.errorMessage.startsWith('Cannot access bookings')) {
              // Service is retired - so extract the forwarding url from the error message.
              var message = error.data.errorMessage
              var httpIndex = message.lastIndexOf('http')
              self.forwardingUrl = message.substring(httpIndex)
              self.isRetired = true
            } else if (error.data.errorMessage.startsWith('Cannot mutate bookings or rules - booking service is temporarily readonly')) {
              self.isReadonly = true
            } else if (error.data.errorMessage.indexOf('You must login to manage booking rules') > -1) {
              self.unauthenticatedBookingRulesError = true
            }
          }
          self.bookingRuleExclusionAdditionFailed = true
          updateUi()
        })
    }

    self.removeExclusion = function (ruleIndex, dateToExclude) {
      resetErrorState()
      BookingService.deleteRuleExclusion(
        self.bookingRules[ruleIndex],
        dateToExclude
      )
        .then(function (result) {
          self.refreshBookingRules()
        })
        .catch(function (error) {
          if (typeof error.data !== 'undefined' && (error.data.hasOwnProperty('errorMessage'))) {
            if (error.data.errorMessage.indexOf('latent clash exists') > -1) {
              self.latentClashExists = true
            } else if (error.data.errorMessage.startsWith('Cannot access bookings')) {
              // Service is retired - so extract the forwarding url from the error message.
              var message = error.data.errorMessage
              var httpIndex = message.lastIndexOf('http')
              self.forwardingUrl = message.substring(httpIndex)
              self.isRetired = true
            } else if (error.data.errorMessage.startsWith('Cannot mutate bookings or rules - booking service is temporarily readonly')) {
              self.isReadonly = true
            } else if (error.data.errorMessage.indexOf('You must login to manage booking rules') > -1) {
              self.unauthenticatedBookingRulesError = true
            }
          }
          self.bookingRuleExclusionDeletionFailed = true
          updateUi()
        })
    }

    function resetErrorState () {
      self.tooManyRules = false
      self.tooManyExclusions = false
      self.bookingRuleCreationFailed = false
      self.bookingRuleExclusionAdditionFailed = false
      self.newRuleWouldClash = false
      self.latentClashExists = false
      self.bookingRuleDeletionFailed = false
      self.bookingRuleExclusionDeletionFailed = false
      self.unauthenticatedBookingRulesError = false
      self.isRetired = false
      self.isReadonly = false
      self.loadFailure = false
    }

    self.refreshBookingRules = function () {
      // Start asynchronous load of booking rules
      return BookingService.getBookingRules()
        .then(function (rules) {
          self.bookingRules = rules.bookingRules
          self.lifecycleState = rules.lifecycleState
          self.forwardingUrl = rules.forwardingUrl
          if (self.lifecycleState.toUpperCase() === 'READONLY') {
            self.isReadonly = true
          }
        })
        .then(function () {
          // Need dates so can prevent rule creation for an already bookable date
          return BookingService.getValidDates(new BookingRuleDataBuilder())
        })
        .then(function (builder) {
          var validDates = builder.getValidDates()
          self.earliestNewRuleDate = moment(validDates[validDates.length - 1]).add(1, 'days').toDate()
          self.newRuleDate = self.earliestNewRuleDate
          updateNewRuleDatepickerOptions()
          self.updateAddExclusionDatepickers()
          self.initialLoadSucceeded = true
          self.hideAddRuleForm()
          self.loadFailure = false
          updateUi()
        })
        .catch(function (error) {
          console.log('Error caught in refresh rules:')
          console.dir(error)
          if ((typeof error.data !== 'undefined') && (error.data.hasOwnProperty('errorMessage'))) {
            if (error.data.errorMessage.startsWith('Cannot access bookings')) {
              // Service is retired - so extract the forwarding url from the error message.
              var message = error.data.errorMessage
              var httpIndex = message.lastIndexOf('http')
              self.forwardingUrl = message.substring(httpIndex)
              self.isRetired = true
              self.bookingRules = []
              self.hideAddRuleForm()
              self.loadFailure = false
              updateUi()
              return
            }
          }
          // Don't know how to recover from this one...
          // Set flag so UI can display a general failure message...
          self.loadFailure = true
          self.bookingRules = []
          self.hideAddRuleForm()
          updateUi()
        })
    }

    // Initial booking-rule fetch
    self.refreshBookingRules()

    self.showAddRuleForm = function () {
      self.addRuleFormVisible = true
    }

    self.hideAddRuleForm = function () {
      self.addRuleFormVisible = false
    }

    self.ruleIndex = function (bookingRule) {
      // This is to provide the rule index of a rule ignoring sorting rules in the view
      return self.bookingRules.map(function (testRule) { return angular.equals(bookingRule, testRule) }).indexOf(true)
    }

    self.openNewRuleDatepickerPopup = function () {
      self.newRuleDatepickerPopupOpened = true
    }

    self.updateAllowedCourtSpans = function () {
      self.courtSpans = Array(self.courtNumbers.length - self.newRuleCourt + 1).fill(0).map((x, i, a) => (i + 1))
      // Ensure the selected court span is still valid
      if (self.courtSpans.indexOf(self.newRuleCourtSpan) === -1) {
        self.newRuleCourtSpan = self.courtSpans[self.courtSpans.length - 1]
      }
    }

    self.updateAllowedTimeSlotSpans = function () {
      var slotIndex = self.timeSlots.indexOf(self.newRuleTimeSlot)
      self.timeSlotSpans = Array(self.timeSlots.length - slotIndex).fill(0).map((x, i, a) => (i + 1))
      // Ensure the selected time slot span is still valid
      if (self.timeSlotSpans.indexOf(self.newRuleTimeSlotSpan) === -1) {
        self.newRuleTimeSlotSpan = self.timeSlotSpans[self.timeSlotSpans.length - 1]
      }
    }

    self.newRuleDatepickerOptions = {
      minDate: self.earliestNewRuleDate,
      showWeeks: false
    }

    function updateNewRuleDatepickerOptions () {
      self.newRuleDatepickerOptions = {
        minDate: self.earliestNewRuleDate,
        showWeeks: false
      }
    }

    self.addNewRule = function (form) {
      if (form.$invalid) {
        return
      }
      resetErrorState()
      BookingService.createBookingRule(
        self.newRuleName,
        self.newRuleCourt,
        self.newRuleCourtSpan,
        self.newRuleTimeSlot,
        self.newRuleTimeSlotSpan,
        self.newRuleDate,
        self.newRuleIsRecurring
      )
        .then(function (result) {
          self.refreshBookingRules()
        })
        .catch(function (error) {
          console.log('Error caught in add rule:')
          console.dir(error)
          if (typeof error.data !== 'undefined' && (error.data.hasOwnProperty('errorMessage'))) {
            if (error.data.errorMessage.indexOf('new rule would clash.') > -1) {
              self.newRuleWouldClash = true
            } else if (error.data.errorMessage.startsWith('Cannot access bookings')) {
              // Service is retired - so extract the forwarding url from the error message.
              var message = error.data.errorMessage
              var httpIndex = message.lastIndexOf('http')
              self.forwardingUrl = message.substring(httpIndex)
              self.isRetired = true
            } else if (error.data.errorMessage.startsWith('Cannot mutate bookings or rules - booking service is temporarily readonly')) {
              self.isReadonly = true
            } else if (error.data.errorMessage.indexOf('You must login to manage booking rules') > -1) {
              self.unauthenticatedBookingRulesError = true
            } else if (error.data.errorMessage.indexOf('too many rules.') > -1) {
              self.tooManyRules = true
            }
          }
          self.bookingRuleCreationFailed = true
          updateUi()
        })
    }

    self.deleteRule = function (ruleIndex) {
      resetErrorState()
      BookingService.deleteBookingRule(angular.copy(self.bookingRules[ruleIndex]))
        .then(function (result) {
          self.refreshBookingRules()
        })
        .catch(function (error) {
          if (typeof error.data !== 'undefined' && (error.data.hasOwnProperty('errorMessage'))) {
            if (error.data.errorMessage.startsWith('Cannot access bookings')) {
              // Service is retired - so extract the forwarding url from the error message.
              var message = error.data.errorMessage
              var httpIndex = message.lastIndexOf('http')
              self.forwardingUrl = message.substring(httpIndex)
              self.isRetired = true
            } else if (error.data.errorMessage.startsWith('Cannot mutate bookings or rules - booking service is temporarily readonly')) {
              self.isReadonly = true
            } else if (error.data.errorMessage.indexOf('You must login to manage booking rules') > -1) {
              self.unauthenticatedBookingRulesError = true
            }
          }
          self.bookingRuleDeletionFailed = true
          self.hideAddRuleForm()
          updateUi()
        })
    }

    self.status = {
      isCustomHeaderOpen: false,
      isFirstOpen: true,
      isFirstDisabled: false
    }

    function updateUi () {
      // Wrap in timeout to avoid '$digest already in progress' error.
      $timeout(function () {
        $scope.$apply()
      })
    }

    self.returnToBookings = function () {
      $location.url('/bookings')
    }

    function BookingRuleDataBuilder () {
      this.validDates = undefined

      this.setValidDates = function (validDates) {
        this.validDates = angular.copy(validDates)
      }
      this.getValidDates = function () {
        return this.validDates
      }
    }
  }])
