project(dummy)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g")
endif()

get_target_property(GENERATED_EDL_DIR jvm_host_edl GENERATED_EDL_DIR)
add_executable(dummy_host
        src/dummy_host.cpp)
target_include_directories(dummy_host PUBLIC
        ${GENERATED_EDL_DIR})
target_link_libraries(dummy_host
        jvm_host)

get_property(DUMMY_HOST_SOURCES TARGET dummy_host PROPERTY SOURCES)
determinise_compile(${DUMMY_HOST_SOURCES})
