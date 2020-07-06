//
// OS Stubs for functions declared in sys/wait.h
//
#include "vm_enclave_layer.h"

extern "C" {

int waitpid(pid_t, int*, int) {
    jni_throw("STUB: waitpid");
    errno = -ENOSYS;
    return -1;
}

}
