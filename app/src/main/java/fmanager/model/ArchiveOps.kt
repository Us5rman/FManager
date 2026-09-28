package fmanager.model

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.utils.CountingInputStream
import org.tukaani.xz.LZMA2Options
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

object ArchiveOps {
    enum class Format(val title: String) {
        ZIP("ZIP"), SEVEN_Z("7z"), TAR_GZ("TAR.GZ")
    }

    enum class Level(val title: String, val n: Int, val hint: String) {
        STORE("Store", 0, "no compression, fastest"),
        FAST("Fast", 1, "quick, larger file"),
        NORMAL("Normal", 5, "balanced"),
        MAXIMUM("Maximum", 7, "slower, smaller"),
        ULTRA("Ultra", 9, "slowest, smallest")
    }

    data class RepairResult(val recovered: Int, val complete: Boolean)

    private enum class Kind { ZIP, SEVEN_Z, RAR, TAR, SINGLE }

    private class Entry(val file: File, val name: String)

    private fun kindOf(name: String): Kind? {
        val n = name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar.bz2") ||
                n.endsWith(".tbz2") || n.endsWith(".tbz") || n.endsWith(".tar.xz") ||
                n.endsWith(".txz") || n.endsWith(".tar") -> Kind.TAR
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") -> Kind.ZIP
            n.endsWith(".7z") -> Kind.SEVEN_Z
            n.endsWith(".rar") -> Kind.RAR
            n.endsWith(".gz") || n.endsWith(".bz2") || n.endsWith(".xz") -> Kind.SINGLE
            else -> null
        }
    }

    fun canExtract(name: String) = kindOf(name) != null
    fun canRepair(name: String): Boolean {
        val k = kindOf(name)
        return k == Kind.ZIP || k == Kind.TAR
    }

    fun baseName(name: String): String {
        val n = name.lowercase()
        val suffixes = listOf(
            ".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".tbz", ".txz", ".tar",
            ".zip", ".jar", ".apk", ".7z", ".rar", ".gz", ".bz2", ".xz"
        )
        val s = suffixes.firstOrNull { n.endsWith(it) } ?: return name.substringBeforeLast('.', name)
        return name.dropLast(s.length).ifEmpty { name }
    }

    fun extensionFor(format: Format, level: Level): String = when (format) {
        Format.ZIP -> "zip"
        Format.SEVEN_Z -> "7z"
        Format.TAR_GZ -> if (level == Level.STORE) "tar" else "tar.gz"
    }

    // LZMA2 presets are capped so phones don't run out of memory
    private fun sevenZPreset(level: Level) = when (level) {
        Level.FAST -> 1
        Level.NORMAL -> 4
        Level.MAXIMUM -> 5
        else -> 6
    }

    private fun pump(
        input: InputStream,
        output: OutputStream,
        buf: ByteArray,
        onBytes: (Int) -> Unit,
        check: () -> Unit
    ) {
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            onBytes(n)
            check()
        }
    }

    private fun collect(sources: List<File>): Pair<List<Entry>, Long> {
        val list = ArrayList<Entry>()
        var total = 0L
        for (src in sources) {
            val base = src.parentFile ?: src
            src.walkTopDown().forEach { f ->
                val rel = f.relativeTo(base).path.replace(File.separatorChar, '/')
                list += Entry(f, if (f.isDirectory) "$rel/" else rel)
                if (f.isFile) total += f.length()
            }
        }
        return list to total
    }

    // ---------- Compress ----------

    fun compress(
        sources: List<File>,
        out: File,
        format: Format,
        level: Level,
        report: (Long, Long) -> Unit,
        check: () -> Unit
    ) {
        val (entries, total) = collect(sources)
        var done = 0L
        val buf = ByteArray(64 * 1024)
        val bump: (Int) -> Unit = { n -> done += n; report(done, total) }

        when (format) {
            Format.ZIP -> ZipOutputStream(BufferedOutputStream(FileOutputStream(out), 64 * 1024)).use { zos ->
                zos.setLevel(level.n)
                for (e in entries) {
                    check()
                    val ze = ZipEntry(e.name)
                    ze.time = e.file.lastModified()
                    zos.putNextEntry(ze)
                    if (e.file.isFile) FileInputStream(e.file).use { pump(it, zos, buf, bump, check) }
                    zos.closeEntry()
                }
            }

            Format.SEVEN_Z -> SevenZOutputFile(out).use { sz ->
                val method = if (level == Level.STORE) {
                    SevenZMethodConfiguration(SevenZMethod.COPY)
                } else {
                    SevenZMethodConfiguration(SevenZMethod.LZMA2, LZMA2Options(sevenZPreset(level)))
                }
                sz.setContentMethods(listOf(method))
                val sink = object : OutputStream() {
                    override fun write(b: Int) = sz.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = sz.write(b, off, len)
                }
                for (e in entries) {
                    check()
                    val entry = sz.createArchiveEntry(e.file, e.name.trimEnd('/'))
                    sz.putArchiveEntry(entry)
                    if (e.file.isFile) FileInputStream(e.file).use { pump(it, sink, buf, bump, check) }
                    sz.closeArchiveEntry()
                }
                sz.finish()
            }

            Format.TAR_GZ -> {
                val fos = BufferedOutputStream(FileOutputStream(out), 64 * 1024)
                val sink: OutputStream = if (level == Level.STORE) {
                    fos
                } else {
                    GzipCompressorOutputStream(
                        fos,
                        GzipParameters().apply { compressionLevel = level.n.coerceAtLeast(1) }
                    )
                }
                TarArchiveOutputStream(sink).use { tos ->
                    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    for (e in entries) {
                        check()
                        tos.putArchiveEntry(TarArchiveEntry(e.file, e.name))
                        if (e.file.isFile) FileInputStream(e.file).use { pump(it, tos, buf, bump, check) }
                        tos.closeArchiveEntry()
                    }
                    tos.finish()
                }
            }
        }
    }

    // ---------- Extract ----------

    private fun safeTarget(dest: File, name: String): File {
        val t = File(dest, name)
        val root = dest.canonicalPath
        val c = t.canonicalPath
        if (c != root && !c.startsWith(root + File.separator)) {
            throw IOException("Blocked unsafe path: $name")
        }
        return t
    }

    private fun decompress(raw: InputStream, name: String): InputStream {
        val n = name.lowercase()
        return when {
            n.endsWith(".gz") || n.endsWith(".tgz") -> GzipCompressorInputStream(raw)
            n.endsWith(".bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> BZip2CompressorInputStream(raw)
            n.endsWith(".xz") || n.endsWith(".txz") -> XZCompressorInputStream(raw)
            else -> raw
        }
    }

    // Returns the number of files extracted
    fun extract(
        archive: File,
        dest: File,
        report: (Long, Long) -> Unit,
        check: () -> Unit
    ): Int {
        dest.mkdirs()
        val buf = ByteArray(64 * 1024)
        return when (kindOf(archive.name)) {
            Kind.ZIP -> ZipFile(archive).use { zf ->
                val entries = zf.entries().toList()
                val total = entries.sumOf { it.size.coerceAtLeast(0L) }
                var done = 0L
                var count = 0
                for (e in entries) {
                    check()
                    val t = safeTarget(dest, e.name)
                    if (e.isDirectory) {
                        t.mkdirs()
                        continue
                    }
                    t.parentFile?.mkdirs()
                    zf.getInputStream(e).use { input ->
                        FileOutputStream(t).use { output ->
                            pump(input, output, buf, { n -> done += n; report(done, total) }, check)
                        }
                    }
                    if (e.time > 0) t.setLastModified(e.time)
                    count++
                }
                count
            }

            Kind.SEVEN_Z -> SevenZFile(archive).use { sz ->
                val total = sz.entries.sumOf { it.size }
                var done = 0L
                var count = 0
                val source = object : InputStream() {
                    override fun read(): Int = sz.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int = sz.read(b, off, len)
                }
                var entry = sz.nextEntry
                while (entry != null) {
                    check()
                    val t = safeTarget(dest, entry.name)
                    if (entry.isDirectory) {
                        t.mkdirs()
                    } else {
                        t.parentFile?.mkdirs()
                        FileOutputStream(t).use { output ->
                            pump(source, output, buf, { n -> done += n; report(done, total) }, check)
                        }
                        count++
                    }
                    entry = sz.nextEntry
                }
                count
            }

            Kind.RAR -> Archive(archive).use { rar ->
                val headers = rar.fileHeaders
                val total = headers.sumOf { it.fullUnpackSize }
                var done = 0L
                var count = 0
                for (h in headers) {
                    check()
                    val t = safeTarget(dest, h.fileName.replace('\\', '/'))
                    if (h.isDirectory) {
                        t.mkdirs()
                        continue
                    }
                    t.parentFile?.mkdirs()
                    FileOutputStream(t).use { output -> rar.extractFile(h, output) }
                    done += h.fullUnpackSize
                    report(done, total)
                    count++
                }
                count
            }

            Kind.TAR -> {
                val counting = CountingInputStream(BufferedInputStream(FileInputStream(archive), 64 * 1024))
                val total = archive.length()
                var count = 0
                TarArchiveInputStream(decompress(counting, archive.name)).use { tin ->
                    var e: ArchiveEntry? = tin.nextEntry
                    while (e != null) {
                        check()
                        val t = safeTarget(dest, e.name)
                        if (e.isDirectory) {
                            t.mkdirs()
                        } else {
                            t.parentFile?.mkdirs()
                            FileOutputStream(t).use { output ->
                                pump(tin, output, buf, { report(counting.bytesRead, total) }, check)
                            }
                            count++
                        }
                        e = tin.nextEntry
                    }
                }
                count
            }

            Kind.SINGLE -> {
                val counting = CountingInputStream(BufferedInputStream(FileInputStream(archive), 64 * 1024))
                val total = archive.length()
                val t = FileOps.uniqueTarget(dest, baseName(archive.name))
                decompress(counting, archive.name).use { input ->
                    FileOutputStream(t).use { output ->
                        pump(input, output, buf, { report(counting.bytesRead, total) }, check)
                    }
                }
                1
            }

            null -> throw IOException("Unsupported archive type")
        }
    }

    // ---------- Repair ----------
    // Rebuilds a broken ZIP or TAR-family archive by reading it entry by entry,
    // ignoring the damaged index, and keeping everything that can still be read.

    fun repair(
        archive: File,
        out: File,
        report: (Long, Long) -> Unit,
        check: () -> Unit
    ): RepairResult {
        val kind = kindOf(archive.name)
        if (kind != Kind.ZIP && kind != Kind.TAR) {
            throw IOException("Repair supports ZIP and TAR-based archives only")
        }
        val counting = CountingInputStream(BufferedInputStream(FileInputStream(archive), 64 * 1024))
        val total = archive.length()
        val buf = ByteArray(64 * 1024)
        var recovered = 0
        var complete = true

        val stream: InputStream
        val nextEntry: () -> ArchiveEntry?
        if (kind == Kind.ZIP) {
            val z = ZipArchiveInputStream(counting, "UTF-8", true, true)
            stream = z
            nextEntry = { z.nextEntry }
        } else {
            val t = TarArchiveInputStream(decompress(counting, archive.name))
            stream = t
            nextEntry = { t.nextEntry }
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(out), 64 * 1024)).use { zos ->
            while (true) {
                check()
                val e: ArchiveEntry = (try {
                    nextEntry()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    complete = false
                    null
                }) ?: break

                if (e.isDirectory) {
                    val n = if (e.name.endsWith("/")) e.name else e.name + "/"
                    zos.putNextEntry(ZipEntry(n))
                    zos.closeEntry()
                    continue
                }
                zos.putNextEntry(ZipEntry(e.name))
                var ok = true
                while (true) {
                    check()
                    val n = try {
                        stream.read(buf)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        ok = false
                        -1
                    }
                    if (n < 0) break
                    zos.write(buf, 0, n)
                    report(counting.bytesRead, total)
                }
                zos.closeEntry()
                recovered++
                if (!ok) {
                    complete = false
                    break
                }
            }
        }
        if (recovered == 0) {
            out.delete()
            throw IOException("Nothing could be recovered")
        }
        return RepairResult(recovered, complete)
    }
}
