
# Non-cloud deployment

Conclave recommends users to [deploy applications in Azure VMs](machine-setup.md) as it's the easiest method. 

However, you can also deploy your enclaves on-premise.

To configure the host:

1. Install the SGX kernel driver (not required for Linux kernel versions later than 5.11).
2. Install the Intel platform services software.

!!!Note
   
    1. You can *develop* enclaves on Linux, Windows, or macOS hosts. To use simulation mode in Windows and macOS, you 
       need to install and run Docker. Intel requires that the CPU must at least support
       [SSE4.1](https://en.wikipedia.org/wiki/SSE4#SSE4.1).
    2. Conclave works only in mock mode on new Mac computers with Apple silicon due to a [known issue](known-issues.md).


## Hardware support

The machine needs support from both the CPU and the firmware. Please check with your hardware manufacturer to confirm 
that your system supports SGX.

You need to enable SGX in the BIOS/UEFI firmware screens for some machines. For others, a root user needs to activate 
SGX. The [Enclave host API](api/-conclave%20-core/com.r3.conclave.host/-enclave-host/index.html) will try to 
activate SGX if run with sufficient permissions.


## Distribution support

Intel supports the following Linux distros:

* Ubuntu 18.04 LTS Desktop 64bits
* Ubuntu 18.04 LTS Server 64bits
* Ubuntu 20.04 LTS Desktop 64bits
* Ubuntu 20.04 LTS Server 64bits
* Red Hat Enterprise Linux Server release 7.6 64bits
* Red Hat Enterprise Linux Server release 8.2 64bits
* CentOS 8.2 64bits
* Fedora 31 Server 64bits


## Install the kernel driver and system software

You can get the installers for the system software from Intel
[here](https://01.org/intel-software-guard-extensions/downloads).

Follow this
[user guide](https://download.01.org/intel-sgx/sgx-linux/2.18/docs/Intel_SGX_SDK_Release_Notes_Linux_2.18_Open_Source.pdf)
to install the driver and the system software. 

<!-- The above user-guide link needs to be updated often. Find the version of the Intel SGX SDK in 
cpp/linux-sgx/CMakeLists.txt file, then you can search these Intel softwares here
https://download.01.org/intel-sgx/sgx-linux -->

Intel provides:

* APT repositories for Ubuntu
* Cross-distro installer binaries for other platforms, which set up the system software and install the kernel driver.

!!!Important

    You need to re-run the installer after upgrading the kernel.

Alternatively, you can [compile the system software](https://github.com/intel/linux-sgx/releases/tag/sgx_2.18)
yourself.
The [kernel driver is also available on GitHub](https://github.com/intel/linux-sgx-driver).

A quick summary:

1. Download and run the driver installer binary (all distros).
2. For Ubuntu users:
    * For Ubuntu 18 LTS: `echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu bionic main' > /etc/apt/sources.list.d/intelsgx.list`
    * For Ubuntu 20 LTS: `echo 'deb [arch=amd64] https://download.01.org/intel-sgx/sgx_repo/ubuntu focal main' > /etc/apt/sources.list.d/intelsgx.list`
    * Add the Intel package signing key: `wget -qO - https://download.01.org/intel-sgx/sgx_repo/ubuntu/intel-sgx-deb.key | apt-key add -`
    * Run `apt-get update`
    * Run `apt-get install libssl-dev libcurl4-openssl-dev libprotobuf-dev libsgx-urts libsgx-launch libsgx-quote-ex`
3. For non-Ubuntu users, use the SDK installer (which also installs the platform services software).

## Renewing machine security

After following the above instructions, the
[`EnclaveInstanceInfo`](api/-conclave%20-core/com.r3.conclave.common/-enclave-instance-info/index.html) objects 
might report your enclave as `STALE`. This means the machine requires software updates. To change the security 
evaluation to `SECURE`, you must apply all the updates and reboot. See ["Renewability"](renewability.md) to learn 
more about this topic.
