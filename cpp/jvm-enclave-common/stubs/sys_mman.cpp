//
// OS Stubs for functions declared in sys/mman.h
//
#include "vm_enclave_layer.h"
#include "memory_manager.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(madvise);
STUB(mincore);

extern "C" {

void *mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset) {
    if (addr != nullptr) {
        enclave_trace("mmap: addr not null\n");
        return nullptr;
    }
    void* p = conclave::MemoryManager::instance().alloc(length);
    enclave_trace("mmap(0x%llX, 0x%lX, 0x%X, 0x%X, 0x%X, 0x%lX)=%llX\n", (unsigned long long)addr, length, (unsigned)prot, (unsigned)flags, fd, offset, (unsigned long long)p);
    return p;
}

int munmap(void *addr, size_t length) {
    enclave_trace("munmap(0x%llX, 0x%lX)\n", (unsigned long long)addr, length);
    conclave::MemoryManager::instance().free(addr, length);
    return 0;
}

}
