//
// OS Stubs for functions declared in spawn.h
//
#include "vm_enclave_layer.h"

extern "C" {

struct posix_spawnattr_t;
struct posix_spawn_file_actions_t;

int posix_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *file_actions, const posix_spawnattr_t *attrp, char *const argv[], char *const envp[]) {
    enclave_trace("posix_spawn\n");
    errno = ENOSYS;
    return -1;
}

}
