//
// OS Stubs for functions declared in sys/mman.h
//
#include "vm_enclave_layer.h"
#include "memory_manager.h"

//////////////////////////////////////////////////////////////////////////////
// Stub functions to satisfy the linker
STUB(madvise);
STUB(mincore);

namespace {
void mmap_trace(void *addr, size_t length, int prot, int flags, int fd, off64_t offset, void *p) {
    enclave_trace("mmap(addr=0x%016llX, length=%d(0x%08X), prot=0x%08X, flags=0x%08X, fd=0x%08X, offset=%d(0x%016llX))=0x%016llX\n",
                  reinterpret_cast<uint64_t>(addr), length, length, static_cast<unsigned>(prot),
                  static_cast<unsigned>(flags), fd, offset, offset, reinterpret_cast<uint64_t>(p));
}
}  // namespace

extern "C"
{
    void *mmap64(void *addr, size_t length, int prot, int flags, int fd, off64_t offset) {
        void *p = conclave::MemoryManager::instance().alloc(length, addr);

        mmap_trace(addr, length, prot, flags, fd, offset, p);
        return p;
    }

    void *mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset) {
        return mmap64(addr, length, prot, flags, fd, offset);
    }

    int munmap(void *addr, size_t length) {
        enclave_trace("munmap(addr=0x%016llX, length=%d(0x%08X))\n", reinterpret_cast<uint64_t>(addr), length, length);
        conclave::MemoryManager::instance().free(addr, length);
        return 0;
    }

    int mprotect(void *addr, size_t len, int prot) {
        enclave_trace("mprotect\n");
        errno = EACCES;
        return -1;
    }
}
