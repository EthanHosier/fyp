package com.github.ethanhosier.analysis.refactoring

import org.osgi.framework.Bundle
import org.osgi.framework.launch.Framework
import org.osgi.framework.launch.FrameworkFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

object EquinoxBootstrap {

    private val KNOWN_BUNDLE_NAMES = mapOf(
        "commonmark" to "org.commonmark",
        "commonmark-ext-gfm-tables" to "org.commonmark-gfm-tables",
    )

    private val INSTALL_PREFIXES = listOf(
        "org.eclipse.",
        "org.apache.felix.scr",
        "org.osgi.",
        "com.ibm.icu",
        "com.sun.jna",
        "kotlin",
    )

    private val EXCLUDE_PREFIXES = listOf(
        "org.eclipse.jetty.",
        "org.eclipse.jgit",
        "org.eclipse.jface",          // covers org.eclipse.jface and org.eclipse.jface.text
        "org.eclipse.swt",
        "com.ibm.icu",
    )

    private val START_ORDER = listOf(
        "org.apache.felix.scr",
        "org.eclipse.equinox.common",
        "org.eclipse.equinox.registry",
        "org.eclipse.equinox.preferences",
        "org.eclipse.equinox.app",
        "org.eclipse.core.contenttype",
        "org.eclipse.core.runtime",
        "org.eclipse.core.jobs",
        "org.eclipse.core.filesystem",
        "org.eclipse.core.expressions",
        "org.eclipse.text",
        "org.eclipse.core.filebuffers",
        "org.eclipse.core.resources",
        "org.eclipse.ltk.core.refactoring",
        "org.eclipse.jdt.core",
    )

    private fun shouldInstall(symbolicName: String): Boolean {
        if (EXCLUDE_PREFIXES.any { symbolicName.startsWith(it) }) return false
        return INSTALL_PREFIXES.any { symbolicName == it.trimEnd('.') || symbolicName.startsWith(it) }
    }

    fun start(dataArea: Path): Framework {
        val configArea = dataArea.resolve("config")
        val instanceArea = dataArea.resolve("workspace")
        Files.createDirectories(configArea)
        Files.createDirectories(instanceArea)

        val config = mapOf(
            "osgi.install.area" to configArea.toUri().toString(),
            "osgi.configuration.area" to configArea.toUri().toString(),
            "osgi.instance.area" to instanceArea.toUri().toString(),
            "osgi.user.area" to dataArea.resolve("user").toUri().toString(),
            "osgi.clean" to "true",
            "eclipse.ignoreApp" to "true",
            "osgi.noShutdown" to "false",
        )

        val factory: FrameworkFactory = ServiceLoader.load(FrameworkFactory::class.java)
            .firstOrNull()
            ?: error("No OSGi FrameworkFactory on classpath (org.eclipse.osgi missing?)")
        val framework = factory.newFramework(config)
        framework.init()
        framework.start()

        val ctx = framework.bundleContext
        val bundles = mutableListOf<Bundle>()
        val wrapArea = dataArea.resolve("wrapped")
        Files.createDirectories(wrapArea)
        for (path in classpathJars()) {
            val existing = bundleSymbolicName(path)
            if (existing == "org.eclipse.osgi") continue // the framework itself
            if (existing != null && !shouldInstall(existing)) continue
            try {
                val installable = if (existing != null) path else wrapAsBundle(path, wrapArea)
                if (installable == null) continue
                bundles += ctx.installBundle("reference:" + installable.toUri())
            } catch (e: Throwable) {
                System.err.println("EquinoxBootstrap: skip ${path.fileName}: ${e.message}")
            }
        }

        val ordered = bundles
            .filter { it.headers.get("Fragment-Host") == null }
            .sortedBy { b ->
                val idx = START_ORDER.indexOf(b.symbolicName)
                if (idx >= 0) idx else Int.MAX_VALUE
            }
        for (b in ordered) {
            try {
                b.start(0)
            } catch (e: Throwable) {
                val cause = generateSequence<Throwable>(e) { it.cause }.last()
                System.err.println("EquinoxBootstrap: start ${b.symbolicName} failed: ${e.message} | root=${cause::class.simpleName}: ${cause.message}")
            }
        }

        return framework
    }

    private fun classpathJars(): List<Path> {
        val cp = System.getProperty("java.class.path") ?: return emptyList()
        return cp.split(System.getProperty("path.separator")).mapNotNull { s ->
            val p = Path.of(s)
            if (p.toString().endsWith(".jar") && Files.isRegularFile(p)) p else null
        }
    }

    private fun wrapAsBundle(jar: Path, wrapArea: Path): Path? {
        val packages = sortedSetOf<String>()
        JarFile(jar.toFile()).use { jf ->
            for (entry in jf.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name
                if (!name.endsWith(".class")) continue
                if (name.startsWith("META-INF/")) continue
                val slash = name.lastIndexOf('/')
                if (slash < 0) continue
                packages.add(name.substring(0, slash).replace('/', '.'))
            }
        }
        if (packages.isEmpty()) return null

        val fileName = jar.fileName.toString()
        val version = Regex("-(\\d+(?:\\.\\d+)+)\\.jar$").find(fileName)?.groupValues?.get(1)
            ?: "0.0.0"

        val basename = fileName.removeSuffix(".jar").removeSuffix("-$version")
        val symbolicName = KNOWN_BUNDLE_NAMES[basename] ?: run {
            val topCandidates = packages.map { pkg ->
                val parts = pkg.split('.')
                if (parts.size >= 2) "${parts[0]}.${parts[1]}" else parts[0]
            }
            topCandidates.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                ?: return null
        }

        val manifest = Manifest().apply {
            mainAttributes.apply {
                put(Attributes.Name.MANIFEST_VERSION, "1.0")
                put(Attributes.Name("Bundle-ManifestVersion"), "2")
                put(Attributes.Name("Bundle-SymbolicName"), symbolicName)
                put(Attributes.Name("Bundle-Version"), version)
                put(Attributes.Name("Export-Package"), packages.joinToString(","))
            }
        }

        val out = wrapArea.resolve("${symbolicName}_${version}.jar")
        JarFile(jar.toFile()).use { jf ->
            JarOutputStream(Files.newOutputStream(out), manifest).use { jos ->
                for (entry in jf.entries()) {
                    if (entry.name == "META-INF/MANIFEST.MF") continue
                    jos.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) jf.getInputStream(entry).copyTo(jos)
                    jos.closeEntry()
                }
            }
        }
        return out
    }

    private fun bundleSymbolicName(jar: Path): String? {
        return try {
            JarFile(jar.toFile()).use { jf ->
                val attrs = jf.manifest?.mainAttributes ?: return null
                val raw = attrs.getValue("Bundle-SymbolicName") ?: return null
                raw.substringBefore(';').trim()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
