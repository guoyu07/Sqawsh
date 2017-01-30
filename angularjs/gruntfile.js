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
module.exports = function (grunt) {
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    // JS processing pipeline
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
        files: {
          'dist/<%= pkg.name %>.js': ['app/**/*.js', 'node_modules/babel-polyfill/dist/polyfill.js', '!**/*_test.js', '!app/bower_components/**/*.js', '!app/**/*mock*.js']
        }
      }
    },
    babel: {
      options: {
        sourceMap: true,
        presets: ['es2015']
      },
      dist: {
        files: {
          'dist/<%= pkg.name %>.babel.js': 'dist/<%= pkg.name %>.js'
        }
      }
    },
    uglify: {
      options: {
        banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n',
        compress: {
          properties: false
        }
      },
      dist: {
        files: {
          'dist/<%= pkg.name %>.min.js': 'dist/<%= pkg.name %>.babel.js'
        }
      }
    },
    // CSS processing pipeline
    // Still use less - but use postcss for css minification.
    less: {
      squash: {
        options: {
          compress: false,
          optimization: 2,
          plugins: [],
          relativeUrls: true
        },
        files: {
          'dist/sqawsh.lessmin.css': 'app/sqawsh.less' // destination file and source file
        }
      }
    },
    postcss: {
      squash: {
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
          'dist/sqawsh.min.css': 'dist/sqawsh.lessmin.css' // destination file and source file
        }
      }
    }
  })

  // Load the plugins.
  grunt.loadNpmTasks('grunt-contrib-jshint')
  grunt.loadNpmTasks('grunt-contrib-concat')
  grunt.loadNpmTasks('grunt-babel')
  grunt.loadNpmTasks('grunt-contrib-uglify')
  grunt.loadNpmTasks('grunt-contrib-less')
  grunt.loadNpmTasks('grunt-postcss')
}
