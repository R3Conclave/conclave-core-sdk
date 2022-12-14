# Conclave Init

Conclave Init is a tool for bootstrapping Conclave projects, reducing the amount of boilerplate you need to write.
It will automatically generate your Conclave project so that you can focus on writing enclave code.

## Generate a new project

First download the Conclave Init tool:

```shell
wget https://repo1.maven.org/maven2/com/r3/conclave/conclave-init/1.4/conclave-init-1.4.jar -O conclave-init.jar
```

To create a new Conclave project, run the following command:

```shell
java -jar conclave-init.jar \
  --package com.megacorp \
  --enclave-class-name AmazingEnclave \
  --target ./amazing-conclave-app
```

!!! tip
    Conclave Init also supports Kotlin. Try appending the command with `--language kotlin`.

    Run `java -jar conclave-init.jar --help` to see all available options.

!!! info
    Conclave Init requires Java 17 to run.

- A new project has been created in the `amazing-conclave-app` directory, as specified
  by `--target ./amazing-conclave-app`.
- The `enclave` directory contains the enclave module. The class `AmazingEnclave` has been added to the
  package `com.megacorp.enclave`. The project is ready for you to start implementing your enclave code here!
- The `host` directory has been created, too. It contains only a `build.gradle` file, since that's all that's required
  for the [Conclave web host](conclave-web-host.md).
- The `client` directory contains a basic client which can interact with the web host.

## Next steps

* If you've done this before, you can now start implementing your enclave in the generated project.
* If you'd like to learn how to implement a Conclave app using this project as a starting point, head to
  the [Writing your first enclave](writing-hello-world.md) tutorial.
* If you'd like to read a description of the files that have been generated, see the
  [appendix](#appendix-description-of-generated-files).

## Run the tests

From the newly created project directory, run the tests with

```
./gradlew test -PenclaveMode=<MODE>
```

where `MODE` is the [enclave mode](enclave-modes.md).

## Run the application

!!! note
    Projects created by Conclave Init are configured to use the Conclave web host and web client. However, you are
    free to reconfigure the project to use a different host or client.

1. Build and run the host with
   ```bash
   ./gradlew :host:shadowJar -PenclaveMode=<MODE>
   java -jar host/build/libs/host-<MODE>.jar
   ```
   where `MODE` is the [enclave mode](enclave-modes.md) in lowercase. For example:
   ```bash
   ./gradlew :host:shadowJar -PenclaveMode=mock
   java -jar host/build/libs/host-mock.jar
   ```
   This will spin up a Spring Boot server. You will know it's ready when you see the following output:
   ```bash
   INFO com.r3.conclave.host.web.EnclaveWebHost$Companion - Started EnclaveWebHost.Companion in <SECONDS> seconds
   ```

5. In a separate terminal, build and run the client with
   ```bash
   ./gradlew :client:shadowJar
   
   java -jar client/build/libs/client-all.jar \
       "S:<SIGNING_KEY_HASH> PROD:1 SEC:INSECURE"
   ```
   If you are using mock mode (the default), the `SIGNING_KEY_HASH` will be
   `0000000000000000000000000000000000000000000000000000000000000000`. For any mode, you can grab it from the line in
   the host output which starts with "Code signer". You should see the output
   ```bash
   Enclave returned 321
   ```
   which has been hardcoded in the enclave's `receiveMail()` function.

## Java Version

By default the generated project targets Java 17. This can be changed in the root `build.gradle` where the Java 
toolchain configuration is specified.

```groovy
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }
```

This settings will automatically apply to the JVM target version in Kotlin as well.

## Appendix: Description of Generated Files

The majority of the files generated by Conclave Init contain boilerplate code which configures the project's Gradle 
build. This section will describe the contents of these configuration files that Conclave Init has generated.

In later sections, we will be modifying the generated Java source files to implement the sample application.

### Root directory

#### `gradle/` / `gradlew` / `gradlew.bat`

These are standard Gradle files. More information can be found in the [Gradle 
docs](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

#### `settings.gradle`

This file defines the [Gradle multi-project 
build](https://docs.gradle.org/current/userguide/multi_project_builds.html#sec:creating_multi_project_builds) and 
configures how Conclave repo directory is discovered.

#### `build.gradle`

This is the top-level `build.gradle` file which provides common config that applies to the entire project. It's 
where the Java version of the entire project is set.

#### `gradle.properties`

Defines the version of Conclave being used with the `conclaveVersion` property.

### Enclave, host, and client modules
The enclave, host, and client are the three components in a Conclave application. See
[Conclave Architecture](architecture.md) for more information.

#### `host/build.gradle`

You may have noticed that there are no Java source files in the `host/` directory. There's no need to write any host 
code if using the bundled web server! The host module uses the Conclave [web host](conclave-web-host.md)
implementation, so we don't have to implement our own host. All we need is the `host/build.gradle` file which 
configures the host to use the web host.

#### `enclave/build.gradle`

The enclave `build.gradle` file applies the [Conclave plugin](enclave-configuration.md) which enables a Gradle 
module to be compiled into an enclave binary.

!!! tip
    The generated enclave module doesn't specify a signing configuration and so the default one will be used. See 
    the section on [signing](signing.md) to learn how to configure this.


#### `client/build.gradle`

The client is a simple Java application. It uses the `conclave-web-client` dependency to be able to communicate with 
the web host.
