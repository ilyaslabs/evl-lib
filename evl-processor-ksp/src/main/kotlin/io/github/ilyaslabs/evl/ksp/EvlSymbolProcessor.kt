package io.github.ilyaslabs.evl.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import io.github.ilyaslabs.evl.processor.EvlProcessor
import io.github.ilyaslabs.evl.processor.IndexWriter
import io.github.ilyaslabs.evl.processor.LocalChecks
import io.github.ilyaslabs.evl.processor.Record
import java.io.File

private const val EVL = "io.github.ilyaslabs.evl.EVL"

/** Registers [EvlSymbolProcessor] with KSP. */
class EvlSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        EvlSymbolProcessor(environment.codeGenerator, environment.logger, environment.options)
}

/**
 * KSP counterpart of the javac [EvlProcessor] (EVL-PROC-005): produces the same `index.json`
 * records for Kotlin declarations. Options: `evl.manifest`, `evl.artifact`, `evl.projectDir`,
 * `evl.failOnError`.
 */
class EvlSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {

    private val records = mutableListOf<Record>()
    private val files = linkedSetOf<KSFile>()
    private val seen = hashSetOf<String>()
    private val projectDir = options["evl.projectDir"]?.let { File(it).absoluteFile.invariantSeparatorsPath.trimEnd('/') + "/" }
    private val failOnError = options["evl.failOnError"] == "true"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        for (file in resolver.getNewFiles()) {
            visitFile(file)
        }
        return emptyList()
    }

    private fun visitFile(file: KSFile) {
        annotated(file, file)
        file.declarations.forEach { visit(it, file) }
    }

    private fun visit(d: KSDeclaration, file: KSFile) {
        annotated(d, file)
        when (d) {
            is KSClassDeclaration -> {
                d.declarations.forEach { visit(it, file) }
                d.primaryConstructor?.parameters?.forEach { annotated(it, file) }
            }
            is KSFunctionDeclaration -> d.parameters.forEach { annotated(it, file) }
            else -> {}
        }
    }

    private fun annotated(node: KSAnnotated, file: KSFile) {
        for (a in node.annotations) {
            val qn = a.annotationType.resolve().declaration.qualifiedName?.asString() ?: continue
            if (!qn.startsWith("$EVL.")) continue
            val name = qn.removePrefix("$EVL.")
            if (name.endsWith(".List")) {
                @Suppress("UNCHECKED_CAST")
                (a.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? List<KSAnnotation>)
                    ?.forEach { add(node, file, it, it.annotationType.resolve().declaration.qualifiedName!!.asString().removePrefix("$EVL."), a) }
            } else {
                add(node, file, a, name, a)
            }
        }
    }

    private fun add(node: KSAnnotated, file: KSFile, a: KSAnnotation, name: String, positioned: KSAnnotation) {
        val attrs = linkedMapOf<String, Any>()
        for (arg in a.arguments) {
            val v = value(arg.value) ?: continue
            attrs[arg.name?.asString() ?: "value"] = v
        }
        val loc = (positioned.location as? FileLocation) ?: (node.location as? FileLocation)
        val pkg = file.packageName.asString()
        val path = loc?.filePath?.let { relative(it) } ?: (pkg.replace('.', '/') + "/" + file.fileName)
        val r = Record(name, elementRef(node, file), kindOf(node), pkg, attrs, path, loc?.lineNumber ?: 0, 0)
        if (!seen.add("${r.annotation}|${r.element}|${r.line}|$attrs")) return
        records += r
        files += file
        for (p in LocalChecks.check(r)) {
            val msg = "${p.code} ${p.message}"
            if (failOnError) logger.error(msg, node as? KSNode) else logger.warn(msg, node as? KSNode)
        }
    }

    private fun value(v: Any?): Any? = when (v) {
        null -> null
        is String -> v.ifEmpty { null }
        is Boolean -> if (v) true else null
        is KSType -> {
            val decl = v.declaration
            if (decl is KSClassDeclaration && decl.classKind == ClassKind.ENUM_ENTRY) {
                decl.simpleName.asString().takeIf { it != "UNSPECIFIED" }
            } else {
                decl.qualifiedName?.asString()?.takeIf { it != "kotlin.Unit" && it != "java.lang.Void" && it != "void" }
            }
        }
        is KSClassDeclaration -> if (v.classKind == ClassKind.ENUM_ENTRY) v.simpleName.asString().takeIf { it != "UNSPECIFIED" } else v.qualifiedName?.asString()
        is List<*> -> v.mapNotNull { value(it) }.ifEmpty { null }
        is Number, is Char -> v.toString()
        else -> v.toString().substringAfterLast('.').takeIf { it.isNotEmpty() && it != "UNSPECIFIED" }
    }

    private fun kindOf(node: KSAnnotated): String = when (node) {
        is KSFile -> "PACKAGE"
        is KSClassDeclaration -> "TYPE"
        is KSFunctionDeclaration -> if (node.simpleName.asString() == "<init>") "CONSTRUCTOR" else "METHOD"
        is KSValueParameter -> "PARAMETER"
        else -> "FIELD"
    }

    /** Same syntax as the javac processor; top-level declarations use the JVM facade class (EVL-LNG-032). */
    private fun elementRef(node: KSAnnotated, file: KSFile): String = when (node) {
        is KSFile -> "package:" + node.packageName.asString()
        is KSClassDeclaration -> node.qualifiedName?.asString() ?: node.simpleName.asString()
        is KSFunctionDeclaration -> owner(node, file) + "#" + (if (node.simpleName.asString() == "<init>") "<init>" else node.simpleName.asString()) +
            node.parameters.joinToString(",", "(", ")") { p ->
                val t = p.type.resolve().declaration.simpleName.asString()
                if (p.isVararg) "$t..." else t
            }
        is KSPropertyDeclaration -> owner(node, file) + "#" + node.simpleName.asString()
        is KSValueParameter -> (node.parent as? KSFunctionDeclaration)?.let { elementRef(it, file) } ?: file.fileName
        else -> file.packageName.asString()
    }

    private fun owner(d: KSDeclaration, file: KSFile): String {
        val parent = d.parentDeclaration
        if (parent is KSClassDeclaration) return parent.qualifiedName?.asString() ?: parent.simpleName.asString()
        val facade = file.fileName.removeSuffix(".kt").replaceFirstChar { it.uppercase() } + "Kt"
        val pkg = file.packageName.asString()
        return if (pkg.isEmpty()) facade else "$pkg.$facade"
    }

    private fun relative(path: String): String {
        val p = path.replace('\\', '/')
        if (projectDir != null && p.startsWith(projectDir)) return p.removePrefix(projectDir)
        for (root in listOf("/src/main/kotlin/", "/src/main/java/", "/src/")) {
            val i = p.lastIndexOf(root)
            if (i >= 0) return p.substring(i + 1)
        }
        return p.substringAfterLast('/')
    }

    override fun finish() {
        if (records.isNotEmpty()) {
            val deps = Dependencies(true, *files.toTypedArray())
            codeGenerator.createNewFileByPath(deps, "META-INF/evl/index", "json").use {
                it.write(IndexWriter.index(records, "evl-processor-ksp").toByteArray(Charsets.UTF_8))
            }
        }
        val manifest = options["evl.manifest"]?.let(::File)
        if (manifest != null && manifest.isFile) {
            val fields = readManifest(manifest)
            if (fields["id"] != null) {
                codeGenerator.createNewFileByPath(Dependencies(true), "META-INF/evl/manifest", "json").use {
                    it.write(IndexWriter.manifest(fields, options["evl.artifact"]).toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    private fun readManifest(file: File): Map<String, String> {
        val out = linkedMapOf<String, String>()
        file.readLines().forEach { line ->
            if (line.isEmpty() || line[0].isWhitespace() || line.startsWith("#")) return@forEach
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val key = line.substring(0, colon).trim()
            var value = line.substring(colon + 1).substringBefore(" #").trim()
            if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\'')))) {
                value = value.substring(1, value.length - 1)
            }
            if (value.isNotEmpty() && key in setOf("evl", "id", "name", "description", "kind")) out[key] = value
        }
        return out
    }
}
