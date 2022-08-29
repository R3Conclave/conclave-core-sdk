# Writing the sample enclave

## Prerequisites
* Complete the [Running your first enclave](running-hello-world.md) tutorial.

## Introduction

Use this tutorial to:

* Set up a new Conclave project using [Conclave Init](conclave-init.md).
* Modify the files to create an implementation of the Conclave *sample app* which we explored in the
  [previous tutorial](running-hello-world.md).

This tutorial uses [Conclave web host](conclave-web-host.md) to write a Conclave app.

The client is a command line app for simplicity. You can implement your client as a GUI app or integrate it with 
another program.

## Project Setup

To set up a project:

1. Download the Conclave Init tool.
```bash
   wget https://repo1.maven.org/maven2/com/r3/conclave/conclave-init/1.4/conclave-init-1.4.jar -O conclave-init.jar
```
2. Create a new Conclave project using Conclave Init. Note that you don't have to create a project directory beforehand.
```bash
java -jar conclave-init.jar \
    --package com.example.tutorial \
    --enclave-class-name ReverseEnclave \
    --target <your project directory>
```
3. Navigate to your project directory.
```bash
cd <your project directory>
```


## Configure Signing Keys

1. Modify the `enclave/build.gradle` file to specify the signing methods for each build type. You could keep 
   your private key in a file for both debug and release enclaves. To hold your private keys in an offline system or 
   HSM, configure the `enclave/build.gradle` file as:

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
        //  ./gradlew :host:bootJar -PenclaveMode="Release"
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
* Release builds use a private key managed by an external signing process.

2. Copy the `signing` directory in the `hello world`
[sample](https://github.com/R3Conclave/conclave-tutorials/tree/HEAD/hello-world) repository into your project and
update the paths in the enclave `build.gradle`. 

The `signing` directory has the `privateKey` and `externalKey` signing types. See a description below:

| Key Files              | Description                                                                                                                                                                   |
|:-----------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| sample_private_key.pem | A 3072 bit RSA private key that can be used to test the ```privateKey``` signing type                                                                                         |
| external_signing_*.pem | An AES encrypted 3072 bit RSA public/private key pair that can be used to test the ```externalKey``` signing type. The private key can be accessed with the password '12345'. |


Alternatively, you can also [generate your own](signing.md#generating-keys-for-signing-an-enclave) keys.

!!!Important

    Use these sample keys only for the tutorial. Do not use these keys to sign your own enclaves as Intel hasn't
    whitelisted these sample keys.


## Create a new subclass of [`Enclave`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/index.html)

Enclaves have an equivalent to a "main class", similar to standalone programs. This class must be a subclass of 
[`Enclave`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/index.html).

1. Replace the contents of `/enclave/src/.../ReverseEnclave.java` with the following:

```java
package com.example.tutorial.enclave;

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

### Enclave code breakdown

The enclave code has the following methods:

#### Enclave functionality (`reverse` method)

The first method in the sample enclave is the `reverse` function, which reverses a string. When you write your own 
apps, you can replace this method with the business logic to be run on a secure enclave.

#### Enclave communication (`receiveMail` override)

The second method in the enclave implementation overrides the
[`receiveMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-mail.html) method. Messages enter the 
enclave through this method.

The host calls this method to send messages to the enclave and uses the [Conclave mail](architecture.md#mail)
API for encryption and authentication of messages. The enclave in this tutorial uses the
[`postMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/post-mail.html) method to reply to the host. 
The host then sends the encrypted reply to the appropriate client. This tutorial handles transport using the built-in
[conclave web host](conclave-web-host.md), which allows clients to send and receive Mail items via a simple REST API.
You can also use other methods of transport. See [writing your own host](writing-your-own-enclave-host.md)
for an example using plain TCP sockets.

The [`receiveMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/receive-mail.html) method in this example 
works as follows:

1. Extract the body bytes from the Mail object and interpret them as a string.
1. Reverse the string using the `reverse` method.
1. Create a Mail object encrypted to the sender of the received Mail object, containing the reversed string.
1. Use [`postMail`](api/-conclave%20-core/com.r3.conclave.enclave/-enclave/post-mail.html) to send the Mail item 
   containing the reversed result back to the client.


## Remote attestation

Before a client can set up communication with an enclave, it must get its remote attestation object
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html). Depending 
on the host, a client can get the enclave's `EnclaveInstanceInfo` using:
* A downloaded file.
* A REST endpoint, which is used in this tutorial.

The [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) has a
`toString` function that will print out something like this:

```text
Remote attestation for enclave F86798C4B12BE12073B87C3F57E66BCE7A541EE3D0DDA4FE8853471139C9393F:
  - Mode: SIMULATION
  - Code signer: 01280A6F7EAC8799C5CFDB1F11FF34BC9AE9A5BC7A7F7F54C77475F445897E3B
  - Session signing key: 302A300506032B65700321000568034F335BE25386FD405A5997C25F49508AA173E0B413113F9A80C9BBF542
  - Session encryption key: A0227D6D11078AAB73407D76DB9135C0D43A22BEACB0027D166937C18C5A7973
  - Product ID: 1
  - Revocation level: 0

Assessed security level at 2020-07-17T16:31:51.894697Z is INSECURE
  - Enclave is running in simulation mode.
```

The hash in the first line is the *measurement*. This is a hash of the code of the enclave. It includes all
the Java code inside the enclave as a fat-JAR, and all the support and JVM runtime code. This hash changes any time 
you alter the code of your enclave, the version of Conclave in use, or the mode (simulation/debug/release) of the 
enclave. The clients can audit the enclave by comparing the value they get in the
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) against what 
the build process prints out.

