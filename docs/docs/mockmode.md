# Building enclaves in Mock Mode

Conclave provides a number of different [modes](enclave-modes.md) when building your enclave, supporting different stages of the development
cycle. Release, debug and simulation modes all require a Linux environment in order to run. This does not prevent
running a simulation enclave on macOS or Windows if you load and run you project in a Docker container, but for
convenience it is useful to be able to run your enclave code directly within the host environment. In addition, 
the build time for simulation, debug and release enclaves can be quite high.

With mock mode, the enclave class runs in the same JVM as the host, so interactions between the enclave
and host are all just regular function calls. You can expect very short build times, step through using a debugger 
and enjoy the regular Java development experience.

!!!tip
    To debug your host application in mock mode from IntelliJ, find the `:host:run` task in the gradle menu, add the `-PenclaveMode=mock` argument to the run configuration, and then run the task in debug mode.

## Using mock mode

Mock mode can be used in two different ways, depending on whether the enclave is being loaded from outside or inside
the enclave module.

### Outside the enclave module
Outside enclave module, The mode can be set when adding a Gradle dependency, just like any other mode:
```groovy
dependencies {
    runtimeOnly project(path: ":enclave", configuration: "mock")
}
```

See [Setting the enclave mode](enclave-modes.md#set-the-enclave-mode) for more details.

### Inside the enclave module
When creating an [`EnclaveHost`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/index.html) inside the enclave module, 
Conclave will automatically create it in mock mode.
This means any tests you define in the enclave module will automatically use mock mode - you can write your tests to load 
and call the enclave without having to explicitly configure a mock enclave.
For tests to use any other mode, they must be defined outside the enclave module, for example in the host.

## Mock mode and the host classpath

When loading an enclave from the host, Conclave looks in two places for the enclave class. Firstly, it looks on the
current classpath. If the enclave class is present in the classpath then it will assume a mock configuration and
load the enclave code directly. Secondly, it looks in the resources for a JAR file that contains a simulation, 
debug or release enclave. If that is present, it is unpacked and loaded.

If the enclave class is on the classpath _and_ the enclave JAR file is present then the host will throw an exception
stating that multiple enclave classes have been found. In order to avoid this you need to carefully configure the
dependencies in your host project to ensure only a single enclave is present.

In most cases you do not require the enclave classes within the host project so all you need to do is set the
enclave project as a `runtimeOnly` dependency. This will then work for all enclave configurations:

```groovy
runtimeOnly project(path: ":enclave", configuration: mode)
```

If, however, you do need to [access the enclave class](#accessing-the-enclave-from-the-mock-host) in your host project in mock mode via the the `EnclaveHost.mockEnclave`
property then you will need to conditionally depend on the enclave project at compile or implementation time by
including this in your host `build.gradle`:

```groovy
if (mode == "mock") {
    implementation project(path: ":enclave", configuration: "mock")
} else {
    runtimeOnly project(path: ":enclave", configuration: mode)
}
```

!!! info
    Note that within the enclave project the enclave class is always present on the classpath. This means that
    all tests defined within the enclave project always use mock mode.

## Accessing the enclave from the mock host

When using mock mode, the host provides access to the enclave instance via the
[`EnclaveHost.mockEnclave`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/get-mock-enclave.html) property. If you
want to look at the internal state of your enclave you can cast this property to your actual enclave class type.

```java
ReverseEnclave reverseEnclave = (ReverseEnclave)mockHost.getMockEnclave();
```

!!! notice

    The `EnclaveHost.mockEnclave` property can only be used with mock enclaves. The host does not have access to internal
    enclave state for any other enclave type. If you attempt to access the property on a non-mock enclave then
    `IllegalStateException` will be thrown.

## Mock mode configuration

When you build an enclave in release, debug mode or simulation mode, there are certain environmental parameters
that are defined by the trusted computing base and the Conclave configuration.

In mock mode, instead of being defined by the platform, these parameters are defined using the
[`MockConfiguration`](api/-conclave%20-core/com.r3.conclave.common/-mock-configuration/index.html)
class which is subsequently passed to [`EnclaveHost.load`](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/load.html).
This class can be used to configure these parameters for use 
when the mock enclave is loaded. This is useful to allow test cases to be written for checking correct enclave operation around
version increments and rollbacks.

!!! notice
    `MockConfiguration` is only applied when using mock mode. If you provide a mock configuration when loading a non-mock enclave
    then it is ignored.
 

The [`MockConfiguration`](api/-conclave%20-core/com.r3.conclave.common/-mock-configuration/index.html) class allows configuration of 
the following properties. The table below describes each property and
shows the SGX equivalent parameter for information.

| Property | SGX Equivalent | Allowed values | Default Value | Description |
| -------- | -------------- | -------------- | ------------- | ----------- |
| codeHash | MRENCLAVE | 32 byte array | SHA256(enclave class name) | Specifies an array of bytes to use as the enclave code hash measurement. |
| codeSigningKeyHash | MRSIGNER | 32 byte array | 32 zero bytes | Specifies an array of bytes to use as a hash of the public key used to sign the enclave. The mock enclave will create a public/private key pair based on this value. |
| productID | ISVProdId | 1-65535 | 1 | The mock product ID of the enclave, used to uniquely identify enclaves signed with the same signing key.  |
| revocationLevel | ISVSVN | 0-65534 | 0 | The mock revocation level of the enclave. |
| tcbLevel | CPUSVN | 1-65535 | 1 | A mock version number that defines the TCB level, or version number of the TCB. This is equivalent to the SGX CPUSVN but because Conclave uses an integer, the tcbLevel is ordered allowing for easy testing of [TCB recovery](renewability.md#mock-mode-and-the-sgx-cpusvn). |

If you do not provide a [`MockConfiguration`](api/-conclave%20-core/com.r3.conclave.common/-mock-configuration/index.html) when loading 
a mock enclave then the default values are used. 
If you want to specify your own `MockConfiguration` you can configure these properties when loading the
enclave via the host using code similar to this:

```java
MockConfiguration config = new MockConfiguration();
config.setProductID(2);
config.setTcbLevel(3);
config.setCodeSigningKeyHash(SHA256Hash.parse("1234567890123456789012345678901234567890123456789012345678901234"));
EnclaveHost mockHost = EnclaveHost.load("com.r3.conclave.sample.enclave.ReverseEnclave", config);
```

See [here for more information on CPUSVN](renewability.md#mock-mode-and-the-sgx-cpusvn).