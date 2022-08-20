# Compiling and Running your First Enclave

## Introduction

This tutorial describes how to compile and run the sample hello world app, which you can find in the 
[Conclave tutorials repository](https://github.com/R3Conclave/conclave-tutorials/tree/HEAD/hello-world). This app contains the `ReverseEnclave`, 
which reverses a string provided by the client and passes it back.

We are first going to run the app in [mock mode](enclave-modes.md),
which is the simplest (and least realistic) of the enclave modes.
We will explore running in other modes [later on](#beyond-mock-mode).

If you get stuck, join our [discord server](https://discord.com/invite/dDaBGqGPre) and ask for help!

## Prerequisites

* You need JDK 17 installed in order to build and run the app.
* This tutorial assumes you've read and understood the [conceptual overview](enclaves.md)
  and [architecture overview](architecture.md).

## Compile the sample application

Clone and navigate to the hello world sample.
```bash
git clone https://github.com/R3Conclave/conclave-tutorials.git
cd conclave-tutorials/hello-world
```

The sample app, like all Conclave apps, consists of an
[enclave and a client](architecture.md#primary-entities). The enclave runs inside a host application,
which is provided by the Conclave Core SDK. We can generate a fat JAR for the host and client using the `:bootJar` and
`:shadowJar` tasks, respectively.
=== "Windows"
    ```bash
    gradlew.bat :host:bootJar :client:shadowJar
    ```
=== "macOS / Linux"
    ```bash
    ./gradlew :host:bootJar :client:shadowJar
    ```

!!! note
    * At this time using the JPMS tool `jlink` is not tested.

## Run the host

You can run the host like any other executable JAR with the command
```bash
java -jar host/build/libs/host-mock.jar
```

The sample uses Conclave web host. This is a Spring Boot application so you will see the Spring logo as the web server starts up.

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / /k/ /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v2.4.2)
```


The web server loads the enclave and waits for requests from the client. Amongst the output you will see something like this:

```bash
[main] INFO com.r3.conclave.host.web.EnclaveWebController - Remote attestation for enclave A92F481B7EEAE42D3EBB162BF77613605AF214D77D2E63D75A610FD485CFD7D6:
  - Mode: MOCK
  - Code signers: 0000000000000000000000000000000000000000000000000000000000000000
  - Session signing key: 302A300506032B6570032100D23DD5C05A37CB5B6ED50EA1501E55ABF0EF85B50A97A69D0C3F4F84372AF928
  - Session encryption key: 42CF5E2457B19A9E4FA3716F40CDF6B07A3EEC95D1AFE29C6F1DE99FD0DC647C
  - Product ID: 1
  - Revocation level: 0
```
This is the [remote attestation](enclaves.md#remote-attestation), an object which proves certain information about the
enclave that has been loaded. The private key that corresponds to the "Session encryption key" (printed above) is only
available inside the enclave. Our client will use the "Session encryption key" to encrypt data to send into the enclave.

Once the server is done starting up it will be ready to communicate with the client on http://localhost:8080.
You can proceed to [Run the client](#run-the-client) when you see the following output:

```text
[main] INFO com.r3.conclave.host.web.EnclaveWebHost$Companion - Started EnclaveWebHost.Companion in <SECONDS> seconds 
```

!!! warning
    The sample uses Conclave mail which handles encryption and authentication, but the web host protocol makes use of
    request headers which are not encrypted. Because of this, it is still important to use HTTPS for anything other than
    internal development. Setting up an HTTPS connection is beyond the scope of this tutorial, but you may wish to look
    at configuring Spring Boot or setting up a reverse proxy such as Nginx or Apache. See
    [Conclave web host](conclave-web-host.md) for more information.

## Run the client

Run the client with
```bash
java -jar client/build/libs/client.jar \
  # A constraint which defines which enclaves the client will accept.
  "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE" \
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

Congratulations on running your first enclave!

### Enclave Constraint
The `--constraint` parameter of the client command-line interface defines the 
properties of an acceptable enclave. This includes the signing key 
```
S:0000000000000000000000000000000000000000000000000000000000000000
```
which we saw in the host output in the previous
section.

Try rerunning the command with a different code signer:
```
"S:2222222222222222222222222222222222222222222222222222222222222222 PROD:1 SEC:INSECURE"
```

You should see the following error:
> com.r3.conclave.common.InvalidEnclaveException: Enclave code signer does not match any of the acceptable code signers.
(key hash 0000000000000000000000000000000000000000000000000000000000000000 vs acceptable
2222222222222222222222222222222222222222222222222222222222222222)

For more information on how to choose a constraint, see [Enclave constraints](constraints.md).


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
    gradlew.bat :host:bootJar -PenclaveMode=simulation
    ```
=== "macOS / Linux"
    ```bash
    ./gradlew :host:bootJar -PenclaveMode=simulation
    ```

This would generate the host JAR at `host/build/libs/host-simulation.jar`.

!!! tip
    If working from inside IntelliJ, find the bootJar task of your host project from the Gradle menu on the right-hand 
    side. Modify run configuration of this task by adding the -PenclaveMode=<MODE> flag to the arguments section.
    Now you can run the task.

For **release mode**, the sample app has been configured (in the `build.gradle` of the `enclave` subproject) to use
external signing. Note that **external signing is optional** and you do not have to include it in your own projects.
See [Enclave signing](signing.md) for more information on external signing.

You can generate `host-release.jar` like this:

=== "Windows"
    ```
    // Firstly, build the signing material:
    gradlew.bat prepareForSigning -PenclaveMode=release

    // Generate a signature from the signing material. The password for the sample external key is '12345'
    // Note: you may need to install openssl before you can run this command
    openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin

    // Finally build the signed enclave:
    gradlew.bat :host:bootJar -PenclaveMode="release"
    ```
=== "macOS / Linux"
    ```bash
    // Firstly, build the signing material:
    ./gradlew prepareForSigning -PenclaveMode=release

    // Generate a signature from the signing material. The password for the sample external key is '12345'
    openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin

    // Finally build the signed enclave:
    ./gradlew :host:bootJar -PenclaveMode="release"
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
     inside the container, and the host JAR will be named `host-simulation.jar` rather than `host-mock.jar`.
    * Press `CTRL+D` to exit the container when finished.

    On Windows systems, it is also possible to install the Windows subsystem for linux (2) and download ubuntu 20.04
    from the Windows store. From there, it should be possible to launch an Ubuntu 20.04 shell from the start menu or
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
     inside the container, and the host JAR will be named `host-simulation.jar` rather than `host-mock.jar`.
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

### Run the client in other modes
The client build is independent of the mode. The only difference is that the enclave will have a different signing key
when you are not using mock mode, which will be reflected in the Enclave Constraint provided to the client.

When you run the host, you should see the new signing key in the output: 
```bash hl_lines="3"
[main] INFO com.r3.conclave.host.web.EnclaveWebController - Remote attestation for enclave A92F481B7EEAE42D3EBB162BF77613605AF214D77D2E63D75A610FD485CFD7D6:
  - Mode: MOCK
  - Code signer: 4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
  - Session signing key: 302A300506032B6570032100D23DD5C05A37CB5B6ED50EA1501E55ABF0EF85B50A97A69D0C3F4F84372AF928
  - Session encryption key: 42CF5E2457B19A9E4FA3716F40CDF6B07A3EEC95D1AFE29C6F1DE99FD0DC647C
  - Product ID: 1
  - Revocation level: 0
```

If you haven't built the client already, build it the same way as you would for mock mode


=== "Windows"
    ```bash
    gradlew.bat :client:shadowJar
    ```
=== "macOS / Linux"
    ```bash
    ./gradlew :client:shadowJar
    ```

Then run the client using the signing key from the host's attestation report
```bash
java -jar client/build/libs/client.jar \
  "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE" \
  reverse-me
```

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
