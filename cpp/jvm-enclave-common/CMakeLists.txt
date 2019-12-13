project(jvm-enclave-common)

include(../Determinise.cmake)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Werror")

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
else()
    add_compile_options(-ggdb) # only -ggdb works properly
endif()

# jvm_enclave_common.a this is the library all enclave implementations share
add_library(jvm_enclave_common
        src/dlsym_symbols.cpp
        src/enclave_thread.cpp
        src/enclave_jni.cpp)
target_include_directories(jvm_enclave_common PUBLIC include)
target_compile_options(jvm_enclave_common PUBLIC -nostdinc -fPIC)
target_link_libraries(jvm_enclave_common
        jvm_enclave_edl
        jvm_host_enclave_common_enclave
        linux-sgx_headers
        linux-sgx_tstdc_headers)
add_dependencies(jvm_enclave_common linux-sgx-ext)

if(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    target_compile_definitions(jvm_enclave_common PRIVATE -DSGX_SIM)
endif()

get_property(ENCLAVE_SOURCES TARGET jvm_enclave_common PROPERTY SOURCES)
determinise_compile(${ENCLAVE_SOURCES})
