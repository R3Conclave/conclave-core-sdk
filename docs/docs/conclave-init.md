# Conclave Init

Conclave Init is a tool for bootstrapping Conclave projects, reducing the amount of boilerplate you need to write.

You can use the tool once you have [set up your machine](tutorial.md#set-up-your-machine)
and downloaded [Conclave for Community](https://conclave.net/get-conclave/).

Conclave Init will automatically generate your Conclave project so that you can focus on writing enclave code!

## Generate a new project

You will find `conclave-init.jar` at the root of the Conclave SDK. To create a new conclave project, run the following
command:

```shell
java -jar conclave-init.jar \
  --package com.megacorp \
  --enclave-class-name AmazingEnclave \
  --target amazing-enclave
```

!!! tip
    Conclave Init also supports Kotlin. Try appending the command with `--language kotlin`.

Run `java -jar conclave-init.jar --help` to see all available options.

## Run the tests

From the newly created project directory, run the tests with 
```
./gradlew test -PenclaveMode=<MODE>
```

where `MODE` is the
[Conclave mode](tutorial.md#enclave-modes).

## Project structure
The command provided in [Generate a new project](#generate-a-new-project) will create the following files:
```
.
└── amazing-enclave
    ├── build.gradle
    ├── conclave-repo/
    ├── enclave
    │   ├── build.gradle
    │   └── src
    │       ├── main
    │       │   └── java
    │       │       └── com
    │       │           └── megacorp
    │       │               └── enclave
    │       │                   └── AmazingEnclave.java
    │       └── test
    │           └── java
    │               └── com
    │                   └── megacorp
    │                       └── enclave
    │                           └── AmazingEnclaveTest.java
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
    ├── settings.gradle
    └── versions.gradle
```

- A new project has been created in the `amazing-enclave` directory, as specified by `--target amazing-enclave`.
- The `enclave` directory contains the enclave module. The class `AmazingEnclave` has been added to the
  package `com.megacorp.enclave`. The project is ready for you to start implementing your enclave code here!
- The `host` directory has been created, too. It's lightweight, since only minimal config is required for the
  new `conclave-web-host`.
- The `repo/` directory from the SDK has been copied to `conclave-repo/` and the `conclaveRepo` and `conclaveVersion`
  properties have been set in `gradle.properties`.

For more information on developing your application and how the parts of the application fit together, head to
the [hello-world tutorial](writing-hello-world.md).