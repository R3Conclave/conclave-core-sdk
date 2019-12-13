project(linux-sgx)

include(ExternalProject)

set(BUILD_SRC_DIR ${CMAKE_CURRENT_BINARY_DIR}/src/linux-sgx)
file(MAKE_DIRECTORY ${BUILD_SRC_DIR})

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    set(DEBUG_FLAG "")
else()
    set(DEBUG_FLAG DEBUG=1)
endif()

ExternalProject_Add(linux-sgx-ext
        SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}

        UPDATE_COMMAND ""

        # Copy the source into the build directory, otherwise the build litters the source tree
        CONFIGURE_COMMAND bash -c "[ -d ${BUILD_SRC_DIR}/sdk ] || cp -r ${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/src"

        BUILD_COMMAND make ${DEBUG_FLAG} -C ${BUILD_SRC_DIR}

        INSTALL_COMMAND ""

        TEST_COMMAND "")

ExternalProject_Add_Step(linux-sgx-ext download-prebuilt
        COMMAND bash -c "[ -f ${BUILD_SRC_DIR}/external/ippcp_internal/lib/linux/intel64/libippcp.a] || ./download_prebuilt.sh"
        WORKING_DIRECTORY ${BUILD_SRC_DIR}
        DEPENDERS build
        DEPENDEES configure)

add_library(linux-sgx_headers INTERFACE)
target_include_directories(linux-sgx_headers INTERFACE
        ${CMAKE_CURRENT_SOURCE_DIR}/common/inc
        ${CMAKE_CURRENT_SOURCE_DIR}/common/inc/internal)

add_library(linux-sgx_tstdc_headers INTERFACE)
target_include_directories(linux-sgx_tstdc_headers INTERFACE
        ${CMAKE_CURRENT_SOURCE_DIR}/common/inc/tlibc
        ${CMAKE_CURRENT_SOURCE_DIR}/sdk/tlibcxx/include)

add_library(linux-sgx_urts_headers INTERFACE)
target_include_directories(linux-sgx_urts_headers INTERFACE
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/urts
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/urts/parser
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/urts/linux
        ${CMAKE_CURRENT_SOURCE_DIR}/external/vtune/linux/include
        ${CMAKE_CURRENT_SOURCE_DIR}/external/vtune/linux/sdk/src/ittnotify)

set(LINUX_SGX_LIB_PREFIX ${BUILD_SRC_DIR}/build/linux)
set_property(TARGET linux-sgx-ext PROPERTY BUILD_ARTIFACT_DIR ${LINUX_SGX_LIB_PREFIX})
foreach(SGX_STATIC_LIB_NAME
        sgx_capable
        sgx_pcl
        sgx_pclsim
        sgx_tcmalloc
        sgx_tcrypto
        sgx_tcxx
        sgx_tkey_exchange
        sgx_tprotected_fs
        sgx_trts
        sgx_trts_sim
        sgx_tservice
        sgx_tservice_sim
        sgx_tstdc
        sgx_ukey_exchange
        sgx_uprotected_fs)
    add_library(linux-${SGX_STATIC_LIB_NAME} STATIC IMPORTED GLOBAL)
    set_property(TARGET linux-${SGX_STATIC_LIB_NAME} PROPERTY IMPORTED_LOCATION ${LINUX_SGX_LIB_PREFIX}/lib${SGX_STATIC_LIB_NAME}.a)
    add_dependencies(linux-${SGX_STATIC_LIB_NAME} linux-sgx-ext)
endforeach(SGX_STATIC_LIB_NAME)

add_executable(edger8r IMPORTED GLOBAL)
set_property(TARGET edger8r PROPERTY IMPORTED_LOCATION ${LINUX_SGX_LIB_PREFIX}/sgx_edger8r)
set(EDGER8R_INCLUDE_DIR ${BUILD_SRC_DIR}/common/inc)
add_dependencies(edger8r linux-sgx-ext)

add_executable(sign_enclave IMPORTED GLOBAL)
set_property(TARGET sign_enclave PROPERTY IMPORTED_LOCATION ${LINUX_SGX_LIB_PREFIX}/sgx_sign)
add_dependencies(sign_enclave linux-sgx-ext)

# This is here for IDE support
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
file(GLOB_RECURSE CLION_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/*.c ${CMAKE_CURRENT_SOURCE_DIR}/*.cpp ${CMAKE_CURRENT_SOURCE_DIR}/*.h)
add_library(linux-sgx_clion ${CLION_SOURCES})
target_include_directories(linux-sgx_clion PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/common/inc
        ${CMAKE_CURRENT_SOURCE_DIR}/common/inc/internal
        ${CMAKE_CURRENT_SOURCE_DIR}/common/inc/tlibc
        ${CMAKE_CURRENT_SOURCE_DIR}/sdk/simulation/tinst
        ${CMAKE_CURRENT_SOURCE_DIR}/sdk/simulation/urtssim
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/urts
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/urts/parser
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/urts/linux
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/ae/data/constants/linux
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/ae/inc
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/ae/inc/internal
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/ae/common/inc
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/uae_service/uae_wrapper/inc
        ${CMAKE_CURRENT_SOURCE_DIR}/psw/uae_service/linux
        ${CMAKE_CURRENT_SOURCE_DIR}/external/tinyxml2
        ${CMAKE_CURRENT_SOURCE_DIR}/external/vtune/linux/include
        ${CMAKE_CURRENT_SOURCE_DIR}/external/vtune/linux/sdk/src/ittnotify
        ${CMAKE_CURRENT_SOURCE_DIR}/external/epid-sdk)
target_link_libraries(linux-sgx_clion linux-sgx_headers)
