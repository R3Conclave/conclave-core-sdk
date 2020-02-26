# Machine setup

Before you can develop with or deploy SGX you need to configure the host system. At the time
the host must be Linux and requires the following steps:

1. Installing the SGX kernel driver, which isn't yet included in upstream kernels.
2. Installing the Intel platform services software.

The latter sets up a daemon called `aesmd` that handles various aspects of machine provisioning
and remote attestation.

## Hardware support

The machine needs support from both the CPU and firmware. At this time multi-socket boards don't
support SGX. Your hardware manufacturer can tell you if your machine supports SGX, but most new
computers do (one exception is anything made by Apple).

There is a community maintained list of [tested/compatible hardware available on GitHub](https://github.com/ayeks/SGX-hardware).

For some machines SGX must be explicitly enabled in the BIOS/UEFI firmware screens.

In the cloud Microsoft Azure offers a test cluster with SGX hardware, and rented colo hardware is often
available with it too. OVH offers rentable SGX capable hardware, as an example of one provider.

## Distribution support

The following Linux distros are formally supported by Intel:

* Ubuntu 16.04.3 LTS Desktop 64bits
* Ubuntu 16.04.3 LTS Server 64bits
* Ubuntu 18.04 LTS Desktop 64bits
* Ubuntu 18.04 LTS Server 64bits
* Red Hat Enterprise Linux Server release 7.4 64bits
* Red Hat Enterprise Linux Server release 8.0 64bits
* CentOS 7.4.1708 64bits
* SUSE Linux Enterprise Server 12 64bits

However, others will probably still work.

## Install the kernel driver

Download the driver from the [linux-sgx-driver Github project](https://github.com/intel/linux-sgx-driver)
and follow the instructions in the README to install it.  You will need to repeat the installation process
each time the kernel changes.

## Install the system software

Installers for the system software can be [obtained from Intel](https://01.org/intel-software-guard-extensions/downloads).

!!! important

    You need to download the 2.4 release of the system software, not the latest version.

Alternatively, you can [compile the system software from the sources yourself](https://github.com/intel/linux-sgx/releases/tag/sgx_2.4).

<!--- TODO: We should offer a machine setup test tool here or use the one from Fortanix -->
