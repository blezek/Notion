.. include:: ../global.rst


Metrics and Monitoring
======================

Notion has the following Metrics_.  Metrics are reported at the REST endpoint ``/rest/metrics``.

.. _Metrics: https://dropwizard.github.io/metrics/3.1.0/
.. _Gauges: https://dropwizard.github.io/metrics/3.1.0/manual/core/#gauges
.. _Counters: https://dropwizard.github.io/metrics/3.1.0/manual/core/#counters
.. _Meters: https://dropwizard.github.io/metrics/3.1.0/manual/core/#meters
.. _Timers: https://dropwizard.github.io/metrics/3.1.0/manual/core/#timers

DICOM
-----

Metrics recorded for DICOM.


==================================== =======   =====================
Name                                 Type      Description
------------------------------------ -------   ---------------------
DICOM.image.write                    Timer     Write images/second
DICOM.image.received                 Meter     Receive images/second
DICOM.image.sent                     Meter     Sent images/second
DICOM.image.send.queue               Counter   Pending images to send
DICOM.associations.active            Gauge     # of active associations
DICOM.associations.total             Counter   # of total associations
==================================== =======   =====================


Query / Fetch
-------------

==================================== =======   =====================
Name                                 Type      Description
------------------------------------ -------   ---------------------
Query.timer                          Timer     Query time
Query.counter                        Counter   # of Queries in progress
Fetch.timer                          Timer     Fetch time images/second
Fetch.counter                        Counter   # of Fetches in progress
Fetch.pending                        Counter   # of pending Fetches (Studies)
==================================== =======   =====================



Gauges
------

Gauges_ measure a single value at an instant in time.

====================================      =====================
Name                                      Description
------------------------------------      ---------------------
Pool.XX.studies                           Number of Studies in the Pool
Pool.XX.series                            Number of Series in the Pool
Pool.XX.instance                          Number of Instances in the pool
DB.table.instance                         Total number of Instances
DB.table.instance                         Total number of Instances
DB.table.instance                         Total number of Instances

DICOM.associations.active                 Number of active Associations
====================================      =====================


Counters
--------

Counters_ are a simple incrementing and decrementing 64-bit integer.

====================================      =====================
Name                                      Description
------------------------------------      ---------------------
DICOMReceiver.association.total           Number of total associations since start.
Pool.move.count                           Total number of images moved, should increment and decrement.
Pool.XX.move.count                        Number of images waiting to be moved into Pool XX
====================================      =====================

Meters
------

Meters_ measure the rate at which events happen.  Meters capture 1-, 5-, and 15-minute rate, and an exponentially decaying mean rate.

====================================      =====================
Name                                      Description
------------------------------------      ---------------------
DICOMReceiver.image.received              Overall images received per second.
Pool.XX.process.meter                     Rate of processing incoming image by Pool XX
====================================      =====================


Timers
------

Timers_ collect a histogram of the duration and a meter for an event.

====================================      =====================
Name                                      Description
------------------------------------      ---------------------
DICOMReceiver.image.write                 Time required to write an image
Pool.move.timer                           Time required to move an image into a Pool
Pool.process.timer                        Time required to process images into a Pool
Pool.XX.process.timer                     Processing time for Pool XX
Pool.XX.move.timer                        Move time for Pool XX
Query.query.timer                         Query time
Query.fetch.timer                         Fetch time
====================================      =====================
