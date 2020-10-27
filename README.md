# HERE Workspace Examples for Java and Scala Developers

## Introduction

This repository holds a series of Java and Scala examples, that demonstrate  typical use cases for the HERE Workspace – a part of HERE platform. HERE Workspace is an environment to enrich, transform and deploy location-centric data.
Go to [HERE platform](https://developer.here.com/products/open-location-platform) to learn more. If you are interested in knowing what the platform offers specifically for Java and Scala developers, visit [this page](https://developer.here.com/olp/documentation/sdk-developer-guide/dev_guide/index.html).

## Prerequisites

In order to run the examples, you need to have a HERE Workspace account. If you do not have an account, navigate to [Pricing and Plans](https://developer.here.com/pricing/open-location-platform) to apply for a free trial.

You need to get access credentials and prepare your environment. For more information on how to prepare your environment, see our [guide for Java and Scala developers](https://developer.here.com/olp/documentation/sdk-developer-guide/dev_guide/topics/how-to-use-sdk.html).

## Code Examples

### Processing Sensor Data

The following documents illustrate two use cases around inferring real-world situations from sensor data. [Batch processing](https://developer.here.com/olp/documentation/java-scala-dev/dev_guide/topics/example-use-cases.html#use-case-map-of-recommended-speed-based-on-sensor-observations-and-other-data) is useful when it is important to aggregate sensor input over a longer time period (i.e. hours and longer). [Stream processing](https://developer.here.com/olp/documentation/java-scala-dev/dev_guide/topics/example-use-cases.html#use-case-turning-sensor-data-into-warnings) is recommended for time-critical use cases, like informing about road hazards.

| Name | Description | Source | Labels / Topics |
| ---- | ----------- | ------ | --------------- |
| Infer Stop Sign Locations from Automotive Sensor Observations | The example takes archived sensor data, clusters and path-matches it in a distributed Spark batch environment, and creates a map layer with stop signs at the output. | [Scala](location/scala/spark/infer-stop-signs-from-sensors) | Location Library, Data Client Library, Spark, Batch, GeoJSON, SDII |
| Stream Path Matcher | The example stands for a similar but time critical use case. It does not hash out anything but map matching. It takes sensor data from a stream, map-matches it in Flink, and puts on an output stream. |  [Java](location/java/flink/stream-path-matcher)  | Location Library, Data Client Library, Flink, Stream, SDII  |

### Incremental Map Processing & Validation

For more information, see a use case illustration of [keeping a client map up to date with incremental processing](https://developer.here.com/olp/documentation/java-scala-dev/dev_guide/topics/example-use-cases.html#use-case-incremental-map-processing).

| Name | Description | Source | Labels / Topics |
| ---- | ----------- | ------ | --------------- |
| Geometry Lifter | An application that takes level 12 partitions of road topology and geometry and aggregates them to higher-level (i.e. bigger) partitions. | [Java](data-processing/java/geometry-lifter) / [Scala](data-processing/scala/geometry-lifter) | Data Processing Library, Spark, Batch, Protobuf, HERE Map Content |
| Pedestrian Topologies Extraction to GeoJSON | Topologies, accessible by pedestrians, are selected based on the segment attributes and then are transformed into GeoJSON file format and stored in a new catalog. | [Java](data-processing/java/pedestrian-topologies-extraction-geojson) / [Scala](data-processing/scala/pedestrian-topologies-extraction-geojson) | Data Processing Library, Spark, Batch, GeoJSON, HERE Map Content |
| Pedestrian Topologies Extraction to Protobuf | Topologies, accessible by pedestrians, are selected based on the segment attributes and then are transformed to a newly created proto schema format and stored in a new catalog layer that follows that schema. | [Java](data-processing/java/pedestrian-topologies-extraction-protobuf) / [Scala](data-processing/scala/pedestrian-topologies-extraction-protobuf) | Data Processing Library, Spark, Batch, Protobuf, HERE Map Content |
| Statistics creation across multiple processing runs with stateful processing | The application counts how often the node cardinality of the topology changes in each partition. | [Java](data-processing/java/stateful-nodecardinality-extraction) / [Scala](data-processing/scala/stateful-nodecardinality-extraction) | Data Processing Library, Spark, Batch, JSON, HERE Map Content |
| Here Map Content Diff-Tool | An application to compare the content of two different versions of an input catalog. | [Java](data-processing/java/heremapcontent-difftool) / [Scala](data-processing/scala/heremapcontent-difftool) | Data Processing Library, Spark, Batch, JSON, HERE Map Content  |
| Here Map Content Validation | An application to validate road topology and geometry content against a set of acceptance criteria using [scalatest](www.scalatest.org). | [Scala](data-processing/scala/heremapcontent-validation) | Data Processing Library, Spark, Batch, JSON, HERE Map Content  |
| Data Validation and Testing Part 1: Content Testing | The test considers input partitions containing an octagon as "PASS" tiles and partitions containing a straight line as "FAIL". It records the test result in a new output catalog. | [Java](data-validation/java/quick-start/testing) / [Scala](data-validation/scala/quick-start/testing) | Data Validation Library, Spark, Batch, Protobuf |
| Data Validation and Testing Part 2: Metrics | Reads the output of the testing component, assigns an error severity of CRITICAL for the "FAIL" tiles in the test results, aggregates the failed tile IDs and records the metrics in a new output catalog. | [Java](data-validation/java/quick-start/metrics) / [Scala](data-validation/scala/quick-start/metrics) | Data Validation Library, Spark, Batch, Protobuf |
| Data Validation and Testing Part 3: Assessment | Reads the output of the metrics component, gives a final value of FAIL, if more than 10% of the candidate tiles have CRITICAL errors, and writes the result to a new catalog. | [Java](data-validation/java/quick-start/assessment) / [Scala](data-validation/scala/quick-start/assessment) | Data Validation Library, Spark, Batch, Protobuf |
| Data Validation and Testing: Comparison | A test that compares two different versions of a catalog layer to validate it. | [Java](data-validation/java/quick-start/comparison) / [Scala](data-validation/scala/quick-start/comparison) | Data Validation Library, Spark, Batch, Protobuf |

### Archiving Stream Data

The HERE Workspace allows you to retain stream data for longer periods, which allows you to later query and process the retained data for non-real-time use cases.
For more information, see [Data Archiving Library Developer Guide](https://developer.here.com/olp/documentation/data-archiving-library/dev_guide/index.html).

| Name | Description | Source | Labels / Topics |
| ---- | ----------- | ------ | --------------- |
| Archiving SDII stream data in Avro | The example shows how to use the Data Archiving Library to quickly develop an archiving solution that archives data in Avro format. | [Java](data-archive/java/avro-example) | Data Archiving Library, Flink, Stream, Avro, SDII |
| Archiving SDII stream data in Parquet | The example shows how to use the Data Archiving Library to quickly develop an archiving solution that archives data in Parquet format. | [Java](data-archive/java/parquet-example) | Data Archiving Library, Flink, Stream, Parquet, SDII |
| Archiving SDII stream data in Protobuf | The example shows how to use the Data Archiving Library to quickly develop an archiving solution that archives data in Protobuf format. | [Java](data-archive/java/protobuf-example) | Data Archiving Library, Flink, Stream, Protobuf, SDII|
| Archiving SENSORIS stream data in Parquet| The example shows how to use the Data Archiving Library to quickly develop an archiving solution that archives SENSORIS data in Parquet format. | [Java](data-archive/java/sensoris-parquet-example) | Data Archiving Library, Flink, Stream, SENSORIS, Parquet |
| Archiving SENSORIS stream data in Protobuf| The example shows how to use the Data Archiving Library to quickly develop an archiving solution that archives SENSORIS data in Protobuf format. | [Java](data-archive/java/sensoris-protobuf-example) | Data Archiving Library, Flink, Stream, SENSORIS, Protobuf |

### Compacting Index Data

The HERE Workspace allows you to compact data files with the same index attribute values into one or more files based on the configuration.
Compaction reduces the index layer storage cost, improves query performance, and makes subsequent data processing more efficient.
For more information, see [Index Compaction Library Developer Guide](https://developer.here.com/olp/documentation/index-compaction-library/dev_guide/index.html).

| Name | Description | Source | Labels / Topics |
| ---- | ----------- | ------ | --------------- |
| Compacting Parquet format indexed data | The example shows how to use the Index Compaction Library to quickly develop a compaction application that compacts parquet format data. | [Java](index-compaction-batch/java/parquet-example) | Index Compaction Library, Spark, Batch, Parquet, SDII |
| Compacting Protobuf format indexed data | The example shows how to use the Index Compaction Library to quickly develop a compaction application that compacts protobuf format data. | [Java](index-compaction-batch/java/protobuf-example) | Index Compaction Library, Spark, Batch, Parquet, SDII | 

### Small Examples Showing Usage of Location Library

The following examples demonstrate how to use the Location Library. Sources can be found [here for Java](location/java/standalone) and [here for Scala](location/scala/standalone).

| Name | Description | Labels / Topics |
| ---- | ----------- | --------------- |
| Point Matching | Takes probe points and matches each one against the closest geometry without consider the path. | Location Library, GeoJSON, CSV |
| Traversing the Graph | Shows how to create a traversable graph from the HERE Optimized Map for Location Library catalog. | Location Library, GeoJSON |
| Most Probable Path | Navigates the graph along the most probable path based on simple assumptions like try to stay on same functional class. | Location Library, GeoJSON |
| Path Matching | Matches path probe points against the graph. | Location Library, GeoJSON, CSV |
| Path Matching with restrictions | Matches path probe points against a graph that excludes segments that are not accessible by taxis. | Location Library, GeoJSON, CSV |
| Turn Restrictions | Shows how to check if turns on a road network are restricted or not. | Location Library, GeoJSON |
| Generic Range Based Attributes | Shows how to load a generic attribute that is not available in the HERE Optimized Map for Location Library using a Vertex reference as input. | Location Library |
| Path Matching Sparse Probe Data | Shows how to match sparse path points against the graph by traversing it using the most probable path assumptions. | Location Library, GeoJSON, CSV |
| Converting references from HERE Optimized Map for Location Library to HERE Map Content | Converts Vertex references to Topology Segments and vice versa. | Location Library, HERE Map Content |
| Converting references from TPEG2 to its binary representation | Shows how to read an OpenLR location reference that has been written in the TPEG2 XML encoding and convert it to its binary representation.| Location Library, TPEG2, OpenLR|
| Extracting TPEG2 document | Demonstrates how to load a TPEG2 document and extract its parts.| Location Library, TPEG2 |
| Creating and resolving TMC reference | Searches for a well-known vertex that is covered by TMC to define the input location.| Location Library, TMC |
| Resolving TMC references in RTTI message | Demonstrates how TMC references in Real Time Traffic Incident (RTTI) messages can be converted to TPEG2 TMC references, and how the `location-referencing` module can be used to resolve those references. | Location Library, TPEG2 |
| Creating OpenLR reference from road segments | Transforms a path given as segment references in HERE Map Content to OpenLR reference. | Location Library, HERE Map Content, OpenLR |
| Resolving OpenLR reference from road segments | Shows how to take an OpenLR reference given in XML and resolve this reference to segments in HERE Map Content | Location Library, HERE Map Content, OpenLR |

## License

Copyright (C) 2017-2020 HERE Europe B.V.

Unless otherwise noted in `LICENSE` files, source code files for specific files or directories, the [LICENSE](LICENSE) in the root applies to all content in this repository.
