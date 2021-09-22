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

    int open(const char* file_path, int oflag) {
	conclave::File* file = conclave::FileManager::instance().open(file_path);

	if (file) {
	    return file->handle();
	}
	return open_impl(file_path, oflag);
    }

    int open64 (const char* file_path, int oflag, ...) {
	return open(file_path, oflag);
    }
}
