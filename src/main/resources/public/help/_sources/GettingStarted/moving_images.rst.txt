.. include:: /global.rst

.. _UsingConnectors:

Using Connectors
================

Glad you have made it this far in the tutorials!  By now, you have learned :ref:`how to create Pools and Devices<CreateAPool>`, :ref:`send images via the command line <SendImagesToAPool>` and :ref:`construct a simple anonymizer <AnonymizationIntro>`.  Here we are going to demonstrate a :ref:`simple use case <MultiResearcherUseCase>` using a :ref:`Connector`.  This scenario is useful for multiple researchers to share a common Connection to a PACS, allowing each to create custom anonymization rules and keep images isolated from other Pools.

Configure a Connector
^^^^^^^^^^^^^^^^^^^^^

A :ref:`Connector <Connector>` defines a three party connection.  The role of each party is:


* ``PACS`` An institutional PACS holding clinicial data.  Must be configured to allow queries from the Query Pool, and send to the Destination Pool.
* ``Query Pool`` A Notion pool with permission to query the PACS.
* ``Receiving Pool`` A Notion pool configured as a PACS destination.

Using a Connector involves several steps.

#. Enter a Query (can be an Excel spreadsheet, or an individual MRN (Medical Record Number))
#. ``Query Pool`` sends a C-FIND request to ``PACS``
#. Query results are displayed and Studies to be retrieved are chosen
#. ``Receiving Pool`` issues a C-MOVE to ``PACS``
#. ``Receiving Pool`` retrieves the images from ``PACS``
#. ``Receiving Pool`` moves images into the local Pool for storage and anonymization.
#. ``Receiving Pool`` deletes received images.

Connectors are a complicated topic involving many moving parts, but from a study coordinator's perspective, they are simple.

To add a Connector, click on the ``Connectors`` link on the menu bar and then the `plus` icon next to Connectors:


.. figure:: /images/create_connector.png
	:align: center
	:width: 512

Click Save to save the Connector and enable it for use.  A Connector is accessible from any Pool.

**NB:** The Device used in a connector **must not** use a regular expression.  The query and retieve require a correct Application Entity Title, full hostname and valid port.  See :ref:`DICOM` for details.

Performing a Query
^^^^^^^^^^^^^^^^^^

Clicking on `Pools` in the menu bar returns to the Pools view.  Beside each Pool listed on the left side is a `query` link.  Two modes of query are available.  Both require the appropriate connector to be selected.

An individual query takes a MRN (Medical Record Number), typically a PaitentID and queries using the Connector.  The interface is:

.. figure:: /images/individual_query.png
	:align: center
	:width: 512

Multiple MRN's may be specified using the spreadsheet query.  A template spreadsheet is available through the `Excel template` link.  The only required entry in each row is ``PatientID``, the other columns can be used to limit the query by Modality, Date, AccessionNumber, etc.  Once the spreadsheet is filled out, it can be uploaded to Notion to start the query.

.. figure:: /images/spreadsheet_query.png
	:align: center
	:width: 512

When the DICOM query is completed, a list of possible Studies is returned.  Any checked Study will be retrieved and transferred to this pool.

.. figure:: /images/completed_query.png
	:align: center
	:width: 512


Next Steps
----------

This completes the Notion tutorial.  For further information checkout Notion's :ref:`concepts <Concepts>` and :ref:`use cases <UseCases>`.
