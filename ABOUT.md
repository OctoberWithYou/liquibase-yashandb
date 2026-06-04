# liquibase-yashandb

> Liquibase connector for [YashanDB](https://www.yashandb.com/).

Drop one Maven dependency into your project and Liquibase will recognise
`jdbc:yasdb://...` connections, emit YashanDB-compatible DDL and filter out
YashanDB system objects during snapshots.

```xml
<dependency>
    <groupId>com.yashandb</groupId>
    <artifactId>liquibase-yashandb</artifactId>
    <version>1.0.0</version>
</dependency>
```

See the [README](README.md) for full usage instructions, examples and
contribution guidelines.
