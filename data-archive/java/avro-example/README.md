# Archiving SDII stream data in Avro

This example shows how to use the Data Archiving Library to quickly develop an archiving solution that archives data in Avro format.
There are two options for using this example:

1. Use the example as-is. You can run the example out of the box by creating an input and output catalog, then specifying those catalogs in the `application.conf` file. See the **Update the Configuration File** section below for information on configuring this file.
2. Create your own archiving application using the example as a template.

You should review this entire readme regardless of whether you are running the example as-is or using the example as a template for your own application.

The example consists of two user defined function implementation example classes:

- AvroSimpleKeyExample.java
- AvroMultiKeysExample.java

These classes implement the SimpleUDF or MultiKeysUDF interface from the Data Archiving Library. You can choose which class to use depending on whether you want to use one value per indexing attribute (SimpleUDF) or multiple values per indexing attribute (MultiKeysUDF). See the `API Reference` section of the [Data Archiving Library Developer Guide](#data-archiving-library-dev-guide) for details on this and other interfaces.

This readme contains important instructions that will help you to run the `Data Archiving Library` examples.


## Create Input Catalog and Layer

The examples require you to have a catalog with `stream` layer for the input data. Please create a new catalog with `stream` layer or use an existing one.

Use OLP Portal to create new catalog with `stream` layer:

| Layer ID               | Layer Type | Content Type             | Content Encoding | Coverage
|------------------------|------------|--------------------------|------------------|---------------
| stream 				 | Stream  	  | application/octet-stream | uncompressed     | -

- For instructions on how to create a catalog, please refer to `Create a catalog` in [Data User Guide](#data-user-guide).
- For instructions on how to create a layer, please refer to `Create a Layer` in [Data User Guide](#data-user-guide).

OLP Pipelines are managed by group. Therefore, please grant `read` access to your group id so your pipeline can read from `stream` layer.
- For instructions on how to manage groups, please refer to `Manage Groups` in [Teams and Permissions User Guide](#teams-and-permissions-user-guide).
- For instructions on how to share your catalog, please refer to `Share a Catalog` in [Data User Guide](#data-user-guide).
- For instructions on how to share your project, please refer to `Manage Projects` in [Teams and Permissions User Guide](#teams-and-permissions-user-guide).

Alternatively, you can use the `OLP CLI Commands` in [Command Line Interface Developer Guide](#cli-developer-guide) instead of the Portal to create a new catalog with a `stream` layer:

* Create input catalog and grant `read` permission to your group:
```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY --description CATALOG_DESCRIPTION
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read
```
* Add `stream` layer to created catalog:
```bash
olp catalog layer add $CATALOG_HRN stream stream --stream --summary "stream" --description "stream" --content-type application/octet-stream
```

## Create Output Catalog and Layer

The examples require that you define indexing attributes with the following index definitions for the `index` layer:

- `ingestionTime` should be declared as `timewindow` type. There should be one, and ONLY one `timewindow` column  and it is mandatory for any `index` layer.
- `tileId` should be declared as `heretile` type. There should be at most one `heretile` column. It is optional.
- `eventType` should be declared as `string`.

Use OLP Portal to create new catalog with `index` layer:

| Layer ID               | Layer Type |  Retention | Timewindow Attribute Name  |	Duration | Content Type             | Content Encoding | Coverage
|------------------------|------------|------------|----------------------------|------------|--------------------------|------------------|---------------
| index                  | Index      |  7 days    | ingestionTime	            |   60       | application/x-avro-binary| uncompressed     | -

- For instructions on how to create a catalog, please refer to `Create a catalog` in [Data User Guide](#data-user-guide).
- For instructions on how to create a layer, please refer to `Create a Layer` in [Data User Guide](#data-user-guide).

OLP Pipelines are managed by group. Therefore, please grant `read` and `write` access to your group id so your pipeline can read and write to `index` layer.
- For instructions on how to manage groups, please refer to `Manage Groups` in [Teams and Permissions User Guide](#teams-and-permissions-user-guide).
- For instructions on how to share your catalog, please refer to `Share a Catalog` in [Data User Guide](#data-user-guide).
- For instructions on how to share your project, please refer to `Manage Projects` in [Teams and Permissions User Guide](#teams-and-permissions-user-guide).

Alternatively, you can use the `OLP CLI Commands` in [Command Line Interface Developer Guide](#cli-developer-guide) instead of the Portal to create a new catalog with an `index` layer:

* Create output catalog and grant `read` and `write` permissions to your group:
```bash
olp catalog create $CATALOG_ID $CATALOG_NAME --summary CATALOG_SUMMARY --description CATALOG_DESCRIPTION
olp catalog permission grant $CATALOG_HRN --group $GROUP_ID --read --write
```
* Add `index` layer to created catalog:
```bash
olp catalog layer add $CATALOG_HRN index index --index --summary "index" --description "index" --content-type application/x-avro-binary --ttl 7.days --index-definitions tileId:heretile:8 ingestionTime:timewindow:3600000 eventType:string
```

## Update the Configuration File

The configurations are specified in the `application.conf` found in the example's `/src/main/resources` directory of the example project. You will have to modify it to configure expected behavior of the data archiving project. For information about modifying this file, see the comments in the `application.conf` file.

> #### Note
> You must configure this file in order for the examples to run successfully.

> #### Note
> The property `aggregation.window-seconds` is different from the index attribute of type `timewindow`.
> The property `aggregation.window-seconds` determines how frequently data will be aggregated and processed by the data archiving pipeline.
> The index attribute of type `timewindow` contains the finest time granularity at which the data will be indexed and later queried, as specified in the attribute's `duration` field.

## Package the Application Into a Fat JAR

To run archiving pipeline in the HERE Open Location Platform, you need to build a fat JAR.

You can refer to the `pom.xml` file for packaging details if you are creating your own application. If you are not creating your own application, run the following command under the example's base directory.
```bash
mvn clean package
```

Once the above command is successful, a fat JAR named `data-archive-avro-example-<VERSION>-platform.jar` will be built in the `target` folder.

## Create a Pipeline on the Platform

To run the example, create a pipeline in the HERE Open Location Platform to execute the application.

### Get a Group ID for the App

To create a pipeline, you need to provide a group ID. You can get this group ID on your `Profile` in [OLP Portal](#portal) page. Note that your app must be part of that group. If you or your app do not belong to a group, ask your organization's OLP administrator to assign you or your app to a group.

### Configure and Run as an Open Location Platform Pipeline

You should use application jar with suffix `platform.jar` as a pipeline template to run the archiving process. The pipeline will use the configuration from file `application.conf` in the src/main/resource/
directory, please ensure these values are updated before compiling and uploading your jar.

#### Using the Portal to Run a Pipeline

For information on using the Portal to configure and run a pipeline, see `Deploying a Pipeline via Web Portal` in [Pipelines Developer Guide](#pipelines-developer-guide). Update the logging level of your pipeline from `WARN` to `INFO` if you intend to verify message upload in logs.

#### Using the Command Line Interface to Run a Pipeline

You can use the `OLP CLI Commands` in [Command Line Interface Developer Guide](#cli-developer-guide) to create pipeline components and activate it.

First, configure data sources using the `config/pipeline-config.conf` file. This file contains the permanent configuration of the data sources for Data Archiving Library examples:

```javascript
pipeline.config {
  output-catalog {hrn = "YOUR_OUTPUT_CATALOG_HRN"}
  input-catalogs {
    source {hrn = "YOUR_INPUT_CATALOG_HRN"}
  }
}
```

You must replace `YOUR_OUTPUT_CATALOG_HRN` with the HRN of your output catalog and `YOUR_INPUT_CATALOG_HRN` with the HRN of your input catalog. To find the HRN, in the [OLP Portal](#portal), navigate to your catalog. The HRN is displayed in the upper left corner of the page.

After you have configured data sources, use these CLI commands to create pipeline components and activate it:

- Create pipeline components

  ```bash
   olp pipeline create $PIPELINE_NAME $GROUP_ID
   olp pipeline template create $PIPELINE_TEMPLATE_NAME stream-2.0.0 $PATH_TO_JAR com.here.platform.dal.DALMain $GROUP_ID --input-catalog-ids=source --workers=2 # Note that the value of workers should be greater than or equal to the value of parallelism selected in application.conf
   olp pipeline version create $PIPELINE_VERSION_NAME $PIPELINE_ID $PIPELINE_TEMPLATE_ID "$PATH_TO_CONFIG_FOLDER/pipeline-config.conf"
  ```

- Set log level

  ```bash
  olp pipeline version log level set $PIPELINE_ID $PIPELINE_VERSION_ID --root info
  ```

- Activate pipeline version

  ```bash
  olp pipeline version activate $PIPELINE_ID $PIPELINE_VERSION_ID
  ```

For more information on using the `OLP CLI` to configure and run a pipeline, see `Pipeline Commands` in [Command Line Interface Developer Guide](#cli-developer-guide).

## Verify the Output

In the [OLP Portal](#portal) select the _Pipelines_ tab and find your pipeline.
- Verify pipeline is in `Running` state.

After the pipeline is running, you can ingest your data into the `stream` layer created in the `Create Input Catalog and Layer` section using one of the following:
- `Publish to a Stream Layer` in [Data API Developer Guide](#data-api-developer-guide)
- `Publish Data` in [Data Client Library Developer Guide](#data-client-developer-guide)
- `Stream` in [Command Line Interface Developer Guide](#cli-developer-guide)

#### Note
> For ingesting data, please make sure your app has `read` and `write` permission to your `stream` layer.
> For instructions on how to manage app, please refer to `Manage Apps` in [Teams and Permissions User Guide](#teams-and-permissions-user-guide).
  ```
  olp catalog permission grant $CATALOG_HRN --app $APP_ID --read --write
  olp catalog layer stream put $CATALOG_HRN stream --input /path/to/directory # There are sdii example messages in the folder: src/test/resources/sampleData
  ```

After your data is archived in `index` layer, you can query/retrieve data using one of the following:
- `Get Data from an Index Layer` in [Data API Developer Guide](#data-api-developer-guide)
- `Get Data` in [Data Client Library Developer Guide](#data-client-developer-guide)
- `Partitions` in [Command Line Interface Developer Guide](#cli-developer-guide)
#### Note
> For querying metadata or retrieving data, please make sure your app has `read` permission to your `index` layer.
> For instructions on how to manage app, please refer to `Manage Apps` in [Teams and Permissions User Guide](#teams-and-permissions-user-guide).
  ```
  olp catalog permission grant $CATALOG_HRN --app $APP_ID --read --write
  olp catalog layer partition list $CATALOG_HRN index --filter <query> # E.g: <query>="tileId==92259"
  olp catalog layer partition get $CATALOG_HRN index --filter <query>
  ```

To parse the data retrieved from `index` layer, please refer to "How to parse the output content" in `FAQ` in [Data Archiving Library Developer Guide](#data-archiving-library-dev-guide).

## Run Example Locally

Besides running this example in a pipeline, you can also `Run an Archiving Application Locally`, please refer to [Data Archiving Library Developer Guide](#data-archiving-library-dev-guide).
Running locally in your IDE or Flink cluster will use the configuration from file `application.conf` in the src/test/resource/ directory, please ensure these values are updated before compiling and uploading your jar.
Refer to `Manage Apps` in [Teams and Permissions User Guide](#teams-and-permissions-user-guide) to create a new application and get `credentials.properties`. You will share read/write permissions with this app when you create your `stream` and `index` layers.

## Troubleshooting

If you have any trouble about accessing logs, monitoring, investigating failures and so on, please refer to `FAQ` in [Data Archiving Library Developer Guide](#data-archiving-library-dev-guide).

## References

- OLP Portal
    * RoW: https://platform.here.com
    * China: https://platform.hereolp.cn
- Data Archiving Library Developer Guide
     * RoW: https://developer.here.com/olp/documentation/data-archiving-library/dev_guide/index.html
     * China: https://developer.here.com/olp/cn/documentation/data-archiving-library/dev_guide/index.html
- Command Line Interface Developer Guide
    * RoW: https://developer.here.com/olp/documentation/open-location-platform-cli/user_guide/index.html
    * China: https://developer.here.com/olp/cn/documentation/open-location-platform-cli/user_guide/index.html
- Data API Developer Guide
     * RoW: https://developer.here.com/olp/documentation/data-api/data_dev_guide/index.html
     * China: https://developer.here.com/olp/cn/documentation/data-api/data_dev_guide/index.html
- Data Client Library Developer Guide
     * RoW: https://developer.here.com/olp/documentation/data-client-library/dev_guide/index.html
     * China: https://developer.here.com/olp/cn/documentation/data-client-library/dev_guide/index.html
- Data User Guide
    * RoW: https://developer.here.com/olp/documentation/data-user-guide/index.html
    * China: https://developer.here.com/olp/cn/documentation/data-user-guide/index.html
- Pipelines Developer Guide
    * RoW: https://developer.here.com/olp/documentation/pipeline/index.html
    * China: https://developer.here.com/olp/cn/documentation/pipeline/index.html
- Teams and Permissions User Guide
    * RoW: https://developer.here.com/olp/documentation/access-control/user-guide/index.html
    * China: https://developer.here.com/olp/cn/documentation/access-control/user-guide/index.html