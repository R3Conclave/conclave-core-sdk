#pragma once

#include <sys/mman.h>

struct MunmapGuard {
    void *const address;
    size_t const size;
    MunmapGuard(void *address, size_t size) : address(address), size(size) {}
    ~MunmapGuard() {
        munmap(address, size);
    }
};
