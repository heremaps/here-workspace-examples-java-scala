# Infer Stop Signs From Sensors

This document contains the instructions to run the
`Infer Stop Signs From Sensors` example.

The example shows how to use the distributed clustering and path matching
algorithms of the `Location Library` together with the
`HERE Optimized Map for Location Library` to derive the positions of stop signs.

Input data in form of the Sensor Data Ingestion Interface (SDII) format messages
are coming from a specified SDII index catalog.

The example logic does the following:

1. Retrieve stop sign events from the input catalog.

   - The example limits itself in the specified area of interest so that it does
     not process the entire world.
   - The input catalog contains various events, so it filters out all the events
     except stop sign events.
   - Any given stop sign event does not contain an event position but a time
     stamp when it happened and a path that the car drove along at that time.
     The example calculates the event position by applying interpolation logic.

2. Project stop sign event positions onto the closest road segment.

   - The example map-matches an event path for the stop sign, and then projects
     the event position of the stop sign onto the closest vertex of the matched
     path.

3. Cluster stops sign events.

   - The example uses interpolated stop sign event positions to cluster stop
     sign events.

4. Find the most probable position of the stop sign for each cluster

   - The example averages fraction values of projected points.

5. Publish the result into the output catalog.

   - The example uses the `Data Client Library` API to publish the result in the
     output catalog.

## Prerequisites

Before you execute the instructions in the next sections of this document, read
the [Prerequisites](../../../README.md#prerequisites) for the `Location Library`
examples.

To run the example, you need access to the following catalogs:

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)
or China specific [`HERE Optimized Map for Location Library`](https://platform.hereolp.cn/data/hrn:here-cn:data::olp-cn-here:here-optimized-map-for-location-library-china-2)
catalog
- [`HERE Sample SDII Messages - Berlin`](https://platform.here.com/data/hrn:here:data::olp-here:olp-sdii-sample-berlin-2)
or [`HERE Sample SDII Messages - China`](https://platform.hereolp.cn/data/hrn:here-cn:data::olp-cn-here:sample-data)

## Create Output Catalog

The catalog you need to create is used to store the results of `Infer Stop Signs From Sensors` example.

Using the OLP Portal, create a new catalog and the following catalog layer:

| Layer ID                 | Layer Type | Content Type             | Partitioning | Zoom Level | Content Encoding | Coverage |
|--------------------------|------------|--------------------------|--------------|------------|------------------|----------|
| stop-signs               | versioned  | application/vnd.geo+json | heretile     | 14         | uncompressed     | DE or CN |

Alternatively, you can use the OLP CLI to create a catalog and the corresponding layers:
In the commands that follow replace the variable placeholders with the following values:
- `$CATALOG_ID` is your output catalog's ID.
- `$CATALOG_HRN` is your output catalog's `HRN` (returned by `olp catalog create`).
- `$HRN_PARTITION` is the OLP environment you are in. Specify `here` unless you are
using the OLP China environment, in which case specify `here-cn`.
- `$GROUP_ID` is the credentials group ID your HERE user belongs to.
- `$COVERAGE` is a two-letter code for country and region (in this case `DE` or `CN` for China)

* First, create an output catalog and grant the correct permission to your group:

```bash
olp catalog create $CATALOG_ID $CATALOG_ID --summary "Output catalog for Infer Stop Signs From Sensors example" \
            --description "Output catalog for Infer Stop Signs From Sensors example"
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read --write
```

* Next, add layers to the catalog you have just created:

```bash
olp catalog layer add $CATALOG_HRN stop-signs stop-signs --versioned --summary "Layer for output partitions" \
            --description "Layer for output partitions" --content-type=application/vnd.geo+json \
            --partitioning=heretile:14 --coverage $COVERAGE
```

* Update the output catalog HRN in the pipeline-config.conf file

The `config/here/pipeline-config.conf` (for the HERE OLP environment) and
`config/here-china/pipeline-config.conf` (for the HERE OLP China environment) files contain
the permanent configuration of the data sources for the example.

Pick the file that corresponds to your OLP environment and replace `YOUR_OUTPUT_CATALOG_HRN` placeholder
with the HRN of your Stream Path Matcher catalog.
To find the HRN, in the [OLP Portal](https://platform.here.com/) or the [OLP China
Portal](https://platform.hereolp.cn/), navigate to your catalog. The HRN is displayed in the upper
left corner of page.

## Local Run

We're first going to run the example using a
[Spark pseudo-cluster](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/spark-local.html),
suitable for local development and debugging.

#### Build provided util project

```bash
mvn --also-make --file=../../.. --projects=:utils_2.11 install
```

#### Compile and execute the example

```bash
mvn compile exec:java \
     -Dpipeline-config.file=$PATH_TO_CONFIG_FOLDER/pipeline-config.conf \
     -Dpipeline-job.file=$PATH_TO_CONFIG_FOLDER/pipeline-job.conf \
     -Dspark.master=local[2]
```

In the command above:

- `pipeline-config.conf` defines the `HRN`s of the input and output catalogs,
- `pipeline-job.conf` defines the versions of the input and output catalogs,
- `local[2]` defines the number of threads Spark will run in parallel to
     process the batch.

* Inspect the partitions

```bash
olp catalog layer partition get $CATALOG_HRN stop-signs --all --output="target/stop-signs"
```

## Run on the Platform

The following steps will allow you to run the example on the platform via pipelines.

### Build provided util project

```bash
mvn --also-make --file=../../.. --projects=:utils_2.11 install
```

### Generate a Fat JAR file

Generate a "fat jar" that contains all the dependencies along with the example

```bash
mvn -Pplatform package
```

### Deploy Fat JAR to a Pipeline

You can use the OLP CLI to create pipeline components and activate it, with the following commands:

* Create pipeline components:

```bash
olp pipeline create $COMPONENT_NAME_Pipeline $GROUP_ID
olp pipeline template create $COMPONENT_NAME_Template batch-2.1.0 $PATH_TO_JAR \
                com.here.platform.example.location.scala.spark.InferStopSignsFromSensorsExample $GROUP_ID \
                --input-catalog-ids="$PATH_TO_CONFIG_FOLDER/pipeline-config.conf"
olp pipeline version create $COMPONENT_NAME_version $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
                "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf"
```

* Make sure logs are produced with info level

```bash
olp pipeline version log level set $PIPELINE_ID $PIPELINE_VERSION_ID \
--log4j-properties="$PATH_TO_PROJECT/src/main/resources/log4j.properties"
```

If the operation is successful, you should be able to see the log level you just set:
```sh
olp pipeline version log level get $PIPELINE_ID $PIPELINE_VERSION_ID
```

* Activate the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --input-catalogs "$PATH_TO_CONFIG_FOLDER/pipeline-job.conf"
```

### Inspect the Output Catalog

* Wait until the pipeline version terminates

This allows to wait until the internal job reaches the `completed` state, with a timeout of 30 minutes:

```bash
olp pipeline version wait $PIPELINE_ID $PIPELINE_VERSION_ID --job-state=completed --timeout=1800
```

* Inspect the partitions

By zooming in the `Inspect` tab, you may see the position of stop signs inferred from sensor data:

![Inspecting stop-signs layer](images/stop-signs_output_layer.png)

You may fetch those partitions locally:

```bash
olp catalog layer partition get $CATALOG_HRN stop-signs --all --output="$PATH_TO_PROJECT/target/stop-signs"
```
