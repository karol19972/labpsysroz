Migrator Utility

Feature
-------
The migrator utility is to migrate data entries from a specific source 
to a sink. There are 2 ways to run migrator utility: 
    
  o Run migrator with command line arguments
    A handy way to run migrator with most essential configuration, use
    configuration file for advanced configuration.
    See below "Migrate with command line arguments".
        
  o Run migrator with configuration file 
    Run migrator based on configuration file, the configuration file is
    in JSON format, see below "Migrate based on configuration file" and 
    "Migrate Configuration file" for details.

Currently, there 2 kinds of data sources and 1 sink supported by the migrator

* Data sources:
 +--------------------|-------------------|-----------------------------+
 |                    | Source type name  | Node name in config file    |
 |--------------------|-------------------|-----------------------------|
 | MongoDB JSON       | mongodb-json      | "mongodbJsonSource"         |
 | JSON               | json              | "jsonSource"                |
 +--------------------|-------------------|-----------------------------+
 o MongoDB JSON : MongoDB JSON entries in strict representation mode   
 o JSON: General JSON entries.

* Data sinks:
 +--------------------|-------------------|-----------------------------+
 |                    | Sink type name    | Node name in config file    |
 |--------------------|-------------------|-----------------------------|
 | Oracle NoSQL Store | nosqldb           | "nosqldbSink"               |
 +--------------------|-------------------|-----------------------------+


Display supported sources and sinks
-----------------------------------
Usage:
java -cp <path-to-migrator.jar> -sources | -sinks


Migrate with command line arguments
-----------------------------------
It is a handy way to run migration with essential configuration but may not 
support some advanced configuration. For more advanced configuration, see below
section "Migrate based on configuration file".

1.  Migrate from mongodb-json to nosqldb
Usage: 
java -jar KVHOME/lib/migrator.jar
    -source mongodb-json -sink nosqldb
	-source-files <file-or-dir>[,<file-or-dir>]*
	[-datetime-to-long] 
	-helper-hosts <host:port[,host:port]*>
	-store <storeName>
	[-overwrite]
	[-username <user>] [-security <security-file-path>]
	[-abort-on-error]
	[-error <error-dir>]
	[-checkpoint <checkpoint-dir>]
	[-verbose]

Arguments:
    -source mongodb-json
        the data source type, this is required argument.
    -sink nosqldb
        the data sink type, this is required argument.
    -source-files <file-or-dir>[,<file-or-dir>]*
        the directores that contains source JSON files or the JSON files to
        load. Use comma "," as separator for multiple directories or files.
        A file may contain multiple JSON entries or a array of JSON entries, 
        each JSON entry can be single-line or multiple-lines (pretty mode).
    -datetime-to-long 
        use long to represent date time type value. If not specified, 
        date time value will be represented as ISO-8601 string.
    -helper-hosts <host:port[,host:port]*>
        the helper hosts of Oracle NoSQL store, this is required argument.
    -store <storename>
        the store name, this is required argument
    -overwrite
        a flag to overwrite the existing record when put a record associated 
        a key that already present in the store. Otherwise the record will 
        be rejected and logged as key existing record.
    -username <user>
        the user name used to connect to secured store.
    -security <security-file-path>
        the security file of the specified user.
    -abort-on-error
        if the migration process will stop if any failure during migration. 
        If not specified, the migration process will continue and logged the 
        failures to error file in <error-dir> if specified with -error or to 
        console output.
    -error <error-dir>
        the directory for error output files
    -checkpoint <checkpoint-dir>
        the directory for checkpoint files
    -verbose
        use this flag to get verbose output
        
Notes:
During migration, target table will be automatically created if it is not existed in the 
nosqldb store, the default table schema is (id STRING, document JSON, primary key(id)):
	- "id STRING" is the primary key which stores the string format of "_id" value in 
	  MongoDB JSON
	- "document JSON" is the json field which stores the rest of other non-primary-key 
	  fields.
      
2.  Migrate from json to nosqldb:
Usage: 
java -jar KVHOME/lib/migrator.jar
	-source json -sink nosqldb
	-source-files <file-or-dir>[,<file-or-dir>]*
	-helper-hosts <host:port[,host:port]*>
	-store <storeName>
	[-overwrite]
	[-username <user>] [-security <security-file-path>]
	[-abort-on-error]
	[-error <error-dir>]
	[-checkpoint <checkpoint-dir>]
	[-verbose]

Arguments:
    -source json
        the data source type, this is required argument.     
    The other arguments are same as above from mongodb-json to nosqldb, 
    please refer to the above. 

Notes:
During migration, target table will be automatically created if it is not existed in the 
nosqldb store, the default table schema is 
(id LONG GENERATED ALWAYS AS IDENTITY(CACHE 5000), document JSON, primary key(id)):
 	- "id" is primary key, it is always generated sequence number. If JSON contains "id" 
 	  field or want to customize the primary key field, user may specify it in config file.
	- "document JSON" is the json field which stores the whole JSON entry.

