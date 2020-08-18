project(jvm-enclave-common)

include(../Determinise.cmake)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Werror -Wno-unused-parameter")

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
elseif(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ggdb")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ggdb")
else()
    set(CMAKE_CXX_FLAGS_DEBUG "-ggdb")
    set(CMAKE_C_FLAGS_DEBUG "-ggdb")
endif()

# jvm_enclave_common.a this is the library all enclave implementations share
add_library(jvm_enclave_common
        src/dlsym_symbols.cpp
        src/enclave_thread.cpp
        src/enclave_jni.cpp
        
        src/memory_manager.cpp
        src/file_manager.cpp
        src/vm_enclave_layer.cpp
        src/sigthread.cpp
        src/statvfs.cpp

        stubs/ctype.cpp
        stubs/fcntl.cpp
        stubs/langinfo.cpp
        stubs/libc.cpp
        stubs/locale.cpp
        stubs/pwd.cpp
        stubs/signal.cpp
        stubs/stdio.cpp
        stubs/stdlib.cpp
        stubs/string.cpp
        stubs/sys_resource.cpp
        stubs/sys_stat.cpp
        stubs/sys_time.cpp
        stubs/sys_utsname.cpp
        stubs/sys_wait.cpp
        stubs/thread.cpp
        stubs/time.cpp
        stubs/unistd.cpp
        stubs/pthread.cpp
        stubs/dlfcn.cpp
        stubs/arpa_inet.cpp
        stubs/sys_mman.cpp
        stubs/sched.cpp
        stubs/netdb.cpp
        stubs/sys_socket.cpp
        stubs/dirent.cpp
        stubs/ioctl.cpp
        stubs/utime.cpp
)

target_include_directories(jvm_enclave_common PUBLIC include PRIVATE src)
target_compile_options(jvm_enclave_common PUBLIC -nostdinc -fPIC)
target_link_libraries(jvm_enclave_common
        jvm_enclave_edl
        jvm_host_enclave_common_enclave
        linux-sgx_headers
        linux-sgx_tstdc_headers
        linux-sgx_pthread)
add_dependencies(jvm_enclave_common linux-sgx-ext)

if(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    target_compile_definitions(jvm_enclave_common PRIVATE -DSGX_SIM)
endif()

get_property(ENCLAVE_SOURCES TARGET jvm_enclave_common PROPERTY SOURCES)
determinise_compile(${ENCLAVE_SOURCES})
