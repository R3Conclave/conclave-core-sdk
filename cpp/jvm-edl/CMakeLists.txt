project(jvm-edl)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++14 -frandom-seed=1")

# jvm-edl
# This target generates the .c and .h files for the host and the enclave using the edger8r tool.

set(JVM_EDL_SOURCE jvm.edl)
set(JVM_EDL_GENERATED_TRUSTED jvm_t)
set(JVM_EDL_GENERATED_UNTRUSTED jvm_u)

set(GENERATED_EDL_DIR ${CMAKE_BINARY_DIR}/build/edl)
set(GENERATED_EDL_FILES
        ${GENERATED_EDL_DIR}/host/${JVM_EDL_GENERATED_UNTRUSTED}.c
        ${GENERATED_EDL_DIR}/enclave/${JVM_EDL_GENERATED_TRUSTED}.c)
GET_DIRECTORY_PROPERTY(EDGER8R_INCLUDE_DIR DIRECTORY ${PROJECT_SOURCE_DIR}/../linux-sgx DEFINITION EDGER8R_INCLUDE_DIR)
add_custom_command(
        OUTPUT ${GENERATED_EDL_FILES}
        COMMAND edger8r --search-path ${CMAKE_CURRENT_SOURCE_DIR}/src --search-path ${EDGER8R_INCLUDE_DIR} --trusted-dir ${GENERATED_EDL_DIR}/enclave --untrusted-dir ${GENERATED_EDL_DIR}/host ${JVM_EDL_SOURCE}
        DEPENDS edger8r src/start_thread.edl src/jvm.edl src/${JVM_EDL_SOURCE})
set_source_files_properties(${GENERATED_EDL_FILES} PROPERTIES GENERATED TRUE)
add_custom_target(jvm-edl DEPENDS ${GENERATED_EDL_FILES})

add_library(jvm_enclave_edl src/jvm_enclave.cpp ${GENERATED_EDL_DIR}/enclave/${JVM_EDL_GENERATED_TRUSTED}.c)
target_include_directories(jvm_enclave_edl PUBLIC ${GENERATED_EDL_DIR}/enclave)
target_compile_options(jvm_enclave_edl PUBLIC -nostdinc -fPIC)
target_link_libraries(jvm_enclave_edl linux-sgx_headers linux-sgx_tstdc_headers)
add_dependencies(jvm_enclave_edl jvm-edl)
set_property(TARGET jvm_enclave_edl PROPERTY GENERATED_EDL_DIR ${GENERATED_EDL_DIR}/enclave)

add_library(jvm_host_edl ${GENERATED_EDL_DIR}/host/${JVM_EDL_GENERATED_UNTRUSTED}.c)
target_include_directories(jvm_host_edl PUBLIC ${GENERATED_EDL_DIR}/host)
target_compile_options(jvm_host_edl PUBLIC -fPIC)
target_link_libraries(jvm_host_edl linux-sgx_headers)
add_dependencies(jvm_host_edl jvm-edl)
set_property(TARGET jvm_host_edl PROPERTY GENERATED_EDL_DIR ${GENERATED_EDL_DIR}/host)

get_property(HOST_EDL_SOURCES TARGET jvm_host_edl PROPERTY SOURCES)
get_property(ENCLAVE_EDL_SOURCES TARGET jvm_enclave_edl PROPERTY SOURCES)
