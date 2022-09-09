# Writing your own enclave host

## Prerequisites

* Complete the [running your first enclave](running-hello-world.md) tutorial.
* Complete the [writing your first enclave](writing-hello-world.md) tutorial.

## Introduction

Conclave projects consist of three modules: the client, the host, and the enclave.

The host is responsible for:

1. Instantiating the enclave.
2. Persisting data to disk.
3. Passing messages between the enclave and its clients.

[Conclave web host](conclave-web-host.md) is a built-in web host that manages these tasks for simple use cases.
You can implement a custom host for complex use cases.

To implement a simple host server using raw sockets:

## Project setup

1. Create a new Conclave project using [Conclave Init](conclave-init.md) and implement your enclave.
2. Create a main class for the new host.
```java
package com.example.tutorial.host;

public class MyEnclaveHost {
    public static void main(String[] args) {

    }
}
```
3. Update the host build.gradle to reference the main class:
```groovy hl_lines="2"
application {
    mainClass.set("com.example.tutorial.host.MyEnclaveHost")
}
```
4. Replace the `runtimeOnly conclave-web-host` dependency with `implementation conclave-host`:
```groovy hl_lines="3"
dependencies {
    runtimeOnly project(path: ":enclave", configuration: mode)
    implementation "com.r3.conclave:conclave-host:$conclaveVersion"
}
```
5. Remove the generated client code and create a blank main class:
```java
package com.example.tutorial.client;

class MyEnclaveClient {
    public static void main(String[] args) {

    }
}
```
6. Replace the `conclave-web-client` dependency with `conclave-client`:
```groovy hl_lines="2"
dependencies {
    implementation "com.r3.conclave:conclave-client:$conclaveVersion"
}
```
7. Check that the host and client run without any issues:
```bash
./gradlew :host:run
./gradlew :client:run
```

## Implementing the client

Write the client first as that will direct how to implement the host.

Use the [`EnclaveClient`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/index.html) class for
managing communication with the enclave. It deals with the encryption of Conclave Mail messages and simplifies enclave
restarts.

You can use the [`EnclaveTransport`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/index.html) 
interface to handle the details of the transport layer to the host.
The [`EnclaveClient.start`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/start.html) method needs an 
implementation of the `EnclaveTransport` class. For example, if the enclave is running behind the
[Conclave web host](conclave-web-host.md), then the client needs to use the
[`WebEnclaveTransport`](api/-conclave%20-core/com.r3.conclave.client.web/-web-enclave-transport/index.html) class.

This sample uses a simple, socket-based `EnclaveTransport`.

```java
public class MyEnclaveTransport implements EnclaveTransport, Closeable {
    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    public SocketEnclaveTransport(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        socket = new Socket(host, port);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}
```
This sample implements `Closeable` on the host and port parameters to allow the caller to close any underlying 
connections.
The `input` and `output` streams are the communication channels with the host server for receiving and sending raw 
bytes.

### [`enclaveInstanceInfo()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/enclave-instance-info.html)

The first `EnclaveTransport` method you need to implement is
[`enclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/enclave-instance-info.html), 
which downloads the latest
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object from the
host:

```java
@NotNull
@Override
public EnclaveInstanceInfo enclaveInstanceInfo() throws IOException {
    output.write(1);
    output.flush();

    byte[] attestationBytes = new byte[input.readInt()];
    input.readFully(attestationBytes);
    return EnclaveInstanceInfo.deserialize(attestationBytes);
}
```

The attestation request is a single-byte value. When the client sends the byte, the `enclaveInstanceInfo()` 
method waits for the server to respond with the attestation bytes. When the method receives the attestation bytes 
from the server,
it [deserializes](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/deserialize.html) the 
attestation bytes into an `EnclaveInstanceInfo` object.

### [`connect()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/connect.html)

Next, you need to implement the methods to send and receive Conclave Mail. The 
[`ClientConnection`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/index.html)
interface defines these methods.
You can create an instance of [`ClientConnection`] using
[`EnclaveTransport.connect`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/connect.html).
Multiple `EnclaveClient` instances can use a single `EnclaveTransport` interface. The `ClientConnection` implementation 
is a private inner class and `connect` will simply return a new instance of one.

```java
@NotNull
@Override
public ClientConnection connect(@NotNull EnclaveClient client) throws IOException {
    return new MyClientConnection();
}

private class MyClientConnection implements ClientConnection {
    @Override
    public void disconnect() {
    }
}
```

The [`disconnect`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/disconnect.html)
method is empty in this simple implementation as there's nothing to do when the `EnclaveClient`
[closes](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/close.html).

### [`sendMail()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/send-mail.html)

To send [encrypted Mail](mail.md) to the host, you need to implement
[`sendMail`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/send-mail.html):

