# Stream Path Matcher

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

## Get Your Credentials

To run this example, you need two sets of credentials:

* **Platform credentials:** To get access to platform data and resources, including HERE Map Content data for your pipeline input. 
* **Repository credentials:** To download HERE Data SDK for Java & Scala libraries and Maven archetypes to your environment.

For more details on how to set up your credentials, see [Get Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/topics/get-credentials.html).

For more details on how to verify that your platform credentials are configured correctly, see the [Verify Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/verify-credentials/index.html) tutorial.

## Configure a Project

To follow this example, you'll need a [project](https://developer.here.com/documentation/access-control/user_guide/topics/manage-projects.html). A project is a collection of platform resources
(catalogs, pipelines, and schemas) with controlled access. You can create a project through the
platform portal.
 
Alternatively, use the OLP CLI [`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project) command to create the project:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/shared_content/topics/olp/concepts/hrn.html) of your new project. Note down this HRN as you'll need it later in this tutorial.

> Note:
> You don't have to provide a `--scope` parameter if your app has a default scope.
> For details on how to set a default project scope for an app, see the _Specify a
> default Project_ for Apps chapter of the [Teams and Permissions Guide](https://developer.here.com/documentation/access-control/user_guide/topics/manage-projects.html).

For more information on how to work with projects, see the [Organize your work in projects](https://developer.here.com/documentation/java-scala-dev/dev_guide/organize-work-in-projects/index.html) tutorial.

## Create Output Catalog

The catalog you need to create is used to store the results of the `Stream Path Matcher` example.

Use the HERE platform portal to [create the output catalog](https://developer.here.com/documentation/data-user-guide/user_guide/portal/catalog-creating.html) in your project and [add the following layers](https://developer.here.com/documentation/data-user-guide/user_guide/portal/layer-creating.html):

| Layer ID                 | Layer Type | Content Type           | Content Encoding | Coverage |
|--------------------------|------------|------------------------|------------------|----------|
| out-data                 | Stream     | application/x-protobuf | uncompressed     | DE or CN |

Alternatively, you can use the OLP CLI to create the catalog and the corresponding layers.

In the commands that follow, replace the variable placeholders with the following values:
- `$CATALOG_ID` is your output catalog's ID.
- `$CATALOG_HRN` is your output catalog's `HRN` (returned by `olp catalog create`).
- `$PROJECT_HRN` is your project's `HRN` (returned by `olp project create` command).
- `$COVERAGE` is a two-letter code for country and region (in this case `DE` or `CN` for China)
- `$INPUT_SDII_CATALOG` is the HRN of the public _sdii-catalog_ catalog in your pipeline configuration ([HERE environment](./config/here/pipeline-config.conf) or [HERE China environment](./config/here-china/pipeline-config.conf)).
- `$INPUT_OPTIMIZED_MAP_CATALOG` is the HRN of the public _optimized-map-catalog_ catalog in your pipeline configuration ([HERE environment](./config/here/pipeline-config.conf) or [HERE China environment](./config/here-china/pipeline-config.conf)).

> Note:
> We recommend you to set values to variables so that you can easily copy and execute the following commands.

1. Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create the catalog.
  Make sure to note down the HRN returned by the following command for later use:

```bash
olp catalog create $CATALOG_ID $CATALOG_ID --summary "Output catalog for Stream Path Matcher example" \
            --description "Output catalog for Stream Path Matcher example" \
            --scope $PROJECT_HRN
```

2. Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add) command to add a stream layer to your catalog:

```bash
olp catalog layer add $CATALOG_HRN out-data out-data --stream --summary "Layer for output partitions" \
            --description "Layer for output partitions" --coverage $COVERAGE \
            --scope $PROJECT_HRN
```

3. Update the output catalog HRN in the `pipeline-config.conf` file

The `config/here/pipeline-config.conf` (for the HERE platform environment) and
`config/here-china/pipeline-config.conf` (for the HERE platform China environment) files contain
the permanent configuration of the data sources for the example.

Pick the file that corresponds to your platform environment and replace `YOUR_OUTPUT_CATALOG_HRN` placeholder
with the HRN of your Stream Path Matcher catalog.

To find the HRN, in the [HERE platform portal](https://platform.here.com/) or the [HERE platform China 
portal](https://platform.hereolp.cn/), navigate to your catalog. The HRN is displayed in the upper
left corner of page.

4. Use the [`olp project resources link`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-resources-commands.html#project-resources-link) command to link the _HERE Sample SDII Messages - Berlin_ and _HERE Optimized Map for Location Library_ catalog to your project.

```bash
olp project resources link $PROJECT_HRN $INPUT_SDII_CATALOG
olp project resources link $PROJECT_HRN $INPUT_OPTIMIZED_MAP_CATALOG
```

- For more details on catalog commands, see [Catalog Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html).
- For more details on layer commands, see [Layer Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html).
- For more details on project commands, see [Project Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html).
- For instructions on how to link a resource to a project, see [Project Resources Link command](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-resources-commands.html#project-resources-link).

## Local Run

We're first going to run the example using a
[Flink local environment](https://ci.apache.org/projects/flink/flink-docs-master/dev/local_execution.html),
suitable for local development and debugging.

1. Compile and execute the example

```bash
mvn --projects=:java-flink-stream-path-matcher compile exec:java \
    -Dpipeline-config.file=$PATH_TO_CONFIG_FOLDER/pipeline-config.conf \
    -Dpipeline-job.file=$PATH_TO_CONFIG_FOLDER/pipeline-job.conf \
    -Dhere.platform.data-client.request-signer.credentials.here-account.here-token-scope=$PROJECT_HRN
```

2. Open different terminal and let a few partitions stream out of the layer, they consist of small, one-line messages

```bash
olp catalog layer stream get $CATALOG_HRN out-data --delimiter=\\n --limit=42 --timeout=900 --scope $PROJECT_HRN
```

3. End stream example process after partitions were retrieved

## Run on the Platform

The following steps allow you to run the `StreamPathMatcherExample` pipeline on the platform.

### Generate a Fat JAR file
Generate a "fat jar" for `StreamPathMatcherExample` that will be sent to the platform later

```bash
mvn -Pplatform package
```

### Deploy Fat JAR to a Pipeline

You can use the OLP CLI to create pipeline components and activate the pipeline version with the following commands:

1. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline-workflows.html) pipeline components:

```bash
olp pipeline create $COMPONENT_NAME_Pipeline --scope $PROJECT_HRN
olp pipeline template create $COMPONENT_NAME_Template stream-3.0.0 $PATH_TO_JAR \
                com.here.platform.example.location.java.flink.StreamPathMatcherExample \
                --input-catalog-ids="$PATH_TO_CONFIG_FOLDER/pipeline-config.conf" \
                --scope $PROJECT_HRN
olp pipeline version create $COMPONENT_NAME_version $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
                "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf" \
                --scope $PROJECT_HRN
```

* Make sure logs are produced with info level

```bash
olp pipeline version log level set $PIPELINE_ID $PIPELINE_VERSION_ID \
--log4j-properties="$PATH_TO_PROJECT/src/main/resources/log4j.properties" \
--scope $PROJECT_HRN
```
If the operation is successful, you should be able to see the log level you just set:
```sh
olp pipeline version log level get $PIPELINE_ID $PIPELINE_VERSION_ID \
--scope $PROJECT_HRN
```

2. [Activate](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate) the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --input-catalogs "$PATH_TO_CONFIG_FOLDER/pipeline-job.conf" --scope $PROJECT_HRN
```

### Let a few partitions stream out of the layer

Partitions consist of small, one-line messages.

```bash
olp catalog layer stream get $CATALOG_HRN out-data --delimiter=\\n --limit=42 --timeout=900 --scope $PROJECT_HRN
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
olp pipeline version cancel $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
 ```
