# API changes

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
