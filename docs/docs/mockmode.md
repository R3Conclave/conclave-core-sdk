# Building enclaves in Mock Mode

Conclave provides a number of different modes when building your enclave, supporting different stages of the development
cycle. Release, debug and simulation modes all require a Linux environment in order to run. This does not prevent
running a simulation enclave on MacOS or Windows if you load and run you project in a Docker container, but for
convenience it is useful to be able to run your enclave code directly within the host environment. In addition, 
the build time for simulation, debug and release enclaves can be quite high.

With mock mode, the enclave class runs in the same JVM as the host, so interactions between the enclave
and host are all just regular function calls. You can expect very short build times, step through using a debugger 
and enjoy the regular Java development experience.

## Using mock mode

Mock mode can be used in two different ways. Firstly, you can compile your enclave in mock mode using the
[`-PenclaveMode` flag](tutorial.md#selecting-your-mode) for fast, iterative development. 

Secondly, when creating an `EnclaveHost` inside the enclave module, Conclave will automatically create it in mock mode.
This means any tests you define in the enclave module will automatically use mock mode - you can write your tests to load 
and call the enclave without having to explicitly configure a mock enclave. 

## Accessing the enclave from the mock host

When using mock mode, the host provides access to the enclave instance via the `EnclaveHost.mockEnclave` property. If you 
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

In mock mode, instead of being defined by the platform, these parameters are instead defined using the `MockConfiguration` 
class which is subsequently passed to `EnclaveHost.load()`. This class can be used to configure these parameters for use 
when the mock enclave is loaded. This is useful to allow test cases to be written for checking correct enclave operation around
version increments and rollbacks.

!!! notice
    `MockConfiguration` is only applied when using mock mode. If you provide a mock configuration when loading a non-mock enclave
    then it is ignored.
 

The `MockConfiguration` class allows configuration of the following properties. The table below describes each property and
shows the SGX equivalent parameter for information.

| Property | SGX Equivalent | Allowed values | Default Value | Description |
| -------- | -------------- | -------------- | ------------- | ----------- |
| codeHash | MRENCLAVE | 32 byte array | SHA256(enclave class name) | Specifies an array of bytes to use as the enclave code hash measurement. |
| codeSigningKeyHash | MRSIGNER | 32 byte array | 32 zero bytes | Specifies an array of bytes to use as a hash of the public key used to sign the enclave. The mock enclave will create a public/private key pair based on this value. |
| productID | ISVProdId | 1-65535 | 1 | The mock product ID of the enclave, used to uniquely identify enclaves signed with the same signing key.  |
| revocationLevel | ISVSVN | 0-65534 | 0 | The mock revocation level of the enclave. |
| tcbLevel | CPUSVN | 1-65535 | 1 | A mock version number that defines the TCB level, or version number of the TCB. This is equivalent to the SGX CPUSVN but because Conclave uses an integer, the tcbLevel is ordered allowing for easy testing of [TCB recovery](renewability.md#mock-mode-and-the-sgx-cpusvn). |

If you do not provide a `MockConfiguration` when loading a mock enclave then the default values are used. 
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