# cpp/jvm-host
This is C/C++ code used in the host.

It allows Java/Kotlin source code from the host side (`conclave-host`) to interact with C/C++ layer through JNI.

The artifacts produced from this directory are shared C/C++ archive libraries, 
one for *simulation* enclave mode (`libjvm_host_sim.so`) and another one for both debug and release mode (`libjvm_host.so`).
