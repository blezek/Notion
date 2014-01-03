.. include:: /global.rst


Tutorial 2
==========

.. _AnonymizationIntro:

Anonymization
-------------

To turn on Anonymization, click the :tt:`Edit` icon next to the Pool info on this page http://localhost:11118/index.html#/pools/pool/1.  In the dialog box, check :tt:`Use Anonymizer`, click :tt:`Save changes` and :tt:`close`.  Now our Pool display looks somewhat different with two new sections: :tt:`CTP Configuration` and :tt:`Anonymization Rules`.  Opening up the :tt:`CTP Configuration` reveals an editor for the CTP configuration used by the pool.  The particular file is for the `CTP DICOM anonymizer <http://mircwiki.rsna.org/index.php?title=The_CTP_DICOM_Anonymizer>`_ configuration stored on the Notion server.  Editing and saving the file will change how anonymization is handled on the Notion server.

.. figure:: /images/ctp_configuration.png
   :align: center
   :width: 768

   Configuration of the `CTP DICOM anonymizer <http://mircwiki.rsna.org/index.php?title=The_CTP_DICOM_Anonymizer>`_.  Changes will be saved to the server and used for anonymization of incoming DICOM images.

CTP is the first stage of the anonymization process in Notion.  The second stage is a series of `Javascript <http://en.wikipedia.org/wiki/JavaScript>`_ snippits that are used to substitute DICOM tags.  To demonstrate, we will add a new anonymization script by clicking the :tt:`plus` sign next to the :tt:`Anonymization Rules`.  This brings up a dialog to create the new rule.

.. figure:: /images/new_rule.png
   :align: center

   Create a new rule to replace the DICOM tag :tt:`PatientName` by entering

   :Tag: PatientName
   :Stcript: tags.PatientName

   By default, the context in which the Javascript snippit executes has a map called :tt:`tags`.  The :tt:`tags` hash map contains the original tag values for the current image (that is, before CTP Anonymization as CTP removes/changes many tags).  Save the script and look in the editor:

.. figure:: /images/editing_anonymization_rule.png
   :align: center

   Editor for the :tt:`PatientId` anonymization rule.  Will replace :tt:`PatientName` with the original value.

The editor has some nifty features, including saving the rule and trial execution of the rule.  Pressing :tt:`'Ctrl-S'` on Windows or :tt:`'Command-S'` on the Mac will save the rule, while pressing :tt:`'Ctrl-Return'` will execute the rule's Javascript on the server with dummy values for the :tt:`tags` hash.  To try it out, run the script.  The output should be :tt:`#PatientName#`.  Some of the other options of the anonymization rules include lookups and sequence numbers.  Now, change the rule to be:

.. code-block:: javascript

   tags.PatientName + '-' + anonymizer.sequenceNumber('PatientName', tags.PatientName)

And give the script a trial run.  The result should be :tt:`#PatientName#-42`.  This is because ``tags.PatientName`` returns :tt:`#PatientName#` as a string and ``anonymizer.sequenceNumber('PatientName', tags.PatientName)`` always returns the string :tt:`'42'`.  They are concatenated using ``+ '-' +`` to form the output string.  The ``anonymizer`` object is available in the Javascript and contains many useful functions.  Apart from the trial execution, ``anonymizer.sequenceNumber( Type, Key )`` first attempts to retrieve a previously stored sequence number indexed by ``Type`` ( :tt:`PatientName` in our case) and ``Key`` (:tt:`tags.PatientName`).  If found, the previous number is returned, otherwise a new sequence number is generated, stored for later use and returned.  Multiple sequences can be independantly maintained by the ``Type`` argument, or a single sequence can be used.

Enter this code in the :tt:`PatientName` rule:

.. code-block:: javascript

   anonymizer.hash ( tags.PatientName )

The result of the trial execution is ``3382174773882``.  This is the `MD5 hash <http://en.wikipedia.org/wiki/MD5>`_ of the input string.  The function ``anonymizer.hash ( Key, [length] )`` returns the first ``length`` digits of the MD5 hash value of ``Key``.  If we change our function to be:

.. code-block:: javascript

   anonymizer.hash ( tags.PatientName, 3 )

Trial execution gives ``338`` the first 3 digits of the full hash value from the previous script.

The ``anonymizer`` object has several other functions

