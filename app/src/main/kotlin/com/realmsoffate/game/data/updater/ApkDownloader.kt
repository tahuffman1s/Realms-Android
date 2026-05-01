package com.realmsoffate.game.data.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Test seam — production impl is [ApkDownloader]. */
interface ApkDownloaderLike {
    fun download(downloadUrl: String, filename: String, expectedSize: Long): Flow<ApkDownloader.Progress>
}

/**
 * Streams an APK from [downloadUrl] into [cacheDir]/<filename>, emitting
 * progress as a Flow. Terminal events are [Progress.Done] or [Progress.Failed].
 *
 * Cancels cleanly when the collecting coroutine is cancelled — partial file
 * is deleted on cancellation or failure.
 */
class ApkDownloader(
    private val httpClient: OkHttpClient,
    private val cacheDir: File
) : ApkDownloaderLike {
    sealed class Progress {
        data class Streaming(val bytesRead: Long, val totalBytes: Long, val percent: Int) : Progress()
        data class Done(val file: File) : Progress()
        data class Failed(val reason: String) : Progress()
    }

    override fun download(downloadUrl: String, filename: String, expectedSize: Long): Flow<Progress> = flow {
        cacheDir.mkdirs()
        val outFile = File(cacheDir, filename)
        if (outFile.exists()) outFile.delete()
        val request = Request.Builder().url(downloadUrl).get().build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            emit(Progress.Failed("Network: ${e.message}"))
            return@flow
        }
        try {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    emit(Progress.Failed("HTTP ${resp.code}"))
                    outFile.delete()
                    return@flow
                }
                val body = resp.body ?: run {
                    emit(Progress.Failed("Empty body"))
                    return@flow
                }
                val total = if (expectedSize > 0) expectedSize else (body.contentLength().takeIf { it > 0 } ?: -1L)
                var read = 0L
                outFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var lastEmittedPct = -1
                        while (currentCoroutineContext().isActive) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100L) / total).toInt().coerceIn(0, 100)
                                if (pct != lastEmittedPct) {
                                    emit(Progress.Streaming(read, total, pct))
                                    lastEmittedPct = pct
                                }
                            }
                        }
                    }
                }
                if (expectedSize > 0 && read != expectedSize) {
                    outFile.delete()
                    emit(Progress.Failed("Download corrupt — size $read != expected $expectedSize"))
                    return@flow
                }
                emit(Progress.Done(outFile))
            }
        } catch (e: IOException) {
            outFile.delete()
            emit(Progress.Failed("Network: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
