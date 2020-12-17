//
// OS Stubs for functions declared in fcntl.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"
#include "fcntl.h"

extern "C" {

int fcntl(int fd, int, ... ) {
    jni_throw("STUB: fcntl(%d)", fd);
    errno = -ENOSYS;
    return -1;
}

int open(const char* __file, int oflag) {
    conclave::File* file = conclave::FileManager::instance().open(__file);
    if (file) {
        return file->handle();
    }
    int fd = conclave::FileManager::instance().allocateHandle();
    return open_impl(__file, oflag, fd);
}

int open64 (const char* __file, int oflag, ...) {
    return open(__file, oflag);
}

}
