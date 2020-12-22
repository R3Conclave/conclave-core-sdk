# First enclave

!!! important

    * You need the Conclave SDK. If you don't have it please [contact R3 and request a trial](https://www.conclave.net).
    * This tutorial assumes you've read and understood the [conceptual overview](enclaves.md).

You can find a **sample app** in the `hello-world` directory of your SDK. You can use this app as a template 
for your own if you want a quick start. We will cover:

1. How to set up your machine.
2. How to compile and run the sample app.
2. [How to write the sample app](writing-hello-world.md).

## Enclave modes

SGX enclaves can be used in one of four modes, in order of increasing realism:

1. Mock: your enclave class is created in the host JVM and no native or SGX specific code is used. This provides a
   pure Java development experience.
1. Simulation: an enclave is compiled to native machine code and loaded, but SGX hardware doesn't need to be present.
1. Debug: the enclave is loaded using SGX hardware and drivers, but with a back door that allows debugger access to the memory.
1. Release: the enclave is loaded using SGX hardware and drivers, and there's no back door. This is the real deal.

Only release mode locks out the host and provides the standard SGX security model.

## Setting up your machine

For this tutorial you will need Java 8 or 11 (your choice). If you use IntelliJ IDEA the IDE can download both a JDK
and the Gradle build system for you, so you don't need anything to get started except the IDE itself (the free 
Community Edition works fine).

Currently, we support developing enclaves on Windows, macOS and Linux. However, there are a few platform specific
differences to be aware of.

Firstly, although you can develop and work on enclaves in the pure Java mock mode, to test against the embedded JVM
you will need to install Docker. Enclaves are Linux shared libraries with special extensions, and this
requires access to a Linux build environment. A build container will be constructed for you during the Gradle build
process automatically, even on Windows and macOS! However Conclave won't actually install Docker for you. Grab it
from their website, or on Linux, install it via your distribution.

