project(binutils)

include(ExternalProject)

set(BUILD_SRC_DIR ${CMAKE_CURRENT_BINARY_DIR}/binutils-${binutils_version})

ExternalProject_Add(macos-binutils
    SOURCE_DIR ${BUILD_SRC_DIR}/build

    DOWNLOAD_DIR ${CMAKE_CURRENT_BINARY_DIR}
    DOWNLOAD_COMMAND curl -sSL -o binutils-${binutils_version}.tar.gz https://ftp.gnu.org/gnu/binutils/binutils-${binutils_version}.tar.gz
        && tar xf binutils-${binutils_version}.tar.gz

    PATCH_COMMAND ""

    UPDATE_COMMAND ""

    CONFIGURE_COMMAND CFLAGS=-mmacosx-version-min=10.14 CXXFLAGS=-mmacosx-version-min=10.14 ../configure --target=x86_64-unknown-linux-gnu

    BUILD_COMMAND make -j$(NATIVE_BUILD_PARALLEL_LEVEL)

    INSTALL_COMMAND ""

    TEST_COMMAND ""

    BUILD_IN_SOURCE 1
)
