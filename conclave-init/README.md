# Conclave Init
Conclave Init is a tool for creating new conclave projects.

It works by copying a template project with some modifications, namely custom package, enclave class name, and target directory.

## Project Structure
The CLI is exposed through [Main.kt](src/main/kotlin/com/r3/conclave/init/cli/Main.kt). You can see the available parameters in [CommandLineParameters](src/main/kotlin/com/r3/conclave/init/cli/CommandLineParameters.kt).

The entry point for the main logic is [ConclaveInit.kt](src/main/kotlin/com/r3/conclave/init/ConclaveInit.kt). In future, this could be called from another interface, e.g. REST or a GUI.

### Template
The [template](template) is a simple Conclave project with Java and Kotlin enclave implementations. Note that the 
tool will filter the source files based on which language is selected.

There is a bit of Gradle trickery involved in packaging the template. The `Zip` commands in [build.gradle](build.gradle) package up the template and also the Gradle wrapper from `sgxjvm`  into the resources directory, so that they are included in the fat JAR produced by the shadow plugin.

The app can be executed via 
```
java -jar /path/to/conclave-init.jar ARGS
```

See [the user docs](../docs/docs/conclave-init.md) for more details.

## Tests
In addition to the unit tests in this module, there are some basic integration tests in the
[`test-conclave-init.sh`](../test-conclave-init.sh) script.
