# Known issues

During the beta period the API may change up in backwards incompatible ways. Although we have no current plans to 
change the API, small changes like package names may still occur and we may adapt the API based on user 
feedback during the beta period.

This release ships with the following known issues that we plan to address in future versions:

1. The Mail API will change in future beta releases. 
1. Conclave doesn't presently implement any automatic side channel attack mitigations.
1. Some system level exceptions like divide by zero or using null reference may crash the enclave/host process.
1. Some Java APIs won't currently work due to lack of full operating system access inside the enclave. We plan to fix
   these issues in upcoming betas, to present the enclave with e.g. an empty file system.
1. Mail is limited in size by the size of the enclave heap, and the size of a Java array (2 gigabytes).
1. SubstrateVM builds are not currently reproducible. Avian builds are, however. 
1. There is no filesystem inside the enclave. Future versions of Conclave will provide a POSIX compatible purely
   in memory filesystem, to enhance compatibility with pre-existing code. To load files from the host system, use
   local messaging and have the host pass the loaded data into the enclave, but remember that the host may arbitrarily
   corrupt or modify the contents unless it's tied to some external root of trust, e.g. via a signature.
1. JavaDocs don't integrate with IntelliJ properly. This is due to a bug in IntelliJ when loading modules from
   on disk repositories.