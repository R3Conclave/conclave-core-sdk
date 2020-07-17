# Conclave: a JVM that runs inside SGX enclaves

This repository contains an SDK that makes working with SGX enclaves easy.

To find your way around, start reading the docs under [internal-docs](/internal-docs/docs) 
and in particular [the directory layout](/internal-docs/docs/directories.md).

## Setting up a development environment

Developing Conclave requires some level of Linux support. On macOS or Windows you
can use Docker with the devenv container. We've tried a variety of approaches to
this because Docker is unfortunately quite slow when not on a Linux host system,
however, other virtualisation approaches can lead to weird bugs and flakyness.
Conclave's build is unfortunately not really portable, and the SGX SDK is at any
rate only supported on two versions of Ubuntu.

Incidentally, to actually build an _enclave_ using the Conclave SDK can be done on
Windows and macOS, in addition to Linux.

Once Docker is installed and set up, use `./scripts/devenv_shell.sh` to set up the
build container and log into it. Then run `./gradlew test` to build all the components
and run the tests.

The build takes a long time because there are a lot of native components.

On Linux you can run IntelliJ and CLion (expense a license) from inside the devenv
container using `./scripts/idea.sh` and `./scripts/clion.sh`

## Joining the project

Please join the #conclave channel on Slack and the sgx@r3.com Outlook group (you can join it
yourself using the Outlook web UI).

