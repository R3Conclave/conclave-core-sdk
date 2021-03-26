package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Graal's native-image tool has a large set of options which can be inspected with
 * `--help`, `--expert-options` and `--expert-options-all`. Some of the relevant options in use are:
 * ```
 * -cp                                  <class search path of directories and zip/jar files>
 * --shared                             build shared library
 * --no-fallback                        build stand-alone image or report failure
 * --no-server                          Disable the native-image build server. We set this because there seems to be a problem with the
 *                                      build server where repeated builds cause the memory usage of the server to rise until the system
 *                                      is out of memory. This may be related to this issue: https://github.com/oracle/graal/issues/1161.
 *                                      In practice, the build is faster and uses less resources without the build server anyway.
 * -H:Name=""                           Name of the output file to be generated.
 * -H:Path=""                           Directory of the image file to be generated.
 * -H:CCompilerOption=...               Provide custom C compiler option used for query code compilation. Default: None
 * -H:CLibraryPath=...                  Search path for C libraries passed to the linker (list of comma-separated directories).
 * -H:NativeLinkerOption=...            Pass the provided raw option to the linker command that produces the final binary. The possible options are platform
 *                                      specific and passed through without any validation. Default: None
 * -H:±SpawnIsolates                    Support multiple isolates.
 * -H:±UseStaticLinking                 Use static linking. Default: - (disabled).
 * -H:±ExcludeLoadingNetwork            Exclude loading net library.
 * -H:ExcludeLibraries=...              Default libraries to be excluded by the linker (list of comma-separated library names, i.e., dl,pthreads). Default: None
 * -H:±DeleteLocalSymbols               Use linker option to remove all local symbols from image. Default: + (enabled).
 * -H:GenerateDebugInfo=0               Insert debug info into the generated native image or library.
 * -H:±JNIExportSymbols                 Export Invocation API symbols. Default: + (enabled).
 * -H:±RemoveUnusedSymbols              Use linker option to prevent unreferenced symbols in image. Default: - (disabled).
 * -H:±ReportExceptionStackTraces       Show exception stack traces for exceptions during image building.). Default: - (disabled).
 * -H:ReflectionConfigurationFiles=...  One or several (comma-separated) paths to JSON files that specify which program elements should be made available via
reflection. Default: None
 * -R:MaxHeapSize=0                     The maximum heap size at run-time, in bytes.
 * -R:StackSize=0                       The size of each thread stack at run-time, in bytes.
 * -H:AlignedHeapChunkSize=4096         (CURRENTLY DISABLED! Left here for future reference.) Having a heap chunk with 4KB size could significantly affect
 *                                      the JVM heap performance although the significantly affect the JVM heap performance although the benchmark sample does
 *                                      not seem to show this.
 * ```
*/