Migrate based on configuration file
-----------------------------------
Usage:
java -cp <path-to-migrator.jar> -config <configuration-file> [-verbose]

Arguments:
    -config <configuration-file>
        the configuration file that contains all configurations for the 
        migration, this is a required argument. 
        See below "Migrate Configuration file" for details.
    -verbose
        flag to get verbose output
      

Migrate Configuration File
--------------------------
The configuration file is in JSON format, it contains 3 parts
  1.  Overall configuration
  2.  Source configuration
  3.  Sink configuration

It looks like below:
  {
    "source": <source-type>,
    "sink": <sink-type>,
     ...
    <source-name>: {
       # source configuration
    },
    <sink-name>: {
       # sink configuration
    }
  }
   

1.  Overall configuration
    {
        "version": <version>
        "source": <source-type>
        "sink": <sink-type>
        "abortOnError": [false | true]
        "errorOutput": <error-dir>
        "errorFileLimitMB": <size-mb>,
        "errorFileCount": <num>,
        "checkpointOutput": <checkpoint-dir>
    }  

Properties:
    "version": <version>
        the version of the configuration, current version will be used if 
        not specified.
    "source": <source-type> 
        the source type name. 
    "sink": <sink-type>
        the sink type name.
    "abortOnError": true | false
        the flag indicates if terminate entire process if any error. 
        true - terminate the entire loading process.
	    false - continue the loading, the error will be logged to the
	            files in <errorOutputDir> if specified, otherwise to 
	            the general logging output.
    "errorOutputDir": <directory>
        the directory of the files that logs the entries failed to 
        load, each data source input corresponds to an error file.
    "errorFileLimitMB": <MB> 
        the size limit of each single error file in MB, the default is 10M.
    "errorFileCount": <num>
        the max count of files can be used for single error file, the default 
        count is 5. if the count of error files for a specific data source input
        reaches the count limit, more errors will terminate the whole migration 
        process.
    "checkpointDir": <directory> 
        the directory of the checkpoint files that logs the completion status of 
        each data source.

2.  Data source configuration
  
  * MongoDB json configuration
    "mongoJsonSource": {
        "version": <version>,
        "files": [<dir-or-file>, <dir-or-file], ...],
        "fileInfos": {
            "<file1>": {
                "namespace": <namespace>,
                "tableName": <table-name>,
                "ignoreFields": [<field1>, <field2>, ...],
                "renameFields": {
                    <old-name>:<new-name>,
                 ...
                }
            },
            "<file2>": {
                ...
            }
         },
        "datetimeToLong": <true | false>
    }
    
    Properties:
        "version": <version>
            the version of the configuration, current version will be used if
            not specified.
        "files": [path, path...] 
            An array of file path, the path can be a file or a directory. 
            If it is directory, then the files within the directory will be
            loaded but its sub-directories will be ignored.
            The source files specified are loaded to sink with default       
            configuration:
                o namespace: default namespace of sink.
                o tableName: the name of the file (exclude the suffix). 
                             e.g. users.json => table "users"
                o No transform on the data entry.
        "fileInfos": {<file-path>: <file-info>, <file-path>: <file-info>,...}
            A map of the file and its configuration, it is used to customize 
            the configuration for the specified source file. 
            The key is file path, and the value object is the configuration 
            that includes below information:
                o "namespace": <namespace>
                    the namespace of target table.
                o "tableName": <tableName> 
                    the target table name, required field.
                o "ignoreFields: [field-name, field-name, ...]
                    an array of fields that will be ignored from JSON entry,
                    only top-level field is allowed specify.
                o "renameFields": {<old-name>: <new-name>, ...}
                    a map of old field name and new field name, the specified
                    fields will be renamed to during migration.
        "dateTimeToLong": true | false
            set to true if use a long type to represent Date type 
            value, otherwise ISO8601 string will be used (default)
  
  * Json configuration
    "jsonSource": {
        "version": <version>,
        "files": [<dir-or-file>, <dir-or-file], ...],
        "fileInfos": {
            "<file1>": {
                "namespace": <namespace>,
                "tableName": <table-name>,
                "ignoreFields": [<field1>, <field2>, ...],
                "renameFields": {
                    <old-name>:<new-name>,
                    ...
                }
            },
            "<file2>": {
                ...
            }
        }
    }    
        
    Properties:
        Same as above for mongdb-json source configuration.

