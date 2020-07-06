//
// OS Stubs for functions declared in libc.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"

extern "C" {

int __vfprintf_chk(FILE *stream, int n, const char *s, va_list va) {
    int res = -1;
    conclave::File* file = conclave::FileManager::instance().fromFILE(stream);
    if (file) {
        char msg[512];
        res = vsnprintf((char*)msg, sizeof(msg), s, va);
        res = file->write(1, res, msg);
    } 
    else {
        char msg[512];
        vsnprintf(msg, sizeof(msg), s, va);
        enclave_trace("Attempt to write to file %s: %s\n", (char*)stream, msg);
    }
    return res;
}

int __fprintf_chk(FILE *f, int n, const char *s, ...) {
    va_list va;
    va_start(va, s);
    int res = __vfprintf_chk(f, n, s, va);
    va_end(va);
    return res;
}

}
