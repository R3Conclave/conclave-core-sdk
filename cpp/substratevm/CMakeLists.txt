project(substratevm)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

# Use "-ggdb" for more gdb friendly debug info
if(${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ggdb")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ggdb")
endif()

GET_DIRECTORY_PROPERTY(EDGER8R_INCLUDE_DIR DIRECTORY ${PROJECT_SOURCE_DIR}/../linux-sgx DEFINITION EDGER8R_INCLUDE_DIR)

# SubstrateVM library which includes stubs, edl and jni implementation
add_library(substratevm
        src/edl.cpp
        src/substrate_jvm.cpp
)

target_include_directories(substratevm PUBLIC
        include
        ../jvm-edl/build/edl/enclave
        ../jvm-host-enclave-common/include
        ../jvm-enclave-common/include
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux)
target_compile_options(substratevm PUBLIC -nostdinc -nostdinc++ -fPIC)
target_link_libraries(substratevm
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux
        # Required for headers, which don't vary between sim/debug/release
        jvm_enclave_common_debug
        linux-sgx_headers
        linux-sgx_tstdc_headers)

get_property(ENCLAVE_SOURCES TARGET substratevm PROPERTY SOURCES)
determinise_compile(${ENCLAVE_SOURCES})
