# Intel Attestation Service

!!! important
    You don't need to read and perform these steps to deploy a Conclave app, unless you wish to use older
    hardware that isn't capable of using the DCAP protocol. [Azure Gen 2 Virtual Machines](azure.md) in particular do not
    need any of these steps.

How do you know a remote computer is fully up to date with the latest security patches and isn't physically compromised?
This is the question Intel's Attestation Service (IAS) exists to answer. IAS is an online service that evaluates
evidence from an enclave and returns:

1. The time at which the assessment was made
2. Whether the system is considered secure or whether it needs:
   1. Software upgrades
   2. Operating system / microcode upgrades
   3. BIOS configuration changes
3. The CVE IDs of any active security advisories against the remote system

... along with various other pieces of data. The response is signed by Intel. Because it's signed it can be hosted and
passed around by anyone, thus when an enclave is started in debug or production mode IAS is contacted and an attestation
calculated. It's a part of a serialized [`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html) object and can be passed to clients from the server. This
means clients don't need to contact Intel's servers themselves and thus don't need authorisation to use IAS.

## Getting access

IAS requires an authorised key to access it. This is distinct from the enclave signing key. To obtain an IAS access
key: 

1. Sign the commercial use agreement [as part of getting a whitelisted enclave signing key](signing.md). You can run your application in simulation or debug mode without this, but try to do it well in advance of when you will need to run in release mode, as the application process can be lengthy.
2. Get an Intel developer zone account and sign in [on the IAS API site](https://api.portal.trustedservices.intel.com/EPID-attestation).
3. Subscribe to development and production access for "EPID Name Base Mode (Linkable Quotes)".

!!! tip
    You should give Intel a group email address rather than an individual address, as these will be used to send you
    notification of security advisories affecting your system, so you know to upgrade and the timelines for 
    [TCB recovery](renewability.md).

Once you have access, you should be able to see an SPID and Primary Key associated with your subscription. You can supply these to the enclave when it is started:
```java
enclave.start(
    new AttestationParameters.EPID(OpaqueBytes.parse(SPID), PRIMARY_KEY),
    (commands) -> {
        ...
});
```

!!! important
    Ensure you have
    [installed the kernel driver and system software](machine-setup.md#install-the-kernel-driver-and-system-software)
    on your machine before trying to use EPID attestation in debug or release mode.