project(fatfs_host)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall -Wextra")
add_definitions(-DFATFS_HOST)

add_library(fatfs_host
  ../host/src/persistent_disk.cpp
  )

target_include_directories(fatfs_host PUBLIC
  ../common/include
  ../host/include
  ${JNI_INCLUDE_DIRS}
  $ENV{JAVA_HOME}/include
  $ENV{JAVA_HOME}/include/linux
  )

target_compile_options(fatfs_host PUBLIC -fPIC)
target_link_libraries(fatfs_host
  linux-sgx_headers
  )
add_dependencies(fatfs_host linux-sgx-ext)

get_property(HOST_SOURCES TARGET fatfs_host PROPERTY SOURCES)
determinise_compile(${HOST_SOURCES})
