project(jvm-host)

include(../Determinise.cmake)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Werror")

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
else()
    add_compile_options(-ggdb) # only -ggdb works properly
endif()

find_package(JNI REQUIRED)

SET(CMAKE_SKIP_BUILD_RPATH FALSE)
SET(CMAKE_BUILD_RPATH "$ORIGIN")

get_target_property(LINUX_BUILD_ARTIFACT_DIR linux-sgx-ext BUILD_ARTIFACT_DIR)

add_library(jvm_host_shared SHARED
        src/host_jni_shared.cpp
        src/enclave_platform.cpp
        )
target_include_directories(jvm_host_shared PUBLIC
        include
        ${JNI_INCLUDE_DIRS})
target_link_libraries(jvm_host_shared
        avian_headers
        linux-sgx_headers
        linux-sgx_urts_headers
        -L${LINUX_BUILD_ARTIFACT_DIR}
        -lsgx_capable
        jvm_host_enclave_common_host
        # The $ORIGIN rpath is very important, using this we can bundle dependency .sos next to jvm_host.so without
        # having to specify LD_LIBRARY_PATH
        -Wl,-rpath,"$ORIGIN")
add_dependencies(jvm_host_shared linux-sgx-ext)

get_property(HOST_SOURCES TARGET jvm_host_shared PROPERTY SOURCES)
determinise_compile(${HOST_SOURCES})
