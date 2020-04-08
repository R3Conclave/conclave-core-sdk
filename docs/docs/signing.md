Enclaves must be signed in order to be loaded. This section covers:
 
* *Why* is signing required
* *Who* can sign enclaves
* *How* to sign your enclaves

## Why is signing required?

Signing requirements are a part of the Intel SGX architecture. The enclave signature is used for two different purposes:

1. Linking different enclave versions together into an upgrade path, so new enclaves can decrypt data stored by or sent
   to old enclaves.
2. Authorising which enclaves can be executed on a host.

Restricting which enclaves can launch on a host ensures that datacenter providers aren't hosting server processes they 
can't examine or turn over to law enforcement. It also makes it harder for people to write malware with un-debuggable 
cores, and is part of the privacy (anti-tracking) infrastructure in SGX.

Using signatures to link binaries into a single upgrade path is the same technique used by Android and iOS to move
permissions and stored data from old to new apps.

Signing is also used to authorise which enclaves can start. Intel chips won't start an enclave unless it's signed by
a key recognised by a launch approver.
 
## Who can sign
 
On the most common kind of hardware, permission from Intel is required to create a launchable enclave. Getting 
whitelisted is free and can be done quickly. It's a similar process to getting an SSL certificate but using 
different tools.

On Xeon E CPUs with Intel SPS support in the chipset, and a recent enough kernel driver, the owner can add their 
own whitelisting authorities via BIOS/UEFI firmware settings. This means they can whitelist their own 
enclaves / enclave vendors.

Current Conclave versions aren't able to use this capability at present. To run in production (fully secured) mode
you need a whitelisted Intel key. We plan to implement support for the *flexible launch control* feature in future
versions. At this time not much shipping hardware supports it however, so for near term uses you should plan on
getting a whitelisted Intel key.

## How to sign your enclaves

Firstly, [get a commercial license](https://software.intel.com/en-us/sgx/request-license). This is a lightweight
process and doesn't cost anything or impose other requirements. Following the instructions provided on that page 
should allow you to get a signing key.

!!! tip
    It's up to you whether or not to store the key in an HSM.

<!--- TODO: Need to provide details on how to use the Gradle tasks to step through this procedure instead of the SDK -->
