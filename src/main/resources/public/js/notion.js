

// Init the Clipboard module
$( document ).ready(function() {
  new Clipboard('.clipboard');
})



// Connect the AJAX error function
$( document ).ajaxError(function( event, jqxhr, settings, thrownError ) {
  var destination = settings.url;
  toastr.error("REST command failed.  This likely indicates a problem with the server.  Notion was trying to reach " + destination + ' <p>Reload <a href="index.html">here</a></p>', "Network Error", { positionClass: "toast-top-full-width", hideDuration: "1000000" });
  console.error("REST failed, see following for details");
  console.error("jQuery event:", event);
  console.error("XHR:", jqxhr);
  console.error("Settings:", settings);
  console.error("Thrown error:", thrownError);
});


// for some unknown reason, we have to do this first under Chrome?
var poolCollection = new PoolCollection();
poolCollection.fetch();

// Notifications

notionApp = angular.module('notionApp', ['ui.router', 'ui.bootstrap', 'ui.ace', 'w11k.select', 'w11k.select.template']);

notionApp.config(function($stateProvider, $urlRouterProvider) {
  $urlRouterProvider.otherwise('/pools/index');
  $urlRouterProvider.when('', '/pools/index');

  $stateProvider
  .state('root', {
    abstract: true,
    url: '',
    templateUrl: 'partials/root.html',
    controller: 'RootController'
  })
  .state('root.pools', {
    abstract: true,
    url: '/pools',
    templateUrl: 'partials/pools.html',
    controller: 'PoolsController',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.pools.index', {
    url: "/index",
    templateUrl: 'partials/pools.index.html',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.pools.pool', {
    url: "/:poolKey",
    templateUrl: 'partials/pool.root.html',
    controller: 'PoolRootController',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.pools.pool.detail', {
    url: "/detail",
    templateUrl: 'partials/pool.detail.html',
    controller: 'PoolController',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.pools.pool.studies', {
    url: "/studies",
    templateUrl: 'partials/pool.studies.html',
    controller: 'StudyController',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.pools.pool.authorization', {
    url: "/authorization",
    templateUrl: 'partials/pool.authorization.html',
    controller: 'AuthorizationController',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.pools.pool.query', {
    url: "/query",
    templateUrl: 'partials/pool.query.html',
    controller: 'QueryController',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.pools.pool.queryresult', {
    url: "/result/:queryKey",
    templateUrl: 'partials/pool.queryresult.html',
    controller: 'QueryResultController',
    data: {
      access: ['admin', 'user']
    }
  })
  .state('root.connectors', {
    url: "/connectors",
    templateUrl: 'partials/connectors.html',
    controller: 'ConnectorsController',
    data: {
      access: ['admin']
    }
  })
  .state('root.groups', {
    url: "/groups",
    templateUrl: 'partials/groups.html',
    controller: 'GroupController',
    data: {
      access: ['admin']
    }
  })
  .state('root.users', {
    url: "/users",
    templateUrl: 'partials/users.html',
    controller: 'UserController',
    data: {
      access: ['admin']
    }
  })
  .state('root.loggedout', {
    templateUrl: 'partials/loggedout.html',
  });

});


notionApp.config(function ($httpProvider) {
  $httpProvider.interceptors.push([
    '$injector',
    function ($injector) {
      return $injector.get('AuthInterceptor');
    }
    ]);
  });



// Extra directive
notionApp.directive('ngEnter', function () {
    return function (scope, element, attrs) {
        element.bind("keydown keypress", function (event) {
            if(event.which === 13) {
                scope.$apply(function (){
                    scope.$eval(attrs.ngEnter);
                });

                event.preventDefault();
            }
        });
    };
});


  notionApp.constant('EVENTS', {
    loginSuccess: 'auth-login-success',
    loginFailed: 'auth-login-failed',
    logoutSuccess: 'auth-logout-success',
    sessionTimeout: 'auth-session-timeout',
    notAuthenticated: 'auth-not-authenticated',
    notAuthorized: 'auth-not-authorized'
  });

  notionApp.factory('AuthInterceptor', function ($rootScope, $q,EVENTS) {
    return {
      responseError: function (response) {
        if (response.status === 401) {
          $rootScope.$broadcast(EVENTS.notAuthenticated,
            response);
          }
          if (response.status === 403) {
            $rootScope.$broadcast(EVENTS.notAuthorized,
              response);
            }
            if (response.status === 419 || response.status === 440) {
              $rootScope.$broadcast(EVENTS.sessionTimeout,
                response);
              }
              return $q.reject(response);
            }
          };
        });



        // authorization works by checking to see if the user can access certain routes.
        // The check is done by role.
        notionApp.factory ( 'authorization', function($http) {
          var u = { username: null, roles: [] };
          function changeUser(newUser) {
            // console.log("Changing user to ", newUser, 'from ', u)
            angular.extend(u,newUser.user);
          }
          return {
            isLoggedIn: function() {
              if ( u === undefined ) {
                return false;
              }
              var r = u.username !== null;
              // console.log("isLoggedIn:", u, r);
              return u.username !== null;
            },
            logout: function(success, error) {
              $http.post("/rest/user/logout")
              .success( function() {
                changeUser({username: null, roles:[]});
                success();
              }).error(error);
            },
            checkLogin: function(success,error) {
              $http.get("/rest/user/").success(function(result){
                // console.log("checkLogin: ", result );
                changeUser(result);
                if ( success ) { success(); }
              }).error(error);
            },
            login: function(credentials, success, error) {
              $http.post('/rest/user/login', credentials ).success(function(result) {
                u = result;
                changeUser(u);
                success();
              }).error(error);
            },
            isPermitted: function(expectedRoles) {
              // console.log("isPermitted: ", expectedRoles, " for user ", u)
              if ( u === undefined  ) {
                // console.log("undefined or no roles")
                return false; }
                for ( var i = 0; i < expectedRoles.length; i++ ) {
                  // console.log("jquery:", $.inArray(String(expectedRoles[i]), u.roles))
                  // console.log("jquery #2:", $.inArray('admin', u.roles))
                  // console.log("looking at ", expectedRoles[i], u.roles.indexOf(expectedRoles[i]) )
                  for ( var j = 0; j < u.roles.length; j++ ) {
                    if ( String(u.roles[j]) === String(expectedRoles[i])) {
                      return true;
                    }
                  }

                }
                return false;
              },
              user: u
            };
          });

          notionApp.run(['$rootScope', '$state', 'authorization', function( $rootScope, $state, authorization ) {
            console.log ( "run from notionApp", authorization.isLoggedIn() );

            $rootScope.$on("$stateChangeStart", function(e, toState, toParams, fromState, fromParams) {
              return;
            });
          }]);




        console.log ("Build notion app");
