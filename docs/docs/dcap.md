## The DCAP protocol

Intel SGX uses the Data Center Attestation Primitives (DCAP) protocol to prove what code runs in an enclave. To 
perform attestation using DCAP, Conclave needs a way to gather information about the platform the enclave is
hosted on. This information provides proof from Intel that the system supports SGX and that it is patched and up to 
date. DCAP is also known as ECDSA attestation.

In DCAP, repeated attestation requests are served from a cache rather than forwarded to Intel. A newly installed
machine obtains a machine certificate from Intel via the cache, which may then be persisted to disk. All this is
automated for you.

## DCAP Client

A DCAP client is basically a library named `libdcap_quoteprov.so.1` with a symbolic link at `libdcap_quoteprov.so`, 
which is to be installed in `/usr/lib/` or `/usr/lib/x86_64-linux-gnu`.

You can use one of the three available DCAP clients listed below:

1. Intel DCAP client
2. Azure DCAP client
3. Conclave-Azure bundled client

To avoid conflicts between DCAP client plugins, you need to uninstall the plugins that you don't require.

### Intel DCAP Client

Intel provides a DCAP client plugin as part of the DCAP runtime.

If you are using bare-metal machines, you need to install the DCAP client package:

`sudo apt-get install libsgx-dcap-default-qpl`

To use Intel's DCAP plugin, a [subscription](https://api.portal.trustedservices.intel.com/provisioning-certification). 
You must also set up a Provisioning Certificate Caching Service (PCCS). Intel provides an example and some 
instructions [here](https://github.com/intel/SGXDataCenterAttestationPrimitives/blob/master/QuoteGeneration/pccs/README.md). 
If you like to use Intel's reference implementation of their [PCCS service](https://github.com/intel/SGXDataCenterAttestationPrimitives/blob/master/QuoteGeneration/pccs),
then you might need to provide data in the correct format for Conclave. Please [let us know](mailto:conclave@r3.com) if 
you need to set this up.

### Azure DCAP Client

Microsoft provides an Azure DCAP client, which you can use on Azure virtual machines. Azure's DCAP [plugin](https://github.com/microsoft/Azure-DCAP-Client) 
does not require a subscription. 

You can use the following command to install Azure's DCAP client package:

`sudo apt-get install az-dcap-client`

### Conclave-Azure Bundled Client

Conclave provides a bundled Azure DCAP plugin, which is the recommended option for users. This plugin will be used 
only if no other plugin exists in the default libraries under `/usr/lib/`. The runtime will use the first `.so` it 
finds in the order below:

```
/usr/lib/x86_64-linux-gnu/libdcap_quoteprov.so.1
/usr/lib/x86_64-linux-gnu/libdcap_quoteprov.so
/usr/lib/libdcap_quoteprov.so.1
/usr/lib/libdcap_quoteprov.so
```
If you choose Conclave's recommended bundled DCAP plugin, delete or rename any `.so` in the above locations.

You might also want to set the Azure DCAP client logging level to FATAL as the default setting is quite verbose:

```sh
export AZDCAP_DEBUG_LOG_LEVEL=FATAL
```

## Using Docker container(s)
If you plan to use a Docker container with DCAP hardware, you must map two different device files like this:

```sh
docker run --device /dev/sgx/enclave --device /dev/sgx/provision ...
```

!!!Note

    Azure offers a "Confidential Kubernetes" service. At this time, we haven't tested Conclave with that. If you try it,
    [let us and the community know](conclave-discuss@groups.io) if it works.

## Running a Conclave Application
After setting up the machine, you can follow the [Compiling and running](running-hello-world.md) tutorial to run the `hello-world` sample.

The sample is configured to use DCAP attestation with the following line in `Host.java`:

```java
enclave.start(new AttestationParameters.DCAP(), ... );
```

DCAP doesn't require specific API keys or parameters, so creating the empty object is sufficient to choose it.
