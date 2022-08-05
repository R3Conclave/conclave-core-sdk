# Conclave: a JVM that runs inside SGX enclaves

This repository contains an SDK that makes working with SGX enclaves easy.

This document describes how to build and work on the Conclave SDK project. It is *not*
about how to use Conclave to make enclaves: for that visit the [external docsite](https://www.conclave.net/).

Conclave SDK currently only fully supports **Ubuntu Linux**. There is a limited support for macOS and Windows: 
you can build the enclaves, but you can't run enclaves on them.

You can develop on macOS and Windows platforms by using Docker (which runs Linux in a VM), this is also
recommended when developing on Linux.
We will likely only run enclaves on Linux servers for the foreseeable future,
this is because macOS has no kernel drivers for SGX and Windows Server isn't a popular deployment target.

The Dell laptops support SGX, so if you are comfortable using desktop Linux you can just 
use one of those for your development tasks. 
Azure VMs also can support SGX, but it is anyway convenient to use SGX locally.


## Setting up a development environment & building the SDK

Developing Conclave SDK requires setting up the development container (also called **devenv**), entering into it and
building the SDK with a Gradle command.

Instructions on how to set up the development container can be found
[here below](#preparing-the-development-container). Once the container has been set up,
you should be able to enter the development environment by running 
```console
./scripts/devenv_shell.sh
```
from inside the development container, then you can build the SDK and run tests by running
```console
./gradlew build test
```
Due to the large number of native components, the build takes around 10 minutes the first time,
then some elements of the build are cached and hence it should be sensibly quicker
after that.

## Preparing the development container
Because the security of the SGX model depends so heavily on exact reproducibility, and because the build is complex with
many native components, we have encapsulated the build environment into a **Docker container**. Although theoretically possible
to build and work on Conclave without using a container, we have found that it results in lots of flakiness and problems,
at least when working on the native code. Unless staying entirely in the Java/Kotlin world, it is better to use it.

### macOS
On macOS you can install Docker Desktop [following Docker's instructions](https://docs.docker.com/desktop/mac/install/)

> :warning: **On macOS you must increase your Docker RAM allocation**. The default is 2GB but we have found out that it
      needs around 6GB of RAM to successfully build and run the tests.

### Linux (Ubuntu)
On Linux you can get Docker by using your distro's package manager. You can follow the
[instructions here](https://docs.docker.com/engine/install/ubuntu/) or simply do the following:
```console
sudo apt install docker docker-compose
```

If on Linux or macOS, make sure you can use Docker without root privileges by adding yourself to the `docker` UNIX group:

```console
sudo groupadd docker            # Create the docker group. Don't worry if it already exists!
sudo usermod -aG docker $USER   # Add your user to the docker group.
```

This step is *not* configured out of the box on some Linux distributions like Ubuntu, and will break the development
environment scripts (which you will need later).
For more information follow [this link](https://docs.docker.com/install/linux/linux-postinstall/)
to learn more about the docker post-installation steps for linux.

Development is done inside a "devenv" container, which has access to your host disk and network.

## Entering the container

Enter the devenv container from a terminal:

```console
cd <conclave-sdk's local repository>
./scripts/devenv_shell.sh
```

After the container has been built and your terminal have printed out a lot of logging, you should see at the end 
a message similar to this:
```console
Welcome to the Conclave development environment.

You are now in a shell inside a Docker container. 
Run ./gradlew build test to compile and run unit tests.
Browse to http://localhost:8000 to view the external docsite.

conclave master ~/conclave-sdk> 
```

You can now run
```console
./gradlew build test
 ``` 
to compile Conclave and run some unit tests. As previously said, this will take a long time as
there are lots of C++ components.

>Note: if in your development you need to use `sudo` inside the container, then enter it using
>```console
>docker exec --user root -it <container-id> /bin/bash
>```
> In this way you will be using the container as a `root` user.
### Debug mode
Note that by default the `linux-sgx` Intel SGX SDK builds in `Release` mode. If `Debug` mode is needed for debugging purpose, you can add
`-PnativeDebug` to the parameters. e.g. 
```console
./gradlew build -PnativeDebug
```

## IntelliJ and CLion inside the container (Linux only)
There is support for installing IntelliJ and CLion into the containers, but this is only usable on Linux.

Setting the environment variable `CONCLAVE_DOCKER_IDE=1` will enable their automatic download/installation.
Just add 
```console
export CONCLAVE_DOCKER_IDE_TEST=1
```
to the last row of shell initialization scripts (e.g. in `~/.bashrc`), and restart the terminal.

> Downloads are accomplished using curl, which is not part of a default minimal installation of Ubuntu.
Before proceeding, please ensure that curl is installed on your system: 
> ```console
> sudo apt-get install curl
> ```

Use these commands to launch your IDE of choice:

```console
cd <conclave-sdk's local repository>
./scripts/idea.sh      # starts IDEA in a container
./scripts/clion.sh     # starts CLion in a container
```

> Note: for the download or update to happen, the container must not be running.

> Note: by default, the IDEs will be downloaded under the host's `~/.opt/` folder.

## Reset the container

If you `exit` the shell, the container will continue to run. You can see a list of running containers and their
ID's by running 
```console
docker ps
```
If necessary, a container can be shut down with 
```console
docker stop <container id>
```

If you stop the container, you can restart it and log back in by re-running the `devenv_shell.sh` script.

Gradle maintains various caches and such in the container in the `/gradle` directory (this won't appear on your host
system). If your container gets messed up you can blow it away by stopping it and then using `docker images` to list the
image, and `docker rmi` to delete the image. Then rerun `devenv_shell.sh` to redownload things fresh.

## Exploring the codebase

Conclave SDK consists of several technologies bundled together. Here is a description of the most
meaningful directories in this repo.

### azure-plugin
This contains script and tools to use Conclave SDK in an Azure Virtual Machine.

### conclave-{client, common, host, init, mail, web-client, web-host}
These directories contain the public modules of the Conclave API, this is where the Java/Kotlin source code resides.

### containers
This contains Gradle subprojects building various containers relating to the build itself as well as deployment artifacts.

#### containers/aesmd
This is a daemon provided by Intel that does things related to remote attestation.

#### containers/sdk-build
These are the containers used to compile Conclave, and a layer on top that lets you run a shell in the build environment.

### cpp
This is where all C++ code resides. This is mostly a CMake project, with a wrapper `build.gradle` extracting the
artifacts.

#### cpp/linux-sgx
This is the directory of the Intel SGX SDK. CMake checks out the (Intel SGX SDK)[https://github.com/intel/linux-sgx] and applies 
some patches on top of it.
The build compiles the SDK, installs it locally on a build directory so the PSW compilation (`cpp/psw`) can find its dependencies.

#### cpp/jvm-edl
This is where we generate the ECALL/OCALL boundary using Intel's EDL language. Note that the boundary is minimal, the
JVM boundary is implemented on top of this.

#### cpp/jvm-enclave-common
This is JVM enclave code, independent of the specific JVM implementation, like threading. This is supposed to be shared between
different JVM implementations (i.e. HotSpot, SubstrateVM).

### docs
It contains the documentation pages that we use for (Conclave website)[https://www.conclave.net].

### design-docs
This directory contains documentation about the Conclave design, and it also provides a background for certain design decisions.

### dokka
It contains scripts to get and use a (Dokka)[https://github.com/Kotlin/dokka], a documentation engine for Kotlin.

### graal-cap-cache
This contains a Gradle project and tasks to cache the builds of Graal (see next point).

### graal
This is a separate independent Gradle project to build and publish the (GraalVM)[https://www.graalvm.org/] artifact `graal-sdk.tar.gz` 
required by Conclave SDK.

### integration-tests
It contains a Gradle project which uses the published Conclave SDK artifacts to perform integration-level tests.

### plugin-enclave-gradle
This is a Gradle plugin which automates all the process for taking a partial enclave file and 
producing a signed one which contains the end-user enclave code, and which can be used seamlessly by the host code.

### scripts
This contains shell scripts and utilities used for managing deployment, developer environments, releases etc.
