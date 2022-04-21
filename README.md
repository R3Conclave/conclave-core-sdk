# Conclave: a JVM that runs inside SGX enclaves

This repository contains an SDK that makes working with SGX enclaves easy.

To find your way around, start reading the docs under [internal-docs](/internal-docs/docs/index.md)
and in particular [the directory layout](/internal-docs/docs/directories.md).

## Setting up a development environment & building the SDK

Developing Conclave requires some level of Linux support. On macOS or Windows you
can use Docker with the devenv container. We've tried a variety of approaches to
this because Docker is unfortunately quite slow when not on a Linux host system,
however, other virtualisation approaches can lead to weird bugs and flakyness.
Conclave's build is unfortunately not really portable, and the SGX SDK is at any
rate only supported on two versions of Ubuntu.

Instructions on how to set up the devenv container can be found
[here](/internal-docs/docs/index.md#using-the-devenv-container). Once the container has been set up,
you should be able to enter the development environment by running `./scripts/devenv_shell.sh`.
From inside the development container, you can build the SDK and run tests by running
`./gradlew build test`.

Due to the large number of native components, the build may take some time.

It is worth noting that while development is (mostly) limited to linux, enclaves can
still be created using the SDK on Windows and macOS.

### IntelliJ and CLion (Linux only)

There is some support for using IntelliJ and CLion within the container, though only on linux. Refer
to [using the devenv container](/internal-docs/docs/index.md) for instructions.

## Joining the project

Please ensure you have access to @Conclave group on Microsoft teams.

