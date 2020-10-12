//
// Memory managements for emulating mmap and munmap
//
#include "memory_manager.h"

#include "conclave_tools.h"

#ifndef UNIT_TEST
#include "vm_enclave_layer.h"
#else
#include <malloc.h> // For memalign().
#endif

#ifdef LOG_MEMORY
#define MEM_LOG(...) enclave_trace(__VA_ARGS__)
#else
#define MEM_LOG(...) \
    do {             \
    } while (0)
#endif

#ifdef THROW_MEMORY
#define JNI_THROW(...) jni_throw(__VA_ARGS__)
#else
#define JNI_THROW(...) \
    do {               \
    } while (0)
#endif

using namespace conclave;

constexpr auto page_size = 4096;
constexpr auto ALLOC_ERROR_STR = "No memory region allocated";
constexpr auto FREE_ALLOC_ERROR_STR = "Attempt to free unallocated memory";
constexpr auto FREE_ALIGN_ERROR_STR = "Attempt to free unaligned memory";
constexpr auto FREE_UNCOMMIT_ERROR_STR = "Failed to uncommit pages";
constexpr auto FREE_ALLOCRGN_ERROR_STR = "Attempt to free unallocated memory region";

/**
 * Creates a MemoryRegion which represents an allocated, committed virtual address range. This
 * class takes ownership of the memory buffer and frees it on destruction.
 */
class conclave::MemoryRegion {
private:
    void*  mem_base_;
    const size_t initial_size_;
    size_t committed_;

public:

    /**
     * Constructor.
     * 
     * @param p Pointer to the memory region.
     * @param size The size in bytes in the memory region.
     */
    MemoryRegion(void* p, size_t size) : mem_base_(p), initial_size_(size), committed_(size) {
        MEM_LOG("Memory region 0x%016llX : Allocating %d bytes\n", reinterpret_cast<uint64_t>(p), size);
    }

    ~MemoryRegion() {
        MEM_LOG("Freeing %d bytes\n", initial_size_);
        free(mem_base_);
    }

    /**
     * Uncommits and frees part of the allocated memory region. In theory the region size can
     * be reduced to exclude the freed region however we leave it allocated until the entire
     * region is freed.
     * 
     * @param size The size to be uncommitted.
     */
    bool uncommit(size_t size) {
        if (committed_ >= size) {
            committed_ -= size;
            MEM_LOG("Memory region 0x%016llX : Uncommitting %d bytes\n", reinterpret_cast<uint64_t>(mem_base_), size);
            return true;          
        }
        MEM_LOG("Memory region 0x%016llX : FAILED Uncommitting %d bytes  because the number of bytes left is %d!\n",
                      reinterpret_cast<uint64_t>(mem_base_), size, committed_);
        return false;
    }

    /**
     * @return The original size in bytes.
     */
    size_t initial_size() const {
        return initial_size_;
    }

    /**
     * @return The number of bytes committed.
     */
    size_t committed() const {
        return committed_;
    }

    /**
     * @return true if the memory region is entirelly uncommitted.
     */
    bool is_empty() const {
        return committed_ == 0;
    }
};

MemoryManager& MemoryManager::instance() {
    static MemoryManager instance;
    return instance;
}

/**
 * Allocates alligned memory, or retrieves p if it points to a valid allocated memory and has enough size to suit the request.
 * 
 * @param size Requested size to be allocated.
 * @param p If set to nullptr, then a new allocation will be made, otherwise a pointer to a place in memory. 
 * 
 * @return if p = nullptr, the address the newly allocated memory;
 *         if p != nullptr, then it will return p if it points to a valid allocated place in memory;
 *         -1 in case more details can be seen in "errno".
 */
void* MemoryManager::alloc(size_t size, void* p) {
    void* ret = reinterpret_cast<void*>(-1);
    if (!size) {
        JNI_THROW(ALLOC_ERROR_STR);
        return ret;
    }

    if (p) {
        // Just another helper lambda to avoid repeating code.
        auto check_within_and_set = [&](const map_regions_it& it) {
            // Check if p and p + size are within the region boundaries.
            if ((it->first <= p) && 
            ((reinterpret_cast<uint64_t>(it->first) + it->second->initial_size()) >= reinterpret_cast<uint64_t>(p) + size)) {
                ret = p;
            } else {
                ret = reinterpret_cast<void*>(-1);
                errno = EINVAL;  // "Invalid argument".
                MEM_LOG(
                    "MemoryManager::alloc(size=%d(0x%08X), p=0x%016llX)=0x%016llX : "
                    "FAILED as memory region 0x%016llX as it doesn't match the memory address!\n",
                    size, size, reinterpret_cast<uint64_t>(p), reinterpret_cast<uint64_t>(ret),
                    reinterpret_cast<uint64_t>(it->first));
                JNI_THROW(ALLOC_ERROR_STR);
            }
        };
        {
            std::lock_guard<std::mutex> lock(mem_mutex_);
            if (!regions_.size()) {
                errno = EINVAL;  // "Invalid argument".
                MEM_LOG("MemoryManager::alloc(size=%d(0x%08X), p=0x%016llX)=0x%016llX : FAILED no memory region allocated!\n",
                        size, size, reinterpret_cast<uint64_t>(p), reinterpret_cast<uint64_t>(ret));
                JNI_THROW(ALLOC_ERROR_STR);
                return ret;
            }
            auto cur_region = regions_.lower_bound(p);
            if (cur_region != regions_.end()) {
                if (cur_region->first != p && cur_region != regions_.begin())
                    --cur_region;
                check_within_and_set(cur_region);
            } else {
                if (cur_region != regions_.begin())
                    --cur_region;
                check_within_and_set(cur_region);
            }
        }
    } else {
        // We keep track of the memory allocation in pages because the caller assumes
        // for example that if they commit 100 bytes they can free it by uncommitting 4k bytes.

        // Round the allocation to a multiple of page_size (currently 4k).
        size_t allocation_size = ((size + page_size - 1) / page_size) * page_size;
        if ((ret = memalign(page_size, allocation_size))) {
            // We want to keep the lock time as minimum as possible, so allocate 1st and transfer the pointer to the map.
            try {
                auto memory_region = std::make_unique<MemoryRegion>(ret, allocation_size);
                {
                    std::lock_guard<std::mutex> lock(mem_mutex_);
                    regions_[ret] = std::move(memory_region);
                }
            } catch (...) { 
                // For any reason that may happen, we catch the exception and return (void*)-1.
                // errno will be set to the appropriate reason.
                MEM_LOG(
                    "MemoryManager::alloc(size=%d(0x%08X), p=0x%016llX)=0x%016llX : FAILED errno=%d\n",
                    size, size, reinterpret_cast<uint64_t>(p), reinterpret_cast<uint64_t>(ret), errno);
                ret = reinterpret_cast<void*>(-1);
            }
        }
    }
    MEM_LOG("MemoryManager::alloc(size=%d(0x%08X), p=0x%016llX)=0x%016llX\n",
                  size, size, reinterpret_cast<uint64_t>(p), reinterpret_cast<uint64_t>(ret));

    return ret;
}

