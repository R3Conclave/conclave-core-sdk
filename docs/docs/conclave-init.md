# Conclave Init

Conclave Init is a tool for bootstrapping Conclave projects, reducing the amount of boilerplate you need to write.

You can use the tool once you have [set up your machine](system-requirements.md)
and installed [Conclave](https://conclave.net/get-conclave/). (You can use the community edition for the purposes of
this tutorial!)

Conclave Init will automatically generate your Conclave project so that you can focus on writing enclave code!

## Generate a new project

You will find `conclave-init.jar` in the `tools` directory of the Conclave SDK. To create a new Conclave project,
run the following
command:

```shell
java -jar /path/to/conclave-sdk/tools/conclave-init.jar \
  --package com.megacorp \
  --enclave-class-name AmazingEnclave \
  --target ./amazing-conclave-app
```

!!! tip
    Conclave Init also supports Kotlin. Try appending the command with `--language kotlin`.

    Run `java -jar conclave-init.jar --help` to see all available options.

That's it! You are now ready to start implementing your enclave. The next sections will show you how to interact with
your project.

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
   the host output which starts with "Code signing key hash". You should see the output
   ```bash
   Enclave returned 321
   ```
   which has been hardcoded in the enclave's `receiveMail()` function.

## Project structure

The command provided in [Generate a new project](#generate-a-new-project) will create the following files:

```
.
└── amazing-conclave-app
    ├── build.gradle
    ├── client
    │   ├── build.gradle
    │   └── src
    │       └── main/java/com/megacorp/client/AmazingEnclaveClient.java
    ├── conclave-repo
    │   └── ...
    ├── enclave
    │   ├── build.gradle
    │   └── src
    │       ├── main/java/com/megacorp/enclave/AmazingEnclave.java
    │       └── test/java/com/megacorp/enclave/AmazingEnclaveTest.java
    ├── gradle
    │   └── wrapper
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── host
    │   └── build.gradle
    ├── README.md
    └── settings.gradle

```

- A new project has been created in the `amazing-conclave-app` directory, as specified
  by `--target ./amazing-conclave-app`.
- The `repo/` directory from the SDK has been copied to `conclave-repo/`, and the `conclaveRepo` and `conclaveVersion`
  properties have been set in `gradle.properties` to reference it.
- The `enclave` directory contains the enclave module. The class `AmazingEnclave` has been added to the
  package `com.megacorp.enclave`. The project is ready for you to start implementing your enclave code here!
- The `host` directory has been created, too. It's lightweight, since only minimal config is required for the
  new `conclave-web-host`.
- The `client` directory contains a basic client which can interact with the web host.

For more information on developing your application and how the parts of the application fit together, head to
the [hello-world tutorial](writing-hello-world.md).

## Java Compatibility

=== "Java"
    By default, Gradle will compile the generated Java project using the current Java version. Conclave needs at least Java
    8, but if you want to compile to a different version then you can do so. See
    the [Gradle docs](https://docs.gradle.org/current/userguide/building_java_projects.html#sec:java_cross_compilation)
    for details on how to do this.

=== "Kotlin"
    The following code in the root `build.gradle` of the generated project tells Gradle to compile the project to Java 11 bytecode:
    ```groovy hl_lines="4"
    tasks.withType(AbstractCompile) {
        if (it.class.name.startsWith('org.jetbrains.kotlin.gradle.tasks.KotlinCompile')) {
            kotlinOptions {
                jvmTarget = "11"
                apiVersion = '1.5'
                languageVersion = '1.5'
            }
        }
    }
    ```

    If you want to revert to Java 8, remove the block or set `jvmTarget=1.8`. See the [Kotlin docs](https://docs.gradle.org/current/userguide/building_java_projects.html#sec:java_cross_compilation) for more details.

    !!! note
        Since this project uses Gradle 6, the `KotlinCompile` task is not directly available. Hence we use `AbstractCompile` instead.
