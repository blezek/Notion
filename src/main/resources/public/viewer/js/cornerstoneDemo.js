// Load in HTML templates

function getParameterByName(name) {
  var match = RegExp('[?&]' + name + '=([^&]*)').exec(window.location.search);
  return match && decodeURIComponent(match[1].replace(/\+/g, ' '));
}

var hash = getParameterByName('hash');
var poolKey = getParameterByName('poolKey');

var viewportTemplate; // the viewport template
loadTemplate("templates/viewport.html", function(element) {
  viewportTemplate = element;
});

var studyViewerTemplate; // the study viewer template
loadTemplate("templates/studyViewer.html", function(element) {
  studyViewerTemplate = element;
});

if ( poolKey ) {
  // Get study list from JSON manifest
  $.getJSON('/rest/pool/' + poolKey + '/viewer/studies', function(data) {
    data.studyList.forEach(function(study) {

      // Create one table row for each study in the manifest
      var studyRow = '<tr><td>' +
                     study.patientName + '</td><td>' +
                     study.patientId + '</td><td>' +
                     study.studyDate + '</td><td>' +
                     study.modality + '</td><td>' +
                     study.studyDescription + '</td><td>' +
                     study.numImages + '</td><td>' +
                     '</tr>';

      // Append the row to the study list
      var studyRowElement = $(studyRow).appendTo('#studyListData');

      // On study list row click
      $(studyRowElement).click(function() {

        // Add new tab for this study and switch to it
        var studyTab = '<li><a href="#x' + study.patientId + '" data-toggle="tab">' + study.patientName + '</a></li>';
        $('#tabs').append(studyTab);

        // Add tab content by making a copy of the studyViewerTemplate element
        var studyViewerCopy = studyViewerTemplate.clone();

        /*var viewportCopy = viewportTemplate.clone();
           studyViewerCopy.find('.imageViewer').append(viewportCopy);*/


        studyViewerCopy.attr("id", 'x' + study.patientId);
        // Make the viewer visible
        studyViewerCopy.removeClass('hidden');
        // Add section to the tab content
        studyViewerCopy.appendTo('#tabContent');

        // Show the new tab (which will be the last one since it was just added
        $('#tabs a:last').tab('show');

        // Toggle window resize (?)
        $('a[data-toggle="tab"]').on('shown.bs.tab', function(e) {
          $(window).trigger('resize');
        });

        // Now load the study.json
        // Get the JSON data for the selected studyId
        $.getJSON('/rest/pool/' + poolKey + '/viewer/study/' + study.studyId + "/series", function(data) {
          loadStudy(studyViewerCopy, viewportTemplate, data);
        });
      });

      // Check if we are to load this study
      if (getParameterByName('studyKey') == study.studyId) {
        $(studyRowElement).trigger('click');
      }
    });
  });
}


if ( hash ) {
  // Get study list from JSON manifest
  $.getJSON('/rest/viewer/' + hash + '/series', function(data) {
    console.log ( "Getting by hash", data)
    var study = data;

    // Hide the StudyList tab
    $('#studyListTab').hide();
    
    // Add new tab for this study and switch to it
    var studyTab = '<li><a href="#x' + study.patientId + '" data-toggle="tab">' + study.patientName + '</a></li>';
    $('#tabs').append(studyTab);

    // Add tab content by making a copy of the studyViewerTemplate element
    var studyViewerCopy = studyViewerTemplate.clone();

    /*var viewportCopy = viewportTemplate.clone();
       studyViewerCopy.find('.imageViewer').append(viewportCopy);*/

    studyViewerCopy.attr("id", 'x' + study.patientId);
    // Make the viewer visible
    studyViewerCopy.removeClass('hidden');
    // Add section to the tab content
    studyViewerCopy.appendTo('#tabContent');

    // Show the new tab (which will be the last one since it was just added
    $('#tabs a:last').tab('show');

    // Toggle window resize (?)
    $('a[data-toggle="tab"]').on('shown.bs.tab', function(e) {
      $(window).trigger('resize');
    });

    // Now load the study.json
    // Get the JSON data for the selected studyId
    loadStudy(studyViewerCopy, viewportTemplate, data);
  })
   .fail(function( jqxhr, textStatus, error ) {
     var err = textStatus + ", " + error;
     console.log( "Request Failed: " + err, jqxhr );
     alert ( "Failed to load study: " + jqxhr.responseText );
  });
}

// Show tabs on click
$('#tabs a').click (function(e) {
  e.preventDefault();
  $(this).tab('show');
});

// Resize main
function resizeMain() {
  var height = $(window).height();
  $('#main').height(height - 50);
  $('#tabContent').height(height - 50 - 42);
}


// Call resize main on window resize
$(window).resize(function() {
  resizeMain();
});
resizeMain();


// Prevent scrolling on iOS
document.body.addEventListener('touchmove', function(e) {
  e.preventDefault();
});
