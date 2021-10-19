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

Create or modify a file called `settings.gradle` in your project root directory, so it looks like this:

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
conclaveVersion=1.2-SNAPSHOT
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
We intend to use `conclave-web-host`, so no coding is required for the `host`. Simply create an empty `host` project and
`build.gradle` with the following content:

```groovy
plugins {
    id 'application'
}

application {
    mainClassName = "com.r3.conclave.host.web.EnclaveWebHost"
}

// Override the default (mock) with -PenclaveMode=
def mode = findProperty("enclaveMode")?.toString()?.toLowerCase() ?: "mock"

dependencies {
    runtimeOnly project(path: ":enclave", configuration: mode)
    // Use the host web server for receiving and sending mail to the clients.
    runtimeOnly "com.r3.conclave:conclave-web-host:$conclaveVersion"

    // Enable unit tests
    testImplementation "com.r3.conclave:conclave-host:$conclaveVersion"
    testImplementation "org.junit.jupiter:junit-jupiter:5.6.0"
}
```

!!! tip
    Don't worry if you see the error `Could not resolve project :enclave`. This will be resolved when
    we configure the enclave module in the next section.

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

The client module is the simplest of all. This is literally a bog-standard hello world command line app Gradle build,
with a single dependency on the Conclave client library:

```groovy
plugins {
    id 'application'
}

application {
    mainClassName = "com.r3.conclave.sample.client.Main" // CHANGE THIS
}

dependencies {
    implementation "com.r3.conclave:conclave-client:$conclaveVersion"
    implementation "org.apache.httpcomponents:httpclient:4.5.13"
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

    private byte[] reverse(byte[] bytes) {
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++)
            result[i] = bytes[bytes.length - 1 - i];
        return result;
    }

    @Override
    protected void receiveMail(long id, EnclaveMail mail, String routingHint) {
        // This is used when the host delivers a message from the client.
        final byte[] reversed = reverse(mail.getBodyAsBytes());
        // Get the post office object for responding back to this mail and use it to encrypt our response.
        final byte[] responseBytes = postOffice(mail).encryptMail(reversed);
        postMail(responseBytes, routingHint);
    }
}
```

The `Enclave` class by itself doesn't require you to support direct communication with the host. This is because
sometimes you don't need that and shouldn't have to implement message handlers. In this case we'll use that
functionality because it's a good place to start learning, so we also override and implement the `receiveMail`
method which takes a byte array and optionally sends a byte array back via `postMail`. Here we just reverse the contents.

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

## Remote attestation

There's no point in using an enclave to protect purely local data, as the data must ultimately come from the
(assumed malicious/compromised) host in that scenario. That's why you need remote attestation, which lets an enclave
prove its identity to the third parties who will upload secret data. If this paragraph doesn't make
sense please review the [Architecture overview](architecture.md) and the [Enclaves](enclaves.md) section.

Before client can set up communication with an enclave, we must get remote attestation working.

