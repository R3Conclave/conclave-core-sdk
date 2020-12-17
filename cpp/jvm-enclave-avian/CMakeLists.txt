project(jvm-enclave-avian)

include(Determinise)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Werror -Wno-error=nonnull")

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    add_definitions(-DNDEBUG)
elseif(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ggdb")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ggdb")
else()
    set(CMAKE_CXX_FLAGS_DEBUG "-ggdb")
    set(CMAKE_C_FLAGS_DEBUG "-ggdb")
endif()

# generate-dlsym
# This target generates a hand-rolled dlsym that resolves symbol names to statically linked symbols.
add_executable(gen_dlsym IMPORTED)
set_property(TARGET gen_dlsym PROPERTY IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_dlsym.py)
set(GENERATED_DLSYM_DIR ${CMAKE_CURRENT_SOURCE_DIR}/generated_dlsym)
file(MAKE_DIRECTORY ${GENERATED_DLSYM_DIR})
add_custom_command(
        OUTPUT ${GENERATED_DLSYM_DIR}/dlsym.cpp
        COMMAND gen_dlsym ${CMAKE_CURRENT_SOURCE_DIR}/resources/dlsym.cpp.template ${GENERATED_DLSYM_DIR}/dlsym.cpp ${GENERATED_DLSYM_DIR}/dlsym.txt $<TARGET_FILE:avian>
        DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/resources/dlsym.cpp.template ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_dlsym.py $<TARGET_FILE:avian>)
set_source_files_properties(${GENERATED_DLSYM_DIR}/dlsym.cpp PROPERTIES GENERATED TRUE)
add_custom_target(dlsym DEPENDS ${GENERATED_DLSYM_DIR}/dlsym.cpp)
add_dependencies(dlsym avian)

# generate-stubs
# This target generates stub methods for certain openjdk calls that we don't implement, like fseek.
add_executable(gen_stubs IMPORTED)
set_property(TARGET gen_stubs PROPERTY IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_stubs.py)
set(GENERATED_STUBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/generated_stubs)
file(MAKE_DIRECTORY ${GENERATED_STUBS_DIR})
add_custom_command(
        OUTPUT ${GENERATED_STUBS_DIR}/stubs.cpp.none
        COMMAND gen_stubs $<TARGET_FILE:jvm_enclave_avian_partial> ${CMAKE_CURRENT_SOURCE_DIR}/resources/stubs.cpp.template ${GENERATED_STUBS_DIR}/stubs.cpp ${GENERATED_STUBS_DIR}/stubs.txt
        DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/resources/stubs.cpp.template ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_stubs.py $<TARGET_FILE:jvm_enclave_avian_partial>)
set_source_files_properties(${GENERATED_STUBS_DIR}/stubs.cpp.none PROPERTIES GENERATED TRUE)
add_custom_target(stubs DEPENDS ${GENERATED_STUBS_DIR}/stubs.cpp.none)
add_dependencies(stubs jvm_enclave_avian_partial)

# boot.jar
# This target creates the boot.jar
set(BOOT_JAR_PATH ${CMAKE_CURRENT_BINARY_DIR}/boot.jar)
set(PROGUARD_JAR_PATH )
set(PROGUARD_FILTER "(!META-INF/*.MF,!META-INF/*.SF,!META-INF/*.DSA,!META-INF/*.RSA)")
get_target_property(AVIAN_CLASSPATH_JAR_PATH avian CLASSPATH_JAR_PATH)
get_target_property(AVIAN_VM_PRO_PATH avian VM_PRO_PATH)
add_custom_command(
        OUTPUT ${BOOT_JAR_PATH}
        COMMAND bash ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_repeatable_jar.sh ${BOOT_JAR_PATH} ${AVIAN_CLASSPATH_JAR_PATH}
        DEPENDS ${AVIAN_CLASSPATH_JAR_PATH} ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_repeatable_jar.sh)
add_custom_target(avian-boot-jar DEPENDS ${BOOT_JAR_PATH})
add_dependencies(avian-boot-jar avian)
set_source_files_properties(${BOOT_JAR_PATH} PROPERTIES GENERATED TRUE)

# boot.jar.o
# This target creates an object file out of avian's classpath.jar
set(BOOT_JAR_O_PATH ${CMAKE_CURRENT_BINARY_DIR}/boot.jar.o)
get_filename_component(BOOT_JAR_DIR ${BOOT_JAR_PATH} DIRECTORY)
get_filename_component(BOOT_JAR_NAME ${BOOT_JAR_PATH} NAME)
add_custom_command(
        OUTPUT ${BOOT_JAR_O_PATH}
        COMMAND ${CMAKE_OBJCOPY} -D -I binary -O elf64-x86-64 -B i386 ${BOOT_JAR_NAME} ${BOOT_JAR_O_PATH}
        WORKING_DIRECTORY ${BOOT_JAR_DIR}
        DEPENDS avian ${BOOT_JAR_PATH})
set_source_files_properties(${BOOT_JAR_O_PATH} PROPERTIES GENERATED TRUE)
add_custom_target(avian-boot-jar-o DEPENDS ${BOOT_JAR_O_PATH})
add_dependencies(avian-boot-jar-o avian-boot-jar) # need this double dependency declaration to workaround a CMake limitation

