# Intel Attestation Service

!!!Note
    
    You don't need to perform these steps if you use [Azure Gen 2 VMs](azure.md) or hardware that supports the
    DCAP attestation protocol.

Intel's Attestation Service (IAS) helps to verify that a remote computer isn't physically compromised and has the 
latest security patches.

IAS evaluates evidence from an enclave and returns:

1. The time at which the IAS made the assessment.
2. If the system is secure or if it needs:
   1. Software upgrades
   2. OS or microcode upgrades
   3. BIOS configuration changes
3. The [CVE ID](https://en.wikipedia.org/wiki/Common_Vulnerabilities_and_Exposures#CVE_identifiers)s of relevant
security advisories.

You can send and use the response anywhere, as Intel has signed it. When an enclave starts in debug or release mode, 
it contacts the IAS to calculate the attestation. This attestation is a part of a serialized
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object and 
can be passed to clients from the server. This means clients don't need authorization to use the IAS.

## Getting access

You need an authorized key to access the IAS. This key is different from the enclave signing key.

To obtain an IAS access key: 

1. Sign the commercial use agreement [as part of getting an enclave signing key](signing.md). You need to do this 
   well in advance before using the release mode, as the application process can be lengthy. You can run 
   your application in simulation or debug mode without this agreement.
2. Get an Intel developer zone account and sign in[on the IAS API site](https://api.portal.trustedservices.intel.com/EPID-attestation).
3. Subscribe to development and production access for
   [EPID Name Base Mode (Linkable Quotes)](https://api.portal.trustedservices.intel.com/EPID-attestation).

!!!Note
    
    You should give Intel a group email address rather than an individual address, as Intel uses it to send security 
    advisories that affect your system.

When you have access, you should be able to see an SPID and a primary key associated with your subscription. You can
pass these to the enclave when it starts.
```java
enclave.start(
    new AttestationParameters.EPID(OpaqueBytes.parse(SPID), PRIMARY_KEY),
    (commands) -> {
        ...
});
```

!!!Important
    
    [Install the kernel driver and system software](machine-setup.md#install-the-kernel-driver-and-system-software)
    on your machine before trying to use EPID attestation in debug or release mode.