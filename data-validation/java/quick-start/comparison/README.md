# Simple data comparison

This Data Validation Library example shows how to use a comparison component
to create a simple comparison logic for two different versions of the same catalog.

The main entities in this example are Comparison and Comparator. Basic Comparator uses retrievers
to know where to extract data from and uses Comparison to analyse it.

## Hint
{templates_dir}     value is `cn` for China and `olp` for others
{here_value}        value is `here-cn` for China and `here` for others
{realm}             The ID of your organization, also called a realm. Consult your Platform invitation
letter to learn your organization ID

## Set up Access to the Data API

To run the example, you need access to a HERE Data Validation Library Quick Start Comparison Example Input,
and you need to create one catalog of your own for the output data. For more information, refer to the
[Open Location Platform CLI](https://developer.here.com/olp/documentation/open-location-platform-cli/user_guide/index.html) and
[Data User Guide](https://developer.here.com/olp/documentation/data-user-guide/index.html) documentation.

## Package the Application Into a Fat JAR

To run comparison pipeline in the HERE Open Location Platform, you need to build a fat JAR.

You can refer to the `pom.xml` file for packaging details if you are creating your own application. If you are not creating your own application, run the following command under the example's base directory.
```bash
mvn clean install -Pplatform
```

Once the above command is successful, a fat JAR named `comparison_2.11-<VERSION>-platform.jar` will be built in the `target` folder.

## Create Output Catalog and Layers

The catalog you need to create is used to store the output data for this example
which is executed on a input catalog with a single line segment per HERE Tile over the Berlin area.
Each line segment has 9 points (an octagon) or 2 points. In version 0 of this catalog, the 2-point line segments are horizontal.
In version 1 of this catalog, the 2-point line segments are vertical.

Using the OLP Portal, create a new catalog and the following catalog layers:

| Layer ID                    | Layer Type | Partitioning | Zoom Level | Content Type             | Content Encoding | Schema
|-----------------------------|------------|--------------|------------|--------------------------|------------------|------------------------------
| heretile-comparison-results | Versioned  | HEREtile     | 12         | application/x-protobuf   | uncompressed     | validation example quickstart
| generic-comparison-results  | Versioned  | Generic      | N.A.       | application/octet-stream | uncompressed     | -
| state                       | Versioned  | Generic      | N.A.       | application/octet-stream | uncompressed     | -

Alternatively, You can do that using a configuration template file named output-comparison-catalog-platform.json.template
(in the examples/data-validation/{java,scala}/quick-start/config/pipeline/{templates_dir} directory relative to the root of your unpacked SDK).
Remove the `.template` extension of that file's name and replace the placeholder `{output_catalog_id}` with `{CATALOG_ID}`,
where CATALOG_ID is a unique identifier, such as "YOUR_LOGIN-validation-quickstart-comparison".

* First, create an output catalog:

Use the OLP CLI (tools/OLP_CLI relative to the root of your unpacked SDK) to create a catalog with a command like this:

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY --description CATALOG_DESCRIPTION
olp catalog layer add $CATALOG_HRN heretile-comparison-results heretile-comparison-results --versioned --summary "heretile-comparison-results" --description "heretile-comparison-results" --content-type application/x-protobuf --partitioning heretile:12
olp catalog layer add $CATALOG_HRN generic-comparison-results generic-comparison-results --versioned --summary "generic-comparison-results" --description "generic-comparison-results" --content-type application/octet-stream
olp catalog layer add $CATALOG_HRN state state --versioned --summary "state" --description "state" --content-type application/octet-stream
```

or, You can execute a single command

`olp catalog create $CATALOG_ID $CATALOG_NAME \
--summary $CATALOG_SUMMARY \
--config $JSON_FILE_PATH`


where CATALOG ID is the unique identifier you used above, such as "YOUR_LOGIN-validation-quickstart-comparison". This identifier is the resource portion of your catalog's HERE Resource Name (HRN),

`CATALOG_NAME` is a unique identifier (whitespaces are allowed), such as "YOUR_LOGIN Data Validation Library Quick Start Example Comparison Results"
(this is the value that appears for your catalog in the Portal's Data tab, when you list all available catalogs or search for a catalog), and
`CATALOG_SUMMARY` is an informal description like "Output catalog of the Comparison component in the Data Validation Library Quick Start Example"
(the --summary option is actually required).
`JSON_FILE_PATH` is the path to your configuration file from the previous step above.

It will take approximately a minute for the catalog to be created on the platform, before you get a result like this,
containing the HRN that you can use for all further CLI and SDK operations to identify this catalog:

    `Catalog hrn:{here_value}:data::{realm}:{YOUR_CATALOG_ID} has been created.`
	
The HERE Resource Name (HRN) for this catalog can now be used as the output for your comparison pipeline.

* Then grant "read", "write", and "manage" permissions for this catalog to your group with the respective group ID by running the following command:

```bash
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read --write --manage
```

For more details on how to create a catalog and its layers refer to the
[Data User Gui  de](https://developer.here.com/olp/documentation/data-user-guide/content/index.html), particularly
[Create a Catalog](https://developer.here.com/olp/documentation/data-user-guide/content/portal/catalog-creating.html) and
[Create a Layer](https://developer.here.com/olp/documentation/data-user-guide/content/portal/layer-creating.html).

## Configure the Comparison Pipeline

For Java, the configuration template files are in the examples/data-validation/java/quick-start/config/pipeline folder, For Scala, they are in examples/data-validation/scala/quick-start/config/pipeline.

Fill out the template files as described below and save them without the ".template" suffix in the folder from where you are running the OLP CLI.
Replace the output catalog HRN in pipeline-testing-config.conf to that of the catalog you created above.
Replace the candidate catalog HRN in pipeline-testing-config.conf to: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-input

## Run application on a platform

To run example on a platform You should prepare appropriate infrastructure

* Create a pipeline with a following command

```bash
olp pipeline create $PIPELINE_NAME $GROUP_ID
```

* Create a pipeline template

```bash
olp pipeline template create $PIPELINE_TEMPLATE_NAME batch-2.1.0 $PATH_TO_JAR com.here.platform.data.validation.example.quickstart.comparison.java.Main $GROUP_ID --input-catalog-ids candidate
```

* Create the pipeline version, configuring the reference catalog hrn and version as runtime parameters, to get a pipeline version ID

```bash
olp pipeline version create $PIPELINE_VERSION_NAME $PIPELINE_ID $PIPELINE_TEMPLATE_ID "$PATH_TO_CONFIG_FOLDER/pipeline-comparison-config.conf"
```

* Activate the pipeline version

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID
```

It may take a few minutes before the pipeline starts running, as your fat jar for the pipeline template may still be uploading to the platform in the background.
To find out when the pipeline starts running you can either check its state via the Pipelines tab of the Portal or use this OLP CLI command
(when the running state is reached, the portal lets you navigate to the Splunk logs, and this olp command will output the respective URL):

`olp pipeline version wait $PIPELINE_ID $PIPELINE_VERSION_ID --job-state=running --timeout=300`

The pipeline takes up to 10 minutes to complete. Manually refresh the Pipelines tab in Portal.
If the pipeline is complete, its status will refresh from "RUNNING" to "READY".

* Remove portal infrastructure

If you want to remove this pipeline, template, and version from the server, you can delete them with the commands below.
However, the results of the pipeline remain in the output catalog


`olp pipeline version delete $PIPELINE_ID $PIPELINE_VERSION_ID`
`olp pipeline template delete $PIPELINE_TEMPLATE_ID`
`olp pipeline delete $PIPELINE_ID`


### Inspect the Comparison Output Catalog

There are at least two ways to decode the contents of output comparison catalog, either using the Portal or using protoc on your local machine.

* Inspect the Comparison Output Catalog in the Portal

In the Portal's Data tab, click on the "heretile-comparison-results" layer for the output comparison catalog that you have created and populated.
Alternatively you can inspect the following catalog in the Portal's Data tab: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-scala-comparison
On the Layer page, click the Inspect tab to open the catalog.
Between the two input catalog versions, partitions with line segments that differ are highlighted in blue.
Click on a specific partition to see its decoded data.
Portal should render the differing geometry and display the decoded data values for the selected partition.

* Inspect the Comparison Output Catalog locally

Alternatively you can inspect the following catalog in the Portal's Data tab: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-scala-comparison
On the Layer page, click on the Partitions tab so that we can see specific partitions.
Click on "23618304" under Partition ID.
Click on Download raw data to save the raw partition data to disk.
You can then run protoc on the raw data to decode it, using:

`protoc --decode_raw < $PATH_TO_RAW_PARTITION_DATA`

The output is structured as follows:
1 {
  1 {
    1: 0x404a0d0000000000
    2: 0x4029668000000000
  }
  1 {
    1: 0x404a064000000000
    2: 0x4029668000000000
  }
}
2 {
  1 {
    1: 0x404a09a000000000
    2: 0x4029590000000000
  }
  1 {
    1: 0x404a09a000000000
    2: 0x4029740000000000
  }
}

The first item is the pair of points representing the horizontal line segment in the reference catalog version. The second item is the pair of points representing the vertical line segment in the candidate catalog version.
In the Portal's Data tab, click on the "generic-comparison-results" layer for the output comparison catalog that you have created and populated.
On the Layer page, select the Partitions tab. Click on "state-fingerprints" under Partition ID.
Its content does not need to be decoded by protoc, and simply contains the string:

"checksum differs"

This indicates that the generic comparison for the "state" layer in each input catalog version yielded a checksum difference in the "fingerprints" partition.
Generic comparisons do not retrieve payload content, and only compare metadata fields which are common to any catalog partition.