# javahome.jar.o
# Embed JRE files in object file
set(JAVAHOME_JAR_O_PATH ${CMAKE_CURRENT_BINARY_DIR}/javahome.jar.o)
set(JAVAHOME_JAR_PATH ${CMAKE_CURRENT_BINARY_DIR}/javahome.jar)
get_target_property(AVIAN_JAVAHOME_JAR_PATH avian JAVAHOME_JAR_PATH)
get_filename_component(JAVAHOME_JAR_DIR ${JAVAHOME_JAR_PATH} DIRECTORY)
get_filename_component(JAVAHOME_JAR_NAME ${JAVAHOME_JAR_PATH} NAME)
add_custom_command(
        OUTPUT ${JAVAHOME_JAR_O_PATH}
        COMMAND bash ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_repeatable_jar.sh ${JAVAHOME_JAR_PATH} ${AVIAN_JAVAHOME_JAR_PATH}
        COMMAND ${CMAKE_OBJCOPY} -D -I binary -O elf64-x86-64 -B i386 ${JAVAHOME_JAR_NAME} ${JAVAHOME_JAR_O_PATH}
        WORKING_DIRECTORY ${JAVAHOME_JAR_DIR}
        DEPENDS avian ${AVIAN_JAVAHOME_JAR_PATH} ${CMAKE_CURRENT_SOURCE_DIR}/resources/gen_repeatable_jar.sh)
set_source_files_properties(${JAVAHOME_JAR_O_PATH} PROPERTIES GENERATED TRUE)
add_custom_target(avian-javahome-jar-o DEPENDS ${JAVAHOME_JAR_O_PATH})

# jvm_enclave_avian_partial
# This includes the JNI code initializing the JVM and forwarding ECALLS in the enclave and the SGX implementation of the
# Avian OS interface
if(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    set(SIM_SUFFIX "_sim")
else()
    set(SIM_SUFFIX "")
endif()

set(CMAKE_CXX_IMPLICIT_LINK_LIBRARIES "")
set(CMAKE_CXX_IMPLICIT_LINK_DIRECTORIES "")
add_executable(jvm_enclave_avian_partial
        ${GENERATED_DLSYM_DIR}/dlsym.cpp
        src/avian/system.cpp
        src/avian/memory.cpp
        src/avian/signal.cpp
        src/singleton_jvm.cpp
        src/debug_utils.cpp
        src/edl.cpp
        src/fcntl.cpp
        src/sys_stat.cpp
        src/unistd.cpp
        ${BOOT_JAR_O_PATH}
        ${JAVAHOME_JAR_O_PATH})
set_target_properties(jvm_enclave_avian_partial PROPERTIES LINKER_LANGUAGE C)
target_compile_options(jvm_enclave_avian_partial PUBLIC -nostdinc -fPIC)
if(${CMAKE_BUILD_TYPE} STREQUAL "Simulation")
    target_compile_definitions(jvm_enclave_avian_partial PRIVATE -DSGX_SIM)
else()
    target_compile_definitions(jvm_enclave_avian_partial PRIVATE -DSGX)
endif()
target_include_directories(jvm_enclave_avian_partial PUBLIC include ${GENERATED_EDL_DIR})
target_link_libraries(jvm_enclave_avian_partial
        avian_headers
        linux-sgx_headers
        linux-sgx_tstdc_headers
        jvm_enclave_edl
        "-Wl,--whole-archive"
        jvm_enclave_common
        jvm_host_enclave_common_enclave
        "-Wl,--no-whole-archive"
        avian
        linux-sgx_tservice${SIM_SUFFIX}
        linux-sgx_tcxx
        "-Wl,--whole-archive"
        linux-sgx_trts${SIM_SUFFIX}
        "-Wl,--no-whole-archive"
        linux-sgx_tcrypto
        linux-sgx_tstdc
        zlib

        "-nostdlib"
        "-nodefaultlibs"
        "-nostartfiles"
        "-no-pie"   # https://wiki.ubuntu.com/SecurityTeam/PIE
        "-Wl,--entry=enclave_entry"
        "-Wl,--build-id=none"
        "-Wl,-Bstatic"
        "-Wl,-Bsymbolic"
        "-Wl,--export-dynamic"
        "-Wl,--relocatable"
        "-Wl,--defsym,__ImageBase=0"
        "-Wl,--defsym,__HeapSize=0"
        "-Wl,--defsym,__StackSize=0")
add_dependencies(jvm_enclave_avian_partial linux-sgx-ext avian-boot-jar-o avian-javahome-jar-o dlsym)

add_executable(jvm_enclave_avian
        src/jvm_enclave_avian.cpp)
target_compile_options(jvm_enclave_avian PUBLIC -nostdinc -fPIC)
target_link_libraries(jvm_enclave_avian
        $<TARGET_FILE:jvm_enclave_avian_partial>
        "-nostdlib"
        "-nodefaultlibs"
        "-nostartfiles"
        "-no-pie"   # https://wiki.ubuntu.com/SecurityTeam/PIE
        "-Wl,--entry=enclave_entry"
        "-Wl,--build-id=none"
        "-Wl,-Bstatic"
        "-Wl,-Bsymbolic"
        "-Wl,--export-dynamic"
        "-Wl,--relocatable"
        "-Wl,--defsym,__DeadlockTimeout=10"
        "-Wl,--defsym,__ImageBase=0")
add_dependencies(jvm_enclave_avian jvm_enclave_avian_partial)

if(${CMAKE_BUILD_TYPE} STREQUAL "Release")
    # Remove all debug symbols from this binary.
    target_link_libraries(jvm_enclave_avian "-Wl,-S")

    # Remove anything we don't need to process relocations, e.g. ".localalias."
    add_custom_command(TARGET jvm_enclave_avian
        POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:jvm_enclave_avian>
    )
endif()

get_property(ENCLAVE_SOURCES TARGET jvm_enclave_avian PROPERTY SOURCES)
get_property(ENCLAVE_PARTIAL_SOURCES TARGET jvm_enclave_avian_partial PROPERTY SOURCES)
determinise_compile(${AVIAN_OS_SOURCES} ${ENCLAVE_SOURCES} ${ENCLAVE_PARTIAL_SOURCES})