open class NativeImage @Inject constructor(
        objects: ObjectFactory,
        private val buildType: BuildType,
        private val linkerScript: Path,
        private val linuxExec: LinuxExec) : ConclaveTask() {
    companion object {
        /**
         * Portion of the enclave's maximum heap size available to SubstrateVM,
         * so OutOfMemory exceptions can be thrown before the enclave runs out
         * of heap and crashes.
         */
        private const val HEAP_SIZE_PERCENTAGE = 0.8
    }

    @get:InputDirectory
    val nativeImagePath: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val jarFile: RegularFileProperty = objects.fileProperty()

    @get:InputFiles
    val includePaths: ConfigurableFileCollection = objects.fileCollection()

    @get:InputDirectory
    val libraryPath: RegularFileProperty = objects.fileProperty()

    @get:InputFiles
    val libraries: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFiles
    val librariesWholeArchive: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFile
    val reflectionConfiguration: RegularFileProperty = objects.fileProperty()

    @get:InputFiles
    val reflectionConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFiles
    val resourcesConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFiles
    val serializationConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:Input
    val maxStackSize: Property<String> = objects.property(String::class.java)

    @get:Input
    val maxHeapSize: Property<String> = objects.property(String::class.java)

    @get:Input
    val supportLanguages: Property<String> = objects.property(String::class.java)

    @get:Input
    val deadlockTimeout: Property<Int> = objects.property(Int::class.java)

    @get:OutputFile
    val outputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val ldPath: RegularFileProperty = objects.fileProperty()

    private fun defaultOptions() = listOf(
        "--no-fallback",
        "--no-server",
        "-H:+UseStaticLinking",
        "-H:+ExportStaticSymbols",
        "-H:+ExcludeLoadingNetwork",
        "-H:ExcludeLibraries=pthread,dl,rt,z",
        "-H:-SpawnIsolates",
        "-H:+ForceNoROSectionRelocations",
        // "-H:AlignedHeapChunkSize=4096",
        "-R:MaxHeapSize=" + calculateMaxHeapSize(GenerateEnclaveConfig.getSizeBytes(maxHeapSize.get())),
        "-R:StackSize=" + calculateMaxStackSize(),
        "--enable-all-security-services",
        /*
         * Explicitly set Jimfs classes and its guava dependency to initialize at build time,
         * otherwise NativeImage claims they were unintentionally initialized at build time and errors.
         * These classes are being initialized at build time since the new default filesystem is being
         * instantiated during Graal's build.
         */
        "--initialize-at-build-time=com.r3.conclave.shaded.com.google.common",
        "--initialize-at-build-time=com.r3.conclave.filesystem.jimfs",
        "-H:-AddAllFileSystemProviders",
        "--features=com.oracle.svm.core.jdk.DefaultFileSystemFeature"
    )

    private val compilerOptions get() = listOf(
            "-H:CCompilerOption=-B${ldPath.get().asFile.parentFile.absolutePath}",
            "-H:CCompilerOption=-fvisibility=hidden",
            "-H:CCompilerOption=-fpie",
            "-H:CCompilerOption=-ffunction-sections",
            "-H:CCompilerOption=-fdata-sections",
            "-H:CCompilerOption=-fstack-protector-strong"
    )

    private val debugOptions = listOf(
            "-H:GenerateDebugInfo=1",
            "-H:-DeleteLocalSymbols",
            "-H:+JNIExportSymbols",
            "-H:-RemoveUnusedSymbols",
            "-H:+ReportExceptionStackTraces",
            "-H:CCompilerOption=-ggdb"
    )

    private fun calculateMaxHeapSize(value: Long): Int {
        return (value * HEAP_SIZE_PERCENTAGE).roundToInt()
    }

    private fun calculateMaxStackSize(): Long {
        // Substrate VM takes the value of stack we pass in to native-image and adds a yellow and red zone
        // area to this. This propogates through to a call to pthread_attr_setstacksize() with a value that
        // is greater than the size we pass to native-image. The stack cannot be bigger than what we
        // configure in the Enclave configuration file so subtract the red and yellow zone default sizes.
        // See Target_java_lang_Thread.java in the SVM source code to see where the stack size is altered.
        // Default yellow and red zone sizes are in StackOverflowCheck.java in SVM:
        // yellow zone size = 32K
        // red zone size = 8K
        val zoneSize = (32 * 1024) + (8 * 1024)
        val stackSize = GenerateEnclaveConfig.getSizeBytes(maxStackSize.get())
        if (stackSize <= zoneSize) {
            // Invalid stack size
            throw GradleException("The configured stack size is too small (<= 40K). Please specify a larger stack size in the Conclave configuration for your enclave.");
        }
        return stackSize - zoneSize
    }

    private fun includePathsOptions(): List<String> {
        return includePaths.files.map { "-H:CCompilerOption=-I$it" }.toList()
    }

    private fun libraryPathOption(): String {
        return "-H:NativeLinkerOption=-L" + libraryPath.get().asFile.absolutePath
    }

    private fun librariesOptions(): List<String> {
        return libraries.files.map { "-H:NativeLinkerOption=$it" }.toList()
    }

    private fun librariesWholeArchiveOptions(): List<String> {
        return librariesWholeArchive.files.map { "-H:NativeLinkerOption=$it" }.toList()
    }

    private fun placeholderLibPathOption(): String {
        // The placeholder libraries are just empty library archives to prevent any libraries that native-image
        // decides to link against from clashing with the trusted SGX runtime. We just need to ensure the linker
        // adds the path to the directory containing the placeholders to prevent it from pulling in the OS native
        // versions of the libraries.
        return "-H:NativeLinkerOption=-L" + nativeImagePath.get().asFile.absolutePath + "/placeholderlibs"
    }

    /**
     * Detailed documentation regarding linker options can be found at
     * https://sourceware.org/binutils/docs-2.32/ld/Options.html#Options
     * and https://gcc.gnu.org/onlinedocs/gcc/Link-Options.html
     *
     * -z keyword
     *  Keywords:
     *      relro
     *      Create an ELF PT_GNU_RELRO segment header in the object.
     *      This specifies a memory segment that should be made read-only after relocation, if supported.
     *      Specifying ‘common-page-size’ smaller than the system page size will render this protection ineffective.
     *
     *      now
     *      When generating an executable or shared library, mark it to tell the dynamic linker to resolve all symbols
     *      when the program is started, or when the shared library is loaded by dlopen, instead of deferring function call
     * resolution to the point when the function is first called.
     *
     *      noexecstack
     *      Marks the object as not requiring executable stack.
     *
     * -l namespec
     * Add the archive or object file specified by namespec to the list of files to link.
     * This option may be used any number of times. If namespec is of the form :filename, ld will search
     * the library path for a file called filename, otherwise it will search the library path for a file
     * called libnamespec.a.
     *
     * On systems which support shared libraries, ld may also search for files other than libnamespec.a.
     * Specifically, on ELF and SunOS systems, ld will search a directory for a library called libnamespec.so
     * before searching for one called libnamespec.a. (By convention, a .so extension indicates a shared library.)
     * Note that this behavior does not apply to :filename, which always specifies a file called filename.
     *
     * The linker will search an archive only once, at the location where it is specified on the command line.
     * If the archive defines a symbol which was undefined in some object which appeared before the archive
     * on the command line, the linker will include the appropriate file(s) from the archive.
     * However, an undefined symbol in an object appearing later on the command line will not cause the
     * linker to search the archive again.
     *
     * See the -( option for a way to force the linker to search archives multiple times.
     *
     * You may list the same archive multiple times on the command line.
     *
     * This type of archive searching is standard for Unix linkers. However, if you are using ld on AIX,
     * note that it is different from the behaviour of the AIX linker.
     *
     * --no-undefined
     * Report unresolved symbol references from regular object files. This is done even if the linker
     * is creating a non-symbolic shared library. The switch --[no-]allow-shlib-undefined controls
     * the behaviour for reporting unresolved references found in shared libraries being linked in.
     *
     * The effects of this option can be reverted by using -z undefs.
     *
     * -nostdlib
     * Only search library directories explicitly specified on the command line.
     * Library directories specified in linker scripts (including linker scripts specified on the command line) are ignored.
     *
     * -nodefaultlibs
     * Do not use the standard system libraries when linking. Only the libraries you specify are passed to the linker,
     * and options specifying linkage of the system libraries, such as -static-libgcc or -shared-libgcc, are ignored.
     * The standard startup files are used normally, unless -nostartfiles is used.
     *
     * The compiler may generate calls to memcmp, memset, memcpy and memmove. These entries are usually resolved by entries in libc.
     * These entry points should be supplied through some other mechanism when this option is specified.
     *
     * -nostartfiles
     * Do not use the standard system startup files when linking.
     * The standard system libraries are used normally, unless -nostdlib, -nolibc, or -nodefaultlibs is used.
     *
     * --whole-archive
     * For each archive mentioned on the command line after the --whole-archive option, include every object
     * file in the archive in the link, rather than searching the archive for the required object files.
     * This is normally used to turn an archive file into a shared library, forcing every object
     * to be included in the resulting shared library. This option may be used more than once.
     * This task takes a configuration parameter named 'librariesWholeArchive' where the caller can specify a
     * set of libraries that live within a '--whole-archive' section. This is required when the static library
     * contains global constructors or variables that must be initialised but are not then referenced outside
     * of the library.
     *
     * Two notes when using this option from gcc: First, gcc doesn’t know about this option, so you have to use
     * -Wl,-whole-archive. Second, don’t forget to use -Wl,-no-whole-archive after your list of archives,
     * because gcc will add its own list of archives to your link and you may not want this flag to affect those as well.
     *
     * --start-group archives --end-group
     * The archives should be a list of archive files. They may be either explicit file names, or ‘-l’ options.
     *
     * The specified archives are searched repeatedly until no new undefined references are created.
     * Normally, an archive is searched only once in the order that it is specified on the command line.
     * If a symbol in that archive is needed to resolve an undefined symbol referred to by an object in an
     * archive that appears later on the command line, the linker would not be able to resolve that reference.
     * By grouping the archives, they all be searched repeatedly until all possible references are resolved.
     *
     * Using this option has a significant performance cost. It is best to use it only when there are unavoidable
     * circular references between two or more archives.
     *
     * -Bstatic
     * Do not link against shared libraries. This is only meaningful on platforms for which shared libraries are supported.
     * The different variants of this option are for compatibility with various systems.
     * You may use this option multiple times on the command line: it affects library searching for -l options which follow it.
     * This option also implies --unresolved-symbols=report-all. This option can be used with -shared.
     * Doing so means that a shared library is being created but that all of the library’s external references
     * must be resolved by pulling in entries from static libraries.
     *
     * -e entry
     * Use entry as the explicit symbol for beginning execution of your program, rather than the default entry point.
     * If there is no symbol named entry, the linker will try to parse entry as a number,
     * and use that as the entry address (the number will be interpreted in base 10; you may use a leading ‘0x’ for base 16,
     * or a leading ‘0’ for base 8).
     * See Entry Point (https://sourceware.org/binutils/docs-2.32/ld/Entry-Point.html#Entry-Point), for a discussion of defaults and other ways of specifying the entry point.
     *
     * --defsym
     * Define symbols within the object file. Two symbols are defined; __ImageBase and __HeapSize. __ImageBase is set
     * to 0 but when the executable is linked and relocated, will be at offset 0 from the relocate base. Therefore getting
     * the address of this global variable will return the base address of the exectuable (enclave). __HeapSize is set to
     * an address equivalent to the size of the enclave heap in pages. This is not a real address and may be outside the enclave
     * image. However, it allows pointer arithmetic to be use in the unistd.cpp stub to determine the heap size in pages
     * without a compile-time option.
     *
     * --export-dynamic
     * When creating a dynamically linked executable, using the -E option or the --export-dynamic option
     * causes the linker to add all symbols to the dynamic symbol table. The dynamic symbol table is the set of symbols
     * which are visible from dynamic objects at run time.
     *
     * If you do not use either of these options (or use the --no-export-dynamic option to restore the default behavior),
     * the dynamic symbol table will normally contain only those symbols which are referenced
     * by some dynamic object mentioned in the link.
     *
     * If you use dlopen to load a dynamic object which needs to refer back to the symbols defined by the program,
     * rather than some other dynamic object, then you will probably need to use this option when linking the program itself.
     *
     * You can also use the dynamic list to control what symbols should be added to the dynamic symbol table
     * if the output format supports it. See the description of ‘--dynamic-list’.
     *
     * Note that this option is specific to ELF targeted ports.
     * PE targets support a similar function to export all symbols from a DLL or EXE;
     * see the description of ‘--export-all-symbols’ below.
     *
     * --gc-sections
     * Enable garbage collection of unused input sections. It is ignored on targets that do not support this option.
     * The default behaviour (of not performing this garbage collection) can be restored by specifying ‘--no-gc-sections’
     * on the command line. Note that garbage collection for COFF and PE format targets is supported,
     * but the implementation is currently considered to be experimental.
     *
     * ‘--gc-sections’ decides which input sections are used by examining symbols and relocations.
     * The section containing the entry symbol and all sections containing symbols undefined on the command-line will be kept,
     * as will sections containing symbols referenced by dynamic objects. Note that when building shared libraries,
     * the linker must assume that any visible symbol is referenced. Once this initial set of sections has been determined,
     * the linker recursively marks as used any section referenced by their relocations.
     * See ‘--entry’, ‘--undefined’, and ‘--gc-keep-exported’.
     *
     * This option can be set when doing a partial link (enabled with option ‘-r’).
     * In this case the root of symbols kept must be explicitly specified either by one of the options
     * ‘--entry’, ‘--undefined’, or ‘--gc-keep-exported’ or by a ENTRY command in the linker script.
     */
    private fun sgxLibrariesOptions(): List<String> {
        val simSuffix = if (buildType == BuildType.Simulation) "_sim" else ""
        val trtsLib = "sgx_trts$simSuffix"
        val serviceLib = "sgx_tservice$simSuffix"

        return listOf("-H:NativeLinkerOption=-Wl,-z,relro,-z,now,-z,noexecstack",
                "-H:NativeLinkerOption=-lsgx_pthread",
                "-H:NativeLinkerOption=-Wl,--no-undefined",
                "-H:NativeLinkerOption=-nostdlib",
                "-H:NativeLinkerOption=-nodefaultlibs",
                "-H:NativeLinkerOption=-nostartfiles",
                "-H:NativeLinkerOption=-Wl,--whole-archive,-l$trtsLib,--no-whole-archive",
                "-H:NativeLinkerOption=-Wl,--start-group,-lsgx_tstdc,-lsgx_tcxx,-lsgx_tcrypto,-l$serviceLib,--end-group",
                "-H:NativeLinkerOption=-Wl,-Bstatic",
                // We don't specify -Bsymbolic here, because the behaviour of this flag in combination with the others
                // appears to vary in subtle ways between ld versions causing reproducibility failures, and because it
                // doesn't do anything for enclaves (even though Intel set this option in their samples). All the flag
                // does is make a DT_SYMBOLIC tag appear in the .dynamic section but the Intel urts ELF interpreter
                // doesn't do anything with it - indeed the tag has little meaning in an enclave environment.
                "-H:NativeLinkerOption=-Wl,--no-undefined",
                "-H:NativeLinkerOption=-Wl,-pie,-eenclave_entry",
                "-H:NativeLinkerOption=-Wl,--export-dynamic",
                "-H:NativeLinkerOption=-Wl,--defsym,__ImageBase=0,--defsym,__HeapSize=" +
                            GenerateEnclaveConfig.getSizeBytes(maxHeapSize.get()) / 4096,
                "-H:NativeLinkerOption=-Wl,--defsym,__StackSize=" +
                            GenerateEnclaveConfig.getSizeBytes(maxStackSize.get()) / 4096,
                "-H:NativeLinkerOption=-Wl,--defsym,__DeadlockTimeout=" + deadlockTimeout.get(),
                "-H:NativeLinkerOption=-Wl,--gc-sections"
        )
    }

    private fun linkerScriptOption(): String {
        return "-H:NativeLinkerOption=-Wl,--version-script=$linkerScript"
    }

    private fun reflectConfigurationOption(): String {
        val fileList = if (reflectionConfigurationFiles.isEmpty) ""
            else ("," + reflectionConfigurationFiles.joinToString(separator = ",") { it.absolutePath })
        return "-H:ReflectionConfigurationFiles=${reflectionConfiguration.get().asFile.absolutePath}$fileList"
    }

    private fun serializationConfigurationOption(): String {
        return if (serializationConfigurationFiles.isEmpty) ""
        else "-H:SerializationConfigurationFiles=${serializationConfigurationFiles.joinToString(separator = ",") { it.absolutePath }}"
    }

    private fun getLanguages(): List<String> {
        if (supportLanguages.get().isNotEmpty()) {
            val languages = supportLanguages.get().split(",")
            return languages.map {
                "--language:$it"
            }
        }
        return emptyList()
    }

    private fun includeResourcesOption(): List<String> {
        if (resourcesConfigurationFiles.isEmpty)
            return emptyList()

        val files = resourcesConfigurationFiles.joinToString { it.absolutePath }
        return listOf("-H:ResourceConfigurationFiles=$files",
            "-H:Log=registerResource:verbose")
    }

    override fun action() {
        GenerateLinkerScript.writeToFile(linkerScript)
        var nativeImageFile = File(nativeImagePath.get().asFile.absolutePath + "/jre/bin/native-image")
        if (!nativeImageFile.exists()) {
            nativeImageFile = File(nativeImagePath.get().asFile.absolutePath + "/bin/native-image")
        }

        val errorOut = linuxExec.exec(
            listOf<String>(
                    nativeImageFile.absolutePath,
                    "--shared",
                    "-cp",
                    jarFile.get().asFile.absolutePath,
                    "-H:Name=enclave",
                    "-H:Path=" + outputs.files.first().parent
            )
            + (if (buildType != BuildType.Release) debugOptions else emptyList())
            + defaultOptions()
            + compilerOptions
            + placeholderLibPathOption()
            + includePathsOptions()
            + libraryPathOption()
            + "-H:NativeLinkerOption=-Wl,--whole-archive"
            + librariesWholeArchiveOptions()
            + "-H:NativeLinkerOption=-Wl,--no-whole-archive"
            + librariesOptions()
            + sgxLibrariesOptions()
            + linkerScriptOption()
            + reflectConfigurationOption()
            + includeResourcesOption()
            + serializationConfigurationOption()
            + getLanguages()
        )
        if (errorOut?.any { "Image generator watchdog is aborting image generation" in it } == true) {
            // If there is too much memory pressure in the container, native image can just get slower and slower until
            // a watchdog timer fires. Give the user some advice on how to fix it.
            linuxExec.throwOutOfMemoryException()
        }
        else if (errorOut?.any { "Default native-compiler executable 'gcc' not found via environment variable PATH" in it } == true) {
            // This error will only occur on Linux because on other platforms native-image is run in a docker container that
            // already has gcc installed.
            throw GradleException(
                    "Conclave requires gcc to be installed when building GraalVM native-image based enclaves. "
                            + "Try running 'sudo apt-get install build-essential' or the equivalent command on your distribution to install gcc. "
                            + "See https://docs.conclave.net/tutorial.html#setting-up-your-machine"
            )
        }
        else if (errorOut != null) {
            throw GradleException("The native-image enclave build failed. See the error message above for details.")
        }
    }
}
