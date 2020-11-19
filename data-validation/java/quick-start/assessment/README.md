# Assessment step in a simple catalog validation process

This Data Validation Library example shows how to run an assessment phase.
The input catalog for this example uses the result of metrics phase example.
Result from the Assessment component of the Data Validation Library Quick Start Example. It has a single partition, ASSESSMENT, with the final PASS/FAIL result along with additional context such as the configured error threshold.
The example Assessment component takes as inputs a metrics catalog and the original candidate catalog which was the input to the Testing component. It counts the number of tiles with CRITICAL errors and computes the percentage against the total number of tiles in the candidate catalog.
If the error percentage is above a configured threshold, in this case 10%, the assessment result is FAIL.

## Hint
{templates_dir}     value is `cn` for China and `olp` for others
{here_value}        value is `here-cn` for China and `here` for others
{realm}             The ID of your organization, also called a realm. Consult your platform invitation
letter to learn your organization ID.

## Get Your Credentials

To run this example, you need two sets of credentials:

* **Platform credentials:** To get access to the platform data and resources, including HERE Map Content data for your pipeline input. 
* **Repository credentials:** To download HERE Data SDK for Java & Scala libraries and Maven archetypes to your environment.

For more details on how to set up your credentials, see the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more details on how to verify that your platform credentials are configured correctly, see the [Verify Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/verify-credentials/index.html) tutorial.

## Configure a Project

To follow this example, you'll need a [project](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html). A project is a collection of platform resources
 (catalogs, pipelines, and schemas) with controlled access. You can create a project through the
 **HERE platform portal**.
 
Alternatively, use the OLP CLI [`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project) command to create the project:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/shared_content/topics/olp/concepts/hrn.html) of your new project. Note down this HRN as you'll need it later in this tutorial.

> Note:
> You don't have to provide a `--scope` parameter if your app has a default scope.
> For details on how to set a default project scope for an app, see the _Specify a
> default Project_ for Apps chapter of the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more information on how to work with projects, see the [Organize your work in projects](https://developer.here.com/documentation/java-scala-dev/dev_guide/organize-work-in-projects/index.html) tutorial.

## Package the Application Into a Fat JAR

To run comparison pipeline in the HERE platform, you need to build a fat JAR.

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

Use the HERE platform Portal to [create the output catalog](https://developer.here.com/documentation/data-user-guide/user_guide/portal/catalog-creating.html) in your project and [add the following layers](https://developer.here.com/documentation/data-user-guide/user_guide/portal/layer-creating.html)::

| Layer ID       | Layer Type | Partitioning | Zoom Level | Content Type             | Content Encoding | Schema
|-----------------------------|--------------|------------|--------------------------|------------------|------------------------------------------------
| assessment     | Versioned  | Generic      | 12         | application/x-protobuf   | uncompressed     | assessment example quickstart
| state          | Versioned  | Generic      | N.A.       | application/octet-stream | uncompressed     | -

Alternatively, You can do that using a configuration template file named `output-assessment-catalog-platform.json.template`
(in the `examples/data-validation/{java,scala}/quick-start/config/pipeline/{templates_dir}` directory relative to the root of your unpacked SDK).
Remove the `.template` extension of that file's name and replace the placeholder `{output_catalog_id}` with $CATALOG_ID,
where CATALOG_ID is a unique identifier, such as "YOUR_LOGIN-validation-quickstart-assessment"

> Note:
> We recommend you to set values to variables so that you can easily copy and execute the following commands.

1. Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create the catalog.
Make sure to note down the HRN returned by the following command for later use:

Use the OLP CLI (tools/OLP_CLI relative to the root of your unpacked SDK) to create a catalog with a command like this:

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY \
            --description CATALOG_DESCRIPTION --scope $PROJECT_HRN
```

2. Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add) command to add two `versioned` layers to your catalog:

```bash
olp catalog layer add $CATALOG_HRN assessment assessment --versioned --summary "assessment" \
            --description "assessment" --content-type application/x-protobuf \
            --scope $PROJECT_HRN
olp catalog layer add $CATALOG_HRN state state --versioned --summary "state" \
            --description "state" --content-type application/octet-stream \
            --scope $PROJECT_HRN
```

or, You can execute a single command

```
olp catalog create $CATALOG_ID $CATALOG_NAME \
            --summary $CATALOG_SUMMARY \
            --config $JSON_FILE_PATH \
            --scope $PROJECT_HRN
```

where CATALOG_ID is the unique identifier you used above, such as "YOUR_LOGIN-validation-quickstart-assessment". This identifier is the resource portion of your catalog's HERE Resource Name (HRN),

