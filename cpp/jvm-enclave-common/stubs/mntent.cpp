//
// OS Stubs for functions declared in mntent.h
//
#include "vm_enclave_layer.h"

extern "C" {

char *hasmntopt(const struct mntent *mnt, const char *opt) {
    enclave_trace("hasmntopt\n");
    return nullptr;
}

FILE *setmntent(const char *filename, const char *type) {
   enclave_trace("setmntent\n");
   return nullptr;
}

struct mntent *getmntent_r(FILE *streamp, struct mntent *mntbuf, char *buf, int buflen) {
    enclave_trace("getmntent_r\n");
    return nullptr;
}

int endmntent(FILE *fp) {
    enclave_trace("endmntent\n");
    return 1;
}

}
