# MODULE:   TargetTest
#
# PROVIDES:
#   FREE_ALLOCRGN_ERROR_STR(PROJ projectName
#                           TARGET              prefixDir
#                           TEST_SRC_PATH       srcDir
#                           TEST_OUT_PATH       outDir
#                           CMAKE_BINARY_DIR    binDir
#                           PUBLIC_INCLUDE      pubDir
#                           PRIVATE_INCLUDE     privDir)
#
#       Creates unit tests targets of *.cpp files placed under srcDir. Places them in 
#       outDir and sets the projectName to depend on it.
#       binDir is the root from where cmake is being executed.
#
# EXAMPLE USAGE:
#
#   include(TargetTest)
#   FREE_ALLOCRGN_ERROR_STR(PROJECT_NAME          ${PROJECT_NAME}
#               TARGET                jvm_enclave_common
#               TEST_SRC_PATH         "${CMAKE_CURRENT_SOURCE_DIR}/test"
#               TEST_OUT_PATH         "${CMAKE_CURRENT_BINARY_DIR}/test_bin"
#               CMAKE_BINARY_DIR      "${CMAKE_BINARY_DIR}"
#               INCLUDE               "${CMAKE_CURRENT_SOURCE_DIR}/include" "${CMAKE_CURRENT_SOURCE_DIR}/src")
#
#===========================================================================================================


include(CMakeParseArguments)

function(target_test)
    set(options QUIET)
    set(oneValueArgs
        PROJ
        TARGET
        TEST_SRC_PATH
        TEST_OUT_PATH
        CMAKE_BINARY_DIR)

    set(multiValueArgs 
        INCLUDE)

    cmake_parse_arguments(ARGS "${options}" "${oneValueArgs}" "${multiValueArgs}" ${ARGN})

    file(GLOB TEST_SRCS "${ARGS_TEST_SRC_PATH}/*.cpp")
    # Run through each source
    foreach(TEST_SRC ${TEST_SRCS})
        # Extract the filename without an extension (NAME_WE)
        get_filename_component(FILENAME_WE ${TEST_SRC} NAME_WE)
        set(TEST_NAME "${ARGS_PROJ}.${FILENAME_WE}")

        # Add compile target
        add_executable(${TEST_NAME}.TEST ${TEST_SRC})
        target_include_directories(${TEST_NAME}.TEST PRIVATE ${ARGS_INCLUDE})

        # set test output into test_bin
        set_target_properties(${TEST_NAME}.TEST PROPERTIES
            RUNTIME_OUTPUT_DIRECTORY ${ARGS_TEST_OUT_PATH}
            DEPENDS ${TEST_NAME}.BUILD) # Make the test to depend on its own build.
        target_link_libraries(${TEST_NAME}.TEST gtest gmock)
        # add a test build step.
        add_test(NAME ${TEST_NAME}.BUILD
            WORKING_DIRECTORY ${ARGS_TEST_OUT_PATH}
            COMMAND "${CMAKE_COMMAND}" --build ${ARGS_CMAKE_BINARY_DIR} --target ${TEST_NAME}.TEST)
        # add a test name
        add_test(NAME ${TEST_NAME}.TEST
                WORKING_DIRECTORY ${ARGS_TEST_OUT_PATH}
                COMMAND ${ARGS_TEST_OUT_PATH}/${TEST_NAME}.TEST)
        add_custom_command(TARGET ${ARGS_TARGET}
                           COMMENT "Run ${TEST_NAME} tests"
                           POST_BUILD COMMAND ${CMAKE_CTEST_COMMAND} -V ${TEST_NAME}.TEST
                           DEPENDS ${TEST_NAME}.TEST)
    endforeach(TEST_SRC)
endfunction()

