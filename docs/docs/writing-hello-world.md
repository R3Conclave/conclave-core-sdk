# Writing the sample enclave

!!! tip
    If you want to get started as quickly as possible, you can use [Conclave Init](conclave-init.md) to bootstrap your project. 

    If you'd like to learn how Conclave apps work in detail, then keep reading.

The sample "hello world" enclave just reverses whatever string is passed into it. We'll do these things to make
our own version of the hello enclave project:

1. Configure Gradle.
1. Implement an enclave object that accepts encrypted messages from a client.
1. Configure the host program to run as a web server.
1. Run the host and enclave in mock, simulation and debug modes.
1. Write the client that sends the enclave encrypted messages via the host.

## Configure your modules

Create a new Gradle project via whatever mechanism you prefer, e.g. IntelliJ can do this via the New Project wizard.
Create three modules defined in the project: one for the host, one for the enclave and one for the client.

The host program may be an existing server program of some kind, but in this tutorial we'll write a command line 
host that uses built-in web server that comes with Conclave. The client may likewise be a GUI app or integrated with 
some other program (like a server), but in this case to keep it simple the client will also be a command line app.    

### Root `settings.gradle` file

In the unzipped SDK there is a directory called `repo` that contains a local Maven repository. This is where the libraries
and Gradle plugin can be found. We need to tell Gradle to look there for plugins.

Create or modify a file called `settings.gradle` in your project root directory, so it looks like this:

```groovy
pluginManagement {
    repositories {
        maven {
            def repoPath = file(rootDir.relativePath(file(conclaveRepo)))
            if (repoPath == null) {
                throw new GradleException("Make sure the 'conclaveRepo' setting exists in gradle.properties, or your " +
                        "\$HOME/gradle.properties file. See the Conclave tutorial on https://docs.conclave.net")
            } else if (!new File(repoPath, "com").isDirectory()) {
                throw new GradleException("The $repoPath directory doesn't seem to exist or isn't a Maven " +
                        "repository; it should be the SDK 'repo' subdirectory. See the Conclave tutorial on " +
                        "https://docs.conclave.net")
            }
            url = repoPath
        }
        // Add standard repositories back.
        gradlePluginPortal()
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
locate the plugin that configures the rest of the boilerplate build logic for you :wink:.

The `pluginManagement` block tells Gradle to use a property called `conclaveRepo` to find the `repo` directory
in your SDK download. Because developers on your team could unpack the SDK anywhere, they must configure the path
before the build will work. The code above will print a helpful error if they forget or get it wrong.

To set the value, add a couple of lines to
[the `gradle.properties` file](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#declare_properties_in_gradle_properties_file)
like this:

```text
conclaveRepo=/path/to/sdk/repo
conclaveVersion=1.2-RC3
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


### Configure the _host_ to run as a web server

Add this bit of code to your host `build.gradle` file so the mode [mode](enclave-modes.md) may be set from the
command line:

```groovy
// Override the default (mock) with -PenclaveMode=
def mode = findProperty("enclaveMode")?.toString()?.toLowerCase() ?: "mock"
```

