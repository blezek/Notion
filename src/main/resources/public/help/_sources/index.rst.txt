.. Notion documentation master file, created by
   sphinx-quickstart on Tue Nov 12 11:25:06 2013.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

.. include:: global.rst

Notion: a 'PACS' for researchers
================================

This project exists to create a low-overhead DICOM storage system for researchers in radiology.  A full PACS system includes EMR integration, reading workstations, worklists, etc. and is outside the scope of Notion.  However, Notion provides several useful facilities for a department including multiple, independant "pools" of images, restrictions on access to images stored in pools, anonymization, simple installation, and web interface for administration.


Why use Notion?
---------------

Need to:

- store DICOM images, but do not want/have a dedicated research PACS?
- anonymize DICOM images?
- map Names, IDs and Accession numbers during anonymization?
- maintain separation of image data across projects?
- scale to 100's of independent research projects?

Why *not* use Notion?
---------------------

If you:

- already have a research PACS
- do not need to anonymize data
- do not care about isolation between research projects
- are happy using manual anonymization tools

There are other Open Source / free PACS systems available including

- Conquest_
- orthanc_
- OsiriX_
- DCM4CHE_ (Notion is based on dcm4che)
- ClearCanvas_

.. _Conquest: http://ingenium.home.xs4all.nl/dicom.html
.. _orthanc: http://code.google.com/p/orthanc/
.. _OsiriX: http://www.osirix-viewer.com/
.. _DCM4CHE: http://www.dcm4che.org/
.. _ClearCanvas: http://www.clearcanvas.ca/

Depending on needs, one of the other systems may be a better fit.

Overview
========

Main documentation for Notion is in several different sections:

* :ref:`site-docs`
* :ref:`feature-docs`
* :ref:`concept-docs`

Information about development and administration of your own instance is also available:

* :ref:`dev-docs`
* :ref:`ops-docs`


.. _site-docs:

User Documentation
------------------
.. toctree::
  :maxdepth: 2

  GettingStarted/installation
  GettingStarted/getting_started
  GettingStarted/dicom
  GettingStarted/anonymization
  GettingStarted/moving_images

.. _feature-docs:

Features
--------
.. toctree::
  :maxdepth: 2

  GettingStarted/use_cases

.. _concept-docs:

Concepts
--------
.. toctree::
  :maxdepth: 2

  Reference/reference


.. _dev-docs:

Developer Documentation
-----------------------
.. toctree::
  :maxdepth: 2

  Development/development
  Development/metrics

.. _ops-docs:

Operations Documentation
------------------------
.. toctree::
  :maxdepth: 2

  Reference/operations

.. Reference/kitchen_sink
