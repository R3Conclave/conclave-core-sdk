//
// OS Stubs for functions declared in dirent.h
//
#include "vm_enclave_layer.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker

extern "C" {

    void* opendir(const char* name) {
	enclave_trace("opendir\n");
	int err = 0;
	void* res = opendir_impl(name, err);
	errno = err;
	return res;
    }

    
    struct dirent* readdir(void* dirp) {
	enclave_trace("readdir\n");
	int err = 0;
	struct dirent* res = readdir_impl(dirp, err);
	errno = err;
	return res;
    }


    struct dirent64* readdir64(void* dirp) {
	enclave_trace("readdir64\n");
	int err = 0;
	struct dirent64* res = readdir64_impl(dirp, err);
	errno = err;
	return res;
    }


    int readdir_r(void* dirp, struct dirent* entry, struct dirent** result) {
	enclave_trace("readdir_r\n");
	*result = nullptr;
	int err = 0;
	struct dirent* readdir_res = readdir_impl(dirp, err);

	if (readdir_res != nullptr) {
	    *result = readdir_res;
	    return 0;
	} else {
	    errno = err;
	    return -1;
	}
    }


    int readdir64_r(void* dirp, struct dirent64* entry, struct dirent64** result) {
	enclave_trace("readdir64_r\n");
	*result = nullptr;
	int err = 0;
	struct dirent64* readdir_res = readdir64_impl(dirp, err);

	if (readdir_res != nullptr) {
	    *result = readdir_res;
	    return 0;
	} else {
	    errno = err;
	    return -1;
	}
    }


    int closedir(void* dirp) {
	enclave_trace("closedir\n");
	int err = 0;
	const int res = closedir_impl(dirp, err);
	errno = err;
	return res;
    }
}
