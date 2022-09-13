# Intel Attestation Service (EPID attestation only)

The Intel Attestation Service (IAS) helps you to verify that a remote computer isn't physically compromised and has the 
latest security patches. IAS is used in the context of EPID attestation protocol. DCAP, the
more recent attestation protocol, follows a different attestation flow

IAS evaluates evidence from an enclave and returns:

1. The time at which the IAS made the assessment.
2. If the system is secure or if it needs:
   1. Software upgrades
   2. Operating System or microcode upgrades
   3. BIOS configuration changes
3. The [CVE IDs](https://en.wikipedia.org/wiki/Common_Vulnerabilities_and_Exposures#CVE_identifiers) of 
   relevant security advisories.

As Intel has signed the response, you can send it and use it everywhere. 

For example, when an enclave starts in debug or release mode, it contacts the IAS to calculate the attestation. This 
attestation is a part of a serialized
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object and 
can be passed to the clients. The clients need not contact the IAS separately.

## Getting access

You need an authorized key to access the IAS. This key is different from the enclave signing key.

To obtain an IAS access key: 

1. [Sign the commercial use agreement](signing.md) as part of getting an enclave signing key. You need to do this 
   well in advance before using the release mode, as the application process can be lengthy. You can run 
   your application in simulation or debug mode without this agreement.
2. Get an Intel developer zone account and sign in to the [IAS API site](https://api.portal.trustedservices.intel.
   com/EPID-attestation).
3. Subscribe to development and production access for EPID Name Base Mode (Linkable Quotes).

!!!Note
    
    Use a group email address instead of an individual id to not miss security advisories from Intel.

When you have access, you should be able to see a Service Provider ID (SPID) and a Primary Key associated with 
your subscription. You can pass these to the enclave when it starts.
```java
enclave.start(
    new AttestationParameters.EPID(OpaqueBytes.parse(SPID), PRIMARY_KEY),
    (commands) -> {
        ...
});
```

!!!Important
    
    [Install the kernel driver and system software](non-cloud-deployment.md#install-the-kernel-driver-and-system-software)
    on your machine before using EPID attestation in debug or release mode.