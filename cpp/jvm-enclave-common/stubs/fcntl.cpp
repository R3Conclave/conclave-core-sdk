//
// OS Stubs for functions declared in fcntl.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"

extern "C" {

int fcntl(int fd, int, ... ) {
    jni_throw("STUB: fcntl(%d)", fd);
    errno = -ENOSYS;
    return -1;
}

int open(const char* __file, int) {
    conclave::File* file = conclave::FileManager::instance().open(__file);
    if (file) {
        return file->handle();
    }
    return -1;
}

int open64 (const char* __file, int , ...) {
    conclave::File* file = conclave::FileManager::instance().open(__file);
    if (file) {
        return file->handle();
    }
    return -1;
}

}
