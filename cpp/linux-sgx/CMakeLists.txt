project(linux-sgx)

include(ExternalProject)

set(BUILD_SRC_DIR ${CMAKE_CURRENT_BINARY_DIR}/src/linux-sgx)
file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/src)

if(${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    set(DEBUG_FLAG DEBUG=1)
else()
    set(DEBUG_FLAG "")
endif()

# Note: Intel hasn't created the tag sgx_2.17.1 yet, so we clone the
#   whole repository and we checkout the commit id. This requires us
#   to save the current directory ("pwd"), change directory into the
#   Intel SGX repository (just cloned), checkout the commit id and change
#   back to the previous "current directory".
#   All of this will be removed when the tag is available, only the
#   "git clone" command will be required.
# TODO: Use the tag when available and remove the related git commands.
set(SGX_GIT_LABEL "70e1535caa96c315189760a3ad4663eded9e8a2c")
set(SGX_GIT_TAG "sgx_2.17.1")

ExternalProject_Add(linux-sgx-ext
# Based on linux-sgx Makefile "preparation" rules and adapted to conclave's CMakeLists.txt.

        DOWNLOAD_COMMAND rm -fr ${BUILD_SRC_DIR}
            && git clone https://github.com/intel/linux-sgx.git ${BUILD_SRC_DIR}
            && TEMP_DIR=$(pwd) && cd ${BUILD_SRC_DIR} && git checkout -b ${SGX_GIT_TAG} ${SGX_GIT_LABEL} && cd ${TEMP_DIR}
            && git -C ${BUILD_SRC_DIR} submodule update --init --depth 1 --recursive
            && bash -c "cd ${BUILD_SRC_DIR} && ./external/dcap_source/QuoteVerification/prepare_sgxssl.sh nobuild"
            && bash -c "cd ${BUILD_SRC_DIR} && cd external/openmp/openmp_code && git apply ../0001-Enable-OpenMP-in-SGX.patch >/dev/null 2>&1 ||  git apply ../0001-Enable-OpenMP-in-SGX.patch --check -R"
            && bash -c "cd ${BUILD_SRC_DIR} && ./download_prebuilt.sh"
            && bash -c "cd ${BUILD_SRC_DIR} && ./external/dcap_source/QuoteGeneration/download_prebuilt.sh"

        SOURCE_DIR ${BUILD_SRC_DIR}

        PATCH_COMMAND git apply ${CMAKE_CURRENT_SOURCE_DIR}/determinism.diff ${CMAKE_CURRENT_SOURCE_DIR}/debug.diff ${CMAKE_CURRENT_SOURCE_DIR}/pthread_changes.diff ${CMAKE_CURRENT_SOURCE_DIR}/memset.patch
             #  The dtv.patch solves an issue we have spotted in the Intel SGX SDK, starting from version 2.15.1.
             #    See https://github.com/intel/linux-sgx/issues/855
             #    As this is an issue in simulation mode only, we will apply this patch until the issue is solved or closed.
             && git apply ${CMAKE_CURRENT_SOURCE_DIR}/dtv.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/dcap_quoteverify/sgx_dcap_quoteverify.cpp ${CMAKE_CURRENT_SOURCE_DIR}/dcap_quoteverify_cpp.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/dcap_quoteverify/inc/sgx_urts_wrapper.h ${CMAKE_CURRENT_SOURCE_DIR}/dcap_sgx_urts_wrapper_h.patch
	     && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/quote/linux/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/dcap_quote_wrapper_make.patch
	     && patch ${BUILD_SRC_DIR}/psw/ae/aesm_service/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/psw_aesm_make.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/QuoteGen_makefile.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/ql/linux/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/dcap_ql.patch
             && patch ${BUILD_SRC_DIR}/sdk/cpprt/linux/libunwind/src/se-libc-stubs.c ${CMAKE_CURRENT_SOURCE_DIR}/ndebug.patch

        UPDATE_COMMAND ""

        CONFIGURE_COMMAND ""

        BUILD_COMMAND make -j$(NATIVE_BUILD_PARALLEL_LEVEL) ${DEBUG_FLAG} -C ${BUILD_SRC_DIR} sdk_install_pkg
             && bash -c "yes yes | $(ls ${BUILD_SRC_DIR}/linux/installer/bin/sgx_linux_x64_sdk*.bin)"
             && SGX_SDK=${CMAKE_CURRENT_BINARY_DIR}/linux-sgx-ext-prefix/src/linux-sgx-ext-build/sgxsdk make -j$(NATIVE_BUILD_PARALLEL_LEVEL) ${DEBUG_FLAG} -C ${BUILD_SRC_DIR} psw_install_pkg
             && SGX_SDK=${CMAKE_CURRENT_BINARY_DIR}/linux-sgx-ext-prefix/src/linux-sgx-ext-build/sgxsdk make ${DEBUG_FLAG} -C ${BUILD_SRC_DIR}/external/dcap_source QuoteGeneration QuoteVerification


        INSTALL_COMMAND ""

        TEST_COMMAND "")

add_library(linux-sgx_headers INTERFACE)
target_include_directories(linux-sgx_headers INTERFACE
        ${BUILD_SRC_DIR}/common/inc
        ${BUILD_SRC_DIR}/common/inc/internal
	${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/common/inc
        ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/ql/inc
        ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/pce_wrapper/inc
        ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/dcap_quoteverify/inc
        ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/QvE/Include)

add_library(linux-sgx_tstdc_headers INTERFACE)
target_include_directories(linux-sgx_tstdc_headers INTERFACE
        ${BUILD_SRC_DIR}/common/inc/tlibc
        ${BUILD_SRC_DIR}/sdk/tlibcxx/include)

add_library(linux-sgx_urts_headers INTERFACE)
target_include_directories(linux-sgx_urts_headers INTERFACE
        ${BUILD_SRC_DIR}/psw/urts
        ${BUILD_SRC_DIR}/psw/urts/parser
        ${BUILD_SRC_DIR}/psw/urts/linux
        ${BUILD_SRC_DIR}/external/vtune/linux/include
        ${BUILD_SRC_DIR}/external/vtune/linux/sdk/src/ittnotify)

set(LINUX_SGX_LIB_PREFIX ${BUILD_SRC_DIR}/build/linux)
set_property(TARGET linux-sgx-ext PROPERTY BUILD_ARTIFACT_DIR ${LINUX_SGX_LIB_PREFIX})
foreach(SGX_STATIC_LIB_NAME
        sgx_capable
        sgx_omp
        sgx_pcl
        sgx_pclsim
        sgx_pthread
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
        sgx_tswitchless
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

