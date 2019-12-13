#include <os_support.h>
#include <avian/system/memory.h>
#include <avian/util/assert.h>
#include <aex_assert.h>

namespace {
    constexpr size_t executableSectionSize = 30 * 1024 * 1024; // from avian/../compile.cpp
    char executableSection[executableSectionSize] __attribute__((aligned(4096), section(".rwx_data,\"wax\",@progbits#"))) = { 0 };
}

namespace avian {
    namespace system {
        util::Slice<uint8_t> Memory::allocate(size_t sizeInBytes, Permissions permissions)
        {
            void* p = nullptr;
            if (permissions & Permissions::Execute) {
                // This is the executable area needed by Avian
                aex_assert(sizeInBytes == executableSectionSize);
                p = executableSection;
            } else {
                p = malloc(sizeInBytes);
            }

            if (p == nullptr) {
                return util::Slice<uint8_t>(0, 0);
            } else {
                return util::Slice<uint8_t>(static_cast<uint8_t*>(p), sizeInBytes);
            }
        }

        void Memory::free(util::Slice<uint8_t> slice)
        {
            if (slice.begin() != reinterpret_cast<unsigned char*>(&executableSection[0])) {
                ::free(slice.begin());
            }
        }

    }  // namespace system
}  // namespace avian
