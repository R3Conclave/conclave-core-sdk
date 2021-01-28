# API changes

Between beta 4 and 1.0 the API for creating mail has changed. `MutableMail` has been replaced by `PostOffice` which is a
factory for creating encrypted mail. There's no longer any need to manually increment the sequence number as that's done
for you. Instead make sure to only have one instance per sender key and topic. This allows the enclave to check for
dropped or reordered mail. `Mail.decrypt` and `EnclaveInstanceInfo.decryptMail` have been replaced by `PostOffice.decryptMail`.
Decrypt any response mail using the same post office instance that created the request.

Inside the enclave `Enclave.createMail` has been replaced by `Enclave.postOffice` which returns a cached post office for
the destination and topic. This means you don't need to manage post office instances inside the enclave as you do in the
client.

Between beta 3 and beta 4 some APIs were changed. The changes are minor and it should only take you a few minutes to
port your application. Additionally the format of mail and serialised `EnclaveInstanceInfo` also changed, so old data
from beta 3 will not work across upgrades. Enclave, host and clients must all be recompiled against the new versions.

## Local calls

The old `EnclaveCall` interface is no longer needed. To receive local calls just override the
[`receiveFromUntrustedHost`](api/com/r3/conclave/enclave/Enclave.html#receiveFromUntrustedHost-bytes-) method on 
[`Enclave`](api/com/r3/conclave/enclave/Enclave.html). 

## Mail delivery

The mail API now lets the host provide a `routingHint`  parameter when delivering mail, which allows the enclave to 
direct the host where replies should go to. For instance, you can set it to a connection ID, user name, etc. This makes
it easier to bind the enclave to the network.

To get code compiling again, add a `String routingHint` as a new second parameter to your override of 
`Enclave.receiveMail`.

## Attestation parameters.

The `EnclaveHost.start` method has been adjusted to take just two parameters, both of which can be null.

The first is an `AttestationParameters` object. Instantiate either an `AttestationParameters.EPID` or 
`AttestationParameters.DCAP` object. The latter should be used on the latest hardware or if using Azure Confidential
Computing VMs. The former is for older hardware, and requires you to obtain [an API key from Intel](ias.md).

To convert existing code, take the first two parameters of `EnclaveHost.start` and pass them to the constructor
of `AttestationParameters.EPID`.
