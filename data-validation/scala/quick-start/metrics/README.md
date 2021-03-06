# Metrics step in a simple catalog validation process

This Data Validation Library example shows how to run a metrics phase.
The input catalog for this example uses the result of testing phase example.
Results from the Metrics component of the Data Validation Library Quick Start Example. Its partitions are the test statuses assigned by the Testing component, in this case, PASS and FAIL.
Each partition contains one or more error severities along with the corresponding tile IDs from the candidate catalog that was the input to the Testing component.
In this case, the Metrics component considered all PASS results as severity NONE, and all FAIL results as severity CRITICAL.

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

To follow this example, you will need a [projects](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html). A project is a collection of platform resources
 (catalogs, pipelines, and schemas) with controlled access. You can create a project through the
[HERE platform portal](https://platform.here.com/).
 
Alternatively, use the OLP CLI [`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project) command to create the project:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/index.html) of your new project. Note down this HRN as you will need it later in this tutorial.

> #### Note
> You do not have to provide a `--scope` parameter if your app has a default scope.
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
You can do that using a configuration template file named `output-metrics-catalog-platform.json.template`
(in the examples/data-validation/{java,scala}/quick-start/config/pipeline/{templates_dir} directory relative to the root of your unpacked SDK).
Remove the `.template` extension of that file's name and replace the placeholder `{output_catalog_id}` with $CATALOG_ID,
where CATALOG_ID is a unique identifier, such as "YOUR_LOGIN-validation-quickstart-metrics"

Use the HERE platfrom Portal to [create the output catalog](https://developer.here.com/documentation/data-user-guide/user_guide/portal/catalog-creating.html) in your project and [add the following layers](https://developer.here.com/documentation/data-user-guide/user_guide/portal/layer-creating.html):

| Layer ID       | Layer Type | Partitioning | Zoom Level | Content Type             | Content Encoding | Schema
|-----------------------------|--------------|------------|--------------------------|------------------|------------------------------------------------
| metrics-result | Versioned  | Generic      | N.A.       | application/x-protobuf   | uncompressed     | metrics example quickstart
| state          | Versioned  | Generic      | N.A.       | application/octet-stream | uncompressed     | -

Alternatively, You can do that using a configuration template file named `output-metrics-catalog-platform.json.template`
(in the `examples/data-validation/{java,scala}/quick-start/config/pipeline/{templates_dir}` directory relative to the root of your unpacked SDK).
Remove the `.template` extension of that file's name and replace the placeholder `{output_catalog_id}` with $CATALOG_ID,
where CATALOG_ID is a unique identifier, such as "YOUR_LOGIN-validation-quickstart-metrics"

> #### Note
> We recommend to set values to variables so that you can easily copy and execute the following commands.

1. Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create the catalog.
Make sure to note down the HRN returned by the following command for later use:

Use the OLP CLI (tools/OLP_CLI relative to the root of your unpacked SDK) to create a catalog with a command like this:

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY \
            --description CATALOG_DESCRIPTION --scope $PROJECT_HRN
```

2. Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add) command to add two `versioned` layers to your catalog:

```bash
olp catalog layer add $CATALOG_HRN metrics-result metrics-result --versioned --summary "metrics-result" \
            --description "metrics-result" --content-type application/x-protobuf \
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

where CATALOG_ID is the unique identifier you used above, such as "YOUR_LOGIN-validation-quickstart-metrics". This identifier is the resource portion of your catalog's HERE Resource Name (HRN),

- `CATALOG_NAME` is a unique identifier (whitespaces are allowed), such as "YOUR_LOGIN Data Validation Library Quick Start Example metrics Results"
(this is the value that appears for your catalog in the **Data** tab, when you list all available catalogs or search for a catalog), and
- `CATALOG_SUMMARY` is an informal description like "Output catalog of the metrics component in the Data Validation Library Quick Start Example"
(the --summary option is actually required).
- `JSON_FILE_PATH` is the path to your configuration file from the previous step above.
- `$PROJECT_HRN` is your project's `HRN` (returned by `olp project create` command).

It will take approximately a minute for the catalog to be created on the platform, before you get a result like this,
containing the HRN that you can use for all further CLI and SDK operations to identify this catalog:

    `Catalog hrn:{here_value}:data::{realm}:{YOUR_CATALOG_ID} has been created.`
	
The HERE Resource Name (HRN) for this catalog can now be used as the output for your metrics pipeline.

3. Use the [`olp project resources link`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-resources-commands.html#project-resources-link) command to link the _Data Validation Library Quick Start Example Input_ catalog to your project:

```bash
olp project resources link $PROJECT_HRN $CATALOG_QUICK_START_TESTING
```

- `$CATALOG_QUICK_START_TESTING` - The HRN of the public _Data Validation Library Quick Start Example Input_ catalog in your pipeline configuration.

- For more details on catalog commands, see [Catalog Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html).
- For more details on layer commands, see [Layer Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html).
- For more details on project commands, see [Project Commands](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html).
- For instructions on how to link a resource to a project, see [Project Resources Link command](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-resources-commands.html#project-resources-link).

## Configure the metrics Pipeline

For Java, the configuration template files are in the examples/data-validation/java/quick-start/config/pipeline folder, For Scala, they are in examples/data-validation/scala/quick-start/config/pipeline.

Fill out the template files as described below and save them without the ".template" suffix in the folder from where you are running the OLP CLI.
Replace the output catalog HRN in pipeline-metrics-config.conf to that of the catalog you created above.
Replace the candidate catalog HRN in pipeline-metrics-config.conf to: hrn:{here_value}:data::{realm}:dvl-example-berlin4-validation-quickstart-input

## Run application on a platform

You can use the OLP CLI to create pipeline components and activate the pipeline version with the following commands:

1. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline-workflows.html) pipeline components:

```bash
olp pipeline create $PIPELINE_NAME --scope $PROJECT_HRN
olp pipeline template create $PIPELINE_TEMPLATE_NAME batch-2.1.0 $PATH_TO_JAR \
            com.here.platform.data.validation.example.quickstart.metrics.scala.Main \
            --input-catalog-ids quickstarttesting \
            --scope $PROJECT_HRN
```

2. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-create) the pipeline version, configuring the reference catalog hrn and version as runtime parameters, to get a pipeline version ID:

```bash
olp pipeline version create $PIPELINE_VERSION_NAME $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
            "$PATH_TO_CONFIG_FOLDER/pipeline-metrics-config.conf" \
            --scope $PROJECT_HRN
```

3. [Activate](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate) the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

It may take a few minutes before the pipeline starts running, as your fat jar for the pipeline template may still be uploading to the platform in the background.
To find out when the pipeline starts running you can either check its state via the **Pipelines** tab of the platform portal or use this OLP CLI command
(when the running state is reached, the portal lets you navigate to the Splunk logs, and this olp command will output the respective URL):

`olp pipeline version wait $PIPELINE_ID $PIPELINE_VERSION_ID --job-state=running --timeout=300 --scope $PROJECT_HRN`

The pipeline takes up to 10 minutes to complete. Manually refresh the **Pipelines** tab in the platform portal.
If the pipeline is complete, its status will refresh from "RUNNING" to "READY".

* Remove portal infrastructure

If you want to remove this pipeline, template, and version from the server, you can delete them with the commands below.
However, the results of the pipeline remain in the output catalog

`olp pipeline version delete $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN`
`olp pipeline template delete $TEMPLATE_ID --scope $PROJECT_HRN`
`olp pipeline delete $PIPELINE_ID --scope $PROJECT_HRN`

### Inspect the metrics Output Catalog

There are at least two ways to decode the contents of output metrics catalog, either using the platform portal or using protoc on your local machine.

* Inspect the metrics output catalog in the platform portal

In the **Data** tab, click the "metrics-result" layer for the output metrics catalog that you have created and populated.
Alternatively you can inspect the output catalog in the **Data** tab.
On the Layer page, click the **Inspect** tab to open the catalog.
Click a specific partition to see its decoded data.

* Inspect the metrics output catalog locally

Alternatively you can inspect the output catalog in the **Data** tab.
On the Layer page, select the **Inspect** tab. Click on "FAIL" under Partition ID.
Click **Download raw data** to save the raw partition data to disk.
You can then run protoc on the raw data to decode it, using:

`protoc --decode_raw < $PATH_TO_RAW_PARTITION_DATA`

The output is structured as follows:
1 {
  1 {
    1: "quickstartmetriccalc"
    2: 3
  }
  2: 23618394
  2: 23618304
  2: 23618412
  2: 23618385
  2: 23618349
  2: 23618313
  2: 23618322
  2: 23618358
  2: 23618367
  2: 23618430
  2: 23618376
  2: 23618403
  2: 23618331
  2: 23618340
  2: 23618421
}

The first item is our metric ID and severity enumeration, with 3 indicating CRITICAL. The second item is the list of tile IDs that contain single horizontal lines.
Now type "PASS" in the Search box and click Submit.
Click **Download raw data** to save the raw partition data to disk.
The decoded output for this raw partition will start with something like:

1 {
  1 {
    1: "quickstartmetriccalc"
    2: 0
  }
  2: 23618375
  2: 23618343
  2: 23618324
  2: 23618351
  2: 23618307
  2: 23618389
  2: 23618414
  ...

The enumeration of 0 indicates a metric severity of NONE, and the tile IDs listed are those which contained octagons in the original input catalog and received a test result of PASS in the testing pipeline.