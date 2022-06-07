cmake_minimum_required(VERSION 3.20.5)

project(fatfs_enclave)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Wno-unused-parameter -fpermissive")

GET_DIRECTORY_PROPERTY(EDGER8R_INCLUDE_DIR DIRECTORY ${PROJECT_SOURCE_DIR}/../../linux-sgx DEFINITION EDGER8R_INCLUDE_DIR)

add_library(fatfs_enclave
  ../common/src/diskio.cpp
  ../common/src/ff.cpp
  ../common/src/ffsystem.cpp
  ../common/src/ffunicode.cpp
  ../common/src/fatfs_file_manager.cpp
  ../enclave/src/disk.cpp
  ../enclave/src/inmemory_disk.cpp
  ../enclave/src/persistent_disk.cpp
  ../enclave/src/api.cpp  
  )

target_include_directories(fatfs_enclave PUBLIC
  ../common/include
  ../enclave/include
  ../../substratevm/include
  ../../jvm-edl/build/edl/enclave
  ../../jvm-host-enclave-common/include
  ../../jvm-enclave-common/include
  ${JNI_INCLUDE_DIRS}
  $ENV{JAVA_HOME}/include
  $ENV{JAVA_HOME}/include/linux
  )
target_compile_options(fatfs_enclave PUBLIC -nostdinc -nostdinc++ -fPIC)
target_link_libraries(fatfs_enclave
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux
        # Required for headers, which don't vary between sim/debug/release
        # Doesn't actually matter which we use, but we have to specify one of them.
        # jvm_enclave_common_(debug|simulation|release) produces a set of archives, and also their associated headers.
        # The headers don't vary between versions, so we just add release as the dependency.
        # It's a bit yucky, but it saves us needing three separate builds of fatfs like we have for jvm_enclave_common_*
        jvm_enclave_common_release
        linux-sgx_headers
        linux-sgx_tstdc_headers)

get_property(ENCLAVE_SOURCES TARGET fatfs_enclave PROPERTY SOURCES)
determinise_compile(${ENCLAVE_SOURCES})
