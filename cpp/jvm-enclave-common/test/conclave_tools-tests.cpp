#include <gtest/gtest.h>

TEST(conclave_tools, is_bounded_array) {
    class A {};
    EXPECT_EQ(typeid(std::is_bounded_array<A>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_bounded_array<A[]>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_bounded_array<A[3]>::type), typeid(std::true_type));
    EXPECT_EQ(typeid(std::is_bounded_array<float>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_bounded_array<int>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_bounded_array<int[]>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_bounded_array<int[3]>::type), typeid(std::true_type));
}

TEST(conclave_tools, is_unbounded_array) {
    class A {};
    EXPECT_EQ(typeid(std::is_unbounded_array<A>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_unbounded_array<A[]>::type), typeid(std::true_type));
    EXPECT_EQ(typeid(std::is_unbounded_array<A[3]>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_unbounded_array<float>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_unbounded_array<int>::type), typeid(std::false_type));
    EXPECT_EQ(typeid(std::is_unbounded_array<int[]>::type), typeid(std::true_type));
    EXPECT_EQ(typeid(std::is_unbounded_array<int[3]>::type), typeid(std::false_type));
}

TEST(conclave_tools, make_unique_fundamental) {
    {
        // Basic fundamental type.
        auto p = std::make_unique<char>(3);
        EXPECT_EQ(typeid(p), typeid(std::unique_ptr<char>));
        EXPECT_NE(p, nullptr);
        EXPECT_NE(p.get(), nullptr);
        p.release();
        EXPECT_EQ(p, nullptr);
        EXPECT_EQ(p.get(), nullptr);
    }

    {
        // Funcamental unbounded array type.
        auto p = std::make_unique<char[]>(3);
        EXPECT_EQ(typeid(p), typeid(std::unique_ptr<char[]>));
        EXPECT_NE(p, nullptr);
        EXPECT_NE(p.get(), nullptr);
        p.release();
        EXPECT_EQ(p, nullptr);
        EXPECT_EQ(p.get(), nullptr);
    }
}

TEST(conclave_tools, make_unique_compound) {
    struct A {
        A() = default;
        A(int a) : a_{a} {}
        A(int a, int b) : a_{a}, b_{b} {}

        int a_ = 0xF;
        int b_ = 0xFF;
    };

    {
        // Default constructor.
        auto p = std::make_unique<A>();
        EXPECT_EQ(typeid(p), typeid(std::unique_ptr<A>));
        EXPECT_NE(p, nullptr);
        EXPECT_NE(p.get(), nullptr);
        EXPECT_EQ(p->a_, 0xF);
        EXPECT_EQ(p->b_, 0xFF);
        p.release();
        EXPECT_EQ(p, nullptr);
        EXPECT_EQ(p.get(), nullptr);
    }

    {
        // Forward a single argument.
        auto p  = std::make_unique<A>(0x1);
        EXPECT_EQ(typeid(p), typeid(std::unique_ptr<A>));
        EXPECT_NE(p, nullptr);
        EXPECT_NE(p.get(), nullptr);
        EXPECT_EQ(p->a_, 0x1);
        EXPECT_EQ(p->b_, 0xFF);
        p.release();
        EXPECT_EQ(p, nullptr);
        EXPECT_EQ(p.get(), nullptr);
    }

    {
        // Forward multiple arguments.
        auto p = std::make_unique<A>(0x2, 0x3);
        EXPECT_EQ(typeid(p), typeid(std::unique_ptr<A>));
        EXPECT_NE(p, nullptr);
        EXPECT_NE(p.get(), nullptr);
        EXPECT_EQ(p->a_, 0x2);
        EXPECT_EQ(p->b_, 0x3);
        p.release();
        EXPECT_EQ(p, nullptr);
        EXPECT_EQ(p.get(), nullptr);
    }
}

int main(int argc, char **argv) {
    testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
