package com.r3.conclave.internaltesting.dynamic

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.BiFunction

typealias CacheKey = String

data class Directory(val asFile: File)

class Cache(val cacheDirectory: File, val executor: ExecutorService) {
    private val inMemoryCache = HashMap<CacheKey, Any>()
    @Suppress("UNCHECKED_CAST")
    operator fun <A : Any> get(cached: Cached<A>): A {
        return inMemoryCache.getOrPut(cached.key) {
            Cached.get(cached, cacheDirectory, executor).get()
        } as A
    }
}

sealed class Cached<out A : Any> {
    data class Pure<out A : Any>(val value: A) : Cached<A>()
    data class FMap<A : Any, out B : Any>(
            val f: (A) -> B,
            val a: Cached<A>
    ) : Cached<B>()
    data class FApply<A : Any, out B : Any>(
            val f: Cached<(A) -> B>,
            val a: Cached<A>
    ) : Cached<B>()
    data class SingleCached<out A : Any>(
            val name: String?,
            val providedKey: CacheKey,
            val produce: (outputDirectory: Directory) -> Unit,
            val getValue: (outputDirectory: Directory) -> A
    ) : Cached<A>()
    // TODO this might not be needed, just FApply + SingleCached
    data class FApplyCached<A : Any, out B : Any>(
            val name: String?,
            val produce: Cached<(outputDirectory: Directory, A) -> Unit>,
            val a: Cached<A>,
            val getValue: (outputDirectory: Directory) -> B
    ) : Cached<B>()

    val key: CacheKey by lazy {
        when (this) {
            is Cached.Pure -> DigestTools.sha256String(value.hashCode().toString())
            is Cached.FMap<*, *> -> DigestTools.sha256String(sha256Lambda(f) + a.key)
            is Cached.FApply<*, *> -> DigestTools.sha256String(f.key + a.key)
            is Cached.SingleCached -> providedKey
            is Cached.FApplyCached<*, *> -> DigestTools.sha256String(produce.key + a.key)
        }
    }

    fun <R : Any> product(f: Cached<(A) -> R>): Cached<R> {
        return Cached.FApply(f, this)
    }

