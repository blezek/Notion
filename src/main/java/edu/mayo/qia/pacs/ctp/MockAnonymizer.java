package edu.mayo.qia.pacs.ctp;

import org.dcm4che2.data.DicomObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;

public class MockAnonymizer extends Anonymizer {

  @Override
  public ScriptableObject setBindings(ScriptableObject scope, DicomObject obj) {
    ScriptableObject.putProperty(scope, "pool", pool);
    ScriptableObject.putProperty(scope, "anon", this);
    ScriptableObject.putProperty(scope, "anonymizer", this);
    NativeObject tagObject = new NativeObject();

    for (String tagName : fieldMap.values()) {
      tagObject.defineProperty(tagName, "#" + tagName + "#", ScriptableObject.READONLY);
    }
    ScriptableObject.putProperty(scope, "tags", tagObject);
    return scope;
  }

  @Override
  public String lookup(String type, String name) {
    return "LOOKUP";
  }

  @Override
  public Integer setValue(String type, String name, String value) {
    return 1;
  }

  @Override
  String getSequence(String type) {
    return "mockSequence";
  }

  @Override
  public int sequenceNumber(String type, String name) {
    return 42;
  }

}
