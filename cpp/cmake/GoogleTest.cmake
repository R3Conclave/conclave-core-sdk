# Include GTest and GMock in the project.

# Download and build googletest aka "gtest + gmock" at configuration time.
include(DownloadProject)
download_project(PROJ                googletest
                 GIT_REPOSITORY      https://github.com/google/googletest.git
                 GIT_TAG             release-1.10.0
                 ${UPDATE_DISCONNECTED_IF_AVAILABLE}
)
# make gtest and gmock available to subprojects
add_subdirectory(${googletest_SOURCE_DIR} ${googletest_BINARY_DIR})

# include the helper function TargetTest
include(TargetTest)