    companion object {
        private fun sha256Lambda(lambda: Function<*>): String {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            objectOutputStream.writeObject(lambda)
            return DigestTools.sha256ByteArray(byteArrayOutputStream.toByteArray())
        }

        private fun <A> ExecutorService.fork(f: () -> A): CompletableFuture<A> {
            val completableFuture = CompletableFuture<A>()
            submit {
                try {
                    val result = f()
                    completableFuture.complete(result)
                } catch (exception: Throwable) {
                    completableFuture.completeExceptionally(exception)
                }
            }
            return completableFuture
        }

        fun singleFile(key: String, outputName: String, produce: (output: File) -> Unit): Cached<File> {
            return Cached.SingleCached(
                    name = outputName,
                    providedKey = key,
                    produce = { outputDirectory -> produce(File(outputDirectory.asFile, outputName)) },
                    getValue = { outputDirectory -> File(outputDirectory.asFile, outputName) }
            )
        }

        fun <A : Any> productFileList(cached: Cached<A>, fProduce: Cached<(output: File, List<A>) -> Unit>): Cached<(output: File, List<A>) -> Unit> {
            return cached.product(fProduce.map { produce -> { a: A -> { output: File, rest: List<A> -> produce(output, listOf(a) + rest) } } })
        }

        fun <A : Any> pure(a: A): Cached<A> {
            return Cached.Pure(a)
        }

        fun <A : Any> combineFile(list: List<Cached<A>>, outputName: String, produce: (output: File, List<A>) -> Unit): Cached<File> {
            var partiallyApplied = pure(produce)
            for (cached in list) {
                partiallyApplied = productFileList(cached, partiallyApplied)
            }
            return Cached.FApplyCached(
                    name = outputName,
                    produce = partiallyApplied.map { apply -> { outputDirectory: Directory, _: Unit -> apply(File(outputDirectory.asFile, outputName), emptyList()) } },
                    a = Cached.Pure(Unit),
                    getValue = { outputDirectory -> File(outputDirectory.asFile, outputName) }
            )
        }

        private fun withCache(outputDirectory: Directory, produce: () -> Unit) {
            val cacheAckFile = File(outputDirectory.asFile, "cached")
            if (!outputDirectory.asFile.exists() || !cacheAckFile.exists()) {
                outputDirectory.asFile.deleteRecursively()
                outputDirectory.asFile.mkdirs()
                produce()
                cacheAckFile.createNewFile()
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <A : Any> get(cached: Cached<A>, cacheDirectory: File, executor: ExecutorService): CompletableFuture<A> {
            return when (cached) {
                is Cached.Pure -> CompletableFuture.completedFuture(cached.value)
                is Cached.FMap<*, *> -> {
                    get(cached.a, cacheDirectory, executor).thenApply {
                        (cached.f as (Any?) -> A)(it)
                    }
                }
                is Cached.FApply<*, *> -> {
                    val fFuture = get(cached.f, cacheDirectory, executor) as CompletableFuture<(Any?) -> A>
                    val aFuture = get(cached.a, cacheDirectory, executor)
                    fFuture.thenCombineAsync(aFuture, BiFunction { f, a ->
                        f(a)
                    }, executor)
                }
                is Cached.SingleCached -> {
                    val outputDirectory = Directory(File(cacheDirectory, cached.providedKey))
                    executor.fork {
                        withCache(outputDirectory) {
                            cached.produce(outputDirectory)
                        }
                        cached.getValue(outputDirectory)
                    }
                }
                is Cached.FApplyCached<*, *> -> {
                    val key = cached.key
                    val outputDirectory = Directory(File(cacheDirectory, key))
                    val produceFuture = get(cached.produce, cacheDirectory, executor) as CompletableFuture<(Directory, Any?) -> Unit>
                    val aFuture = get(cached.a, cacheDirectory, executor)
                    produceFuture.thenCombineAsync(aFuture, BiFunction { produce, a ->
                        withCache(outputDirectory) {
                            produce(outputDirectory, a)
                        }
                        cached.getValue(outputDirectory) as A
                    }, executor)
                }
            }
        }

    }

    fun mapFile(outputName: String, produce: (output: File, A) -> Unit): Cached<File> {
        return Cached.FApplyCached(
                name = outputName,
                produce = Cached.Pure { outputDirectory, aValue -> produce(File(outputDirectory.asFile, outputName), aValue) },
                a = this,
                getValue = { outputDirectory -> File(outputDirectory.asFile, outputName) }
        )
    }

    fun applyFile(outputName: String, fProduce: Cached<(output: File, A) -> Unit>): Cached<File> {
        return Cached.FApplyCached(
                name = outputName,
                produce = fProduce.map { produce -> { outputDirectory: Directory, aValue: A -> produce(File(outputDirectory.asFile, outputName), aValue) } },
                a = this,
                getValue = { outputDirectory -> File(outputDirectory.asFile, outputName) }
        )
    }

    fun <B : Any> combineFile(b: Cached<B>, outputName: String, produce: (output: File, A, B) -> Unit): Cached<File> {
        return Cached.FApplyCached(
                name = outputName,
                produce = map { aValue ->
                    { outputDirectory: Directory, bValue: B -> produce(File(outputDirectory.asFile, outputName), aValue, bValue)}
                },
                a = b,
                getValue = { outputDirectory -> File(outputDirectory.asFile, outputName) }
        )
    }

    fun <B : Any, C : Any> combineFile(b: Cached<B>, c: Cached<C>, outputName: String, produce: (output: File, A, B, C) -> Unit): Cached<File> {
        return Cached.FApplyCached(
                name = outputName,
                produce = product(b.map { bValue ->
                    { a: A ->
                        { outputDirectory: Directory, c: C -> produce(File(outputDirectory.asFile, outputName), a, bValue, c)}
                    }
                }),
                a = c,
                getValue = { outputDirectory -> File(outputDirectory.asFile, outputName) }
        )
    }

    fun <R> productList(fl: Cached<(List<*>) -> R>): Cached<(List<*>) -> R> {
        return product(fl.map { listFun -> { a: A -> { rest: List<*> ->
            listFun(listOf(a) + rest)
        }}})
    }

    fun <R : Any> map(f: (A) -> R): Cached<R> {
        return Cached.FMap(f, this)
    }
}