Using remote attestation is easy! Just obtain an `EnclaveInstanceInfo` via `/attestation` endpoint and validate it.
Here is an example of how to do that:
```java
    private EnclaveInstanceInfo getEnclaveInstanceInfo() throws Exception {
        HttpResponse response = httpClient.execute(new HttpGet(domain + "/attestation"));
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() != HttpStatus.SC_OK)
            throw new IOException(status.toString());

        int len = (int)response.getEntity().getContentLength();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
        response.getEntity().writeTo(baos);

        byte[] attestationBytes = baos.toByteArray();
        EnclaveInstanceInfo attestation = EnclaveInstanceInfo.deserialize(attestationBytes);

        // Three distinct signing key hashes can be accepted.
        // Release mode:            360585776942A4E8A6BD70743E7C114A81F9E901BF90371D27D55A241C738AD9
        // Debug/Simulation mode:   4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4
        // Mock mode:               0000000000000000000000000000000000000000000000000000000000000000
        EnclaveConstraint.parse("S:360585776942A4E8A6BD70743E7C114A81F9E901BF90371D27D55A241C738AD9 "
             + "S:4924CA3A9C8241A3C0AA1A24A407AA86401D2B79FA9FF84932DA798A942166D4 "
             + "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE").check(attestation);
        return attestation;
    }

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
or `INSECURE`. An assessment of `STALE` means there is a software/firmware/microcode update available for the platform
that improves security in some way. The client may wish to observe when this starts being reported and define a
time span in which the remote enclave operator must upgrade.

We can send the serialized bytes to a client via whatever network mechanism we want. The bytes are essentially a large,
complex digital signature, so it's safe to publish them publicly. For simplicity in this tutorial we are just going to
copy them manually and hard-code them in the client, but more on that [later](#writing-the-client).

An attestation doesn't inherently expire but because the SGX ecosystem is always moving, client code will typically have
some frequency with which it expects the host code to refresh the `EnclaveInstanceInfo`. At present this is done by
stopping/closing and then restarting the enclave.

## Configure attestation
At the time of writing we only support DCAP attestation.
1. Azure DCAP. The _datacenter attestation primitives_ protocol is newer and designed for servers. When running on a
   Microsoft Azure Confidential Compute VM or Kubernetes pod, you don't need any parameters. It's all configured out of
   the box.
2. Generic DCAP. When not running on Azure, you need to obtain API keys for Intel's PCCS service.

!!! info
    Why does Conclave need to contact Intel's servers? It's because those servers contain the most up to date information
    about what CPUs and system enclave versions are considered secure. Over time these servers will change their
    assessment of the host system and this will be visible in the responses, as security improvements are made.  

## Run what we've got so far

Now everything should be ready to run the host from the command line (replace enclave class name with your enclave class name).
```bash
./gradlew host:run --args="--enclave.class=com.r3.conclave.sample.enclave.ReverseEnclave --sealed.state.file=/tmp/hello-world-sealed-state"
```
or
```bash
./gradlew clean host:installDist
./host/build/install/host/bin/host --enclave.class=com.r3.conclave.sample.enclave.ReverseEnclave --sealed.state.file=/tmp/hello-world-sealed-state
```
!!! note
    `--sealed.state.file` specifies where the host would store a sealed state. See [Mail Commands](#mail-commands) for details.

Expect to see the Spring Boot logo:
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
./gradlew host:run --args="--enclave.class=com.r3.conclave.sample.enclave.ReverseEnclave --sealed.state.file=/tmp/hello-world-sealed-state"
```

## Encrypted messaging

The enclave isn't of much use to the host, because the host already trusts itself. It's only useful to remote clients
that want to use the enclave for computation without having to trust the host machine or software.

We're now going to wire up encrypted messaging. Conclave provides an API for this called Mail. Conclave Mail handles all the
encryption for you, but leaves it up to you how the bytes themselves are moved around. You could use a REST API, a gRPC
API, JMS message queues, a simple TCP socket as in this tutorial, or even files.

