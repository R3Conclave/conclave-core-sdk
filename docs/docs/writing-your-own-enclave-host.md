# Writing your own enclave host

## Prerequisites

This tutorial assumes you have a good understanding of Conclave and at a minimum have gone through the 
[introduction tutorial](writing-hello-world.md).

## Introduction

Conclave projects will typically consist of three modules; the client, the host and the enclave. The host is responsible for 
instantiating the enclave, persisting data to disk, and passing messages between the enclave and its clients.
Conclave provides a built-in web host that manages these details for you and allows communication with the enclave 
via a REST API sufficient for simple use cases (see [Conclave web host](conclave-web-host.md) for more information).

If however this default web host does not suit the needs of your project, a custom host can be implemented instead. The 
following section will outline how to do that by implementing a very basic host server using raw sockets.

## Project setup

Start by creating a new Conclave project using [Conclave Init](conclave-init.md) and implement your enclave.
Conclave Init generates a project which uses the [web host](conclave-web-host.md). We need to replace that with our own 
host. Start by creating a main class:

```java
package com.example.tutorial.host;

public class MyEnclaveHost {
    public static void main(String[] args) {

    }
}
```

Update the host build.gradle to reference it:

```groovy hl_lines="2"
application {
    mainClass.set("com.example.tutorial.host.MyEnclaveHost")
}
```

And replace the `runtimeOnly conclave-web-host` dependency with `implementation conclave-host`:

```groovy hl_lines="3"
dependencies {
    runtimeOnly project(path: ":enclave", configuration: mode)
    implementation "com.r3.conclave:conclave-host:$conclaveVersion"
}
```

Next, remove the generated client code provided and create a blank main class:

```java
package com.example.tutorial.client;

class MyEnclaveClient {
    public static void main(String[] args) {

    }
}
```

Replace the `conclave-web-client` dependency with just `conclave-client`:

```groovy hl_lines="2"
dependencies {
    implementation "com.r3.conclave:conclave-client:$conclaveVersion"
}
```

Check that the host and client run without any issues:

```bash
./gradlew :host:run
./gradlew :client:run
```

## Implementing the client

Now that the project modules have been set up, we can start implementing functionality. Perhaps counterintuitively, 
the first step is to actually write our client, as that will direct how we implement the host.

We recommend clients use the [`EnclaveClient`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/index.html) 
class for managing communication with the enclave. It deals with the encryption of mail messages and amongst other 
things, makes dealing with enclave restarts transparent. The details of the transport layer to the host are dealt 
with by the [`EnclaveTransport`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/index.html) interface. An 
implementation of this is required by [`EnclaveClient.start`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/start.html).
For example, if the enclave is running behind the [Conclave web host](conclave-web-host.md) then the client needs to 
use the [`WebEnclaveTransport`](api/-conclave%20-core/com.r3.conclave.client.web/-web-enclave-transport/index.html) class.

We will write a very simple socket based `EnclaveTransport`. Host and port parameters will be required, and it 
should implement `Closeable` to allow the caller to close any underlying connections.

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

The `input` and `output` streams are our communication channels with the host server for receiving and sending raw 
bytes, respectively.

### [`enclaveInstanceInfo()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/enclave-instance-info.html)

The first `EnclaveTransport` method we'll implement is
[`enclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/enclave-instance-info.html), which 
downloads the latest [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html)
object from the host:

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

We'll represent the attestation request by a single byte value of 1. The byte is sent and the method blocks immediately 
waiting for the server to respond back with the attestation bytes. Once received we
[deserialize](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/deserialize.html) them into an 
`EnclaveInstanceInfo` object.

### [`connect()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/connect.html)

Next we need to implement the methods for sending and receiving mail, but these are not defined in
[`EnclaveTransport`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/index.html) but are 
represented by an interface called
[`ClientConnection`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/index.html).
An instance of this is created by [`EnclaveTransport.connect`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/connect.html)
and represents a logical `EnclaveClient` connection. This allows multiple `EnclaveClient` instances to use a single 
`EnclaveTransport`. Our `ClientConnection` implementation will be a private inner class and `connect` will simply 
return a new instance of one.

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
method is empty as this is a simple implementation and there's nothing to do when the `EnclaveClient`
[closes](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/close.html).

