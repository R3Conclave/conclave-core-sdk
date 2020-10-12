#include <gtest/gtest.h>
#include <string>

#define UNIT_TEST

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

#undef enclave_trace
#define enclave_trace(...) \
    do {                   \
    } while (0)

#define THROW_MEMORY

#include <memory_manager.cpp>

using namespace std;

TEST(memory_manager, alloc_empty) {
    INIT_GLOBAL();
    bjni_throw = false;
    jni_throw_msg = nullptr;
    conclave::MemoryManager::instance().alloc(0);
    string msg = ALLOC_ERROR_STR;
    
    EXPECT_TRUE(bjni_throw);

    EXPECT_PRED2([](const string& msg, const string& jni_throw_msg) { return msg == jni_throw_msg; }, msg, jni_throw_msg);
}

TEST(memory_manager, alloc_and_free) {
    INIT_GLOBAL();

    // Allocate 4096 bytes in p.
    auto* p = conclave::MemoryManager::instance().alloc(4096);

    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());

    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    auto res = conclave::MemoryManager::instance().free(p, 4096);
    EXPECT_TRUE(res == 0);
    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
}

TEST(memory_manager, alloc_and_free_in_steps) {
    INIT_GLOBAL();

    // Allocate 1024 bytes in p. (it actually allocates one full 4096 bytes page.)
    auto* p = conclave::MemoryManager::instance().alloc(1024);

    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());

    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    size_t step = 16;
    for (size_t i = step; i <= 4096; i = i + step) {
        EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
        auto res = conclave::MemoryManager::instance().free(p, step);
        EXPECT_TRUE(res == 0);
    }

    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
}

TEST(memory_manager, alloc_and_free_partial) {
    INIT_GLOBAL();

    // Allocate 4096 bytes in p;
    auto* p = conclave::MemoryManager::instance().alloc(4096);

    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());

    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    // Free 4095 bytes.
    auto res = conclave::MemoryManager::instance().free(p, 4095);
    EXPECT_TRUE(res == 0);
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());

    // Free the remaining byte.
    res = conclave::MemoryManager::instance().free(p, 1);
    EXPECT_TRUE(res == 0);

    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
}

TEST(memory_manager, alloc_and_free_invalid) {
    INIT_GLOBAL();

    // Allocate 4096 bytes in p;
    auto* p = conclave::MemoryManager::instance().alloc(4096);

    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());

    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    // Free 4095 bytes.
    auto res = conclave::MemoryManager::instance().free(p, 4095);
    EXPECT_TRUE(res == 0);
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    EXPECT_FALSE(bjni_throw);

    // Free 2 bytes.
    res = conclave::MemoryManager::instance().free(p, 2);
    EXPECT_TRUE(res == 0);

    EXPECT_TRUE(bjni_throw);

    string msg = FREE_UNCOMMIT_ERROR_STR;
    EXPECT_PRED2([](const string& msg, const string& jni_throw_msg) { return msg == jni_throw_msg; }, msg, jni_throw_msg);
}

TEST(memory_manager, alloc_and_free_partial_invalid) {
    INIT_GLOBAL();

    // Allocate 4096 bytes in p;
    auto* p = conclave::MemoryManager::instance().alloc(4096);

    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());

    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    // Free 4095 bytes.
    auto res = conclave::MemoryManager::instance().free(p, 4095);
    EXPECT_TRUE(res == 0);
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());

    // Free the remaining byte.
    res = conclave::MemoryManager::instance().free(p, 1);
    EXPECT_TRUE(res == 0);

    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
    EXPECT_FALSE(bjni_throw);

    // Free one more.
    res = conclave::MemoryManager::instance().free(p, 1);
    EXPECT_TRUE(res == 0);
    EXPECT_TRUE(bjni_throw);

    string msg = FREE_ALLOC_ERROR_STR;
    EXPECT_PRED2([](const string& msg, const string& jni_throw_msg) { return msg == jni_throw_msg; }, msg, jni_throw_msg);
}

TEST(memory_manager, alloc_many_free_many) {
    INIT_GLOBAL();

    const size_t count = 10000;
    std::vector<void*> pointer_vec(count);

    for (size_t i = 0; i < count; ++i) {
        pointer_vec[i] = conclave::MemoryManager::instance().alloc(1);
        ASSERT_TRUE(pointer_vec[i] != reinterpret_cast<void*>(-1));
        EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    }

    for (auto p : pointer_vec) {
        EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
        auto res = conclave::MemoryManager::instance().free(p, page_size);
        EXPECT_TRUE(res == 0);
    }

    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
}

TEST(memory_manager, alloc_many_free_many_in_steps) {
    INIT_GLOBAL();

    const size_t count = 10000;
    std::vector<void*> pointer_vec(count);

    for (size_t i = 0; i < count; ++i) {
        pointer_vec[i] = conclave::MemoryManager::instance().alloc(1);
        ASSERT_TRUE(pointer_vec[i] != reinterpret_cast<void*>(-1));
        EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    }

    for (auto p : pointer_vec) {
        size_t step = 256;
        for (size_t i = step; i <= 4096; i = i + step) {
            EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
            auto res = conclave::MemoryManager::instance().free(p, step);
            EXPECT_TRUE(res == 0);
        }
    }

    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
}

