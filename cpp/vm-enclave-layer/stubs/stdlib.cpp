//
// OS Stubs for functions declared in stdlib.h
//
#include "vm_enclave_layer.h"

extern "C" {

// Just some dummy environment variables.
const char *_environ[] = { "HOME=/", "HOSTNAME=enclave", NULL };
char **environ = (char**) environ;

char *getenv(const char *varname) {
    // Could do a proper search here, but this isn't the right way to pass data into an enclave anyway.
    if (!strcmp(varname, "HOME")) {
        return (char*) "/";
    } else if (!strcmp(varname, "HOSTNAME")) {
        return (char*) "enclave";
    }  else {
        return NULL;
    }
}

int putenv(char*) {
    return 0;
}

void exit(int status) {
    jni_throw("STUB: exit(%d)\n", status);
    while(1);   // Avoid warning about a noreturn function that actually returns.
}

char *realpath(const char *path, char *resolved_path) {
    if (!strcmp(path, "/."))
        return strcpy(resolved_path, "/");
    else if (!strncmp(path, "/[", 2) || !strcmp(path, "/avian-embedded/javahomeJar/lib/logging.properties") || !strcmp(path, "/avian-embedded/javahomeJar/lib"))
        return strcpy(resolved_path, path + 1);
    else {
        enclave_trace("realpath(%s)\n", path);
        return NULL;
    }
}

}
