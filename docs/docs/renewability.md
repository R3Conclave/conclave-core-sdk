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
[`EnclaveInstanceInfo.securityInfo.summary`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/get-security-info.html) 
property will be set to [`STALE`](api/-conclave%20-core/com.r3.conclave.common/-enclave-security-info/-summary/index.html), indicating that the 
system is running in a secure mode but there are known reasons to upgrade.

To perform TCB recovery one or more of the following actions may be required by the owner of the hardware (i.e. either 
you or your cloud vendor):

1. Applying operating system updates (as these include new microcode)
2. Upgrading Conclave
3. Recompiling the enclave and distributing a new version
4. Altering the BIOS/UEFI configuration

In cases where you're running on virtualised hardware, contact your cloud provider to learn about their schedule for
performing TCB recoveries.

If a new version of Conclave is required that will be communicated to licensees when it becomes available. New versions
may also provide more detailed advice on what to do in the
[`EnclaveSecurityInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-security-info/index.html) object. 
Converting an [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) object to a 
string will usually include a textual summary explaining why a machine is judged to
be insecure or stale and what can be done to resolve the problem.

## Key rotation and the SGX CPU Security Version Number

When a TCB recovery occurs, enclave keys must be changed. This is to stop someone upgrading their machine past the 
security fix, convincing someone to send them data, then downgrading the machine so the data can be accessed.
Recovery must be a one way door: *new* enclaves should be able to read *old* data, but *old* enclaves should not be able
to read *new* data.

The current state of the TCB is identified by the SGX CPU Security Version Number (CPUSVN). This is
an array of bytes that is used to uniquely identify the security state of the the CPU platform, including the CPU SGX
microcode revision. Whenever a TCB recovery takes place, the CPUSVN will change. This means that whenever a TCB recovery
occurs, the CPUSVN changes and all data that is sealed after the recovery will use a new key. 

The enclave can request the generation of a sealing key using the current CPUSVN or a previous CPUSVN. This gives an
enclave the ability to read data sealed using previous TCB states. However, the reverse is not possible - an enclave
cannot request a key based on a future value of the CPUSVN.

Conclave Mail handles TCB recovery automatically. Mails sent to the enclave prior to a TCB recovery are still decryptable
after the recovery. Once clients have downloaded a fresh [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) any new mails sent to the enclave using it
will not be decryptable by the old system, or one that's been downgraded.

### Mock Mode and the SGX CPUSVN
It is a good idea to test your enclave's ability to handle TCB recovery, checking that any data persisted by the enclave
is still accessible when the CPUSVN value changes and that rolling back the CPUSVN (as an attacker might do) prevents
the enclave from being able to access data with a later CPUSVN.

In order to test this Conclave gives you the ability to set the SGX CPUSVN [when using mock mode](mockmode.md#mock-mode-configuration).
Rather than providing an array of bytes, Conclave simplifies the setting of mock CPUSVN values by providing an
integer [`tcbLevel`](api/-conclave%20-core/com.r3.conclave.common/-mock-configuration/set-tcb-level.html) setting. This 
allows you to specify an integer between 1 and 65535 which is hashed 
internally in the mock enclave using SHA256 and the resulting byte array is used as the mock CPUSVN. The use of an
integer for `tcbLevel` means it is easy to test with ordered version numbers, allowing you to test how your enclave reacts
to incrementing and decrementing TCB level values, simulating TCB recovery and downgrades.

You can set the TCB level to use for testing by providing a
[`MockConfiguration`](api/-conclave%20-core/com.r3.conclave.common/-mock-configuration/index.html) when loading your enclave in mock mode. 
For example, the following configuration sets the SGX CPUSVN while specifying the default values for everything else:

```java hl_lines="2"
MockConfiguration config = new MockConfiguration();
config.setTcbLevel(5);
EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave", config);
```

The above example sets the CPUSVN value to SHA256(5). This will allow sealing keys to be created inside your enclave
for any CPUSVN from 1 to 5.

## Enclave-specific TCB recovery

The enclave itself makes up a part of the overall system's TCB, because it's trusted to work correctly. If there's a
security bug in the enclave business logic itself (or in Conclave) you will need to do an enclave-specific TCB recovery.
This is easy: just increment the revocationLevel in your [enclave configuration](enclave-configuration.md). Enclaves
with a higher revocation level can read mail sent to enclaves with a lower level, but not vice-versa. When clients 
download a fresh [`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) then they will
start sending mail that can't be decrypted by the revoked enclave.
The final step is for clients to adjust their enclave constraint to require the new revocation level, thus forcing the
server to run the upgraded version.

## Timeframes for TCB recovery

When a global TCB recovery begins Intel announce it via their website. Deadlines are provided at which time remote attestations
from non-upgraded systems will become labelled as [`STALE`](api/-conclave%20-core/com.r3.conclave.common/-enclave-security-info/-summary/index.html).
This doesn't happen immediately: time is provided with which
to implement any required changes and upgrade. This is to avoid apps that require fully upgraded systems from 
unexpectedly breaking on the day of the security announcements.

## CPU Security Version Number
The current security revision of an Intel SGX platform is identified by the CPU Security Version Number (CPUSVN). This is
an array of bytes that is used to uniquely identify the security state of the the CPU platform, including the CPU SGX
microcode version. Whenever a TCB recovery takes place, the CPUSVN will change.

The CPUSVN is used (along with other parameters) to derive the keys used to seal data that is sent outside the enclave.
This means that whenever a TCB recovery occurs, the CPUSVN changes and all sealed data after the recovery will use a
new key. 