/**
 * Uncommits the region of memory pointed by *p. It deallocates (frees) it if the whole MemoryRegion has been uncommitted.
 * 
 * @param p Pointer place in memory to be deallocated.
 * @param size Size to be deallocated.
 * 
 * @return -1 = "only for unaligned memory, like munmap does!" more details can be seen in "errno"
 *          0 = othwerwise (this can't really be trusted to verify if a "free" succeeded or not).
 */
int MemoryManager::free(void* p, size_t size) {
    // Frees must always be page aligned
    if ((reinterpret_cast<uint64_t>(p) % page_size) != uint64_t(0)) {
        JNI_THROW(FREE_ALIGN_ERROR_STR);
        errno = EINVAL;
        return -1; // return 0, as munmap would do for a nullptr.
    }

    std::unique_ptr<MemoryRegion> memory_region;

    // Just another helper lambda.
    auto uncommit_erase = [&](const map_regions_it& it) {  
        // Check if p and p + size are within the region boundaries.
        if ((it->first <= p) &&
            ((reinterpret_cast<uint64_t>(it->first) + it->second->initial_size()) >= reinterpret_cast<uint64_t>(p) + size)) {
            // Uncommit.
            if (it->second->uncommit(size)) {
                MEM_LOG("MemoryManager::free(p=0x%016llX, size=%d(0x%08X)) : %d committed bytes left\n",
                              reinterpret_cast<uint64_t>(p), size, size, it->second->committed());
                // Verify if it's empty, if so, erase it.
                if (it->second->is_empty()) {
                    memory_region = std::move(it->second);  // To be deleted when memory_region goes out of scope.
                    MEM_LOG("MemoryManager::free(p=0x%016llX, size=%d(0x%08X)) : Erasing memory region 0x%016llX\n",
                                  reinterpret_cast<uint64_t>(p), size, size, it->first);
                    regions_.erase(it);
                }
            } else {
                MEM_LOG("MemoryManager::free(p=0x%016llX, size=%d(0x%08X)) : FAILED to uncommit!\n ",
                              reinterpret_cast<uint64_t>(p), size, size, it->first);
                JNI_THROW(FREE_UNCOMMIT_ERROR_STR);
            }
        } else {
            MEM_LOG(
                "MemoryManager::free(p=0x%016llX, size=%d(0x%08X)) : "
                "FAILED as memory region 0x%016llX as it doesn't match the memory address!\n ",
                reinterpret_cast<uint64_t>(p), size, size, it->first);
            JNI_THROW(FREE_ALLOCRGN_ERROR_STR);
        }
    };

    // Find the nearest region to the freed address.
    {
        std::lock_guard<std::mutex> lock(mem_mutex_);
        if (!regions_.size()) {
            MEM_LOG("MemoryManager::free(p=0x%016llX, size=%d(0x%08X)) : FAILED, THERE'S NO MEMORY REGION ALLOCATED!\n",
                          reinterpret_cast<uint64_t>(p), size, size);
            JNI_THROW(FREE_ALLOC_ERROR_STR);
            return 0;
        }

        auto cur_region = regions_.lower_bound(p);
        if (cur_region != regions_.end()) {
            if (cur_region->first != p && cur_region != regions_.begin())
                --cur_region;
            uncommit_erase(cur_region);
        } else {
            if (cur_region != regions_.begin())
                --cur_region;
            uncommit_erase(cur_region);
        }
    }
    return 0;
}

/**
 * Clears all memory allocated.
 */
void MemoryManager::clear() {
    std::lock_guard<std::mutex> lock(mem_mutex_);
    regions_.clear();
}

/**
 * @return true if there's no allocated memory.
 */
bool MemoryManager::is_empty() {
    std::lock_guard<std::mutex> lock(mem_mutex_);
    return regions_.size() == 0;
}