```java
@Nullable
@Override
public byte[] sendMail(@NotNull byte[] encryptedMailBytes) throws IOException, MailDecryptionException {
    output.write(2);
    output.writeInt(encryptedMailBytes.length);
    output.write(encryptedMailBytes);
    output.flush();

    int responseType = input.readByte();
    if (responseType == 1) {
        return readMail();
    } else if (responseType == 2) {
        throw new MailDecryptionException();
    } else {
        throw new IOException("Unknown response type " + responseType);
    }
}
```

The `sendMail()` request has a byte value 2, followed by the size prefix. After sending this request, the client 
waits for a response from the host. The
[`sendMail`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/send-mail.html)
specification states that the method must block and wait for the enclave to process the Mail. If the enclave 
processes the Mail successfully, the client must receive and return any response from the enclave. A response type 1
represents such a success response from the client.

```java
private byte[] readMail() throws IOException {
    int responseMailSize = input.readInt();
    if (responseMailSize > 0) {
        byte[] responseMail = new byte[responseMailSize];
        input.readFully(responseMail);
        return responseMail;
    } else {
        return null;
    }
}
```

If the enclave couldn't decrypt the Mail, the client must throw a
[`MailDecryptionException`](api/-conclave%20-core/com.r3.conclave.mail/-mail-decryption-exception/index.html). A 
response type 2 represents such a response from the client.

### [`pollMail()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/poll-mail.html)

The final method you need to implement is
[`pollMail`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/poll-mail.html)
which is for polling for any extra response Mail the enclave might have created for the client.

```java
@Nullable
@Override
public byte[] pollMail() throws IOException {
    output.write(3);
    output.flush();

    return readMail();
}
```

A single-byte prefix represents the `pollMail()` request. You don't need to send any other parameters. The response 
follows the same path as `sendMail` if it receives a Mail response. So you can reuse the `readMail()` method from 
above.

You can implement `EnclaveTransport` with an
[`EnclaveClient`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/index.html) instance to connect to 
the host.

```java
public static void main(String[] args) throws InvalidEnclaveException, IOException {
    EnclaveClient client = new EnclaveClient(EnclaveConstraint.parse(args[0]));
    MyEnclaveTransport enclaveTransport = new MyEnclaveTransport("localhost", 8000);
    enclaveTransport.start();
    client.start(enclaveTransport);
    // Send and receive mail
}
```

You need to implement the corresponding logic on the host to receive and process the requests.

## Implementing the host

### Loading the enclave

[Load the enclave](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/load.html) by scanning the classpath.

```java
public class MyEnclaveHost {
    private static EnclaveHost enclaveHost;
    
    public static void main(String[] args) throws EnclaveLoadException, IOException {
        enclaveHost = EnclaveHost.load();
    }
}
```

!!!Note

    In projects containing multiple enclave modules, you can specify the enclave to load by using the fully qualified
    class name:
    ```java
    enclaveHost = EnclaveHost.load("com.example.tutorial.enclave.MyEnclave");
    ```

### Starting the enclave

Start the enclave by calling [`EnclaveHost.start`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/start.html).

```java
Path hostDir = Paths.get(args[0]);
String kdsUrl = args[1];

enclaveStateFile = hostDir.resolve("enclave.state");
Path enclaveFileSystemFile = hostDir.resolve("enclave.fs");

byte[] sealedState;
if (Files.exists(enclaveStateFile)) {
    sealedState = Files.readAllBytes(enclaveStateFile);
} else {
    sealedState = null;
}
```

In this tutorial, you can pass the parameters of `EnclaveHost.start` method from the command line. The first 
parameter is a reference to the host directory, which will contain the file for the enclave's encrypted file system 
and a file for the enclave's *sealed state*. This parameter is optional.

!!! Note

    The enclave's sealed state should ideally be stored in a database and committed as part of the same transaction 
    that processes outbound Mail from the enclave. This is why the sealed state parameter is a byte array and not a 
    file path. You can find more information about this [here](persistence.md).

The second command line parameter is for the URL to the [Key Derivation Service (KDS)](kds-configuration.md). You 
can leave out this parameter if your enclave doesn't use KDS.

Call `enclaveHost.start`:

```java
enclaveHost.start(
        new AttestationParameters.DCAP(),
        sealedState,
        enclaveFileSystemFile,
        new KDSConfiguration(kdsUrl),
        (commands) -> {
            try {
                processMailCommands(commands);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
);

System.out.println(enclaveHost.getEnclaveInstanceInfo());
```

The last `start` parameter is a callback lambda for processing
[Mail commands](api/-conclave%20-core/com.r3.conclave.host/-mail-command/index.html) from the enclave. The commands 
come from the enclave grouped together in a list after every
[`callEnclave`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/call-enclave.html) or
[`deliverMail`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/deliver-mail.html) call.

