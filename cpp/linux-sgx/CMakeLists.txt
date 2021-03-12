project(linux-sgx)

include(ExternalProject)

set(BUILD_SRC_DIR ${CMAKE_CURRENT_BINARY_DIR}/src/linux-sgx)
file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/src)

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    set(DEBUG_FLAG "")
else()
    set(DEBUG_FLAG DEBUG=1)
endif()

ExternalProject_Add(linux-sgx-ext
# Based on linux-sgx Makefile "preparation" rules and adapted to conclave's CMakeLists.txt.

        DOWNLOAD_COMMAND rm -fr ${BUILD_SRC_DIR}
            && git clone -b sgx_2.13 --depth 1 https://github.com/intel/linux-sgx.git ${BUILD_SRC_DIR}
            && git -C ${BUILD_SRC_DIR} submodule update --init --depth 1 --recursive
            && bash -c "cd ${BUILD_SRC_DIR} && ./external/dcap_source/QuoteVerification/prepare_sgxssl.sh nobuild"
            && bash -c "cd ${BUILD_SRC_DIR} && cd external/openmp/openmp_code && git apply ../0001-Enable-OpenMP-in-SGX.patch >/dev/null 2>&1 ||  git apply ../0001-Enable-OpenMP-in-SGX.patch --check -R"
            && bash -c "cd ${BUILD_SRC_DIR} && ./download_prebuilt.sh"
            && bash -c "cd ${BUILD_SRC_DIR} && ./external/dcap_source/QuoteGeneration/download_prebuilt.sh"

        SOURCE_DIR ${BUILD_SRC_DIR}

        PATCH_COMMAND git apply ${CMAKE_CURRENT_SOURCE_DIR}/determinism.diff ${CMAKE_CURRENT_SOURCE_DIR}/debug.diff ${CMAKE_CURRENT_SOURCE_DIR}/pthread_changes.diff
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/dcap_quoteverify/sgx_dcap_quoteverify.cpp ${CMAKE_CURRENT_SOURCE_DIR}/dcap_quoteverify_cpp.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/dcap_quoteverify/inc/sgx_urts_wrapper.h ${CMAKE_CURRENT_SOURCE_DIR}/dcap_sgx_urts_wrapper_h.patch
	     && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/quote/linux/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/dcap_quote_wrapper_make.patch
	     && patch ${BUILD_SRC_DIR}/psw/ae/aesm_service/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/psw_aesm_make.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/QuoteGen_makefile.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/ql/linux/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/dcap_ql.patch

        UPDATE_COMMAND ""

        CONFIGURE_COMMAND ""

        BUILD_COMMAND make -j$(NATIVE_BUILD_PARALLEL_LEVEL) ${DEBUG_FLAG} -C ${BUILD_SRC_DIR} sdk_install_pkg
             && yes yes | ${BUILD_SRC_DIR}/linux/installer/bin/sgx_linux_x64_sdk_2.13.100.4.bin
             && SGX_SDK=${CMAKE_CURRENT_BINARY_DIR}/linux-sgx-ext-prefix/src/linux-sgx-ext-build/sgxsdk make -j$(NATIVE_BUILD_PARALLEL_LEVEL) ${DEBUG_FLAG} -C ${BUILD_SRC_DIR} psw_install_pkg
             && SGX_SDK=${CMAKE_CURRENT_BINARY_DIR}/linux-sgx-ext-prefix/src/linux-sgx-ext-build/sgxsdk make ${DEBUG_FLAG} -C ${BUILD_SRC_DIR}/external/dcap_source QuoteGeneration QuoteVerification


        INSTALL_COMMAND ""

        TEST_COMMAND "")

