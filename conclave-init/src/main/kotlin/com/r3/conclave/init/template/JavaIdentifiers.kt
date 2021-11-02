package com.r3.conclave.init.template

class JavaPackage(val name: String) {
    init {
        // Does not check for reserved words. See [JavaPckageValidationTest.kt] for allowed names.
        // Pattern taken from https://stackoverflow.com/q/39289986/6631502
        val allowed = Regex("(^(?:[a-z_]+(?:\\d*[a-zA-Z_]*)*)(?:\\.[a-z_]+(?:\\d*[a-zA-Z_]*)*)*\$)")
        require(allowed.matches(name)) { "Could not parse '$name' as a valid package name." }
    }

    val dirs by lazy { name.replace(".", "/") }
}

val templateEnclavePackage = JavaPackage("com.r3.conclave.template")

class JavaClass(val name: String) {
    init {
        val first = name.first()
        require(Character.isJavaIdentifierStart(first)) { "Java class name cannot start with '$first'" }

        val rest = name.drop(1)
        val invalid = rest.filter { !it.isJavaIdentifierPart() }.toSet()
        require(invalid.isEmpty()) { "Java class name contains invalid characters $invalid" }
    }
}

val templateEnclaveClass = JavaClass("TemplateEnclave")