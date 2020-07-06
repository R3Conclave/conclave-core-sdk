//
// Memory management for emulating mmap and munmap
//
#pragma once

#include "vm_enclave_layer.h"
#include <map>

namespace conclave {

class MemoryRegion;

/*
 * Memory manager for emulating mmap for allocating committed memory and for
 * allowing freeing of regions inside a previously allocated region.
 * SubstrateVM calls mmap to allocate new heap regions. For Windows it requests
 * a large virtual memory area and then only commits the parts it uses. For 
 * Posix (which is the version we are using) it currently only requests memory
 * that it wants committed. We need to watch out in case they change the memory
 * management strategy and adjust this accordingly.
 * 
 * The method used here to free memory is not very efficient as it iterates through
 * the entire list of allocated regions. This could be improved but in practice
 * substratevm will only be allocating a small number of regions, each time it
 * requires more heap space.
 */
class MemoryManager {
private:
    std::map<unsigned long long, MemoryRegion*> _regions;
    static MemoryManager*   _instance;

public:
    static MemoryManager& instance();

    void* alloc(unsigned long size);
    void free(void* p, unsigned long size);

};

}