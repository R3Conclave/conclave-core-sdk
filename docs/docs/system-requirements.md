# System requirements

## Running Conclave applications
### Operating Systems
With some limitations, it's possible to run Conclave enclaves even if the host Operating System isn't Linux. 

The table below correlates the Operating System and the possible Conclave running [modes](tutorial.md#enclave-modes).

| OS      | Mock               | Simulation                                   | Debug                     | Release                  |
|---------|:------------------:|:--------------------------------------------:|:-------------------------:|:------------------------:|
| Linux   | :heavy_check_mark: | :heavy_check_mark:                           | :heavy_check_mark:        | :heavy_check_mark:       |
| macOS   | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |
| Windows | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |

!!! info ""
    :fontawesome-brands-linux:: Using WSL or Docker with Linux.

Although it is not possible to _run_ all [modes](tutorial.md#enclave-modes) on every operating system, it is possible to
_develop and build_ enclaves on Linux, macOS and Windows. You can develop and test your enclaves using mock mode then
build a Simulation, Debug or Release mode enclave for deployment to a Linux platform for testing or production.

In order to build a Simulation, Debug or Release mode enclaves on macOS or Windows, or to run a Simulation mode enclave 
on macOS or Windows, Conclave needs access to a Linux build environment. This is automatically created during the build 
process using Docker. If you do not have Docker installed then the build will generate an error prompting you to
install Docker on your system. Once Docker is installed and added to your `PATH` environment variable you can proceed
to build Simulation, Debug or Release mode enclaves. Docker is not required if you are using a Linux system.

### Supported JVMs
Conclave clients can run on any device but require a JVM. During our tests, we used OpenJDK's HotSpot running on Ubuntu 18.04 LTS.

## Requirements for developing applications with Conclave

### IDEs
There are no specific IDE requirements, though we recommend [IntelliJ IDEA](https://www.jetbrains.com/idea/).

### Supported JDKs
You require a JDK, and we test against the following:

| JDK      | Version |
|----------|---------|
| OpenJDK  | 8       |
| OpenJDK  | 11      |

## Linux distros and versions
We test building and running release-mode enclaves on the following distros:

| Platform                 | CPU Architecture |
|--------------------------|------------------|
| Ubuntu 18.04 LTS Server  | x86-64           |
| Ubuntu 18.04 LTS Desktop | x86-64           |