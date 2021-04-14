# Machine setup

So. There's an easy way to do this, and a hard way.

## The easy way

1. [Requisition a Microsoft Azure Gen 2 VM](azure.md).
1. Upload your Java app to it and run it, as if it were any other Java app.
1. There is no step 3!

When using Azure everything is fully automatic.

## The harder way

To deploy an enclave using real SGX hardware you need to configure the host system, and if your hardware
does not support DCAP attestation also get access to the [Intel Attestation Service (IAS)](ias.md). At this time
the host must be [Linux](system-requirements.md#linux-distros-and-versions) and requires the following steps:

1. Installing the SGX kernel driver, which isn't yet included in upstream kernels.
2. Installing the Intel platform services software.
3. Follow the instructions at the [IAS website](https://api.portal.trustedservices.intel.com/EPID-attestation) to get
   access to the IAS servers using a whitelisted SSL key.

!!! note
    To just *develop* enclaves it's sufficient to have any Linux or Windows host, as the simulation mode requires no 
    special machine setup. However, Intel requires that the CPU must at least support [SSE4.1](https://en.wikipedia.org/wiki/SSE4#SSE4.1).

## Hardware support

The machine needs support from both the CPU and firmware. At this time multi-socket boards don't support SGX. Your 
hardware manufacturer can tell you if your machine supports SGX, but most new computers do (one exception is anything 
made by Apple).

There is a community maintained list of [tested/compatible hardware available on GitHub](https://github.com/ayeks/SGX-hardware).

For some machines SGX must be explicitly enabled in the BIOS/UEFI firmware screens. For others it can be activated
by any root user: the Conclave host API will try to activate it for you, if possible and if run with sufficient
permissions.

## Hosting providers

* Microsoft Azure offers [virtual machines with SGX hardware](https://azure.microsoft.com/en-us/solutions/confidential-compute/)
* OVH offers [rentable SGX hardware](https://www.ovh.com/world/dedicated-servers/software-guard-extensions/)

## Distribution support

The following Linux distros are formally supported by Intel:

* Ubuntu 16.04 LTS Desktop 64bits
* Ubuntu 16.04 LTS Server 64bits
* Ubuntu 18.04 LTS Desktop 64bits
* Ubuntu 18.04 LTS Server 64bits
* Ubuntu 20.04 LTS Desktop 64bits
* Ubuntu 20.04 LTS Server 64bits
* Red Hat Enterprise Linux Server release 7.6 64bits
* Red Hat Enterprise Linux Server release 8.2 64bits
* CentOS 8.2 64bits
* Fedora 31 Server 64bits

However, others will probably still work.

## Install the kernel driver and system software

Installers for the system software can be [obtained from Intel](https://01.org/intel-software-guard-extensions/downloads).
We recommend reading the [installation user guide](https://download.01.org/intel-sgx/sgx-linux/2.12/docs/Intel_SGX_Installation_Guide_Linux_2.12_Open_Source.pdf).
The installation process is simple. Intel provides:

* APT repositories for Ubuntu
* Cross-distro installer binaries for other platforms, which set up the system software and compile/install the kernel driver.

!!! important

    The installer will need to be re-run when the kernel is upgraded.

Alternatively, you can [compile the system software](https://github.com/intel/linux-sgx/releases/tag/sgx_2.12) yourself.
The [kernel driver is also available on GitHub](https://github.com/intel/linux-sgx-driver).  

For SGX remote attestation to operate and machine provisioning to succeed, a small daemon called `aesmd` is used. This
comes as part of the SGX platform services software and will be set up during the install process.

The quick summary looks like this:

1. Download and run the driver installer binary (all distros)
2. For Ubuntu users, as root run:
   * For Ubuntu 16 LTS: `echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu xenial main' > /etc/apt/sources.list.d/intelsgx.list`
   * For Ubuntu 18 LTS: `echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu bionic main' > /etc/apt/sources.list.d/intelsgx.list`
   * Add the Intel package signing key: `wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -`
   * Then run `apt-get update`
   * And finally `apt-get install libssl-dev libcurl4-openssl-dev libprotobuf-dev libsgx-urts libsgx-launch libsgx-epid libsgx-quote-ex`
3. For other users, use the SDK installer (which installs the platform services software as well)
4. These steps will start the `aesm_service`. 


## Limited network connectivity

The enclave host machine needs to contact Intel's attestation servers, as part of proving to third parties that it's
a genuine unrevoked CPU running in the latest known secure configuration. Therefore if the machine has limited
connectivity you must use an outbound HTTP[S] proxy server.

The `aesmd` service has a configuration file in `/etc/aesmd.conf`. You may need to put your proxy settings there.

The program that uses Conclave will also need to make web requests to https://api.trustedservices.intel.com so you
may need to [provide Java with HTTP proxy settings](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html) 
as well. 
   
## Using containers

To configure Docker for use with SGX, you must pass at least these flags when creating the container: 

`--device=/dev/isgx -v /var/run/aesmd/aesm.socket:/var/run/aesmd/aesm.socket`

Failure to do this may result in an SGX_ERROR_NO_DEVICE error when creating an enclave. 

<!--- TODO: We should offer a machine setup test tool here or use the one from Fortanix -->

## Renewing machine security  

After following the above instructions, you may discover your `EnclaveInstanceInfo` objects report the enclave as 
`STALE`. This means the machine requires software updates. Applying all available updates and
rebooting should make the security evaluation of `STALE` go away. See ["Renewability"](renewability.md) to learn more
about this topic and what exactly is involved.
