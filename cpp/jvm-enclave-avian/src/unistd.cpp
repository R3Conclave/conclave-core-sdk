#include "unistd.h"
#include "vm_enclave_layer.h"

extern "C" {

ssize_t read_impl(int, void*, size_t) {
    return (ssize_t)-1;
}

ssize_t pread_impl(int, void*, size_t, off_t) {
    return (ssize_t)-1;
}

int close_impl(int) {
    return 0;
}

off64_t lseek64_impl(int, off64_t, int) {
    return -1;
}

ssize_t write_impl(int, const void *, size_t) {
    return (ssize_t)-1;
}

ssize_t pwrite_impl(int, const void *, size_t, off_t) {
    return (ssize_t)-1;
}
}
