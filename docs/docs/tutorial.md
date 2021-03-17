# First enclave

!!! important

    * You need the Conclave SDK. If you don't have it, grab a copy from [conclave.net](https://www.conclave.net).
    * This tutorial assumes you've read and understood the [conceptual overview](enclaves.md).

You can find a **sample app** in the `hello-world` directory of your SDK. You can use this app as a template 
for your own if you want a quick start. We will cover:

1. How to set up your machine.
2. How to compile and run the sample app.
2. [How to write the sample app](writing-hello-world.md).

## Enclave modes

SGX enclaves can be used in one of four modes, in order of increasing realism:

1. **Mock**: your enclave class is created in the host JVM and no native or SGX specific code is used. This provides a
   pure Java development experience.
1. **Simulation**: an enclave is compiled to native machine code and loaded, but SGX hardware doesn't need to be present.
1. **Debug**: the enclave is loaded using SGX hardware and drivers, but with a back door that allows debugger access to the memory.
1. **Release**: the enclave is loaded using SGX hardware and drivers, and there's no back door. This is the real deal.

Only release mode locks out the host and provides the standard SGX security model.

## Setting up your machine

For this tutorial you will need [Java 8 or 11](system-requirements.md#supported-jdks) (your choice). If you use IntelliJ IDEA the IDE can download both a JDK
and the Gradle build system for you, so you don't need anything to get started except the IDE itself (the free 
Community Edition works fine).