!!! info
    [Learn more about the design of Conclave Mail](architecture.md#mail), and [compare it to TLS](mail.md).

### Receiving and posting mail in the enclave

The new part is `receiveMail`.

This method has a routing hint string parameter. It's provided by the host and it helps the host route replies when
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
enclave.start(new AttestationParameters.DCAP(), null, (commands) -> {
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
it could deliver it to the wrong place. However, if it does deliver it wrongly, the encryption will ensure the
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
enclave.deliverMail(mailBytes, "routingHint");
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

The *routing hint* parameter is an arbitrary string that can be used to identify the sender of the mail from the host's
perspective, e.g. a connection ID, username, identity - it's up to you. The enclave can use this string to
signal to the host that a mail should go to that location. It's called a "hint" to remind you that the host code may
be modified or written by an attacker, so the enclave can't trust it. However, the encryption on the mail makes it
useless for the host to mis-direct mail.

!!! todo
    In future we will provide APIs to bind enclaves to common transports, to avoid this sort of boilerplate.

### Mail commands

The third parameter to `EnclaveHost.start` is a callback which returns a list of `MailCommand` objects from the enclave.
There are two commands the host can receive:

1. **Post mail**. This is when the enclave wants to send mail over the network to a client. The enclave may provide a
   routing hint with the mail to help the host route the message. The host is also expected to safely store the message
   in case the enclave is restarted. If that happens then it needs to redeliver all the (unacknowledged) mail back to
   the enclave in order.
2. **Store sealed state**. This is an encrypted blob which contains the enclave's state that needs to be persisted
   across restarts. The host must store this blob in the same transaction alongside the posting of mail. On restart
   the most recent sealed state blob must be passed into the `EnclaveHost.start`. Failure to do this will result in
   the enclave's client detecting a state roll back.

The host receives these commands grouped together within the scope of a single `EnclaveHost.deliverMail` or `EnclaveHost.callEnclave`
call. This allows the host to add transactionality when processing the commands. So for example, the delivery of the mail
from the client to the enclave and the subsequent reply back can be processed atomically within the same database transaction
when the host is providing persistent, durable messaging.

## Writing the client

The client app will do three things:

1. Connect to the host server and download the `EnclaveInstanceInfo` from it.
1. Verify the enclave is acceptable: i.e. that it will do what's expected.
1. Send it the command line arguments as a string to reverse and get back the answer, using encrypted mail.

The WebHost REST API includes the following three endpoints:
* `/attestation` - get attestation info
* `/deliver-mail` - deliver mail to enclave
* `/inbox` - retrieve mail posted by enclave

Here is a simple client which communicates with the web host using its REST API.
```java
/**
 * Simple client to interact with enclave using REST API.
 *
 * Note: This is experimental code and as such is subject to change.
 * Note 2: The current implementation does not support HTTPS protocol.
 *         You might set up a "HTTPS To HTTP Reverse Proxy" as a workaround.
 */
public class Client {

    /**
     * Client needs to know where the WebHost is
     * and where to store the mail state.
     *
     * @param domain        points to running WebHost (i.e. http://my.web.host:8080)
     * @param mailStateFile this is where the mail state (including mailSequenceNumber) is stored
     *
     * @throws IllegalArgumentException if `domain` string violates RFC 2396
     */
    public Client(String domain, String mailStateFile) throws IllegalArgumentException;

    /**
     * Retrieves enclave attestation info and validates it.
     *
     * @throws IOException             thrown if host is not available or there is a problem with mail state file
     * @throws InvalidEnclaveException thrown if there is a problem with attestation
     */
    public void connect() throws IOException, InvalidEnclaveException;

    /**
     * Delivers message to an enclave, the reply (if any) will go into a specified mailbox.
     *
     * @param correlationId _uniquely_ identifies a mailbox
     * @param message       bytes to be sent
     * @throws IOException thrown if there is a problem communicating with the host
     *                     or with the enclave
     *                     or HTTP response code is not 200
     */
    public void deliverMail(String correlationId, byte[] message) throws IOException;

    /**
     * The client won't be notified of any new messages from the enclave,
     * you have to call `checkInbox` to get the messages.
     * All messages pulled will be removed from the mailbox.
     *
     * @param correlationId _uniquely_ identifies a mailbox
     * @return retrieved mail as list of EnclaveMail objects
     * @throws Exception thrown if there is a problem communicating with the host
     *                   or HTTP response code is not 200
     */
    public List<EnclaveMail> checkInbox(String correlationId) throws IOException;

    /**
     * Saves mail state (if mail state file is specified)
     * Close HTTP connected.
     *
     * @throws IOException
     */
    public void close() throws IOException;
}
```

And here is how you might use this `Client`:
```java
public static void main(String[] args) throws Exception {
    Client client=new Client("http://127.0.0.1:8080",null);
    client.connect();
    System.out.println(client);
    client.close();
}
```
!!! note
    In order to resume a mail session after restart, the client has to persist the mail state. The second parameter of the client constructor
    is a filename where the mail state will be stored. The mail state includes values required for `PostOffice` (private key, topic, nextSeqN, stateId).
    You *have to* call `close()` for mail state to be saved into the file.

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
constraint from a configuration file, system property or command line flag. Finally, it uses the `check` method with
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

It is also possible to specify a maximum age for the attestation using the EXPIRE keyword:

```java
EnclaveConstraint.parse(
        "S:5124CA3A9C8241A3C0A51A1909197786401D2B79FA9FF849F2AA798A942165D3 " +
        "PROD:1 SEC:INSECURE " +
        "EXPIRE:P6M2W5D"    // 6 months, 2 weeks, 5 days
)
```

When specified, this will cause the check to fail if the timestamp within the attestation object indicates an
age older than the specified duration. If no period is specified then no expiry check will be applied. The age string
uses the ISO-8601 duration format.

### Keys and mail

The client wants to receive a response from the enclave, and we want that to be encrypted/tamperproofed too. That means it
need a key pair of its own. Conclave uses Curve25519, a state of the art elliptic curve algorithm. For reasons of
implementation robustness and avoidance of side channel attacks, this is the only algorithm supported by Conclave Mail.
If you want to use other algorithms for some reason you would need to implement your own messaging system on top of
host-local calls. Alternatively, use that other algorithm to encrypt/decrypt a Curve25519 private key.

The complexity of dealing with private keys is conveniently hidden inside the sample `Client`. If curious check `buildPostOffice()` method:

```java
private void buildPostOffice() throws IOException {
    /**
     * identityKey = Curve25519PrivateKey.random();
     * topic = UUID.randomUUID().toString();
     * nextMailSequenceN = 0
     */
    State state = ...;
    postOffice = enclaveInstanceInfo.createPostOffice(state.identityKey, state.topic);
    postOffice.setNextSequenceNumber(state.nextMailSequenceN);
}
```

### Sending and receiving mail

The `Client` provides two methods `deliverMail()` and `checkInbox()` wrapping corresponding endpoints of the Web Host REST API.

Here is how you send a message to the enclave:
```java
client.deliverMail(correlationId, input.getBytes(StandardCharsets.UTF_8));
```

And here is how you retrieve incoming messages:
```java
List<EnclaveMail> messages = client.checkInbox(correlationId);
```

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
        enclave.start(new AttestationParameters.DCAP(), null);
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
VMs for you. See the instructions for [compiling and running the host](tutorial.md#running-the-host) for more information.

### End-to-end test on CI

Provided `Main` class demonstrates how you could do end-to-end test:
```java
public static void main(String[] args) throws Exception {
    // edit URI if running web host on a different machine or with different address/port
    Client client = new Client("http://127.0.0.1:8080", "/tmp/hello.mail.state");
    client.connect();
    System.out.println(client);

    String correlationId = "corrId";
    String input = String.join(" ", args);

    client.deliverMail(correlationId, input.getBytes(StandardCharsets.UTF_8));
    List<EnclaveMail> messages = client.checkInbox(correlationId);
    client.close();

    String actual = new String(messages.get(0).getBodyAsBytes());
    System.out.println(actual);

    String expected = reverse(input);
    assert (actual == expected);
}
```

To run this, you have to first start a process serving Web Host REST API:
```bash
./gradlew host:run --args="--enclave.class=com.r3.conclave.sample.enclave.ReverseEnclave --sealed.state.file=/tmp/hello-world-state" &
```
And now you can run the `client`:
```bash
./gradlew client:run --args "reverse me!"
```
Note: since the host is now running in background, you have to stop it manually:
```bash
kill -9 `ps -ef | grep com.r3.conclave.sample.enclave.ReverseEnclave | grep -v grep | tr -s ' ' | cut -d ' ' -f2`
rm -rf /tmp/hello-world-state
```
