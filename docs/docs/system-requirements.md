# System requirements

## Operating Systems
We test building and running release-mode enclaves on Ubuntu 18.04 LTS Server x86-64.

### Running
With some limitations, it's possible to run Conclave enclaves even if the host Operating System isn't Linux. The table below correlates the Operating System and the possible Conclave running [modes](tutorial.md#enclave-modes).

| OS      | Mock               | Simulation                                   | Debug                     | Release                  |
|---------|:------------------:|:--------------------------------------------:|:-------------------------:|:------------------------:|
| Linux   | :heavy_check_mark: | :heavy_check_mark:                           | :heavy_check_mark:        | :heavy_check_mark:       |
| macOS   | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |
| Windows | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |

!!! info ""
    :fontawesome-brands-linux:: Using WSL or Docker with Linux.

### Building
Although it is not possible to _run_ all [modes](tutorial.md#enclave-modes) on every operating system, it is possible to
_develop and build_ enclaves on Linux, macOS and Windows. You can develop and test your enclaves using mock mode then
build a simulation, debug or release mode enclave for deployment to a Linux platform for testing or production.

In order to build a simulation, debug or release mode enclave on macOS or Windows (or to run a simulation mode enclave 
on macOS or Windows), Conclave needs access to a Linux build environment. This is automatically created during the build 
process using Docker. If you do not have Docker installed then the build will generate an error prompting you to
install Docker on your system. Once Docker is installed and added to your `PATH` environment variable you can proceed
to build Simulation, Debug or Release mode enclaves. Docker is not required if you are using a Linux system.

## JDKs
We test building and running conclave applications using the latest OpenJDK 8 and 11, as of the most recent release.
### Enclave
All code inside the enclave module must be compatible with Java 8. The Conclave gradle plugin will automatically compile the enclave module to Java 8 bytecode, regardless of the Java version used to build the rest of the application.

### Host and Client
The host and client are normal Java libraries targeting Java 8, so all code outside the enclave module can be written and built with any Java version that is 8 or higher.