ExternalProject_Add(macos-sign-tool
# Based on linux-sgx Makefile "preparation" rules and adapted to conclave's CMakeLists.txt.
    DOWNLOAD_COMMAND rm -fr ${BUILD_SRC_DIR}
        && git clone -b sgx_2.13 --depth 1 https://github.com/intel/linux-sgx.git ${BUILD_SRC_DIR}
        && git -C ${BUILD_SRC_DIR} submodule update --init --depth 1 --recursive
        && bash -c "cd ${BUILD_SRC_DIR}/external/dcap_source && git apply ${CMAKE_CURRENT_SOURCE_DIR}/dcap_source_macos.diff"
        && bash -c "cd ${BUILD_SRC_DIR} && ./external/dcap_source/QuoteVerification/prepare_sgxssl.sh nobuild"
        && bash -c "cd ${BUILD_SRC_DIR} && cd external/openmp/openmp_code && git apply ../0001-Enable-OpenMP-in-SGX.patch >/dev/null 2>&1 ||  git apply ../0001-Enable-OpenMP-in-SGX.patch --check -R"
        && bash -c "cd ${BUILD_SRC_DIR} && git apply ${CMAKE_CURRENT_SOURCE_DIR}/macos.diff && ./download_prebuilt.sh"
        && bash -c "cd ${BUILD_SRC_DIR} && ./external/dcap_source/QuoteGeneration/download_prebuilt.sh"

    SOURCE_DIR ${BUILD_SRC_DIR}

    PATCH_COMMAND git apply ${CMAKE_CURRENT_SOURCE_DIR}/determinism.diff ${CMAKE_CURRENT_SOURCE_DIR}/debug.diff ${CMAKE_CURRENT_SOURCE_DIR}/pthread_changes.diff
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/dcap_quoteverify/sgx_dcap_quoteverify.cpp ${CMAKE_CURRENT_SOURCE_DIR}/dcap_quoteverify_cpp.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteVerification/dcap_quoteverify/inc/sgx_urts_wrapper.h ${CMAKE_CURRENT_SOURCE_DIR}/dcap_sgx_urts_wrapper_h.patch
	     && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/quote/linux/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/dcap_quote_wrapper_make.patch
	     && patch ${BUILD_SRC_DIR}/psw/ae/aesm_service/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/psw_aesm_make.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/QuoteGen_makefile.patch
             && patch ${BUILD_SRC_DIR}/external/dcap_source/QuoteGeneration/quote_wrapper/ql/linux/Makefile ${CMAKE_CURRENT_SOURCE_DIR}/dcap_ql.patch

    UPDATE_COMMAND ""

    CONFIGURE_COMMAND ""

    BUILD_COMMAND make -j$(NATIVE_BUILD_PARALLEL_LEVEL) ${DEBUG_FLAG} -C ${BUILD_SRC_DIR}/sdk/sign_tool/SignTool

    INSTALL_COMMAND ""

    TEST_COMMAND ""
)

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

# The below hasn't been working for a while, it makes CMake v3.18 crash. may be worth revisiting in the future.
# Disabling it for now.
message("linux-sgx_clion currently disabled!")
set(ENABLE_CLION OFF)
if (ENABLE_CLION)
if (NOT ${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
# This is here for IDE support
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
file(GLOB_RECURSE CLION_SOURCES ${BUILD_SRC_DIR}/*.c ${BUILD_SRC_DIR}/*.cpp ${BUILD_SRC_DIR}/*.h)
message("MGS {BUILD_SRC_DIR} ${BUILD_SRC_DIR}")
message("MGS {CLION_SOURCES} ${CLION_SOURCES}")
add_library(linux-sgx_clion ${CLION_SOURCES})
add_dependencies(linux-sgx_clion linux-sgx_headers linux-sgx-ext)
set_target_properties(linux-sgx_clion PROPERTIES LINKER_LANGUAGE CXX)
target_include_directories(linux-sgx_clion PUBLIC
        ${BUILD_SRC_DIR}/common/inc
        ${BUILD_SRC_DIR}/common/inc/internal
        ${BUILD_SRC_DIR}/common/inc/tlibc
        ${BUILD_SRC_DIR}/sdk/simulation/tinst
        ${BUILD_SRC_DIR}/sdk/simulation/urtssim
        ${BUILD_SRC_DIR}/psw/urts
        ${BUILD_SRC_DIR}/psw/urts/parser
        ${BUILD_SRC_DIR}/psw/urts/linux
        ${BUILD_SRC_DIR}/psw/ae/data/constants/linux
        ${BUILD_SRC_DIR}/psw/ae/inc
        ${BUILD_SRC_DIR}/psw/ae/inc/internal
        ${BUILD_SRC_DIR}/psw/ae/common/inc
        ${BUILD_SRC_DIR}/psw/uae_service/uae_wrapper/inc
        ${BUILD_SRC_DIR}/psw/uae_service/linux
        ${BUILD_SRC_DIR}/external/tinyxml2
        ${BUILD_SRC_DIR}/external/vtune/linux/include
        ${BUILD_SRC_DIR}/external/vtune/linux/sdk/src/ittnotify
        ${BUILD_SRC_DIR}/external/epid-sdk)
target_link_libraries(linux-sgx_clion linux-sgx_headers)
endif()
endif()
