project(jvm-edl)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -frandom-seed=1")
if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
else()
    add_compile_options(-ggdb) # only -ggdb works properly
endif()

# jvm-edl
# This target generates the .c and .h files for the host and the enclave using the edger8r tool.

# For release builds of the enclave we want to prevent console messages from propagating from
# the enclave to the host. The safest way to do this is to remove the debug_print ocall from
# the edl interface. Define the edl to use based on the requested build type and also define
# a flag to provide a dummy implementation in jvm_enclave.cpp.
if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
set(JVM_EDL_SOURCE jvm_release.edl)
set(JVM_EDL_GENERATED_TRUSTED jvm_release_t)
set(JVM_EDL_GENERATED_UNTRUSTED jvm_release_u)
add_definitions(-DNO_ENCLAVE_PRINT)
else()
set(JVM_EDL_SOURCE jvm_debug.edl)
set(JVM_EDL_GENERATED_TRUSTED jvm_debug_t)
set(JVM_EDL_GENERATED_UNTRUSTED jvm_debug_u)
endif()

set(GENERATED_EDL_DIR ${CMAKE_BINARY_DIR}/build/edl)
set(GENERATED_EDL_FILES ${GENERATED_EDL_DIR}/host/${JVM_EDL_GENERATED_UNTRUSTED}.c ${GENERATED_EDL_DIR}/enclave/${JVM_EDL_GENERATED_TRUSTED}.c)
GET_DIRECTORY_PROPERTY(EDGER8R_INCLUDE_DIR DIRECTORY ${PROJECT_SOURCE_DIR}/../linux-sgx DEFINITION EDGER8R_INCLUDE_DIR)
add_custom_command(
        OUTPUT ${GENERATED_EDL_FILES}
        COMMAND edger8r --search-path ${CMAKE_CURRENT_SOURCE_DIR}/src --search-path ${EDGER8R_INCLUDE_DIR} --trusted-dir ${GENERATED_EDL_DIR}/enclave --untrusted-dir ${GENERATED_EDL_DIR}/host ${JVM_EDL_SOURCE}
        COMMAND cp ${GENERATED_EDL_DIR}/enclave/${JVM_EDL_GENERATED_TRUSTED}.h ${GENERATED_EDL_DIR}/enclave/jvm_t.h
        COMMAND cp ${GENERATED_EDL_DIR}/host/${JVM_EDL_GENERATED_UNTRUSTED}.h ${GENERATED_EDL_DIR}/host/jvm_u.h
        DEPENDS edger8r src/start_thread.edl src/jvm.edl src/${JVM_EDL_SOURCE})
set_source_files_properties(${GENERATED_EDL_FILES} PROPERTIES GENERATED TRUE)
add_custom_target(jvm-edl DEPENDS ${GENERATED_EDL_FILES})

# compiled EDL generated code for the enclave
add_library(jvm_enclave_edl
        src/jvm_enclave.cpp
        ${GENERATED_EDL_DIR}/enclave/${JVM_EDL_GENERATED_TRUSTED}.c
)
target_include_directories(jvm_enclave_edl PUBLIC ${GENERATED_EDL_DIR}/enclave)
target_compile_options(jvm_enclave_edl PUBLIC -nostdinc -fPIC)
target_link_libraries(jvm_enclave_edl
        linux-sgx_headers
        linux-sgx_tstdc_headers)
add_dependencies(jvm_enclave_edl jvm-edl)
set_property(TARGET jvm_enclave_edl PROPERTY GENERATED_EDL_DIR ${GENERATED_EDL_DIR}/enclave)

# compiled EDL generated code for the host
add_library(jvm_host_edl
        ${GENERATED_EDL_DIR}/host/${JVM_EDL_GENERATED_UNTRUSTED}.c)
target_include_directories(jvm_host_edl PUBLIC ${GENERATED_EDL_DIR}/host)
target_compile_options(jvm_host_edl PUBLIC -fPIC)
target_link_libraries(jvm_host_edl
        linux-sgx_headers)
add_dependencies(jvm_host_edl jvm-edl)
set_property(TARGET jvm_host_edl PROPERTY GENERATED_EDL_DIR ${GENERATED_EDL_DIR}/host)

get_property(HOST_EDL_SOURCES TARGET jvm_host_edl PROPERTY SOURCES)
get_property(ENCLAVE_EDL_SOURCES TARGET jvm_enclave_edl PROPERTY SOURCES)