``debug(text) / info(text) / warn(text) / error(text)``
  Print a debug / info / warn / error message to the log

``hash(text,[length])``
  Return a string containing the first ``length`` digits of the MD5 hash of ``text`` (default length is all characters)

``setValue(Type,Key,Value)``
  Store a ``Value`` for later ``lookup``.  ``Value`` is indexed by ``Type`` and ``Key``.  The previous ``Value`` is overwritten by this method

``lookup(Type,Key)``
 Lookup and return a value indexed by ``Type`` and ``Key``.  If the value does not exist, ``lookup`` returns null

``sequenceNumber(Type,Key)``
   First lookup the sequence number indexed by ``Type`` and ``Value``.  If it does not exist, generate a unique number by incrementing the ``Type`` sequence.  For instance the first time ``anonymizer.sequenceNumber('PatientName', 'Jones')`` is called, the return value is the string ``'1'``.  On the second call, ``anonymizer.sequenceNumber('PatientName', 'Jones')`` also returns the string ``'1'``, however ``anonymizer.sequenceNumber('PatientName', 'Smith')`` returns the string ``'2'``.  Sequence numbers are used to generate ``PatientName`` and ``PatientID`` tags if particular identifiers are not required.  *NB:* see the :ref:`anonymizer reference <Anonymizer>` for details on prepopulating the lookup tables.

``Exception(text)``
  Throw an exception, immediately stopping any further processing of this image.  Exceptions can be used to reject images that do not have proper lookup information.  See :ref:`PatientNameAnonymizer` for an example.

Coming back to our example, set the anonymization rule for ``PatientName`` to be:

.. code-block:: javascript

   // Generate a sequence number for PatientName
   var sequenceNumberString = anonymizer.sequenceNumber ( 'PatientName', tags.PatientName )
   // Convert to an Integer
   var sequenceNumber = new java.lang.Integer ( sequenceNumberString )
   // Zero pad
   var padded = java.lang.String.format ( "%05d", sequenceNumber )
   // Final output is Study-#####
   'Study-' + sequenceNumber

This will set the PatientName to be a concatination of the string ``'Study-'`` and the zero padded string stored in ``padded``.  Functions can also be used in anonymization rules:

.. code-block:: javascript

   function pad (s, length) {
     var n = new java.lang.Integer ( s )
     // Zero pad
     return java.lang.String.format ( "%0" + length + "d", n )
   }
   // Generate a sequence number for PatientName
   var sequenceNumber = pad ( anonymizer.sequenceNumber ( 'PatientName', tags.PatientName ), 5 )
   
   // Final output is Study-#####
   'Study-' + sequenceNumber

Remember, the tag replacement value is the last statement of the Javascript function expressed as a string.

Anonymize Images
----------------

The moment we've all been waiting for (yea right).  With our anonymizer in place, send the same data through Notion into the ``test`` Pool.

.. code-block:: plain

   >> java -classpath lib:"lib/*" org.dcm4che2.tool.dcmsnd.DcmSnd test@localhost:11117 /path/to/DICOM/data/
   Scanning files to send
	....................................................................................................................................................................
	Scanned 164 files in 0.214s (=1ms/file)
	INFO - Association(1) initiated Socket[addr=localhost/127.0.0.1,port=11117,localport=60448]
	INFO - test(1): A-ASSOCIATE-RQ test << DCMSND
	INFO - test(1): A-ASSOCIATE-AC DCMSND >> test
	Connected to test@localhost:11117 in 0.069s
	....................................................................................................................................................................
	Sent 164 objects (=3.0736637MB) in 5.895s (=533.91547KB/s)
	INFO - test(1) << A-RELEASE-RQ
	INFO - test(1) >> A-RELEASE-RP
	Released connection to test@localhost:11117
	INFO - test(1): close Socket[addr=localhost/127.0.0.1,port=11117,localport=60448]

`Viewing the studies <http://localhost:11118/index.html#/pools/studies/1>`_ for the ``test`` Pool gives:

.. figure:: /images/anonymized_images.png
	:align: center
	:width: 768

	Anonymized and non-anonymized images.  :tt:`MRA-0068` is the original patient study, and :tt:`Study-00001` is the anonymized version.

If the same images were sent again, they would receive the same PatientID, AccessionNumber and Study Description.

Next Steps
----------

The :ref:`next tutorial <MovingTutorial>` is moving images between pools.

