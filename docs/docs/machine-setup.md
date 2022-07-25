# Machine setup

There's an easy way to do this, and a hard way.

## The easy way

1. [Create a Microsoft Azure Gen 3 VM](azure.md).
2. Upload your Java app to the VM and run it, as if it were any other Java app.

## The harder way

To deploy an enclave using real SGX hardware you need to configure the host system, and if your hardware
does not support DCAP attestation also get access to the [Intel Attestation Service (IAS)](ias.md). At this time,
the host must be [Linux](machine-setup.md#distribution-support), and requires the following steps:

1. Install the SGX kernel driver (not required for Linux kernel versions later than 5.11).
2. Install the Intel platform services software.
3. Follow the instructions at the [IAS website](https://api.portal.trustedservices.intel.com/EPID-attestation) to get
   access to the IAS servers using a whitelisted SSL key.

!!! note
    You can *develop* enclaves on Linux, Windows, or macOS hosts. To use simulation mode in Windows and macOS, you need to install and run Docker. Intel requires that the CPU must at least support [SSE4.1](https://en.wikipedia.org/wiki/SSE4#SSE4.1).

## Hardware support

The machine needs support from both the CPU and firmware. Please check with your hardware manufacture to confirm that your machine supports SGX.

For some machines SGX must be explicitly enabled in the BIOS/UEFI firmware screens. For others, it can be activated
by any root user: the Conclave host API will try to activate it for you, if possible and if run with sufficient
permissions.

## Hosting providers

* Microsoft Azure offers [virtual machines with SGX hardware](https://azure.microsoft.com/en-us/solutions/confidential-compute/)
* OVH offers [rentable SGX hardware](https://www.ovhcloud.com/en-gb/bare-metal/intel-software-guard-extensions/)

## Distribution support

The following Linux distros are formally supported by Intel:

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
We recommend reading the [installation user guide](https://download.01.org/intel-sgx/sgx-linux/2.13.3/docs/Intel_SGX_Installation_Guide_Linux_2.13.3_Open_Source.pdf).
The installation process is simple. Intel provides:

* APT repositories for Ubuntu
* Cross-distro installer binaries for other platforms, which set up the system software and compile/install the kernel driver.

!!! important

    The installer will need to be re-run when the kernel is upgraded.

Alternatively, you can [compile the system software](https://github.com/intel/linux-sgx/releases/tag/sgx_2.13.3) yourself.
The [kernel driver is also available on GitHub](https://github.com/intel/linux-sgx-driver).

For SGX remote attestation to operate and machine provisioning to succeed, a small daemon called `aesmd` is used. This
comes as part of the SGX platform services software and will be set up during the install process.

The quick summary looks like this:

1. Download and run the driver installer binary (all distros)
2. For Ubuntu users, as root run:
   * For Ubuntu 18 LTS: `echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu bionic main' > /etc/apt/sources.list.d/intelsgx.list`
   * For Ubuntu 20 LTS: `echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' > /etc/apt/sources.list.d/intelsgx.list`
   * Add the Intel package signing key: `wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -`
   * Then run: `apt-get update`
   * Finally: `apt-get install libssl-dev libcurl4-openssl-dev libprotobuf-dev libsgx-urts libsgx-launch libsgx-epid libsgx-quote-ex`
3. For other users, use the SDK installer (which installs the platform services software as well)
4. These steps will start the `aesm_service`.


## Limited network connectivity

The enclave host machine needs to contact Intel's attestation servers, as part of proving to third parties that it's
a genuine unrevoked CPU running in the latest known secure configuration. Therefore, if the machine has limited
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

After following the above instructions, you may discover your [`EnclaveInstanceInfo`](api/-conclave/com.r3.conclave.common/-enclave-instance-info/index.html) objects report the enclave as
`STALE`. This means the machine requires software updates. Applying all available updates and
rebooting should make the security evaluation of `STALE` go away. See ["Renewability"](renewability.md) to learn more
about this topic and what exactly is involved.
