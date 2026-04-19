package com.realmsoffate.game.debug

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private const val PORT = 8735
private const val TAG = "RealmsDebug"

data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val body: String
) {
    fun jsonBody(): JsonObject? = try {
        Json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) { null }
}

data class HttpResponse(
    val status: Int = 200,
    val contentType: String = "application/json",
    val body: ByteArray = ByteArray(0)
) {
    companion object {
        fun json(obj: String, status: Int = 200) = HttpResponse(
            status = status,
            contentType = "application/json; charset=utf-8",
            body = obj.toByteArray(Charsets.UTF_8)
        )
        fun png(bytes: ByteArray) = HttpResponse(contentType = "image/png", body = bytes)
        fun error(status: Int, message: String) = json("""{"error":"$message"}""", status)
        fun ok(detail: String = "ok") = json("""{"ok":true,"detail":"$detail"}""")
    }
}

typealias RouteHandler = suspend (HttpRequest) -> HttpResponse

object DebugServer {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val routes = mutableMapOf<Pair<String, String>, RouteHandler>()

    fun route(method: String, path: String, handler: RouteHandler) {
        routes[method.uppercase() to path] = handler
    }

    fun start() {
        StateEndpoints.register()
        CommandEndpoints.register()
        ScreenshotEndpoints.register()
        ThemeEndpoints.register()
        InjectionEndpoints.register()
        MacroEndpoints.register()
        DescribeEndpoints.register()

        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, """{"event":"serverStarted","port":$PORT}""")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, """{"event":"serverError","error":"${e.message}"}""")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val (path, queryString) = if ("?" in fullPath) {
                fullPath.substringBefore("?") to fullPath.substringAfter("?")
            } else fullPath to ""

            val query = queryString.split("&").filter { it.isNotBlank() }.associate {
                val kv = it.split("=", limit = 2)
                kv[0] to (kv.getOrNull(1) ?: "")
            }

            var contentLength = 0
            var line = reader.readLine()
            while (line != null && line.isNotBlank()) {
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = reader.readLine()
            }

            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n == -1) break
                    read += n
                }
                String(buf, 0, read)
            } else ""

            val request = HttpRequest(method, path, query, body)
            val handler = routes[method to path]
            val response = if (handler != null) {
                try { handler(request) }
                catch (e: Exception) {
                    Log.e(TAG, "Handler error: ${e.message}", e)
                    HttpResponse.error(500, e.message ?: "Internal error")
                }
            } else {
                HttpResponse.error(404, "Not found: $method $path")
            }

            writeResponse(socket.getOutputStream(), response)
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun writeResponse(out: OutputStream, response: HttpResponse) {
        val statusText = when (response.status) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 500 -> "Internal Server Error"
            else -> "Unknown"
        }
        val header = buildString {
            append("HTTP/1.1 ${response.status} $statusText\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(response.body)
        out.flush()
    }
}
