
CREATE TABLE VIEWERHASH (
  ViewerHashKey INT NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY , 
  StudyKey INT NOT NULL,
  PoolKey INT NOT NULL,
  Hash VARCHAR(2048),
  Expires TIMESTAMP 
);

CREATE INDEX ViewerHash_UNIQUE on VIEWERHASH (Hash);
CREATE INDEX ViewerHash_Expires on VIEWERHASH (Expires);