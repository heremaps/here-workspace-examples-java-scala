# Archiving Sensoris Stream Data in Protobuf Format

This Data Archiving Library Java example shows how to use the HERE Data SDK for Java & Scala to quickly develop an application that archives
[`Sensoris`](https://sensoris.org/presentations/) messages in the `Protobuf` format.

The archiving application used in this example consists of the user-defined `SensorisProtobufSplittedUDFExample.java` class that implements the Data Archiving Library `SplittedUDF` interface.
Like any other Data Archiving Library interface implementation, this one reads input data from a `stream` layer, aggregates it using certain indexing attributes, and stores it to the `index` layer.
This specific example processes a stream of `Sensoris DataMessages` by splitting them into separate `DataMessage` payloads and indexes them by several indexing attributes like `eventTime` and `tileId`.
While indexing, application uses individual indexing attributes values per message.

For details on this and other interfaces, see the [API Reference](https://developer.here.com/documentation/data-archiving-library/api_reference/index.html) section of the Data Archiving Library Developer Guide.

## Get Your Credentials

To run this example, you need two sets of credentials:

- **Platform credentials:** To get access to the platform data and resources.
- **Repository credentials:** To download HERE Data SDK for Java & Scala libraries and Maven archetypes to your environment.

For more details on how to set up your credentials, see the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more details on how to verify that your platform credentials are configured correctly, see the [Verify Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/verify-credentials/index.html) tutorial.

## Run the Archiver Locally

The archiver is a Flink application that reads data you want to archive from the `stream` layer and writes the archived data to the `index` layer.
To run the application locally, both layers should be created in local catalogs as described below.

For more information about local catalogs, see [the SDK tutorial about local development and testing](https://developer.here.com/documentation/java-scala-dev/dev_guide/local-development-workflow/index.html)
and [the OLP CLI documentation](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data-workflows.html).

> Note
>
> We recommend that you set values to variables, so that you can easily copy and execute the following commands.

### Create a Local Input Catalog and Layer

As the Data Archiving Library reads data that is to be archived from a `stream` layer, let's create one.

First, use the [`olp local catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/catalog-commands.html#catalog-create)
command to create a local input catalog:

```bash
olp local catalog create sensoris-protobuf-input-catalog sensoris-protobuf-input-catalog \
        --summary "Input catalog for Sensoris-protobuf-archiving application" \
        --description "Input catalog for Sensoris-protobuf-archiving application"
```

The local input catalog will have the `hrn:local:data:::sensoris-protobuf-input-catalog` HRN.
Note down this HRN as you'll need it later in this example.

Next, add a `stream` layer to your catalog:

| Layer ID | Layer Type | Content Type             | TTL    |
| -------- | ---------- | ------------------------ | ------ |
| stream   | stream     | application/octet-stream | 600000 |

The content type of this layer should be defined as `application/octet-stream` as we are going to archive binary-encoded `Protobuf` serialized `Sensoris` messages.

In our case, the `ttl` value for the `stream` layer is defined as `600000` milliseconds (`10` minutes), which is the minimal value for this layer type.
To prevent data loss, the [recommendation](https://developer.here.com/documentation/data-archiving-library/dev_guide/topics/best-practices.html) is to always set the `ttl`
at least three times higher than `aggregation.window-seconds` defined in the [configuration file](./src/main/resources/application.conf).
Our value not only totally satisfies this recommendation, but also reduces the run-costs of the application.

Use the [`olp local catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/layer-commands.html#catalog-layer-add)
command to add a `stream` layer to your catalog:

```bash
olp local catalog layer add hrn:local:data:::sensoris-protobuf-input-catalog \
         stream stream \
        --stream \
        --summary "Sensoris-protobuf archiver input stream layer" \
        --description "Sensoris-protobuf archiver input stream layer" \
        --content-type application/octet-stream \
        --ttl 600000
```

Note down the layer ID as you'll need it later in this example.

### Create a Local Output Catalog and Layer

The Data Archiving Library stores archived data in the `index` layer, so let's create it.

First, use the [`olp local catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/catalog-commands.html#catalog-create)
command to create a local output catalog:

```bash
olp local catalog create sensoris-protobuf-output-catalog sensoris-protobuf-output-catalog \
        --summary "Output catalog for Sensoris-protobuf-archiving application" \
        --description "Output catalog for Sensoris-protobuf-archiving application"
```

The local output catalog will have the `hrn:local:data:::sensoris-protobuf-output-catalog` HRN.
Note down the HRN as you'll need it later in this example.

Now, add an `index` layer to the catalog:

| Layer ID | Layer Type | Event Time | Duration | Tile ID  | Zoom Level | Content Type             | TTL    |
| -------- | ---------- | ---------- | -------- | -------- | ---------- | ------------------------ | ------ |
| index    | index      | timewindow | 600000   | heretile | 8          | application/octet-stream | 7 days |

As the application stores data in the form of binary-encoded `Protobuf` messages, use the `application/octet-stream` content type for the `index` layer.
The `ttl` property is set to the minimal value for this layer type, which is `7 days`.

The most important thing while creating the `index` layer is selecting the indexing attributes.
One way to think about indexing attributes is to consider the characteristics by which you want to query your indexed data.
In this example, we plan to index vehicle sensor data, and we are interested in understanding different events occurring in different geographic locations at different times.
For this use case, we would query the indexed data on multiple characteristics, such as timestamp and geolocation.
That leads us to the following indexing attributes - `eventTime` and `tileId`.
The `eventTime` attribute should be specified as `timewindow` with the duration of `600000` milliseconds. This means that all the messages with an event time in the given time window will have the same index value.
`tileId` should have type `heretile` and zoom level `8` for saving data on the `8` level of the HERE Tiles.

Use the [`olp local catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/layer-commands.html#catalog-layer-add)
command to add an `index` layer to your catalog:

```bash
olp local catalog layer add hrn:local:data:::sensoris-protobuf-output-catalog \
        index index \
        --index \
        --summary "Sensoris-protobuf archiver output index layer" --description "Sensoris-protobuf archiver output index layer" \
        --index-definitions tileId:heretile:8 eventTime:timewindow:600000 \
        --content-type application/octet-stream \
        --ttl 7.days
```

Note down the layer ID as you'll need it later in this example.

### Update the Configuration File

After all the required resources have been created, let's configure the archiving application.
The application configuration is defined in the [`application.conf`](./src/main/resources/application.conf) file.

Mostly, the application uses default values for config parameters and only a few customized ones, for example, info about the main class of your application, catalogs and layers used for uploading unarchived data and storing archived data, and similar.

The configuration file itself is not ready to use as-is, you have to complete it first.
Before running the application, you have to define HRNs of the local [input](#create-a-local-input-catalog-and-layer) and [output](#create-a-local-output-catalog-and-layer) catalogs,
as well as the `local` discovery service environment that allows the Data Client Library to work **only** with local catalogs.

For information about all available configuration options, see the [Configure Your Application](https://developer.here.com/documentation/data-archiving-library/dev_guide/topics/configuration.html) section of the Data Archiving Library Developer Guide.

### Run the Archiver Locally

After the application has been configured, you can run it locally by running the entry point to the application:

- `com.here.platform.data.archive.example.Main`

As the `argument`, you must provide the `-Padd-dependencies-for-local-run` parameter that adds all the dependencies
needed for a local run of the archiving application.

Execute the following command in the [`sensoris-protobuf-example`](../sensoris-protobuf-example) directory to run the Sensoris Protobuf Archiving Application:

```bash
mvn compile exec:java \
-Dexec.mainClass=com.here.platform.data.archive.example.Main \
-Padd-dependencies-for-local-run
```

At a certain point after start, the application pauses and waits for you to ingest data you want to archive.

### Ingest Data for Archiving

Now we can ingest several partitions to the `stream` layer and archive them.
Partitions contain `Sensoris` messages serialized as `Protobuf` with binary encoding.
Let's take a look at [partition](src/test/resources/sampleData/sensoris_protobuf_1.pb) content after deserialization:

```
event_group {
  localization_category {
    vehicle_position_and_orientation {
      envelope {
        timestamp {
          posix_time {
            value: 1597853297988
          }
        }
      }
      position_and_accuracy {
        geographic_wgs84 {
          longitude {
            value: 1000000000
          }
          latitude {
            value: 1000000000
          }
          altitude {
            value: 0
          }
        }
      }
    }
  }
  ...
}
```

Partition content looks like a standard [`Sensoris`](https://sensoris.org/presentations/) message.

Our application takes the `envelope.timestamp` field to index messages by the `eventTime` property, and the
`position_and_accuracy.longitude_deg` and `position_and_accuracy.latitude_deg` fields are used to index messages by the `tileId` property.

To serialize the `Protobuf` data, you can use the [`_java` bindings](https://platform.here.com/data/schemas/hrn:here:schema::olp-here:org.sensoris:sensoris-specification_v1_2_2:1.1.8/overview) as follows:

```
byte[] proto = Files.readAllBytes(Paths.get("path/to/sensoris_proto_message.pb"));
DataMessages messages = DataMessages.parseFrom(bytes);
messages.getDataMessageList().forEach(System.out::println);
```

While the application is running, start a new terminal session and ingest [data](src/test/resources/sampleData) you want to archive into the `stream` layer that was created in the [previous](#create-a-local-input-catalog-and-layer) section.
For that purpose, use the [`olp local catalog layer stream put`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/local-stream-commands.html#catalog-layer-stream-put) command:

```bash
olp local catalog layer stream put hrn:local:data:::sensoris-protobuf-input-catalog stream \
        --input $PATH_TO_DIRECTORY_WITH_PARTITIONS
```

After the data has been uploaded successfully, you can verify the archived messages.

### Verify Output

After partitions have been uploaded to the `stream` layer, your data will be archived in the `index` layer that was created [previously](#create-a-local-output-catalog-and-layer).
Note that the archiving process may take a couple of minutes.

You can query archived messages using the [`olp local catalog layer partition list`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/local-partition-commands.html#catalog-layer-partition-list)
command.
Using the `--filter` parameter with this command allows filtering partitions by their name, size, and other criteria.
However, a more practical case is filtering partitions by the values of fields that were used to index messages, such as `tileId` and `eventTime`.

Let's query all archived messages that were indexed within the same time window.
For that purpose, use the `olp local catalog layer partition list` command with the `--filter "eventTime==1597852800000"` parameter:

```bash
olp local catalog layer partition list hrn:local:data:::sensoris-protobuf-output-catalog index --filter "eventTime==1597852800000"
```

The command above displays the following list of partitions:

```
dataHandle                              size                checksum                                          CRC
352e4b0e-ae7f-4532-98e6-2324d3b1f4d1    65
5c75a28c-7656-4384-b77a-d3fb1ec09117    65
d1650d3c-f8d0-45f1-ab89-fb1e3aeec173    65
88664c4d-fd79-4b61-abf5-46a318ba8588    65
c3c08247-b2db-4743-adf7-39b88dd1a932    65

Total size: 325 B
```

As you can see, only `5` partitions are present in the command output, although we ingested `6` partitions [previously](#ingest-data-for-archiving).
One partition is missing in the list, because it has been indexed within a time window different from the one mentioned above.
This is expected application behavior, because `Sensoris DataMessage` stored in that [specific](src/test/resources/sampleData/sensoris_protobuf_4.pb) partition
contains data about the event that occurred on a different day as compared to other archived messages.

We can find this partition using the following command:

```bash
olp local catalog layer partition list hrn:local:data:::sensoris-protobuf-output-catalog index --filter "eventTime==1597942200000"
```

The command above displays the following list of partitions:

```
dataHandle                              size                checksum                                          CRC
f3aad0e4-88dd-4384-be23-24c76b297bf4    65

Total size: 65 B
```

As you can see, our application successfully archived all messages, and now they are available in the `index` layer.

Now let's query partitions located in the HERE Tile with ID `90175`.
To do it, use the `olp local catalog layer partition list` command with the `--filter "tileId==90175"` parameter:

```bash
olp local catalog layer partition list hrn:local:data:::sensoris-protobuf-output-catalog index --filter "tileId==90175"
```

The command above displays the following list of partitions:

```
dataHandle                              size                checksum                                          CRC
352e4b0e-ae7f-4532-98e6-2324d3b1f4d1    65

Total size: 65 B
```

As you can see, there is only one partition stored in this specific HERE Tile.

Now, let's get this partition and examine it.
To get archived data from the `index` layer, you can use the [`olp local catalog layer partition get`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/local-partition-commands.html#catalog-layer-partition-get)
command with the `--filter "tileId==90175"` parameter to get the partition mentioned above:

```bash
olp local catalog layer partition get hrn:local:data:::sensoris-protobuf-output-catalog index --filter "tileId==90175"
```

After the partition has been successfully downloaded to the `sensoris-protobuf-output-catalog-index` directory, let's inspect it.
The downloaded partition contains data in the `Protobuf` format. After deserialization, the following `Sensoris` message is displayed:

```
event_group {
  localization_category {
    vehicle_position_and_orientation {
      envelope {
        timestamp {
          posix_time {
            value: 1597853297988
          }
        }
      }
      position_and_accuracy {
        geographic_wgs84 {
          longitude {
            value: 1000000000
          }
          latitude {
            value: 1000000000
          }
          altitude {
            value: 0
          }
        }
      }
    }
  }
  ...
}
```

As you can see, the downloaded partition contains the same `Sensoris` message that has been uploaded in the section [above](#ingest-data-for-archiving).
It is worth mentioning that the `position_and_accuracy` component has the following longitude and latitude values: `1000000000` and `1000000000`, respectively.
If we use these values to calculate the ID of the HERE Tile on the `8` level, we get `90175`, which is exactly the ID of the HERE Tile that contains the message we queried from the `index` layer.

You can deserialize `Protobuf` data as follows:

```
byte[] bytes = Files.readAllBytes(Paths.get("path/to/sensoris_proto_message.pb"));
DataMessages messages = DataMessages.parseFrom(bytes);
messages.getDataMessageList().forEach(System.out::println);
```

## Build and Run the Archiver as a HERE Platform Pipeline

To run the archiving application in a HERE platform pipeline, you need to have a [project](https://developer.here.com/documentation/identity-access-management/dev_guide/topics/manage-projects.html).

### Configure a Project

A project is a collection of platform resources (catalogs, pipelines, schemas, and so on) with controlled access.
You can create a project using the OLP CLI [`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project) command:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/portal/layers/hrn.html) of your new project.
Save the project HRN to the `PROJECT_HRN` variable as you will need it later in this tutorial.

> #### Note
>
> You do not have to provide a `--scope` parameter if your app has a default scope.
> For details on how to set a default project scope for an app, see the _Set a default
> project for an app_ chapter of the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/topics/manage-projects.html#set-a-default-project-for-an-app).

For more information on how to work with projects, see the [Organize your work in projects](https://developer.here.com/documentation/java-scala-dev/dev_guide/organize-work-in-projects/index.html) tutorial.

### Create an Input Catalog and Layer

As the Data Archiving Library reads data to be archived from a `stream` layer, let's create one.

First, use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create)
command to create an input catalog.

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME \
        --summary "Input catalog for Sensoris-protobuf-archiving application" \
        --description "Input catalog for Sensoris-protobuf-archiving application" \
        --scope $PROJECT_HRN
```

Save the catalog HRN to the `INPUT_CATALOG_HRN` variable as you will need it later in this tutorial.

Next, add a `stream` layer to your catalog.
For the required parameters, see the section on the [local `stream` layer](#create-a-local-input-catalog-and-layer) creation.

Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add)
command to add a `stream` layer to your catalog:

```bash
olp catalog layer add $INPUT_CATALOG_HRN \
        stream stream \
        --stream \
        --summary "Sensoris-protobuf archiver input stream layer" \
        --description "Sensoris-protobuf archiver input stream layer" \
        --content-type application/octet-stream \
        --ttl 600000 \
        --scope $PROJECT_HRN
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tags: "YOUR_BILLING_TAG"` parameter.

### Create an Output Catalog and Layer

The Data Archiving Library stores archived data in the `index` layer, so let's create it.

First, use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create)
command to create an output catalog.

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME \
        --summary "Output catalog for Sensoris-protobuf-archiving application" \
        --description "Output catalog for Sensoris-protobuf-archiving application" \
        --scope $PROJECT_HRN
```

Save the catalog HRN to the `OUTPUT_CATALOG_HRN` variable as you will need it later in this tutorial.

Now, add an `index` layer to the catalog.
For the required parameters, see the section on the [local `index` layer](#create-a-local-output-catalog-and-layer) creation.

Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add)
command to add an `index` layer to your catalog:

```bash
olp catalog layer add $OUTPUT_CATALOG_HRN \
        index index \
        --index \
        --summary "Sensoris-protobuf archiver output index layer" --description "Sensoris-protobuf archiver output index layer" \
        --index-definitions tileId:heretile:8 eventTime:timewindow:600000 \
        --content-type application/octet-stream \
        --ttl 7.days \
        --scope $PROJECT_HRN
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tags: "YOUR_BILLING_TAG"` parameter.

### Update the Configuration File {#update-config-for-run-on-platform}

After all the required resources have been created, let's configure the archiving application.
The application configuration is defined in the [`application.conf`](./src/main/resources/application.conf) file.

Let's use the same configurations as for the [local run](#update-the-configuration-file) but
apply the HRNs of the platform [input](#create-an-input-catalog-and-layer) and [output](#create-an-output-catalog-and-layer) catalogs,
and the `here` discovery service environment.

For information about all available configuration options, see the [Configure Your Application](https://developer.here.com/documentation/data-archiving-library/dev_guide/topics/configuration.html) section of the Data Archiving Library Developer Guide.

### Generate a Fat JAR file

Now we can move forward and create a fat JAR from the application.
To create it, run the `mvn clean package` command in the [`sensoris-protobuf-example`](../sensoris-protobuf-example)
directory:

```bash
mvn clean package
```

Once the above command is successful, a fat JAR named `data-archive-sensoris-protobuf-example-<VERSION>-platform.jar` will be built in the `target` folder.

### Configure a Pipeline Template

After we received the fat JAR, we can start creating a pipeline template.

HERE platform provides pipeline templates as a way to get started with common data processing tasks.
Pipeline templates are scalable, configurable processing blocks that you can deploy as part of your own workflow, without needing to write any code.
Each pipeline template is designed to perform a specific task and can be customized to accommodate your particular use case.

Use the [`olp pipeline template create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/template-commands.html#pipeline-template-create) command to create a pipeline template:

```bash
olp pipeline template create sensoris-protobuf-pipeline-template \
    stream-5.0 \
    $PATH_TO_JAR \
    com.here.platform.dal.DALMain \
    --input-catalog-ids=source \
    --scope $PROJECT_HRN
```

Save the pipeline template ID to the `PIPELINE_TEMPLATE_ID` variable as you will need it later in this tutorial.

### Configure a Pipeline

Let's move forward and create a data processing pipeline.
HERE platform uses pipelines to process data from HERE geospatial resources and custom client resources to produce new useful data products.

Use the [`olp pipeline create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/pipeline-commands.html#pipeline-create) command to create a pipeline:

```bash
olp pipeline create sensoris-protobuf-pipeline --scope $PROJECT_HRN
```

Save the pipeline ID to the `PIPELINE_ID` variable as you will need it later in this tutorial.

### Update the Pipeline Configuration File

To run your archiving application as a HERE platform pipeline, you need to configure data sources in the [`pipeline-config.conf`](./config/pipeline-config.conf) file.
This file contains the configuration of the data sources that are used for the Data Archiving Library application:

```javascript
pipeline.config {
  output-catalog {hrn = "YOUR_OUTPUT_CATALOG_HRN"}
  input-catalogs {
    source {hrn = "YOUR_INPUT_CATALOG_HRN"}
  }
}
```

You must replace the `YOUR_INPUT_CATALOG_HRN` placeholder with the HRN of the [input](#create-an-input-catalog-and-layer) catalog
and the `YOUR_OUTPUT_CATALOG_HRN` placeholder with the HRN of the [output](#create-an-output-catalog-and-layer) catalog.

### Configure a Pipeline Version

Once you have created both the pipeline and pipeline template and updated the pipeline configuration file, you can proceed to creating a pipeline version.
A pipeline version is an immutable entity representing an executable form of a pipeline within the HERE platform.

Use the [`olp pipeline version create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-create) command to create a pipeline version:

```bash
olp pipeline version create sensoris-protobuf-pipeline-version \
    $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
    "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf" \
    --scope $PROJECT_HRN
```

Save the pipeline version ID to the `PIPELINE_VERSION_ID` variable as you will need it later in this tutorial.

### Run the Archiver on HERE Platform

Now you can run the application as a HERE platform pipeline.
For that purpose, use the [`olp pipeline version activate`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate) command:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

Use the [`olp pipeline version show`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-show) command
to inspect the state of the pipeline version:

```
olp pipeline version show $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

Since this is a Flink application, this means that it runs until you stop it.
In order to stop the application after you have finished working with it, execute the [`olp pipeline version cancel`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-cancel) command:

```
olp pipeline version cancel $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

Once the pipeline version gets in the `running` state, you can start using your archiving application as described in the next section.

### Ingest Data for Archiving {#ingest-data-for-platform-run}

Now we can ingest several partitions to the `stream` layer to archive their data.
In this section, we will use the same partitions as for the [local run](#ingest-data-for-archiving) of the application.

While the application is running, start a new terminal session and ingest [data](src/test/resources/sampleData) you want to archive into the `stream` layer that was created in the [previous](#create-an-input-catalog-and-layer) section.
To do it, use the [`olp catalog layer stream put`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/stream-commands.html#catalog-layer-stream-put) command:

```
olp catalog layer stream put $INPUT_CATALOG_HRN stream \
        --input $PATH_TO_DIRECTORY_WITH_PARTITIONS \
        --scope $PROJECT_HRN
```

After the data has been uploaded successfully, you can verify the archived messages.

### Verify Output {#verify-output-for-platform-run}

After partitions have been uploaded to the `stream` layer, your data will be archived in the `index` layer that was created [previously](#create-a-local-output-catalog-and-layer).
Note that the archiving process may take a couple of minutes.

You can query archived messages using the [`olp catalog layer partition list`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-list)
command.
Using the `--filter` parameter with this command allows filtering partitions by their name, size, and other criteria.
However, a more practical case is filtering partitions by the values of fields that were used to index messages, such as `tileId` and `eventTime`.

Let's query all archived messages that were indexed within the same time window.
For that purpose, use the `olp catalog layer partition list` command with the `--filter "eventTime==1597852800000"` parameter:

```
olp catalog layer partition list $OUTPUT_CATALOG_HRN index \
    --filter "eventTime==1597852800000" \
    --scope $PROJECT_HRN
```

The command above displays the following list of partitions:

```
dataHandle                              size                checksum                                          CRC
352e4b0e-ae7f-4532-98e6-2324d3b1f4d1    65
5c75a28c-7656-4384-b77a-d3fb1ec09117    65
d1650d3c-f8d0-45f1-ab89-fb1e3aeec173    65
88664c4d-fd79-4b61-abf5-46a318ba8588    65
c3c08247-b2db-4743-adf7-39b88dd1a932    65

Total size: 325 B
```

As you can see, only `5` partitions are present in the command output, although we ingested `6` partitions [previously](#ingest-data-for-archiving-ingest-data-for-platform-run).
This is expected application behavior, for more details, see the section on output verification for the [local run](#verify-output).

Now let's query partitions located in the HERE Tile with ID `90175`.
To do it, use the `olp catalog layer partition list` command with the `--filter "tileId==90175"` parameter:

```
olp catalog layer partition list $OUTPUT_CATALOG_HRN index \
    --filter "tileId==90175" \
    --scope $PROJECT_HRN
```

The command above displays the following list of partitions:

```
dataHandle                              size                checksum                                          CRC
352e4b0e-ae7f-4532-98e6-2324d3b1f4d1    65

Total size: 65 B
```

As you can see, there is only one partition stored in this specific HERE Tile.

Now, let's get this partition and examine it.
To get archived data from the `index` layer, you can use the [`olp catalog layer partition get`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-get)
command with the `--filter "tileId==90175"` parameter to get the partition mentioned above:

```
olp catalog layer partition get $OUTPUT_CATALOG_HRN index \
    --filter "tileId==90175" \
    --scope $PROJECT_HRN
```

After the partition has been successfully downloaded to the `sensoris-protobuf-output-catalog-index` directory,
you can inspect it using the same approach as described in the [local run](#verify-output) section.
