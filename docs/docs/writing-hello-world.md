# Writing the sample enclave

The sample "hello world" enclave just reverses whatever string is passed into it. We'll do these things to make 
our own version of the hello enclave project:

1. Configure Gradle. At this time Conclave projects must use Gradle as their build system.
2. Create a new subclass of [`Enclave`](api/com/r3/conclave/enclave/Enclave.html)
3. Implement the `EnclaveCall` interface for local communication.
4. Write a host program.
5. Run the host and enclave in simulation mode.
6. Run the host and enclave in debug mode.

<!---
TODO: Complete this tutorial:

      Signing/running in release mode.
      Update as API is completed.
-->

## Configure Gradle

Create a new Gradle project via whatever mechanism you prefer, e.g. IntelliJ can do this via the New Project wizard.
Create two modules defined in the project: one for the host and one for the enclave. The host program may be an
existing server program of some kind, e.g. a web server, but in this tutorial we'll write a command line host.   
    
### `settings.gradle`

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
```

This boilerplate is unfortunately necessary to copy/paste into each project that uses Conclave. It sets up Gradle to
locate the plugin that configures the rest of the boilerplate build logic for you :wink:
    
The `pluginManagement` block tells Gradle to use a property called `conclaveRepo` to find the `repo` directory
in your SDK download. Because developers on your team could unpack the SDK anywhere, they must configure the path
before the build will work. The code above will print a helpful error if they forget or get it wrong.

To set the value a developer can add to [the `gradle.properties` file](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#declare_properties_in_gradle_properties_file)
a couple of lines like this:

```text
conclaveRepo=/path/to/sdk/repo
conclaveVersion=0.2
```

0.2 here means use beta 2.

Gradle properties can be set using a file in the project directory, or more usefully in the developer's home directory.
You may wish to put the version in the project's `gradle.properties` file and the path in each developer's personal
`gradle.properties`. Alternatively just add a `sdk` directory to the `.gitignore` and require everyone to unpack the
SDK to the source tree. 
    
### `build.gradle`
    
Add the following code to your root `build.gradle` file to import the repository:

```groovy hl_lines="20 21 22 23 24 25"
plugins {
    id 'java'
    id 'idea'
}

idea {
    module {
        downloadJavadoc = true
    }
}

subprojects {
    apply plugin: 'idea'
    idea {
        module {
            downloadJavadoc = true
        }
    }

    repositories {
        maven {
            url = rootProject.file(conclaveRepo)
        }
        mavenCentral()
    }
}
```

!!! tip
    Most of this boilerplate isn't strictly necessary except for the highlighted region. However, the rest works around
    [a bug in IntelliJ IDEA](https://youtrack.jetbrains.com/issue/IDEA-231254) if you wish to use that IDE in which 
    interactive JavaDocs would otherwise not be available. To benefit from the workaround you should run `./gradlew idea`
    to generate your IntelliJ project. If IntelliJ is running at the time it should automatically notice the changes and
    apply them. If you click "import from Gradle" in the notification popup workflow then everything will work fine 
    except JavaDoc integration.

### Configure the host build

In the host module add a dependency on the Conclave host library:

```groovy hl_lines="2"
dependencies {
    implementation "com.r3.conclave:conclave-host"
}
```

You don't need to specify the version number for Conclave libraries. The plugin will set the version to match the
plugin version automatically.

SGX enclaves can be built in one of three modes: simulation, debug and release. Simulation mode doesn't require any
SGX capable hardware. Debug executes the enclave as normal but allows the host process to snoop on and modify the
protected address space, so provides no protection. Release locks out the host and provides the standard SGX
security model, but (at this time) requires the enclave to be [signed](signing.md) with a key whitelisted by Intel. 

Add this bit of code to your Gradle file to let the mode be chosen from the command line:

```groovy
// Override the default (simulation) with -PenclaveMode=
def mode = findProperty("enclaveMode")?.toString()?.toLowerCase() ?: "simulation"
```

Then use `mode` in another dependency, this time, on the enclave module:

```groovy hl_lines="2"
dependencies {
    runtimeOnly project(path: ":my-enclave", configuration: mode)
}
```

This says that at runtime (but not compile time) the enclave must be on the classpath, and configures dependencies to
respect the three different variants of the enclave.

If you intend to use an [external signing process](signing.md) to sign your enclave then add the following lines to
the Gradle file:

```groovy hl_lines="4 5 6 7"
// Override the default (simulation) with -PenclaveMode=
def mode = findProperty("enclaveMode")?.toString()?.toLowerCase() ?: "simulation"

