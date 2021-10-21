//
// OS Stubs for functions declared in fcntl.h
//
#include "vm_enclave_layer.h"
#include "file_manager.h"
#include "fcntl.h"

extern "C" {

    int fcntl(int fd, int val, ... ) {
	enclave_trace("fcntl(%d, %d)\n", fd, val);
	return 0;
    }


    int open(const char* file_path, int oflag) {
	enclave_trace("open(%s, %d)\n", file_path, oflag);
	conclave::File* file = conclave::FileManager::instance().open(file_path);

	if (file) {
	    return file->handle();
	}
	int err = 0;
	const int res = open_impl(file_path, oflag, err);
	errno = err;
	return res;
    }


    int open64(const char* file_path, int oflag, ...) {
	enclave_trace("open64(%s, %d)\n", file_path, oflag);
	conclave::File* file = conclave::FileManager::instance().open(file_path);

	if (file) {
	    return file->handle();
	}
	int err = 0;
	const int res = open_impl(file_path, oflag, err);
	errno = err;
	return res;
    }
}
