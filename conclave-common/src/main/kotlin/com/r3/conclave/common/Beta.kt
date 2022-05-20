package com.r3.conclave.common

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.*

/**
 * Signifies that a public API (public class, method or field) is subject to breaking changes or removal in a future
 * release. An API bearing this annotation is exempt from any compatibility guarantees made by the Conclave SDK.
 * Note that the presence of this annotation implies nothing about the quality or stability of the indicated API, only
 * the fact that it isn't "API-frozen".
 */
@Target(ANNOTATION_CLASS, CONSTRUCTOR, FIELD, FUNCTION, TYPE, CLASS)
@Retention(BINARY)
@MustBeDocumented
annotation class Beta()