3.  Data sink configuration

  * Oracle NoSQL Store configuration
    "nosqldbSink" : {
        "version": <version>
        "helperHosts" : [ <host:port>, ... ],
        "storeName" : <store-name>,
        "userName" : <username>,
        "security" : <security-file-path>,
        "namespace" : <default-namespace>,
        "streamConcurrency" : <stream-parallelism>,
        "perShardConcurrency" : <per-shard-parallelism>,
        "requestTimeoutMs" : <request-timeout-ms>,
        "overwrite": <true | false>,
        "tables" : [{
            "namespace" : <namespace>,
            "tableName" : <table-name>,
            "tableStatement" : <create-table or alter-table>,
            "indexStatements": [<create-index or drop-index>, ...],
            "continueOnDdlError": <true | false>  
        }, {
            "namespace" : <namespace>,
            "tableName" : <table-name>,
            "primaryKeyField: {
                <pkey-field1>: <type>,
                <pkey-field2>: <type>,
                ...
            },
            "shardKeyFields": [<pkey-field>, ...] 
            "jsonFieldName": <json-field-name-for-non-key-fields>,
            "indexStatements": [<create-index or drop-index>, ...],
            "continueOnDdlError": <true | false>
        }]
    }

    Properties:
        "version": <version>
            the version of the configuration, current version will be used if 
            not specified.
        "helperHosts": [<helper-host>, <helper-host>, ....]
            the helper hosts of Oracle NoSQL store
        "storeName": <store-name>
            the store name
        "username": <user-name>
            the user name to connect to secured NoSQL store.
        "security": <security-file>
            the user security file
        "namespace": <namespace> 
            the default namespace of target tables. If it is not specified or
            null, then system default namespace is used.
        "streamConcurrency": <stream-concurrency> 
            the max number of source files concurrently read. If not specified 
            or 0, use default configuration.
        "perShardConcurrency": <per-shard-concurrency> 
            the max number of threads to write to a shard. 
            If not specified or 0, use default configuration.
        "requestTimeoutMs": <ms>
            the request time out in millisecond. 
            If not specified or 0, use default configuration.
        "overwrite": false | true
            the flag indicates if overwrite the existing record when put a 
            record associated a key that already present in the store or not.
            Set to true if overwrite, otherwise the record will be rejected 
            and logged as key existing record.
        "tables": [<table-info>, <table-info>, ...] 
            An array of target table information, table information contains:
            o "namespace": <namespace>
                name
            o "tableName": <table-name>
                - Specifies the primary key fields and JSON field name used when
                  create the table with primary key fields and a JSON field.                
                    o "primaryKeyFields": {<field>:<type>, <field>:<type>, ...}
                    o "shardKeyFields": [<field>, ...]
                    o "jsonFieldName": <field-name>
                - Specify create table statement or alter table statement. 
                    o "tableStatemant": <ddl-statment>
                        the create table ddl or alter table ddl                                  
            o "indexStatement": [<create-index-statement>, ...]
               an array of create index or drop index statement.
            o "continueOnDdlError": false | true
               set to true if continue the process when table/index statement
               is invalid or execute failed, otherwise throw exception and 
               terminate the migrate process. Default is false.

4.  An example of configuration file
    There are 3 JSON files in directory "jsonInput": 
      1. users.json
      2. trans.json
      3. accounts.json
      
    Run migration with below load.cfg:
      o users.json -> Table "ns0:users"
        - Table ddl
            create table if not exists users(
                userid INTEGER, 
                name STRING, 
                age INTEGER, 
                address MAP(STRING), 
                PRIMARY KEY(userid))
        - The field "age" will be ignored during migration
        - Rename "id" to "userid", "addr" to "address
      o trans.json -> Table "transaction" 
        - Table ddl
            create table if not exists (
                id STRING, 
                ts TIMESTAMP(9), 
                transRecord JSON, 
                PRIMARY KEY(id, ts))
      o accounts.json -> Table "accounts" 
        - Table ddl
            create table if not exists(
                id STRING, 
                document JSON,
                PRIMARY KEY(id))
 The configuration file "load.cfg" is as below:
    {
        "source" : "mongodb-json",
        "sink": "nosqldb",
        "abortOnError" : false,
        "errorOutput" : jsonInput/error,
        "checkpointOutput" : "jsonInput/checkpoints",
        "mongodbJsonSource" : {
            "files": [ "jsonInput" ]
            "fileInfos" : {
                "jsonInput/trans.json" : {
                    "tableName" : "transaction"
                },
                "jsonInput/users.json" : {
                    "namespace" : "ns0,
                    "tableName" : "users",
                    "ignoreFields" : ["email"],
                    "renameFields" : {
                        "id":"userid",
                        "addr":"address",
                    }
                }
        }
        "nosqldbSink" : {
            "helperHosts" : [ "localhost:5000" ],
            "storeName" : "kvstore",
            "tables": [{
                "namespace": "ns0",
                "tableName": "users",
                "tableStatement": "create table if not exists users(userid integer, name string, age integer, address map(string), primary key(userid))"
            },{
                "tableName": "transaction",
                "primaryKeyFields": {"id":"string", "ts":"timestamp(9)"},
                "jsonFieldName": "transRecord",
            }]
        }
    }
