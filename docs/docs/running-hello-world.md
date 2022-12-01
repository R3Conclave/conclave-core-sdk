# Compiling and running your first enclave

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/nwyGL5OemSU"
title="Getting started with Conclave SDK" frameborder="0" allow="accelerometer; autoplay; clipboard-write;
encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>

## Introduction

This tutorial describes how to compile and run the
[sample hello world app](https://github.com/R3Conclave/conclave-tutorials/tree/HEAD/hello-world).
This application contains an enclave which reverses a string provided by the client and passes it back.

If you get stuck at any step, please [talk to us on Discord](https://discord.gg/zpHKkMZ8Sw).

## Prerequisites

* You need JDK 17, which you can download [here](https://www.oracle.com/java/technologies/downloads/). For this 
  sample app, other JDK versions won't work for all modes.

## Compile the sample application

To get the sample application and compile it:

1. Open a command line interface.
2. Clone the conclave-tutorials repository.
```bash
git clone https://github.com/R3Conclave/conclave-tutorials.git
```
3. Go to the hello-world directory.
```bash
cd conclave-tutorials/hello-world
```
4. Generate a fat JAR for the host and client using the `:bootJar` and `:shadowJar` tasks.

=== "Linux/macOS"

    ```bash
    ./gradlew :host:bootJar :client:shadowJar
    ```

=== "Windows"

    ```bash
    gradlew.bat :host:bootJar :client:shadowJar
    ```


When the compilation is complete, you will get a success message:

```text
BUILD SUCCESSFUL in <SECONDS>s
9 actionable tasks: 2 executed, 7 up-to-date
```

## Run the host

Like all Conclave applications, this sample app has an [enclave and a client](architecture.md#primary-entities). The
enclave runs inside a host, which is also a part of the sample application.

To run the host:

1. Run the command:
```bash
java -jar host/build/libs/host-mock.jar
```

This will start *Conclave web host*, a Spring Boot service, to handle the client's requests. You will see the Spring
logo when the web server starts up.

```text

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v2.6.1)

```


The web server loads the enclave and waits for requests from the client. You will see an output like this:

```bash
[main] INFO com.r3.conclave.host.web.EnclaveWebController - Remote attestation for enclave A92F481B7EEAE42D3EBB162BF77613605AF214D77D2E63D75A610FD485CFD7D6:
  - Mode: MOCK
  - Code signers: 0000000000000000000000000000000000000000000000000000000000000000
  - Session signing key: 302A300506032B6570032100D23DD5C05A37CB5B6ED50EA1501E55ABF0EF85B50A97A69D0C3F4F84372AF928
  - Session encryption key: 42CF5E2457B19A9E4FA3716F40CDF6B07A3EEC95D1AFE29C6F1DE99FD0DC647C
  - Product ID: 1
  - Revocation level: 0
```
This output is the [remote attestation](enclaves.md#remote-attestation), an object which proves certain information
about the enclave. The private key corresponding to the ```Session encryption key``` is only available
inside the enclave. The client will use the public key corresponding to the ```Session encryption key``` to encrypt
data and send it to the enclave.

You can confirm that the server started up when you see the following output:

```text
[main] c.r.c.host.web.EnclaveWebHost$Companion  : Started EnclaveWebHost.Companion in <SECONDS> seconds
```

The host is ready to communicate with the client on http://localhost:8080.

Now you can [run the client](#run-the-client).

## Run the client

To run the client:

1. Open another command line interface.
2. Go to the hello-world directory.
```bash
cd conclave-tutorials/hello-world
```
3. Run the client.
```bash
java -jar client/build/libs/client.jar "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE" reverse-me
```
In the above command, the parameters in quotes are the [constraints](constraints.md), and 'reverse-me' is the
string to be reversed.

You will see the output:
```bash
Reversing `reverse-me` gives `em-esrever`
```

You have run your first enclave successfully.


## Sequence of events

The sample application reversed the string securely and confidentially by these steps:

1. The host loads the enclave, which generates a remote attestation report.
2. The client connects to the host and retrieves the attestation report.
3. The client uses the attestation report to verify that it is connected to the expected enclave.
4. The client encrypts the "reverse-me" string using the public key provided in the attestation.
5. In the message to the enclave, the client also includes a public key for the enclave to use when responding.
6. The client sends the encrypted message to the host's REST API.
7. The host passes the encrypted message to the enclave.
8. The enclave decrypts the client's message, reverses the string, and encrypts the response using the client's
   public key.
9. The enclave passes the encrypted response to the host, which forwards it to the client in an HTTP response.

## Beyond mock mode

In the previous section, you run the sample application in [mock mode](enclave-modes.md). Mock mode, as the name
suggests, isn't secure at all.

This section describes how to build and run the sample in other [enclave modes](enclave-modes.md), namely
simulation, debug, and release.

Only the release mode provides confidential guarantees of SGX. You can learn to develop enclaves on Windows, macOS, 
or Linux. However, to develop secure Conclave applications, you must use Linux 5.11 or higher, which has in-kernel 
SGX support.

*Check these [system requirements](enclave-modes.md#system-requirements) for each enclave mode.*

### Build the enclave in other modes

The hello world sample has been [configured](enclave-modes.md#set-the-enclave-mode) such that you can define
the mode using the `enclaveMode` Gradle parameter. For example, you can compile the host for simulation mode with
the command:

=== "Linux/macOS"

    ```bash
    ./gradlew :host:bootJar -PenclaveMode=simulation
    ```
    Replace `simulation` with `debug` for debug mode.

=== "Windows"

    ```bash
    gradlew.bat :host:bootJar -PenclaveMode=simulation
    ```
    Replace `simulation` with `debug` for debug mode.


This generates the host JAR at `host/build/libs/host-simulation.jar`.

#### Build the enclave in release mode

For *release* mode, the sample application is configured (in the `build.gradle` of the `enclave` subproject) to use
external code signing. Note that *external code signing is optional*.
See [enclave signing](signing.md) for more information on external signing.

To generate `host-release.jar`:

=== "Linux/macOS"

    1. Install [OpenSSL](https://www.openssl.org/source/) on your system if it's not already installed.
    2. Build the signing material:
    ```bash
    ./gradlew prepareForSigning -PenclaveMode=release
    ```
    3. Generate a signature from the signing material. The password for the sample external key is '12345'.
    ```bash
    openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin
    ```    
    4. Build the signed enclave:
    ```bash
    ./gradlew :host:bootJar -PenclaveMode="release"
    ```

=== "Windows"

    1. Install [OpenSSL](https://www.openssl.org/source/) on your system if it's not already installed.
    2. Build the signing material:
    ```bash
    gradlew.bat prepareForSigning -PenclaveMode=release
    ```
    3. Generate a signature from the signing material. The password for the sample external key is '12345'.
    ```bash
    openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin
    ```
    4. Build the signed enclave:
    ```bash
    gradlew.bat :host:bootJar -PenclaveMode="release"
    ```

### Run the enclave in other modes

=== "Linux"

    On a Linux machine that meets the [system requirements](enclave-modes.md#system-requirements), you can run your
    app in all the modes the same way as in mock mode.

=== "macOS"

    You need a Linux environment to run the host in simulation mode. This section describes how to create such an
    environment using Conclave's Docker integration.

    !!!Note
        * You need Docker to run the *host* on macOS. You can run the *client* on macOS without Docker.
        * It is not possible to run enclaves in debug or release mode on macOS.
        * Conclave works *only* in [mock mode](enclave-modes.md#mock-mode) on
          [new Mac computers with Apple silicon](https://support.apple.com/en-in/HT211814) due to the reliance on x64
          binaries.


    1. Create the `conclave-build` Docker image, which you can use to create a Linux environment to run the host.
       ```bash
       ./gradlew enclave:setupLinuxExecEnvironment
       ```
    2. Instantiate a container using the `conclave-build` image.
       ```bash
       docker run -it --rm -p 8080:8080 -v ${PWD}:/project -w /project conclave-build /bin/bash
       ```
       This will give you a bash shell in the container that simulates a native Linux machine. You can tweak this
       command according to the specific needs of your project. Check the
       [Docker reference manual](https://docs.docker.com/engine/reference/commandline/run/) or run
       `docker <command> --help` on the Docker command line interface for more information.

    3. You can now run the host and client as we did for mock mode. The only differences are that the host must be
       run from inside the container, and the host JAR will be named `host-simulation.jar` rather than `host-mock.jar`.
    4. Press `CTRL+D` to exit the container when finished.

=== "Windows"

    You need a Linux environment to run the host in simulation mode. This section describes how to create such an
    environment using Conclave's Docker integration.

    !!! note
        * You need Docker to run the *host* on Windows. You don't need Docker to run the client.
        * It is not possible to run enclaves in debug or release mode on Windows.


    1. Create the `conclave-build` Docker image, which you can use to create a Linux environment to run the host.
       ```bash
       gradlew.bat enclave:setupLinuxExecEnvironment
       ```
    2. Instantiate a container using the `conclave-build` image using
      ```bash
      docker run -it --rm -p 8080:8080 -v ${PWD}:/project -w /project conclave-build /bin/bash
      ```
      This will give you a bash shell in the container that simulates a native Linux machine. You can tweak this command
      according to the specific needs of your project. Please take a look at the explanation of the options used in this
      command [at the end of this tutorial](#appendix-summary-of-docker-command-options).
      Check the [Docker reference manual](https://docs.docker.com/engine/reference/commandline/run/) or run
      `docker <command> --help` on the Docker command line interface for more information.
    3. Run the host and client as you did for mock mode. The only differences are that the host must be run from inside
      the container, and the host JAR is named `host-simulation.jar` rather than `host-mock.jar`.
    4. Press `CTRL+D` to exit the container when finished.

    Alternatively, you can also install the Windows subsystem for Linux (2) and download Ubuntu 20.04 from the Windows
    store. From there, you can launch an Ubuntu 20.04 shell from the start menu and build or run Conclave applications
    as in a native Linux environment.
    Instructions for installing WSL-2 can be found [here](https://docs.microsoft.com/en-us/windows/wsl/install).
    Please [talk to us on Discord](https://discord.gg/zpHKkMZ8Sw) if you need help with this.


!!!Note

    If your platform or container doesn't support SGX enclaves, you might see the following message when you run the
    host:
    ```text
    This platform does not support hardware enclaves: SGX_DISABLED_UNSUPPORTED_CPU: SGX is not supported by the
    CPU in this system
    ```
You will still be able to use simulation mode even if you see this message.

### Run the client in other modes
The client build is independent of the mode. The only difference is that the enclave might have a different code
signer, which will be reflected in the enclave constraint provided to the client.


=== "Linux/macOS"

    1. When you run the host, you should see the new code signer in the output:
    ```bash hl_lines="3"
    [main] INFO com.r3.conclave.host.web.EnclaveWebController -
    Remote attestation for enclave A92F481B7EEAE42D3EBB162BF77613605AF214D77D2E63D75A610FD485CFD7D6:
    - Mode: SIMULATION
    - Code signer: 4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
    - Session signing key: 302A300506032B6570032100D23DD5C05A37CB5B6ED50EA1501E55ABF0EF85B50A97A69D0C3F4F84372AF928
    - Session encryption key: 42CF5E2457B19A9E4FA3716F40CDF6B07A3EEC95D1AFE29C6F1DE99FD0DC647C
    - Product ID: 1
    - Revocation level: 0
    ```
    2. If you haven't built the client already, build it the same way as in mock mode:
    ```bash
    ./gradlew :client:shadowJar
    ```
    3. Run the client using the code signer from the host's attestation report
    ```bash
    java -jar client/build/libs/client.jar "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE" reverse-me
    ```
=== "Windows"

    1. When you run the host, you should see the new code signer in the output:
    ```bash hl_lines="3"
    [main] INFO com.r3.conclave.host.web.EnclaveWebController -
    Remote attestation for enclave A92F481B7EEAE42D3EBB162BF77613605AF214D77D2E63D75A610FD485CFD7D6:
    - Mode: SIMULATION
    - Code signer: 4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
    - Session signing key: 302A300506032B6570032100D23DD5C05A37CB5B6ED50EA1501E55ABF0EF85B50A97A69D0C3F4F84372AF928
    - Session encryption key: 42CF5E2457B19A9E4FA3716F40CDF6B07A3EEC95D1AFE29C6F1DE99FD0DC647C
    - Product ID: 1
    - Revocation level: 0
    ```
    2. If you haven't built the client already, build it the same way as in mock mode:
    ```bash
    gradlew.bat :client:shadowJar
    ```
    3. Run the client using the code signer from the host's attestation report
    ```bash
    java -jar client/build/libs/client.jar "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE" reverse-me
    ```

## Next steps

Now you know how to build and run the hello world sample, see how it is implemented in
[Writing hello world](writing-hello-world.md).

You can also learn more about enclaves [here](enclaves.md.).

## Got this far? Join the community

You can ask your questions on our Discord server. Conclave's development team answers your questions during UK
business hours (GMT 0900-1700).

You can discuss everything about Conclave on our mailing list. You can also use the mailing list to discuss SGX and
other Trusted Execution Environment (TEE) technologies.


[:fontawesome-brands-discord: Join our discord server](https://discord.gg/zpHKkMZ8Sw){: .md-button }

[:fontawesome-solid-paper-plane: Join conclave-discuss@groups.io](https://groups.io/g/conclave-discuss){: .md-button }

[:fontawesome-solid-paper-plane: Email us](mailto:conclave@r3.com){: .md-button }