When the enclave starts, the host logs the
[enclave's attestation report](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/get-enclave-instance-info.html) 
to the console. You can use these logs for debugging and choosing the enclave constraint when running the client. 

You can find a detailed explanation of the `start` parameters in the
[API docs](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/start.html). 

### Accepting the client connection

Now the enclave is ready to receive Mail. For this, you need to set the host to listen on a port for a client to 
connect to. You can do this by passing a server port from the command line:

```java
int serverPort = Integer.parseInt(args[2]);
ServerSocket serverSocket = new ServerSocket(serverPort);
System.out.println("Listening on port " + serverPort);
```

### Implementing the request loop

Next, set up the request loop:

```java
Socket clientSocket = serverSocket.accept();
System.out.println("Client connected");

DataInputStream input = new DataInputStream(clientSocket.getInputStream());
DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

while (true) {
    int requestType = input.read();
    if (requestType == -1) {
        System.out.println("Client disconnected");
        break;
    }
    if (requestType == 1) {
        sendAttestation(output);
    } else if (requestType == 2) {
        processInboundMail(input, output);
    } else if (requestType == 3) {
        sendPostedMail(output);
    } else {
        System.err.println("Unknown request type " + requestType);
    }
}

serverSocket.close();
```

!!! Note

    This host implementation accepts only a single client connection. To support multiple concurrent clients, you need
    to make necessary changes to both the host and the client.

The `input` and `output` objects receive and send bytes to the client, respectively. The first thing to do is to block 
and wait for the first byte from the client. You can implement the different methods available depending on the 
request type as given below:

#### Attestation request

The attestation request is straightforward to implement as it's just sending the
[serialized `EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/serialize.html):

```java
private static void sendAttestation(DataOutputStream output) throws IOException {
    byte[] attestationBytes = enclaveHost.getEnclaveInstanceInfo().serialize();
    output.writeInt(attestationBytes.length);
    output.write(attestationBytes);
    output.flush();
}
```

#### Mail request

The next request to implement is [`sendMail`](#sendmail):

```java
private static void processInboundMail(DataInputStream input, DataOutputStream output) throws IOException {
    byte[] mailBytes = new byte[input.readInt()];
    input.readFully(mailBytes);
    try {
        enclaveHost.deliverMail(mailBytes, null);
        sendPostedMail(output);
    } catch (MailDecryptionException e) {
        output.write(2);
        output.flush();
    }
}
```

When the host receives the Mail bytes, the host uses the
[`deliverMail`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/deliver-mail.html) method to deliver the Mail 
bytes to the enclave. 

The enclave needs a *routing hint* parameter to route responses back to clients if there are multiple clients. This
implementation doesn't use a routing hint, as only one client exists.

The `deliverMail` method throws a
[`MailDecryptionException`](api/-conclave%20-core/com.r3.conclave.mail/-mail-decryption-exception/index.html) if the 
enclave cannot decrypt the Mail bytes. You need to notify this exception so that the client can send back a
response value of 2, which the [earlier implementation of `sendMail`](#sendmail) expects.

### Mail commands

Now you need to implement the Mail commands [introduced earlier](#starting-the-enclave) where the call to
`EnclaveHost.start` references a `processMailCommands` method:

```java
private static final Queue<byte[]> postedMail = new LinkedList<>();

private static void processMailCommands(List<MailCommand> commands) throws IOException {
    for (MailCommand command : commands) {
        if (command instanceof MailCommand.PostMail) {
            MailCommand.PostMail postMail = (MailCommand.PostMail) command;
            postedMail.add(postMail.getEncryptedBytes());
        } else if (command instanceof MailCommand.StoreSealedState) {
            MailCommand.StoreSealedState storeSealedState = (MailCommand.StoreSealedState) command;
            Files.write(enclaveStateFile, storeSealedState.getSealedState());
        }
    }
}
```

Mail responses from the enclave go through the
[`PostMail`](api/-conclave%20-core/com.r3.conclave.host/-mail-command/-post-mail/index.html) command. In this 
tutorial, you store the Mail responses in a queue. 

This tutorial also uses the
[`StoreSealedState`](api/-conclave%20-core/com.r3.conclave.host/-mail-command/-store-sealed-state/index.html) command.
This command overwrites the disk with the new sealed state. 

You need to implement `sendPostedMail`, which takes the first response Mail from the queue, and sends it to the client:

```java
private static void sendPostedMail(DataOutputStream output) throws IOException {
    byte[] mailResponse = postedMail.poll();
    output.write(1);
    if (mailResponse != null) {
        output.writeInt(mailResponse.length);
        output.write(mailResponse);
    } else {
        output.writeInt(0);
    }
    output.flush();
}
```

As the `pollMail` request also uses the same logic, the [request loop above](#implementing-the-request-loop) calls
`sendPostedMail` if it receives a request type 3.