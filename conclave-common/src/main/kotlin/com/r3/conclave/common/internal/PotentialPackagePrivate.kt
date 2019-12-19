package com.r3.conclave.common.internal

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

/**
 * Marker annotation to highlight those areas in the Kotlin codebase that ideally should have package-private visibility
 * (but can't due to a lack of support in the Kotlin language).
 */
@Target(CLASS, CONSTRUCTOR, FUNCTION, PROPERTY, FIELD)
@Retention(SOURCE)
annotation class PotentialPackagePrivate
