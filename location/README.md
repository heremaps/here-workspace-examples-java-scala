# Location Library Examples

The Location Library examples are divided in four groups:

- [java-flink-stream-path-matcher](java/flink/stream-path-matcher/README.md)
  uses the Java API of the `Location Library` in an Apache Flink powered
  pipeline.
- [scala-spark-infer-stop-signs-from-sensors_2.11](scala/spark/infer-stop-signs-from-sensors/README.md)
  uses the distributed clustering and path matching Scala API of the
  `Location Library` together with the `HERE Optimized Map for Location Library`
  and the `Data Client Library` to derive the positions of stop signs in an
  Apache Spark powered pipeline.
- [scala-standalone_2.11](scala/standalone/README.md) uses the Scala high level
  API of the `Location Library` locally.
- [java-standalone](java/standalone/README.md) uses the Java high level API of
  the `Location Library` locally.

The `location` directory contains parent `pom.xml` files for managing
commonalities between examples projects. To import any of the examples above
into your IDE, use the `pom.xml` file in the root folder of the relevant example
group.

## [Prerequisites](#)

This section contains important instructions that will help you to run the
`Location Library` examples.

### Create Catalog Layers

The examples require you to create catalogs and layers in these catalogs. For
instructions on how to create layers, see the related section in the
[Data User Guide](https://developer.here.com/documentation/data-user-guide/portal/layer-creating.html) 
or the [China Data User Guide](https://developer.here.com/cn/documentation/data-user-guide/portal/layer-creating.html).

### Access to Catalogs

The examples require access to catalogs.

If you are running the examples locally, you must first create an app and set up
a file containing your credentials on your computer. For more information, see the
[Identity & Access Management Developer Guide](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

If you are running the examples in a pipeline on the platform, you also have to
share the catalogs with your group so that the examples can access the data. You
can get this group ID on your [Profile](https://platform.here.com/profile/)
or your [China Profile](https://platform.hereolp.cn/profile/) page.

### Run Pipelines on the Platform

To create a pipeline, you need to provide a group ID. You can get this group ID
on your [Profile](https://platform.here.com/profile/) or your [China Profile](https://platform.hereolp.cn/profile/) page.

Note that your app must be part of that group. If you or your app do not belong
to a group, ask the administrator for your team or organization to assign you or
your app to a group.

### Use the Command Line Interface

The creation of platform resources (catalogs, pipelines, etc.) is conveniently
achievable by means of the OLP Command Line Interface,
see
[OLP CLI](https://developer.here.com/documentation/open-location-platform-cli/user_guide/index.html) or 
[OLP China CLI](https://developer.here.com/cn/documentation/open-location-platform-cli/user_guide/index.html).

Examples documentation only refers to the `olp` command line tool. If you are on
Windows, always replace `olp` with `olp.bat`.

In order to be able to run provided shell snippets, make sure the `olp` command
line tool can be found on your user `PATH`.

## Troubleshooting

### I get `Non-resolvable parent POM for com.here.platform.example.location:parent:x.x.x: Failure to find com.here.platform:environment:pom:x.x.x`

Make sure that you have properly setup the `${user.home}/.m2/settings.xml`,
including the HERE platform repositories.

### I get `Caused by: java.lang.IllegalStateException: Credentials not found`

Make sure that you have followed the instructions on how to
[Get Your Credentials](https://developer.here.com/documentation/identity-access-management/dev_guide/index.html).

### I get `These credentials do not authorize access`

You probably need to share your catalog, for example, with the HERE
platform group you are part of or app that you have credentials for. For more
information, see the platform documentation on how to
[Share a Catalog](https://developer.here.com/documentation/data-user-guide/portal/catalog-sharing.html) or
[Share a China Catalog](https://developer.here.com/cn/documentation/data-user-guide/portal/catalog-sharing.html).

### I get `com.typesafe.config.ConfigException$Missing: No configuration setting found for key 'here.token'`

Your credentials file does not contain all the required entries.

### I get `... request dscid=... info=... ${catalog-hrn}, ... response ... status=403 Forbidden`

You have valid credentials but they cannot access `catalog-hrn`. You need to
grant access to the catalog using the Portal.

### I get `Command 'olp' not found`

Make sure the `olp` command line tool can be found on your user `PATH`.
