project(vmenclavelayer)

include(../Determinise.cmake)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
else()
    add_compile_options(-ggdb) # only -ggdb works properly
    
    # Show trace-level stub output. This should be enabled during stub development
    # to show exactly which stubs are being called.
    #add_definitions(-DDEBUG_TRACE_OUTPUT)

    # Direct stdout and stderr from the enclave to the console
    add_definitions(-DDEBUG_PRINT_OUTPUT)
endif()

# GET_DIRECTORY_PROPERTY(EDGER8R_INCLUDE_DIR DIRECTORY ${PROJECT_SOURCE_DIR}/../linux-sgx DEFINITION EDGER8R_INCLUDE_DIR)

# VM to Enclave interface that provides stubs and implementations for SubstrateVM and Avian for C runtime,
# Posix and other native functions.
add_library(vmenclavelayer
        src/memory_manager.cpp
        src/file_manager.cpp
        src/vm_enclave_layer.cpp
        
        stubs/ctype.cpp
        stubs/fcntl.cpp
        stubs/langinfo.cpp
        stubs/libc.cpp
        stubs/locale.cpp
        stubs/pwd.cpp
        stubs/signal.cpp
        src/sigthread.cpp
        src/statvfs.cpp
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
target_include_directories(vmenclavelayer PUBLIC
        include
        src
        ../jvm-edl/build/edl/enclave
        ../jvm-host-enclave-common/include
        ../jvm-enclave-common/include
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux)
target_compile_options(vmenclavelayer PUBLIC -nostdinc -nostdinc++ -fPIC)
target_link_libraries(vmenclavelayer
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/linux
        jvm_enclave_common
        linux-sgx_headers
        linux-sgx_tstdc_headers)


get_property(ENCLAVE_SOURCES TARGET vmenclavelayer PROPERTY SOURCES)
determinise_compile(${ENCLAVE_SOURCES})
