.. _Concepts:

Concepts
========

.. _Pools:
.. _Pool:

Pool
----

A Pool is a collection of DICOM images, anonymization rules and Devices to manage access.

.. _Devices:
.. _Device:

Device
------

A Device consists of a descriptive name, `Application Entity Title <https://www.dabsoft.ch/dicom/8/C.1/>`_ (which must be unique for the Pool, and is abbreviated AETitle), Hostname, Port and a freetext Description.  The AETitle is used to restrict access to images using DICOM protocols.  The AETitle and Hostname must match incoming DICOM connections to the Pool to allow access.  Both AETitle and Hostname are matched using Java's regular expressions.  For instance ``.*`` matches any entry.

.. _Anonymization:

Anonymization
-------------

Anonymization is handled in two stages.  Each incoming image is processed separately by the two stages.  First, an instance of the `CTP DICOM anonymizer <http://mircwiki.rsna.org/index.php?title=The_CTP_DICOM_Anonymizer>`_ processes images first according to its configuration file that is editable via the web app (see :ref:`AnonymizationIntro` for details).  The second stage is a series of custom `Javascript <http://en.wikipedia.org/wiki/JavaScript>`_ commands that are run on specific tags.  The Javascript has access to a custom map called ``tags`` that contains the original tags of the incoming image.  Javascript also has access to an object (``anonymization``) containing several useful methods for anonymization.  The methods are:

``debug(text) / info(text) / warn(text) / error(text)``
  Print a debug / info / warn / error message to the log

``hash(text,[length])``
  Return a string containing the first ``length`` digits of the MD5 hash of ``text`` (default length is all characters)

``setValue(Type,Key,Value)``
  Store a ``Value`` for later ``lookup``.  ``Value`` is indexed by ``Type`` and ``Key``.  The previous ``Value`` is overwritten by this method

``lookup(Type,Key)``
 Lookup and return a value indexed by ``Type`` and ``Key``.  If the value does not exist, ``lookup`` returns null

``sequenceNumber(Type,Key)``
   First lookup the sequence number indexed by ``Type`` and ``Value``.  If it does not exist, generate a unique number by incrementing the ``Type`` sequence.  For instance the first time ``anonymizer.sequenceNumber('PatientName', 'Jones')`` is called, the return value is the string ``'1'``.  On the second call, ``anonymizer.sequenceNumber('PatientName', 'Jones')`` also returns the string ``'1'``, however ``anonymizer.sequenceNumber('PatientName', 'Smith')`` returns the string ``'2'``.  Sequence numbers are used to generate ``PatientName`` and ``PatientID`` tags if particular identifiers are not required.  *NB:* see the :ref:`anonymizer reference <Anonymization>` for details on prepopulating the lookup tables.

``Exception(text)``
  Throw an exception, immediately stopping any further processing of this image.  Exceptions can be used to reject images that do not have proper lookup information.

The :ref:`anonymization tutorial <AnonymizationIntro>` contains more details.


.. _Connectors:
.. _Connector:

Connectors
----------

Notion supports the concept of Connectors, that is a specific Pool that is configured to query and receive DICOM images from a PACS system.  It is easier for PACS administrators to configure to send to a single Notion Pool, rather than configuring each individual Pool.  A Connector is a configuration involving several pools.

.. figure:: /images/create_connector.png
  :align: center
  :width: 512


The ``Query Pool`` will be used to query the ``Query Device`` (often a hospital PACS).  ``Receiving Pool`` will be used for moving the images into Notion.  The PACS will be given a ``C-MOVE`` request to move images to the ``Receiving Pool``.  Connectors can be used by any Pool in Notion, allowing researchers to query and retrieve images from remote PACS to their pool.  See :ref:`UsingConnectors` for more details.
