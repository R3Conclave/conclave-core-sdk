//
// Memory managements for emulating mmap and munmap
//
#include "memory_manager.h"
#include <mutex>

using namespace conclave;

//#define LOG_MEMORY

// Memory manager singleton
MemoryManager* MemoryManager::_instance;
std::mutex mem_mutex;

class conclave::MemoryRegion {
private:
    void*                   _mem_base;
    size_t                  _mem_length;
    size_t                  _committed;

public:
    // Creates a MemoryRegion which represents an allocated, committed virtual address range. This
    // class takes ownership of the memory buffer and frees it on destruction.
    // The length is in 4K pages.
    MemoryRegion(void* p, size_t length) : _mem_base(p), _mem_length(length), _committed(length) {
#ifdef LOG_MEMORY
        enclave_trace("Allocating %lX pages\n", length);
#endif
    }

    ~MemoryRegion() {
#ifdef LOG_MEMORY
        enclave_trace("Freeing %lX pages\n", mem_length);
#endif
        free(_mem_base);
    }

    // Uncommits and frees part of the allocated memory region. In theory the region size can
    // be reduced to exclude the freed region however we leave it allocated until the entire
    // region is freed.
    // The length is in 4K pages.
    void* uncommit(void*p , size_t length) {
        if (_committed >= length) {
            _committed -= length;
#ifdef LOG_MEMORY
            enclave_trace("Uncommitting %lX pages\n", length);
#endif            
        }
    }

    bool is_empty() {
        return _committed == 0;
    }
};

MemoryManager& MemoryManager::instance() {
    std::lock_guard<std::mutex> lock(mem_mutex);
    if (!_instance) {
        _instance = new MemoryManager;
    }
    return *_instance;
}

void* MemoryManager::alloc(unsigned long size) {
    void* p = memalign(4096, size);
    if (p) {
        // We keep track of the memory allocation in pages because the caller assumes
        // for example that if they commit 100 bytes they can free it by uncommitting 4k bytes.
        unsigned long pages = (size + 4095) / 4096;

        std::lock_guard<std::mutex> lock(mem_mutex);
        _regions[(unsigned long long)p] = new MemoryRegion(p, pages);
    }
    return p;
}

void MemoryManager::free(void* p, unsigned long size) {
    // Frees must always be page aligned
    if (((unsigned long long)p % 4096) != 0ull) {
        jni_throw("Attempt to free unaligned memory");
    }

    // Get the number of pages. 
    unsigned long pages = (size + 4095) / 4096;

    // Find the nearest region to the freed address.
    std::lock_guard<std::mutex> lock(mem_mutex);
    auto cur_region = _regions.end();
    for (auto it = _regions.begin(); it != _regions.end(); ++it) {
        if (it->first > (unsigned long long)p) {
            break;
        }
        cur_region = it;
    }
    if (cur_region != _regions.end()) {
        if (cur_region->second->uncommit(p, pages)) {
            if (cur_region->second->is_empty()) {
                delete cur_region->second;
                _regions.erase(cur_region);
            }
        }
    }
}
