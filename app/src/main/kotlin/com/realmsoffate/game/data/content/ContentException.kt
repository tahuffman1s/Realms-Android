package com.realmsoffate.game.data.content

sealed class ContentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class MissingFile(val path: String, cause: Throwable? = null) :
        ContentException("Content asset missing: $path", cause)

    class MalformedJson(val path: String, cause: Throwable) :
        ContentException("Content asset malformed: $path — ${cause.message}", cause)

    class SchemaVersionMismatch(val expected: Int, val actual: Int) :
        ContentException("Content schema version mismatch: expected $expected, got $actual")
}
