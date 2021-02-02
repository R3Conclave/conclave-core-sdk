# Renewability and TCB recovery

All tamperproof systems need a way to be re-secured in the field after someone finds a way to breach their security.
This property is called renewability. For Intel SGX renewability is obtained via a process called "TCB recovery". 

## What is a TCB?

The *trusted computing base* is defined as the set of computing technologies that must be working correctly and not be
malicious or compromised for a security system to operate. The larger a TCB is, the more easily can something 
go wrong. In SGX the TCB is very small relative to comparable systems. It consists of:

* The CPU itself, including:
  * The silicon
  * The upgradeable microcode
* The system enclaves like the 'quoting enclave' which produces material for remote attestation.
* The runtime code linked into an enclave that's not direct business logic. For Conclave that means:
  * The `trts` (trusted runtime system) from the Intel Linux SDK.
  * The Conclave JVM and message routing code.

Other components you might expect to be a part of the TCB aren't, for instance the operating system isn't, nor is the
the BIOS nor the `aesmd` daemon that handles interaction with Intel's remote attestation assessment servers. 
Only small parts of the SGX infrastructure and code that runs inside enclaves needs to be operating correctly. 

Intel systems also have a chip called the 'management engine' (ME). Although some SGX apps use capabilities from this chip,
Conclave doesn't use any ME services and thus the ME isn't a part of the trusted computing base. By implication
bugs in the ME have no effect on your enclave.

## Recovering the TCB

When bugs or security weaknesses are found the replaceable parts of the TCB are upgraded via normal software updates.
The version of the TCB a computer is using is a part of the remote attestation, so enclave clients can check if the
operator of an enclave has upgraded their TCB correctly and refuse to upload data if not. In this state the
`EnclaveInstanceInfo.securityInfoFromServer.summary` property will be set to `STALE`, indicating that the system is
running in a secure mode but there are known reasons to upgrade.

To perform TCB recovery one or more of the following actions may be required by the owner of the hardware (i.e. either 
you or your cloud vendor):

1. Applying operating system updates (as these include new microcode)
2. Upgrading Conclave
3. Recompiling the enclave and distributing a new version
4. Altering the BIOS/UEFI configuration

In cases where you're running on virtualised hardware, contact your cloud provider to learn about their schedule for
performing TCB recoveries.

If a new version of Conclave is required that will be communicated to licensees when it becomes available. New versions
may also provide more detailed advice on what to do in the `EnclaveSecurityInfo` object. Converting an 
`EnclaveInstanceInfo` object to a string will usually include a textual summary explaining why a machine is judged to
be insecure or stale and what can be done to resolve the problem.

## Key rotation

When a TCB recovery occurs, enclave keys must be changed. This is to stop someone upgrading their machine past the 
security fix, convincing someone to send them data, then downgrading the machine so the data can be accessed.
Recovery must be a one way door: *new* enclaves should be able to read *old* data, but *old* enclaves should not be able
to read *new* data.

Conclave Mail handles TCB recovery automatically. Mails sent to the enclave prior to a TCB recovery are still decryptable
after the recovery. Once clients have downloaded a fresh `EnclaveInstanceInfo` any new mails sent to the enclave using it
will not be decryptable by the old system, or one that's been downgraded.

## Enclave-specific TCB recovery

The enclave itself makes up a part of the overall system's TCB, because it's trusted to work correctly. If there's a
security bug in the enclave business logic itself (or in Conclave) you will need to do an enclave-specific TCB recovery.
This is easy: just increment the revocationLevel in your [enclave configuration](enclave-configuration.md). Enclaves
with a higher revocation level can read mail sent to enclaves with a lower level, but not vice-versa. When clients 
download a fresh `EnclaveInstanceInfo` then they will start sending mail that can't be decrypted by the revoked enclave.
The final step is for clients to adjust their enclave constraint to require the new revocation level, thus forcing the
server to run the upgraded version.

## Timeframes for TCB recovery

When a global TCB recovery begins Intel announce it via their website. Deadlines are provided at which time remote attestations
from non-upgraded systems will become labelled as `STALE`. This doesn't happen immediately: time is provided with which
to implement any required changes and upgrade. This is to avoid apps that require fully upgraded systems from 
unexpectedly breaking on the day of the security announcements.