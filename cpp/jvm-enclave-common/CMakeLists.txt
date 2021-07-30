project(jvm-enclave-common)

include(Determinise)

# Enable the row below to get debug trace output from c++ modules.
# add_definitions(-DDEBUG_TRACE_OUTPUT -DLOG_MEMORY)

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
        src/enclave_shared_data.cpp
        src/sigthread.cpp
        src/statvfs.cpp

        stubs/ctype.cpp
        stubs/fcntl.cpp
        stubs/grp.cpp
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
        stubs/sys_poll.cpp
        stubs/dirent.cpp
        stubs/ioctl.cpp
        stubs/utime.cpp
        stubs/mntent.cpp
        stubs/semaphore.cpp
        stubs/sys_epoll.cpp
        stubs/sys_vfs.cpp
)
add_dependencies(jvm_enclave_common linux-sgx-ext)

target_include_directories(jvm_enclave_common PUBLIC include include/public PRIVATE src)
target_compile_options(jvm_enclave_common PUBLIC -nostdinc -fPIC)
target_link_libraries(jvm_enclave_common
        jvm_enclave_edl
        jvm_host_enclave_common_enclave
        linux-sgx_headers
        linux-sgx_tstdc_headers
        linux-sgx_pthread
	zlib
	)

if(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    target_compile_definitions(jvm_enclave_common PRIVATE -DSGX_SIM)
endif()

# Include google tests found under./test
target_test(PROJ                  ${PROJECT_NAME}
            TARGET                jvm_enclave_common
            TEST_SRC_PATH         "${CMAKE_CURRENT_SOURCE_DIR}/test"
            TEST_OUT_PATH         "${CMAKE_CURRENT_BINARY_DIR}/test_bin"
            CMAKE_BINARY_DIR      "${CMAKE_BINARY_DIR}"
            INCLUDE               "${CMAKE_CURRENT_SOURCE_DIR}/include" 
                                  "${CMAKE_CURRENT_SOURCE_DIR}/src"
                                  "${CMAKE_CURRENT_SOURCE_DIR}/../jvm-host-enclave-common/include"
                                  "${CMAKE_CURRENT_SOURCE_DIR}/../jvm-enclave-common/include/public"
                                  )
