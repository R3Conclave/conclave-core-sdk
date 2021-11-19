# Writing your own enclave host

Conclave projects will typically consist of three types of module; clients, hosts and enclaves. A simple project might consist of a single host, enclave and client. Host modules are responsible for instantiating enclaves, persisting data to disk, and passing messages between enclaves and their clients. Most of this functionality is managed internally by Conclave and isn't something that the user has to worry about.

By default, Conclave projects will use a built-in web host that manages these details for you and allows communication with the enclave via a REST API sufficient for simple use cases (see [conclave web host](conclave-web-host.md) for more information). If the default web host does not suit the needs of your project however, then a custom host can be implemented. The following section contains an example client and host based on the hello world sample bundled with the SDK.

## Project Setup

Start by creating a new Conclave project template using [conclave-init](conclave-init.md).

```bash
java -jar <path to sdk>/tools/conclave-init.jar --package=com.example.tutorial --enclave-class-name=MyEnclave --target=<your project directory>
cd <your project directory>
```

Then implement your enclave. For the purposes of this tutorial, we will use the simple `ReverseEnclave` from the hello world sample (bundled with the SDK). This enclave simply receives an encrypted string via Conclave mail, computes its reverse, and then returns the encrypted result via Conclave mail back to the host. In real world applications, your enclave should implement any processes within your application which are required to process confidential data.

***enclave/src/main/java/com/example/tutorial/enclave/MyEnclave.java:***
```java
package com.example.tutorial.enclave;

public class MyEnclave extends Enclave {
    private static String reverse(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = input.length() - 1; i >= 0; i--) {
            builder.append(input.charAt(i));
        }
        return builder.toString();
    }

    @Override
    protected void receiveMail(EnclaveMail mail, String routingHint) {
        final String stringToReverse = new String(mail.getBodyAsBytes());
        final byte[] reversedEncodedString = reverse(stringToReverse).getBytes();
        final byte[] responseBytes = postOffice(mail).encryptMail(reversedEncodedString);
        postMail(responseBytes, routingHint);
    }
}
```

Next, update the host build.gradle so that it uses the newly created main class:

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
package com.example.tutorial.host;

public class MyEnclaveHost {
    public static void main(String[] args) throws Exception {

    }
}
```

Then configure the host build.gradle to use the newly created main class:

***host/build.gradle (application)***

```groovy hl_lines="2"
application {
    mainClassName = "com.example.tutorial.host.MyEnclaveHost"
}
```

***host/build.gradle (dependencies)***
```groovy hl_lines="2-4"
dependencies {
    runtimeOnly project(path: ":enclave", configuration: mode)
    runtimeOnly "org.slf4j:slf4j-simple:1.7.30"
    implementation "com.r3.conclave:conclave-host:$conclaveVersion"
}

```

Next, remove the client code provided by conclave-init, and create a blank main class like so:

***client/src/main/java/com/example/tutorial/client/Main.java***
```java
package com.example.tutorial.client;

class MyEnclaveClient {
    public static void main(String[] args) throws Exception {

    }
}
```

Finally, check that the host and client run without any issues:

```bash
./gradlew :host:run
./gradlew :client:run
```

## Host Initialisation

Now that the project modules have been set up, we can start implementing functionality. The Conclave SDK provides an API for querying Conclave support on the system at runtime. The following example code makes use of that API to log the status of SGX support on the system. It will also attempt to enable SGX through software if it is possible to do so. This platform support check is optional.

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Print platform support and attempt to software enable if possible
if (EnclaveHost.isHardwareEnclaveSupported()) {
    System.out.println("This platform supports enclaves in simulation, debug and release mode.");
} else if (EnclaveHost.isSimulatedEnclaveSupported()) {
    System.out.println("This platform does not support hardware enclaves, but does support enclaves in simulation.");
    System.out.println("Attempting to enable hardware enclave support...");
    try {
        EnclaveHost.enableHardwareEnclaveSupport();
        System.out.println("Hardware support enabled!");
    } catch (PlatformSupportException e) {
        System.out.println("Failed to enable hardware enclave support. Reason: ${e.message}");
    }
} else {
    System.out.println("This platform supports enclaves in mock mode only.");
}
```