// Create a task that can be used for generating signing materials
tasks.register("prepareForSigning") {
    it.dependsOn(":enclave:generateEnclaveSigningMaterial" + mode.capitalize())
}    
```

This creates a new task that can be invoked using Gradle to halt the build after generating materials that need to
be signed by an external signing process. After the material has been signed the build can be resumed.

### Configure the enclave build

Add the Conclave Gradle plugin:

```groovy hl_lines="2"
plugins {
    id 'com.r3.conclave.enclave'
}
```

and a dependency on the Conclave enclave library:

```groovy hl_lines="2"
dependencies {
    implementation "com.r3.conclave:conclave-enclave"
}
```

Specify the enclave's product ID and revocation level:

```groovy hl_lines="2"
conclave {
    productID = 1
    revocationLevel = 0
}
```

The product ID is an arbitrary number that can be used to distinguish between different enclaves produced by the same
organisation (which may for internal reasons wish to use a single signing key). This value should not typically change.

The revocation level should be incremented if a weakness in the enclave code discovered and fixed; doing this will enable
clients to avoid connecting to old, compromised enclaves. The revocation level should not be incremented on every new
release, but only when security improvements have been made.

Specify the signing methods for each of the build types:

```groovy hl_lines="5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27"
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
        // The enclave needs to be built in stages.
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

And with that, we're done configuring the build.

### Information on signing keys for the tutorial

