# cpp/jvm-host-shared
This contains C/C++ code used on the host side, mainly to retrieve hardware information using Intel SGX SDK.

There is a single artifact produced by this directory: a C/C++ static archive library (`libjvm_host_shared.a`) 
that gets linked to form the artifacts (both the *simulation* and *debug/release* shared libraries)
from the `jvm-host` directory.
