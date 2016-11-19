module.exports = function (grunt) {
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    jshint: {
      files: ['Gruntfile.js', 'src/**/*.js', 'test/**/*.js'],
      options: {
        // options here to override JSHint defaults
        globals: {
          jQuery: true,
          console: true,
          module: true,
          document: true
        }
      }
    },
    concat: {
      options: {
        separator: ';'
      },
      dist: {
        src: ['src/**/*.js'],
        dest: 'dist/<%= pkg.name %>.js'
      }
    },
    uglify: {
      options: {
        banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
      },
      dist: {
        files: {
          'dist/<%= pkg.name %>.min.js': ['<%= concat.dist.dest %>']
        }
      }
    },
    // Still use less - but use postcss for css minification.
    less: {
      angular: {
        options: {
          compress: false,
          optimization: 2,
          plugins: [],
          relativeUrls: true
        },
        files: {
          'app/sqawsh.lessmin.css': 'app/sqawsh.less' // destination file and source file
        }
      },
      // Have separate css for no-script app - so it can ditch bootstrap.
      noscript: {
        options: {
          compress: false,
          optimization: 2,
          plugins: [],
          relativeUrls: true
        },
        files: {
          'app/sqawshNoScript.lessmin.css': 'app/sqawshNoScript.less' // destination file and source file
        }
      }
    },
    postcss: {
      angular: {
        options: {
          map: {
            inline: false, // save all sourcemaps as separate files...
            annotation: 'dist/css/maps/' // ...to the specified directory
          },
          processors: [
            // Ignore css linting for now...
            // require("stylelint")({config: {
            //  "extends": "stylelint-config-standard"
            // }}),
            require('pixrem')(), // add fallbacks for rem units
            require('postcss-cssnext')({browsers: '>1%, last 15 versions'}), // autoprefixing etc.
            require('cssnano')(), // minify the result
            require('postcss-reporter')()
          ],
          failOnError: true
        },
        files: {
          'app/sqawsh.min.css': 'app/sqawsh.lessmin.css' // destination file and source file
        }
      },
      noscript: {
        options: {
          map: {
            inline: false, // save all sourcemaps as separate files...
            annotation: 'dist/css/maps/' // ...to the specified directory
          },
          processors: [
            // Ignore css linting for now...
            // require("stylelint")({config: {
            //  "extends": "stylelint-config-standard"
            // }}),
            require('pixrem')(), // add fallbacks for rem units
            require('postcss-cssnext')({browsers: '>1%, last 15 versions'}), // autoprefixing etc.
            require('cssnano')(), // minify the result
            require('postcss-reporter')()
          ],
          failOnError: true
        },
        files: {
          'app/sqawshNoScript.min.css': 'app/sqawshNoScript.lessmin.css' // destination file and source file
        }
      }
    }
  })

  // Load the plugins.
  grunt.loadNpmTasks('grunt-contrib-jshint')
  grunt.loadNpmTasks('grunt-contrib-concat')
  grunt.loadNpmTasks('grunt-contrib-uglify')
  grunt.loadNpmTasks('grunt-contrib-less')
  grunt.loadNpmTasks('grunt-postcss')
}