We need to apply the Gradle
[`application` plugin]((https://docs.gradle.org/current/userguide/application_plugin.html#application_plugin)) and set the `mainClassName` property
to let us run the host web server from the command line:

```groovy hl_lines="3-4 7-9"
plugins {
    id 'java'
    id 'application'
    id 'com.github.johnrengelman.shadow' version '6.1.0'
}

application {
    mainClassName = "com.r3.conclave.host.web.EnclaveWebHost"
}
```

We've also added the [Shadow plugin](https://imperceptiblethoughts.com/shadow/introduction/) to allow us to run the 
host application from a single single "fat" jar containing all the dependencies.  

Then add the following dependencies:

```groovy hl_lines="2 3"
dependencies {
    runtimeOnly project(path: ":enclave", configuration: mode)
    runtimeOnly "com.r3.conclave:conclave-web-host:$conclaveVersion"

    testImplementation "com.r3.conclave:conclave-host:$conclaveVersion"
    testImplementation "org.junit.jupiter:junit-jupiter:5.6.0"
}
```

This says that at runtime (but not compile time) the `:enclave` module must be on the classpath, and configures
dependencies to respect the three different variants of the enclave. That is, the enclave module will expose tasks
to compile and use either mock, simulation, debug or release mode. Which task to use is actually selected by the host build.

!!! tip
    Don't worry if you see the error `Could not resolve project :enclave`. This will be resolved when
    we configure the enclave module in the next section.

We also added the web server as a runtime dependency as well. You'll notice there are no compile-time dependencies.
There's no need to write any host code if using the bundled web server!

Finally we should configure the shadow jar:

```groovy
shadowJar {
    archiveAppendix.set(mode)
    archiveClassifier.set("")
}
```

Since the host shadow jar contains the enclave, it's helpful to know the enclave mode the jar represents. This will 
insert the mode into the host jar filename.

If you intend to use an [external signing process](signing.md) to sign your enclave then add the following lines to
the Gradle file:

```groovy
// Create a task that can be used for generating signing materials
tasks.register("prepareForSigning") {
    it.dependsOn(":enclave:generateEnclaveSigningMaterial" + mode.capitalize())
}
```

This creates a new task that can be invoked using Gradle to halt the build after generating materials that need to
be signed by an external signing process. After the material has been signed, the build can be resumed.

### Configure the _enclave_ module

Add the Conclave Gradle plugin to your enclave `build.gradle` file:

```groovy hl_lines="2"
plugins {
    id 'com.r3.conclave.enclave'
}
```

and add your dependencies, in this case we are using junit for testing. You don't need to include conclave libraries
here as the enclave gradle plugin will include them for you:

```groovy
dependencies {
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

The client module is the simplest of all. Since the host is using the Conclave web server, we will configure the 
enclave client to be a web-client. 

```groovy hl_lines="11"
plugins {
    id 'application'
    id 'com.github.johnrengelman.shadow' version '6.1.0'
}

application {
    mainClassName = "com.r3.conclave.sample.client.ReverseClient" // CHANGE THIS
}

dependencies {
    implementation "com.r3.conclave:conclave-web-client:$conclaveVersion"

    runtimeOnly "org.slf4j:slf4j-simple:1.7.32"
}
```

And with that, we're done configuring the module.

## Create a new subclass of `Enclave`

Enclaves are similar to standalone programs and as such have an equivalent to a "main class". This class must be a
subclass of [`Enclave`](api/-conclave/com.r3.conclave.enclave/-enclave/index.html).

Create your enclave class:

```java
package com.superfirm.enclave;  // CHANGE THIS

import com.r3.conclave.enclave.Enclave;

/**
 * Simply reverses the bytes that are passed in.
 */
public class ReverseEnclave extends Enclave {

    private String reverse(String input) {
        var builder = new StringBuilder(input.length());
        for (var i = input.length() - 1; i >= 0; i--) {
            builder.append(input.charAt(i));
        }
        return builder.toString();
    }

    @Override
    protected void receiveMail(EnclaveMail mail, String routingHint) {
        // First, decode mail body as a String.
        var stringToReverse = new String(mail.getBodyAsBytes());
        // Reverse it and re-encode to UTF-8 to send back.
        var reversedEncodedString = reverse(stringToReverse).getBytes();
        // Get the post office object for responding back to this mail and use it to encrypt our response.
        var responseBytes = postOffice(mail).encryptMail(reversedEncodedString);
        postMail(responseBytes, routingHint);
    }
}
```

!!! tip
    You'll notice that the enclave code is using the `var` syntax that was introduced in Java 10. That's because the 
    enclave environment supports Java 11 by default!

We override `receiveMail` so that the enclave can process the mail it receives from the remote clients via the web-based
host server. Here we just reverse the contents of the mail body and reverse it as a string.

!!! tip
    In a real app you would use the byte array to hold serialised data structures. You can use whatever data formats you
    like. You could use a simple string format or a binary format like protocol buffers.

## Encrypted messaging

The enclave isn't of much use to the host, because the host already trusts itself. It's only useful to remote clients
that want to use the enclave for computation without having to trust the host machine or software.

Conclave provides an API for this called Mail. Conclave Mail handles all the encryption for you, but leaves it up to 
you how the bytes themselves are moved around. We're using REST via the provided web server but you could use a 
gRPC API, JMS message queues, a simple TCP socket, or even files.

!!! info
    [Learn more about the design of Conclave Mail](architecture.md#mail), and [compare it to TLS](mail.md).

### Receiving and posting mail in the enclave

The `receiveMail` method that we implemented above has a routing hint string parameter. It's provided by the host 
and it helps the host route replies when dealing with multiple clients. It's passed into `postMail` when the enclave 
posts a reply.

#### Mail headers

The first parameter is an `EnclaveMail`. This object gives us access to the body bytes that the client sent, but it
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

### Threading

In this tutorial we won't write a multi-threaded enclave. If you want to do this, you'll need to override the
`boolean isThreadSafe()` method in the `Enclave` class (use `override val threadSafe: Boolean get() = true` in Kotlin).
This tells Conclave to allow multiple threads into the enclave simultaneously. You're required to opt-in to allowing
multi-threading to avoid accidents when someone writes a simple enclave that isn't thread safe, and forgets that the host
is malicious and can enter your code with multiple threads simultaneously even if you aren't ready for it, corrupting
your application level data via race conditions. By blocking multi-threading until you indicate readiness, the hope
is that some types of attack can be avoided. See the page on [enclave threading](threads.md) to learn more.

## Remote attestation

Before we continue onto the client, we first need to quickly talk about remote attestation and why it's important.

There's no point in using an enclave to protect purely local data, as the data must ultimately come from the
(assumed malicious/compromised) host in that scenario. That's why you need remote attestation, which lets an enclave
prove its identity to the third parties who will upload secret data. If this paragraph doesn't make
sense please review the [Architecture overview](architecture.md) and the [Enclaves](enclaves.md) section.

Before a client can set up communication with an enclave it must first get its remote attestation object, or its 
`EnclaveInstanceInfo`. How a client gets hold of enclave's `EnclaveInstanceInfo` depends on the host. It could be a 
REST endpoint, which is what we'll be using, or as a file downloaded out of band.

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
or `INSECURE`. An assessment of `STALE` means there is a software/firmware/microcode update available for the platform
that improves security in some way. The client may wish to observe when this starts being reported and define a
time span in which the remote enclave operator must upgrade.

An attestation doesn't inherently expire but because the SGX ecosystem is always moving, client code will typically have
some frequency with which it expects the host code to refresh the `EnclaveInstanceInfo`. At present this is done by
stopping/closing and then restarting the enclave.

## Run what we've got so far

We should be ready to run the host web server from the command line.

```bash
./gradlew host:shadowJar
java -jar host/build/libs/host-mock.jar
```

!!! note
    We configured mock to our default which is why the host jar name contains the "mock" string.

The Conclave host web server uses Spring Boot so you will see the Spring logo as the web server starts up. Once it's 
done starting up it will be ready to communicate with the client on http://localhost:8080.

!!! warning
    Even though we use Conclave mail which does the encryption and authentication for us, when using the Conclave 
    host web server it's still important to use HTTPS for anything other than internal development. The web host 
    protocol uses a unique correlation ID per client which must be encrypted over HTTPS. Setting up a HTTPS 
    connection is beyond the scope of this tutorial but you may wish to look at configuring Spring Boot or setting 
    up a reverse proxy such as Nginx or Apache.

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v2.4.2)
```

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

```bash
./gradlew -PenclaveMode=debug host:shadowJar
```

## Writing the client

The client app will do three things:

1. Connect to the host web server and download the `EnclaveInstanceInfo` from it.
1. Verify the enclave is acceptable: i.e. that it will do what's expected.
1. Send it the command line arguments as a string to reverse and get back the answer, using encrypted mail.

Actually, the first two steps are done for you when using an
[`EnclaveClient`](/api/-conclave/com.r3.conclave.client/-enclave-client/index.html) object. `EnclaveClient` handles the encryption and 
decryption of Mail for you and provides a simple interface for sending and receiving Mail. However, it doesn't know 
_how_ to transport the mail, which is where [`EnclaveTransport`](/api/-conclave/com.r3.conclave.client/-enclave-transport/index.html) 
comes in. Since we're connecting to the host web server, we'll be using
[`WebEnclaveTransport`](/api/-conclave/com.r3.conclave.client.web/-web-enclave-transport/index.html) as our transport.

Copy the client implementation below into your project:
```java
package com.superfirm.cleint; // CHANGE THIS

import com.r3.conclave.client.EnclaveClient;
import com.r3.conclave.client.web.WebEnclaveTransport;
import com.r3.conclave.common.EnclaveConstraint;
import com.r3.conclave.common.InvalidEnclaveException;
import com.r3.conclave.mail.EnclaveMail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ReverseClient {
    private static String DESCRIPTION = "Simple client that communicates with the ReverseEnclave using the web host.";
    private static String USAGE_MESSAGE = "Usage: reverse-client ENCLAVE_CONSTRAINT STRING_TO_REVERSE\n" +
            "  ENCLAVE_CONSTRAINT: Enclave constraint which determines the enclave's identity and whether it's " +
            "acceptable to use.\n" +
            "  STRING_TO_REVERSE: The string to send to the enclave to reverse.";

    private static String REVERSE_HOST_URL = "http://localhost:8080";

    public static void main(String... args) throws IOException, InvalidEnclaveException {
        if (args.length != 2) {
            System.out.println(DESCRIPTION);
            System.out.println(USAGE_MESSAGE);
        }

        EnclaveConstraint constraint = EnclaveConstraint.parse(args[0]);
        String stringToReverse = args[1];

        callEnclave(constraint, stringToReverse);
    }

    public static void callEnclave(EnclaveConstraint constraint, String stringToReverse) throws IOException, InvalidEnclaveException {
        try (WebEnclaveTransport transport = new WebEnclaveTransport(REVERSE_HOST_URL);
             EnclaveClient client = new EnclaveClient(constraint)) {

            client.start(transport);
            // TODO
        }
    }
}
```

When creating an `EnclaveClient` you have to first provide it an `EnclaveConstraint`.

### Constraints

How do you know the `EnclaveInstanceInfo` you've got is for the enclave you really intend to interact with? In normal
client/server programming you connect to a host using some sort of identity, like a domain name or IP address. TLS
is used to ensure the server that picks up is the rightful owner of the domain name you intended to connect to. In
enclave programming the location of the enclave might not matter much because the host is untrusted. Instead, you have
to verify *what* is running, rather than *where* it's running.

!!! note
    The domain name of the server can still be important in some applications, in which case you should use TLS instead
    of raw sockets as is the case here.

One way to do this is by inspecting the properties on the `EnclaveInstanceInfo` object and hard-coding some logic. That
works fine, but testing an `EnclaveInstanceInfo` is a common pattern in enclave programming, so we provide an API to
do it for you.

The [`EnclaveConstraint`](api/-conclave/com.r3.conclave.common/-enclave-constraint/index.html) class takes an `EnclaveInstanceInfo` and
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
6. The maximum age of the attestation in ISO-8601 duration format

If you specify a signing public key then you must also specify the product ID, otherwise if the organisation that
created the enclave makes a second different kind of enclave in the future, a malicious host might connect you with the
wrong one. If the input/output commands are similar then a confusion attack could be opened up. That's why you must
always specify the product ID even if it's zero.

The simplest possible string-form constraint looks like this:

`C:F86798C4B12BE12073B87C3F57E66BCE7A541EE3D0DDA4FE8853471139C9393F`

It says "accept exactly one program, with that measurement hash". In this case the value came from the output of the
build process as shown above. This is useful when you neither trust the author nor the host of the enclave, and want to
audit the source code and then reproduce the build.

Often that's too rigid. We trust the *developer* of the enclave, just not the host. In that case we'll accept any enclave
signed by the developer's public key. We can express that by listing code signing key hashes, like this:

`S:01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B PROD:1`

When constraining to a signing key we must also specify the product ID, because a key can be used to sign more than
one product.

As you can see from the above code, the enclave constraint is passed into the client via a command line flag:

```bash
--constraint="S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 PROD:1 SEC:INSECURE"
```

!!! tip
    Replace the signing key in the snippet above with the enclave signer hash that was printed when you
    [built the enclave](#run-what-weve-got-so-far).

The string is then parsed into an `EnclaveConstraint` using the the custom `EnclaveConstraintConverter` class. The 
above constraint says that any enclave (even if run in simulation mode) signed by this hash of a code signing key 
with product ID of 1 is acceptable. Obviously in a real app, you would remove the part that says `SEC:INSECURE`, but 
it's convenient to have this whilst developing.

`EnclaveConstraint` has a `check` method that compares the enclave's `EnclaveInstanceInfo` against the constraint and 
throws a `InvalidEnclaveException` if it doesn't match. This check is done automatically by the
`client.start(transport)` line above, as is the downloading of the `EnclaveInstanceInfo` from the host. Past this point 
we know we're talking to the real `ReverseEnclave` we wrote earlier.

If needed, more than one key hash could be added to the list of enclave constraints (e.g. if simulation and debug 
modes use a distinct key from release mode). The enclave is accepted if one key hash matches.

```
// Two distinct signing key hashes can be accepted.
S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 S:01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B PROD:1 SEC:INSECURE
```

If you are building an enclave in mock mode then the enclave reports it is using a signing key hash consisting of 
all zeros. If you want to allow a mock enclave to pass the constraint check then you need to include this dummy 
signing key in your constraint:

```
// The zero dummy hash can be accepted.
S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE
```

It is also possible to specify a maximum age for the attestation using the EXPIRE keyword:

```
S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 PROD:1 SEC:INSECURE EXPIRE:P6M2W5D
```

When specified, this will cause the check to fail if the timestamp within the attestation object indicates an
age older than the specified duration. If no period is specified then no expiry check will be applied. The age string
uses the ISO-8601 duration format. The above example is enforcing an maximum age of 6 months, 2 weeks and 5 days.

### Keys

The client wants to receive a response from the enclave, and we want that to be encrypted/tamperproofed too. That means it
need a key pair of its own. Conclave uses Curve25519, a state of the art elliptic curve algorithm. For reasons of
implementation robustness and avoidance of side channel attacks, this is the only algorithm supported by Conclave Mail.
If you want to use other algorithms for some reason you would need to implement your own messaging system on top of
host-local calls. Alternatively, use that other algorithm to encrypt/decrypt a Curve25519 private key.

The complexity of dealing with private keys is conveniently hidden inside `EnclaveClient`. The
`new EnclaveClient(constraint)` line in the above code also creates a new random Curve25519 private key. If you
have an existing private key that you want to use, or want to manually create a new random key, then you could do 
something like:

```java
PrivateKey myKey = Curve25519PrivateKey.random();
EnclaveClient client = new EnclaveClient(myKey, constraint);
```

!!! note
    Unfortunately the Java Cryptography Architecture only introduced official support for Curve25519 in Java 11. At the 
    moment in Conclave therefore, you must utilize our `Curve25519PublicKey` and `Curve25519PrivateKey` classes. In 
    future we may offer support for using the Java 11 JCA types directly. A Curve25519 private key is simply 32 
    random bytes, which you can access using the getEncoded() method on PrivateKey.

### Sending and receiving mail

Now that we've connected to the host web server and verified we're communicating with the right enclave we can now 
send it mail. This is done by calling `EnclaveClient.sendMail` and passing in the serialized bytes of the request 
message. If the enclave responds back immediately with a mail of its own then that is returned by `sendMail`. We can 
use all of this to fill in the missing piece in our client:

```java hl_lines="5 7-12"
public static void callEnclave(EnclaveConstraint constraint, String stringToReverse) throws IOException, InvalidEnclaveException {
    try (WebEnclaveTransport transport = new WebEnclaveTransport(REVERSE_HOST_URL);
         EnclaveClient client = new EnclaveClient(constraint)) {

        // Connect to the host and send the string to reverse
        client.start(transport);
        byte[] requestMailBody = stringToReverse.getBytes(StandardCharsets.UTF_8);
        EnclaveMail responseMail = client.sendMail(requestMailBody);

        // Parse and print out the response
        String responseString = (responseMail != null) ? new String(responseMail.getBodyAsBytes()) : null;
        System.out.println("Reversing `" + stringToReverse + "` gives `" + responseString + "`");
    }
}
```

The response we get back from the enclave is represented as an `EnclaveMail` object. We need the mail body which
contains the encoded reversed string.

!!! tip
    If you write your enclave such that it might respond back to the client later at some point then you can use the 
    `pollMail` method to poll for responses. It will return `null` if there aren't any.

## Testing

There are two ways you can test the enclave: using a mock build of the enclave in tests defined as part of
your enclave project, or integrating enclave tests in your host project.

### Mock tests within the enclave project

Mock mode allows you to whitebox test your enclave by running it fully in-memory. There is no need for SGX hardware
or a specific OS and thus it is ideal for cross-platform unit testing.

Conclave supports building and running tests within the enclave project itself. When you define tests as part
of your enclave project, the enclave classes are loaded along with the tests. Conclave detects this
configuration and automatically enables mock mode for the enclave and test host. You do not need to explicitly
specify [mock mode](mockmode.md) for your project.

To use this functionality, simply create an instance of the enclave as usual by calling `EnclaveHost.load` inside your
test class.

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
to be loaded with `EnclaveHost.load`. By default, this will run the tests in a simulated SGX environment and will require
the tests to be executed within Linux. In addition, testing on real hardware will require the tests to be executed within
Linux on a system that supports SGX.

```java
@EnabledOnOs(OS.LINUX)
public class NativeTest {
    private static EnclaveHost enclave;

    @BeforeAll
    static void startup() throws EnclaveLoadException {
        enclave = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
        enclave.start(new AttestationParameters.DCAP(), null, null);
    }
}
```

You'll notice that we annotated the test class with `@EnabledOnOs(OS.LINUX)`. This is from
[JUnit 5](https://junit.org/junit5/docs/current/user-guide/#writing-tests-conditional-execution-os) and it will make sure
the native test isn't run on non-Linux environments.

The tests can use any enclave mode: release, debug, simulation or mock. Therefore, if you want to run your tests on
a non-Linux system then you can configure your tests to depend on a mock mode enclave instead. In this case, remove the
`@EnabledOnOs(OS.LINUX)` annotation from the above code.

!!! note
    Running your integration tests in mock mode is very similar to
    [Integrating enclave tests in your host project](#integrating-enclave-tests-in-your-host-project). In both cases
    the enclave code is loaded and run fully in memory. However, for integration tests, you can also choose to run
    the tests in modes other than mock, which is not possible for enclave project tests.

Running

```
./gradlew host:test
```

will execute the test using a simulation enclave, or not at all if the OS is not Linux. You can switch to a debug enclave
and test on real secure hardware by using the `-PenclaveMode` flag:

```
./gradlew -PenclaveMode=debug host:test
```

Or you can use a mock enclave and test on a non-Linux platform by removing `@EnabledOnOs(OS.LINUX)` and by running this
command:

```
./gradlew -PenclaveMode=mock host:test
```

!!! tip
    To run the tests in a simulated SGX environment on a non-Linux machine you can use Docker, which manages Linux
    VMs for you. See the [system requirements](enclave-modes.md#system-requirements) and instructions
    for [compiling and running the host](running-hello-world.md#beyond-mock-mode) for more 
    information.
