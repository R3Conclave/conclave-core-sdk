project(jvm-host)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall -Wextra")

find_package(JNI REQUIRED)

SET(CMAKE_SKIP_BUILD_RPATH FALSE)
SET(CMAKE_BUILD_RPATH "$ORIGIN")

get_target_property(LINUX_BUILD_ARTIFACT_DIR linux-sgx-ext BUILD_ARTIFACT_DIR)
find_package(Threads)

file(GLOB sources "src/*.cpp" "src/*.c")

set(common_includes
        include
        ../jvm-host-shared/include
        ../fatfs/common/include
        ${JNI_INCLUDE_DIRS})

set(common_link_libraries
        linux-sgx_headers
        linux-sgx_urts_headers
        -L${LINUX_BUILD_ARTIFACT_DIR}
        -L../jvm-host-shared
        -lsgx_capable
        -ljvm_host_shared
        jvm_host_edl
        jvm_host_enclave_common_host
        -L../fatfs/host
        fatfs_host
        ${CMAKE_THREAD_LIBS_INIT}
        # The $ORIGIN rpath is very important, using this we can bundle dependency .sos next to jvm_host.so without
        # having to specify LD_LIBRARY_PATH
        -Wl,-rpath,"$ORIGIN")

# Release and debug mode library
add_library(jvm_host SHARED ${sources})
add_dependencies(jvm_host linux-sgx-ext jvm_host_shared fatfs_host)
target_include_directories(jvm_host PUBLIC ${common_includes})
target_link_libraries(jvm_host
        -lsgx_uae_service
        -lsgx_urts
        ${common_link_libraries})

# Simulation mode library
add_library(jvm_host_sim SHARED ${sources})
target_compile_definitions(jvm_host_sim PRIVATE -DSGX_SIM)
add_dependencies(jvm_host_sim linux-sgx-ext jvm_host_shared fatfs_host)
target_include_directories(jvm_host_sim PUBLIC ${common_includes})
target_link_libraries(jvm_host_sim
        -lsgx_uae_service_sim
        -lsgx_urts_sim
        ${common_link_libraries})

get_property(HOST_SOURCES TARGET jvm_host PROPERTY SOURCES)
get_property(HOST_SOURCES_SIM TARGET jvm_host_sim PROPERTY SOURCES)
determinise_compile(${HOST_SOURCES} ${HOST_SOURCES_SIM})
