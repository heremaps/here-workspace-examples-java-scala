# Stream Path Matcher Example

This example shows how to exercise the path matching functionality of the
`Location Library` in a streaming environment.

The example map-matches data coming from the `sample-streaming-layer` that
contains SDII messages. The catalog that contains this layer is defined in the
`sdii-catalog` entry in `config/here/pipeline-config.conf` or
`config/here-china/pipeline-config.conf` for China. The example writes the
result of the computation to the `out-data` streaming layer in the catalog you
will create.

The `StreamPathMatcherExample` class does the following:

- Get the Flink `StreamExecutionEnvironment`
- Add a `SdiiMessageMapFunction`, the Flink `SourceFunction` that provides data
  from the input streaming layer and deserializes it
- Add a `PathMatcherMapFunction`, the Flink `SourceFunction` that does the path
  matching
- Put the result descriptions into the catalog you will create, as well as print
  them to the standard output

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

The catalog you need to create is used to store the results of `Stream Path Matcher` example.

Using the OLP Portal, create a new catalog and the following catalog layer:

| Layer ID                 | Layer Type | Content Type           | Content Encoding | Coverage |
|--------------------------|------------|------------------------|------------------|----------|
| out-data                 | Stream     | application/x-protobuf | uncompressed     | DE or CN |

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
olp catalog create $CATALOG_ID $CATALOG_ID --summary "Output catalog for Stream Path Matcher example" \
            --description "Output catalog for Stream Path Matcher example"
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read --write
```

* Next, add layers to the catalog you have just created:

```bash
olp catalog layer add $CATALOG_HRN out-data out-data --stream --summary "Layer for output partitions" \
            --description "Layer for output partitions" --coverage $COVERAGE
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
[Flink local environment](https://ci.apache.org/projects/flink/flink-docs-master/dev/local_execution.html),
suitable for local development and debugging.

* Compile and execute the example

```bash
mvn --projects=:java-flink-stream-path-matcher compile exec:java \
    -Dpipeline-config.file=$PATH_TO_CONFIG_FOLDER/pipeline-config.conf \
    -Dpipeline-job.file=$PATH_TO_CONFIG_FOLDER/pipeline-job.conf
```

* Open different terminal and let a few partitions stream out of the layer, they consist of small, one-line messages

```bash
olp catalog layer stream get $CATALOG_HRN out-data --delimiter=\\n --limit=42 --timeout=900
```

* End stream example process after partitions were retrieved

## Run on the Platform

The following steps allow you to run the `StreamPathMatcherExample` pipeline on the platform.

### Generate a Fat JAR file
Generate a "fat jar" for `StreamPathMatcherExample` that will be sent to the platform later

```bash
mvn -Pplatform package
```

### Deploy Fat JAR to a Pipeline

You can use the OLP CLI to create pipeline components and activate it, with the following commands:

* Create pipeline components:

```bash
olp pipeline create $COMPONENT_NAME_Pipeline $GROUP_ID
olp pipeline template create $COMPONENT_NAME_Template stream-2.0.0 $PATH_TO_JAR \
                com.here.platform.example.location.java.flink.StreamPathMatcherExample $GROUP_ID \
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

### Let a few partitions stream out of the layer

Partitions consist of small, one-line messages.

```bash
olp catalog layer stream get $CATALOG_HRN out-data --delimiter=\\n --limit=42 --timeout=900
```

You can now monitor the performance of your pipeline job by creating a custom graph on Grafana.
To do this you can use the following query in a Grafana panel specifying your pipeline ID:

```
flink_taskmanager_job_task_operator_numRecordsOutPerSecond{operator_name="mapmatch_sdii_message",pipelineId="$pipeline_id"}
```

The resulting graph represents the number of SDII messages that are map matched per second.

### Cancel the pipeline version

After partition inspection, cancel the stream pipeline.

```bash
olp pipeline version cancel $PIPELINE_ID $PIPELINE_VERSION_ID
 ```
