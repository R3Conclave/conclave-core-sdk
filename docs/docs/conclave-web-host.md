# The Conclave web host

By default, Conclave projects use a built in host module that manages your enclave instance and provides a simple 
REST API to facilitate communication. The web host is sufficient for simple projects, but more complex projects may 
require additional functionality not implemented by the web host. In these cases a custom host can be implemented, 
see [writing your own host](writing-your-own-enclave-host.md) for more information.

!!! warning

    The web host is not suitable for production use for various reasons. For example, mail responses from the enclave 
    which haven'been picked by the client are only kept in memory and are not persisted. A restart of the host 
    means they will be lost.

## Command line interface
The Conclave web host includes several command line parameters which can be used to exercise various features 
(listed below). These options may be passed on the command line as follows:

```bash
java -jar host.jar [options]
```

!!!note
    If executing your project using Gradle, then arguments can be passed using the --args option. For example:
    `./gradlew :host:run --args="--sealed-state-file=<path>"`

### `--sealed.state.file=<path>`
Path at which the enclave should store the sealed state containing the persistent map, if the persistent map is 
enabled. See [enclave persistence](persistence.md) for more information. If the persistent map is not enabled, this 
option has no effect.

### `--filesystem.file=<path>`
Path to encrypted filesystem file. If this is not specified, then the encrypted filesystem will not be used. See 
[enclave persistence](persistence.md) for more information.

### `--kds.url=<url>`
URL of the key derivation service to use.

### `--kds.connection.timeout.seconds=<count-in-seconds>`
Timeout to use when attempting to contact the key derivation service enclave.

## REST API:
The REST API consists of several endpoints, detailed below. When using this API, clients begin an interaction with 
the enclave by fetching an attestation. The client will then use the Conclave SDK to examine the attestation 
information and make a decision whether to proceed (see [enclave-constraints](constraints.md)). If the enclave is 
deemed to be trusted by the client, then an encryption key is derived from the attestation data, and the client can 
continue to interact with the enclave via the host by delivering and receiving encrypted messages using the the 
`/deliver-mail` and `/poll-mail` endpoints. For code examples, see the [hello world sample](https://github.com/R3Conclave/conclave-samples/hello-world).

### `/attestation (GET)`
This endpoint accepts GET requests and returns the serialized attestation data-structure as a block of bytes.

*Response Body:*

Bytes containing serialized attestation data which may be deserialized using `EnclaveInstanceInfo.deserialize()`.

### `/deliver-mail (POST)`
Deliver a mail item to the enclave and retrieve a reply immediately if there is one.

*Special request headers:*

- `Correlation-ID` - Unique ID specified by the client which is used by the enclave to identify the client. This 
  serves as the routing hint passed to the enclave on mail delivery and is used by the enclave to direct mail
  replies back to the appropriate client.

*Request body:*

Serialized enclave mail object to be delivered to the enclave.

*Response body:*

Reply mail from the enclave as an array of bytes, or empty byte array otherwise.

#### Error messages

When an error occurs as a result of a request sent to `/deliver-mail`, for example if a mail item is unable to be 
decrypted, the host will return status code 400 (bad request) and the body of the response will contain information 
regarding the cause of the error.

The error message will be json encoded and will contain fields with the following keys:

- `message` - A message regarding the nature of the error, if appropriate (see below).
- `error` - Name of the error. Possible values:
    - `MAIL_DECRYPTION` - If the enclave was unable to decrypt the mail. This can occur for a few reasons, the most 
      likely reason being the enclave was restarted and the mail encryption key was changed. In this scenario the 
      client needs to re-request attestation from the `/attestation` endpoint, derive a new key from the attestation,
      and then re-encrypt and re-deliver the mail item to the enclave, using the new key.
    - `ENCLAVE_EXCEPTION` - In the event that other enclave errors occur. In this case, the message field will 
      contain the exception message if the enclave is in Mock, Simulation or Debug mode, but will be null in Release 
      mode to prevent potential leakage of information from within the enclave.

In the event of a non Conclave error, a regular 503 (internal server error) response will be generated instead.

### `/poll-mail (POST)`
Retrieve mail from the enclave if there is any to retrieve, otherwise return an empty byte array.

*Special request headers:*

- `Correlation-ID` - Unique ID specified by the client which is used by the enclave to identify the client. This 
  serves as the routing hint passed to the enclave on mail delivery and is used by the enclave to direct mail 
  replies back to the appropriate client.

*Response body:*

Reply mail from the enclave as an array of bytes, or empty byte array otherwise.

!!!warning
    As attestation information is unique to the specific CPU that the enclave is running on, requests to 
    `/deliver-mail` and `/poll-mail` must be sent to the same server as the attestation request. As such, the web host 
    cannot be considered purely RESTful and processes that involve caching or load balancing which assume RESTful 
    behaviour may not function correctly.
