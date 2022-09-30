[![Conclave Core SDK](.github/assets/logo.png)][conclave_website]

The [Conclave Core SDK][docs] is an open source platform that lets you create SGX enclaves easily. You can 
write your enclave code in high-level languages such as Java, Kotlin, and JavaScript.

**Enclaves** are small pieces of software that are protected from attack by the owner of the computer on which they 
run. They are ideally suited to solving multi-party collaboration and privacy problems, 
but can also be used to secure your infrastructure against attack.

The Conclave Core SDK is developed by [R3][r3_website], and has been made available to the [open source community](#license).

## How to use Conclave
If you want to learn how to use Conclave and build enclaves, take a look at the
[hello world example and tutorial][hello_world]. You can also refer to the [Conclave documentation][docs] and
[API docs](https://docs.conclave.net/api/index.html).

## Contributing
We encourage you to contribute to Conclave. Please read our contribution [guidelines](CONTRIBUTING.md).

### Community
You can also interact with the Conclave community on these channels:

[Discord](https://discord.gg/zpHKkMZ8Sw)

[Mailing list](https://groups.io/g/conclave-discuss)

## Building the SDK
If you want to build the SDK, you can do so both on Linux and macOS (Intel only) using a development Docker container.

> :warning: **Building the SDK is currently not supported on Apple Silicon hardware.

In the instructions below we assume Ubuntu as the Linux distribution; in case you use a different one,
you need to translate the instructions to your chosen distribution.

### Getting the source code
To retrieve the source code you need to clone this repository:
```shell
git clone https://github.com/R3Conclave/conclave-core-sdk.git
```

### Preparing the development container
We have created Docker containers to have an encapsulated and easy-to-use build environment, follow the instructions
for your operating system.

#### macOS (Intel)

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

conclave master ~/conclave-core-sdk> 
```

### Building the SDK and runnning tests
We use [Gradle](https://docs.gradle.org/) to build the SDK, which you can do with the following command:
```shell
./gradlew build
```

This is will also run the unit tests. To skip them run:
```shell
./gradlew build -x test
```

Due to the large number of native components, the build takes around 10 minutes the first time,
then some elements of the build are cached and hence it should be sensibly quicker
after that.

The integration tests reside as a separate Gradle project in [integration-tests/](integration-tests). To run them
you will first need to create a local Maven repository with the SDK artifacts:
```shell
./gradlew publishAllPublicationsToBuildRepository
cd integration-tests
./gradlew test
```

>Note: if in your development you need to use `sudo` inside the container, then enter it using
>```shell
>docker exec --user root -it <container-id> /bin/bash
>```
> In this way you will be using the container as a `root` user.
> 
### Debug mode
As part of the build, Conclave uses a modified version of the [Intel SGX SDK](https://github.com/intel/linux-sgx),
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
cd <conclave-core-sdk's local repository>
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

Gradle maintains various caches and such in the container in the `/gradle` directory (this won't appear on your host
system). If your container gets messed up you can blow it away by stopping it and then using `docker images` to list the
image, and `docker rmi` to delete the image. Then rerun `devenv_shell.sh` to re-download things fresh.


## Exploring the codebase

The Conclave Core SDK consists of several technologies working together. Here is a description of the most
meaningful directories in this repo.

| Directory                                                                                                                                                                                                                                          | Description                                                                                                                 |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| [conclave&#x2011;client/](conclave-client)<br/>[conclave&#x2011;common/](conclave-common)<br/>[conclave&#x2011;host/](conclave-host)<br/>[conclave&#x2011;mail/](conclave-mail)<br/>[conclave&#x2011;web&#x2011;client/](conclave-web-client)<br/> | Kotlin/Java code of the Core API.                                                                                           |
| [conclave&#x2011;web&#x2011;host/](conclave-web-host)                                                                                                                                                                                              | Simple web-based Conclave [host server](https://docs.conclave.net/conclave-web-host.html).                                  |
| [conclave&#x2011;init/](conclave-init)                                                                                                                                                                                                             | This is a tool to quickly and automatically generate a Conclave project.                                                    |
| [containers/conclave&#x2011;build/](containers/conclave-build)                                                                                                                                                                                     | Docker container for building Conclave enclaves in Windows and macOS.                                                       |
| [containers/sdk&#x2011;build/](containers/sdk-build)                                                                                                                                                                                               | Docker container for building the Conclave Core SDK.                                                                        |
| [cpp/](cpp)                                                                                                                                                                                                                                        | This is where all C++ code resides. This is mostly a CMake project, with a wrapper `build.gradle` extracting the artifacts. |
| [cpp/fatfs/](cpp/fatfs)                                                                                                                                                                                                                            | This code creates the representation of the enclave filesystem using FatFs.                                                 |
| [cpp/jvm&#x2011;edl/](cpp/jvm-edl)                                                                                                                                                                                                                 | The minimal ECALL/OCALL boundary using Intel's EDL language.                                                                |
| [cpp/jvm&#x2011;enclave&#x2011;common/](cpp/jvm-enclave-common)                                                                                                                                                                                    | Implementations and stubs of Linux system POSIX calls inside the enclave.                                                   |
| [cpp/jvm&#x2011;host/](cpp/jvm-host)                                                                                                                                                                                                               | Native code which interacts with the Java/Kotlin layer through JNI.                                                         |
| [cpp/jvm&#x2011;host&#x2011;enclave&#x2011;common/](cpp/jvm-host-enclave-common)                                                                                                                                                                   | Native code used both by the host and the enclave. It mainly consists of utility functions/classes.                         |
| [cpp/linux&#x2011;sgx/](cpp/linux-sgx)                                                                                                                                                                                                             | Conclave modifications to the [Intel SGX SDK](https://github.com/intel/linux-sgx).                                          |
| [cpp/substratevm/](cpp/substratevm)                                                                                                                                                                                                                | This is C/C++ enclave code, with the implementation of the entry points (host to enclave) of some EDL code.                 |
| [docs/](docs)                                                                                                                                                                                                                                      | This contains the source code for the [Conclave documentation](https://docs.conclave.net).                                  |
| [dokka/](dokka)                                                                                                                                                                                                                                    | This contains a script to build a fork of [Dokka](https://github.com/Kotlin/dokka), a documentation engine for Kotlin.      |
| [integration&#x2011;tests/](integration-tests)                                                                                                                                                                                                     | This is a separate independent Gradle project which is used to test the SDK artifacts at an integration-level.              |
| [plugin&#x2011;enclave&#x2011;gradle/](plugin-enclave-gradle)                                                                                                                                                                                      | The Conclave Grade enclave plugin which automates the process of building a native SGX binary.                              |
| [scripts/](scripts)                                                                                                                                                                                                                                | Various scripts for building and testing the SDK.                                                                           |

## License
Copyright © 2022, R3 LLC, all rights reserved.

The Conclave Core SDK is distributed under the [Apache License v2.0](LICENSE).

It incorporates components from third-party open source libraries. See the [NOTICE](NOTICE.md) file for more information.

[r3_website]: https://www.r3.com
[conclave_website]: https://www.conclave.net
[docs]: https://docs.conclave.net
[hello_world]: https://docs.conclave.net/running-hello-world.html
