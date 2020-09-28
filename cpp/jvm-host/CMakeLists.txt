project(jvm-host)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Werror")

if(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    set(SIM_SUFFIX "_sim")
else()
    set(SIM_SUFFIX "")
endif()

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
elseif(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ggdb")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ggdb")
else()
    set(CMAKE_CXX_FLAGS_DEBUG "-ggdb")
    set(CMAKE_C_FLAGS_DEBUG "-ggdb")
endif()

find_package(JNI REQUIRED)

SET(CMAKE_SKIP_BUILD_RPATH FALSE)
SET(CMAKE_BUILD_RPATH "$ORIGIN")

get_target_property(LINUX_BUILD_ARTIFACT_DIR linux-sgx-ext BUILD_ARTIFACT_DIR)

add_library(jvm_host SHARED
        src/host_jni.cpp
        src/host_start_thread.cpp
        src/enclave_metadata.cpp
        src/ecall_context.cpp
        src/enclave_console.cpp)
target_include_directories(jvm_host PUBLIC
        include
        ../jvm-host-shared/include
        ${JNI_INCLUDE_DIRS})
if(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    target_compile_definitions(jvm_host PRIVATE -DSGX_SIM)
endif()
find_package(Threads)
target_link_libraries(jvm_host
        avian_headers
        linux-sgx_headers
        linux-sgx_urts_headers
        -L${LINUX_BUILD_ARTIFACT_DIR}
        -L../jvm-host-shared
        -lsgx_uae_service${SIM_SUFFIX}
        -lsgx_urts${SIM_SUFFIX}
        -lsgx_capable
        -ljvm_host_shared
        jvm_host_edl
        jvm_host_enclave_common_host
        ${CMAKE_THREAD_LIBS_INIT}
        # The $ORIGIN rpath is very important, using this we can bundle dependency .sos next to jvm_host.so without
        # having to specify LD_LIBRARY_PATH
        -Wl,-rpath,"$ORIGIN")
add_dependencies(jvm_host linux-sgx-ext jvm_host_shared)

get_property(HOST_SOURCES TARGET jvm_host PROPERTY SOURCES)
determinise_compile(${HOST_SOURCES})
