project(jvm-enclave-common)

include(Determinise)

# Enable the row below to get debug trace output from c++ modules.
# add_definitions(-DDEBUG_TRACE_OUTPUT -DLOG_MEMORY)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++14 -Wall -Wextra -Werror -Wno-unused-parameter")

set(sources
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
        stubs/spawn.cpp
        stubs/signal.cpp
        stubs/stdio.cpp
        stubs/stdlib.cpp
        stubs/string.cpp
        stubs/sys_sendfile.cpp
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
        stubs/sched_cpucount.cpp
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
        stubs/sys_xattr.cpp
)

set(link_libraries
        jvm_enclave_edl
        jvm_host_enclave_common_enclave
        linux-sgx_headers
        linux-sgx_tstdc_headers
        linux-sgx_pthread
        zlib
)

# targets for libjvm_enclave_common_<simulation|debug|release>.a
# Release build target, blocks debug prints from the enclave
add_library(jvm_enclave_common_release ${sources})
add_dependencies(jvm_enclave_common_release linux-sgx-ext)
target_compile_options(jvm_enclave_common_release PUBLIC -nostdinc -fPIC)
target_include_directories(jvm_enclave_common_release PUBLIC include include/public PRIVATE src)
target_link_libraries(jvm_enclave_common_release ${link_libraries})

# Debug build target, allows prints (-DENABLE_DEBUG_PRINT)
add_library(jvm_enclave_common_debug ${sources})
add_dependencies(jvm_enclave_common_debug linux-sgx-ext)
target_compile_definitions(jvm_enclave_common_debug PRIVATE -DENABLE_DEBUG_PRINT)
target_compile_options(jvm_enclave_common_debug PUBLIC -nostdinc -fPIC)
target_include_directories(jvm_enclave_common_debug PUBLIC include include/public PRIVATE src)
target_link_libraries(jvm_enclave_common_debug ${link_libraries})

# Simulation build target, allows prints (-DENABLE_DEBUG_PRINT, -DSGX_SIM)
add_library(jvm_enclave_common_simulation ${sources})
add_dependencies(jvm_enclave_common_simulation linux-sgx-ext)
target_compile_definitions(jvm_enclave_common_simulation PRIVATE -DENABLE_DEBUG_PRINT -DSGX_SIM)
target_compile_options(jvm_enclave_common_simulation PUBLIC -nostdinc -fPIC)
target_include_directories(jvm_enclave_common_simulation PUBLIC include include/public PRIVATE src)
target_link_libraries(jvm_enclave_common_simulation ${link_libraries})

# Include google tests found under./test
target_test(PROJ                  ${PROJECT_NAME}
            TARGET                jvm_enclave_common_debug
            TEST_SRC_PATH         "${CMAKE_CURRENT_SOURCE_DIR}/test"
            TEST_OUT_PATH         "${CMAKE_CURRENT_BINARY_DIR}/test_bin"
            CMAKE_BINARY_DIR      "${CMAKE_BINARY_DIR}"
            INCLUDE               "${CMAKE_CURRENT_SOURCE_DIR}/include" 
                                  "${CMAKE_CURRENT_SOURCE_DIR}/src"
                                  "${CMAKE_CURRENT_SOURCE_DIR}/../jvm-host-enclave-common/include"
                                  "${CMAKE_CURRENT_SOURCE_DIR}/../jvm-enclave-common/include/public"
                                  )