Secondly, *executing* enclaves without using mock mode also requires Linux or a Linux container. 
[Instructions are provided below](#testing-on-windows-and-macos) to show you how to use Docker on Windows and macOS to 
run your entire application in "simulation mode". Alternatively, for day to day development the mock API is plenty 
sufficient and allows you to debug into enclave calls as well. Compiling a real enclave is only needed for integration 
testing against the embedded JVM, or real deployment.

Enclaves can run in simulation mode without requiring any special setup of Linux or SGX capable hardware. However you 
of course get no hardware protections. To run against real SGX hardware you must perform some 
[additional machine setup](machine-setup.md).

## Compiling the sample enclave

**Step 1:** Import the project
 
![Import the project](./images/import.png)

**Step 2:** Look at the Conclave SDK's top level directory

![Look at the SDK's top level directory](./images/import-sdk.png) 
 
**Step 3:** Click "import" when notified that there's a Gradle build script

![Import Gradle script](./images/gradle-import.png) 
 
**Step 4:** If on Linux or Windows, double-click on `:host:assemble`. This is the second 
highlighted `assemble` in the screenshot below. Voila! :smile: You have just built your first enclave.
  
![Double-click on `:host:assemble`](./images/gradle-tasks.png)
  
Now explore the `build` folder. 

![Explore the `build` folder.](./images/build-artifact.png)  

As normal with Gradle, the `assemble` task has bundled the program into a zip, with startup scripts. These scripts are
nothing special - they just set up the classpath. You could also e.g. make a fat JAR if you want. 

### Selecting your mode

In the sample app, the `assemble` task will build the app for simulation mode by default. Use the `-PenclaveMode`
argument to configure this. If you are using SGX hardware, you can build the app for debug mode with the command:

```
./gradlew host:assemble -PenclaveMode=debug
```

If working from inside IntelliJ, start the assemble task for the host project from the tree on the right hand side,
and then edit the created run config. Add the `-PenclaveMode=debug` flag to the arguments section of the run config.

For release mode, the sample app has been configured (in the `build.gradle` of the `enclave` subproject) to use external
signing. This means it must be built in multiple stages:

```bash
// Firstly, build the signing material:
./gradlew prepareForSigning -PenclaveMode=release

// Generate a signature from the signing material. The password for the sample external key is '12345'
openssl dgst -sha256 -out signing/signature.bin -sign signing/external_signing_private.pem -keyform PEM enclave/build/enclave/Release/signing_material.bin

// Finally build the signed enclave:
./gradlew build -PenclaveMode="release"
```

!!! note
    See [Enclave signing](signing.md) for more information.

## Running the host

### Linux

Just run the host app like any app - no special customisation or setup is required with Conclave! Here we will run
a shell script generated by Gradle that starts the JVM:

```bash
./gradlew host:installDist
cd host/build/install
./host/bin/host
```

Gradle can also create `.tar.gz` files suitable for copying to the Linux host, fat JARs, WAR files for deployment into
servlet containers and various other ways to deploy your app. NB: At this time using the JPMS tool `jlink` is not 
tested.

If your Linux machine doesn't have SGX, you should see something like this:

```text
This platform does not support hardware enclaves: SGX_DISABLED_UNSUPPORTED_CPU: SGX is not supported by the CPU in this system
This attestation requires 2163 bytes.
Remote attestation for enclave F86798C4B12BE12073B87C3F57E66BCE7A541EE3D0DDA4FE8853471139C9393F:
  - Mode: SIMULATION
  - Code signing key hash: 01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B
  - Public signing key: 302A300506032B65700321000568034F335BE25386FD405A5997C25F49508AA173E0B413113F9A80C9BBF542
  - Public encryption key: A0227D6D11078AAB73407D76DB9135C0D43A22BEACB0027D166937C18C5A7973
  - Product ID: 1
  - Revocation level: 0

Assessed security level at 2020-07-17T16:31:51.894697Z is INSECURE
  - Enclave is running in simulation mode.

Reversing Hello World!: !dlrow olleH

Listening on port 9999. Use the client app to send strings for reversal.
``` 

### macOS

On macOS there is a script that lets you run Gradle (and by extension anything it runs) inside a Linux environment based 
on the Conclave build container. It wraps Docker and makes it simpler to work with.

Just use the `../scripts/container-gradle` script as a replacement for `gradlew`. You might want to add it to your
`$PATH` variable.

For more information see the [Container Gradle](container-gradle.md) page.

### Windows

On Windows you can still test locally in simulation mode using a Docker container. However you may need to configure
mounts and other parameters yourself. Look at the `scripts/container-gradle` file to see how this is done on macOS.

___Windows PowerShell___
```
gradlew.bat host:installDist
docker run -it --rm -p 9999:9999 -v ${PWD}:/project -w /project conclave-build /bin/bash
```

This will give you a shell inside a Linux virtual machine. Having built the app outside the container you can then run:

```
cd host/build/install
./host/bin/host
```

Please consult the [Docker reference manual](https://docs.docker.com/engine/reference/commandline/run/) to understand 
what switches you can pass to this command.
 
### Within IntelliJ

You may want to create an IntelliJ launch configuration to incorporate the `build` and `deploy` stages. If using the
`container-gradle` script on macOS, IntelliJ does expect the command to launch Gradle to be called `gradle` or `gradlew`. 
What you can do is rename `gradlew` to something else, then copy the `scripts/container-gradle` script to be called 
`gradlew` in your project root. Finally, edit the last line of the script to start the renamed script. Therefore 
IntelliJ will run the `container-gradle` script whilst  thinking it's running normal Gradle.

## Running the client

The host has loaded the enclave, obtained its remote attestation (the `EnclaveInstanceInfo` object), printed it out,
asked the enclave to reverse a string and finally opened up a TCP port which will now listen for requests from remote
clients.

So, let's run the client app:

```bash
./gradlew client:run --args="reverse me!"
```

The client will connect to the host, download the `EnclaveInstanceInfo`, check it, and then send an encrypted string
to reverse. The host will deliver this encrypted string to the enclave, and the enclave will send back to the client
the encrypted reversed response:

```text
Reading a remote attestation of length 2163 bytes.
Sending the encrypted mail to the host.
Reading reply mail of length 196 bytes.
Enclave reversed 'reverse me!' and gave us the answer '!em esrever'
```

Try this:

```bash
./gradlew client:run --args="aibohphobia"
```

!!! tip
    Aibohphobia is the fear of palindromes.

If you get stuck please contact [conclave-discuss@groups.io](mailto:conclave-discuss@groups.io) and ask for help!  
