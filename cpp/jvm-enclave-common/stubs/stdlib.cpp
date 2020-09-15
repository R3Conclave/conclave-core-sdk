//
// OS Stubs for functions declared in stdlib.h
//
#include "vm_enclave_layer.h"
#include <string>

extern "C" {

// Just some dummy environment variables.
static const char *_environ[] = { "HOME=/", "HOSTNAME=enclave", NULL };
char **environ = (char**)_environ;

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
    enclave_trace("realpath(%s)\n", path);
    std::string resolved(path);
    if (!strcmp(path, "/.")) {
        resolved = "/";
    }
    else if (!strncmp(path, "/[", 2) || !strcmp(path, "/avian-embedded/javahomeJar/lib/logging.properties") || !strcmp(path, "/avian-embedded/javahomeJar/lib")) {
        resolved = path + 1;
    }
    if (!resolved_path) {
        // The caller is responsible for freeing this. See https://man7.org/linux/man-pages/man3/realpath.3.html
        resolved_path = (char*)malloc(resolved.size() + 1);
    }
    if (resolved_path) {
        // Ideally we should restrict this to PATH_MAX but we don't know what value of PATH_MAX
        // the JDK was using. Also there is no guarantee the caller allocated the correct size
        // of buffer
        strcpy(resolved_path, resolved.c_str());
    }
    return resolved_path;
}

}
