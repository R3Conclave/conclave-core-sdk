//
// OS Stubs for functions declared in sys/utsname.h
//
#include "vm_enclave_layer.h"

extern "C" {

static struct utsname {
    const char* sysname;
    const char* nodename;
    const char* release;
    const char* version;
    const char* machine;
#ifdef _GNU_SOURCE
    const char* domainname;
#endif
} uname_data = {
        /*sysname:*/ "linux-sgx",
        /*nodename:*/  "enclave",
        /*release:*/ "1.0",
        /*version:*/ "1.0",
        /*machine:*/ "enclave",
#ifdef _GNU_SOURCE
        /*domainname:*/ "enclave"
#endif
};

int uname(struct utsname *buf) {
    if (buf) {
        memcpy(buf, &uname_data, sizeof(struct utsname));
    } else {
        errno = -EFAULT;
        return -1;
    }
    return 0;
}


}