TEST(memory_manager, alloc_many_free_many_in_steps_reverse) {
    INIT_GLOBAL();

    const size_t count = 10000;
    std::vector<void*> pointer_vec(count);

    for (size_t i = 0; i < count; ++i) {
        pointer_vec[i] = conclave::MemoryManager::instance().alloc(1);
        ASSERT_TRUE(pointer_vec[i] != reinterpret_cast<void*>(-1));
        EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    }

    for_each(pointer_vec.rbegin(), pointer_vec.rend(),
             [](void* p) {
                 size_t step = 256;
                 for (size_t i = step; i <= 4096; i = i + step) {
                     EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
                     auto res = conclave::MemoryManager::instance().free(p, step);
                     EXPECT_TRUE(res == 0);
                 }
             });
    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
}

TEST(memory_manager, free_nullptr) {
    INIT_GLOBAL();

    auto res = conclave::MemoryManager::instance().free(nullptr, 1);
    EXPECT_TRUE(res == 0);
    EXPECT_TRUE(bjni_throw);

    string msg = FREE_ALLOC_ERROR_STR;
    EXPECT_PRED2([](const string& msg, const string& jni_throw_msg) { return msg == jni_throw_msg; }, msg, jni_throw_msg);
}

TEST(memory_manager, free_unaligned_wrong_ptr) {
    INIT_GLOBAL();

    auto res = conclave::MemoryManager::instance().free((void*)(0x1), 15);
    EXPECT_TRUE(res == -1);
    EXPECT_TRUE(errno == EINVAL);
    EXPECT_TRUE(bjni_throw);

    string msg = FREE_ALIGN_ERROR_STR;
    EXPECT_PRED2([](const string& msg, const string& jni_throw_msg) { return msg == jni_throw_msg; }, msg, jni_throw_msg);
}

TEST(memory_manager, free_aligned_wrong_ptr) {
    INIT_GLOBAL();
    conclave::MemoryManager::instance().alloc(1);
    auto res = conclave::MemoryManager::instance().free((void*)(page_size), 15);
    EXPECT_TRUE(res == 0);
    EXPECT_TRUE(bjni_throw);

    string msg = FREE_ALLOCRGN_ERROR_STR;
    EXPECT_PRED2([](const string& msg, const string& jni_throw_msg) { return msg == jni_throw_msg; }, msg, jni_throw_msg);
}

TEST(memory_manager, alloc_and_free_before_allocation) {
    INIT_GLOBAL();

    auto* p = conclave::MemoryManager::instance().alloc(4096);
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    // Free exactly in a position 4096 bytes before the first allocation.
    auto res = conclave::MemoryManager::instance().free(reinterpret_cast<void*>(reinterpret_cast<uint64_t>(p)-4096), 4096);
    EXPECT_TRUE(res == 0);
    EXPECT_TRUE(bjni_throw);

    string msg = FREE_ALLOCRGN_ERROR_STR;
    EXPECT_PRED2([](const string& msg, const string& jni_throw_msg) { return msg == jni_throw_msg; }, msg, jni_throw_msg);
}

TEST(memory_manager, alloc_and_free_5k) {
    INIT_GLOBAL();

    auto* p = conclave::MemoryManager::instance().alloc(5000);
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    auto res = conclave::MemoryManager::instance().free(p, 8192);

    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
    EXPECT_TRUE(res == 0);
    EXPECT_FALSE(bjni_throw);
}

TEST(memory_manager, alloc_and_free_steps_5k) {
    INIT_GLOBAL();

    auto* p = conclave::MemoryManager::instance().alloc(5000);
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    size_t step = 16;
    for (size_t i = step; i <= 8192; i = i + step) {
        EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
        auto res = conclave::MemoryManager::instance().free(p, step);
        EXPECT_FALSE(bjni_throw);
        EXPECT_TRUE(res == 0);
    }
    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
}

TEST(memory_manager, alloc_and_free_before_allocation_mid_region) {
    INIT_GLOBAL();

    auto* p = conclave::MemoryManager::instance().alloc(8193);  // 8193 bytes! Meaning 12288 total allocated.
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    ASSERT_TRUE(p != reinterpret_cast<void*>(-1));

    auto mid = reinterpret_cast<void*>(reinterpret_cast<uint64_t>(p) + 4096);  // mid = p + 4192 bytes offset.

    auto* q = conclave::MemoryManager::instance().alloc(1024, mid);
    ASSERT_TRUE(mid == q);
    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    EXPECT_FALSE(bjni_throw);

    // Free partial.
    auto res = conclave::MemoryManager::instance().free(mid, 8192);

    EXPECT_FALSE(conclave::MemoryManager::instance().is_empty());
    EXPECT_TRUE(res == 0);
    EXPECT_FALSE(bjni_throw);

    // Free the remaining.
    res = conclave::MemoryManager::instance().free(mid, 4096);
    EXPECT_TRUE(conclave::MemoryManager::instance().is_empty());
    EXPECT_TRUE(res == 0);
    EXPECT_FALSE(bjni_throw);
}

TEST(memory_manager, alloc_mid_region_with_following_region) {
    INIT_GLOBAL();

    auto* p1 = conclave::MemoryManager::instance().alloc(8192);
    auto* p2 = conclave::MemoryManager::instance().alloc(8192);
    auto* pmin = std::min(p1, p2);

    ASSERT_TRUE(p1);
    ASSERT_TRUE(p2);
    ASSERT_TRUE(p1 != p2);
    ASSERT_TRUE((pmin == p1) || (pmin == p2));
    auto* p = conclave::MemoryManager::instance().alloc(1024, static_cast<unsigned char*>(pmin) + 1024);
    EXPECT_TRUE(p != reinterpret_cast<void*>(-1));

    EXPECT_FALSE(bjni_throw);
}

int main(int argc, char **argv) {
    testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
