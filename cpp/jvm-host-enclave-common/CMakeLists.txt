project(jvm-host-enclave-common)

include(FindJNI)
include(Determinise)

if (NOT JNI_FOUND)
    message(FATAL_ERROR "JNI headers not found")
endif()

# This project contains two libraries both sharing the same code,
# but the enclave one is linked against tstdc and the host one against system libc

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Werror")

find_package(JNI REQUIRED)

# jvm_host_enclave_common_host.a the host side of things, linked against proper libc
add_library(jvm_host_enclave_common_host
        src/sgx_errors.cpp
        src/sgx_device_status.cpp
        src/jni_utils.cpp)
target_include_directories(jvm_host_enclave_common_host PUBLIC
        include
        ${JNI_INCLUDE_DIRS})
target_compile_options(jvm_host_enclave_common_host PUBLIC -fPIC)
target_link_libraries(jvm_host_enclave_common_host
        linux-sgx_headers)
add_dependencies(jvm_host_enclave_common_host linux-sgx-ext)

# jvm_host_enclave_common_enclave.a the enclave side of things, linked against tstdc
add_library(jvm_host_enclave_common_enclave
        src/sgx_errors.cpp
        src/sgx_device_status.cpp
        src/jni_utils.cpp)
target_include_directories(jvm_host_enclave_common_enclave PUBLIC
        ${JAVA_INCLUDE_PATH}
        ${JAVA_INCLUDE_PATH2}
        include)
target_compile_options(jvm_host_enclave_common_enclave PUBLIC -nostdinc -fPIC)
target_link_libraries(jvm_host_enclave_common_enclave
        linux-sgx_headers
        linux-sgx_tstdc_headers)
add_dependencies(jvm_host_enclave_common_enclave linux-sgx-ext)

get_property(COMMON_HOST_SOURCES TARGET jvm_host_enclave_common_host PROPERTY SOURCES)
get_property(COMMON_ENCLAVE_SOURCES TARGET jvm_host_enclave_common_enclave PROPERTY SOURCES)
determinise_compile(${COMMON_HOST_SOURCES} ${COMMON_ENCLAVE_SOURCES})
