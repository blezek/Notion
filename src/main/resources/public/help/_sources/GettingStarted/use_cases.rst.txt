.. include:: /global.rst

.. _UseCases:

Use Cases
=========

.. _MultiResearcherUseCase:

Multi-Researcher / Multi-Pool
-----------------------------

Multiple researchers desire to collect anonymized images and manage access to the images via DICOM queries.  Notion supports this use case through multiple Pools, each with custom anonymization rules and configurations.  A central pool (perhaps named :tt:`public`) would be available for modalities and/or image archives to send images containing PHI.  Each researcher could "claim" images by moving them to their Pool and deleting the images from the :tt:`public` Pool when completed.  With further refinement, this process could be automated based on lookup values for each Pool.

.. _IRBPoolUseCase:

IRB Pool
--------

A researcher has several Institutional Review Board approved projects.  Data for each project should be maintained separately, with limited access to the data.

Notion supports this use case through the use of individual Pools created for each project.  Image data is not shared between Pools and :ref:`access can be restricted <users-and-groups>`.

.. _MiniPACSUseCase:

Research PACS
-------------

A radiology department desires to have a long term archive of anonymized DICOM images for teaching and training.

Notion supports this use case by providing Pools with anonymization capabilities.  Moreover, by taking care to define Devices for each Pool, access to the data can be restricted.  DICOM tools such as OsiriX_ (for Mac OSX) and ClearCanvas_ (for Windows) can be used to query Notion and retrive images as needed

.. _OsiriX: http://www.osirix-viewer.com/
.. _ClearCanvas: http://www.clearcanvas.ca/
