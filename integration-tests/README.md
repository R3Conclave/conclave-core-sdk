# Integration Tests

This is a separate Gradle project which uses a locally published repository containing the Core SDK libraries to 
run integration-level tests. This repository is expected at the location `../build/repo`, which can be produced by 
running

```shell script
./gradlew publishAllPublicationsToBuildRepository
```

at the root level. This approach is taken (and not something like `mavenLocal()`) to ensure only the intended
artifacts are tested.

To run the tests

```shell script
./gradlew test -PenclaveMode=<mode>
```

where `mode` is either `debug` or `simulation`.
