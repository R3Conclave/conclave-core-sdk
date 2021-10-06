# System requirements

## Operating Systems
We test building and running release-mode enclaves on Ubuntu 18.04 LTS Server x86-64.

### Building conclave projects
In order to build a simulation, debug or release mode enclave (see [modes](tutorial.md#enclave-modes)) on macOS or
Windows, Conclave needs access to a Linux build environment. This is automatically created during the build process
using Docker. If you do not have Docker installed then the build will generate an error prompting you to install Docker
on your system. Once Docker is installed and added to your `PATH` environment variable you can proceed to build
Simulation, Debug or Release mode enclaves. Docker is not required if you are using a Linux system.

### Running conclave projects
Although it is possible to _build_ an enclave project in any [mode](tutorial.md#enclave-modes) on any of the listed
platforms, it is only possible to _run_ simulation, debug or release mode enclaves in a Linux environment. It is
therefore recommended that you develop and test your enclaves using mock mode first before building in simulation, 
debug or release mode for testing or deployment on a Linux system.

It's possible to run Conclave applications built in simulation mode (but not debug or release) on operating systems
other than Linux, but this requires some additional work to set up a linux environment:

=== "MacOS"
    **Instructions for running apps under MacOS:**

    1. Build you project as you normally would in your desired mode, e.g.: `./gradlew build -PenclaveMode=simulation`
    1. Download and install docker desktop.
    1. Navigate to your project and run the following command: `./gradle enclave:setupLinuxExecEnvironment`. This
       will create a docker image called `conclave-build` that can be instantiated as a container and used to run
       conclave projects.
    1. Execute the following command from the root directory of your project to instantiate a container using the image
       `docker run -it --rm -p 9999:9999 -v ${PWD}:/project -w /project conclave-build /bin/bash`. This will give you a
       bash shell in a Linux environment that you can use to run your project as if you were on a native Linux machine.
       Please note, this command may not be suitable for _your_ specific project! Consult the instructions below for
       more information.

    **Summary of docker command options:**

    - `-i`: Run container interactively.
    - `-t`: Allocate a pseudo TTY.
    - `--rm`: Automatically remove the container once it exits.
    - `-p <system-port>:<container-port>`: This options maps a port from your system to a port "inside" the container.
       If your project listens for connections on any port, you will need to use this option to forward that port into
       the container. In the case of the command above, the port 9999 is mapped to port 9999 inside the container.
    - `-v <system-dir>:<container-dir>`: This option mounts a directory from your filesystem inside the container. You
       will need to ensure that any files your project needs to access at runtime are mounted inside the container.
    - `-w <directory>` This is the directory within the container where you want the prompt to start (in this case, the
       directory where the project was mounted).

=== "Windows"
    **Instructions for running apps under Windows (powershell):**

    1. Build you project as you normally would in your desired mode, e.g.: `.\gradlew.bat build -PenclaveMode=simulation`
    1. Download and install docker desktop.
    1. Navigate to your project and run the following command: `.\gradlew.bat enclave:setupLinuxExecEnvironment`. This
       will create a docker image called `conclave-build` that can be instantiated as a container and used to run
       conclave projects.
    1. Execute the following command from the root directory of your project to instantiate a container using the image
       `docker run -it --rm -p 9999:9999 -v ${PWD}:/project -w /project conclave-build /bin/bash`. This will give you a
       bash shell in a Linux environment that you can use to run your project as if you were on a native Linux machine.
       Please note, this command may not be suitable for _your_ specific project! Consult the instructions below for
       more information.

    **Summary of docker command options:**

    - `-i`: Run container interactively.
    - `-t`: Allocate a pseudo TTY.
    - `--rm`: Automatically remove the container once it exits.
    - `-p <system-port>:<container-port>`: This options maps a port from your system to a port "inside" the container.
       If your project listens for connections on any port, you will need to use this option to forward that port into
       the container. In the case of the command above, the port 9999 is mapped to port 9999 inside the container.
    - `-v <system-dir>:<container-dir>`: This option mounts a directory from your filesystem inside the container. You
       will need to ensure that any files your project needs to access at runtime are mounted inside the container.
    - `-w <directory>` This is the directory within the container where you want the prompt to start (in this case, the
       directory where the project was mounted).

    !!! info
        On windows systems, it is also possible to install the windows subsystem for linux (2) and download ubuntu 18.04
        from the windows store. From there, it should be possible to launch a ubuntu 18.04 shell from the start menu or
        desktop and proceed to build or run conclave applications as you would in a native Linux environment.
        Instructions for installing wsl-2 can be found [here](https://docs.microsoft.com/en-us/windows/wsl/install).
        Please note that this method hasn't been extensively tested.

!!! tip
    Please consult the [Docker reference manual](https://docs.docker.com/engine/reference/commandline/run/) or run
    `docker <command> --help` for more information on the docker command line interface.

!!! warning
    Due to overheads involved with file IO in docker, Windows and MacOS builds using docker may run slower than those in
    native Linux environments.

The table below summarizes which [modes](tutorial.md#enclave-modes) can be used in which environments.

| OS      | Mock               | Simulation                                   | Debug                     | Release                  |
|---------|:------------------:|:--------------------------------------------:|:-------------------------:|:------------------------:|
| Linux   | :heavy_check_mark: | :heavy_check_mark:                           | :heavy_check_mark:        | :heavy_check_mark:       |
| macOS   | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |
| Windows | :heavy_check_mark: | :heavy_check_mark::fontawesome-brands-linux: | :heavy_multiplication_x:  | :heavy_multiplication_x: |

!!! info ""
    :fontawesome-brands-linux:: Using WSL or Docker desktop (see Windows/MacOS instructions above).

## JDK Compatibility
As of the most recent release, we test building and running conclave applications using the latest OpenJDK 8 and 11.

### Enclave
All code inside the enclave module must be compatible with Java 8. The Conclave gradle plugin will automatically compile
the enclave module to Java 8 bytecode, regardless of the Java version used to build the rest of the application.

### Host and Client
The host and client are normal Java libraries targeting Java 8, so all code outside the enclave module can be written
and built with any Java version that is 8 or higher.

## Gradle
The tool used to build Conclave is Gradle. The recommended version is 6.6.1.