!!!Note

    1. You can get all this data using individual getters on the
       [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html). So 
       you don't need to parse the output of `toString`.
    2. You can easily build mock attestations in your tests using `EnclaveInstanceInfo` interface.
    3. When not in simulation mode, the timestamp is signed by Intel.

An instance has a [security assessment](api/-conclave%20-core/com.r3.conclave.common/-enclave-security-info/index.html),
which changes when infrastructure vulnerabilities are discovered (even without any changes in the host or enclave). 
This sample enclave isn't secure yet because it's running in simulation mode. An enclave can be `SECURE`, `STALE`, 
or `INSECURE`. An assessment of `STALE` means a software/firmware/microcode update that improves security is 
available. The client needs to define a time span by which the remote enclave operator must upgrade when the 
security assessment turns `STALE`.

An attestation doesn't expire. As the SGX ecosystem is constantly changes, the client code needs to define a frequency 
with which it expects the host code to refresh the
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html). Currently, 
the host code is refreshed by restarting the enclave.

## Run the host and enclave

To run the host from the command line:

```bash
./gradlew host:bootJar
java -jar host/build/libs/host-mock.jar
```

## Write the client

Replace the client code at `client/src/.../ReverseEnclaveClient.java` with:

```java
package com.example.tutorial.client;

import com.r3.conclave.client.EnclaveClient;
import com.r3.conclave.client.web.WebEnclaveTransport;
import com.r3.conclave.common.EnclaveConstraint;
import com.r3.conclave.common.InvalidEnclaveException;
import com.r3.conclave.mail.EnclaveMail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ReverseEnclaveClient {
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
        }
    }
}
```

To encrypt the response from the enclave to the client, the client needs a public key and a private key.

Conclave abstracts the complexity of dealing with private keys inside `EnclaveClient`. The
`new EnclaveClient(constraint)` line in the above code also creates a new random Curve25519 private key.

To use an existing private key or to manually create a new random key, use the following code:

```java
PrivateKey myKey = Curve25519PrivateKey.random();
EnclaveClient client = new EnclaveClient(myKey, constraint);
```

### Sending and receiving Mail