- `CATALOG_NAME` is a unique identifier (whitespaces are allowed), such as "YOUR_LOGIN Data Validation Library Quick Start Example assessment Results"
(this is the value that appears for your catalog in the Portal's Data tab, when you list all available catalogs or search for a catalog), and
- `CATALOG_SUMMARY` is an informal description like "Output catalog of the assessment component in the Data Validation Library Quick Start Example"
(the --summary option is actually required).
- `JSON_FILE_PATH` is the path to your configuration file from the previous step above.
- `$PROJECT_HRN` is your project's `HRN` (returned by `olp project create` command).

It will take approximately a minute for the catalog to be created on the platform, before you get a result like this,
containing the HRN that you can use for all further CLI and SDK operations to identify this catalog:

    `Catalog hrn:{here_value}:data::{realm}:{YOUR_CATALOG_ID} has been created.`
	
The HERE Resource Name (HRN) for this catalog can now be used as the output for your assessment pipeline.

3. Use the [`olp project resources link`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-resources-commands.html#project-resources-link) command to link the _HERE Map Content_ catalog to your project:

```bash
olp project resources link $PROJECT_HRN $CATALOG_QUICK_START_METRICS
olp project resources link $PROJECT_HRN $CATALOG_QUICK_START_INPUT
```

- `$CATALOG_QUICK_START_METRICS` - The HRN of the public _Data Validation Library Quick Start Example Metrics Results_ catalog in your pipeline configuration.
- `$CATALOG_QUICK_START_INPUT` - The HRN of the public _Data Validation Library Quick Start Example Input_ catalog in your pipeline configuration.

- For more details on catalog commands, see [Catalog Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html).
- For more details on layer commands, see [Layer Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html).
- For more details on project commands, see [Project Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html).
- For instructions on how to link a resource to a project, see [Project Resources Link command](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-resources-commands.html#project-resources-link).

## Configure the assessment Pipeline

For Java, the configuration template files are in the examples/data-validation/java/quick-start/config/pipeline folder, For Scala, they are in examples/data-validation/scala/quick-start/config/pipeline.

Fill out the template files as described below and save them without the ".template" suffix in the folder from where you are running the OLP CLI.
Replace the output catalog HRN in pipeline-assessment-config.conf to that of the catalog you created above.
Replace the candidate catalog HRN in pipeline-assessment-config.conf to: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-input

## Run application on a platform

You can use the OLP CLI to create pipeline components and activate the pipeline version with the following commands:

1. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline-workflows.html) pipeline components:

```bash
olp pipeline create $PIPELINE_NAME --scope $PROJECT_HRN
olp pipeline template create $PIPELINE_TEMPLATE_NAME batch-2.1.0 $PATH_TO_JAR \
            com.here.platform.data.validation.example.quickstart.assessment.java.Main \
            --input-catalog-ids quickstartmetrics quickstartinput \
            --scope $PROJECT_HRN
```

2. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-create) the pipeline version, configuring the reference catalog hrn and version as runtime parameters, to get a pipeline version ID:

```bash
olp pipeline version create $PIPELINE_VERSION_NAME $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
            "$PATH_TO_CONFIG_FOLDER/pipeline-assessment-config.conf" \
            --scope $PROJECT_HRN
```

3. [Activate](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate) the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

It may take a few minutes before the pipeline starts running, as your fat jar for the pipeline template may still be uploading to the platform in the background.
To find out when the pipeline starts running you can either check its state via the Pipelines tab of the Portal or use this OLP CLI command
(when the running state is reached, the portal lets you navigate to the Splunk logs, and this olp command will output the respective URL):

`olp pipeline version wait $PIPELINE_ID $PIPELINE_VERSION_ID --job-state=running --timeout=300 --scope $PROJECT_HRN`

The pipeline takes up to 10 minutes to complete. Manually refresh the Pipelines tab in Portal.
If the pipeline is complete, its status will refresh from "RUNNING" to "READY".

* Remove portal infrastructure

If you want to remove this pipeline, template, and version from the server, you can delete them with the commands below.
However, the results of the pipeline remain in the output catalog

`olp pipeline version delete $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN`
`olp pipeline template delete $TEMPLATE_ID --scope $PROJECT_HRN`
`olp pipeline delete $PIPELINE_ID --scope $PROJECT_HRN`

### Inspect the assessment Output Catalog

There are at least two ways to decode the contents of output assessment catalog, either using the Portal or using protoc on your local machine.

* Inspect the assessment Output Catalog in the Portal

In the Portal's Data tab, click on the "assessment-result" layer for the output assessment catalog that you have created and populated.
Alternatively you can inspect the following catalog in the Portal's Data tab: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-java-assessment
On the Layer page, click the _Inspect_ tab to open the catalog.
Click on a specific partition to see its decoded data.

* Inspect the assessment Output Catalog locally

Alternatively you can inspect the following catalog in the Portal's Data tab: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-java-assessment
On the Layer page, select the _Inspect_ tab. Click on "FAIL" under Partition ID.
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