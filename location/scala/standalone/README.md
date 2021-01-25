# GraphExample

This example shows how to create a traversable graph from the
`HERE Optimized Map for Location Library` catalog. The `routinggraph` layer
contains information about the connectivity between segments of the HERE Map
Content to allow the efficient traversal of the road topology. In the routing
graph, a `Vertex` represents a topology segment in a direction. The routing
graph uses `Edge`s for connectivity.

The example implements a basic graph algorithm,
[breadth-first search](https://en.wikipedia.org/wiki/Breadth-first_search).

The start vertex is an arbitrary `Vertex`. The example writes the output to
GeoJSON.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.GraphExample
```

# HereMapContentToOptimizedMapTranslationExample

This example shows how to convert `Vertex` references into HERE Map Content
segment URIs with a direction, and vice versa.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.HereMapContentToOptimizedMapTranslationExample
```

# MostProbablePathExample

This example shows how to navigate the graph representing the road network
topology, using properties to compute the transition probability between road
segments.

Computation of the transition probability depends on the angle between the roads
and the difference in the functional class.

Construction of the path follows a pure greedy rule and chooses the most
probable next link.

The example writes the output to a temporary file in GeoJSON format.

## Setup

To run the example, you need access to the following catalogs.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.MostProbablePathExample
```

# PathMatcherExample

This example creates a path matcher using the `geometry` layer as a spatial
index and `routinggraph` layer as the topology connectivity layer.

The example creates default emission and transition probability strategies used
to initialize the
[Hidden Markov Model](https://en.wikipedia.org/wiki/Hidden_Markov_model) based
path matcher.

The example writes the output to GeoJSON.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.PathMatcherExample
```

# PathMatcherWithCustomNetworkFilterExample

This example also creates a path matcher but filters the routing graph to
exclude road segments that are not traversable by taxis.

The example uses the filtered graph to create a custom `CandidateGenerator` and
`TransitionProbabilityStrategy` for the path matcher.  
All components of the path matcher that use the routing graph must use the same
(filtered) view on it.  
Otherwise, the the path matcher might produce inconsistent results.

The example prints its output to the console.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.PathMatcherWithCustomNetworkFilterExample
```

# PointMatcherExample

The point matcher is a simple point matching implementation for trips, showing
how to use the Proximity Search functionality of the library to search for
vertices around a location.

This example resolves and loads tiles from the `geometry` layer in order to
cover the area needed to map-match the sample trip coordinates. Every trip
element is resolved to the closest segment.

For visualization purposes, the example serializes the output to GeoJSON.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.PointMatcherExample
```

# FunctionalClassExample

This example shows how you can get the functional class for a vertex.

It looks up the vertices around a given location and produces a GeoJson file
containing all these vertices colored by functional class.

## Setup

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.FunctionalClassExample
```

# TurnRestrictionsExample

This example shows how to check if turns (transitions between adjacent vertices,
modeled as Edges) are restricted or not.

It looks up the two start vertices that represent the same road segment, figures
out whether turns to their adjacent vertices have restrictions, and produces a
GeoJson file containing the start vertices and all vertices that are directly
adjacent to a start vertex. The start vertices are shown in blue. An adjacent
vertex is shown in red, if turning from a start vertex to that vertex is
restricted, in gray otherwise.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.TurnRestrictionsExample
```

# OnTheFlyCompiledPropertyMapExample

This example shows how to load a generic attribute that is not available in the
`HERE Optimized Map for Location Library` using a `Vertex` reference.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.OnTheFlyCompiledPropertyMapExample
```

# AdasCurvatureAttributeExample

This example shows how to fetch and use ADAS attributes in the
`HERE Optimized Map for Location Library` using a `Vertex` or an `Edge`
reference.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.AdasCurvatureAttributeExample
```

# SparsePathMatcherExample

Another example on how to use the path matcher (see also the section
_PathMatcherExample_) that shows how to handle the case of sparse probes, and
the reconstruction of route segments between distant points.

The example writes the output to GeoJSON using different colours to highlight
the reconstructed segments.

## Setup

To run the example, you need access to the following catalog.

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.SparsePathMatcherExample
```

# ConvertTpeg2ContainerExample

In this example we read an OpenLR location reference that has been written in
the TPEG2 XML encoding and convert it to its binary representation.

## Setup

The example does not depend on map data.

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.ConvertTpeg2ContainerExample
```

# ExtractSpecificTpeg2MessagesExample

This example demonstrates how to load a TPEG2 document and extract parts of the
document that you are interested in. The functions available in `TpegExtractors`
allow you to extract specific application messages, or location references.

## Setup

The example does not depend on map data.

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.ExtractSpecificTpeg2MessagesExample
```

# TmcCreateAndResolveExample

An example of how to use the `location-referencing` module to create a TMC
reference and resolve it.

The example searches for a well-known vertex that is covered by TMC to define
the input location.

## Setup

To run the example, you need access to the following catalog:

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.TmcCreateAndResolveExample
```

# TmcResolveReferencesInRttiMessageExample

An example that demonstrates how TMC references in Real Time Traffic Incident
(RTTI) messages can be converted to TPEG2 TMC references, and how the
`location-referencing` module can be used to resolve those references.

RTTI messages are commonly found in the
[HERE Real Time Traffic catalog](https://platform.here.com/data/hrn:here:data::olp-here:olp-traffic-1/traffic-incidents-delta-volatile).
The example provides a sample message, so that access to the catalog is not
necessary when running it.

## Setup

To run the example, you need access to the following catalog:

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.TmcResolveReferencesInRttiMessageExample
```

# OlrCreateReferenceFromHmcSegmentsExample

This example shows how to take a path given as segments in HERE Map Content and create an OpenLR reference from it.

## Setup

To run the example, you need access to the following catalog:

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.OlrCreateReferenceFromHmcSegmentsExample
```

# OlrResolveReferenceToHmcSegmentsExample

This example shows how to take an OpenLR reference given in XML and to resolve this reference to segments in HERE Map Content.

## Setup

To run the example, you need access to the following catalog:

- [`HERE Optimized Map for Location Library`](https://platform.here.com/data/hrn:here:data::olp-here:here-optimized-map-for-location-library-2)

To run the example locally, use the following commands:

```bash
mvn --also-make --file=../.. --projects=:utils_2.11 install

mvn --projects=:scala-standalone_2.11 compile exec:java \
    -Dexec.mainClass=com.here.platform.example.location.scala.standalone.OlrResolveReferenceToHmcSegmentsExample
```