Currently, we support developing enclaves on [Windows, macOS and Linux](system-requirements.md#operating-systems). However, there are a few platform specific
differences to be aware of.

Firstly, you need a [Linux](system-requirements.md#linux-distros-and-versions) environment to build and execute enclaves, including for native testing. This is because
enclaves are Linux shared libraries with special extensions. If you are not using Linux you will need to install Docker.
On Windows and macOS, Conclave uses Docker to build the enclave in a Linux environment.
[Instructions are provided below](#running-the-host) to show you how to use Docker on Windows and macOS to run your
entire application in "simulation mode". Alternatively, for day to day development the mock API is plenty sufficient
and allows you to debug into enclave calls as well. Compiling a real enclave is only needed for integration testing
against the embedded JVM, or real deployment.

Secondly, when building enclaves using the `graalvm_native_image` runtime, Conclave internally uses the C++ compiler
gcc. This is automatically installed when building on Windows and macOS but on Linux you need to make sure you have
installed gcc yourself. If your build system uses the aptitude package manager then you can install everything you need with
this command:

```bash
sudo apt-get install build-essential
```

Enclaves can run in simulation mode without requiring any special setup of Linux or SGX capable hardware. However you 
of course get no hardware protections. To run against real SGX hardware you must perform some 
[additional machine setup](machine-setup.md).

## Compiling the sample enclave

**Step 1:** Import the project
 
![Import the project](./images/import.png)

**Step 2:** Look at the Conclave SDK's top level directory

![Look at the SDK's top level directory](./images/import-sdk.png) 
 
**Step 3:** When notified that there's a Gradle build script, click "hello-world" to import the project.

![Import Gradle script](./images/gradle-import.png) 
 
**Step 4:** Double-click on `:host:assemble`. This is the second
highlighted `assemble` in the screenshot below. Voila! :smile: You have just built your first enclave.
  
![Double-click on `:host:assemble`](./images/gradle-tasks.png)
  
Now explore the `build` folder. 

![Explore the `build` folder.](./images/build-artifact.png)  

As normal with Gradle, the `assemble` task has bundled the program into a zip, with startup scripts. These scripts are
nothing special - they just set up the classpath. You could also e.g. make a fat JAR if you want. 

### Selecting your mode

In the sample app, the `assemble` task will build the app for **simulation mode** by default.

Use the `-PenclaveMode` argument to configure the mode.
If you are using SGX hardware, you can build the app for **debug mode** with the command:

```
./gradlew host:assemble -PenclaveMode=debug
```

If working from inside IntelliJ, start the assemble task for the host project from the tree on the right hand side,
and then edit the created run config. Add the `-PenclaveMode=debug` flag to the arguments section of the run config.

For **release mode**, the sample app has been configured (in the `build.gradle` of the `enclave` subproject) to use external
signing. This means it must be built in multiple stages:

!!! note
    See [Enclave signing](signing.md) for more information.

```bash
// Firstly, build the signing material:
./gradlew prepareForSigning -PenclaveMode=release

// Generate a signature from the signing material. The password for the sample external key is '12345'
openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin

// Finally build the signed enclave:
./gradlew build -PenclaveMode="release"
```

!!! note
    The only mode we cannot access using the `-PenclaveMode` flag is **mock mode**. Mock mode must be accessed via the
    Mock API. More on this [later](writing-hello-world.md#testing).


## Running the host
=== "Linux"

    Just run the host app like any app - no special customisation or setup is required with Conclave! Here we will run
    a shell script generated by Gradle that starts the JVM:

    ```bash
    ./gradlew host:installDist
    cd host/build/install
    ./host/bin/host
    ```

    Gradle can also create `.tar.gz` files suitable for copying to the Linux host, fat JARs, WAR files for deployment into
    servlet containers and various other ways to deploy your app.

    !!! note
        At this time using the JPMS tool `jlink` is not tested.

=== "macOS"

    On macOS there is a script that lets you run Gradle (and by extension anything it runs) inside a Linux environment based
    on the Conclave build container. It wraps Docker and makes it simpler to work with.

    Just use the `../scripts/container-gradle` script as a replacement for `gradlew`. You might want to add it to your
    `$PATH` variable. To run the host, use this command:
    ```text
    ../scripts/container-gradle host:run
    ```

    For more information see the [Container Gradle](container-gradle.md) page.

    !!! tip
        Don't be surprised if the application gets built again from scratch! We do this to ensure the build and
        runtime environments match.

=== "Windows"

    On Windows you can still test locally in simulation mode using a Docker container. However you may need to configure
    mounts and other parameters yourself. Refer to the `scripts/container-gradle` file to see how this is done on macOS.


    ___Windows PowerShell___
    ```
    gradlew.bat host:installDist
    docker run -it --rm -p 9999:9999 -v ${PWD}:/project -w /project conclave-build /bin/bash
    ```

    This will build the app on your Windows machine, then mount the app into a Linux container and get a shell. Next,
    run the app from within the container:
    ```
    cd host/build/install
    ./host/bin/host
    ```

    !!! tip
        Please consult the [Docker reference manual](https://docs.docker.com/engine/reference/commandline/run/) to understand
        what switches you can pass to the `docker run` command.

If your Linux machine (or container) doesn't have SGX, you should see something like the following. Don't worry, you can still complete the tutorial because we are using [simulation mode](#selecting-your-mode):
```text
This platform does not support hardware enclaves: SGX_DISABLED_UNSUPPORTED_CPU: SGX is not supported by the CPU in this system
```

You can proceed to [Running the client](#running-the-client) when you see the following:
```text
Listening on port 9999. Use the client app to send strings for reversal.
```

### IntelliJ configuration
If you are using IntelliJ, you may want to create a launch configuration to incorporate the `build` and `deploy` stages. If using the
`container-gradle` script on macOS, IntelliJ does expect the command to launch Gradle to be called `gradle` or `gradlew`.
What you can do is rename `gradlew` to something else, then copy the `scripts/container-gradle` script to be called
`gradlew` in your project root. Finally, edit the last line of the script to start the renamed script. Therefore
IntelliJ will run the `container-gradle` script whilst  thinking it's running normal Gradle.


## Running the client

The host has opened up a TCP port which will now listen for requests from remote clients. So, let's run the client app:

=== "Linux / macOS"
    ```bash
    ./gradlew client:run --args="Reverse me"
    ```
=== "Windows"
    ```bash
    gradlew.bat client:run --args="Reverse me"
    ```

!!! tip
    Docker is only required to run the *host* on macOS or windows. The client can be run without Docker.

The host will load the enclave, obtain its remote attestation (the `EnclaveInstanceInfo` object), print it out,
and ask the enclave to reverse a string. You should see the following output from the host:

```
Remote attestation for enclave D4FFB9E1539148401529035C202A9904D7562C83B2A95E33E3B639BE8693E87B:
  - Mode: SIMULATION
  - Code signing key hash: 4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
  - Public signing key: 302A300506032B657003210007C159388855F2ECD0B34C36C31F00ED276D144F1DC077D294F3F28F542E98B8
  - Public encryption key: 4D92642BF5C7B93DDB912D809230DE3BFE09531F9095617BF3E90D720F84E151
  - Product ID: 1
  - Revocation level: 0

Assessed security level at 2021-01-26T10:31:02.974Z is INSECURE
  - Enclave is running in simulation mode.

Reversing Hello World!: !dlroW olleH
``` 

The client will connect to the host, download the `EnclaveInstanceInfo`, check it, print it out, and then send an encrypted string to reverse. The host will deliver this encrypted string to the enclave, and the enclave will send back to the client the encrypted reversed response. You should see the following output from the client:

```text
Attempting to connect to localhost:9999
Connected to Remote attestation for enclave D4FFB9E1539148401529035C202A9904D7562C83B2A95E33E3B639BE8693E87B:
  - Mode: SIMULATION
  - Code signing key hash: 4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
  - Public signing key: 302A300506032B657003210007C159388855F2ECD0B34C36C31F00ED276D144F1DC077D294F3F28F542E98B8
  - Public encryption key: 4D92642BF5C7B93DDB912D809230DE3BFE09531F9095617BF3E90D720F84E151
  - Product ID: 1
  - Revocation level: 0

Assessed security level at 2021-01-26T10:31:02.974Z is INSECURE
  - Enclave is running in simulation mode.
Sending the encrypted mail to the host.
Reading reply mail of length 170 bytes.
Enclave reversed 'Reverse me' and gave us the answer 'em esreveR'
```

Finally, the host and the client will exit.

Try this:
=== "Linux / macOS"
    ```bash
    ./gradlew client:run --args="aibohphobia"
    ```
=== "Windows"
    ```bash
    gradlew.bat client:run --args="aibohphobia"
    ```

!!! tip
    Aibohphobia is the fear of palindromes.

If you get stuck please contact [conclave-discuss@groups.io](mailto:conclave-discuss@groups.io) and ask for help!  
