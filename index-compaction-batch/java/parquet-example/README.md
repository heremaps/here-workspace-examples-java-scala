# Compacting Index Layer Data in Parquet Format

This example shows how to use the [Index Compaction Library](https://developer.here.com/documentation/index-compaction-library/dev_guide/index.html) to quickly develop a compaction application that compacts Parquet-format data in an index layer.

The [`ParquetCompactionExample`](src/main/java/com/here/platform/index/compaction/batch/ParquetCompactionExample.java) application
compacts partitions in the index layer with the same index attribute values into one partition.
It allows users to reduce the index layer storage cost, improves query performance, and also makes subsequent data processing more efficient.
The application implements the [`CompactionUDF`](https://developer.here.com/documentation/index-compaction-library/api_reference/index.html) interface
that provides control over how the data is compacted in the index layer.

For details on this interface, see the _API Reference_ section of the [Index Compaction Library Developer Guide](https://developer.here.com/documentation/index-compaction-library/api_reference/index.html).

## Get Your Credentials

To run this example, you need two sets of credentials:

- **Platform credentials:** To get access to the platform data and resources.
- **Repository credentials:** To download HERE Data SDK for Java and Scala libraries and Maven archetypes to your environment.

For more details on how to set up your credentials, see the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more details on how to verify that your platform credentials are configured correctly, see the [Verify Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/verify-credentials/index.html) tutorial.

## Run the Application Locally

To run the compaction application locally, use local catalogs as described
below. For more information about local catalogs, see [the SDK tutorial about local development and testing](https://developer.here.com/documentation/java-scala-dev/dev_guide/local-development-workflow/index.html)
and [the OLP CLI documentation](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data-workflows.html).

### Create a Local Input Catalog and Layer

The Index Compaction Library compacts data in the `index` layer, so let's create it.

First, use the [`olp local catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/catalog-commands.html#catalog-create)
command to create a local output catalog:

```bash
olp local catalog create compaction-parquet compaction-parquet \
        --summary "Input catalog for index compaction application" \
        --description "Input catalog for index compaction application"
```

The local output catalog will have the `hrn:local:data:::compaction-parquet` HRN.
Note down the HRN as you'll need it later in this example.

Now, let's add the `index` layer to the `hrn:local:data:::compaction-parquet` catalog.
As the application stores data in the form of binary-encoded `Parquet` messages, use the `application/x-parquet` content type for the `index` layer.

The most important thing while creating the `index` layer is selecting the indexing attributes.
This application compacts partitions that were loaded with common index attribute values.
To ensure correct performance, we need to assign the following index attributes when adding a layer - `eventType`, `tileId`, and `ingestionTime`.

The `eventType` attribute should be declared as `string` because we want to compact messages indexed on the basis of names of certain events (`signRecognition`, `fogHazard`, and similar.)
The `tileId` attribute should have type `heretile` and zoom level `8` for compacting data on level `8` of the HERE Tiles.
The `ingestionTime` attribute should be specified as `timewindow` with the duration of `3600000` milliseconds (10 min). This means that all the messages with an event time in the given time window will have the same index value.

Use the [`olp local catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/layer-commands.html#catalog-layer-add)
command to add an `index` layer to your catalog:

```bash
olp local catalog layer add hrn:local:data:::compaction-parquet index index --index --summary "index" \
        --description "index" --content-type application/x-parquet \
        --index-definitions tileId:heretile:8 ingestionTime:timewindow:3600000 eventType:string
```

Note down the layer ID as you'll need it later in this example.

### Create a Local Output Catalog

> #### Note
>
> The HERE platform does not allow the same catalog to be used as both input and output for batch pipelines.
> For the Index Compaction Library, the input and output catalogs are the same as the library compacts the same index layer.
> Specify the catalog to be compacted under the `input-catalogs` setting.
> For the `output-catalog` setting, you still need to pass a valid catalog.
> You can use a catalog with zero layers.

Use the [`olp local catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/catalog-commands.html#catalog-create)
command to create a local output catalog:

```bash
olp local catalog create compaction-parquet-output compaction-parquet-output \
        --summary CATALOG_SUMMARY \
        --description CATALOG_DESCRIPTION
```

The local output catalog will have the `hrn:local:data:::compaction-parquet-output` HRN.

### Ingest Data for Local Compaction

After creating an input catalog and layer,
you should populate the index layer with [sample data](src/test/resources/sampleData) that has common index attribute values.
The [sample data](src/test/resources/sampleData) folder contains 6 files with `SDII` messages serialized as `Parquet` with binary encoding.
Let's take a look at partition content after deserialization:

```
envelope {
  version: "2.1"
  submitter: "test"
  vehicleMetaData {
    vehicleTypeGeneric: PASSENGER_CAR
    vehicleSpecificMetaData {
      key: "OEM.Reference"
      value: "804ece23-981e-47fd-97b9-9ccf4323658b"
    }
    vehicleReferencePointDeltaAboveGround_m: 0.5
  }
}
path {
  positionEstimate {
    timeStampUTC_ms: 1492125730551
    positionType: FILTERED
    interpolatedPoint: true
    longitude_deg: 61.473041
    latitude_deg: 23.773894
    horizontalAccuracy_m: 2.0
  }
  positionEstimate {
  .......
  }
}
pathEvents {
   signRecognition {
    timeStampUTC_ms: 1492135095592
    positionOffset {
      lateralOffsetSimple: LATERAL_OFFSET_SIMPLE_LEFT
    }
    ........
   }
}
```

Partition content looks like a standard [`SDII`](https://developer.here.com/documentation/sdii-data-spec/dev_guide/topics/message-components.html) message.
The application takes the `timeStampUTC_ms` field to index messages by the `timewindow` property, while the `longitude_deg` and `latitude_deg` fields are used to index messages by the `tileId` property,
and the `signRecognition` event is used to index messages by the `eventType` property.

To deserialize all files, you can use the [`_java bindings`](https://platform.here.com/data/schemas/hrn:here:schema::olp-here:com.here.sdii:sdii_message_v3:4.2.6/overview) as follows:

```
ParquetReader<SdiiMessage.Message.Builder> parquetReader = ProtoParquetReader.<SdiiMessage.Message.Builder>builder(
                new org.apache.hadoop.fs.Path("path/to/parquet_in_pb_format")).build();

for (SdiiMessage.Message.Builder sdiiMessageBuilder;
     (sdiiMessageBuilder = parquetReader.read()) != null; ) {
    System.out.println(sdiiMessageBuilder.build());
}
```

Let's use the [`olp local catalog layer partition put`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-put) command to populate the `index` layer
with such index fields as `ingestionTime:1594236600000`, `tileId:79963` and `eventType:SignRecognition`. Thus, all partitions that were uploaded with common index attributes will be compacted into one partition.

```bash
olp local catalog layer partition put hrn:local:data:::compaction-parquet index \
        --input src/test/resources/sampleData \
        --index-fields timewindow:ingestionTime:1594236600000 heretile:tileId:79963 string:eventType:SignRecognition
```

Once the partitions are uploaded to the index layer, we can move on to running the application.

### Run the Application from the Command Line

To run the compiler locally, you will need to run the entry point to the application:

- `com.here.platform.index.compaction.batch.Main`

As argument, you must provide the `-Padd-dependencies-for-local-run` parameter that adds all dependencies
needed for a local run of the application.

To run the application locally, you need
two configuration files: [`application.conf`](src/main/resources/application.conf) and [`pipeline-config.conf`](src/main/resources/pipeline-config.conf).

The [`application.conf`](src/main/resources/application.conf) configuration file contains all the application-specific settings that differ from the defaults provided by the `reference.conf` file in the Index Compaction Library.
It contains the `com.here.platform.index.compaction.batch.ParquetCompactionExample` class that implements the `CompactionUDF` interface,
index layer ID and the `query.constraint` field with `size>0` value to compact all partitions in the index layer.
If you want to compact a slice of an index layer based on `timewindow`, `heretile`, and so on, update the `constraint` field using [rsql](https://developer.here.com/documentation/data-client-library/dev_guide/client/rsql.html) query language.

For more information about the `application.conf` configuration file, see the [Index Compaction Library Developer Guide](https://developer.here.com/documentation/index-compaction-library/dev_guide/index.html).

The [`pipeline-config.conf`](src/main/resources/pipeline-config.conf) file contains the input and output catalog HRNs.

To run your Spark application locally with Java 17, you should provide `--add-opens=java.base/sun.nio.ch=ALL-UNNAMED` to the command arguments.

Execute the following command in the [parquet-example](../parquet-example) directory to compact partitions in the index layer:

```bash
mvn compile -q exec:exec \
-Dexec.args="-cp %classpath --add-opens=java.base/sun.nio.ch=ALL-UNNAMED com.here.platform.index.compaction.batch.Main" \
-Padd-dependencies-for-local-run
```

> #### Note
>
> If you encounter problems running Hadoop on Windows,
> you can follow this [guide](https://cwiki.apache.org/confluence/display/HADOOP2/WindowsProblems)
> to get required binary like `WINUTILS.EXE` and
> set the environment variable `HADOOP_HOME` to point to the directory above the `BIN` dir containing `WINUTILS.EXE`.

### Verify the Local Run Output

After the application is finished, you can check the result of the compaction.

To verify the compaction example output,
use the [`olp local catalog layer partition get`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-get) command to query the `index` layer.

```bash
olp local catalog layer partition list hrn:local:data:::compaction-parquet index \
        --filter "size=gt=0"
```

If you populated your `index` layer with [sample data](src/test/resources/sampleData) using the command in [Ingest Data for Local Compaction](#ingest-data-for-local-compaction) section,
the command above should return `1` partition on querying your `index` layer:

```
dataHandle                              size         checksum       CRC
07a00c79-b16f-4b31-8410-f886ee0fce90    174232

Total size: 170.1 KB
```

It means that all your small files have been successfully compacted in one big file, and at the same time the file size is much smaller than the individual file sizes.

Use the following OLP CLI command to download the compacted partition:

```bash
olp local catalog layer partition get hrn:local:data:::compaction-parquet index \
        --filter "size=gt=0"
```

After the partition has been successfully downloaded, let's inspect it.
The downloaded partition contains data in the Parquet format. After deserialization with the Java code snippet mentioned in the [Ingest Data For Local Compaction](#ingest-data-for-local-compaction) section,
you should see the content of the 6 files uploaded in the previous section compacted in one big partition:

```
envelope {
  version: "2.1"
  submitter: "test"
  vehicleMetaData {
    vehicleTypeGeneric: PASSENGER_CAR
    vehicleSpecificMetaData {
      key: "OEM.Reference"
      value: "56e25e71-b98d-4533-b018-abcfc683f25f"
    }
    vehicleReferencePointDeltaAboveGround_m: 0.5
  }
}
path {
  positionEstimate {
    ...........
  }
  positionEstimate {
    ...........
  }
}
pathEvents {
  signRecognition {
    timeStampUTC_ms: 1492135094592
    positionOffset {
      lateralOffsetSimple: LATERAL_OFFSET_SIMPLE_LEFT
    }
    roadSignType: SPEED_LIMIT_START
    roadSignPermanency: VARIABLE
    roadSignValue: "70"
  }
  signRecognition {
    ..............
}

envelope {
  version: "2.1"
  submitter: "test"
  vehicleMetaData {
    vehicleTypeGeneric: PASSENGER_CAR
    vehicleSpecificMetaData {
      key: "OEM.Reference"
      value: "774f0ea9-daad-48f5-9c4d-a58b1f93bbe0"
    }
    vehicleReferencePointDeltaAboveGround_m: 0.5
  }
}
path {
  positionEstimate {
    ..............
  }
 .............
  }
}
pathEvents {
  specificObservedEvent {
    timeStampUTC_ms: 1492129536521
    cause: stationaryVehicle
    subcause {
      stationaryVehicleSubCause: vehicleBreakdown
    }
    relevanceTrafficDirection: allTrafficDirections
    relevanceEventReference: allStreamsTraffic
    relevanceDistance: lessThan1000M
    eventTimeToLive: 1000
  }
}

envelope {
  version: "2.1"
  submitter: "test"
  vehicleMetaData {
    vehicleTypeGeneric: PASSENGER_CAR
    vehicleSpecificMetaData {
      key: "OEM.Reference"
      value: "a1746204-ab67-44ce-916e-c122b640ad19"
    }
    vehicleReferencePointDeltaAboveGround_m: 0.5
  }
}
..........
}
```

## Build and Run the Application as a HERE Platform Pipeline

To run the application as a HERE platform pipeline, you need to create a project first.

### Configure a Project

To follow this example, you will need a [project](https://developer.here.com/documentation/identity-access-management/dev_guide/topics/manage-projects.html). A project is a collection of platform resources
(catalogs, pipelines, and schemas) with controlled access. You can create a project through the
[HERE platform portal](https://platform.here.com/).

Alternatively, use the OLP CLI [`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project) command to create the project:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/index.html) of your new project. Note down this HRN as you will need it later in this tutorial.

> #### Note
>
> You do not have to provide a `--scope` parameter if your app has a default scope.
> For details on how to set a default project scope for an app, see the _Set a default project for an app_
> chapter of the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more information on how to work with projects, see the [Organize your work in projects](https://developer.here.com/documentation/java-scala-dev/dev_guide/organize-work-in-projects/index.html) tutorial.

### Create Input Catalog and Layer

The Index Compaction Library compacts data in the `index` layer, so let's create it.

Let's create an input catalog with the same configuration as we used in the [Create a Local Input Catalog and Layer](#create-a-local-input-catalog-and-layer) section:

1. Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create the catalog.
   Make sure you record the HRN returned by the following command for later use:

```bash
olp catalog create $CATALOG_ID $CATALOG_ID --summary "Input catalog for index compaction application" \
        --description "Input catalog for index compaction application" \
        --scope $PROJECT_HRN
```

Save the catalog HRN to the `CATALOG_HRN` variable as you will need it later in this example.

2. Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add) command to add an `index` layer to your catalog:

```bash
olp catalog layer add $CATALOG_HRN index index --index --summary "index" \
        --description "index" --content-type application/x-parquet --ttl 7.days \
        --index-definitions tileId:heretile:8 ingestionTime:timewindow:3600000 eventType:string \
        --scope $PROJECT_HRN
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tags: "YOUR_BILLING_TAG"` parameter.

### Create Output Catalog

> #### Note
>
> The HERE platform does not allow the same catalog to be used as both input and output for batch pipelines.
> For Index Compaction Library, input and output catalog are the same as the library compacts the same index layer.
> You should specify the desired catalog to be compacted under the `input-catalogs` setting.
> For the `output-catalog` setting, you still need to pass a valid catalog.
> You can use a catalog with zero layers.

Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create a catalog.

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY \
        --description CATALOG_DESCRIPTION --scope $PROJECT_HRN
```

### Ingest Data for Compaction

After creating an input catalog and layer,
you should populate the index layer with [sample data](src/test/resources/sampleData) that has common index attribute values,
so that corresponding records with smaller files can be compacted to bigger files.

1. Use the [`olp catalog layer partition put`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-put) command to populate the `index` layer.

```bash
olp catalog layer partition put $CATALOG_HRN index \
        --input src/main/resources/sampleData \
        --index-fields timewindow:ingestionTime:1594236600000 heretile:tileId:79963 string:eventType:SignRecognition \
        --scope $PROJECT_HRN
```

### Package the Application Into a Fat JAR

To run the compaction pipeline in the HERE platform, you need to build a fat JAR.

You can see the `pom.xml` file for packaging details if you are creating your own application.
Otherwise, run the following command under the example's base directory:

```bash
mvn clean package
```

Once the above command is successful, a fat JAR named `index-compaction-parquet-example-<VERSION>-platform.jar` will be built in the `target` folder.

### Configure a Pipeline Template

After we received the fat JAR, we can start creating a pipeline template.

HERE platform provides pipeline templates as a way to get started with common data processing tasks.
Pipeline templates are scalable, configurable processing blocks that you can deploy as part of your own workflow, without needing to write any code.
Each pipeline template is designed to perform a specific task and can be customized to accommodate your particular use case.

Use the [`olp pipeline template create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/template-commands.html#pipeline-template-create) command to create a pipeline template:

```bash
olp pipeline template create $PIPELINE_TEMPLATE_NAME \
    batch-4.1  \
    $PATH_TO_JAR \
    com.here.platform.index.compaction.batch.Driver \
    --input-catalog-ids=source \
    --scope $PROJECT_HRN
```

Save the pipeline template ID to the `PIPELINE_TEMPLATE_ID` variable as you will need it later in this tutorial.

### Configure a Pipeline

Let's move forward and create a data processing pipeline.
HERE platform uses pipelines to process data from HERE geospatial resources and custom client resources to produce new useful data products.

Use the [`olp pipeline create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/pipeline-commands.html#pipeline-create) command to create a pipeline:

```bash
olp pipeline create $PIPELINE_NAME --email $OLP_EMAIL --scope $PROJECT_HRN
```

Save the pipeline ID to the `PIPELINE_ID` variable as you will need it later in this tutorial.

### Update the Pipeline Configuration File

To run your application as a HERE platform pipeline, you need to configure data sources in the [`pipeline-config.conf`](./config/pipeline-config.conf) file.
This file contains the configuration of the data sources that are used for the Data Archiving Library application:

```javascript
pipeline.config {
  output-catalog {hrn = "YOUR_OUTPUT_CATALOG_HRN"}
  input-catalogs {
    source {hrn = "YOUR_INPUT_CATALOG_HRN"}
  }
}
```

You must replace the `YOUR_INPUT_CATALOG_HRN` placeholder with the HRN of the [input](#create-input-catalog-and-layer) catalog
and the `YOUR_OUTPUT_CATALOG_HRN` placeholder with the HRN of the [output](#create-output-catalog) catalog.

### Configure a Pipeline Version

Once you have created both the pipeline and the pipeline template and updated the pipeline configuration file, you can proceed to creating a pipeline version.
A pipeline version is an immutable entity representing an executable form of a pipeline within the HERE platform.

Use the [`olp pipeline version create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-create) command to create a pipeline version:

```bash
olp pipeline version create $PIPELINE_VERSION_NAME $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
        $PATH_TO_CONFIG_FOLDER/pipeline-config.conf --scope $PROJECT_HRN
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tag: "YOUR_BILLING_TAG"` parameter.

Save the pipeline version ID to the `PIPELINE_VERSION_ID` variable as you will need it later in this tutorial.

### Run the Application on the Platform

Now you can run the application as a HERE platform pipeline.
For that purpose, use the [`olp pipeline version activate`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate) command:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

Execute the following command to wait until the pipeline reaches the "completed" state:

```
olp pipeline version wait $PIPELINE_ID $PIPELINE_VERSION_ID --job-state=completed --scope $PROJECT_HRN
```

To get more information on how to monitor a Spark application, see the [`Run a Spark application on the platform`](https://developer.here.com/documentation/java-scala-dev/dev_guide/run-spark-application-platform/index.html)

## Verify the Output

Once the compaction pipeline has finished, you can query the compacted data using
the [`olp catalog layer partition list`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-list) command to query the `index` layer.

```bash
olp catalog layer partition list $CATALOG_HRN index \
        --filter "size=gt=0" --scope $PROJECT_HRN
```

If you populated your `index` layer with [sample data](src/test/resources/sampleData) using the command in [Ingest Data for Compaction](#ingest-data-for-compaction) section,
the command above should return `1` partition on querying your `index` layer:

```
dataHandle                              size         checksum       CRC
07a00c79-b16f-4b31-8410-f886ee0fce90    174232

Total size: 170.1 KB
```

It means that all your small files have been successfully compacted in one big file, and at the same time the file size is much smaller than the individual file sizes.

Use the following OLP CLI command to download the compacted partition:

```bash
olp catalog layer partition get $CATALOG_HRN index \
        --filter "size=gt=0" --scope $PROJECT_HRN
```

After the partition has been successfully downloaded, let's inspect it.
The downloaded partition contains data in the Parquet format. After deserialization with the Java code snippet mentioned in the [Ingest Data For Local Compaction](#ingest-data-for-local-compaction) section,
you should see the content of the 6 files uploaded in the previous section compacted in one big partition:
