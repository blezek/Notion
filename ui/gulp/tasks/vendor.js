var gulp = require('gulp'),
  uglify = require('gulp-uglify');

// Vended source
gulp.task('vendor', function() {
  gulp.src([
    'bower_components/jquery/dist/jquery.js',
    'bower_components/toastr/toastr.js',
    'bower_components/angular-ui-ace/ui-ace.js',
    'bower_components/angular-ui-bootstrap-bower/ui-bootstrap-tpls.js',
    'bower_components/angular-ui-router/release/angular-ui-router.js',
    'bower_components/angular-ui-select/dist/select.js',
    'bower_components/backbone/backbone.js',
    'bower_components/clipboard/dist/clipboard.js',
    'bower_components/notifyjs/dist/notify-combined.js',
    'bower_components/angular/angular.js',
    'bower_components/angular-route/angular-route.js',
    'bower_components/underscore/underscore.js',
    'bower_components/angular-bindonce/bindonce.js',
    'bower_components/w11k-dropdownToggle/dist/w11k-dropdownToggle.js',
    'bower_components/w11k-select/dist/w11k-select.js',
    'bower_components/w11k-select/dist/w11k-select.tpl.js'
  ])
  // .pipe(uglify())
  .pipe(gulp.dest('public/js'));

  gulp.src('bower_components/ace-builds/src-noconflict/**')
  .pipe(gulp.dest("public/js/ace"));

  gulp.src('bower_components/bootstrap/dist/**')
  .pipe(gulp.dest("public/"));

  gulp.src([
    'bower_components/font-awesome/css/font-awesome*.css',
    'bower_components/w11k-select/dist/w11k-select.css',
    'bower_components/toastr/toastr.css'
    ])
  .pipe(gulp.dest('public/css'));

  gulp.src('bower_components/font-awesome/fonts/**').pipe(gulp.dest('public/fonts'));


  gulp.src(['bower_components/freeboard/js/**']).pipe(gulp.dest('public/dashboard/js/'));
  gulp.src(['bower_components/freeboard/css/**']).pipe(gulp.dest('public/dashboard/css/'));
  gulp.src(['bower_components/freeboard/img/**']).pipe(gulp.dest('public/dashboard/img/'));
gulp.src(['bower_components/freeboard/plugins/**']).pipe(gulp.dest('public/dashboard/plugins/'));


});
