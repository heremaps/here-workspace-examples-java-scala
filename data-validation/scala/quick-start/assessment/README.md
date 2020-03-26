# Assessment step in a simple catalog validation process

This Data Validation Library example shows how to run a assessment phase.
The input catalog for this example uses the result of metrics phase example.
Result from the Assessment component of the Data Validation Library Quick Start Example. It has a single partition, ASSESSMENT, with the final PASS/FAIL result along with additional context such as the configured error threshold.
The example Assessment component takes as inputs a metrics catalog and the original candidate catalog which was the input to the Testing component. It counts the number of tiles with CRITICAL errors and computes the percentage against the total number of tiles in the candidate catalog.
If the error percentage is above a configured threshold, in this case 10%, the assessment result is FAIL.

## Hint
{templates_dir}     value is `cn` for China and `olp` for others
{here_value}        value is `here-cn` for China and `here` for others
{realm}             The ID of your organization, also called a realm. Consult your Platform invitation
letter to learn your organization ID

## Set up Access to the Data API

The source code and configuration templates for the example is in the SDK under examples/data-validation/java/quick-start for Java and examples/data-validation/scala/quick-start for Scala.
For running pipelines, this document assumes that you have already set up your app and credentials using the instructions from Get Your Credentials.
If not, please refer to:
[Get Your Credentials](https://developer.here.com/olp/documentation/data-validation-library/content/dev_guide/topics/credentials.html)

## Package the Application Into a Fat JAR

To run comparison pipeline in the HERE Open Location Platform, you need to build a fat JAR.

You can refer to the `pom.xml` file for packaging details if you are creating your own application. If you are not creating your own application, run the following command under the example's base directory.
```bash
mvn clean install -Pplatform
```

Once the above command is successful, a fat JAR named `comparison_2.11-<VERSION>-platform.jar` will be built in the `target` folder.

## Create Output Catalog and Layers

You will be creating a catalog with two layers, a "Compilation State" layer and a "Test Result" layer.
You can do that using a configuration template file named `output-assessment-catalog-platform.json.template`
(in the examples/data-validation/{java,scala}/quick-start/config/pipeline/{templates_dir} directory relative to the root of your unpacked SDK).
Remove the `.template` extension of that file's name and replace the placeholder `{output_catalog_id}` with $CATALOG_ID,
where CATALOG_ID is a unique identifier, such as "YOUR_LOGIN-validation-quickstart-assessment"

Using the OLP Portal, create a new catalog and the following catalog layers:

| Layer ID       | Layer Type | Partitioning | Zoom Level | Content Type             | Content Encoding | Schema
|-----------------------------|--------------|------------|--------------------------|------------------|------------------------------------------------
| assessment     | Versioned  | Generic      | 12         | application/x-protobuf   | uncompressed     | assessment example quickstart
| state          | Versioned  | Generic      | N.A.       | application/octet-stream | uncompressed     | -

Alternatively, You can do that using a configuration template file named `output-assessment-catalog-platform.json.template`
(in the `examples/data-validation/{java,scala}/quick-start/config/pipeline/{templates_dir}` directory relative to the root of your unpacked SDK).
Remove the `.template` extension of that file's name and replace the placeholder `{output_catalog_id}` with $CATALOG_ID,
where CATALOG_ID is a unique identifier, such as "YOUR_LOGIN-validation-quickstart-assessment"

* First, create an output catalog:

Use the OLP CLI (tools/OLP_CLI relative to the root of your unpacked SDK) to create a catalog with a command like this:

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY --description CATALOG_DESCRIPTION
olp catalog layer add $CATALOG_HRN assessment assessment --versioned --summary "assessment" \
            --description "assessment" --content-type application/x-protobuf
olp catalog layer add $CATALOG_HRN state state --versioned --summary "state" \
            --description "state" --content-type application/octet-stream
```

or, You can execute a single command

`olp catalog create $CATALOG_ID $CATALOG_NAME \
--summary $CATALOG_SUMMARY \
--config $JSON_FILE_PATH`

where CATALOG_ID is the unique identifier you used above, such as "YOUR_LOGIN-validation-quickstart-assessment". This identifier is the resource portion of your catalog's HERE Resource Name (HRN),

`CATALOG_NAME` is a unique identifier (whitespaces are allowed), such as "YOUR_LOGIN Data Validation Library Quick Start Example assessment Results"
(this is the value that appears for your catalog in the Portal's Data tab, when you list all available catalogs or search for a catalog), and
`CATALOG_SUMMARY` is an informal description like "Output catalog of the assessment component in the Data Validation Library Quick Start Example"
(the --summary option is actually required).
`JSON_FILE_PATH` is the path to your configuration file from the previous step above.

It will take approximately a minute for the catalog to be created on the platform, before you get a result like this,
containing the HRN that you can use for all further CLI and SDK operations to identify this catalog:

    `Catalog hrn:{here_value}:data::{realm}:{YOUR_CATALOG_ID} has been created.`
	
The HERE Resource Name (HRN) for this catalog can now be used as the output for your assessment pipeline.

* Then grant "read", "write", and "manage" permissions for this catalog to your group with the respective group ID by running the following command:

```bash
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read --write --manage
```

For more details on how to create a catalog and its layers refer to the
[Data User Gui  de](https://developer.here.com/olp/documentation/data-user-guide/content/index.html), particularly
[Create a Catalog](https://developer.here.com/olp/documentation/data-user-guide/content/portal/catalog-creating.html) and
[Create a Layer](https://developer.here.com/olp/documentation/data-user-guide/content/portal/layer-creating.html).

## Configure the assessment Pipeline

For Java, the configuration template files are in the examples/data-validation/java/quick-start/config/pipeline folder, For Scala, they are in examples/data-validation/scala/quick-start/config/pipeline.

Fill out the template files as described below and save them without the ".template" suffix in the folder from where you are running the OLP CLI.
Replace the output catalog HRN in pipeline-assessment-config.conf to that of the catalog you created above.
Replace the candidate catalog HRN in pipeline-assessment-config.conf to: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-input

## Run application on a platform

To run example on a platform You should prepare appropriate infrastructure

* Create a pipeline with a following command

```bash
olp pipeline create $PIPELINE_NAME $GROUP_ID
```

* Create a pipeline template

```bash
olp pipeline template create $PIPELINE_TEMPLATE_NAME batch-2.1.0 $PATH_TO_JAR com.here.platform.data.validation.example.quickstart.assessment.scala.Main $GROUP_ID --input-catalog-ids quickstartmetrics quickstartinput
```

* Create the pipeline version, configuring the reference catalog hrn and version as runtime parameters, to get a pipeline version ID

```bash
olp pipeline version create $PIPELINE_VERSION_NAME $PIPELINE_ID $PIPELINE_TEMPLATE_ID "$PATH_TO_CONFIG_FOLDER/pipeline-assessment-config.conf"
```

* Activate the pipeline

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
`olp pipeline template delete $TEMPLATE_ID`
`olp pipeline delete $PIPELINE_ID`

### Inspect the assessment Output Catalog

There are at least two ways to decode the contents of output assessment catalog, either using the Portal or using protoc on your local machine.

* Inspect the assessment Output Catalog in the Portal

In the Portal's Data tab, click on the "assessment-result" layer for the output assessment catalog that you have created and populated.
Alternatively you can inspect the output catalog in the Portal's Data tab.
On the Layer page, click the Partitions tab to open the catalog.
Click on a specific partition to see its decoded data.

* Inspect the assessment Output Catalog locally

Alternatively you can inspect the output catalog in the Portal's Data tab.
On the Layer page, select the Partitions tab. Click on "FAIL" under Partition ID.
Click on Download raw data to save the raw partition data to disk.
You can then run protoc on the raw data to decode it, using:

```bash
  protoc --decode_raw < $PATH_TO_RAW_PARTITION_DATA
```

The output is structured as follows:
1 {
  1: "quickstartcriteria"
  2: 1
}
2: 0x3fb999999999999a
3: 0x3fbe000000000000
4: 15
5: 128

The first item is our assessment ID and result enum, with 1 indicating FAIL.
The second item is the critical threshold for the assessment criteria, that is, the percentage of total tiles permitted to contain critical errors. It is a double-precision floating point number, output here in hexadecimal format by the raw decoder. Our assessment criteria was configured to have a critical threshold of 0.1, or 10% of the total tiles.
The third item is the critical percentage, that is, the percent of total candidate tiles which contained critical errors. Like the critical threshold, it is a double represented here in hexadecimal format. The value above is 0.1171875.
The fourth item is the total number of tiles with critical errors.
The fifth item is the total number of input tiles from the original candidate catalog that we fed into the testing component. of 0 indicates a metric severity of NONE, and the tile IDs listed are those which contained octagons in the original input catalog and received a test result of PASS in the testing pipeline.