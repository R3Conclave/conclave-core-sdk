cmake_minimum_required(VERSION 3.16.3)

project(gramine_dcap)

set(CMAKE_INSTALL_LIBDIR "/usr/lib")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++14 -Wall -Wextra")

add_compile_options(-Wall -Wextra -Wno-unused-parameter)

add_library(gramine_dcap SHARED
    gramine_dcap.cpp
    gramine_jni.cpp
    jni_utils.cpp
  )


target_include_directories(gramine_dcap PUBLIC
  /usr/lib/jvm/java-17-openjdk-amd64/include
  /usr/lib/jvm/java-17-openjdk-amd64/include/linux
  .
)

install(TARGETS gramine_dcap
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    PUBLIC_HEADER DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})
