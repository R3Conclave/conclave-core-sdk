package com.r3.conclave.init.template

// If this gets any more complex we should use a templating engine
// such as Apache Freemarker
class TemplateTextTransformer(
    private val basePackage: JavaPackage,
    private val projectEnclaveClass: JavaClass
) {
    fun transform(text: String): String = text
        .replace(templateEnclavePackage.name, basePackage.name)
        .replace(templateEnclaveClass.name, projectEnclaveClass.name)
}