# Instructions

## With existing `sgx_sign.exe`
* Getting Cygwin binaries
  Use `./download.Cygwin` to get required `.exe`, `.dll` and `ldscripts`.

* sgx_sign
  `sgx_sign.exe` is build from Intel's linux-sgx/**/signtool using patch provided.

## Building `sgx_sign.exe` from source (**Windows** is necessary)
To build `sgx_sign.exe` from source, use the following procedure:

1. Install [Cygwin](https://www.Cygwin.com/setup-x86_64.exe) in your **Windows** environment using the default options;
2. In **Cygwin's** terminal, clone `sgxjvm` in your home folder (e.g. `git clone https://github.com/corda/sgxjvm.git`);
3. Go to `sgxjvm/cpp/cygwin`;
4. Execute `./cygwin-package-setup.sh` to download **Cygwin's** required components;
5. Execute `./cygwin-build-signtool.sh` which will download the `linux-sgx` SDK and build `sgx_sign.exe`.

!!! note
  **With Cygwin it is not able to download packages for specific versions. Therefore `sgx_sign.exe` must be tested in the
  host's Windows OS with the existing `.dll`s in order to verify compatibility.