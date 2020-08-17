project(substratevm)

include(../Determinise.cmake)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
else()
    add_compile_options(-ggdb) # only -ggdb works properly
endif()

GET_DIRECTORY_PROPERTY(EDGER8R_INCLUDE_DIR DIRECTORY ${PROJECT_SOURCE_DIR}/../linux-sgx DEFINITION EDGER8R_INCLUDE_DIR)

# SubstrateVM library which includes stubs, edl and jni implementation
add_library(substratevm
        src/edl.cpp
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
        jvm_enclave_common
        linux-sgx_headers
        linux-sgx_tstdc_headers)

get_property(ENCLAVE_SOURCES TARGET substratevm PROPERTY SOURCES)
determinise_compile(${ENCLAVE_SOURCES})
