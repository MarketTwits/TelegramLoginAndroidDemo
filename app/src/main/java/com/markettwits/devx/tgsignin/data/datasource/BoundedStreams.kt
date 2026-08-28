package com.markettwits.devx.tgsignin.data.datasource

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal fun InputStream.readUtf8Bounded(maxBytes: Int): String = use { input ->
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw ResponseTooLargeException(maxBytes)
        output.write(buffer, 0, read)
    }
    output.toString(Charsets.UTF_8.name())
}

internal class ResponseTooLargeException(maxBytes: Int) :
    IOException("HTTP response exceeds the $maxBytes byte limit")
