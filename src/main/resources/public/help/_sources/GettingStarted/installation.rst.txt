.. include:: ../global.rst

.. _Installation:

Installation
============

After downloading Notion and unzipping, look in the ``Notion-x.x.x`` folder (where ``x.x.x`` is the version number).  This folder contains the following items:

:tt:`Notion-x.x.x.jar`
    The Jar file with the Notion classes and runtime files bundled.

:tt:`lib`
    External Java libraries.  Must be maintained in the same directory as ``Notion-x.x.x.jar`` because the main jar file references the libraries by a relative path.

:tt:`Documentation`
    Contains this documentation

:tt:`notion.yml`
    A configuration file for Notion in `YAML <http://www.yaml.org/>`_ format.

The jar file and lib directory may be copied to any location as needed.

Running Notion
--------------

TL;DR
^^^^^

.. code-block:: bash

  java -jar Notion.jar server notion.yml

Point a browser at http://localhost:8080

.. _CLI:

Configuration
-------------

Notion is configured through a YAML file.  A starting configuration is included in the distribution.  The Notion-specific sections are described below.  Additional configuration options may be found at http://dropwizard.io/manual/configuration.html

.. literalinclude:: ../../notion.example.yml
  :linenos:

DB Web Interface
^^^^^^^^^^^^^^^^

The ``dbWeb`` option gives a port where Notion listens and shows a web interface to the embedded Derby database.  If omitted, Notion does not specify the DB interface.  More details are at :ref:`dbweb`.

Database
^^^^^^^^

Notion requires the Derby_ embedded database, removing any external database dependancies.  The directory where Derby stores the database is specified in the ``url`` setting of the ``database`` section.  The directory is specified after the ``directory:`` portion of the ``url``.  ``Username`` and ``password`` are optional settings.

Notion
^^^^^^

The Notion section specifies where notion listens for incoming DICOM communications (``dicomPort``) and where image files are saved on disk (``imageDirectory``).  It is common to store images in the same directory as the Derby database.

Logging
^^^^^^^

The logging level of the entire application (including non-Notion components such as Derby) can be controlled in the ``logging`` section of the configuration.  See http://dropwizard.io/manual/core.html#logging for extensive documentation of logging.

.. _Derby: http://db.apache.org/derby/

User Authentication and Authorization -- Shiro
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Notion uses Shiro (http://shiro.apache.org/) a pluggable authorization and authentication package.  Shiro enables Notion to authenticate against a diverse set of providers, including LDAP and Active Directory.  Configuration of Shiro is somewhat outside the scope of this documentation, but covered briefly in :ref:`shiro-config`.
