# Machine setup

Before you can deploy an enclave to use real SGX hardware you need to configure the host system. At the time
the host must be Linux and requires the following steps:

1. Installing the SGX kernel driver, which isn't yet included in upstream kernels.
2. Installing the Intel platform services software.

The latter sets up a daemon called `aesmd` that handles various aspects of machine provisioning
and remote attestation.

!!! note
    To just *develop* enclaves it's sufficient to have any Linux host, as the simulation mode requires no special
    machine setup.

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

## Install the kernel driver and system software

Installers for the system software can be [obtained from Intel](https://01.org/intel-software-guard-extensions/downloads).
We recommend reading the [installation user guide](https://download.01.org/intel-sgx/sgx-linux/2.8/docs/Intel_SGX_Installation_Guide_Linux_2.8_Open_Source.pdf) available from Intel.
The installation process is not complex. Intel provide:

* APT repositories for Ubuntu
* Cross-distro installer binaries for other platforms, which set up the system software and compile/install the kernel driver.

!!! important

    The installer will need to be re-run when the kernel is upgraded.

Alternatively, you can [compile the system software](https://github.com/intel/linux-sgx/releases/tag/sgx_2.8) yourself.
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
4. These steps will start the `aesm_service`, which has a configuration file in `/etc/aesmd.conf`. You may wish to edit 
   it if your environment requires proxy servers.
   
## Using containers

To configure Docker for use with SGX, you must pass at least these flags when creating the container: 

`--device=/dev/isgx -v /var/run/aesmd/aesm.socket:/var/run/aesmd/aesm.socket`

Failure to do this may result in an SGX_ERROR_NO_DEVICE error when creating an enclave. 

<!--- TODO: We should offer a machine setup test tool here or use the one from Fortanix -->

## Handling GROUP_OUT_OF_DATE errors

After following the above instructions, you may discover your `EnclaveInstanceInfo` objects report the enclave as 
"stale". This means the machine requires either BIOS, PSW or microcode updates. Applying all available updates and
rebooting should make the security evaluation of 'stale' go away. See [TCB recovery](tcb-recovery.md) to learn more
about this topic.