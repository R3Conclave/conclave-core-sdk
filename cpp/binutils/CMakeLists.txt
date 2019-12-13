project(binutils)

include(ExternalProject)

ExternalProject_Add(binutils-ext
        # "http://ftp.gnu.org/gnu/binutils/binutils-2.32.tar.gz"
        URL "${CMAKE_CURRENT_SOURCE_DIR}/binutils-2.32.tar.gz"
        URL_MD5 "d1119c93fc0ed3007be4a84dd186af55"

        CONFIGURE_COMMAND ./configure LDFLAGS=--static

        BUILD_COMMAND make all-ld -j8

        INSTALL_COMMAND ""

        TEST_COMMAND ""

        BUILD_IN_SOURCE 1)
