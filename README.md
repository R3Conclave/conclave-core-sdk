[![Conclave SDK](.github/assets/logo.png)][conclave_website]

## Overview
[Conclave SDK][docs] is an open source platform that makes working with SGX enclaves easy.

It is a toolkit for building **enclaves**, small pieces of software that are protected from attack 
by the owner of the computer on which they run. 
It is ideally suited to solving multi-party collaboration and privacy problems, 
but can also be used to secure your infrastructure against attack.

Conclave SDK has been originally developed by [R3][r3_website], and it is has been made available 
to the community, see the [license below](#license).

### Documentation and tutorials
Please refer to the [Conclave website for documentation and tutorials][docs].
Sources of the documentation are under the directory [docs/](docs/docs).

### Contributing
We encourage you to contribute to Conclave. As a contributor please [follow these guidelines](#contributing.md).

### Community
You can interact with the Conclave SDK community on these channels:

[Discord channel](https://discord.gg/zpHKkMZ8Sw)

[Mailing list](https://groups.io/g/conclave-discuss)

## Usage
If you want to start using Conclave SDK and building enclaves, please see
the [Hello World example and tutorials][hello_world].

<!--- TODO: <https://r3-cev.atlassian.net/browse/CON-1020> (This is the Jira ticket to publish artifacts to Maven Central.) --->
Pre-built artifacts of Conclave SDK are present in [Maven Central](https://MAVEN_CENTRAL_REPOSITORY_TO_BE_ADDED),
so you just need to use that repository and add the related dependencies to your project.

Conclave SDK currently only fully supports Ubuntu Linux. There is a limited support for macOS and Windows:
you **can build** the enclaves, but you **can't run** enclaves on them.

## Building the SDK
If you want to modify the source code of Conclave SDK, you can develop on macOS (Intel) and Linux platforms by using 
a development Docker container that we have prepared.
If you are comfortable on Linux, you might want to use Dell laptops: they support SGX, and it is 
convenient to use SGX locally.

In the instructions below we assume Ubuntu as Linux distribution; in case you use a different one,
you need to translate the instructions to your chosen distribution.

### Getting the source code
To retrieve the source code you need to clone this repository:
```shell
git clone https://github.com/R3Conclave/conclave-sdk.git
```

### Preparing your development container
We have created Docker containers to have an encapsulated and easy-to-use build environment, follow the instructions
for your operating system.

#### macOS (Intel)
> :warning: **Conclave SDK is currently not supported on Apple Silicon CPUs.

You can install Docker Desktop [following Docker's instructions](https://docs.docker.com/desktop/mac/install/)

> :warning: **On macOS you must increase your Docker RAM allocation**. The default is 2GB but we have found out that it
needs around 6GB of RAM to successfully build and run the tests.

#### Linux (Ubuntu)
You can get install Docker for Linux [following Docker's instructions](https://docs.docker.com/engine/install/ubuntu/).

In either cases (Linux or macOS), make sure you can use Docker without root privileges by adding yourself
to the `docker` UNIX group:

```shell
sudo groupadd docker            # Create the docker group. Don't worry if it already exists!
sudo usermod -aG docker $USER   # Add your user to the docker group.
```

This step is *not* configured out of the box on some Linux distributions like Ubuntu, and will break the development
environment scripts (which you will need later).

Development is done inside a **devenv** container, which has access to your host disk and network. This is layered on top
of the build container. 

### Building and entering into your development container
Once you have set up your container, you need to enter into it:
```shell
./scripts/devenv_shell.sh
```

After the container has been built and your terminal have printed out a lot of logging, you should see at the end
a message similar to this:
```shell
Welcome to the Conclave development environment.

You are now in a shell inside a Docker container. 
Run ./gradlew build test to compile and run unit tests.
Browse to http://localhost:8000 to view the external docsite.

conclave master ~/conclave-sdk> 
```

### Building the SDK and runnning tests
We use **Gradle** to build the SDK, you can now build and run tests with the following commands:
```shell
./gradlew build
./gradlew test
```

Due to the large number of native components, the build takes around 10 minutes the first time,
then some elements of the build are cached and hence it should be sensibly quicker
after that.

>Note: if in your development you need to use `sudo` inside the container, then enter it using
>```shell
>docker exec --user root -it <container-id> /bin/bash
>```
> In this way you will be using the container as a `root` user.
> 
### Debug mode
As part of the build, Conclave SDK uses a modified version of [Intel SGX SDK](https://github.com/intel/linux-sgx),
together with other C/C++ code.
By default, this code is built in `Release` mode. If you need to debug C/C++ code you need to set the `Debug` mode by adding
`-PnativeDebug` to the parameters. e.g. 
```shell
./gradlew build -PnativeDebug
```

If you want to debug C/C++ code inside the enclave, you can use the scripts `conclave-gdb` and `conclave-gdb-attach`
 (in the directory `scripts/`) to debug your code. These are just wrappers of `gdb` for Conclave.

### IntelliJ and CLion inside the container (Linux only)
It is possible to install IntelliJ and CLion into the containers, but only on Linux.

Setting the environment variable `CONCLAVE_DOCKER_IDE=1` will enable their automatic download/installation.
Just add 
```shell
export CONCLAVE_DOCKER_IDE_TEST=1
```
to the last row of shell initialization scripts (e.g. in `~/.bashrc`), and restart the terminal.

> For the download or update to happen, the container must not be running, so you need to stop
> it in that case (see below).
> 
> Downloads are accomplished using **curl**, so ensure that it is installed on your system: 
> ```shell
> sudo apt-get install curl
> ```

By default, the IDEs will be downloaded under the host's `~/.opt/` folder.

You can now use these commands to launch your IDE of choice:

```shell
cd <conclave-sdk's local repository>
./scripts/idea.sh      # starts IDEA in a container
./scripts/clion.sh     # starts CLion in a container
```
### Stopping and restarting the container

If you `exit` the shell, the container will continue to run. You can see a list of running `sdk-build` containers and their
IDs by running
```shell
docker ps -f label=sdk-build 
```
If necessary, a container can be shut down with 
```shell
docker stop $(docker ps -f label=sdk-build -q)
```

If you stop the container, you can restart it and log back in by re-running the `devenv_shell.sh` script.

**Gradle** maintains various caches and such in the container in the `/gradle` directory (this won't appear on your host
system). If your container gets messed up you can blow it away by stopping it and then using `docker images` to list the
image, and `docker rmi` to delete the image. Then rerun `devenv_shell.sh` to re-download things fresh.


## Exploring the codebase

Conclave SDK consists of several technologies bundled together. Here is a description of the most
meaningful directories in this repo.

| Directory                                                                                                                                                                                                                                                                                                    | Description                                                                                                                                                                                                                                                                                                    |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [azure&#x2011;plugin/](azure-plugin)                                                                                                                                                                                                                                                                         | This contains script and tools to use the SDK in an Azure Virtual Machine.                                                                                                                                                                                                                                     |
| [conclave&#x2011;client/](conclave-client)<br/>[conclave&#x2011;common/](conclave-common)<br/>[conclave&#x2011;host/](conclave-host)<br/>[conclave&#x2011;mail/](conclave-mail)<br/>[conclave&#x2011;web&#x2011;client/](conclave-web-client)<br/>[conclave&#x2011;web&#x2011;host/](conclave-web-host)<br/> | These directories contain the public modules of the Conclave API.                                                                                                                                                                                                                                              |
| [conclave&#x2011;init/](conclave-init)                                                                                                                                                                                                                                                                       | This is a tool to quickly and automatically generate a Conclave project.                                                                                                                                                                                                                                       |
| [containers/aesmd/](containers/aesmd)                                                                                                                                                                                                                                                                        | This is daemon required for remote attestation, needed to run the integration tests with EPID.                                                                                                                                                                                                                 |
| [containers/sdk&#x2011;build/](containers/sdk-build)                                                                                                                                                                                                                                                         | These are the containers used to compile Conclave, and a layer on top that lets you run a shell in the build environment.                                                                                                                                                                                      |
| [cpp/](cpp)                                                                                                                                                                                                                                                                                                  | This is where all C++ code resides. This is mostly a CMake project, with a wrapper `build.gradle` extracting the artifacts.                                                                                                                                                                                    |
| [cpp/linux&#x2011;sgx/](cpp/linux-sgx)                                                                                                                                                                                                                                                                       | This directory contains modifications to the Intel SGX SDK. CMake checks out the [Intel SGX SDK](https://github.com/intel/linux-sgx) and applies some patches on top of it. The build compiles the SDK, installs it locally on a build directory so the PSW compilation (`cpp/psw`) can find its dependencies. |
| [cpp/jvm&#x2011;edl/](cpp/jvm-edl)                                                                                                                                                                                                                                                                           | This is where we generate the ECALL/OCALL boundary using Intel's EDL language. Note that the boundary is minimal, the  JVM boundary is implemented on top of this.                                                                                                                                             |
| [cpp/jvm&#x2011;enclave&#x2011;common/](cpp/jvm-enclave-common)                                                                                                                                                                                                                                              | This is JVM enclave code, independent of the specific JVM implementation, like threading. This is supposed to be shared between different JVM implementations (i.e. HotSpot, SubstrateVM).                                                                                                                     |
| [docs/](docs)                                                                                                                                                                                                                                                                                                | This contains the source code for the [Conclave documentation](https://docs.conclave.net).                                                                                                                                                                                                                     |
| [design&#x2011;docs/](design-docs)                                                                                                                                                                                                                                                                           | This directory contains documentation about the Conclave design, and it also provides a background for certain design decisions.                                                                                                                                                                               |
| [dokka/](dokka)                                                                                                                                                                                                                                                                                              | This contains a script to build a fork of [Dokka](https://github.com/Kotlin/dokka), a documentation engine for Kotlin.                                                                                                                                                                                         |
| [graal&#x2011;cap&#x2011;cache/](graal-cap-cache)                                                                                                                                                                                                                                                            | This contains a Gradle project to generate and cache "C Annotation Processor" metadata that helps Graal/Native Image to speed up the process of creating C header files with C type definitions during the *Java bytecode to native code* translation.                                                         |
| [integration&#x2011;tests/](integration-tests)                                                                                                                                                                                                                                                               | This is a separate independent Gradle project which is used to test the SDK artifacts at an integration-level.                                                                                                                                                                                                 |
| [plugin&#x2011;enclave&#x2011;gradle/](plugin-enclave-gradle)                                                                                                                                                                                                                                                | This is a Gradle plugin which automates all the process for taking a partial enclave file and producing a signed one which contains the end-user enclave code, and which can be used seamlessly by the host code.                                                                                              |
| [scripts/](scripts)                                                                                                                                                                                                                                                                                          | This contains shell scripts and utilities used for managing deployment, developer environments, releases etc.                                                                                                                                                                                                  |

[r3_website]: https://www.r3.com
[conclave_website]: https://www.conclave.net
[docs]: https://docs.conclave.net
[hello_world]: https://docs.conclave.net/running-hello-world.html
