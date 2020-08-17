//
// OS Stubs for functions declared in pwd.h
//
#include "vm_enclave_layer.h"

extern "C" {

static struct passwd {
    const char* username;
    const char* password;
    int         uid;
    int         gid;
    const char* userinfo;
    const char* homedir;
    const char* shell;
} passwd_info = {
   "enclave",               /* username */
   "",                      /* user password */
   1,                       /* user ID */
   1,                       /* group ID */
   "",                      /* user information */
   "/",                     /* home directory */
   "there is no shell"      /* shell program */
};

struct passwd *getpwuid(uid_t uid) {
    if (uid != 1)
        enclave_trace("getpwuid(%d)\n", uid);
    return &passwd_info;
}

}
