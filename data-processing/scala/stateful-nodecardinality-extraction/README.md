# A Stateful Processing Compiler to Process Input and Feedback Data

This Data Processing Library Scala example shows how to build a
[stateful processing compiler](https://developer.here.com/olp/documentation/data-processing-library/content/dev_guide/topics/stateful-processing.html)
that extracts the cardinality of nodes in the "Road Topology & Geometry" layer of HERE Map Content catalog
and how to use the output of the previous compilation as feedback input to count the number of times
the node cardinalities changed for each partition.

The node cardinality refers to the number of segment references for every topology node.
In this example we use the
[Road Topology and Geometry Layer](https://developer.here.com/olp/documentation/here-map-content/topics/topology-layer.html)
that consists of
[Topology Geometry Partitions](https://developer.here.com/olp/documentation/here-map-content/topics_api/com.here.schema.rib.v2.topologygeometrypartition.html)
where each
[Partition](https://developer.here.com/olp/documentation/data-store/data_dev_guide/shared_content/topics/location_platform_data_store/partitions.html#partitions)
contains a set of
[Nodes](https://developer.here.com/olp/documentation/here-map-content/topics_api/com.here.schema.rib.v2.node.html#com.here.schema.rib.v2.node)
.

The compiler in this example is a
[DirectMToNCompiler](https://developer.here.com/olp/documentation/data-processing-library/content/dev_guide/topics/functional-patterns.html)
which:

- takes HERE Map Content input data as well as data from a previous compilation as feedback
- finds the cardinality for each node in a partition
- if the input data has changed since the last compilation, the compiler
updates the cardinality and increments the variable that counts the number of times the compiler has
run

## Set up Access to the Data API

To run the example, you need access to a HERE Map Content catalog, and you need to create one
catalog of your own.

### Create the Stateful Processing Compiler Catalog

The catalog you create is used to store the cardinality of nodes and the number of times the
compiler has run.

Using the OLP Portal, create a new catalog and the following catalog layers:

| Layer ID             | Partitioning | Zoom Level | Layer Type | Content Type             | Schema | Content Encoding |
|----------------------|--------------|------------|------------|--------------------------|--------|------------------|
| nodecardinality-count| HEREtile     | 12         | Versioned  | application/json         | None   | uncompressed     |
| state                | Generic      | N.A.       | Versioned  | application/octet-stream | None   | uncompressed     |

Alternatively, you can use the OLP CLI to create a catalog and the corresponding layers.
In the commands that follow replace the variable placeholders with the following values:
- `$CATALOG_ID` is your output catalog's ID.
- `$CATALOG_HRN` is your output catalog's `HRN` (returned by `olp catalog create`).
- `$GROUP_ID` is the credentials group ID your HERE user belongs to.

* First, create an output catalog and grant the correct permission to your group:

```bash
olp catalog create $CATALOG_ID $CATALOG_ID --summary "Stateful compiler example catalog" \
            --description "Stateful compiler example catalog"
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read --write --manage
```

* Next, add layers to the catalog you have just created:

```bash
olp catalog layer add $CATALOG_HRN nodecardinality-count nodecardinality-count --versioned \
            --summary "nodecardinality count" --description "nodecardinality count" --partitioning heretile:12 --content-type application/json
olp catalog layer add $CATALOG_HRN state state --versioned --summary "state" --description "state" \
            --partitioning Generic --content-type application/octet-stream
```

For more details on how to create a catalog and its layers refer to the
[Data User Guide](https://developer.here.com/olp/documentation/data-user-guide/content/index.html)
, particularly
[Create a Catalog](https://developer.here.com/olp/documentation/data-user-guide/content/portal/catalog-creating.html)
and
[Create a Layer](https://developer.here.com/olp/documentation/data-user-guide/content/portal/layer-creating.html).

### Set up Local Data Access

To run the compiler locally, you also need to create an app key, download the properties file for
that app key to your computer, and grant your app access to your catalog via
[CLI](https://developer.here.com/olp/documentation/open-location-platform-cli/user_guide/topics/data-commands.html#catalog-grant)
or
[UI](https://developer.here.com/olp/documentation/data-user-guide/portal/catalog-sharing.html)
.

For details on how you can obtain the necessary credentials, see
[Get Credentials](https://developer.here.com/olp/documentation/access-control/user-guide/content/topics/get-credentials.html).

## Configure the Compiler

From the SDK examples directory, open the `data-processing/scala/stateful-nodecardinality-extraction` project in
your Integrated Development Environment (IDE).

The `config/here/pipeline-config.conf` (for the HERE OLP environment) and
`config/here-china/pipeline-config.conf` (for the HERE OLP China environment) files contain
the permanent configuration of the data sources for the compiler.

Pick the file that corresponds to your OLP environemnt. For example, the pipeline configuration for
the HERE OLP environment looks like:

```javascript
pipeline.config {
  output-catalog {hrn = "YOUR_OUTPUT_CATALOG_HRN"}
  input-catalogs {
    rib {hrn = "hrn:here:data::olp-here:rib-2"}
  }
}
```

Replace `YOUR_OUTPUT_CATALOG_HRN` with the HRN of your nodecardinality catalog.
To find the HRN, in the [OLP Portal](https://platform.here.com/) or the [OLP China
Portal](https://platform.hereolp.cn/), navigate to your catalog. The HRN is displayed in the upper
left corner of page.

The `config/here/pipeline-job-first.conf` and `config/here/pipeline-second.conf` files contain the compiler's run
configuration and point to two different versions of the HERE Map Content Catalog.

To find the latest version of the HERE Map Content catalog, in the [OLP Portal](https://platform.here.com/)
or the [OLP China Portal](https://platform.hereolp.cn/), navigate to the HERE Map Content catalog;
the current version number is displayed in the upper left corner of the page.

The remainder of the configuration is specified in the `application.conf` found in the
`src/main/resources` directory of the compiler project. But you do not have to modify it unless
you want to change the behavior of the compiler.

## Build the Compiler

To build the compiler, run `mvn install` in the `stateful-nodecardinality-extraction` directory.

## Run the Compiler Locally

To run the compiler locally, you will need to run the entry point to the compiler:

- `com.here.platform.data.processing.example.scala.feedback.Main`

As _arguments_ you must provide the `--master` _parameter_ with the address of the master Spark
server to connect to, and any configuration parameters you want to override. Alternatively, you can
add those parameters to the `application.conf` file.

Additionally, you need to specify the `-Dpipeline-config.file` and `-Dpipeline-job.file`
_parameters_ to specify the location of a configuration file that contains the catalogs as well as
job-specific versions of the catalogs, to read from and write to. If you choose to do so, make sure
you update the layer coverage to reflect your different geographical region.

For local runs, a bounding box filter is provided in the
`config/here/local-application.conf` and `config/here-china/local-application.conf` to
limit the number of partitions to be processed. This speeds up the compilation process. In this
example, we use a bounding box around the city of Berlin and Beijing for the HERE OLP and HERE OLP
China environments respectively. You can edit the bounding box coordinates to compile a different
partition of HERE Map Content. Make sure you update the layer coverage to reflect the different
geographical region. In order to use this configuration file, you need to use the `-Dconfig.file`
parameter.

### Run the Compiler from the Command Line

Finally run the following command line in the
`stateful-nodecardinality-extraction` directory to run The Stateful Processing Compiler.

For the HERE OLP environment:

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.feedback.Main \
-Dpipeline-config.file=./config/here/pipeline-config.conf \
-Dpipeline-job.file=./config/here/pipeline-job-first.conf \
-Dconfig.file=./config/here/local-application.conf \
-Dexec.args="--master local[*]"
```

To observe the behavior of The Stateful Processing Compiler you have to run compiler again using
`pipeline-job-second.conf` as job configuration:

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.feedback.Main \
-Dpipeline-config.file=./config/here/pipeline-config.conf \
-Dpipeline-job.file=./config/here/pipeline-second.conf \
-Dconfig.file=./config/here/local-application.conf \
-Dexec.args="--master local[*]"
```

For the HERE OLP China environment, instead, use the files in the `config/here-china` directory:

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.feedback.Main \
-Dpipeline-config.file=./config/here-china/pipeline-config.conf \
-Dpipeline-job.file=./config/here-china/pipeline-job-first.conf \
-Dconfig.file=./config/here-china/local-application.conf \
-Dexec.args="--master local[*]"
```

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.feedback.Main \
-Dpipeline-config.file=./config/here-china/pipeline-config.conf \
-Dpipeline-job.file=./config/here-china/pipeline-second.conf \
-Dconfig.file=./config/here-china/local-application.conf \
-Dexec.args="--master local[*]"
```

## Run this Compiler as an Open Location Platform Pipeline

### Generate a Fat JAR file:

Run the `mvn -Pplatform package` command in the `stateful-nodecardinality-extraction`
directory to generate a fat JAR file to deploy the compiler to a Pipeline.

```bash
mvn -Pplatform package
```

### Deploy The Compiler to a Pipeline:

Once the previous command is finished, your JAR is then available at the `target` directory, and you
can upload it using the [OLP Pipeline UI](https://platform.here.com/pipelines) (the
[OLP China Pipeline UI](https://platform.hereolp.cn/pipelines) in China)
or the [Open Location Platform CLI](https://developer.here.com/olp/documentation/open-location-platform-cli).

You can use the OLP CLI to create pipeline components and activate it, with the following commands:
* Create pipeline components:

```bash
olp pipeline create $COMPONENT_NAME_Pipeline $GROUP_ID
olp pipeline template create $COMPONENT_NAME_Template batch-2.1.0 $PATH_TO_JAR \
                com.here.platform.data.processing.example.scala.feedback.Main $GROUP_ID \
                --workers=4 --worker-units=3 --supervisor-units=2 --input-catalog-ids=rib
olp pipeline version create $COMPONENT_NAME_version $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
                "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf"
```

* Activate the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --input-catalogs "$PATH_TO_CONFIG_FOLDER/pipeline-job-first.conf"
```

You don't have to specify the input catalog's version, unless you want
to. The latest version will be automatically used.

## Verify the Output

In the [OLP Portal](https://platform.here.com/) / [OLP China Portal](https://platform.hereolp.cn/)
select the _Data_ tab and find your catalog.

- Open the `nodecardinality-count` layer and select the _Partitions_ tab. Verify that partitions
with the JSON data are present and you can view this data by selecting a partition.
- Select any partition to look at its content. The field `updatesCount` will be `1` for those
partitions that did not change during the second run. It will be `2` otherwise.


