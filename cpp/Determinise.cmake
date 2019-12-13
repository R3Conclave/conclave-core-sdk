# Ensure that static archives are repeatable artifacts.
set(CMAKE_C_ARCHIVE_CREATE "<CMAKE_AR> qcD <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_APPEND "<CMAKE_AR> qD <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_FINISH "<CMAKE_RANLIB> -D <TARGET>")

set(CMAKE_CXX_ARCHIVE_CREATE "<CMAKE_AR> qcD <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_CXX_ARCHIVE_APPEND "<CMAKE_AR> qD <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_CXX_ARCHIVE_FINISH "<CMAKE_RANLIB> -D <TARGET>")

# Calculate -frandom-seed=XXX compiler option for source files.
function(determinise_compile)
    foreach(_file ${ARGV})
        if (${_file} MATCHES "\\.cpp$|\\.c$|\\.cc$")
            if (IS_ABSOLUTE ${_file})
                file(SHA256 ${_file} checksum)
            else()
                file(SHA256 ${CMAKE_CURRENT_SOURCE_DIR}/${_file} checksum)
            endif()
        endif()
        string(SUBSTRING ${checksum} 0 64 checksum)
        set_property(SOURCE ${_file} APPEND_STRING PROPERTY COMPILE_FLAGS "-frandom-seed=${checksum}")
    endforeach()
endfunction(determinise_compile)
