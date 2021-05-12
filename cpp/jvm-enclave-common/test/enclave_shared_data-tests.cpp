#include <gtest/gtest.h>
#include <string>

#define UNIT_TEST

// SGX SDK mock functions
#define SGX_SUCCESS 0

int sgx_is_outside_enclave(const void *addr, size_t size);
int shared_data_ocall(void** sharedBufferAddr);

#define NS_PER_SEC (1000 * 1000 * 1000)

/**
 * MACROS to replace function definitions and and global variables to store exception status and messages.
 */
thread_local bool bjni_throw = false;
thread_local const char* jni_throw_msg = nullptr;
#define INIT_GLOBAL()                                                \
    do {                                                             \
        bjni_throw = 0;                                              \
        jni_throw_msg = 0;                                           \
        conclave::MemoryManager::instance().clear();                 \
        ASSERT_TRUE(conclave::MemoryManager::instance().is_empty()); \
    } while (0)

#undef jni_throw
#define jni_throw(x, ...)  \
    do {                   \
        jni_throw_msg = x; \
        bjni_throw = true; \
    } while (0)

// Include the module under test.
#include <enclave_shared_data.cpp>

using namespace std;
using namespace r3::conclave;

static bool is_outside_enclave = true;
static SharedData sd;

int sgx_is_outside_enclave(const void *addr, size_t size) {
    return is_outside_enclave;
}

int shared_data_ocall(void** sharedBufferAddr) {
    *sharedBufferAddr = (void*)&sd;
    return SGX_SUCCESS;
}

TEST(enclave_shared_data, real_time) {
    
    // Just set a time and make sure it is reflected in the result.
    sd.real_time = 1234;

    EnclaveSharedData& esd = EnclaveSharedData::instance();
    EXPECT_EQ(esd.real_time(), 1234);
}

TEST(enclave_shared_data, real_time_forwards) {
    EnclaveSharedData& esd = EnclaveSharedData::instance();
    
    // Just set a time, check it then move it forwards and check the time is updated
    for (uint64_t tm = 2000; tm < 2100; ++tm) {
        sd.real_time = tm;
        EXPECT_EQ(esd.real_time(), tm);
    }
}

TEST(enclave_shared_data, real_time_backwards) {
    EnclaveSharedData& esd = EnclaveSharedData::instance();
    
    // Check that if the time goes backwards, the latest time is returned
    sd.real_time = 500;
    EXPECT_EQ(esd.real_time(), 500);
    sd.real_time = 450;
    EXPECT_EQ(esd.real_time(), 500);
    sd.real_time = 510;
    EXPECT_EQ(esd.real_time(), 510);
    
}

int main(int argc, char **argv) {
    testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
