# Compiling and Running your First Enclave

## Introduction

This tutorial describes how to compile and run the **sample app**, which you can find in the `hello-world` directory of
the Conclave SDK. This app contains the `ReverseEnclave`, which reverses a string provided by the client and
passes it back.

We are first going to run the app in [mock mode](enclave-modes.md),
which is the simplest (and least realistic) of the enclave modes.
We will explore running in other modes [later on](#beyond-mock-mode).

If you get stuck, join our [discord server](https://discord.com/invite/dDaBGqGPre) and ask for help!

## Prerequisites

* You need a Java 8 or 11 JDK (your choice) installed in order to build and run the app.
* You need the Conclave SDK. If you don't have it, grab a copy from [conclave.net](https://www.conclave.net).
* This tutorial assumes you've read and understood the [conceptual overview](enclaves.md)
  and [architecture overview](architecture.md).

## Compile the sample application

Navigate to the hello world sample.
```bash
cd /path/to/conclave/sdk/hello-world
```

The sample app, like all Conclave apps, consists of an
[an enclave and a client](architecture.md#primary-entities). The enclave runs inside a host app,
which we provide. We can generate a fat JAR for the host and client using the `:shadowJar` task.
=== "Windows"
    ```bash
    gradlew.bat :host:shadowJar :client:shadowJar
    ```
=== "MacOS / Linux"
    ```bash
    ./gradlew :host:shadowJar :client:shadowJar
    ```

!!! note
    * At this time using the JPMS tool `jlink` is not tested.

## Run the host

You can run the host like any other executable JAR with the command
```bash
java -jar host/build/libs/host-mock-1.2-RC1.jar
```

The sample uses Conclave Web Host, a Spring Boot application which loads the enclave and waits for requests from the
client. Amongst the output, you will see something like this:
```bash
[main] INFO com.r3.conclave.host.web.EnclaveWebController - Remote attestation for enclave A92F481B7EEAE42D3EBB162BF77613605AF214D77D2E63D75A610FD485CFD7D6:
  - Mode: MOCK
  - Code signing key hash: 0000000000000000000000000000000000000000000000000000000000000000
  - Public signing key: 302A300506032B6570032100D23DD5C05A37CB5B6ED50EA1501E55ABF0EF85B50A97A69D0C3F4F84372AF928
  - Public encryption key: 42CF5E2457B19A9E4FA3716F40CDF6B07A3EEC95D1AFE29C6F1DE99FD0DC647C
  - Product ID: 1
  - Revocation level: 0
```
This is the [remote attestation](enclaves.md#remote-attestation), an object which proves certain information about the
enclave that has been loaded. The private key that corresponds to the "Public encryption key" (printed above) is only
available inside the enclave. Our client will use the "Public encryption key" to encrypt data to send into the enclave.

You can proceed to [Run the client](#run-the-client) when you see the following output:

```text
[main] INFO com.r3.conclave.host.web.EnclaveWebHost$Companion - Started EnclaveWebHost.Companion in <SECONDS> seconds 
```

## Run the client

Run the client with
```bash
java -jar client/build/libs/client-1.2-RC1.jar \
  # A constraint which defines which enclaves the client will accept.
  --constraint "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE" \
  # A file in which to store any persistent state for the client.
  --file-state "client-state" \
  # The URL of the host server.
  --url "http://localhost:8080" \
  # The string to reverse
  reverse-me
```

You should see the output
```bash
Reversing `reverse-me` gives `em-esrever`
```
which has been produced by the following sequence of events:

1. The host loaded the enclave, which generated a remote attestation report.
2. The client connected to the host and downloaded the attestation report.
3. The client used the attestation report to verify that it was connected to the enclave it expected, and that the
   enclave was running in the expected mode.
4. The client encrypted the "reverse-me" string using the public key provided in the attestation.
5. As part of the message to the enclave, the client also included a public key for the enclave to use when responding.
6. The client sent the encrypted message to the host's REST API.
7. The host passed the encrypted message to the enclave.
8. The enclave decrypted the client's message, reversed the string, and encrypted the response using the client's
   public key.
9. The enclave passed the encrypted message to the host, was sent back to the client as an HTTP response to the same
   request.

!!! note
    The `--constraint` parameter of the client command-line interface contains the
    [Enclave Constraint](writing-hello-world.md#constraints), which defines the 
    properties of an acceptable enclave. This includes the signing key 
    `S:0000000000000000000000000000000000000000000000000000000000000000`, which we saw in the host output in the previous
    section.

    Try removing the `client-state` file and rerunning the command with a different code signer:
    `"S:2222222222222222222222222222222222222222222222222222222222222222 PROD:1 SEC:INSECURE"`
    
    You should see the following error:
    > com.r3.conclave.common.InvalidEnclaveException: Enclave code signer does not match any of the acceptable code signers.
    (key hash 0000000000000000000000000000000000000000000000000000000000000000 vs acceptable
    2222222222222222222222222222222222222222222222222222222222222222)

Congratulations on running your first enclave!


## Beyond mock mode
Eventually you will want to run your application in one of the other [enclave modes](enclave-modes.md), namely simulation,
debug, or release. This section describes how to build and run the sample in these other modes.

**First, make sure that your system fulfills the
[system requirements](enclave-modes.md#system-requirements) for your desired mode.**

### Build the enclave in other modes
The hello world sample has been [configured](enclave-modes.md#set-the-enclave-mode) such that you can supply the mode
via the `enclaveMode` Gradle parameter. For example, you could compile the host for simulation mode with the command
=== "Windows"
    ```bash
    gradlew.bat :host:shadowJar -PenclaveMode=simulation
    ```
=== "MacOS / Linux"
    ```bash
    ./gradlew :host:shadowJar -PenclaveMode=simulation
    ```

This would generate the host JAR at `host/build/libs/host-simulation-1.2-RC1.jar`.

!!! tip
    If working from inside IntelliJ, find the shadowJar task of your host project from the Gradle menu on the right
    hand side. Modify run configuration of this task by adding the -PenclaveMode=<MODE> flag to the arguments section.
    Now you can run the task.

For **release mode**, the sample app has been configured (in the `build.gradle` of the `enclave` subproject) to use
external signing. Note that **external signing is optional** and you do not have to include it in your own projects.
See [Enclave signing](signing.md) for more information on external signing.

You can generate `host-release-1.2-RC1.jar` like this:

=== "Windows"
    ```bash
    // Firstly, build the signing material:
    gradlew.bat prepareForSigning -PenclaveMode=release

    // Generate a signature from the signing material. The password for the sample external key is '12345'
    openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin

    // Finally build the signed enclave:
    gradlew.bat :host:shadowJar -PenclaveMode="release"
    ```
=== "MacOS / Linux"
    ```bash
    // Firstly, build the signing material:
    ./gradlew prepareForSigning -PenclaveMode=release

    // Generate a signature from the signing material. The password for the sample external key is '12345'
    openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin

    // Finally build the signed enclave:
    ./gradlew :host:shadowJar -PenclaveMode="release"
    ```


### Run the enclave in other modes

=== "Windows"
    A Linux environment is required to run the host in simulation mode. This section describes how to instantiate such an
    environment using Conclave's Docker integration.
    
    !!! note
        * Docker is only required to run the *host* on Windows. The client can be run without Docker.
        * It is not possible to run enclaves in debug or release mode on Windows.

    * Create the `conclave-build` Docker image, which we will use to instantiate a Linux environment where we can run
    the host.
    ```
    gradlew.bat enclave:setupLinuxExecEnvironment
    ```
    * Instantiate a container using the `conclave-build` image using
    ```bash
    docker run -it --rm -p 8080:8080 -v ${PWD}:/project -w /project conclave-build /bin/bash
    ```
    This will give you a bash shell in the container that you can use to run your project as if you were on 
    a native Linux machine. Please note, this command may not be suitable for _your_ specific project! We've provided
    an explanation of the options used in this command [at the end of this tutorial](#appendix-summary-of-docker-command-options).
    Consult the [Docker reference manual](https://docs.docker.com/engine/reference/commandline/run/) or run
    `docker <command> --help` for more information on the docker command line interface.

    * You can now run the host and client as we did for mock mode. The only differences are that the host must be run from
     inside the container, and the host JAR will be named `host-simulation-1.2-RC1.jar` rather than `host-mock-1.2-RC1.jar`.
    * Press `CTRL+D` to exit the container when finished.

    On Windows systems, it is also possible to install the Windows subsystem for linux (2) and download ubuntu 18.04
    from the Windows store. From there, it should be possible to launch an Ubuntu 18.04 shell from the start menu or
    desktop and proceed to build or run Conclave applications as you would in a native Linux environment.
    Instructions for installing wsl-2 can be found [here](https://docs.microsoft.com/en-us/windows/wsl/install).
    Please note that this method hasn't been extensively tested.

=== "macOS"
    A Linux environment is required to run the host in simulation mode. This section describes how to instantiate such an
    environment using Conclave's Docker integration.

    !!! note
        * Docker is only required to run the *host* on macOS. The client can be run without Docker.
        * It is not possible to run enclaves in debug or release mode on macOS.

    * Create the `conclave-build` Docker image, which we will use to instantiate a Linux environment where we can run
    the host.
    ```
    ./gradlew enclave:setupLinuxExecEnvironment
    ```
    * Instantiate a container using the `conclave-build` image using
    ```bash
    docker run -it --rm -p 8080:8080 -v ${PWD}:/project -w /project conclave-build /bin/bash
    ```
    This will give you a bash shell in the container that you can use to run your project as if you were on 
    a native Linux machine. Please note, this command may not be suitable for _your_ specific project! We've provided
    an explanation of the options used in this command [at the end of this tutorial](#appendix-summary-of-docker-command-options).
    Consult the [Docker reference manual](https://docs.docker.com/engine/reference/commandline/run/) or run
    `docker <command> --help` for more information on the docker command line interface.

    * You can now run the host and client as we did for mock mode. The only differences are that the host must be run from
     inside the container, and the host JAR will be named `host-simulation-1.2-RC1.jar` rather than `host-mock-1.2-RC1.jar`.
    * Press `CTRL+D` to exit the container when finished.

=== "Linux"
    If the system requirements are satisfied for your desired mode,
    you can run your app in the same way on a Linux system as you would in mock mode.

!!! note
    If your platform or container doesn't support SGX enclaves, you should see the following message when you run the host:
    ```text
    This platform does not support hardware enclaves: SGX_DISABLED_UNSUPPORTED_CPU: SGX is not supported by the CPU in this system
    ```
    Don't worry, you will still be able to use simulation mode even if you see this message.

## Next steps
Now you know how to build and run the hello world sample, see how it is implemented in [Writing hello world](writing-hello-world.md).

## Got this far? Join the community!

A discord server is available where you can find the development team during UK office hours (GMT 0900-1700).
There's a public mailing list for users to discuss Conclave, and we also welcome general SGX talk.

[:fontawesome-brands-discord: Join our discord server](https://discord.com/invite/dDaBGqGPre){: .md-button }

[:fontawesome-solid-paper-plane: Join conclave-discuss@groups.io](https://groups.io/g/conclave-discuss){: .md-button }

[:fontawesome-solid-paper-plane: Email us directly](mailto:conclave@r3.com){: .md-button }

## Appendix: Summary of Docker command options
- `-i`: Run container interactively.
- `-t`: Allocate a pseudo TTY.
- `--rm`: Automatically remove the container once it exits.
- `-p <system-port>:<container-port>`: This options maps a port from your system to a port "inside" the container.
  If your project listens for connections on any port, you will need to use this option to forward that port into
  the container. In the case of the command above, the port 8080 is mapped to port 8080 inside the container.
- `-v <system-dir>:<container-dir>`: This option mounts a directory from your filesystem inside the container. You
  will need to ensure that any files your project needs to access at runtime are mounted inside the container.
- `-w <directory>` This is the directory within the container where you want the prompt to start (in this case, the
  directory where the project was mounted).