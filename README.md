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


## Python support (work in progress)

There is a **work in progress** Python API which is available on the master build of the SDK (1.4-SNAPSHOT). 

The [enclave Java API](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.enclave/-enclave/index.html) 
has been ported to the following global functions:

* `on_enclave_startup()` - equivalent to [`onStartup`](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.enclave/-enclave/on-startup.html)
* `on_enclave_shutdown()` - equivalent to [`onShutdown`](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.enclave/-enclave/on-shutdown.html)
* `receive_from_untrusted_host(bytes)` - equivalent to [`receiveFromUntrustedHost`](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-from-untrusted-host.html).
  The Java byte array is converted to Python [`bytes`](https://docs.python.org/3/library/stdtypes.html#bytes-objects).
  If there’s no return value then it is treated as null, otherwise the return value is expected to be `bytes`.
* `receive_enclave_mail(mail)` - equivalent to [`receiveMail`](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-mail.html).
  The Java [`EnclaveMail`](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.mail/-enclave-mail/index.html)
  object is converted to a simpler Python equivalent which is just a class holding the body, envelope and 
  authenticated sender. The topic and sequence number are ignored for now. The authenticated sender is represented 
  by its encoded binary form in `bytes`. The return value (if there is one) is treated as a response and is 
  encrypted as Mail back to the sender. A single `bytes` value is treated as the reponse body, whilst a tuple of 
  `bytes` is treated as the body and envelope.

These functions need to be defined in a single Python file and are all optional. Not defining them is equivalent to 
not overriding the equivalent method from `Enclave`. The Python script must exist in the enclave Gradle module under 
`src/main/python`. Only one Python script is supported at this time. Otherwise, everything else is the same as a 
Java or Kotlin project. The Python enclave module needs to be part of a Gradle multi-module project with the 
host module taking a dependency to the enclave module.

The Python script also has access to an `enclave_sign(data)` global function, which allows the given data `bytes` to be 
signed by the enclave's private signing key. This is equivalent to [`signer()`](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.enclave/-enclave/signer.html)
in the Java API.

Have a look at the [PyTorch sample](https://github.com/R3Conclave/conclave-samples/tree/master/pytorch) to see how 
this API is used. If you need any help then please do [reach out](#community) and we'll be happy to help. If you also 
have feedback on the API then we'd love to hear it.

### How it works

Under the hood, the Python support is implemented using an ["adapter" enclave](python-enclave-adapter/src/main/kotlin/com/r3/conclave/python/PythonEnclaveAdapter.kt) 
which extends `Enclave` and behaves like a normal Java/Kotlin Conclave enclave. The enclave API calls are delegated 
to the Python script using [Jep](https://github.com/ninia/jep). Using this avoids having to re-implement all the 
underlying enclave, Mail and attestation code. Jep integrates with the Python/C API via JNI and thus should provide 
good compatibility with existing Python libraries.

### Limitations

As work in progress, there are plently of issues and features missing, which we plan to address. Some of which are:

* Mock mode support is limited. There's currently no way to inspect objects from the Python environment without 
  using reflection.
* All the necessary tools, such as Python, pip and Gramine, must be installed locally
* Most likely the enclave will only work on the same machine that it was built on.
* Only a single Python file is supported
* There's no yet API to send responses to other than the requester.

## Exploring the codebase

The Conclave Core SDK consists of several technologies working together. Here is a description of the most
important directories in this repo.

| Directory                                                                                                                                                                                                                                          | Description                                                                                                     |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| [conclave&#x2011;client/](conclave-client)<br/>[conclave&#x2011;common/](conclave-common)<br/>[conclave&#x2011;host/](conclave-host)<br/>[conclave&#x2011;mail/](conclave-mail)<br/>[conclave&#x2011;web&#x2011;client/](conclave-web-client)<br/> | Kotlin/Java code of the Core API.                                                                               |
| [conclave&#x2011;web&#x2011;host/](conclave-web-host)                                                                                                                                                                                              | Simple web-based Conclave [host server](https://docs.conclave.net/conclave-web-host.html).                      |
| [conclave&#x2011;init/](conclave-init)                                                                                                                                                                                                             | The tool to quickly and automatically generate a Conclave project.                                              |
| [containers/conclave&#x2011;build/](containers/conclave-build)                                                                                                                                                                                     | Docker container for building Conclave enclaves in Windows and macOS.                                           |
| [containers/sdk&#x2011;build/](containers/sdk-build)                                                                                                                                                                                               | Docker container for building the Conclave Core SDK.                                                            |
| [cpp/](cpp)                                                                                                                                                                                                                                        | Place for all C++ code. This is mostly a CMake project, with a wrapper `build.gradle` extracting the artifacts. |
| [cpp/fatfs/](cpp/fatfs)                                                                                                                                                                                                                            | Framework to create the representation of the enclave filesystem using FatFs.                                   |
| [cpp/jvm&#x2011;edl/](cpp/jvm-edl)                                                                                                                                                                                                                 | The minimal ECALL/OCALL boundary using Intel's EDL language.                                                    |
| [cpp/jvm&#x2011;enclave&#x2011;common/](cpp/jvm-enclave-common)                                                                                                                                                                                    | Implementations and stubs of Linux system POSIX calls inside the enclave.                                       |
| [cpp/jvm&#x2011;host/](cpp/jvm-host)                                                                                                                                                                                                               | Native code which interacts with the Java/Kotlin layer through JNI.                                             |
| [cpp/jvm&#x2011;host&#x2011;enclave&#x2011;common/](cpp/jvm-host-enclave-common)                                                                                                                                                                   | Native code used both by the host and the enclave. It mainly consists of utility functions/classes.             |
| [cpp/linux&#x2011;sgx/](cpp/linux-sgx)                                                                                                                                                                                                             | Conclave modifications to the [Intel SGX SDK](https://github.com/intel/linux-sgx).                              |
| [cpp/substratevm/](cpp/substratevm)                                                                                                                                                                                                                | C/C++ enclave code, with the implementation of the entry points (host to enclave) of some EDL code.             |
| [docs/](docs)                                                                                                                                                                                                                                      | Source code for the [Conclave documentation](https://docs.conclave.net).                                        |
| [integration&#x2011;tests/](integration-tests)                                                                                                                                                                                                     | A separate independent Gradle project which is used to test the SDK artifacts at an integration level.          |
| [plugin&#x2011;enclave&#x2011;gradle/](plugin-enclave-gradle)                                                                                                                                                                                      | The Conclave Gradle enclave plugin, which automates the process of building a native SGX binary.                |
| [python&#x2011;enclave&#x2011;adapter/](python-enclave-adapter)                                                                                                                                                                                    | PoC "adapter" enclave for enabling Python support.                                                              |
| [scripts/](scripts)                                                                                                                                                                                                                                | Various scripts for building and testing the SDK.                                                               |

## License
Copyright © 2022, R3 LLC, all rights reserved.

The Conclave Core SDK is distributed under the [Apache License v2.0](LICENSE).

It incorporates components from third-party open source libraries. See the [NOTICE](NOTICE.md) file for more information.

[r3_website]: https://www.r3.com
[conclave_website]: https://www.conclave.net
[docs]: https://docs.conclave.net
[hello_world]: https://docs.conclave.net/running-hello-world.html
