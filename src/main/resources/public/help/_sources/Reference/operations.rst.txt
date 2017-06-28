.. include:: ../global.rst


Operations
==========

.. _DICOMConfig:

DICOM Configuration
-------------------

Notion's DICOM listener supports ``C-ECHO``, ``C-FIND``, ``C-STORE`` and ``C-MOVE``.  The listening port is specified in the ``notion.yml`` configuration file and is 11117 by default.  All other DICOM configuration is by Pools and Devices.

.. _shiro-config:

Authentication and Authorization Configuration
----------------------------------------------


.. _users-and-groups:

Users and Groups
----------------

Notion supports multiple user accounts.  The first user created has Administrative rights and can grant those rights to other users.  User rights can be edited on the `Users` tab in the menu bar.  Administrators can see all Pools, configure Connectors, edit Groups and change users permissions.

Groups
^^^^^^

A Group is a collection of users.  Groups can be granted PoolAdmin and Coordinator rights to a Pool.

PoolAdmin
  A PoolAdmin is allowed to change access to the pool, edit details, add Devices and change Anonymization settiongs.  PoolAdmins rights include all rights granted to Coordinators.

Coordinators
  A Coordinator has limited access to a Pool.  The Coordinator role is designed to let users query images from a Connector and download the resulting images.

.. _dbweb:

DBWeb Interface
---------------
