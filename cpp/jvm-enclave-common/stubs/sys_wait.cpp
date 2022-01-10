//
// OS Stubs for functions declared in sys/wait.h
//
#include "vm_enclave_layer.h"

extern "C" {

typedef int idtype_t;
struct siginfo_t;
typedef	long id_t;

int waitpid(pid_t, int*, int) {
    jni_throw("STUB: waitpid");
    errno = ENOSYS;
    return -1;
}

int waitid(idtype_t idtype, id_t id, siginfo_t *infop, int options) {
    jni_throw("STUB: waitid");
    errno = ENOSYS;
    return -1;
}

}
