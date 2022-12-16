# Compacting Index Layer Data in Protobuf Format

This example shows how to use the Index Compaction Library to quickly develop a compaction application that compacts Protobuf format data in an index layer.
There are two options for using this example:

1. Use the example as is. You can run the example out-of-the-box by creating input and output catalogs, then specifying the configuration.
   For information on the configuration, see the [Update the Configuration File](#update-the-configuration-file) section.
2. Create your own compaction application using the example as a template.

You should review this entire readme regardless of whether you are running the example as-is or using the example as a template for your own application.

The example consists of one user-defined function implementation example class:

- `ProtobufCompactionExample.java`

This class implements the `CompactionUDF` interface from the Index Compaction Library.
For details on this interface, see the _API Reference_ section of the [Index Compaction Library Developer Guide](#index-compaction-library-developer-guide).

This readme contains important instructions that will help you to run the _Index Compaction Library_ examples.

## Get Your Credentials

To run this example, you need two sets of credentials:

- **Platform credentials:** To get access to the platform data and resources, including HERE Map Content data for your pipeline input.
- **Repository credentials:** To download HERE Data SDK for Java and Scala libraries and Maven archetypes to your environment.

For more details on how to set up your credentials, see the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more details on how to verify that your platform credentials are configured correctly, see the [Verify Your Credentials](https://developer.here.com/documentation/java-scala-dev/dev_guide/verify-credentials/index.html) tutorial.

## Configure a Project

To follow this example, you will need a [project](https://developer.here.com/documentation/identity-access-management/dev_guide/topics/manage-projects.html). A project is a collection of platform resources
(catalogs, pipelines, and schemas) with controlled access. You can create a project through the
HERE platform portal.

Alternatively, use the OLP CLI [`olp project create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/project/project-commands.html#create-project) command to create the project:

```bash
olp project create $PROJECT_ID $PROJECT_NAME
```

The command returns the [HERE Resource Name (HRN)](https://developer.here.com/documentation/data-user-guide/user_guide/index.html) of your new project. Note down this HRN as you will need it later in this tutorial.

> #### Note
>
> You do not have to provide a `--scope` parameter if your app has a default scope.
> For details on how to set a default project scope for an app, see the _Specify a
> default Project_ for Apps chapter of the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

For more information on how to work with projects, see the [Organize your work in projects](https://developer.here.com/documentation/java-scala-dev/dev_guide/organize-work-in-projects/index.html) tutorial.

## Create Input Catalog and Layer

The examples require you to have a catalog with an `index` layer configured for the input data.

You can create a new catalog with the `index` layer if you do not have one.
When creating a new catalog and index layer, be sure to populate the index layer with Protobuf format data that can be compacted.
For testing purposes, you can ensure your data has common index attribute values so corresponding records having smaller files can be compacted to bigger files.

For this example, your index layer should have the following configuration:

- `ingestionTime` should be declared as `timewindow` type.
- `tileId` should be declared as `heretile` type.
- `eventType` should be declared as `string`.

Use the HERE platform portal to [create the input catalog](https://developer.here.com/documentation/data-user-guide/user_guide/portal/catalog-creating.html) in your project and [add the following layers](https://developer.here.com/documentation/data-user-guide/user_guide/portal/layer-creating.html):

| Layer ID | Layer Type | Retention | Timewindow Attribute Name | Duration | Content Type           | Content Encoding | Coverage |
| -------- | ---------- | --------- | ------------------------- | -------- | ---------------------- | ---------------- | -------- |
| index    | Index      | 7 days    | ingestionTime             | 60       | application/x-protobuf | uncompressed     | -        |

- For instructions on how to create a catalog, see _Create a Catalog_ in the [Data User Guide](#data-user-guide).
- For instructions on how to create a layer, see _Create a Layer_ in the [Data User Guide](#data-user-guide).
- For instructions on how to link a resource to a project, see _Project Resource Link_ in the [Command Line Interface Developer Guide](#command-line-interface-developer-guide).
- For instructions on how to share your project, see _Manage Projects_ in the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

Alternatively, you can use the _Data Commands_ in [Command Line Interface Developer Guide](#command-line-interface-developer-guide) instead of the platform portal to create a new catalog with an `index` layer:

> #### Note
>
> We recommend you set your parameters to environment variables so that you can easily copy and execute the following commands.

1. Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create the catalog.
   Make sure you record the HRN returned by the following command for later use:

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY \
        --description CATALOG_DESCRIPTION --scope $PROJECT_HRN
```

2. Use the [`olp catalog layer add`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/layer-commands.html#catalog-layer-add) command to add an `index` layer to your catalog:

```bash
olp catalog layer add $CATALOG_HRN index index --index --summary "index" \
        --description "index" --content-type application/x-parquet --ttl 7.days \
        --index-definitions tileId:heretile:8 ingestionTime:timewindow:3600000 eventType:string \
        --scope $PROJECT_HRN
```

> #### Note::
>
> If a billing tag is required in your realm, use the `--billing-tags: "YOUR_BILLING_TAG"` parameter.

## Populate Index Layer with Sample Data

After creating input catalog and layer, for running the compaction example,
you should populate the index layer with the [sample data](src/test/resources/sampleData) that has common index attribute values
so corresponding records having smaller files can be compacted to bigger files.

1. Use the [`olp catalog layer partition put`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-put) command to populate the `index` layer.

```bash
olp catalog layer partition put $CATALOG_HRN index \
        --input src/test/resources/sampleData \
        --index-fields timewindow:ingestionTime:1594236600000 heretile:tileId:79963 string:eventType:SignRecognition \
        --scope $PROJECT_HRN
```

## Create Output Catalog

> #### Note
>
> The HERE platform does not allow the same catalog to be used as both input and output for batch pipelines.
> For Index Compaction Library, input and output catalog are the same as the library compacts the same index layer.
> You should specify the desired catalog to be compacted under the `input-catalogs` setting.
> For the `output-catalog` setting, you still need to pass a valid catalog.
> You can use a catalog with zero layers.

- For instructions on how to create a catalog, see _Create a Catalog_ in the [Data User Guide](#data-user-guide).
- For instructions on how to link a resource to a project, see _Project Resource Link_ in the [Command Line Interface Developer Guide](#command-line-interface-developer-guide).
- For instructions on how to share your project, see _Manage Projects_ in the [Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

Alternatively, you can use the _Data Commands_ in [Command Line Interface Developer Guide](#command-line-interface-developer-guide) instead of the platform portal to create a new catalog:

1. Use the [`olp catalog create`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/catalog-commands.html#catalog-create) command to create the catalog.

```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY \
        --description CATALOG_DESCRIPTION --scope $PROJECT_HRN
```

## Update the Configuration File

You have to modify the settings in the [src/main/resources/application.conf](src/main/resources/application.conf) file to configure the expected behavior of index compaction job.

In addition, you have to specify the input and output catalogs for your application.

- When using the HERE platform portal, you will get an option while creating the pipeline version.
- When using the OLP CLI, you have to modify the [config/pipeline-config.conf](config/pipeline-config.conf) file.
- When running on your local IDE, you have to modify the [src/test/resources/pipeline-config.conf](src/test/resources/pipeline-config.conf) file.

For information about modifying these files, see the comments in each configuration file.

## Package the Application Into a Fat JAR

To run the compaction pipeline in the HERE platform, you need to build a fat JAR.

You can see the `pom.xml` file for packaging details if you are creating your own application.
Otherwise, run the following command under the example's base directory:

```bash
mvn clean package
```

Once the above command is successful, a fat JAR named `index-compaction-protobuf-example-<VERSION>-platform.jar` will be built in the `target` folder.

## Run on the HERE platform

To run the example, create a pipeline in the HERE Workspace to execute the application.

### Configure and Run as a Platform Pipeline

You should use the application jar with the `platform.jar` suffix as a pipeline template to run the compaction process. The pipeline will use the configuration from the [application.conf](src/main/resources/application.conf) file.
directory, ensure these values are updated before compiling and uploading your jar.

#### Use the Platform Portal to Run a Pipeline

For information on using the HERE Workspace to configure and run a pipeline, see _Deploying a Pipeline via Web Portal_ in [Pipelines Developer Guide](#pipelines-developer-guide).
Update the logging level of your pipeline from `WARN` to `INFO` if you intend to verify message upload in logs.

#### Use the OLP CLI to Run a Pipeline

You can use the _Pipeline Commands_ in the [Command Line Interface Developer Guide](#command-line-interface-developer-guide) to create pipeline components and activate the version.

First, configure the data source using the [config/pipeline-config.conf](config/pipeline-config.conf) file. This file contains the configuration of the data source which will be used for the Index Compaction Library examples:

```javascript
pipeline.config {
  output-catalog {
    hrn = "YOUR_OUTPUT_CATALOG_HRN"
  }
  input-catalogs {
    source {
      hrn = "YOUR_INPUT_CATALOG_HRN"
    }
  }
}
```

> #### Note
>
> The HERE platform does not allow the same catalog to be used as both input and output for batch pipelines.
> For Index Compaction Library, input and output catalog are the same as the library compacts the same index layer.
> You should specify the desired catalog to be compacted under the `input-catalogs` setting.
> For the `output-catalog` setting, you still need to pass a valid catalog.
> You can use a catalog with zero layers.

To find the HRN, in the [HERE platform portal](#here-platform-portal) navigate to your catalog. The HRN is displayed in the upper left corner of the page.

You can use the OLP CLI to create pipeline components and activate the pipeline version with the following commands:

1. [Create](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline-workflows.html) pipeline components:

```bash
olp pipeline create $PIPELINE_NAME --scope $PROJECT_HRN
olp pipeline template create $PIPELINE_TEMPLATE_NAME batch-3.0 $PATH_TO_JAR com.here.platform.index.compaction.batch.Driver \
        --input-catalog-ids=source --workers=2 --scope $PROJECT_HRN
olp pipeline version create $PIPELINE_VERSION_NAME $PIPELINE_ID $PIPELINE_TEMPLATE_ID \
        $PATH_TO_CONFIG_FOLDER/pipeline-config.conf --scope $PROJECT_HRN
```

2. [Set](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-log-set-level) log level:

```bash
olp pipeline version log level set $PIPELINE_ID $PIPELINE_VERSION_ID --root info --scope $PROJECT_HRN
```

3. [Activate](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/pipeline/version-commands.html#pipeline-version-activate) the pipeline version:

```bash
olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID --scope $PROJECT_HRN
```

For more information on using the OLP CLI to configure and run a pipeline, see _Pipeline Commands_ in the [Command Line Interface Developer Guide](#command-line-interface-developer-guide).

### Monitor the Pipeline

In the `Pipelines` page on the [HERE platform portal](#here-platform-portal), find your pipeline and ensure that it is in the `Running` state.
For additional information on monitoring pipelines, see _Pipeline Monitoring_ in [Logs, Monitoring and Alert](#logs-monitoring-and-alert).
You can use pipeline version wait command to check if the pipeline completes successfully:

```bash
olp pipeline version wait $PIPELINE_ID $PIPELINE_VERSION_ID --job-state=completed
```

## Run on the Local IDE

You can also run the compaction example in your local IDE.
You should update values in the following files:

1. Update the input and output settings in the [src/test/resources/pipeline-config.conf](src/test/resources/pipeline-config.conf) file.
2. Update your configuration in the [src/main/resources/application.conf](src/main/resources/application.conf) file.
3. Update your credentials in the [src/test/resources/credentials.properties](src/test/resources/credentials.properties) file.
   If you want to use your platform credentials in `~/.here/credentials.properties`, delete the [src/test/resources/credentials.properties](src/test/resources/credentials.properties) file.
4. Optionally, to use a custom logger, modify the [src/test/resources/log4j.properties](src/test/resources/log4j.properties) file.

Once you have made your updates, run the Java class [ProtobufCompactionExampleRunner.java](src/test/java/com/here/platform/index/compaction/batch/runner/ProtobufCompactionExampleRunner.java).
You have to ensure either the Maven profile `add-dependencies-for-IDEA` is selected
or the checkbox for `Include dependencies with "Provided" scope` in `Edit Configurations` for `ProtobufCompactionExampleRunner.java` is selected.

## Verify the Output

Once the compaction pipeline has completed, you can query the compacted data using one of the following methods:

- _Get Data from an Index Layer_ in the [Data API Developer Guide](#data-api-developer-guide)
- _Get Data_ in the [Data Client Library Developer Guide](#data-client-library-developer-guide)
- _Partitions_ in the [Command Line Interface Developer Guide](#command-line-interface-developer-guide)

For verifying the output of running the compaction example,

1. Use the [`olp catalog layer partition get`](https://developer.here.com/documentation/open-location-platform-cli/user_guide/topics/data/partition-commands.html#catalog-layer-partition-get) command to query the `index` layer.

```bash
olp catalog layer partition get $CATALOG_HRN index \
        --filter "size=gt=0" \
        --scope $PROJECT_HRN
```

If you populated your `index` layer with [sample data](src/test/resources/sampleData) using the command in [Populate Index Layer with Sample Data](#populate-index-layer-with-sample-data) section,
you should get 1 partition on querying your `index` layer.
It means that all your small files successfully compacted in one big file.
You can further parse the file/partition to verify the contents.

## Troubleshooting

If you have any trouble accessing logs, monitoring, investigating failures and so on, see `FAQ` in [Index Compaction Library Developer Guide](#index-compaction-library-developer-guide).

## Reference

- ##### HERE Platform Portal

  - RoW: https://platform.here.com
  - China: https://platform.hereolp.cn

- ##### Index Compaction Library Developer Guide

  - RoW: https://developer.here.com/documentation/index-compaction-library/dev_guide/index.html
  - China: https://developer.here.com/documentation/index-compaction-library/dev_guide/index.html

- ##### Data API Developer Guide

  - RoW: https://developer.here.com/documentation/data-api/data_dev_guide/index.html
  - China: https://developer.here.com/cn/documentation/data-api/data_dev_guide/index.html

- ##### Data Client Library Developer Guide

  - RoW: https://developer.here.com/documentation/data-client-library/dev_guide/index.html
  - China: https://developer.here.com/cn/documentation/data-client-library/dev_guide/index.html

- ##### Command Line Interface Developer Guide

  - RoW: https://developer.here.com/documentation/open-location-platform-cli/user_guide/index.html
  - China: https://developer.here.com/cn/documentation/open-location-platform-cli/user_guide/index.html

- ##### Data User Guide

  - RoW: https://developer.here.com/documentation/data-user-guide/index.html
  - China: https://developer.here.com/cn/documentation/data-user-guide/index.html

- ##### Pipelines Developer Guide

  - RoW: https://developer.here.com/documentation/pipeline/index.html
  - China: https://developer.here.com/cn/documentation/pipeline/index.html

- ##### Identity & Access Management Developer Guide

  - RoW: https://developer.here.com/documentation/identity-access-management/dev_guide/index.html
  - China: https://developer.here.com/documentation/identity-access-management/dev_guide/index.html

- ##### Logs, Monitoring and Alert

  - RoW: https://developer.here.com/documentation/metrics-and-logs/user_guide/index.html
  - China: https://developer.here.com/cn/documentation/metrics-and-logs/user-guide/index.html
