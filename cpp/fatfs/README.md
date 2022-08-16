# cpp/fatfs
This code creates the representation of the enclave filesystem using FatFs.

There are two separate artifacts generated: `libfatfs_enclave.a` is a C++ static archive library to be linked together
with other static libraries to form the shared object that represents the **SGX enclave**, `libfatfs_host.a` is a C++
static archive library to be linked together with other static libraries to form the shared object representing
the **host C++ layer** that communicates with the host Java/Kotlin layer through JNI.
