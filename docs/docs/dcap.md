# The DCAP protocol

Intel SGX uses the Data Center Attestation Primitives (DCAP) protocol to provide proof to clients about the state of an 
enclave. DCAP is also known as _ECDSA attestation_.

## Provisioning Certificate Caching Service

In DCAP, repeated attestation requests are served from a cache rather than sent directly to Intel. This cache is known 
as the Provisioning Certificate Caching Service (PCCS). A newly installed machine obtains a machine certificate from 
Intel via the PCCS, which may then be persisted to disk. This process is automatic.

The PCCS is typically operated as an organisation-wide service. For example, Microsoft provides a PCCS service for users 
of the Azure cloud platform.

## DCAP Client

In addition to the PCCS, the DCAP protocol also makes use of a DCAP client (sometimes called a DCAP "plugin") which
is designed to work with a corresponding caching service. The client is also responsible for gathering information
about the platform that the enclave is running on, such as SGX support and whether there are any patches available for
it.

Microsoft maintains a [DCAP client](https://github.com/microsoft/Azure-DCAP-Client) for use with the Azure PCCS.

### Conclave-Azure Bundled Client

The Microsoft Azure PCCS is open to the internet and accessible to machines outside the Azure cloud. Conclave makes use
of this by bundling a copy of the Azure DCAP plugin with the Conclave SDK. If no DCAP client is installed on the host 
system, then Conclave will use the bundled Azure DCAP client.

Conclave will search the following paths for system installed DCAP Clients:

```
/usr/lib/x86_64-linux-gnu/libdcap_quoteprov.so.1
/usr/lib/x86_64-linux-gnu/libdcap_quoteprov.so
/usr/lib/libdcap_quoteprov.so.1
/usr/lib/libdcap_quoteprov.so
```

If none of these files are present, then the bundled client will be used.

This is the recommended way to use Conclave on Azure virtual machines, but may also be used to host enclaves outside
the Azure cloud. It requires no additional setup.

If using the Azure DCAP Client and PCCS, you may also want to set the Azure DCAP client logging level to FATAL, as the 
default setting is quite verbose:

```sh
export AZDCAP_DEBUG_LOG_LEVEL=FATAL
```

Other configuration options for the Azure DCAP client are documented [here](https://github.com/microsoft/Azure-DCAP-Client).

### Intel DCAP Client & PCCS

Intel provides reference DCAP client and PCCS implementations. If you do not want to rely on externally hosted services
for attestation, such as the Azure PCCS, you can set up your own PCCS based on the Intel implementation. This requires 
that you create an account with Intel and generate an API key so that your PCCS can communicate with the Intel 
provisioning servers.

Full Installation instructions for the Intel PCCS and DCAP client can be found on 
[Intel's website](https://www.intel.com/content/www/us/en/developer/articles/guide/intel-software-guard-extensions-data-center-attestation-primitives-quick-install-guide.html).

Once installed, Conclave will load the Intel DCAP client rather than the bundled Azure client.

This is the recommended approach for applications that will run outside the Azure cloud.

### Azure DCAP Client

Although Conclave bundles the Azure DCAP client, it may also be installed directly from Microsoft's repositories:

```
wget -qO - https://packages.microsoft.com/keys/microsoft.asc | sudo apt-key add -
echo 'deb [arch=amd64] https://packages.microsoft.com/ubuntu/20.04/prod focal main' | sudo tee /etc/apt/sources.list.d/msprod.list
sudo apt-get install az-dcap-client
```

Once installed, Conclave will load the system installed DCAP client rather than the bundled one.

## Using Docker container(s)
If you plan to use a Docker container with DCAP hardware, you must map two different device files like this:

```sh
docker run --device /dev/sgx/enclave --device /dev/sgx/provision ...
```

!!!Note

    Azure offers a "Confidential Kubernetes" service. At this time, we haven't tested Conclave with that. If you try it,
    [let us and the community know](conclave-discuss@groups.io) if it works.

## Running a Conclave Application
After setting up the machine, you can follow the [Compiling and running](running-hello-world.md) tutorial to run the 
`hello-world` sample.

The sample is configured to use DCAP attestation with the following line in `Host.java`:

```java
enclave.start(new AttestationParameters.DCAP(), ... );
```