The next step is to load our enclave. In this case we have only a single enclave, so we can load it like this:

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Load the enclave
EnclaveHost enclave = EnclaveHost.load();
```

!!!tip
    In projects containing multiple enclave modules, the enclave to load can be specified by passing the fully qualified class name like so:

    ```java
    // Load the enclave
    EnclaveHost enclave = EnclaveHost.load("com.example.tutorial.enclave.MyEnclave");
    ```

After loading the enclave, we start it up. When starting the enclave, a callback is passed which the enclave will use to deliver replies to any message it receives. In the case of this example, we store these replies in a queue for later:

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Start the enclave, providing a callback for any replies
Queue<byte[]> enclaveResponses = new LinkedList<>();
try {
    enclave.start(new AttestationParameters.DCAP(), null, null, (commands) -> {
        for (MailCommand command : commands) {
            if (command instanceof MailCommand.PostMail) {
                final byte[] bytes = ((MailCommand.PostMail) command).getEncryptedBytes();
                enclaveResponses.add(bytes);
            }
        }
    });
} catch (EnclaveLoadException e) {
    throw new RuntimeException("Failed to start enclave!", e);
}
```

!!!note
    In the case of this tutorial, the host only services a single client connection. In cases where multiple clients may connect however, care must be taken to ensure that replies are sent back to the right clients.

## Host-Client communication

One of the main responsibilities of the host is to pass messages between clients and enclaves. In this case we will use plain TCP sockets, though in principal any communication channel may be used.

**Host:**

Open a socket and wait for a client to connect:

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Open a socket
final int port = 9999;
ServerSocket serverSocket = new ServerSocket(port);

// Wait for client to connect
System.out.println("Listening on port " + port + ". Use the client app to send strings for reversal.");
Socket socket = serverSocket.accept();
DataOutputStream toClient = new DataOutputStream(socket.getOutputStream());
DataInputStream fromClient = new DataInputStream(socket.getInputStream());
```

**Client:**

Initiate a connection with the host:

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Connect to the host
final int port = 9999;
Socket socket = new Socket();
System.out.println("Connecting to host on localhost:" + port);
socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
DataInputStream fromHost = new DataInputStream(socket.getInputStream());
DataOutputStream toHost = new DataOutputStream(socket.getOutputStream());
```

## Attestation

Once communication between the client and host has been established, the next step is attestation. Attestation is the process by which clients ensure the identity of the enclave with which they are communicating, and is a core concept which must be understood in order to write secure applications using the Conclave SDK.

During attestation, the host first generates attestation data for the enclave in question. Then the attestation is sent to the client for interrogation. This data can be thought of as similar to an SSL certificate in that it consists of a set of facts signed by a trusted third party (in this case, the CPU manufacturer). In this case however, it verifies the identity of a specific enclave.

!!!note
    In addition to serving as proof of enclave identity, the attestation data is also used by the client to encrypt messages such that that may only be decrypted by the corresponding enclave.

**Host:**

In this case, our project contains only one enclave, so we simply generate attestation data for that enclave and send it directly to the client as soon as they connect:

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Build the attestation data-structure and send it to the client.
final EnclaveInstanceInfo attestation = enclave.getEnclaveInstanceInfo();
final byte[] attestationBytes = attestation.serialize();
toClient.writeInt(attestationBytes.length);
toClient.write(attestationBytes);
toClient.flush();
```

**Client:**

On the client side, we first receive and deserialize the attestation data:

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Retrieve attestation data from the host
byte[] attestationBytes = new byte[fromHost.readInt()];
fromHost.readFully(attestationBytes);
EnclaveInstanceInfo attestation = EnclaveInstanceInfo.deserialize(attestationBytes);

// Print attestation data in human readable format
System.out.println(attestation);
```

!!!note
    In this case, we serialize byte arrays by transmitting their size followed by the data they contain. This pattern is repeated throughout this tutorial. In principle however, any method of serialization can be used.

