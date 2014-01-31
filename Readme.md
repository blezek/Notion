
Notion PACS
===========

Notion is a stand-alone [PACS](http://en.wikipedia.org/wiki/Picture_archiving_and_communication_system) designed to be used by radiology researchers for storage and anonymization of research images.  

### Why use Notion?

If you have a need to:
- store DICOM images, but do not want/have a dedicated research PACS
- anonymize DICOM images
- map Names, IDs and Accession numbers during anonymization
- maintain separation of image data across projects
- scale to 100's of independant research projects

### Why *not* use Notion?

If you:
- already have a research PACS
- do not need to anonymize data
- do not care about isolation between research projects
- are happy using manual anonymization tools

There are other Open Source / free PACS systems available including 
- [Conquest](http://ingenium.home.xs4all.nl/dicom.html)
- [orthanc](http://code.google.com/p/orthanc/)
- [OsiriX](http://www.osirix-viewer.com/)
- [DCM4CHE](http://www.dcm4che.org/) (Notion is based on dcm4che)
- [ClearCanvas](http://www.clearcanvas.ca/)

Depending on needs, one of the other systems may be a better fit.

#### Installation

[Download and unzip the Notion-1.0.0.zip package.](http://www.nitrc.org/frs/download.php/6233/Notion-1.0.0.zip)  Inside you'll find several interesting files, including ```Notion.jar``` and the [documentation](Documentation/html).  Installation is complete at this point.

Notion is released through [NITRIC](http://www.nitric.org), future and older releases can be found on the [Notion downloads page](http://www.nitrc.org/frs/?group_id=793).

#### Getting started

###### TL;DR version
```bash
java -jar Notion.jar
```
Point a browser at [http://localhost:11118](http://localhost:11118).

###### [TL;DR video](http://youtu.be/w1gzPN3aaDk)
[![Notion ResearchPACS Overview and Tutorial](http://img.youtube.com/vi/w1gzPN3aaDk/0.jpg)](http://youtu.be/w1gzPN3aaDk)

###### Command line options
Notion supports setting several command line parameters:
```bash
# java -jar Notion-1.0.0.jar  --help
usage: Notion [options] [directory]
options:
 -d,--db <arg>     Start the embedded DB Web server on the given port
                   (normally 8082), will not start without this option
 -h,--help         Print help and exit
 -m,--memoryDB     Start services in memory (DB only)
 -p,--port <arg>   Port to listen for DICOM traffic, default is 11117
 -r,--rest <arg>   Port to listen for REST traffic, default is 11118

Start the Notion PACS system using [directory] for data storage.  If not
specified defaults to the current working directory
(/Users/blezek/Source/ResearchPACS).  By default the REST api is started
on port 11118, with the web app being served at http://localhost:11118
The DICOM listener starts on port 11117 (can be changed with a --port) and
provides C-ECHO, C-MOVE, C-STORE and C-FIND services.  Notion serves as a
full DICOM query / retrive SCP.
Database administration can be handled via the bundled web interface.  By
default, http://localhost:8082, if the  --db option is given.  It will not
start up otherwise. The JDBC connection URL is given in the log message of
the server.
```

Of particular interest is the `--db` argument which specifies a port for the server to listen on for web access to the embedded database.  Performance tuning, db maintance, etc can be performed through the web interface ([http://localhost:8082](http://localhost:8082) by default).


#### Development

Development of Notion requires several tools.

##### Eclipse
The primary development environment is [Eclipse](http://www.eclipse.org/) with the [IvyDE](http://ant.apache.org/ivy/ivyde/) plugin to manage dependancies.

##### Ember.js
[Ember.js](http://emberjs.com/) is a client side MVC framework for single page Javascript apps.  Notion's web app is constructed using Ember.js and several other UI interface tools, including [Bootstrap](http://getbootstrap.com/).

##### Brunch.io
[Brunch.io](http://brunch.io/) is a *"is an ultra-fast HTML5 build tool"* and is used in Notion to construct the web app.  Javascript code is automatically minified and concatenated, and style sheets are processed to conserve space.  Brunch.io is only one of many tools based on [node.js](http://nodejs.org/).