### [`sendMail()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/send-mail.html)

For sending [encrypted mail](mail.md) to the host we need to implement
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

This request is represented by the byte value 2, followed by the size prefix mail bytes. Once that's sent we
immediately block waiting for a response from the host. The
[`sendMail`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/send-mail.html)
specification states the method must block and wait for the mail to be processed by the enclave. If the enclave is 
able to process the mail successfully then any response from it must be received and returned. This is represented 
by the response type 1:

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

If the enclave couldn't decrypt the sent mail then that must also be 
indicated and a [`MailDecryptionException`](api/-conclave%20-core/com.r3.conclave.mail/-mail-decryption-exception/index.html) 
must be thrown instead (which is response type 2).

### [`pollMail()`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/poll-mail.html)

The final method that needs to be implemented is [`pollMail`](api/-conclave%20-core/com.r3.conclave.client/-enclave-transport/-client-connection/poll-mail.html)
which is for polling for any extra response mail the enclave might have created for the client:

```java
@Nullable
@Override
public byte[] pollMail() throws IOException {
    output.write(3);
    output.flush();

    return readMail();
}
```

We follow the same pattern of prefixing the sent bytes with a single byte to represent a polling request. Since 
there are no other parameters that's all that needs to be sent. The response follows the same path as `sendMail` if 
it receives a mail response and so we can reuse the `readMail()` method from above.

And that's it for the client side! We can use our socket `EnclaveTransport` implementation with an
[`EnclaveClient`](api/-conclave%20-core/com.r3.conclave.client/-enclave-client/index.html) instance to connect to the host:

```java
public static void main(String[] args) throws InvalidEnclaveException, IOException {
    EnclaveClient client = new EnclaveClient(EnclaveConstraint.parse(args[0]));
    MyEnclaveTransport enclaveTransport = new MyEnclaveTransport("localhost", 8000);
    enclaveTransport.start();
    client.start(enclaveTransport);
    // Send and receive mail
}
```

Now we need to implement the corresponding logic on the host to receive and process these requests.

## Implementing the host

### Loading the enclave

One of the first things the host does is [load the enclave](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/load.html), 
which by default it does by scanning the classpath.

```java
public class MyEnclaveHost {
    private static EnclaveHost enclaveHost;
    
    public static void main(String[] args) throws EnclaveLoadException, IOException {
        enclaveHost = EnclaveHost.load();
    }
}
```

!!! tip

    In projects containing multiple enclave modules, the enclave to load can be specified by passing the fully qualified class name like so:

    ```java
    enclaveHost = EnclaveHost.load("com.example.tutorial.enclave.MyEnclave");
    ```

### Starting the enclave

The next thing is to start the enclave, which is done by calling
[`EnclaveHost.start`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/start.html). It takes a series of parameters 
which are explained in more detail in the [API docs](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/start.html).
For this tutorial most of these parameters will be passed in from the command line:

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

The first command line parameter is a reference to the host directory which will contain the file for the enclave's 
encrypted file system and also a file for the enclave's "sealed state". Both of these parameters are optional and 
don't need to be specified if it's known the enclave will not use them.

!!! note

    The enclave's sealed state should ideally be stored in a database and committed as part of the same transaction 
    that processes outbound mail from the enclave, which is why the sealed state parameter is a byte array and not a 
    file path. More information about this can be found [here](persistence.md).

The second command line parameter is for the URL to the [key derivation service (KDS)](kds-configuration.md). This
is also optional and can be left out if the enclave is not configured to use a KDS.

We now have enough to call `start`:

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
[mail commands](api/-conclave%20-core/com.r3.conclave.host/-mail-command/index.html) from the enclave. The commands come 
from the enclave grouped together in a list after every
[`callEnclave`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/call-enclave.html) or
[`deliverMail`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/deliver-mail.html) call. We will 
provide a basic implementation of these commands, which we will do shortly.

