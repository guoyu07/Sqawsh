module.exports = function (config) {
  config.set({
    basePath: './',

    files: [
      'app/bower_components/angular/angular.js',
      'app/bower_components/angular-route/angular-route.js',
      'app/bower_components/angular-mocks/angular-mocks.js',

      'app/components/identity/mockIdentityService.js',
      'app/components/bookings/mockBookingsService.js',
      'app/reservationView/**/*.js',
      'app/cancellationView/**/*.js',
      'app/loginView/**/*.js',
      'app/bookingView/bookingView.js',
      'app/bookingView/bookingView_test.js',
      'app/bookingView/**/*.js'
    ],

    logLevel: config.LOG_DEBUG,

    frameworks: ['jasmine'],

    browsers: ['Chrome'],

    plugins: [
      'karma-chrome-launcher',
      'karma-firefox-launcher',
      'karma-jasmine',
      'karma-junit-reporter'
    ],

    junitReporter: {
      outputFile: 'test_out/unit.xml',
      suite: 'unit'
    },

    singleRun: false
  })
}
