# cpp/linux-sgx
This contains patches to the [Intel SGX SDK](https://github.com/intel/linux-sgx) to make it work with Conclave.

The build compiles the SDK, installs it locally on a build directory so the PSW compilation (`cpp/psw`) can find its
dependencies.
