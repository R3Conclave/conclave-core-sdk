package com.r3.conclave.samples.djvm.enclave

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.SandboxConfiguration.Companion.ALL_DEFINITION_PROVIDERS
import net.corda.djvm.SandboxConfiguration.Companion.ALL_EMITTERS
import net.corda.djvm.SandboxRuntimeContext
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.code.DefinitionProvider
import net.corda.djvm.code.Emitter
import net.corda.djvm.execution.ExecutionProfile
import net.corda.djvm.messages.Severity
import net.corda.djvm.messages.Severity.WARNING
import net.corda.djvm.rewiring.LoadedClass
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.rules.Rule
import net.corda.djvm.source.ApiSource
import net.corda.djvm.source.ClassSource
import net.corda.djvm.source.UserPathSource
import net.corda.djvm.source.UserSource
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.thread

abstract class DJVMBase {
    companion object {
        private lateinit var bootstrapSource: ApiSource
        private lateinit var userSource: UserSource
        private lateinit var sandboxConfiguration: SandboxConfiguration
        private lateinit var sandboxClassLoader: SandboxClassLoader
        private lateinit var classPaths: List<Path>

        @JvmStatic
        fun setupClassLoader(userSource: UserSource, bootstrapSource: ApiSource, testingLibraries: List<Path>, sandboxConfiguration: SandboxConfiguration) {
            Companion.bootstrapSource = bootstrapSource
            Companion.userSource = userSource
            Companion.sandboxConfiguration = sandboxConfiguration
            sandboxClassLoader = SandboxClassLoader.createFor(sandboxConfiguration)
            classPaths = testingLibraries
        }

        @JvmStatic
        fun destroyRootContext() {
            bootstrapSource.close()
        }

        @JvmStatic
        @Throws(
                ClassNotFoundException::class,
                InstantiationException::class,
                IllegalAccessException::class
        )
        fun <T, R> SandboxClassLoader.typedTaskFor(
                taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
                taskClass: Class<out Function<T, R>>
        ): Function<T, R> {
            @Suppress("unchecked_cast")
            return createTaskFor(taskFactory, taskClass) as Function<T, R>
        }

        inline fun <T, R, reified K : Function<T, R>> SandboxClassLoader.typedTaskFor(
                taskFactory: Function<in Any, out Function<in Any?, out Any?>>
        ) = typedTaskFor(taskFactory, K::class.java)
    }

    /**
     * Run action on a separate thread to ensure that the code is run off a clean slate. The sandbox context is local to
     * the current thread, so this allows inspection of the cost summary object, etc. from within the provided delegate.
     */
    fun customSandbox(
            visibleAnnotations: Set<Class<out Annotation>>,
            action: SandboxRuntimeContext.() -> Unit
    ) {
        return customSandbox(
                visibleAnnotations = visibleAnnotations,
                minimumSeverityLevel = WARNING,
                enableTracing = true,
                action = action
        )
    }

    fun customSandbox(action: SandboxRuntimeContext.() -> Unit) = customSandbox(emptySet(), action)

    fun customSandbox(
            vararg options: Any,
            visibleAnnotations: Set<Class<out Annotation>> = emptySet(),
            sandboxOnlyAnnotations: Set<String> = emptySet(),
            minimumSeverityLevel: Severity = WARNING,
            enableTracing: Boolean = true,
            action: SandboxRuntimeContext.() -> Unit
    ) {
        val rules = mutableListOf<Rule>()
        val emitters = mutableListOf<Emitter>().apply { addAll(ALL_EMITTERS) }
        val definitionProviders = mutableListOf<DefinitionProvider>().apply { addAll(ALL_DEFINITION_PROVIDERS) }
        val classSources = mutableListOf<ClassSource>()
        var executionProfile = ExecutionProfile.UNLIMITED
        var whitelist = Whitelist.MINIMAL
        for (option in options) {
            when (option) {
                is Rule -> rules.add(option)
                is Emitter -> emitters.add(option)
                is DefinitionProvider -> definitionProviders.add(option)
                is ExecutionProfile -> executionProfile = option
                is ClassSource -> classSources.add(option)
                is Whitelist -> whitelist = option
                is List<*> -> {
                    rules.addAll(option.filterIsInstance<Rule>())
                    emitters.addAll(option.filterIsInstance<Emitter>())
                    definitionProviders.addAll(option.filterIsInstance<DefinitionProvider>())
                }
            }
        }
        var thrownException: Throwable? = null
        thread {
            try {
                UserPathSource(classPaths).use {
                    val analysisConfiguration = AnalysisConfiguration.createRoot(
                            userSource = userSource,
                            whitelist = whitelist,
                            visibleAnnotations = visibleAnnotations,
                            sandboxOnlyAnnotations = sandboxOnlyAnnotations,
                            minimumSeverityLevel = minimumSeverityLevel,
                            bootstrapSource = bootstrapSource
                    )
                    SandboxRuntimeContext(SandboxConfiguration.of(
                            executionProfile,
                            rules.distinctBy(Any::javaClass),
                            emitters.distinctBy(Any::javaClass),
                            definitionProviders.distinctBy(Any::javaClass),
                            analysisConfiguration
                    )).use(Consumer { ctx ->
                        ctx.action()
                    })
                }
            } catch (exception: Throwable) {
                thrownException = exception
            }
        }.join()
        throw thrownException ?: return
    }

    fun sandbox(visibleAnnotations: Set<Class<out Annotation>>, action: SandboxRuntimeContext.() -> Unit)
            = sandbox(WARNING, visibleAnnotations, emptySet(), action)

    fun sandbox(visibleAnnotations: Set<Class<out Annotation>>, sandboxOnlyAnnotations: Set<String>, action: SandboxRuntimeContext.() -> Unit)
            = sandbox(WARNING, visibleAnnotations, sandboxOnlyAnnotations, action)

    fun sandbox(action: SandboxRuntimeContext.() -> Unit)
            = sandbox(WARNING, emptySet(), emptySet(), action)

    fun sandbox(
            minimumSeverityLevel: Severity,
            visibleAnnotations: Set<Class<out Annotation>>,
            sandboxOnlyAnnotations: Set<String>,
            action: SandboxRuntimeContext.() -> Unit
    ) {
        var thrownException: Throwable? = null
        thread {
            try {
                UserPathSource(classPaths).use {
                    SandboxRuntimeContext(sandboxConfiguration.createChild(userSource, Consumer { builder ->
                        builder.setMinimumSeverityLevel(minimumSeverityLevel)
                        builder.setSandboxOnlyAnnotations(sandboxOnlyAnnotations)
                        builder.setVisibleAnnotations(visibleAnnotations)
                    })).use(Consumer {ctx ->
                        ctx.action()
                    })
                }
            } catch (exception: Throwable) {
                thrownException = exception
            }
        }.join()
        throw thrownException ?: return
    }

    fun SandboxRuntimeContext.loadClass(className: String): LoadedClass = classLoader.loadForSandbox(className)
}