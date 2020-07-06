cmake_minimum_required(VERSION 3.2)

project(oblivium-cpp VERSION 0.1 LANGUAGES CXX)

set(CMAKE_VERBOSE_MAKEFILE ON)

find_program(CCACHE_FOUND ccache)
if(CCACHE_FOUND)
    set_property(GLOBAL PROPERTY RULE_LAUNCH_COMPILE ccache)
    set_property(GLOBAL PROPERTY RULE_LAUNCH_LINK ccache)
endif(CCACHE_FOUND)

enable_testing()
add_subdirectory(linux-sgx)
add_subdirectory(openjdk8)
add_subdirectory(avian)
add_subdirectory(zlib)
add_subdirectory(dummy)

add_subdirectory(jvm-edl)
add_subdirectory(jvm-host-enclave-common)
add_subdirectory(jvm-enclave-common)
add_subdirectory(jvm-enclave-avian)
add_subdirectory(jvm-host)
add_subdirectory(jvm-host-shared)

add_subdirectory(vm-enclave-layer)
add_subdirectory(substratevm)
