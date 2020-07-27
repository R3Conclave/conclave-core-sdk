# Known issues

During the beta period the API may change up in backwards incompatible ways. Although we have no current plans to 
change the API, small changes like package names may still occur and we may adapt the API based on user 
feedback during the beta period.

This release ships with the following known issues that we plan to address in future versions:

1. The Mail API will change in future beta releases. 
1. Conclave doesn't presently implement any side channel attack mitigations.
1. Some system level exceptions like divide by zero or using null reference may crash the enclave/host process.
1. The type of attestation used currently requires you to sign up with and be whitelisted by Intel. Future versions
   will implement "DCAP attestation" which will allow the owner of the hardware to whitelist enclaves, not just Intel.
1. [TCB recoveries](renewability.md) invalidate remote attestations, such that enclaves cannot read mail sent to them
   from before the upgrade.
1. The enclave hangs if it's asked to terminate whilst threads are still active inside it.
1. Some Java APIs won't currently work due to lack of full operating system access inside the enclave. We plan to fix
   these issues in upcoming betas, to present the enclave with e.g. an empty file system.
1. Mail is limited in size by the size of the enclave heap, and the size of a Java array (2 gigabytes).
