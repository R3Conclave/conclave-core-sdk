# Writing the sample enclave

The sample "hello world" enclave just reverses whatever string is passed into it. We'll do these things to make 
our own version of the hello enclave project:

1. Configure Gradle. At this time Conclave projects must use Gradle as their build system.
1. Implement an enclave object that accepts both local calls from the host, and encrypted messages from a client.
1. Write the host program that loads the enclave.
1. Run the host and enclave in simulation and debug modes.
1. Write the client that sends the enclave encrypted messages via the host.

## Configure your modules

Create a new Gradle project via whatever mechanism you prefer, e.g. IntelliJ can do this via the New Project wizard.
Create three modules defined in the project: one for the host, one for the enclave and one for the client. 

The host program may be an existing server program of some kind, e.g. a web server, but in this tutorial we'll 
write a command line host. The client may likewise be a GUI app or integrated with some other program (like a server), 
but in this case to keep it simple the client will also be a command line app.    
    
### Root `settings.gradle` file

In the unzipped SDK there is a directory called `repo` that contains a local Maven repository. This is where the libraries
and Gradle plugin can be found. We need to tell Gradle to look there for plugins.

Create or modify a file called `settings.gradle` in your project root directory so it looks like this:

```groovy
pluginManagement {
    repositories {
        maven {
            def repoPath = file(rootDir.relativePath(file(conclaveRepo)))
            if (repoPath == null)
                throw new Exception("Make sure the 'conclaveRepo' setting exists in gradle.properties, or your \$HOME/gradle.properties file. See the Conclave tutorial on https://docs.conclave.net")
            else if (!new File(repoPath, "com").isDirectory())
                throw new Exception("The $repoPath directory doesn't seem to exist or isn't a Maven repository; it should be the SDK 'repo' subdirectory. See the Conclave tutorial on https://docs.conclave.net")
            url = repoPath
        }
        // Add standard repositories back.
        gradlePluginPortal()
        jcenter()
        mavenCentral()
    }

    plugins {
        id 'com.r3.conclave.enclave' version conclaveVersion apply false
    }
}

include 'enclave'
include 'host'
include 'client'
```

This boilerplate is unfortunately necessary to copy/paste into each project that uses Conclave. It sets up Gradle to
locate the plugin that configures the rest of the boilerplate build logic for you :wink:
    
The `pluginManagement` block tells Gradle to use a property called `conclaveRepo` to find the `repo` directory
in your SDK download. Because developers on your team could unpack the SDK anywhere, they must configure the path
before the build will work. The code above will print a helpful error if they forget or get it wrong.

