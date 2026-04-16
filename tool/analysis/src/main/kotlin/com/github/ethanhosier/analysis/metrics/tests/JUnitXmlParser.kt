package com.github.ethanhosier.analysis.metrics.tests

import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension

/**
 * Parses Gradle's JUnit XML reports under `build/test-results/test/` and
 * aggregates totals + failure details. Uses JDK's built-in DOM parser — no
 * extra dependency.
 *
 * XML is treated as untrusted input (technically under our control, but
 * belt-and-braces): external entities and DOCTYPE are disabled to prevent
 * XXE regardless of what a build script might inject into the reports dir.
 */
internal object JUnitXmlParser {

    private const val STACK_TRACE_TAIL_BYTES = 2_048

    data class Aggregate(
        val total: Int,
        val passed: Int,
        val failed: Int,
        val skipped: Int,
        val failures: List<TestFailure>,
    ) {
        companion object {
            val EMPTY = Aggregate(0, 0, 0, 0, emptyList())
        }
    }

    fun parse(resultsDir: Path): Aggregate {
        if (!Files.isDirectory(resultsDir)) return Aggregate.EMPTY

        val xmls = Files.walk(resultsDir).use { stream ->
            stream.filter { it.extension == "xml" && Files.isRegularFile(it) }.toList()
        }
        if (xmls.isEmpty()) return Aggregate.EMPTY

        var total = 0
        var failed = 0
        var skipped = 0
        val failures = mutableListOf<TestFailure>()

        for (xml in xmls) {
            val suite = parseSuite(xml)
            total += suite.tests
            failed += suite.failures + suite.errors
            skipped += suite.skipped
            failures += suite.failureList
        }

        val passed = (total - failed - skipped).coerceAtLeast(0)
        return Aggregate(total = total, passed = passed, failed = failed, skipped = skipped, failures = failures)
    }

    private data class SuiteData(
        val tests: Int,
        val failures: Int,
        val errors: Int,
        val skipped: Int,
        val failureList: List<TestFailure>,
    )

    private fun parseSuite(xml: Path): SuiteData {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(xml.toFile())
        val root = doc.documentElement

        val tests = root.intAttr("tests")
        val failuresAttr = root.intAttr("failures")
        val errorsAttr = root.intAttr("errors")
        val skippedAttr = root.intAttr("skipped")

        val failureList = mutableListOf<TestFailure>()
        val testcases = root.getElementsByTagName("testcase")
        for (i in 0 until testcases.length) {
            val tc = testcases.item(i) as Element
            val className = tc.getAttribute("classname")
            val methodName = tc.getAttribute("name")
            failureList += collect(tc, "failure", className, methodName, "failure")
            failureList += collect(tc, "error", className, methodName, "error")
        }

        return SuiteData(
            tests = tests,
            failures = failuresAttr,
            errors = errorsAttr,
            skipped = skippedAttr,
            failureList = failureList,
        )
    }

    private fun collect(
        testcase: Element,
        tag: String,
        className: String,
        methodName: String,
        type: String,
    ): List<TestFailure> {
        val elements = testcase.getElementsByTagName(tag)
        return buildList {
            for (i in 0 until elements.length) {
                val el = elements.item(i) as Element
                add(
                    TestFailure(
                        className = className,
                        methodName = methodName,
                        type = type,
                        message = el.getAttribute("message"),
                        stackTraceTail = tail(el.textContent.orEmpty()),
                    ),
                )
            }
        }
    }

    private fun tail(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= STACK_TRACE_TAIL_BYTES) trimmed
        else trimmed.substring(trimmed.length - STACK_TRACE_TAIL_BYTES)
    }

    private fun Element.intAttr(name: String): Int =
        getAttribute(name).toIntOrNull() ?: 0
}
