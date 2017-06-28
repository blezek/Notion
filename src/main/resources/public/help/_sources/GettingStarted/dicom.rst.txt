.. include:: /global.rst


.. _DICOM:

DICOM Configuration
===================

For those new to DICOM, the `Wikipedia DICOM Article <http://en.wikipedia.org/wiki/DICOM>`_ is especially helpful in understanding the different concepts discussed.


Notion operates multiple Application Entities as Service Class Providers.  An Application Entity eithor provides a service or uses a service (and sometimes both).  Each Pool in Notion is configured as an Application Entity providing Store and Query/Retrieve.  External DICOM Application Entities can send images to Pools by using the Pool's Application Entity Title, hostname and port.  The Query service enables other Application Entities to issue C-FIND requests to a Pool and retrieve a list of studies in the Pool.  The Retrive service of a Notion Pool sends images outbound to a properly specifed remote Application Entity (a Device).

Device Setup
------------

Access to a Pool's services (Store, Query and Retrieve) are controlled by the list of Devices associated with a Pool.  Each operation on a Pool requires a matching Device to be found before the operation is allowed.  A Device consists of 4 elements:

``Application Entity Title``
  The 16 character name of the Application Entity

``Hostname``
  The IP address (numeric or text) of the Application Entity

``Port``
  Port where the Application Entity is listening.

``IsAutoforward``
  When true, forward this Pool's incoming messages to this destination.

``Description``
  An optional description of the Device.

.. image:: /images/new_device.png
  :align: center
  :width: 300px

To find a matching Device, Notion examines the incoming request, trying to find a match in the list of Devices.  Matches are found using `Java's regular expression rules <http://www.vogella.com/tutorials/JavaRegularExpressions/article.html>`_.  For instance, the incoming Application Entity ``Incoming`` will match ``Incoming``, ``Incom.*`` and ``.*``.  The Application Entity Title and Hostname are matched according to the regular expression rules.  Port is not matched.

If a matching Device is not found, the incoming operation is rejected.  This is the only available DICOM mechanism for authorization, and does not allow very fine grain access controls.

Store
-----

The Store service is invoked when a remote Application Entity attempts to send images to a Notion Pool.  The receiving Pool must be specified as the ``Called Application Entity Title`` and must match (exact and case-sensitive).  Notion matches the ``Calling Application Entity Title`` and remote Hostname against the list of Devices defined in the Pool according to the rules above.  If the Device is defined to Notion, the images are accepted and anonymized if the Pool is so configured.

Query
-----

The Query service is invoked when a remote Application Entity attempts to query a Notion Pool.    The queried Pool must be specified as the ``Called Application Entity Title`` and must match (exact and case-sensitive).  Notion matches the ``Calling Application Entity Title`` and remote Hostname against the list of Devices defined in the Pool according to the rules above.  If the Device is matched, the DICOM query is completed.  Notion supports Exam and Series queries.


Retrieve
--------

The Query service is invoked when a remote Application Entity requests Notion to send images.  In this case two matches are required, the Application Entity requesting the retrive and the destination Application Entity.  The destination Application Entity's Title, Hostname and Port are used to initiate the DICOM send from Notion.  Thus the destination Application Entity is generally not a regular expression.

Example
-------

Suppose a Pool (Application Entity Title of ``femur``) has the following Devices defined (Application Entity Title abbreviated by AET):

=======    ==================    ====    ====================================================
Title      Hostname              Port    Description
-------    ------------------    ----    ----------------------------------------------------
.*         head.hospital.edu     0       Any AET from ``head.hospital.edu``
radius     arm.hospital.edu      1234    The ``radius`` AET on ``arm.hospital.edu``
cranium    .*.hospital.edu       0       ``cranium`` AET from any host in the hospital domain
=======    ==================    ====    ====================================================

Query & Store
^^^^^^^^^^^^^

Based on the first Device, any AET coming from ``head.hospital.edu`` can Store and Query the ``femur`` Pool, as can ``radius`` from ``arm.hospital.edu``.  The ``cranium`` AET can be matched against any hostname in the ``hospital.edu`` domain, thus any host can query ``femur``, if the use an AET of ``cranium``.

Retrive
^^^^^^^

The only valid retrieve destination Device is ``radius`` listening at port ``1234`` on ``arm.hospital.edu``.  The other two Devices do not have valid ports, and the regular expression in the ``cranium`` Device makes it impossible to send images.

Match Examples
^^^^^^^^^^^^^^

Each Application Entity is represented in the form ``AET@Hostname:Port`` with matches based on the above table shown.

================================ =====  =====  =======  ===================================================
Application Entity               Store  Query  Retrive  Remarks
-------------------------------- -----  -----  -------  ---------------------------------------------------
sternum\@chest.hospital.edu:104  no     no     no       Partial match ``.*@head.hospital.edu`` (not Hostname)
mandible\@head.hospital.edu:104  yes    yes    no       Matches ``.*@head.hospital.edu``
cranium\@leg.hospital.edu:1234   yes    yes    no       Matches ``cranium@.*.hospital.edu``
radius\@arm.hospital.edu         yes    yes    yes      Exact match ``radius@arm.hospital.edu:1234``
================================ =====  =====  =======  ===================================================