Once the enclave has started, the host logs the
[enclave's attestation report](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/get-enclave-instance-info.html) 
to the console. This is useful for debugging but also for determining the enclave constraint to use when running the 
client. 

### Accepting the client connection

Now that the enclave is ready to receive mail, our host needs to listen on a port for a client to connect to. We can 
do this by passing in a server port from the command line:

```java
int serverPort = Integer.parseInt(args[2]);
ServerSocket serverSocket = new ServerSocket(serverPort);
System.out.println("Listening on port " + serverPort);
```

### Implementing the request loop

Next we listen for a client connection and setup the request loop:

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

!!! note

    This host implementation only accepts a single client connection. Once that client has disconnected the host 
    also shuts down. The rest of this implementation works off this behaviour. Obviously this is not suitable for
    production and necessary changes to both the host and client will need to be made to support multiple concurrent
    clients.

`input` and `output` will be used to receive and send bytes to the client respectively. The first thing we do is 
block and wait for the first byte from the client. This represents the request type but it's also used to detect if 
the client has disconnected. We'll implement the methods that process these requests below.

#### Attestation request

The attestation request is straightforward to implement as it's just sending the
[serialised `EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/serialize.html):

```java
private static void sendAttestation(DataOutputStream output) throws IOException {
    byte[] attestationBytes = enclaveHost.getEnclaveInstanceInfo().serialize();
    output.writeInt(attestationBytes.length);
    output.write(attestationBytes);
    output.flush();
}
```

#### Mail request

The next request to implement for is [`sendMail`](#sendmail):

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

Once the mail bytes have been received they are [delivered](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/deliver-mail.html)
to the enclave to be decrypted and processed. A second parameter called the "routing hint" is required alongside it. 
This is used by the enclave to correctly route responses back to clients. In this simple implementation there is 
only one client at a time and so the routing hint isn't used. If there were multiple clients then they would each be 
given a unique routing hint.

`deliverMail` will throw a [`MailDecryptionException`](api/-conclave%20-core/com.r3.conclave.mail/-mail-decryption-exception/index.html)
if the enclave could not decrypt the mail bytes. It's important the client be notified of this, so we catch it and 
send back a response value of 2, which is what our [earlier implementation of `sendMail`](#sendmail) expects.

### Mail commands

After `deliverMail` has successfully returned we need to check if the enclave produced a response and if so send it to 
the client synchronously. This is what `sendPostedMail` should do, but before we can implement that we need to go back 
and implement the mail commands first. These were [introduced earlier](#starting-the-enclave) where the call to 
`EnclaveHost.start` referenced a `processMailCommands` method. We can implement this now:

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

Mail responses from the enclave are emitted through the [`PostMail`](api/-conclave%20-core/com.r3.conclave.host/-mail-command/-post-mail/index.html)
command. We store them in a queue for retrieval later. We ignore the
[routing hint](api/-conclave%20-core/com.r3.conclave.host/-mail-command/-post-mail/get-routing-hint.html) here for the same 
reason we didn't use it earlier. To support multiple clients the the routing hint would be used to assign the posted 
mail to the correct client connection.

We also implement the other command, [`StoreSealedState`](api/-conclave%20-core/com.r3.conclave.host/-mail-command/-store-sealed-state/index.html).
The new sealed state is written to disk, overwriting the previous one. 

!!! note

    To reiterate the point from earlier, the mail commands should be actioned inside a transaction such that the 
    delivery of response mail (or at least their storage for later processing) and the storing of the sealed state are 
    done atomically. Storing the response mail in memory is obviously not safe for production!

We can now implement `sendPostedMail`, which simply takes the first response mail from the queue, if one exists, and 
sends it to the client:

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

This also happens to be the logic needed for the `pollMail` request, and so the [request loop above](#implementing-the-request-loop)
also calls `sendPostedMail` if it receives a request type 3.

And that's it for host!
