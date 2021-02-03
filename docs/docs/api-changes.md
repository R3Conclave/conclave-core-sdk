# API changes

Between beta 4 and 1.0 the API for creating mail has changed. `MutableMail` has been replaced by `PostOffice` which is a
factory for creating encrypted mail. There's no longer any need to manually increment the sequence number as that's done
for you. Instead make sure to only have one instance per sender key and topic. This allows the enclave to check for
dropped or reordered mail. `Mail.decrypt` and `EnclaveInstanceInfo.decryptMail` have been replaced by `PostOffice.decryptMail`.
Decrypt any response mail using the same post office instance that created the request.

Inside the enclave `Enclave.createMail` has been replaced by `Enclave.postOffice` which returns a cached post office for
the destination and topic. This means you don't need to manage post office instances inside the enclave as you do in the
client.

The routing hint parameter in `Enclave.receiveMail` has been moved to the end to make the method signature consistent
with `EnclaveHost.deliverMail`.
