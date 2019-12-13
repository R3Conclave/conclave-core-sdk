project(zlib)

include(ExternalProject)

ExternalProject_Add(zlib-ext
        URL "https://github.com/madler/zlib/archive/v1.2.11.tar.gz"
        URL_MD5 "0095d2d2d1f3442ce1318336637b695f"

        CMAKE_ARGS -D "CMAKE_C_FLAGS=-fPIC" -D "ZLIB_SOURCE_DIR=${CMAKE_CURRENT_SOURCE_DIR}"
        PATCH_COMMAND patch -p1 CMakeLists.txt ${CMAKE_CURRENT_SOURCE_DIR}/zlib-CMakeLists.diff
        INSTALL_COMMAND "")


ExternalProject_Get_Property(zlib-ext BINARY_DIR)

add_library(zlib STATIC IMPORTED GLOBAL)
set_property(TARGET zlib PROPERTY IMPORTED_LOCATION ${BINARY_DIR}/libz.a)
add_dependencies(zlib zlib-ext)
