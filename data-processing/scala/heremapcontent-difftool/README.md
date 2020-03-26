# Example - A Compiler to Compute Catalog Differences

This Data Processing Library Scala example shows how to use the Open Location Platform SDK
to build a compiler pipeline that computes the difference between two versions of an input catalog
and outputs the difference is JSON format.

The `topology-geometry` layer in the HERE Map Content catalog contains, among others, road topology
segments with unique identifiers, as well as their geometry in the form of coordinate sequences.
This compiler loads two different versions of the layer and outputs a JSON document containing all
identifiers of segments that were added, removed or modified between the two versions.

The two versions accessed by the compiler are always:
1. The catalog version specified in the pipeline job configuration file. This can be, for example,
   the latest version of the catalog.
2. The catalog version specified in the pipeline job configuration _in the previous run_ of the
   compiler.

For example, if the compiler was run for the first time on version 1 of the input catalog and is
subsequently run on version 3 of the same catalog, the result of the second run is the difference
between version 1 and version 3 of the input catalog. In the first run of the compiler, since there
is no previous run, the version specified in the pipeline job configuration file is compared to an
empty catalog.

## Set up Access to the Data API

To run the example, you need access to a HERE Map Content catalog, and you need to create one
catalog of your own. For more information, refer to the [Open Location Platform
SDK](https://developer.here.com/olp/documentation/sdk-developer-guide/content/dev_guide/index.html)
and [Portal](https://developer.here.com/olp/documentation) documentation.

### Create the HERE Map Content Differences Catalog

The catalog you need to create is used to store the differences between two versions of the HERE Map
Content catalog.

Using the OLP Portal, create a new catalog and the following catalog layers:

| Layer ID               | Layer Type | Partitioning | Zoom Level | Content Type             | Content Encoding |
|------------------------|------------|--------------|------------|--------------------------|------------------|
| topology-geometry-diff | Versioned  | HEREtile     | 12         | application/json         | uncompressed     |
| state                  | Versioned  | Generic      | N.A.       | application/octet-stream | uncompressed     |

Alternatively, you can use the OLP CLI to create a catalog and the corresponding layers.
In the commands that follow replace the variable placeholders with the following values:
- `$CATALOG_ID` is your output catalog's ID.
- `$CATALOG_HRN` is your output catalog's `HRN` (returned by `olp catalog create`).
- `$GROUP_ID` is the credentials group ID your HERE user belongs to.

* First, create an output catalog and grant the correct permission to your group:

```bash
olp catalog create $CATALOG_ID $CATALOG_ID --summary "HERE Map Content diftool example catalog" \
            --description "HERE Map Content diftool example catalog"
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read --write --manage
```

* Next, add layers to the catalog you have just created:

```bash
olp catalog layer add $CATALOG_HRN topology-geometry-diff topology-geometry-diff --versioned \
            --summary "diff" --description  "diff" --partitioning heretile:12 --content-type application/json
olp catalog layer add $CATALOG_HRN state state --versioned --summary "state" --description "state" \
            --partitioning Generic --content-type application/octet-stream
```

For more details on how to create a catalog and its layers refer to the [Data User
Guide](https://developer.here.com/olp/documentation/data-user-guide/content/index.html),
particularly [Create a
Catalog](https://developer.here.com/olp/documentation/data-user-guide/content/portal/catalog-creating.html)
and [Create a
Layer](https://developer.here.com/olp/documentation/data-user-guide/content/portal/layer-creating.html).

### Set up Local Data Access

To run the compiler locally, you also need to create an app key, download the properties file for
that app key to your computer, and grant your app access to your catalog.

For details on how you can obtain the necessary credentials, see [Get
Credentials](https://developer.here.com/olp/documentation/access-control/user-guide/content/topics/get-credentials.html).

## Configure the Compiler

From the SDK examples directory, open the `data-processing/scala/heremapcontent-difftool` project in your
Integrated Development Environment (IDE).

### Pipeline Configuration

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

Replace `YOUR_OUTPUT_CATALOG_HRN` with the HRN of your HERE Map Content differences
catalog. To find the HRN, in the [OLP Portal](https://platform.here.com/) or the [OLP China
Portal](https://platform.hereolp.cn/), navigate to your catalog; the HRN is displayed in the upper
left corner of the page.

### Pipeline Job Configuration

The pipeline job configuration file defines on which version of the input catalog to run the
compiler on.

The example project provides two template job configurations, `config/here/pipeline-job-first.conf` and
`config/here/pipeline-job-second.conf` for the first and second run of the pipeline, respectively.
If you are using the OLP China environment use the files `config/here-chine/pipeline-job-first.conf`
and `config/here-china/pipeline-job-second.conf` instead.

`pipeline-job-first.conf` specifies in the line `version = 1` that the version 1 of the input
catalog should be processed in the first run. You can change this version to any number between 0
and the most recent version of the HERE Map Content catalog. You can find the most recent version by
opening the [OLP Portal](https://platform.here.com/) or the
[OLP China Portal](https://platform.hereolp.cn/) and navigating to the HERE Map  Content
catalog; the current version number is displayed in the upper left corner of the page.

`pipeline-job-second.conf` specifies in the line `version = 2` that version 2 of the input
catalog should be processed in the second run. You can change this version to any number that is
less or equal the most recent version of the HERE Map Content catalog and greater than the version
specified in `pipeline-job-first.conf`.

### Other Configuration Files

The remainder of the configuration is specified in the `application.conf` found in the
`src/main/resources` directory of the compiler project. But you do not have to modify it unless you
want to change the behavior of the compiler.

## Build the Compiler

To build the compiler, run `mvn install` in the `heremapcontent-difftool` directory.

## Run the Compiler Locally

To run the compiler locally, you will need to run the entry point to the compiler:

- `com.here.platform.data.processing.example.scala.difftool.processor.Main`

As _arguments_, you must provide the `--master` _parameter_ with the address of the Spark server
master to connect to, and any configuration parameters you want to override. Alternatively, you can
add those parameters to the `application.conf` file.

Additionally, you also need to specify the `-Dpipeline-config.file` and `-Dpipeline-job.file`
_parameters_ to specify the location of a configuration file that contains the catalogs as well as
job-specific versions of the catalogs, to read and write to.

For local runs, a bounding box filter is provided in the
`config/here/local-application.conf` and `config/here-china/local-application.conf` to
limit the number of partitions to be processed. This speeds up the compilation process. In this
example, we use a bounding box around the city of Berlin and Beijing for the HERE OLP and HERE OLP
China environments respectively. You can edit the bounding box coordinates to compile a different
partition of HERE Map Content. Make sure you update the layer coverage to reflect the different
geographical region. In order to use this configuration file, you need to use the `-Dconfig.file`
parameter.

### Run the Compiler from the Command Line

The first run of the pipeline will use the job configuration `pipeline-job-first.conf`. As
mentioned before, the first run will compute the differences between the catalog version specified
in `pipeline-job-first.conf` to an empty catalog. That means all segments contained in the
input catalog will be considered as newly added segments. Run the following command line in the
`heremapcontent-difftool` directory to run the compiler.

For the HERE OLP environment:

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.difftool.processor.Main \
-Dpipeline-config.file=./config/here/pipeline-config.conf \
-Dpipeline-job.file=./config/here/pipeline-job-first.conf \
-Dconfig.file=./config/here/local-application.conf \
-Dexec.args="--master local[*]"
```

In a second run, we can now compute the differences between the version used in the first run and
the version specified in `pipeline-job-second.conf`. Run the following command line in the
`heremapcontent-difftool` project to run the Compiler a second time.

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.difftool.processor.Main \
-Dpipeline-config.file=./config/here/pipeline-config.conf \
-Dpipeline-job.file=./config/here/pipeline-job-second.conf \
-Dconfig.file=./config/here/local-application.conf \
-Dexec.args="--master local[*]"
```

For the HERE OLP China environment, instead, use the files in the `config/here-chine` directory:

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.difftool.processor.Main \
-Dpipeline-config.file=./config/here-china/pipeline-config.conf \
-Dpipeline-job.file=./config/here-china/pipeline-job-first.conf \
-Dconfig.file=./config/here-china/local-application.conf \
-Dexec.args="--master local[*]"
```

```bash
mvn exec:java \
-Dexec.mainClass=com.here.platform.data.processing.example.scala.difftool.processor.Main \
-Dpipeline-config.file=./config/here-china/pipeline-config.conf \
-Dpipeline-job.file=./config/here-china/pipeline-job-second.conf \
-Dconfig.file=./config/here-china/local-application.conf \
-Dexec.args="--master local[*]"
```

## Run this Compiler as an Open Location Platform Pipeline

### Generate a Fat JAR file:

Run the `mvn -Pplatform package` command in the `heremapcontent-difftool` directory to generate a
fat JAR file to deploy the compiler to a Pipeline.

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
                com.here.platform.data.processing.example.scala.difftool.processor.Main $GROUP_ID \
                --workers=4 --worker-units=3 --supervisor-units=2 --input-catalog-ids=rib
olp pipeline version create $COMPONENT_NAME_version $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
                "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf"
```

* Activate the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --input-catalogs "$PATH_TO_CONFIG_FOLDER/pipeline-job-first.conf" --output-catalog -1
```

* Wait for the first job to finish and start the second run with the different version of input catalog:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --input-catalogs "$PATH_TO_CONFIG_FOLDER/pipeline-job-second.conf" --output-catalog 0
```

## Verify the Output

In the [OLP Portal](https://platform.here.com/) / [OLP China Portal](https://platform.hereolp.cn/)
select the _Data_ tab and find your catalog.

- Open `topology-geometry-diff` layer and select the _Inspect_ tab.
- On the map, navigate to the location of your bounding box and set the zoom to level 10.
- Finally, select any highlighted partition to view the results. The JSON output of the compiler
  should be displayed on the right side.
