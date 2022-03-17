# API changes

## 1.2 to 1.3

### KDS REST API

The Key Derivation Service (KDS) is out of beta and its REST API has been finalised. The request format for both the
`/public` and `/private` endpoints has changed. The old format is no longer supported and HTTP clients are now
required to specify the HTTP header `API-VERSION` with a value of `1`. Details on the API can be found
[here](#kds-rest-api).

### KDS enclave configuration

The `keySpec` block in the enclave's build.gradle has changed to `persistenceKeySpec`. The old `keySpec` is still
supported but deprecated and will be removed in a future version.

### API changes

[`EnclaveHost.deliverMail`](api/-conclave/com.r3.conclave.host/-enclave-host/deliver-mail.html) now throws an 
`IOException`. This can occur if the mail to be delivered was encrypted using a KDS key and the host is unable to 
retrieve it from the KDS.

## 1.1 to 1.2

### Improvements to mail and persistence

We've made writing persistent code inside the enclave easy and secure from roll back attacks in 1.2. Mail is now
only used for communication and no longer plays a part in persistence. This means certain features are no longer
necessary and have been removed. We hope this is a one-off occurrence that will be worth the small effort in
upgrading your code.

Firstly, mail acknowledgement no longer exists. Mail will no longer be replayed on enclave startup and so you no longer
need to think about having to acknowledge any mail. That means the `acknowledgeMail` method in `Enclave` has been
removed. This also means the mail ID parameter that's used for acknowledgement is no longer needed and has been
removed as a parameter to the methods [`EnclaveHost.deliverMail`](api/-conclave/com.r3.conclave.host/-enclave-host/deliver-mail.html) 
and [`Enclave.receiveMail`](api/-conclave/com.r3.conclave.enclave/-enclave/receive-mail.html).

You will need to change the signature of your `receiveMail` method in your enclave class:

```java
// old
protected void receiveMail(long id, EnclaveMail mail, String routingHint) {

// new
protected void receiveMail(EnclaveMail mail, String routingHint) {
```

Similarly, you will need to update calls to `EnclaveHost.deliverMail`:

```java
// old
enclave.deliverMail(id, mail, routingHint)

// new
enclave.deliverMail(mail, routingHint)
```

If you have used `acknowledgeMail` then you will need to remove those calls. You may also need to redesign your
enclave to _not_ think about redelivery of mail, but this may actually make your code simpler!

Another consequence of no longer having mail redelivery is that the mail-to-self pattern to persist data across
enclave restarts is no longer valid. However, this has been replaced by a more secure and easier to use API with
the introduction of a [`persistentMap`](api/-conclave/com.r3.conclave.enclave/-enclave/get-persistent-map.html)
inside the enclave. This is a normal `java.util.Map` object which stores
key strings to arbitrary byte arrays. Use this map to persist data that you need available across restarts.
[Learn more about enclave persistence](persistence.md).

On the host side the API has changed slightly as well. Since mail acknowledgement no longer exists then the mail
commands `AcknowledgeMail` and `AcknowledgementReceipt` have also been removed. They have been replaced by a new
command [`StoreSealedState`](api/-conclave/com.r3.conclave.host/-mail-command/-store-sealed-state/index.html). These 
changes have a small knock-on effect with the
[`start`](api/-conclave/com.r3.conclave.host/-enclave-host/start.html) method. The second byte array
parameter has changed its _meaning_ to represent a sealed state blob rather than the acknowledgement receipt blob.
Also, the mail commands callback is no longer optional and must be specified.

### Switch to enclave instance session keys for encryption

Whilst not an API change, the behaviour of Mail has changed. As Mail no longer functions as a persistence mechanism and
is solely used for communication, the enclave's encryption key is now session-based, rather than persisting across
enclave restarts. In other words, a new random key is created each time an enclave starts. Since no two enclaves will
ever have the same encryption key, a malicious host is prevented from spinning up multiple copies of the same enclave
and trying to manipulate their state by replaying old mail.

As a consequence, when the enclave restarts, clients will need to download the new
[`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html) and use this to 
create a new post office with the enclave's new key. The new post office must be used to send all subsequent mails.

How can a client detect when the enclave restarts? We've updated [`EnclaveHost.deliverMail`](api/-conclave/com.r3.conclave.host/-enclave-host/deliver-mail.html)
to throw a new checked exception [`MailDecryptionException`](api/-conclave/com.r3.conclave.mail/-mail-decryption-exception/index.html)
if the enclave was unable to decrypt the mail message. Since now the most likely
explanation for this decryption error is that the enclave was restarted and is using a new key, the host should 
catch this exception and use it to inform the client.

### Attestation check functionality moved from client to common

To facilitate enclave to enclave communication,
[`EnclaveConstraint`](api/-conclave/com.r3.conclave.common/-enclave-constraint/index.html) and
[`InvalidEnclaveException`](api/-conclave/com.r3.conclave.common/-invalid-enclave-exception/index.html) have moved from
`com.r3.conclave.client` to `com.r3.conclave.common`.

Client code should be updated appropriately.

### Streamlined API for platform support checks

`EnclaveHost.checkPlatformSupportsEnclaves()` has been removed and replaced by several smaller methods to make it 
easier to determine what level of hardware support is provided by the platform:
[`EnclaveHost.getSupportedModes`](api/-conclave/com.r3.conclave.host/-enclave-host/get-supported-modes.html),
[`EnclaveHost.isSimulatedEnclaveSupported`](api/-conclave/com.r3.conclave.host/-enclave-host/is-simulated-enclave-supported.html),
[`EnclaveHost.isHardwareEnclaveSupported`](api/-conclave/com.r3.conclave.host/-enclave-host/is-hardware-enclave-supported.html),
[`EnclaveHost.enableHardwareEnclaveSupport`](api/-conclave/com.r3.conclave.host/-enclave-host/enable-hardware-enclave-support.html),

Example usage of the new API:

```java
public static void main(String args[]) {
    // Try to enable hardware enclave support if not already supported
    if (!EnclaveHost.isHardwareEnclaveSupported()) {
        try {
            EnclaveHost.enableHardwareEnclaveSupport();
        } catch (PlatformSupportException e) {
            System.out.println(
                    "Hardware enclave support is not enabled! " +
                    "Reason: " + e);
        }
    }

    // Print out a list of supported modes
    Set<EnclaveMode> supportedModes = EnclaveHost.getSupportedModes();
    System.out.println("Supported enclave modes: " + supportedModes);
}
```

Note that `MockOnlySupportedException` is no longer a part of the API, and has been entirely superseded by
[`PlatformSupportException`](api/-conclave/com.r3.conclave.host/-platform-support-exception/index.html).

### Java 11

Enclaves will now use Java 11 by default. To revert back to Java 8 update the enclave's build.gradle:

```groovy
compileJava {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
```

## 1.0 to 1.1

There have been a number of changes in the way that you use mock mode in your Conclave projects. You will
need to make some changes to your build files in order to build you earlier projects with Conclave 1.1.

Firstly, the `MockHost` class for loading an enclave in mock mode has been removed. You will need to update your
code to use `EnclaveHost.load` instead of `MockHost.loadMock`. So the pre-1.1 code below:

```java
if (mode == "mock") {
    enclave = MockHost.loadMock(className);
} else {
    enclave = EnclaveHost.load(className);
}
```

Changes to:

```java
enclave = EnclaveHost.load(className);
```

Secondly, the `conclave-testing` package has been removed. This previously contained `MockHost` but this is no
longer required. You need to remove any test dependency on `conclave-testing` from your `build.gradle`
files and remove any `import` statements that refer to `conclave-testing`.

Lastly, you must make sure that your host project (the one that loads the enclave) does not include the
enclave class on its classpath in anything other than mock mode. You can ensure this is the case by
setting a `runtimeOnly` dependency on the enclave project in your host `build.gradle`.

```groovy
runtimeOnly project(path: ":enclave", configuration: mode)
```

If you need to access the enclave class in your host project in mock mode via the the `EnclaveHost.mockEnclave`
property then you will need to conditionally depend on the enclave project at compile or implementation time by
including this in your host `build.gradle`:

```groovy
if (mode == "mock") {
    implementation project(path: ":enclave", configuration: "mock")
} else {
    runtimeOnly project(path: ":enclave", configuration: mode)
}
```

[Learn more about the changes to mock mode](mockmode.md).

## Beta 4 to 1.0
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
