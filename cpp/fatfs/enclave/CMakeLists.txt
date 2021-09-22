cmake_minimum_required(VERSION 3.20.5)

project(fatfs_enclave)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Wno-unused-parameter -fpermissive")

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
  add_definitions(-DNDEBUG)
elseif(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")  
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ggdb")  
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ggdb")  
else()  
  set(CMAKE_CXX_FLAGS_DEBUG "-ggdb") 
  set(CMAKE_C_FLAGS_DEBUG "-ggdb")  
endif()

GET_DIRECTORY_PROPERTY(EDGER8R_INCLUDE_DIR DIRECTORY ${PROJECT_SOURCE_DIR}/../../linux-sgx DEFINITION EDGER8R_INCLUDE_DIR)

add_library(fatfs_enclave
  ../common/src/diskio.cpp
  ../common/src/ff.cpp
  ../common/src/ffsystem.cpp
  ../common/src/ffunicode.cpp
  ../common/src/fatfs_file_manager.cpp
  ../enclave/src/fatfs_ram.cpp
  ../enclave/src/api.cpp  
  )

target_include_directories(fatfs_enclave PUBLIC
  ../common/include
  ../enclave/include
  ../../substratevm/include
  ../../jvm-edl/build/edl/enclave
  ../../jvm-host-enclave-common/include
  ../../jvm-enclave-common/include
  $ENV{JAVA_HOME}/include
  $ENV{JAVA_HOME}/include/linux
  )
target_compile_options(fatfs_enclave PUBLIC -nostdinc -nostdinc++ -fPIC)
target_link_libraries(fatfs_enclave
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux
        jvm_enclave_common
        linux-sgx_headers
        linux-sgx_tstdc_headers)

get_property(ENCLAVE_SOURCES TARGET fatfs_enclave PROPERTY SOURCES)
determinise_compile(${ENCLAVE_SOURCES})
