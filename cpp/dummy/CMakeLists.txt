project(dummy)

# app.jar.o
# This target creates an object file out of app.jar
set(APP_JAR_O_PATH ${CMAKE_CURRENT_BINARY_DIR}/app.jar.o)
add_custom_command(
        OUTPUT ${APP_JAR_O_PATH}
        COMMAND ${CMAKE_OBJCOPY} -I binary -O elf64-x86-64 -B i386 app.jar ${APP_JAR_O_PATH}
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
        DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/app.jar)
set_source_files_properties(${APP_JAR_O_PATH} PROPERTIES GENERATED TRUE)

# Dummy key
set(DUMMY_KEY_PATH ${CMAKE_CURRENT_BINARY_DIR}/dummy_key.pem)
add_custom_command(
        OUTPUT ${DUMMY_KEY_PATH}
        COMMAND openssl genrsa -out ${DUMMY_KEY_PATH} -3 3072)
add_custom_target(dummy-key DEPENDS ${DUMMY_KEY_PATH})

# dummy
add_executable(dummy src/dummy.cpp ${APP_JAR_O_PATH})
target_link_libraries(dummy
        $<TARGET_FILE:jvm_enclave_avian>)
set_property(TARGET dummy PROPERTY POSITION_INDEPENDENT_CODE ON)
add_dependencies(dummy jvm_enclave_avian)

set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} \
    -nostdlib \
    -nodefaultlibs \
    -nostartfiles \
    -Wl,-pie,--entry=enclave_entry \
    -Wl,-Bstatic \
    -Wl,-Bsymbolic \
    -Wl,--no-undefined \
    -Wl,--export-dynamic \
    -Wl,--defsym,__ImageBase=0")

# Dummy signed enclave
add_custom_command(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/dummy.signed.so
        COMMAND sign_enclave sign -key ${DUMMY_KEY_PATH} -enclave $<TARGET_FILE:dummy> -out ${CMAKE_CURRENT_BINARY_DIR}/dummy.signed.so -config ${CMAKE_CURRENT_SOURCE_DIR}/resources/enclave.xml
        DEPENDS dummy dummy-key ${CMAKE_CURRENT_SOURCE_DIR}/resources/enclave.xml)
add_custom_target(dummy-signed DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/dummy.signed.so)
add_custom_target(dummy-test DEPENDS dummy-signed jvm_host)