To set the value, add a couple of lines to
[the `gradle.properties` file](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#declare_properties_in_gradle_properties_file)
like this:

```text
conclaveRepo=/path/to/sdk/repo
conclaveVersion=1.0
```

Gradle properties can be set using a file in the project directory, or more usefully in the developer's home directory.
You may wish to put the version in the project's `gradle.properties` file and the path in each developer's personal
`gradle.properties`. Alternatively just add a `sdk` directory to the `.gitignore` and require everyone to unpack the
SDK to the source tree. 
    
### Root `build.gradle` file
    
Add the following code to your root `build.gradle` file to import the repository:

```groovy
subprojects {
    repositories {
        maven {
            url = rootProject.file(conclaveRepo)
        }
        mavenCentral()
    }
}
```

### IDE Documentation in the root `build.gradle` file
Some IDEs are able to automatically display Conclave SDK documentation whilst editing code. In order for this to
work you may need to add some configuration to the root `build.gradle` depending on your IDE.

Start by adding the Gradle plugin required to support your IDE. Note that Visual Studio Code shares the configuration
provided by the `eclipse` plugin.

```groovy hl_lines="3 4"
plugins {
    id 'java'
    id 'idea'
    id 'eclipse'
}

```

Then add sections to tell the IDEs to download Javadoc for dependencies.

```groovy
eclipse {
    classpath {
        downloadJavadoc = true
    }
}

idea {
    module {
        downloadJavadoc = true
    }
}
```

Finally apply the same configuration to all subprojects.

```groovy hl_lines="2-13"
subprojects {
    apply plugin: 'idea'
    apply plugin: 'eclipse'
    idea {
        module {
            downloadJavadoc = true
        }
    }
    eclipse {
        classpath {
            downloadJavadoc = true
        }
    }

    repositories {
```

!!! info
    At the moment, IntelliJ IDEA has an issue that means it does not correctly display the documentation for Conclave, 
    even if you provide this configuration. Instead, please refer to the [online Javadocs](https://docs.conclave.net/api/index.html)
    for Conclave.


### Configure the _host_ module
Add this bit of code to your host `build.gradle` file to let the [mode](tutorial.md#enclave-modes) be chosen from the command line:

```groovy
// Override the default (simulation) with -PenclaveMode=
def mode = findProperty("enclaveMode")?.toString()?.toLowerCase() ?: "simulation"
```

We can apply the Gradle `application` plugin and set the `mainClassName` property
[in the usual manner](https://docs.gradle.org/current/userguide/application_plugin.html#application_plugin) to let us run
the host from the command line:

```groovy hl_lines="3 6-8"
plugins {
    id 'java'
    id 'application'
}

application {
    mainClassName = "com.superfirm.host.Host" // CHANGE THIS 
}

```

Then add the following dependencies, also to the host's `build.gradle`:

```groovy hl_lines="2 3"
dependencies {
    implementation "com.r3.conclave:conclave-host:$conclaveVersion"
    runtimeOnly project(path: ":enclave", configuration: mode)

    runtimeOnly "org.slf4j:slf4j-simple:1.7.30"
    testImplementation "org.junit.jupiter:junit-jupiter:5.6.0"
}
```

This says that at runtime (but not compile time) the `:enclave` module must be on the classpath, and configures 
dependencies to respect the three different variants of the enclave. That is, the enclave module will expose tasks
to compile and use either mock, simulation, debug or release mode. Which task to use is actually selected by the host build. 

!!! tip
    Don't worry if you see the error `Could not resolve project :enclave`. This will be resolved when
    we configure the enclave module in the next section.

For this simple tutorial we also add a runtime-only dependency on the popular [SLF4J](https://www.slf4j.org) library 
which Conclave uses to do logging. SLF4J enables you to send Conclave's logging to any of the major logging frameworks 
used in Java, but here, we add the "simple" backend which just causes it to log to the console. Finally we configure 
unit testing using JUnit 5.

If you intend to use an [external signing process](signing.md) to sign your enclave then add the following lines to
the Gradle file:

```groovy
// Create a task that can be used for generating signing materials
tasks.register("prepareForSigning") {
    it.dependsOn(":enclave:generateEnclaveSigningMaterial" + mode.capitalize())
}
```

This creates a new task that can be invoked using Gradle to halt the build after generating materials that need to
be signed by an external signing process. After the material has been signed the build can be resumed.

### Configure the _enclave_ module

Add the Conclave Gradle plugin to your enclave `build.gradle` file:

```groovy hl_lines="2"
plugins {
    id 'com.r3.conclave.enclave'
}
```

and a dependency on the Conclave enclave library and a test dependency on the Conclave host library:

```groovy hl_lines="2-3"
dependencies {
    implementation "com.r3.conclave:conclave-enclave"
    testImplementation "com.r3.conclave:conclave-host"
    testImplementation "org.junit.jupiter:junit-jupiter:5.6.0"
}
```

This time you don't have to specify the Conclave version because the plugin will set that for you automatically.

Specify the enclave's runtime environment, product ID and revocation level:

```groovy
conclave {
    productID = 1
    revocationLevel = 0
}
```

These settings are described in detail in the page on [enclave configuration](enclave-configuration.md). A summary
of these settings follows:

Conclave needs access to a Linux build environment in order to build enclaves. 
On MacOS and Windows this is automatically created during the build process using Docker. If you do not have Docker 
installed then the build will generate an error prompting you to install Docker on your system. Once Docker is installed 
and added to your `PATH` environment variable you can proceed to build Simulation, Debug or Release mode enclaves.
Docker is not required if you are using a Linux system.

The **product ID** is an arbitrary number that can be used to distinguish between different enclaves produced by the same
organisation (which may for internal reasons wish to use a single signing key). This value should not change once you
have picked it.

The **revocation level** should be incremented if a weakness in the enclave code is discovered and fixed; doing this will
enable clients to avoid connecting to old, compromised enclaves. The revocation level should not be incremented on every
new release, but only when security improvements have been made.

#### Signing keys
Specify the signing methods for each of the build types. You could keep your private key in a file for both debug and
release enclaves if you like, but some organisations require private keys to be held in an offline system or HSM. In
that case, configure it like this:

```groovy hl_lines="5-27"
conclave {
    productID = 1
    revocationLevel = 0

    // For simulation, we want to use the default signing type of dummyKey so
    // we do not need to specify a configuration.

    debug {
        signingType = privateKey
        signingKey = file("../signing/sample_private_key.pem")
    }

    release {
        // To protect our release private key with an HSM, the enclave needs to be built in stages.
        // Firstly, build the signing material:
        //  ./gradlew prepareForSigning -PenclaveMode="Release"
        //
        // Generate a signature from the signing material.
        //
        // Finally build the signed enclave:
        //  ./gradlew build -PenclaveMode="Release"
        //
        signingType = externalKey
        signatureDate = new Date(1970, 0, 1)
        mrsignerSignature = file("../signing/signature.bin")
        mrsignerPublicKey = file("../signing/external_signing_public.pem")
    }
}
```

The example configuration above specifies different [signing configurations](signing.md#signing-configurations) for 
each of the different build types. 

 * Simulation builds use the default dummy key. 
 * Debug builds use a private key stored in a file.
 * Release builds use a private key managed by some external signing process.

The ```hello-world``` sample in the SDK contains some example keys that can be used with the ```privateKey```
and ```externalKey``` signing types. These can be found in ```hello-world/signing/```.

| Key Files | Description |
| :----     | :----       |
| sample_private_key.pem | A 3072 bit RSA private key that can be used to test the ```privateKey``` signing type |
| external_signing_*.pem | An AES encrypted 3072 bit RSA public/private key pair that can be used to test the ```externalKey``` signing type. The private key can be accessed with the password '12345' |

Copy the `signing` directory from the SDK into your project and/or update the paths in the enclave `build.gradle`.
Alternatively you can provide or [generate your own](signing.md#generating-keys-for-signing-an-enclave) keys.

!!! important
    These keys aren't whitelisted by Intel so you can't use them for real release builds.
    Only use these sample keys for the tutorial. Don't use them for signing your own enclaves!

### Configure the _client_ module

The client module is the simplest of all. This is literally a bog-standard hello world command line app Gradle build,
with a single dependency on the Conclave client library:

```groovy hl_lines="3 6-12"
plugins {
    id 'java'
    id 'application'
}

application {
    mainClassName = "com.superfirm.client.Client" // CHANGE THIS
}

dependencies {
    implementation "com.r3.conclave:conclave-client:$conclaveVersion"
}
```

And with that, we're done configuring the module.

## Create a new subclass of `Enclave`

Enclaves are similar to standalone programs and as such have an equivalent to a "main class". This class must be a
subclass of [`Enclave`](/api/com/r3/conclave/enclave/Enclave.html).

Create your enclave class:

```java
package com.superfirm.enclave  // CHANGE THIS

import com.r3.conclave.enclave.Enclave;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave {
    @Override
    public byte[] receiveFromUntrustedHost(byte[] bytes) {
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            result[i] = bytes[bytes.length - 1 - i];
        return result;
    }
}
```

The `Enclave` class by itself doesn't require you to support direct communication with the host. This is because
sometimes you don't need that and shouldn't have to implement message handlers. In this case we'll use that
functionality because it's a good place to start learning, so we also override and implement the `receiveFromUntrustedHost`
method which takes a byte array and optionally returns a byte array back. Here we just reverse the contents.

!!! tip
    In a real app you would use the byte array to hold serialised data structures. You can use whatever data formats you
    like. You could use a simple string format or a binary format like protocol buffers.

### Threading

In this tutorial we won't write a multi-threaded enclave. If you want to do this, you'll need to override the 
`boolean isThreadSafe()` method in the `Enclave` class (use `override val threadSafe: Boolean get() = true` in Kotlin).
This tells Conclave to allow multiple threads into the enclave simultaneously. You're required to opt-in to allowing
multi-threading to avoid accidents when someone writes a simple enclave that isn't thread safe, and forgets that the host
is malicious and can enter your code with multiple threads simultaneously even if you aren't ready for it, corrupting
your application level data via race conditions. By blocking multi-threading until you indicate readiness, the hope 
is that some types of attack can be avoided. See the page on [enclave threading](threads.md) to learn more.

## Write a simple host program

An enclave by itself is just a library: you must therefore load it from inside a host program.

It's easy to load then pass data to and from an enclave. Let's start with the skeleton of a little command line app:

```java
package com.superfirm.host // CHANGE THIS

import com.r3.conclave.host.EnclaveHost;
import com.r3.conclave.host.EnclaveLoadException;

/**
 * This class demonstrates how to load an enclave and use it.
 */
public class Host {
    public static void main(String[] args) throws EnclaveLoadException {
        // TODO: Fill this out
    }

    public static String callEnclave(EnclaveHost enclave, String input) {
        // TODO: Fill this out.
    }
}
```

At first we will be building and running our enclave in simulation mode. This does not require the platform 
hardware to support SGX. However simulation mode does require us to be using Linux. If we are not using 
Linux as our host OS then we can use a Linux container or virtual machine as described in
[Running the host](tutorial.md#running-the-host). Alternatively we could use [mock mode](mockmode.md)
instead of simulation mode. When we want to switch to loading either a debug or release build of the enclave we need
to ensure the platform supports SGX.

By adding the code below to the main method we can determine whether the platform can load debug and release 
enclaves. This method reports the actual hardware status even if you are currently working with mock or simulation enclaves.

```java hl_lines="2-7"
public static void main(String[] args) throws EnclaveLoadException {
    try {
        EnclaveHost.checkPlatformSupportsEnclaves(true);
        System.out.println("This platform supports enclaves in simulation, debug and release mode.");
    } catch (EnclaveLoadException e) {
        System.out.println("This platform does not support hardware enclaves: " + e.getMessage());
    }
}
```

If SGX is not supported the function throws an exception which describes the reason why. There are a number of 
common reasons why SGX may not be supported including:

1. The CPU or the system BIOS does not support SGX.
2. The host operating system is Windows or Mac OS. Conclave currently only supports loading
   enclaves in simulation, debug or release modes on Linux.
3. SGX is disabled in the BIOS and must be manually enabled by the user.
4. SGX is disabled but can be enabled in software.

If SGX is disabled but can be enabled in software the code below attempts to automatically enable SGX support 
by specifying the 'true' parameter. It might be necessary to run this application with root access and/or reboot 
the system in order to successfully enable SGX. The exception message will describe if this is the case.

To load the enclave we'll put this after the platform check:

```java
String className = "com.superfirm.enclave.ReverseEnclave" // CHANGE THIS
try (EnclaveHost enclave = EnclaveHost.load(className)) {
    enclave.start(null, null);

    System.out.println(callEnclave(enclave, "Hello world!"));
    // !dlrow olleH      :-)

    // TODO: Get the remote attestation
}
```

This code starts by creating an [`EnclaveHost`](api/com/r3/conclave/host/EnclaveHost.html) object. This names the 
class and then attempts to load it inside another JVM running inside an enclave. A [remote attestation](enclaves.md#remote-attestation) procedure is
then performed involving Intel's servers. This procedure can fail if attempting to load a debug or release enclave 
and the platform does not support SGX. This is why it is important to perform the platform check we made in the code 
above. If the enclave does fail to load for any reason then an exception is thrown describing the reason why.

!!! tip
    You can use the command `EnclaveHost.getCapabilitiesDiagnostics()` to print out some diagnostic information about the CPU, which can be helpful for troubleshooting.

We then call `start` which initialises the enclave and the `MyEnclave` class inside it.
You can load multiple enclaves at once but they must all use same mode, and each enclave will get its own isolated
JVM.

Note that an `EnclaveHost` allocates memory out of a pool called the "enclave page cache" which is a machine-wide
limited resource. It'll be freed if the host JVM quits, but it's good practice to close the `EnclaveHost` object by
calling `close` on it when done. Therefore we also make sure the `.close()` method is called on the enclave no
matter what using a try-with-resources statement. This doesn't actually matter in such a tiny hello world sample,
because the enclave will be unloaded by the kernel once we exit like any other resource. It's just here to remind
you that an enclave must be explicitly unloaded if you need to reinitialise it for whatever reason, or if you need
the memory back.

!!! warning
    Starting and stopping/closing an enclave is not free, so **don't** load the enclave, use it and immediately close it 
    again as in the above example. Cost-wise it's like starting a regular process even though no process will actually 
    exist. Treat the enclave like any other expensive resource and keep it around for as long as you might need it.

Once we started the enclave, we call it passing in a string as bytes. The enclave will reverse it and we'll print out
the answer. This is as easy as calling `EnclaveHost.callEnclave`, so put this in the `callEnclave` static method
defined above:

```java
// We'll convert strings to bytes and back.
return new String(enclave.callEnclave(input.getBytes()));
```

So we just convert the string to bytes, send it to the enclave, and convert the response from bytes back to a string.

## Remote attestation

There's no point in using an enclave to protect purely local data, as the data must ultimately come from the
(assumed malicious/compromised) host in that scenario. That's why you need remote attestation, which lets an enclave 
prove its identity to the third parties who will upload secret data. If this paragraph doesn't make
sense please review the [Architecture overview](architecture.md) and the [Enclaves](enclaves.md) section.

Before we can set up communication with a client, we must therefore get remote attestation working.
    
Using remote attestation is easy! Just obtain an `EnclaveInstanceInfo` and serialize/deserialize it using the
provided methods. Add these lines to the end of the `main` function of your `Host` class:
    
```java
final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
final byte[] attestationBytes = attestation.serialize();
System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));
```

The `EnclaveInstanceInfo` has a useful `toString` function that will print out something like this:

```text
Remote attestation for enclave F86798C4B12BE12073B87C3F57E66BCE7A541EE3D0DDA4FE8853471139C9393F:
  - Mode: SIMULATION
  - Code signing key hash: 01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B
  - Public signing key: 302A300506032B65700321000568034F335BE25386FD405A5997C25F49508AA173E0B413113F9A80C9BBF542
  - Public encryption key: A0227D6D11078AAB73407D76DB9135C0D43A22BEACB0027D166937C18C5A7973
  - Product ID: 1
  - Revocation level: 0

Assessed security level at 2020-07-17T16:31:51.894697Z is INSECURE
  - Enclave is running in simulation mode.
```

The hash in the first line is the *measurement*. This is a hash of the code of the enclave. It includes both all
the Java code inside the enclave as a fat-JAR, and all the support and JVM runtime code required. As such it will
change any time you alter the code of your enclave, the version of Conclave in use or the mode
(simulation/debug/release) of the enclave. The enclave measurement should be stable across builds and machines, so
clients can audit the enclave by repeating the Gradle build and comparing the value they get in the
`EnclaveInstanceInfo` against what the build process prints out.

!!! tip
    1. All this data is available via individual getters on the `EnclaveInstanceInfo` so you should never feel a need to
       parse the output of `toString`.
    2. `EnclaveInstanceInfo` is an interface so you can easily build mock attestations in your tests.
    3. When not in simulation mode the timestamp is signed by Intel and comes from their servers. 

An instance has a security assessment, which can change in response to discovery of vulnerabilities in the
infrastructure (i.e. without anything changing about the host or enclave itself). As we can see this enclave isn't
actually considered secure yet because we're running in simulation mode still. An enclave can be `SECURE`, `STALE`, 
or `INSECURE`. A assessment of `STALE` means there is a software/firmware/microcode update available for the platform
that improves security in some way. The client may wish to observe when this starts being reported and define a 
time span in which the remote enclave operator must upgrade.

We can send the serialized bytes to a client via whatever network mechanism we want. The bytes are essentially a large,
complex digital signature, so it's safe to publish them publicly. For simplicity in this tutorial we are just going to
copy them manually and hard-code them in the client, but more on that [later](#writing-the-client).

An attestation doesn't inherently expire but because the SGX ecosystem is always moving, client code will typically have
some frequency with which it expects the host code to refresh the `EnclaveInstanceInfo`. At present this is done by
stopping/closing and then restarting the enclave.

## Configurating attestation

To use SGX remote attestation for real we need to do some additional work. Remember how we wrote 
`enclave.start(null, null);` above? The first parameter contains configuration data required to use an attestation
service. There are three kinds of attestation service:

1. EPID. This older protocol is supported by some desktop/laptop class Intel CPUs. The EPID protocol includes some
   consumer privacy cryptography, and involves talking directly to Intel's IAS service to generate an attestation.
   For that you need to obtain an API key and service provider ID from Intel. You can sign-up easily and for free. 
   [Learn more about IAS](ias.md). Please note that Intel does not provide EPID attestation support for Xeon scalable CPUs
   including Ice Lake and future generations. You need to use DCAP attestation on these platforms.
2. Azure DCAP. The _datacenter attestation primitives_ protocol is newer and designed for servers. When running on a
   Microsoft Azure Confidential Compute VM or Kubernetes pod, you don't need any parameters. It's all configured out of 
   the box.
3. Generic DCAP. When not running on Azure, you need to obtain API keys for Intel's PCCS service. 

We'll target Azure for now to keep things simple. Replace the call to `EnclaveHost.start` above with this snippet:

```java
enclave.start(new AttestationParameters.DCAP(), null);
```

!!! info
    Why does Conclave need to contact Intel's servers? It's because those servers contain the most up to date information
    about what CPUs and system enclave versions are considered secure. Over time these servers will change their 
    assessment of the host system and this will be visible in the responses, as security improvements are made.  

## Run what we've got so far

Now everything should be ready to run the host from the command line.

Run `gradlew host:run` and it should print "Hello World!" backwards along with the security info as shown above.

!!! note
    If you are using Windows or macOS then please follow the instructions for your operating system, which you can find in the [Running the host](tutorial.md#running-the-host) section.

During the build you should see output like this:

```text
> Task :enclave:generateEnclaveMetadataSimulation
Succeed.
Enclave code hash:   61AE6A28838CE9EFBE16A7078F9A506D69BBA70B69FAD229F1FBDB45AA786109
Enclave code signer: 4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
```

The code hash will correspond to the value found in the `EnclaveInstanceInfo.getEnclaveInfo().getCodeHash()` property
and the code signer will be `EnclaveInstanceInfo.getEnclaveInfo().getCodeSigningKeyHash()`.

!!! tip
    Make a note of the value of `Enclave code signer`. We will need it [later on](#constraints) to verify the enclave's identity from the client.

You can switch to debug mode by specifying the `enclaveMode` property. In debug mode the real hardware is used and 
virtually everything is identical to how it will be in production, but there's a small back door that can be used 
by debuggers to read/write the enclave's memory. 

You will need to run this on an [Azure Confidential VM](https://docs.microsoft.com/en-us/azure/confidential-computing/).

`gradlew -PenclaveMode=debug host:run`

## Encrypted messaging

The enclave isn't of much use to the host, because the host already trusts itself. It's only useful to remote clients
that want to use the enclave for computation without having to trust the host machine or software.

We're now going to wire up encrypted messaging. Conclave provides an API for this called Mail. Conclave Mail handles all the
encryption for you, but leaves it up to you how the bytes themselves are moved around. You could use a REST API, a gRPC
API, JMS message queues, a simple TCP socket as in this tutorial, or even files.

!!! info
    [Learn more about the design of Conclave Mail](architecture.md#mail), and [compare it to TLS](mail.md).

### Receiving and posting mail in the enclave

Firstly we need to upgrade our little enclave to be able to receive mails from clients. This is easy! Just override
the `receiveMail` method:

```java hl_lines="14-19"
/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave {
    @Override
    public byte[] receiveFromUntrustedHost(byte[] bytes) {
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[bytes.length - 1 - i];
        }
        return result;
    }

    @Override
    protected void receiveMail(long id, EnclaveMail mail, String routingHint) {
        byte[] reversed = receiveFromUntrustedHost(mail.getBodyAsBytes());
        byte[] responseBytes = postOffice(mail).encryptMail(reversed);
        postMail(responseBytes, routingHint);
    }
}
```

The `receiveFromUntrustedHost` method here isn't really needed, it's just because we're already using this to demonstrate local calls.
The new part is `receiveMail`. This method takes three parameters: the first is an identifier that the host gets to pick, which doesn't
mean anything but we can use to acknowledge the mail if we want to using `Enclave.acknowledgeMail`. Acknowledgement can be used
to tell the host the enclave is done processing the mail if it doesn't want to reply immediately. It will be discussed
more in future tutorials. In this simple tutorial we reply immediately so don't need to use this feature, and thus we
ignore the ID.

The third parameter is a routing hint string. It's also provided by the host and it helps the host route replies when
dealing with multiple clients. It's passed into `postMail` when the enclave posts a reply. In our example the host only
deals with one client and so it's not used.

#### Mail headers

The second parameter is an `EnclaveMail`. This object gives us access to the body bytes that the client sent, but it
also exposes some other header fields:

1. A _topic_. This can be used to distinguish between different streams of mail from the same client. It's a string and
   can be thought of as equivalent to an email subject. Topics are scoped per-sender and are not global. The
   client can send multiple streams of related mail by using a different topic for each stream, and it can do this
   concurrently. The topic is not parsed by Conclave and, to avoid replay attacks, should never be reused for an unrelated set of mails in the future. A good
   value might thus contain a random UUID. Topics may be logged and used by your software to route or split mail streams
   in useful ways.
1. The _sequence number_. Starting from zero, this is incremented by one for every mail delivered on a topic. Conclave will
   automatically reject messages if this doesn't hold true, thus ensuring to the client that the stream of related
   mail is received by the enclave in the order they were sent, and that the host is unable to re-order or drop them.
1. The _envelope_. This is a slot that can hold any arbitrary byte array the sender likes. It's a holding zone for 
   app specific data that should be authenticated but unencrypted.

**These header fields are available to the host and therefore should not contain secrets**. It may seem odd to have
data that's unencrypted, but it's often useful for the client, host and enclave to collaborate in various ways related
to storage and routing of data. Even when the host is untrusted it may still be useful for the client to send data
that is readable by the host and enclave simultaneously, but which the host cannot tamper with. Inside the enclave
you can be assured that the header fields contain the values set by the client, because they're checked before 
`receiveMail` is invoked.

In addition to the headers there is also the _authenticated sender public key_. This is the public key of the client that
sent the mail. Like the body it's encrypted so that the host cannot learn the client identities. It's called "authenticated"
because the encryption used by Conclave means you can trust that the mail was encrypted by an entity holding the private
key matching this public key. If your enclave recognises the public key this feature can be used as a form of user authentication.

In this simple tutorial we only care about the body. We reverse the bytes in the mail body and then create a response
mail that will be encrypted to the sender. It contains the reversed bytes. We use the `postOffice` method to do this. It
gives us back a post office object, which is a factory for creating encrypted mail. Because we want to create a
response, we pass in the original mail to `postOffice` and it will give us an instance which is configured to encrypt mail
back to the sender. It will also use the same topic as the original mail. `encryptMail` will encrypt the reversed bytes
and add on the authenticated header. The resulting mail bytes are passed to `postMail` which delivers it to the host.
It will emerge in a callback we're about to configure.

!!! tip
    You can post mail anytime and to anyone you like. It doesn't have to be a response to the sender, you can post
    multiple mails at once and you can post mails inside `receiveFromUntrustedHost` (i.e. during a local call).

### Receiving and posting mail in the host

Mail posted by an enclave appears in a callback we pass to `EnclaveHost.start`. Let's use a really simple 
implementation: we'll just store the encrypted bytes in a variable, so we can pick it up later.

Replace the call to `EnclaveHost.start` in the `main` function of your `Host` class with this snippet:

```java
// Start it up.
AtomicReference<byte[]> mailToSend = new AtomicReference<>();
enclave.start(new AttestationParameters.DCAP(), (commands) -> {
    for (MailCommand command : commands) {
        if (command instanceof MailCommand.PostMail) {
            mailToSend.set(((MailCommand.PostMail) command).getEncryptedBytes());
        }
    }
});
``` 

Java doesn't let us directly change variables from a callback, so we use an `AtomicReference` here as a box.

!!! tip
    Kotlin lets you alter mutable variables from callbacks directly, without needing this sort of trick.

The callback is a list of `MailCommand` objects, and what we're interested in are requests for delivery which are
represented as `MailCommand.PostMail` objects. They contain the encrypted mail bytes to send. More information about the
mail commands can be found [below](#mail-commands).

The enclave can provide a _routing hint_ to tell the host where it'd like the message delivered.
It's called a "hint" because the enclave must always remember that
the host is untrusted. It can be arbitrarily malicious and could, for example, not deliver the mail at all, or
it could deliver it to the wrong place. However if it does deliver it wrongly, the encryption will ensure the
bogus recipient can't do anything with the mail. In this simple hello world tutorial we can only handle one client
at once so we're going to ignore the routing hint here. In a more sophisticated server your callback implementation can
have access to your connected clients, a database, a durable queue, a `ThreadLocal` containing a servlet connection and so on. 

At the bottom of our `main` method let's add some code to accept TCP connections and send the `EnclaveInstanceInfo` to 
whomever connects. You will also need to add `throws IOException` to the method signature of `main`. Then we'll accept a
mail uploaded by the client, send it to the enclave, and deliver the response back. We'll write the client code in a moment.

```java
int port = 9999;
System.out.println("Listening on port " + port + ". Use the client app to send strings for reversal.");
ServerSocket acceptor = new ServerSocket(port);
Socket connection = acceptor.accept();

// Just send the attestation straight to whoever connects. It's signed so that's MITM-safe.
DataOutputStream output = new DataOutputStream(connection.getOutputStream());
output.writeInt(attestationBytes.length);
output.write(attestationBytes);

// Now read some mail from the client.
DataInputStream input = new DataInputStream(connection.getInputStream());
byte[] mailBytes = new byte[input.readInt()];
input.readFully(mailBytes);

// Deliver it. The enclave will give us some mail to reply with via the callback we passed in
// to the start() method.
enclave.deliverMail(1, mailBytes, "routingHint");
byte[] toSend = mailToSend.getAndSet(null);
output.writeInt(toSend.length);
output.write(toSend);
```

This code is straightforward. In order, it:

1. Opens a socket using the Java sockets API and listens for a connection.
1. Accepts a connection and then sends the serialized `EnclaveInstanceInfo` to the client. We first send the length
   so the client knows how many bytes to read.
1. The client will send us a byte array back, which contains an encrypted string. This code can't read the body, it's 
   just encrypted bytes except for a few fields in the headers, which *are* available to the host.
1. We deliver the encrypted mail bytes to the enclave.
1. We pick up the response from the `AtomicReference` box that was set by the callback.

The first parameter to `deliverMail` is a "mail ID" that the enclave can use to
identify this mail to the host. This feature is intended for use with acknowledgement, which allows the enclave to
signal that it's done with that message and the work it represents can be atomically/transactionally completed.
The *routing hint* is an arbitrary string that can be used to identify the sender of the mail from the host's
perspective, e.g. a connection ID, username, identity - it's up to you. The enclave can use this string to 
signal to the host that a mail should go to that location. It's called a "hint" to remind you that the host code may
be modified or written by an attacker, so the enclave can't trust it. However, the encryption on the mail makes it 
useless for the host to mis-direct mail.

!!! todo
    In future we will provide APIs to bind enclaves to common transports, to avoid this sort of boilerplate.

### Mail commands

The second parameter to `EnclaveHost.start` is a callback which returns a list of `MailCommand` objects from the enclave.
There are two commands the host can receive:

1. **Post mail**. This is when the enclave wants to send mail over the network to a client. The enclave may provide a
   routing hint with the mail to help the host route the message. The host is also expected to safely store the message
   in case the enclave is restarted. If that happens then it needs to redeliver all the (unacknowledged) mail back to
   the enclave in order.
1. **Acknowledge mail**. This is when the enclave no longer needs the mail to be redelivered to it on restart and the host
   is thus expected to delete it from its store. There are many reasons why an enclave may not want a message redelivered.
   For example, the conversation with the client has reached its end and so it acknowledges all the mail in that thread;
   or the enclave can checkpoint in the middle by creating a mail to itself which condenses all the previous mail, which
   are then all acknowledged.

The host receives these commands grouped together within the scope of a single `EnclaveHost.deliverMail` or `EnclaveHost.callEnclave`
call. This allows the host to add transactionality when processing the commands. So for example, the delivery of the mail
from the client to the enclave and the subsequent reply back can be processed atomically within the same database transaction
when the host is providing persistent, durable messaging. Likewise the acknowledgement of any mail can occur within the
same transaction.

## Writing the client

The client app will do three things:

1. Connect to the host server and download the `EnclaveInstanceInfo` from it.
1. Verify the enclave is acceptable: i.e. that it will do what's expected.
1. Send it the command line arguments as a string to reverse and get back the answer, using encrypted mail.

Here's the initial boilerplate to grab the user input, connect, download and deserialize the `EnclaveInstanceInfo`.

```java
import com.r3.conclave.client.InvalidEnclaveException;
import com.r3.conclave.common.EnclaveInstanceInfo;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class Client {
    public static void main(String[] args) throws IOException, InvalidEnclaveException {
        if (args.length == 0) {
            System.err.println("Please pass the string to reverse on the command line");
            return;
        }
        String toReverse = String.join(" ", args);

        // Connect to the host, it will send us a remote attestation (EnclaveInstanceInfo).
        Socket socket = new Socket("localhost", 9999);
        DataInputStream fromHost = new DataInputStream(socket.getInputStream());
        byte[] attestationBytes = new byte[fromHost.readInt()];
        fromHost.readFully(attestationBytes);
        EnclaveInstanceInfo attestation = EnclaveInstanceInfo.deserialize(attestationBytes);
    }
}
```

### Constraints

How do you know the `EnclaveInstanceInfo` you've got is for the enclave you really intend to interact with? In normal
client/server programming you connect to a host using some sort of identity, like a domain name or IP address. TLS 
is used to ensure the server that picks up is the rightful owner of the domain name you intended to connect to. In
enclave programming the location of the enclave might not matter much because the host is untrusted. Instead you have
to verify *what* is running, rather than *where* it's running.

!!! note
    The domain name of the server can still be important in some applications, in which case you should use TLS instead
    of raw sockets as is the case here.

One way to do this is by inspecting the properties on the `EnclaveInstanceInfo` object and hard-coding some logic. That
works fine, but testing an `EnclaveInstanceInfo` is a common pattern in enclave programming, so we provide an API to 
do it for you.

The [`EnclaveConstraint`](/api/com/r3/conclave/client/EnclaveConstraint.html) class takes an `EnclaveInstanceInfo` and
performs some matching against it. A constraint object can be built in code, or it can be loaded from a small domain
specific language encoded as a one-line string. The string form is helpful if you anticipate frequent upgrades that
should be whitelisted or other frequent changes to the acceptable enclave, as it can be easily put into a
configuration file, JSON, XML or command line flags.

The constraint lets you specify:

1. Acceptable code hashes (measurements)
2. Acceptable signing public keys
3. The minimum revocation level 
4. The product ID
5. The security level of the instance: `SECURE`, `STALE`, `INSECURE`

If you specify a signing public key then you must also specify the product ID, otherwise if the organisation that
created the enclave makes a second different kind of enclave in future, a malicious host might connect you with the
wrong one. If the input/output commands are similar then a confusion attack could be opened up. That's why you must
always specify the product ID even if it's zero.

The simplest possible string-form constraint looks like this:

`C:F86798C4B12BE12073B87C3F57E66BCE7A541EE3D0DDA4FE8853471139C9393F`

It says "accept exactly one program, with that measurement hash". In this case the value came from the output of the
build process as shown above. This is useful when you don't trust the author nor host of the enclave, and want to
audit the source code and then reproduce the build.

Often that's too rigid. We trust the *developer* of the enclave, just not the host. In that case we'll accept any enclave
signed by the developer's public key. We can express that by listing code signing key hashes, like this:

`S:01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B PROD:1`

When constraining to a signing key we must also specify the product ID, because a key can be used to sign more than
one product. 

Add this line to the end of the client's `main` method:

```java
// Check it's the enclave we expect. This will throw InvalidEnclaveException if not valid.
EnclaveConstraint.parse("S:01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B PROD:1 SEC:INSECURE").check(attestation);
```

!!! tip
    Replace the signing key in the snippet above with the enclave signer hash that was printed when you
    [built the enclave](#run-what-weve-got-so-far).

This line of code parses a simple constraint that says any enclave (even if run in simulation mode) signed by this
hash of a code signing key with product ID of 1 is acceptable. Obviously in a real app, you would remove the part
that says `SEC:INSECURE`, but it's convenient to have this whilst developing. You'd probably also retrieve the
constraint from a configuration file, system property or command line flag. Finally it uses the `check` method with
the attestation object. If anything is amiss an exception is thrown, so past this point we know we're talking to the
real `ReverseEnclave` we wrote earlier.  

!!! tip
    If needed, more than one key hash could be added to the list of enclave constraints (e.g. if simulation and debug 
    modes use a distinct key from release mode). The enclave is accepted if one key hash matches.
    ```java
    // Below, two distinct signing key hashes can be accepted.
    EnclaveConstraint.parse("S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 "
            + "S:01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B PROD:1 SEC:INSECURE").check(attestation);
    ```
    If you are building an enclave in mock mode then the enclave reports it is using a signing key hash
    consisting of all zeros. If you want to allow a mock enclave to pass the constraint check then you need to include
    this dummy signing key in your constraint:
    ```java
    // Below, two distinct signing key hashes or the zero dummy hash can be accepted.
    EnclaveConstraint.parse("S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 "
            + "S:01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B"
            + "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE").check(attestation);
    ```


### Keys and mail

The client wants to receive a response from the enclave, and we want that to be encrypted/tamperproofed too. That means it
need a key pair of its own. Conclave uses Curve25519, a state of the art elliptic curve algorithm. For reasons of
implementation robustness and avoidance of side channel attacks, this is the only algorithm supported by Conclave Mail.
If you want to use other algorithms for some reason you would need to implement your own messaging system on top of
host-local calls. Alternatively, use that other algorithm to encrypt/decrypt a Curve25519 private key. Generating such
a key is straightforward: 

```java
PrivateKey myKey = Curve25519PrivateKey.random();
```

Unfortunately the Java Cryptography Architecture only introduced official support for Curve25519 in Java 11. At the
moment in Conclave therefore, you must utilize our `Curve25519PublicKey` and `Curve25519PrivateKey`
classes. In future we may offer support for using the Java 11 JCA types directly. A Curve25519 private key is simply
32 random bytes, which you can access using the `getEncoded()` method on `PrivateKey`. 

Now we have a key with which to receive the response, we create a mail to the enclave. This is done using the
`EnclaveInstanceInfo.createPostOffice` method, which returns a new `PostOffice` object. This is similar to the post office
inside the enclave and let's us create mail with increasing sequence numbers. We pass in our private key when creating
the post office so it's mixed in to the calculations when the mail is encrypted and thus becomes available in
`getAuthenticatedSender()` inside the enclave.

```java
PostOffice postOffice = attestation.createPostOffice(myKey, "reverse");
byte[] encryptedMail = postOffice.encryptMail(toReverse.getBytes(StandardCharsets.UTF_8));
```

We've chosen a topic value of "reverse" but any will do as the client uses a random key and only sends one mail using it.
However, if a client needs to send multiple mail which are related to each other such that it's important they reach the
enclave in the same order then these mail need to all use the same topic and private key. In other words, they need to
be created from the same `PostOffice` instance. The enclave will automatically detect any dropped or reordered messages
and throw an exception.

!!! tip
    Make sure there's only one `PostOffice` instance per (destination public key, sender private key, topic) triple.
    This can be done very easily in Kotlin by using a data class with these three properties as a key to a `HashMap`.
    The same can be done in Java except it will be slightly more verbose as you will
    need to override the `equals` and `hashCode`methods of your key class. Modern IDEs let you do this very quickly.

In more complex apps it might be smart to use the topic in more complex ways, like how a web app can use the URL to
separate different functions of the app.

Now we have an encrypted message we can write it to the socket and receive the enclave's response.

```java
System.out.println("Sending the encrypted mail to the host.");
DataOutputStream toHost = new DataOutputStream(socket.getOutputStream());
toHost.writeInt(encryptedMail.length);
toHost.write(encryptedMail);

// Enclave will mail us back.
byte[] encryptedReply = new byte[fromHost.readInt()];
System.out.println("Reading reply mail of length " + encryptedReply.length + " bytes.");
fromHost.readFully(encryptedReply);

// The same post office will decrypt the response.
EnclaveMail reply = postOffice.decryptMail(encryptedReply);
System.out.println("Enclave reversed '" + toReverse + "' and gave us the answer '" + new String(reply.getBodyAsBytes()) + "'");

socket.close();
```

We write out the length of the mail, then the mail bytes, then read the length of the response and read the response 
bytes. Finally we use `PostOffice.decryptMail` on the same instance we used to create our request, passing in the encrypted
reply. This method decrypts the bytes using our private key, checks they really did come from that enclave (by checking
the authenticated sender key against the enclave's public key in the attestation), decodes the bytes and yields the reply.
We can then access the body of the message using `EnclaveMail.getBodyAsBytes()`.

Finally we close the socket, and we're done. Phew! 😅

## Testing

There are two ways you can test the enclave: using a mock build of the enclave in tests defined as part of
your enclave project, or integrating enclave tests in your host project.

### Mock tests within the enclave project

Conclave supports building and running tests within the enclave project itself. When you define tests as part
of your enclave project, the enclave classes are loaded along with the tests. Conclave detects this
configuration and automatically enables mock mode for the enclave and test host. You do not need to explicitly 
specify [mock mode](mockmode.md) for your project.

This allows you to whitebox test your enclave by running it fully in-memory. There is no need for SGX hardware 
or a specific OS and thus it is ideal for cross-platform unit testing.

To enable mock mode in your enclave project tests you need to include the following test dependency in your
**enclave** module `build.gradle` file.

```groovy hl_lines="2"
testImplementation "com.r3.conclave:conclave-host"
```

You can then create an instance of the enclave as normal by calling `EnclaveHost.load`.

```java
EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
mockHost.start(null, null);
```

Conclave will detect that the enclave class is on the classpath and will start the enclave in mock mode. You
can obtain the enclave instance using the `EnclaveHost.mockEnclave` property.

```java
ReverseEnclave reverseEnclave = (ReverseEnclave)mockHost.getMockEnclave();
```

### Integrating enclave tests in your host project

When you want to test your enclave on real SGX hardware or in a simulated SGX environment **you need to define your tests
in a project separate from the enclave project.** A suitable place for your tests would be to define them as part of
the host project tests.

Loading and testing the enclave on real hardware or in a simulated SGX environment is straightforward: the enclave needs 
to be loaded with `EnclaveHost.load`. By default this will run the tests in a simulated SGX environment and will require
the tests to be executed within Linux. In addition, testing on real hardware will require the tests to be executed within
Linux on a system that supports SGX.



```java
@EnabledOnOs(OS.LINUX)
public class NativeTest {
    private static EnclaveHost enclave;

    @BeforeAll
    static void startup() throws EnclaveLoadException {
        enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        enclave.start(new AttestationParameters.DCAP(), null);
    }
}
```

You'll notice that we annotated the test class with `@EnabledOnOs(OS.LINUX)`. This is from
[JUnit 5](https://junit.org/junit5/docs/current/user-guide/#writing-tests-conditional-execution-os) and it will make sure
the native test isn't run on non-Linux environments.

The tests can use any enclave mode: release, debug, simulation or mock. Therefore if you want to run your tests on
a non-Linux system then you can configure your tests to depend on a mock mode enclave instead. In this case, remove the
`@EnabledOnOs(OS.LINUX)` annotation from the above code.

!!! note
    Running your integration tests in mock mode is very similar to 
    [Integrating enclave tests in your host project](#integrating-enclave-tests-in-your-host-project). In both cases
    the enclave code is loaded and run fully in memory. However for integration tests, you can also choose to run
    the tests in modes other than mock, which is not possible for enclave project tests.

Running

```gradlew host:test```

will execute the test using a simulation enclave, or not at all if the OS is not Linux. You can switch to a debug enclave
and test on real secure hardware by using the `-PenclaveMode` flag:

```gradlew -PenclaveMode=debug host:test```

Or you can use a mock enclave and test on a non-Linux platform by removing `@EnabledOnOs(OS.LINUX)` and by running this
command:

```gradlew -PenclaveMode=mock host:test```

!!! tip
    To run the tests in a simulated SGX environment on a non-Linux machine you can use Docker, which manages Linux 
    VMs for you. See the instructions for [compiling and running the host](tutorial.md#running-the-host) for more information.

