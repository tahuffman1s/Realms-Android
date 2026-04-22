package com.realmsoffate.game.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Read/write `.rofsave` zip containers for save slots.
 *
 * Layout (v3):
 *   /manifest.json   — small JSON blob with slot metadata for the title screen
 *   /save.json       — full [SaveData] JSON
 *   /realms.db       — optional Room DB copy (preserves arc summaries bit-for-bit)
 *
 * All entries are stored as zip entries at the root of the archive. Binary
 * entries (Room DB and its WAL / SHM sidecars) are copied byte-for-byte.
 */
object SaveRofZip {
    const val MANIFEST = "manifest.json"
    const val SAVE_JSON = "save.json"
    const val REALMS_DB = "realms.db"
    const val REALMS_DB_WAL = "realms.db-wal"
    const val REALMS_DB_SHM = "realms.db-shm"

    /** Write a zip containing every `name -> file` entry (skipping missing files). */
    fun write(out: File, files: Map<String, File>) {
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            for ((name, src) in files) {
                if (!src.exists() || !src.isFile) continue
                zos.putNextEntry(ZipEntry(name))
                src.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /** Write a zip from in-memory strings (text entries) plus optional file entries. */
    fun writeMixed(out: File, textEntries: Map<String, String>, fileEntries: Map<String, File> = emptyMap()) {
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            for ((name, text) in textEntries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            for ((name, src) in fileEntries) {
                if (!src.exists() || !src.isFile) continue
                zos.putNextEntry(ZipEntry(name))
                src.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /** Extract every entry in [zip] into [destDir]. Creates the dir if needed. */
    fun extract(zip: File, destDir: File) {
        destDir.mkdirs()
        ZipFile(zip).use { zf ->
            zf.entries().asSequence().forEach { entry ->
                val target = File(destDir, entry.name)
                target.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { out -> input.copyTo(out) }
                }
            }
        }
    }

    /** Read a single text entry without extracting other files. Returns null if absent. */
    fun readTextEntry(zip: File, name: String): String? {
        if (!zip.exists()) return null
        return runCatching {
            ZipFile(zip).use { zf ->
                val entry = zf.getEntry(name) ?: return@use null
                zf.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }.getOrNull()
    }

    /** Read a single binary entry into memory. Returns null if absent. */
    fun readBinaryEntry(zip: File, name: String): ByteArray? {
        if (!zip.exists()) return null
        return runCatching {
            ZipFile(zip).use { zf ->
                val entry = zf.getEntry(name) ?: return@use null
                zf.getInputStream(entry).use { input ->
                    val out = ByteArrayOutputStream()
                    input.copyTo(out)
                    out.toByteArray()
                }
            }
        }.getOrNull()
    }

    /** Convenience: read manifest.json. Empty string if missing. */
    fun readManifest(zip: File): String = readTextEntry(zip, MANIFEST) ?: ""
}