Then we check that the attestation conforms to our constraints:

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Check the attestation to ensure that this is the enclave we are after
// Accept an enclave with:
// S:000000.... - Allow the following signing key (signing key hash)
// PROD:1 - Allow the following product ID (1..65535)
// SEC:SECURE - Allow the following security level or better (INSECURE|STALE|SECURE)
EnclaveConstraint.parse(
        "S:0000000000000000000000000000000000000000000000000000000000000000 " +
        "PROD:1 " +
        "SEC:INSECURE"
).check(attestation);
```

The constraints are specified using a simple string representation and the check will fail with an exception if the specified constraints are not met. In this case the constraints are as follows:

* `S:0000000...` - Allow enclaves with a signing key hash of zero (all mock enclaves).
* `PROD:1` - Allow only enclaves with a product ID of 1.
* `SEC:INSECURE` - Allow enclaves with a security level of INSECURE or higher.

!!!note
    These constraints will only work in mock mode and do not provide any security guarantees. The process for selecting these parameters in a general way is beyond the scope of this tutorial, and will not be detailed here. For more information, please see [here](writing-hello-world.md#constraints).

## Enclave-Client Communication

Once communication has been established with the host and the client has confirmed the identity of the enclave, encrypted messages may be exchanged between the client and enclave using the Conclave mail API.

**Client:**

First we retrieve a string to reverse from the command line:

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Get a string to reverse from the command line
String stringToReverse;
if (args.length == 0) {
    stringToReverse = "Hello world!";
} else {
    stringToReverse = String.join(" ", args);
}
```

Then we create a random private key and a "PostOffice" object for use in message encryption:

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Create a random private key and a post office object to use for encrypting messages
PrivateKey myKey = Curve25519PrivateKey.random();
PostOffice postOffice = attestation.createPostOffice(myKey, "string-reversal");
```

!!!note
    In this case, a random key is used. However, if the key is stored and re-used, then the corresponding public key may be used by the enclave to identify specific clients.

Finally, we use the post office instance to encrypt our string, then we transmit it to the host:

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Create an encrypted Conclave mail object containing the string we want to reverse
// then send it to the host.
byte[] encryptedMailOut = postOffice.encryptMail(stringToReverse.getBytes(StandardCharsets.UTF_8));
toHost.writeInt(encryptedMailOut.length);
toHost.write(encryptedMailOut);
toHost.flush();
```

**Host:**

The host then receives the encrypted mail object (which only the attested to enclave can decrypt), which passes it on to the enclave.

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Receive encrypted mail from the client (containing encrypted string to reverse)
// and pass it on to the enclave for reversal.
byte[] encryptedMailIn = new byte[fromClient.readInt()];
fromClient.readFully(encryptedMailIn);
enclave.deliverMail(encryptedMailIn, "");
```

When deliverMail is called above, the deliverMail method in the previously defined enclave will be called. The deliverMail method will then call postMail, which will trigger the enclave callback and deposit the response in the enclaveResponses queue. Next, we read the queue, and send the response back to the client.

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Return the encrypted reversal result to the client
byte[] encryptedMailOut = enclaveResponses.remove();
toClient.writeInt(encryptedMailOut.length);
toClient.write(encryptedMailOut);
toClient.flush();
```

***Client:***

Back on the client side, we receive the encrypted result, decrypt it using the post office object and print out the result:

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Receive reply from the host
byte[] encryptedMailIn = new byte[fromHost.readInt()];
fromHost.readFully(encryptedMailIn);
EnclaveMail decryptedReply = postOffice.decryptMail(encryptedMailIn);
final String reversedString = new String(decryptedReply.getBodyAsBytes());

// Print out the results
System.out.println();
System.out.println("Sent: " + stringToReverse);
System.out.println("Received: " + reversedString);
```

## Termination and Cleanup

Finally, clean up any open resources:

**Host:**

***host/src/main/java/com/example/tutorial/host/MyEnclaveHost.java:***
```java
// Close connection to client and shut down the enclave
toClient.close();
fromClient.close();
socket.close();
serverSocket.close();
enclave.close();
```

**Client:**

***client/src/main/java/com/example/tutorial/client/MyEnclaveClient.java:***
```java
// Close connection to host
toHost.close();
fromHost.close();
socket.close();
```

The application can then be used like so, and should reverse any string passed to is:

```bash
# Terminal 1
./gradlew :host:run
# Terminal 2
./gradlew :client:run --args="String to reverse"
```
