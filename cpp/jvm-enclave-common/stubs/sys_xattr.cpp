//
// OS Stubs for functions declared in sys/xattr.h
//
#include "vm_enclave_layer.h"

extern "C" {

ssize_t fgetxattr(int fd, const char *name, void *value, size_t size) {
	enclave_trace("fgetxattr\n");
	errno = ENOTSUP;
	return -1;
}

}