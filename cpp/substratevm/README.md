# cpp/substratevm
This is C/C++ enclave-side code, with the implementation of the entry points (host to enclave) of some EDL code.

It produces a single artifacts: a C/C++ static archive library (`libsubstratevm.a`) that gets linked to form the 
shared library representing the SGX enclave.
