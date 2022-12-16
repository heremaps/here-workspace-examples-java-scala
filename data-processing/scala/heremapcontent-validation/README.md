# An Incremental Validation Pipeline to Verify Road Topology content with Scalatest DSL

This example shows how to use the Data Processing Library's validation module to build an
incremental processing pipeline that validates the content of HERE Map Content's `topology-geometry`
layer against a set of acceptance criteria. In particular, we validate the following invariants:

- A Node's geometry [must be contained in its host tile's boundaries](https://developer.here.com/documentation/here-map-content/dev_guide/topics/geometry-tile-assignment-model.html).
- A Node must be attached to [at least one Segment](https://developer.here.com/documentation/here-map-content/dev_guide/topics/topology-geometry-model.html).
- A Node must not have dangling references to non-existent Segments.
- A Segment must not have dangling references to non-existent Nodes.
- A Segment's start Node [must be in its host tile](https://developer.here.com/documentation/here-map-content/dev_guide/topics/geometry-tile-assignment-model.html).
- A Segment's Shape Points should [go from the start Node to the end Node](https://developer.here.com/documentation/here-map-content/dev_guide/topics/topology-geometry-model.html).

Finally, we check that the `partitionName` field of a `topology-geometry` partition equals the
actual HERE Tile ID of the partition.

As these checks are carried out, we keep track of the following metric values, which are
automatically aggregated together with the test results:

- Number of Nodes per tile.
- Number of Segments per tile.
- Number of Segments attached to each Node.

To implement these tests we use a scalatest suite, parametrized on the content of one
`topology-geometry` partition and of all the referenced neighbour partitions, using the Data
Processing Library's `batch-validation-scalatest` module.

The validation module automatically encodes the test results and publishes them in a validation
report catalog, which consists of the following layers:

- `report` contains the report of the failed tests (succeeded tests are by default filtered out
  to simplify debugging and identification of data issues, and to reduce the amount of
  published data). This layer will be empty if there are no failed tests.
- `metrics` contains statistics about all test cases run in the suite, and the accumulated values of
  the custom accumulators specified in the suite. Partitions are first published at the
  input zoom level (12) and then incrementally and recursively aggregated at higher zoom
  levels - by default using all the zoom levels configured in the layer up to level 0 (the
  root partition). In this example we use levels 12, 10, 8, 6, 4, 2 and 0.
- `assessment` contains the final assessment partition. This layer will contain one single
  partition, encoding our custom assessment class. In this example we simply assess
  that no test cases have failed.

## Get Your Credentials

To run this example, you need two sets of credentials:

- **Platform credentials:** To get access to the platform data and resources, including HERE Map Content data for your pipeline input.
- **Repository credentials:** To download HERE Data SDK for Java & Scala libraries and Maven archetypes to your environment.

For more details on how to set up your credentials, see the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more details on how to verify that your platform credentials are configured correctly, see the [Verify Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/verify-credentials/index.html) tutorial.

## Build and Run the Compiler

In the commands that follow, replace the variable placeholders with the following values:

- `$CATALOG_ID` is your output catalog's ID.
- `$CATALOG_HRN` is your output catalog's `HRN` (returned by `olp catalog create`).
- `$HRN_PARTITION` is the platform environment you are in. Specify `here` unless you are
  using the HERE platform China environment, in which case specify `here-cn`.
- `$PROJECT_HRN` is your project's `HRN` (returned by `olp project create` command).
- `$REALM` The ID of your organization, also called a realm. Consult your platform
  invitation letter to learn your organization ID.
- `$CATALOG_RIB` is the HRN of the public _HERE Map Content_ catalog in your pipeline configuration ([HERE environment](./config/here/pipeline-config.conf) or [HERE China environment](./config/here-china/pipeline-config.conf)).

> Note:
> We recommend you to set values to variables so that you can easily copy and execute the following commands.

### Run the Compiler Locally

#### Create the Local Validation Report Catalog

To run this compiler locally it is recommended to use a local output catalog, by following the steps
below. If you want to know more about local catalogs, see
[the SDK tutorial about local development and testing](https://developer.here.com/documentation/java-scala-dev/dev_guide/local-development-workflow/index.html)
and [the OLP CLI documentation](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data-workflows.html).

1. Use the [`olp local catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/catalog-commands.html#catalog-create)
   command to create the local catalog.

```bash
olp local catalog create heremapcontent-validation "HERE Map Content - Topology and Geometry Validation" \
            --summary "Validation of HERE Map Content Topology and Geometry layer" \
            --description "Validation of HERE Map Content Topology and Geometry layer"
```

The local catalog will have the HRN `hrn:local:data:::heremapcontent-validation`.

2. Use the [`olp local catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/local-data/layer-commands.html#catalog-layer-add)
   command to add four `versioned` layers to your catalog:

```bash
olp local catalog layer add hrn:local:data:::heremapcontent-validation report report --versioned \
            --summary "Test report" \
            --description "Test report" --partitioning heretile:12 \
            --schema hrn:$HRN_PARTITION:schema:::com.here.platform.data.processing.validation.schema:report_v2:1.0.0 \
            --content-type application/json
olp local catalog layer add hrn:local:data:::heremapcontent-validation metrics metrics --versioned \
            --summary "Test metrics" \
            --description "Test metrics" --partitioning heretile:12,10,8,6,4,2,0 \
            --schema hrn:$HRN_PARTITION:schema:::com.here.platform.data.processing.validation.schema:metrics_v2:1.0.0 \
            --content-type application/json
olp local catalog layer add hrn:local:data:::heremapcontent-validation assessment assessment --versioned \
            --summary "Test assessment" \
            --description "Test assessment" --partitioning generic \
            --content-type application/json
olp local catalog layer add hrn:local:data:::heremapcontent-validation state state --versioned \
            --summary "state" \
            --description "state" --partitioning generic \
            --content-type application/octet-stream
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tags: "YOUR_BILLING_TAG"` parameter.

#### Run the Compiler from the Command Line

To run the compiler locally, you will need to run the entry point to the compiler:

- `com.here.platform.data.processing.example.scala.validation.Main`

As _arguments_, you must provide the `-Dspark.master=local[*]` _parameter_ with the address of the Spark server
master to connect to, and any configuration parameters you want to override. Alternatively, you can
add those parameters to the `application.conf` file.

Additionally, you also need to specify the `-Dpipeline-config.file` and `-Dpipeline-job.file`
_parameters_ to specify the location of a configuration file that contains the catalogs as well as
job-specific versions of the catalogs, to read and write to.

For local runs, a bounding box filter is provided in the
`config/here/local-application.conf` and `config/here-china/local-application.conf` to
limit the number of partitions to be processed. This speeds up the compilation process. In this
example, we use a bounding box around the cities of Berlin and Beijing for the HERE platform and HERE
platform China environments respectively. You can edit the bounding box coordinates to compile a different
partition of HERE Map Content. Make sure you update the layer coverage to reflect the different
geographical region. In order to use this configuration file, you need to use the `-Dconfig.file`
parameter.

Finally, run the following command line in the `heremapcontent-validation` directory to compile and
run the validation pipeline.

For the HERE platform environment:

```bash
mvn compile exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.validation.Main \
-Dpipeline-config.file=./config/here/local-pipeline-config.conf \
-Dpipeline-job.file=./config/here/pipeline-job.conf \
-Dconfig.file=./config/here/local-application.conf \
-Dspark.master=local[*]
```

For the HERE platform China environment:

```
mvn compile exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.validation.Main \
-Dpipeline-config.file=./config/here-china/local-pipeline-config.conf \
-Dpipeline-job.file=./config/here-china/pipeline-job.conf \
-Dconfig.file=./config/here-china/local-application.conf \
-Dspark.master=local[*]
```

After one run, in the HERE platform environment you can inspect the local catalog with the OLP CLI:

```
olp local catalog inspect hrn:local:data:::heremapcontent-validation
```

The `local inspect` command is not available in the HERE platform China environment, but you can
download partitions from the local catalog to manually inspect them. The command below, for example,
downloads the aggregated metrics containing all statistics about the validation run. Metrics are
aggregated at different zoom levels, and the root partition (HERE Tile ID 1) contains the
aggregation of all metrics partitions:

```bash
olp local catalog layer partition get hrn:local:data:::heremapcontent-validation metrics --partitions 1
```

The `report` layer will be likely empty, because no test cases have failed. To force some failures
and check how the output would change in that case, search for `[inject]` in `Main.scala` and
uncomment the line that follows:

```scala
    val (resultsAndMetricsPublishedSet, aggregatedMetrics) = testData
    // [inject] Uncomment the following line to inject some "dangling reference" errors
    // .mapValues(ErrorInjection.injectError)
      .mapValues(suiteCompiler.compile)
      .publishAndAggregateByLevel(suiteCompiler.outLayers, suiteCompiler.metricsLayer)
```

### Run this Compiler as a HERE Platform Pipeline

#### Configure a Project

To run the example as a HERE Platform Pipeline, you'll first need a
[project](https://developer.here.com/documentation/identity-access-management/dev_guide/topics/manage-projects.html).
A project is a collection of platform resources (catalogs, pipelines, and schemas) with controlled
access. You can create a project through the **HERE platform portal**.

Alternatively, use the OLP CLI
[`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project) command to create the project:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/shared_content/topics/olp/concepts/hrn.html) of your new project. Note down this HRN as you'll need it later in this tutorial.

> Note:
> You don't have to provide a `--scope` parameter if your app has a default scope.
> For details on how to set a default project scope for an app, see the _Specify a
> default Project_ for Apps chapter of the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more information on how to work with projects, see the [Organize your work in projects](https://developer.here.com/documentation/java-scala-dev/dev_guide/organize-work-in-projects/index.html) tutorial.

#### Create the Validation Report Catalog

Create a catalog to store the validation reports and metrics generated by the compiler. You will use
the non-local variants of the same commands you have run before to create a local catalog:

1. Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create the catalog.
   Make sure to note down the HRN returned by the following command for later use:

```bash
olp catalog create $CATALOG_ID "HERE Map Content - Topology and Geometry Validation" \
            --summary "Validation of HERE Map Content Topology and Geometry layer" \
            --description "Validation of HERE Map Content Topology and Geometry layer" \
            --scope $PROJECT_HRN
```

2. Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add) command to add two `versioned` layers to your catalog:

```bash
olp catalog layer add $CATALOG_HRN report report --versioned \
            --summary "Test report" \
            --description "Test report" --partitioning heretile:12 \
            --schema hrn:$HRN_PARTITION:schema::$REALM:com.here.platform.data.processing.validation.schema:report_v2:1.0.0 \
            --content-type application/json --scope $PROJECT_HRN
olp catalog layer add $CATALOG_HRN metrics metrics --versioned \
            --summary "Test metrics" \
            --description "Test metrics" --partitioning heretile:12,10,8,6,4,2,0 \
            --schema hrn:$HRN_PARTITION:schema::$REALM:com.here.platform.data.processing.validation.schema:metrics_v2:1.0.0 \
            --content-type application/json --scope $PROJECT_HRN
olp catalog layer add $CATALOG_HRN assessment assessment --versioned \
            --summary "Test assessment" \
            --description "Test assessment" --partitioning generic \
            --content-type application/json --scope $PROJECT_HRN
olp catalog layer add $CATALOG_HRN state state --versioned \
            --summary "state" \
            --description "state" --partitioning generic \
            --content-type application/octet-stream --scope $PROJECT_HRN
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tags: "YOUR_BILLING_TAG"` parameter.

3. Use the [`olp project resource link`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-link-commands.html#project-resource-link) command to link the _HERE Map Content_ catalog to your project:

```bash
olp project resource link $PROJECT_HRN $CATALOG_RIB
```

- For more details on catalog commands, see [Catalog Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html).
- For more details on layer commands, see [Layer Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html).
- For more details on project commands, see [Project Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html).
- For instructions on how to link a resource to a project, see [Project Resource Link command](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-link-commands.html#project-resource-link).

#### Configure the Compiler

From the SDK examples directory, open the `data-processing/scala/heremapcontent-validation` project
in your Integrated Development Environment (IDE).

The `config/here/pipeline-config.conf` (for the HERE platform environment) and
`config/here-china/pipeline-config.conf` (for the HERE platform China environment) files contain
the permanent configuration of the data sources for the compiler.

Pick the file that corresponds to your platform environment. For example, the pipeline configuration for
the HERE platform environment looks like:

```javascript
pipeline.config {
  output-catalog {hrn = "YOUR_OUTPUT_CATALOG_HRN"}
  input-catalogs {
    hmc {hrn = "hrn:here:data::olp-here:rib-2"}
  }
}
```

Replace `YOUR_OUTPUT_CATALOG_HRN` with the HRN of your validation catalog.
To find the HRN, in the [HERE platform portal](https://platform.here.com/) or the
[HERE platform China portal](https://platform.hereolp.cn/), navigate to your catalog. The HRN is displayed in the upper
left corner of page.

The remainder of the configuration is specified in the `application.conf` file that can be found in the
`src/main/resources` directory of the compiler project. However, you do not have to modify it unless
you want to change the behavior of the compiler.

#### Generate a Fat JAR file:

Run the `mvn -Pplatform package` command in the `heremapcontent-validation` directory
to generate a fat JAR file to deploy the compiler to a pipeline.

```bash
mvn -Pplatform package
```

#### Deploy the Compiler to a Pipeline:

Once the previous command is finished, your JAR is then available at the `target` directory, and you
can upload it using the [HERE pipelines UI](https://platform.here.com/pipelines) (the
[HERE China pipelines UI](https://platform.hereolp.cn/pipelines) in China)
or the [OLP CLI](https://developer.here.com/documentation/open-location-platform-cli).

You can use the OLP CLI to create pipeline components and activate the pipeline version with the following commands:

1. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline-workflows.html) pipeline components:

```bash
olp pipeline create $COMPONENT_NAME_Pipeline --scope $PROJECT_HRN
olp pipeline template create $COMPONENT_NAME_Template batch-3.0 $PATH_TO_JAR \
                com.here.platform.data.processing.example.scala.validation.Main \
                --workers=4 --worker-units=3 --supervisor-units=2 --input-catalog-ids=hmc \
                --scope $PROJECT_HRN
olp pipeline version create $COMPONENT_NAME_version $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
                "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf" \
                --scope $PROJECT_HRN
```

2. [Activate](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate) the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID \
                --input-catalogs "$PATH_TO_CONFIG_FOLDER/pipeline-job.conf" \
                --scope $PROJECT_HRN
```

You don't have to specify the input catalog's version, unless you want
to. The latest version will be automatically used.

In the [HERE platform portal](https://platform.here.com/pipelines) / [HERE platform China portal](https://platform.hereolp.cn/pipelines)
navigate to your pipeline to see its status.

## Verify the Output

In the [HERE platform portal](https://platform.here.com/) / [HERE platform China portal](https://platform.hereolp.cn/)
select the _Data_ tab and find your catalog.

1. Open the `assessment` layer and select the _Inspect_ tab.
2. Select the `assessment` partition to view the result of the assessment.
