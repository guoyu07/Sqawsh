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

/* global moment */

'use strict'

describe('squashApp.bookingRuleView module', function () {
  var bookingService
  var getBookingRulesSpy
  var createBookingRuleSpy
  var addExclusionSpy
  var deleteBookingRuleSpy
  var deleteExclusionSpy
  var bookingRuleToAdd

  beforeEach(function () {
    // Load mock booking and identity service modules: this avoids AWS dependencies...
    module('squashApp.identityService')
    module('squashApp.bookingsService')
  })

  // Module under test
  beforeEach(module('squashApp.bookingRuleView'))

  var bookingRuleViewCtrl
  beforeEach(inject(function ($rootScope, $controller, BookingService) {
    // Configure mock response from the BookingService
    getBookingRulesSpy = spyOn(BookingService, 'getBookingRules')
      .and.callThrough()
    createBookingRuleSpy = spyOn(BookingService, 'createBookingRule')
      .and.callThrough()
    addExclusionSpy = spyOn(BookingService, 'addRuleExclusion')
      .and.callThrough()
    deleteBookingRuleSpy = spyOn(BookingService, 'deleteBookingRule')
      .and.callThrough()
    deleteExclusionSpy = spyOn(BookingService, 'deleteRuleExclusion')
      .and.callThrough()
    spyOn(BookingService, 'getValidDates')
      .and.callThrough()
    bookingService = BookingService

    // Create the controller now that the mock is set up
    var scope = $rootScope.$new()
    bookingRuleViewCtrl = $controller('BookingRuleViewCtrl', {$scope: scope})

    // Set up a new rule to add for the addNewRule tests
    bookingRuleToAdd = {
      booking: {name: 'Monday recurring rule', court: 3, courtSpan: 2, slot: 1, slotSpan: 5, date: '2016-10-03'},
      isRecurring: true,
      datesToExclude: ['2016-07-03']
    }
    bookingRuleViewCtrl.newRuleName = bookingRuleToAdd.booking.name
    bookingRuleViewCtrl.newRuleCourt = bookingRuleToAdd.booking.court
    bookingRuleViewCtrl.newRuleCourtSpan = bookingRuleToAdd.booking.courtSpan
    bookingRuleViewCtrl.newRuleTimeSlot = bookingRuleToAdd.booking.slot
    bookingRuleViewCtrl.newRuleTimeSlotSpan = bookingRuleToAdd.booking.slotSpan
    bookingRuleViewCtrl.newRuleDate = bookingRuleToAdd.booking.date
    bookingRuleViewCtrl.newRuleIsRecurring = bookingRuleToAdd.isRecurring
  }))

  describe('BookingRuleViewCtrl controller constructor function', function () {
    it('should refresh the booking rules', inject(function ($rootScope) {}))
  })

  describe('BookingRuleViewCtrl controller updateAddExclusionDatepickers function', function () {
    it('should prevent adding exclusions on days-of-week that differ from the booking rule start day-of-week', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Reset add exclusion datepickers to their uninitialised state
      bookingRuleViewCtrl.bookingNames = undefined

      var sundayRuleIndex = 0
      var sundayRuleDateDisablerFunction = bookingRuleViewCtrl.addExclusionDateOptions[sundayRuleIndex].dateDisabled
      var tuesdayRuleIndex = 1
      var tuesdayRuleDateDisablerFunction = bookingRuleViewCtrl.addExclusionDateOptions[tuesdayRuleIndex].dateDisabled

      // ACT
      bookingRuleViewCtrl.updateAddExclusionDatepickers()

      // ASSERT
      // Sunday rule should have disabled all days except Sunday:
      var data = {'mode': 'day'}
      var sunday = moment().startOf('week')
      var disabledDates = [moment(sunday).add(1, 'days'), moment(sunday).add(2, 'days'), moment(sunday).add(3, 'days'), moment(sunday).add(4, 'days'), moment(sunday).add(5, 'days'), moment(sunday).add('days', 6)]
      disabledDates.forEach((element, index, array) => {
        data.date = element.toDate()
        expect(sundayRuleDateDisablerFunction(data)).toBe(true)
      })
      data.date = sunday.toDate()
      expect(sundayRuleDateDisablerFunction(data)).toBe(false)

      // Tuesday rule should have disabled all days except Tuesday (not that it's relevant for a non-recurring rule...):
      var tuesday = moment().startOf('week').add('days', 2)
      disabledDates = [moment(tuesday).add(1, 'days'), moment(tuesday).add(2, 'days'), moment(tuesday).add(3, 'days'), moment(tuesday).add(4, 'days'), moment(tuesday).add(5, 'days'), moment(tuesday).add('days', 6)]
      disabledDates.forEach((element, index, array) => {
        data.date = element.toDate()
        expect(tuesdayRuleDateDisablerFunction(data)).toBe(true)
      })
      data.date = tuesday.toDate()
      expect(tuesdayRuleDateDisablerFunction(data)).toBe(false)
    }))

    it('should prevent adding exclusions before the booking rule start date', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0
      var tuesdayRuleIndex = 1

      // ACT
      bookingRuleViewCtrl.updateAddExclusionDatepickers()

      // ASSERT
      // The min dates should equal the rule start dates
      expect(bookingRuleViewCtrl.addExclusionDateOptions[sundayRuleIndex].minDate).toEqual('2016-10-02')
      expect(bookingRuleViewCtrl.addExclusionDateOptions[tuesdayRuleIndex].minDate).toEqual('2016-10-04')
    }))

    it('should initialise the current day of the add exclusion datepickers to their booking rule start date', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0
      var tuesdayRuleIndex = 1

      // ACT
      bookingRuleViewCtrl.updateAddExclusionDatepickers()

      // ASSERT
      expect(bookingRuleViewCtrl.datesOfAddExclusionDatepickers[sundayRuleIndex]).toEqual(moment('2016-10-02').toDate())
      expect(bookingRuleViewCtrl.datesOfAddExclusionDatepickers[tuesdayRuleIndex]).toEqual(moment('2016-10-04').toDate())
    }))

    it('should initialise the add exclusion datepickers to be closed', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // ACT
      bookingRuleViewCtrl.updateAddExclusionDatepickers()

      // ASSERT
      expect(bookingRuleViewCtrl.addExclusionDatepickerPopupsOpened).toEqual([false, false])
    }))

    it('should prevent adding rule exclusions more than a year into the future', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0
      var tuesdayRuleIndex = 1

      // ACT
      bookingRuleViewCtrl.updateAddExclusionDatepickers()

      // ASSERT
      var maxDateFromOneYear = moment.duration(moment(bookingRuleViewCtrl.addExclusionDateOptions[sundayRuleIndex].maxDate).diff(moment(new Date()).add(1, 'years')))
      var hoursAwayFromOneYear = maxDateFromOneYear.asHours()
      expect(hoursAwayFromOneYear).toBeLessThan(1)
      maxDateFromOneYear = moment.duration(moment(bookingRuleViewCtrl.addExclusionDateOptions[tuesdayRuleIndex].maxDate).diff(moment(new Date()).add(1, 'years')))
      hoursAwayFromOneYear = maxDateFromOneYear.asHours()
      expect(hoursAwayFromOneYear).toBeLessThan(1)
    }))
  })

  describe('BookingRuleViewCtrl controller openAddExclusionDatepickerPopup function', function () {
    it('should open the corresponding add exclusion datepicker popup', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0
      var tuesdayRuleIndex = 1
      expect(bookingRuleViewCtrl.addExclusionDatepickerPopupsOpened[sundayRuleIndex]).toEqual(false)
      expect(bookingRuleViewCtrl.addExclusionDatepickerPopupsOpened[tuesdayRuleIndex]).toEqual(false)

      // ACT
      bookingRuleViewCtrl.openAddExclusionDatepickerPopup(sundayRuleIndex)

      // ASSERT
      expect(bookingRuleViewCtrl.addExclusionDatepickerPopupsOpened[sundayRuleIndex]).toEqual(true)
      expect(bookingRuleViewCtrl.addExclusionDatepickerPopupsOpened[tuesdayRuleIndex]).toEqual(false)

      // ACT
      bookingRuleViewCtrl.openAddExclusionDatepickerPopup(tuesdayRuleIndex)

      // ASSERT
      expect(bookingRuleViewCtrl.addExclusionDatepickerPopupsOpened[sundayRuleIndex]).toEqual(true)
      expect(bookingRuleViewCtrl.addExclusionDatepickerPopupsOpened[tuesdayRuleIndex]).toEqual(true)
    }))
  })

  describe('BookingRuleViewCtrl controller addExclusion function', function () {
    it('should pass the correct booking rule and exclusion date to the bookings service', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0
      // Set a date to exclude - must be a sunday
      bookingRuleViewCtrl.datesOfAddExclusionDatepickers[sundayRuleIndex] = new Date('2016-10-23')
      expect(bookingService.addRuleExclusion).not.toHaveBeenCalled()

      // ACT
      bookingRuleViewCtrl.addExclusion(sundayRuleIndex)

      // ASSERT
      // Expect the service was called correctly
      expect(addExclusionSpy.calls.count()).toEqual(1)
      expect(addExclusionSpy.calls.argsFor(0)).toEqual([bookingRuleViewCtrl.bookingRules[sundayRuleIndex], '2016-10-23'])
    }))

    it('should refresh the displayed booking rules after successfully adding the rule exclusion', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0
      // Set a date to exclude - must be a sunday
      bookingRuleViewCtrl.datesOfAddExclusionDatepickers[sundayRuleIndex] = new Date('2016-10-23')
      expect(bookingService.addRuleExclusion).not.toHaveBeenCalled()
      expect(getBookingRulesSpy.calls.count()).toEqual(1)

      // ACT
      bookingRuleViewCtrl.addExclusion(sundayRuleIndex)

      // ASSERT
      expect(getBookingRulesSpy.calls.count()).toEqual(1)
      // We detect a call to refresh the rules by getBookingRules being called again
      $rootScope.$apply()
      expect(getBookingRulesSpy.calls.count()).toEqual(2)
    }))

    it('should show a general warning to the user if adding an exclusion date fails for a reason other than exceeding the maximum number of excluded dates or lack of authentication', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure addRuleExclusion to return a non-too-many-exclusions error
      addExclusionSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'Boom!'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(false)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(false)

      // ACT
      bookingRuleViewCtrl.addExclusion(0)
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(true)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(false)
      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(false)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(false)
    }))

    it('should warn the user if adding an exclusion date fails because the user is not authenticated', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure addRuleExclusion to return a not-authenticated error
      addExclusionSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'You must login to manage booking rules.'})
      }))

      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(false)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)

      // ACT
      bookingRuleViewCtrl.addExclusion(0)
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(true)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(true)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(false)
    }))

    it('should warn the user if adding an exclusion date fails because the maximum number of excluded dates would have been exceeded', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure addRuleExclusion to return a too-many-exclusions error
      addExclusionSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'too many exclusions'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(false)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(false)

      // ACT
      bookingRuleViewCtrl.addExclusion(0)
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(true)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(true)
      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(false)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(false)
    }))
  })

  describe('BookingRuleViewCtrl controller removeExclusion function', function () {
    it('should pass the correct booking rule and exclusion date to the bookings service', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0
      expect(bookingService.deleteRuleExclusion).not.toHaveBeenCalled()

      // ACT
      // Remove the one exclusion currently present on the Sunday rule
      bookingRuleViewCtrl.removeExclusion(sundayRuleIndex, '2016-07-02')

      // ASSERT
      // Expect the service was called correctly
      expect(bookingService.deleteRuleExclusion.calls.count()).toEqual(1)
      expect(bookingService.deleteRuleExclusion.calls.argsFor(0)).toEqual([bookingRuleViewCtrl.bookingRules[sundayRuleIndex], '2016-07-02'])
    }))

    it('should refresh the displayed booking rules after successfully removing the rule exclusion', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0

      // ACT
      // Remove the one exclusion currently present on the Sunday rule
      bookingRuleViewCtrl.removeExclusion(sundayRuleIndex, '2016-07-02')

      // ASSERT
      expect(getBookingRulesSpy.calls.count()).toEqual(1)
      // We detect a call to refresh the rules by getBookingRules being called again
      $rootScope.$apply()
      expect(getBookingRulesSpy.calls.count()).toEqual(2)
    }))

    it('should show a general warning to the user if removing an exclusion date fails for a reason other than a latent rule clash or an authentication error', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0

      // Configure deleteRuleExclusion to return a non-latent-clash error
      deleteExclusionSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'Boom!'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(false)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(false)

      // ACT
      bookingRuleViewCtrl.removeExclusion(sundayRuleIndex, '2016-07-02')
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(true)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(false)
      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(false)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(false)
    }))

    it('should warn the user if removing an exclusion date fails because the user is not authenticated', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0

      // Configure deleteRuleExclusion to return the not-authenticated error
      deleteExclusionSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'You must login to manage booking rules.'})
      }))

      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(false)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)

      // ACT
      bookingRuleViewCtrl.removeExclusion(sundayRuleIndex, '2016-07-02')
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(true)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(true)
    }))

    it('should warn the user if removing an exclusion date fails because doing so would expose a latent rule clash', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      var sundayRuleIndex = 0

      // Configure deleteRuleExclusion to return the latent-clash error
      deleteExclusionSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'latent clash exists'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(false)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)

      // ACT
      bookingRuleViewCtrl.removeExclusion(sundayRuleIndex, '2016-07-02')
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleExclusionDeletionFailed).toBe(true)
      expect(bookingRuleViewCtrl.latentClashExists).toBe(true)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.bookingRuleExclusionAdditionFailed).toBe(false)
      expect(bookingRuleViewCtrl.tooManyExclusions).toBe(false)
    }))
  })

  describe('BookingRuleViewCtrl controller refreshBookingRules function', function () {
    it('should get the booking rules from the bookings service', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      expect(getBookingRulesSpy.calls.count()).toEqual(1)

      // ACT
      bookingRuleViewCtrl.refreshBookingRules()

      // ASSERT
      expect(getBookingRulesSpy.calls.count()).toEqual(2)
    }))

    it('should get the valid dates from the bookings service', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingService.getValidDates.calls.count()).toEqual(1)

      // ACT
      bookingRuleViewCtrl.refreshBookingRules()
      $rootScope.$apply()

      // ASSERT
      expect(bookingService.getValidDates.calls.count()).toEqual(2)
    }))

    it('should set the earliest date for adding a new rule to one day later than the last valid date', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Reset the earliest date
      bookingRuleViewCtrl.earliestNewRuleDate = null

      // ACT
      bookingRuleViewCtrl.refreshBookingRules()
      $rootScope.$apply()

      // ASSERT
      // Last valid date is '2016-04-24'
      var earliestDateDiff = moment.duration(moment(bookingRuleViewCtrl.earliestNewRuleDate).diff(moment('2016-04-25'))).asHours()
      expect(earliestDateDiff).toBeLessThan(2)
    }))

    it('should update the add-rule-exclusion datepickers after refreshing the rules', inject(function ($rootScope) {
      // ARRANGE
      spyOn(bookingRuleViewCtrl, 'updateAddExclusionDatepickers').and.callThrough()

      // Trigger the promise chain
      $rootScope.$apply()

      expect(bookingRuleViewCtrl.updateAddExclusionDatepickers.calls.count()).toEqual(1)

      // ACT
      bookingRuleViewCtrl.refreshBookingRules()
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.updateAddExclusionDatepickers.calls.count()).toEqual(2)
    }))

    it('should clear the load failure flag on successful refresh of the booking rules', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Set the load failure flag
      bookingRuleViewCtrl.loadFailure = true

      // ACT
      bookingRuleViewCtrl.refreshBookingRules()
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.loadFailure).toBe(false)
    }))

    it('should set the load failure flag if the bookings service returns an error', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure e.g. getBookingRules to return an error
      getBookingRulesSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'Boom!'})
      }))

      expect(bookingRuleViewCtrl.loadFailure).toBe(false)

      // ACT
      bookingRuleViewCtrl.refreshBookingRules()
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.loadFailure).toBe(true)
    }))
  })

  describe('BookingRuleViewCtrl controller ruleIndex function', function () {
    // The rules are sorted alphabetically by name in the view, but we want to know the
    // index of a rule in the contoller's unsorted booking rule array.
    it('should return the index of the provided rule', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // ACT and ASSERT
      expect(bookingRuleViewCtrl.ruleIndex(bookingRuleViewCtrl.bookingRules[0])).toBe(0)
      expect(bookingRuleViewCtrl.ruleIndex(bookingRuleViewCtrl.bookingRules[1])).toBe(1)
    }))
  })

  describe('BookingRuleViewCtrl controller updateAllowedCourtSpans function', function () {
    it('should set the minimum span to be 1 court', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Try first for new court number of 1
      bookingRuleViewCtrl.newRuleCourt = 1
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.courtSpans[0]).toBe(1)

      // Try for new court number of 5
      bookingRuleViewCtrl.newRuleCourt = 5
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.courtSpans[0]).toBe(1)
    }))

    it('should set the maximum span so that, starting from the new rule court number, the span ends at the highest court number', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Try first for new court number of 1
      bookingRuleViewCtrl.newRuleCourt = 1
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.courtSpans[bookingRuleViewCtrl.courtSpans.length - 1]).toBe(5)

      // Try for new court number of 2
      bookingRuleViewCtrl.newRuleCourt = 2
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.courtSpans[bookingRuleViewCtrl.courtSpans.length - 1]).toBe(4)
    }))

    it('should set the spans to include all spans between the minimum and maximum span', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Try first for new court number of 1
      bookingRuleViewCtrl.newRuleCourt = 1
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.courtSpans).toEqual([1, 2, 3, 4, 5])

      // Try for new court number of 2
      bookingRuleViewCtrl.newRuleCourt = 2
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.courtSpans).toEqual([1, 2, 3, 4])

      // Try for new court number of 5
      bookingRuleViewCtrl.newRuleCourt = 5
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.courtSpans).toEqual([1])
    }))

    it('should clamp the selected span at the maximum span if it exceeds the maximum span', inject(function ($rootScope) {
      // When the court is selected, the court spans update. If the selected span exceeds the new range, it
      // should be reset to the maximum value in the new range.

      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Try first for new court of 4
      bookingRuleViewCtrl.newRuleCourt = 4
      // Set a span that will exceed the new span range
      bookingRuleViewCtrl.newRuleCourtSpan = 5
      bookingRuleViewCtrl.updateAllowedCourtSpans()
      expect(bookingRuleViewCtrl.newRuleCourtSpan).toBe(2)
    }))
  })

  describe('BookingRuleViewCtrl controller updateAllowedTimeSlotSpans function', function () {
    it('should set the minimum span to be 1 time slot', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Try first for new court time slot of 9.15AM
      bookingRuleViewCtrl.newRuleTimeSlot = '9:15 AM'
      bookingRuleViewCtrl.updateAllowedTimeSlotSpans()
      expect(bookingRuleViewCtrl.timeSlotSpans[0]).toBe(1)

      // Try for new court number of 12.15PM
      bookingRuleViewCtrl.newRuleTimeSlot = '12:15 PM'
      bookingRuleViewCtrl.updateAllowedTimeSlotSpans()
      expect(bookingRuleViewCtrl.timeSlotSpans[0]).toBe(1)
    }))

    it('should set the maximum span so that, starting from the new rule time slot, the span ends at the latest time slot', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Try first for new court time slot of 9.15AM
      bookingRuleViewCtrl.newRuleTimeSlot = '9:15 AM'
      bookingRuleViewCtrl.updateAllowedTimeSlotSpans()
      expect(bookingRuleViewCtrl.timeSlotSpans[bookingRuleViewCtrl.timeSlotSpans.length - 1]).toBe(5)

      // Try for new court number of 12.15PM
      bookingRuleViewCtrl.newRuleTimeSlot = '12:15 PM'
      bookingRuleViewCtrl.updateAllowedTimeSlotSpans()
      expect(bookingRuleViewCtrl.timeSlotSpans[bookingRuleViewCtrl.timeSlotSpans.length - 1]).toBe(2)
    }))

    it('should set the spans to include all spans between the minimum and maximum span', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Try first for new court time slot of 9.15AM
      bookingRuleViewCtrl.newRuleTimeSlot = '9:15 AM'
      bookingRuleViewCtrl.updateAllowedTimeSlotSpans()
      expect(bookingRuleViewCtrl.timeSlotSpans).toEqual([1, 2, 3, 4, 5])

      // Try for new court number of 12.15PM
      bookingRuleViewCtrl.newRuleTimeSlot = '12:15 PM'
      bookingRuleViewCtrl.updateAllowedTimeSlotSpans()
      expect(bookingRuleViewCtrl.timeSlotSpans).toEqual([1, 2])
    }))

    it('should clamp the selected span at the maximum span if it exceeds the maximum span', inject(function ($rootScope) {
      // When the time slot is selected, the time slot spans update. If the selected span exceeds the new range, it
      // should be reset to the maximum value in the new range.

      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      bookingRuleViewCtrl.newRuleTimeSlot = '12:15 PM'
      // Set a span that will exceed the new span range
      bookingRuleViewCtrl.newRuleTimeSlotSpan = 5
      bookingRuleViewCtrl.updateAllowedTimeSlotSpans()
      expect(bookingRuleViewCtrl.newRuleTimeSlotSpan).toBe(2)
    }))
  })

  describe('BookingRuleViewCtrl controller addNewRule function', function () {
    it('should pass the correct booking rule parameters to the bookings service', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      expect(createBookingRuleSpy.calls.count()).toEqual(0)

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})

      // ASSERT
      // Expect the service was called correctly
      expect(createBookingRuleSpy.calls.count()).toEqual(1)
      expect(createBookingRuleSpy.calls.argsFor(0)).toEqual([
        bookingRuleToAdd.booking.name,
        bookingRuleToAdd.booking.court,
        bookingRuleToAdd.booking.courtSpan,
        bookingRuleToAdd.booking.slot,
        bookingRuleToAdd.booking.slotSpan,
        // N.B. The date for the new rule will have defaulted to 1 day after the final valid date i.e. 25th April 2016
        moment('2016-04-25').toDate(),
        bookingRuleToAdd.isRecurring
      ])
    }))

    it('should refresh the displayed booking rules after successfully adding the new rule', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      spyOn(bookingRuleViewCtrl, 'refreshBookingRules').and.callThrough()
      expect(bookingRuleViewCtrl.refreshBookingRules.calls.count()).toEqual(0)

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})
      $rootScope.$apply()

      // ASSERT
      // Expect the rules to have been refreshed
      expect(bookingRuleViewCtrl.refreshBookingRules.calls.count()).toEqual(1)
    }))

    it('should hide the add-rule form after successfully adding the new rule', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      spyOn(bookingRuleViewCtrl, 'refreshBookingRules').and.callThrough()
      // Set true - so we can tell if it is later set false
      bookingRuleViewCtrl.addRuleFormVisible = true

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.addRuleFormVisible).toBe(false)
    }))

    it('should not hide the add-rule form if attempting to add the new rule gives an error', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure createBookingRule to return an error
      createBookingRuleSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'Boom!'})
      }))
      // Set to true - so we can later verify the form has not been hidden
      bookingRuleViewCtrl.addRuleFormVisible = true

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.addRuleFormVisible).toBe(true)
    }))

    it('should not attempt to add a new rule if the new-rule form is invalid', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': true})

      // ASSERT
      expect(createBookingRuleSpy).not.toHaveBeenCalled()
    }))

    it('should show a general warning to the user if adding a rule fails for a reason other than exceeding the maximum number of rules, a clash, or an authentication error', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure createBookingRule to return a non-too-many-rules error
      createBookingRuleSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'Boom!'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(true)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)
    }))

    it('should warn the user if adding a rule fails because the maximum number of rules would have been exceeded', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure createBookingRule to return a too-many-rules error
      createBookingRuleSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'too many rules.'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(true)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(true)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)
    }))

    it('should warn the user if adding a rule fails because the user is not authenticated', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure createBookingRule to return a not-authenticated error
      createBookingRuleSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'You must login to manage booking rules.'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(true)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(true)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)
    }))

    it('should warn the user if adding a rule fails because the new rule would have clashed with an existing rule', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure createBookingRule to return a would-have-clashed error
      createBookingRuleSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'new rule would clash.'})
      }))
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)

      // ACT
      bookingRuleViewCtrl.addNewRule({'$invalid': false})
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleCreationFailed).toBe(true)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(true)
    }))
  })

  describe('BookingRuleViewCtrl controller deleteRule function', function () {
    it('should pass the correct booking rule to the bookings service', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      expect(deleteBookingRuleSpy.calls.count()).toEqual(0)

      // ACT
      // Try to delete the rule with index 1
      bookingRuleViewCtrl.deleteRule(1)

      // ASSERT
      // Expect the service was called correctly
      expect(deleteBookingRuleSpy.calls.count()).toEqual(1)
      expect(deleteBookingRuleSpy.calls.argsFor(0)).toEqual([bookingRuleViewCtrl.bookingRules[1]])

      // ACT
      // Also try to delete the rule with index 0 - just to check we don't ignore the rule index
      bookingRuleViewCtrl.deleteRule(0)

      // ASSERT
      // Expect the service was called correctly
      expect(deleteBookingRuleSpy.calls.count()).toEqual(2)
      expect(deleteBookingRuleSpy.calls.argsFor(1)).toEqual([bookingRuleViewCtrl.bookingRules[0]])
    }))

    it('should refresh the displayed booking rules after successfully removing the rule', inject(function ($rootScope) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      spyOn(bookingRuleViewCtrl, 'refreshBookingRules').and.callThrough()
      expect(bookingRuleViewCtrl.refreshBookingRules.calls.count()).toEqual(0)

      // ACT
      // Try to delete the rule with index 1
      bookingRuleViewCtrl.deleteRule(1)
      $rootScope.$apply()

      // ASSERT
      // Expect the rules to have been refreshed
      expect(bookingRuleViewCtrl.refreshBookingRules.calls.count()).toEqual(1)
    }))

    it('should warn the user if deleting a rule fails because the user is not authenticated', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure deleteBookingRule to return a not-authenticated error
      deleteBookingRuleSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'You must login to manage booking rules.'})
      }))

      expect(bookingRuleViewCtrl.bookingRuleDeletionFailed).toBe(false)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(false)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)

      // ACT
      bookingRuleViewCtrl.deleteRule(0)
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleDeletionFailed).toBe(true)
      expect(bookingRuleViewCtrl.unauthenticatedBookingRulesError).toBe(true)
      expect(bookingRuleViewCtrl.tooManyRules).toBe(false)
      expect(bookingRuleViewCtrl.newRuleWouldClash).toBe(false)
    }))

    it('should show a warning to the user if attempting to remove the rule gives an error', inject(function ($rootScope, $q) {
      // ARRANGE
      // Trigger the promise chain
      $rootScope.$apply()

      // Configure deleteBookingRule to return an error
      deleteBookingRuleSpy.and.returnValue($q(function (resolve, reject) {
        reject({'data': 'Boom!'})
      }))

      bookingRuleViewCtrl.bookingRuleDeletionFailed = false

      // ACT
      bookingRuleViewCtrl.deleteRule(0)
      $rootScope.$apply()

      // ASSERT
      expect(bookingRuleViewCtrl.bookingRuleDeletionFailed).toBe(true)
    }))
  })
})
