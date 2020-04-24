package com.r3.conclave.samples.djvm.enclave

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.SandboxRuntimeContext
import net.corda.djvm.messages.Severity
import net.corda.djvm.messages.Severity.WARNING
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.source.ApiSource
import net.corda.djvm.source.UserPathSource
import net.corda.djvm.source.UserSource
import java.nio.file.Path
import java.util.function.Consumer
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
    }

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
}