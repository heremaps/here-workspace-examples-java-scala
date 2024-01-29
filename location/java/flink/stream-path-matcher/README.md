# Stream Path Matcher

This example shows how to exercise the path matching functionality of the
`Location Library` in a streaming environment.

The example map-matches data coming from the `sample-streaming-layer` that
contains SDII messages. The catalog that contains this layer is defined in the
`sdii-catalog` entry in [`config/here/pipeline-config.conf`](./config/here/pipeline-config.conf). The example writes the
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
- [`HERE Sample SDII Messages - Berlin`](https://platform.here.com/data/hrn:here:data::olp-here:olp-sdii-sample-berlin-2)

## Get Your Credentials

To run this example, you need two sets of credentials:

- **Platform credentials:** To get access to platform data and resources.
- **Repository credentials:** To download HERE Data SDK for Java & Scala libraries and Maven archetypes to your
  environment.

For more details on how to set up your credentials, see
the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more details on how to verify that your platform credentials are configured correctly, see
the [Verify Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/verify-credentials/index.html)
tutorial.

## Build and Run the Compiler

In the commands that follow, replace the variable placeholders with the following values:

- `$PROJECT_HRN` is your project's `HRN` (returned by the `olp project create` command).
- `$COVERAGE` is a two-letter code for country and region (in this case, `DE` for Germany)
- `$INPUT_SDII_CATALOG` is the HRN of the public _sdii-catalog_ catalog in your pipeline
  configuration ([HERE environment](./config/here/local-pipeline-config.conf).
- `$INPUT_OPTIMIZED_MAP_CATALOG` is the HRN of the public _optimized-map-catalog_ catalog in your pipeline
  configuration ([HERE environment](./config/here/local-pipeline-config.conf).

> Note:
> We recommend that you set values to variables, so that you can easily copy and execute the following commands.

### Run the Application Locally

#### Create a Local Output Catalog

As mentioned above we will use two public input catalogs, however we need to create our own output catalog to store the
results of the `Stream Path Matcher` example.

To run this compiler locally, use a local output catalog as described
below. For more information about local catalogs, see
[the SDK tutorial about local development and testing](https://developer.here.com/documentation/java-scala-dev/dev_guide/local-development-workflow/index.html)
and [the OLP CLI documentation](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data-workflows.html).

1. Use
   the [`olp local catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/catalog-commands.html#catalog-create)
   command to create a local catalog.

```bash
olp local catalog create path-matcher-java path-matcher-java --summary "Output catalog for Stream Path Matcher example" \
            --description "Output catalog for Stream Path Matcher example"
```

The local catalog will have the HRN `hrn:local:data:::path-matcher-java`.

2. Use
   the [`olp local catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/layer-commands.html#catalog-layer-add)
   command to add one `stream` layer to your catalog:

```bash
olp local catalog layer add hrn:local:data:::path-matcher-java out-data out-data --stream --summary "Layer for output partitions" \
            --description "Layer for output partitions" --coverage $COVERAGE
```

#### Run the Application from the Command Line

First, we're going to run the example using a
[Flink local environment](https://ci.apache.org/projects/flink/flink-docs-master/dev/local_execution.html),
suitable for local development and debugging.

1. Compile and execute the example.

For the HERE platform environment:

```bash
mvn compile exec:java -Dexec.mainClass=com.here.platform.example.location.java.flink.StreamPathMatcherExample \
    -Dpipeline-config.file=config/here/local-pipeline-config.conf \
    -Dpipeline-job.file=config/here/pipeline-job.conf
```

2. Open a different terminal and let a few partitions stream out of the layer. They consist of small, one-line messages.
   The following command prints `10` messages specified by the `--limit` parameter or messages coming within `900`
   seconds separated by `--delimeter=\\n` parameter.

```bash
olp local catalog layer stream get hrn:local:data:::path-matcher-java out-data --delimiter=\\n --limit=10 --timeout=900
```

The command should return the following results:

```
Result for id 818bad2e-6d18-43ae-9396-86d9a8c9ab84: matched 4 points out of 4
Result for id f319318f-a161-4e65-8017-db0588c5470b: matched 69 points out of 70
Result for id 5a095919-49c1-48ef-8be0-d867d176170b: matched 6 points out of 6
Result for id f99bca1a-18a8-4f3d-81df-7ae7742b68b1: matched 92 points out of 92
Result for id 05aa970f-4222-4708-a2a4-3b20b5943d9c: matched 103 points out of 103
Result for id 5f19487e-0390-458d-8e71-1b0d3c2c6050: matched 96 points out of 98
Result for id e315d58c-f64a-42fe-928d-96851ad8c975: matched 7 points out of 7
Result for id 4cddbdbc-152d-4c14-8c19-0deb077026a5: matched 199 points out of 199
Result for id 6417cb50-267b-4ec3-8256-47d6ea661d1f: matched 69 points out of 70
Result for id f1185134-9af1-4514-b3e3-dae7708f810f: matched 6 points out of 6
Result for id bd69c6c6-4d0b-419d-9ae2-51fb0d7365c7: matched 72 points out of 72
```

3. End stream example process after partitions were retrieved.

### Run this Application as a HERE Platform Pipeline

#### Configure a Project

To follow this example, you will need
a [project](https://developer.here.com/documentation/identity-access-management/dev_guide/topics/manage-projects.html).
A project is a collection of platform resources
(catalogs, pipelines, and schemas) with controlled access. You can create a project through the
platform portal.

Alternatively, use the OLP
CLI [`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project)
command to create the project:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns
the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/index.html) of your
new project. Note down this HRN as you will need it later in this tutorial.

> #### Note
>
> You do not have to provide a `--scope` parameter if your app has a default scope.
> For details on how to set a default project scope for an app, see the _Specify a
> default Project_ for Apps chapter of
> the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more information on how to work with projects, see
the [Organize your work in projects](https://developer.here.com/documentation/java-scala-dev/dev_guide/organize-work-in-projects/index.html)
tutorial.

#### Create an Output Catalog

The catalog you need to create is used to store the results of the `Stream Path Matcher` example.

Use the [HERE platform portal](https://platform.here.com/)
to [create the output catalog](https://developer.here.com/documentation/data-user-guide/user_guide/portal/catalog-creating.html)
in your project
and [add the following layers](https://developer.here.com/documentation/data-user-guide/user_guide/portal/layer-creating.html):

| Layer ID | Layer Type | Content Type           | Content Encoding | Coverage |
| -------- | ---------- | ---------------------- | ---------------- | -------- |
| out-data | Stream     | application/x-protobuf | uncompressed     | DE       |

Alternatively, you can use the OLP CLI to create the catalog and the corresponding layers.

1. Use
   the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create)
   command to create the catalog.
   Make sure to note down the HRN returned by the following command for later use:

```bash
olp catalog create $CATALOG_ID $CATALOG_ID --summary "Output catalog for Stream Path Matcher example" \
            --description "Output catalog for Stream Path Matcher example" \
            --scope $PROJECT_HRN
```

2. Use
   the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add)
   command to add a stream layer to your catalog:

```bash
olp catalog layer add $CATALOG_HRN out-data out-data --stream --summary "Layer for output partitions" \
            --description "Layer for output partitions" --coverage $COVERAGE \
            --scope $PROJECT_HRN
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tags: "YOUR_BILLING_TAG"` parameter.

3. Update the output catalog HRN in the `pipeline-config.conf` file

The `config/here/pipeline-config.conf` (for the HERE platform environment) file contains
the permanent configuration of the data sources for the example.

Pick the file that corresponds to your platform environment and replace `YOUR_OUTPUT_CATALOG_HRN` placeholder
with the HRN of your Stream Path Matcher catalog.

To find the HRN, in the [HERE platform portal](https://platform.here.com/), navigate to your catalog. The HRN is
displayed in the upper
left corner of the page.

4. Use
   the [`olp project resource link`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-link-commands.html#project-resource-link)
   command to link the _HERE Sample SDII Messages - Berlin_ and _HERE Optimized Map for Location Library_ catalog to
   your project.

```bash
olp project resource link $PROJECT_HRN $INPUT_SDII_CATALOG
olp project resource link $PROJECT_HRN $INPUT_OPTIMIZED_MAP_CATALOG
```

- For more details on catalog commands,
  see [Catalog Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html).
- For more details on layer commands,
  see [Layer Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html).
- For more details on project commands,
  see [Project Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html).
- For instructions on how to link a resource to a project,
  see [Project Resource Link command](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-link-commands.html#project-resource-link).

#### Generate a Fat JAR file

Generate a "fat jar" for `StreamPathMatcherExample` that will be sent to the platform later

```bash
mvn -Pplatform package
```

#### Deploy Fat JAR to a Pipeline

You can use the OLP CLI to create pipeline components and activate the pipeline version with the following commands:

1. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline-workflows.html)
   pipeline components:

```bash
olp pipeline create $COMPONENT_NAME_Pipeline --scope $PROJECT_HRN
olp pipeline template create $COMPONENT_NAME_Template stream-5.0 $PATH_TO_JAR \
                com.here.platform.example.location.java.flink.StreamPathMatcherExample \
                --input-catalog-ids="$PATH_TO_CONFIG_FOLDER/pipeline-config.conf" \
                --scope $PROJECT_HRN
olp pipeline version create $COMPONENT_NAME_version $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
                "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf" \
                --scope $PROJECT_HRN
```

- Make sure logs are produced with info level

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

2. [Activate](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate)
   the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --input-catalogs "$PATH_TO_CONFIG_FOLDER/pipeline-job.conf" --scope $PROJECT_HRN
```

Since this is a Flink application, this means that it runs until you stop it.
In order to stop the application after you have checked its operation, execute the following command:

```
olp pipeline version cancel $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

#### Let a few partitions stream out of the layer

Partitions consist of small, one-line messages.

The following command prints `10` messages specified by the `--limit` parameter or messages coming within `900` seconds
separated by `--delimeter=\\n` parameter.

```bash
olp catalog layer stream get $CATALOG_HRN out-data --delimiter=\\n --limit=10 --timeout=900 --scope $PROJECT_HRN
```

The command should return the following results:

```
Result for id 818bad2e-6d18-43ae-9396-86d9a8c9ab84: matched 4 points out of 4
Result for id f319318f-a161-4e65-8017-db0588c5470b: matched 69 points out of 70
Result for id 5a095919-49c1-48ef-8be0-d867d176170b: matched 6 points out of 6
Result for id f99bca1a-18a8-4f3d-81df-7ae7742b68b1: matched 92 points out of 92
Result for id 05aa970f-4222-4708-a2a4-3b20b5943d9c: matched 103 points out of 103
Result for id 5f19487e-0390-458d-8e71-1b0d3c2c6050: matched 96 points out of 98
Result for id e315d58c-f64a-42fe-928d-96851ad8c975: matched 7 points out of 7
Result for id 4cddbdbc-152d-4c14-8c19-0deb077026a5: matched 199 points out of 199
Result for id 6417cb50-267b-4ec3-8256-47d6ea661d1f: matched 69 points out of 70
Result for id f1185134-9af1-4514-b3e3-dae7708f810f: matched 6 points out of 6
Result for id bd69c6c6-4d0b-419d-9ae2-51fb0d7365c7: matched 72 points out of 72
```