You can send Mail after connecting to the host web server and verifying the enclave. To send Mail, call 
[`EnclaveClient.sendMail`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/send-mail.html) and send the
serialized bytes of the request message. If the enclave responds back immediately with a Mail of its own, then that 
is returned by `sendMail`.

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

Conclave Mail represents the response from the enclave as an
[`EnclaveMail`](api/-conclave%20-core/com.r3.conclave.mail/-enclave-mail/index.html) object. The Mail body will contain
the encoded reversed string.

!!!Note

    If you write your enclave such that it responds to the client later at some point, you can use the
    [`pollMail`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/poll-mail.html) method to poll for 
    responses. It will return `null` if there aren't any.

## Run the host and client

1. Open a command line window.
2. Navigate to the project directory.
3. Run the host:

```bash
java -jar host/build/libs/host-mock.jar
```

4. Open another command line window.
5. Navigate to the project directory.
6. Build the client:
```bash
./gradlew :client:shadowJar
```
7. Run the client:
```bash
java -jar client/build/libs/client.jar \
  "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE" \
  reverse-me
```

Please refer to [Running your first enclave](running-hello-world.md) for expected output and other information on these
commands.

## Testing

There are two ways to test the enclave: 

1. Use a mock build of the enclave in tests defined as part of your enclave project.
2. Integrate enclave tests in your host project.

### Mock tests within the enclave project

Mock mode allows you to white box test your enclave by running it fully in-memory. As you don't need SGX hardware or a 
specific OS, it's an ideal method for cross-platform unit testing.

Conclave supports building and running tests within the enclave project itself. When you define tests as part of 
your enclave project, Conclave loads the enclave classes along with the tests. Conclave detects this
configuration and automatically enables mock mode for the enclave and test host. You don't need to explicitly
specify [mock mode](mockmode.md) for your project.

To use this functionality, simply create an instance of the enclave as usual by calling
[`EnclaveHost.load`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/load.html) inside your test class.

```java
EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave");
mockHost.start(null, null);
```

Conclave detects the enclave class on the classpath and starts the enclave in mock mode. You
can obtain the enclave instance using the
[`EnclaveHost.mockEnclave`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/get-mock-enclave.html) property.

```java
ReverseEnclave reverseEnclave = (ReverseEnclave)mockHost.getMockEnclave();
```

### Integrating enclave tests in your host project

To test your enclave on real SGX hardware or in a simulated SGX environment, *you need to define your 
tests in a project separate from the enclave project.* A suitable place for your tests is to define them as 
part of the host project tests.

Add the Conclave host as a `testImplementation` dependency in the project where your tests are defined.

```groovy
dependencies {
    testImplementation "com.r3.conclave:conclave-host:$conclaveVersion"
}
```

To load and test the enclave on real hardware or in a simulated SGX environment, you need to load the enclave with 
[`EnclaveHost.load`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/load.html). By default, this runs the 
tests in a simulated SGX environment and allows testing only within Linux. If you configure [`EnclaveHost.load`] to 
test on real hardware, you must test within Linux on an SGX-supported system.

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

The test class is annotated with `@EnabledOnOs(OS.LINUX)`. This is from
[JUnit 5](https://junit.org/junit5/docs/current/user-guide/#writing-tests-conditional-execution-os) to ensure that the
native test is run on a Linux environment.

To execute the test using a simulation enclave, run:

```bash
./gradlew host:test
```

To switch to a debug enclave and test on real secure hardware, use the `-PenclaveMode` flag:

```bash
./gradlew -PenclaveMode=debug host:test
```

To use a mock enclave and test on a non-Linux platform, remove `@EnabledOnOs(OS.LINUX)` and run:

```bash
./gradlew -PenclaveMode=mock host:test
```

!!!Note

    To run the tests in a simulated SGX environment on a non-Linux machine, you can use Docker, which manages Linux
    VMs for you. See the [system requirements](enclave-modes.md#system-requirements) and instructions
    for [compiling and running the host](running-hello-world.md#beyond-mock-mode).