The example configuration above specifies different [signing configurations](signing.md#signing-configurations) for 
each of the different build types. 

 * Simulation builds use the default dummy key. 
 * Debug builds use a private key. 
 * Release builds use an external signing process that requires Gradle to be invoked in two stages.

The ```hello-world``` sample in the SDK contains some example keys that can be used with the ```privateKey```
and ```externalKey``` signing types. These can be found in ```hello-world/signing/```.

| Key Files | Description |
| :----     | :----       |
| sample_private_key.pem | A 3072 bit RSA private key that can be used to test the ```privateKey``` signing type |
| external_signing_*.pem | An AES encrypted 3072 bit RSA public/private key pair that can be used to test the ```externalKey``` signing type. The private key can be accessed with the password '12345' |

Alternatively you can provide or [generate your own](signing.md#generating-keys-for-signing-an-enclave) keys.

!!! important
    These keys aren't whitelisted by Intel so you can't use them for real release builds.
    Only use these sample keys for the tutorial. Don't use them for signing your own enclaves!

## Create a new subclass of `Enclave`

Enclaves are similar to standalone programs and as such have an equivalent to a "main class". This class must be a
subclass of [`Enclave`](/api/com/r3/conclave/enclave/Enclave.html).

Create your enclave class:

```java
package com.superfirm.enclave;   // CHANGE THIS

import com.r3.conclave.common.enclave.EnclaveCall;
import com.r3.conclave.enclave.Enclave;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave implements EnclaveCall {
    @Override
    public byte[] invoke(byte[] bytes) {
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            result[i] = bytes[bytes.length - 1 - i];
        return result;
    }
}
```

The `Enclave` class by itself doesn't require you to support direct communication with the host. This is because
sometimes you don't need that and shouldn't have to implement message handlers. In this case we'll use that
functionality because it's a good place to start learning, so we also implement the `EnclaveCall` interface.
There's one method we must supply: `invoke` which takes a byte array and optionally returns a byte array back. Here
we just reverse the contents.

!!! tip
    In a real app you would use the byte array to hold serialised data structures. You can use whatever data formats you
    like. You could use a simple string format or a binary format like protocol buffers.

## Write a host program

An enclave by itself is just a library. It can't (at this time) be invoked directly. Instead you load it from inside
a host program.

It's easy to load then pass data to and from an enclave. Let's start with the skeleton of a little command line app:

```java
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

At first we will be building and running our enclave in 'simulation' mode. This does not require the platform 
hardware to support SGX. However, when we want to load either a debug or release build of the enclave we need 
to ensure the platform supports SGX.

By adding the code below to the main method we can determine whether the platform can load 'debug' and 'release' 
enclaves. This method reports the actual hardware status even if you are currently working with 'simulation' 
enclaves.

If SGX is not supported the function throws an exception which describes the reason why. There are a number of 
common reasons why SGX may not be supported including:

1. The CPU or the system BIOS does not support SGX.
2. SGX is disabled in the BIOS and must be manually enabled by the user.
3. SGX is disabled but can be enabled in software.

If SGX is disabled but can be enabled in software the code below attempts to automatically enable SGX support 
by specifying the 'true' parameter. It might be necessary to run this application with root access and/or reboot 
the system in order to successfully enable SGX. The exception message will describe if this is the case.

```java
        try {
            EnclaveHost.checkPlatformSupportsEnclaves(true);
            System.out.println("This platform supports enclaves in simulation, debug and release mode.");
        } catch (EnclaveLoadException e) {
            System.out.println("This platform currently only supports enclaves in simulation mode: " + e.getMessage());
        }
```

To load the enclave we'll put this after the platform check:

```java
String className = "com.r3.conclave.sample.enclave.ReverseEnclave";
try (EnclaveHost enclave = EnclaveHost.load(className)) {
    enclave.start();

    System.out.println(callEnclave(enclave, "Hello world!"));
    // !dlrow olleH      :-)

    // TODO: Get the remote attestation
}
```

This code starts by creating an [`EnclaveHost`](api/com/r3/conclave/host/EnclaveHost.html) object. This names the 
class and then attempts to load it inside another JVM running inside an enclave. A remote attestation procedure is
then performed involving Intel's servers. This procedure can fail if attempting to load a 'debug' or 'release' enclave 
and the platform does not support SGX. This is why it is important to perform the platform check we made in the code 
above. If the enclave does fail to load for any reason then an exception is thrown describing the reason why.

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
final byte[] inputBytes = input.getBytes();
return new String(enclave.callEnclave(inputBytes));
```

So we just convert the string to bytes, send it to the enclave, and convert the response from bytes back to a string.

## Remote attestation

There's no point in using an enclave to protect purely local data, as the data must ultimately come from the
(assumed malicious/compromised) host in that scenario. That's why you need remote attestation, which lets an enclave 
prove its identity to the third parties who will upload secret data. If this paragraph doesn't make
sense please review the [Architecture overview](architecture.md) and the [enclaves](enclaves.md) section.
    
Using remote attestation is easy! Just obtain an `EnclaveInstanceInfo` and serialize/deserialize it using the
provided methods. There's a useful `toString` method: 
    
```java
final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
final byte[] attestationBytes = attestation.serialize();
System.out.println(EnclaveInstanceInfo.deserialize(attestationBytes));
```

That will print out something like this:

```text
Remote attestation for enclave 04EDA32215B496C3890348752F47D77DC34989FE4ECACCF5EC5C054F1D68BBE6:
  - Mode: SIMULATION
  - Code signing key hash: 0230CFF16B15F8826AB6BBF686E190C94FDC0AD725041D3E16F5216A925D0A5C
  - Product ID: 0
  - Revocation level: 0

Assessed security level at 2020-03-13T12:40:55.819Z is INSECURE
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
    3. The timestamp is signed by Intel and comes from their servers. 
        
An instance has a security assessment, which can change in response to discovery of vulnerabilities in the
infrastructure (i.e. without anything changing about the host or enclave itself). As we can see this enclave isn't
actually considered secure yet because we're running in simulation mode still. An enclave can be `SECURE`, `STALE`, 
or `INSECURE`. A assessment of `STALE` means there is a software/firmware/microcode update available for the platform
that improves security in some way. The client may wish to observe when this starts being reported and define a 
time span in which the remote enclave operator must upgrade.                                             

Now get the serialized bytes to a client via whatever network mechanism you want. The bytes are essentially a large,
complex digital signature, so it's safe to publish them publicly. An attestation doesn't inherently expire but because 
the SGX ecosystem is always moving, client code will typically have some frequency with which it expects the host code
to refresh the `EnclaveInstanceInfo`. At present this is done by stopping/closing and then restarting the enclave.

## Constraints

How do you know the `EnclaveInstanceInfo` you've got is for the enclave you really intend to interact with? In normal
client/server programming you connect to a host using some sort of identity, like a domain name or IP address. In
enclave programming the location of the enclave might not matter much because the host is untrusted. Instead you have
to verify *what* is running, not *where* it's running.

One way to do it is by inspecting the properties on the `EnclaveInstanceInfo` object and hard-coding some logic. That
works fine, but is a common pattern in enclave-oriented design so we provide an API to do it for you.

The [`EnclaveConstraint`](/api/com/r3/conclave/client/EnclaveConstraint.html) class takes an `EnclaveInstanceInfo` and
performs some matching against it. A constraint object can be built in code, or it can be loaded from a small domain
specific language encoded as a one-line string. The string form is helpful if you anticipate frequent upgrades that
should be whitelisted, or other frequent changes to the acceptable enclave, as it can be easily put into a
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

`C:04EDA32215B496C3890348752F47D77DC34989FE4ECACCF5EC5C054F1D68BBE6`

It says "accept exactly one program, with that measurement hash". In this case the value came from the output of the
build process as shown above.

## Run the host and enclave

We can apply the Gradle `application` plugin and set the `mainClassName` property
[in the usual manner](https://docs.gradle.org/current/userguide/application_plugin.html#application_plugin) to let us run
the host from the command line.

Now run `gradlew host:run` and it should print "Hello World!" backwards along with the security info as shown above.

During the build you should see output like this:

```text
> Task :enclave:generateEnclaveletMetadataSimulation
Succeed.
Enclave measurement: 04EDA32215B496C3890348752F47D77DC34989FE4ECACCF5EC5C054F1D68BBE6
```

The measurement should correspond to the value found in the `EnclaveInstanceInfo.getCodeHash()` property.

You can switch to debug mode, whereby the enclave is run in a real SGX environment albeit with a backdoor for debugging,
by specifying the `enclaveMode` property. You will need to run this on a machine with SGX enabled and also provide your
EPID SPID and attestation key. See [here](ias.md#getting-access) for more information.

```gradlew -PenclaveMode=debug host:run --args="<SPID> <attestation key>"```

## Testing

There are two ways you can test the enclave: as a mock or natively.

### Mock

The `conclave-testing` library has a `MockHost` class which lets you whitebox test your enclave by running the enclave
fully in-memory. There is no need for SGX hardware or a specific OS and thus ideal for cross-platform unit testing. The
underlying enclave object is also exposed enabling you to make assertations on the enclave's state, something that obviously
cannot be done on real hardware or even in simulation mode.

In your enclave module add the following test dependency

```groovy hl_lines="2"
    testImplementation "com.r3.conclave:conclave-testing"
```

You create your mock enclave by calling `MockHost.loadMock`.

```java
MockHost<ReverseEnclave> mockHost = MockHost.loadMock(ReverseEnclave.class);
mockHost.start(null, null);
ReverseEnclave reverseEnclave = mockHost.getEnclave();
```

`MockHost` is a `EnclaveHost` so you call the enclave as normal with `callEnclave`. You have direct assess to the
enclave instance with `mockHost.getEnclave()`.

### Native

Testing the enclave natively is relatively straightforward: the enclave needs to be loaded with `EnclaveHost.load`. By
default this will run the tests in a simulated environment and will require the correct OS. Native tests are ideal for 
integration testing.

To test the enclave in debug mode on real secure hardware the `-PenclaveMode=debug` flag needs to be specified and the
SPID and attestation key need to be passed into the test. This can be done with system properties.

In your host module build file:

```groovy hl_lines="2"
test {
    useJUnitPlatform()
    // Pass through any -Pspid and -Pattestation-key parameters to the tests
    systemProperties project.properties.subMap(["spid", "attestation-key"])
}
```

Pass these values to `EnclaveHost.start` in your test:

```java
private static EnclaveHost enclave;

@BeforeAll
static void startup() throws EnclaveLoadException {
    enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
    String spid = System.getProperty("spid");
    String attestionKey = System.getProperty("attestation-key");
    enclave.start(spid != null ? OpaqueBytes.parse(spid) : null, attestionKey);
}
```

```gradlew -PenclaveMode=debug -Pspid=<SPID> -Pattestation-key=<attestation key> host:test```

Note that native tests are located in the host module while mock tests in the enclave module.
