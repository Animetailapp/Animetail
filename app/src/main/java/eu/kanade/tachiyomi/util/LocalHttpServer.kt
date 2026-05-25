package eu.kanade.tachiyomi.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import tachiyomi.core.common.util.system.logcat
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLConnection

class LocalHttpServer(
    port: String,
    private val contentResolver: ContentResolver,
    private val client: OkHttpClient,
) : NanoHTTPD(port.toInt()) {

    @SuppressLint("Recycle")
    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/file" -> serveLocalFile(session)
            "/proxy" -> serveRemoteContent(session)
            "/subtitle" -> serveSubtitle(session)
            else -> newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "text/plain",
                "Unsupported path",
            )
        }
    }

    private fun serveLocalFile(session: IHTTPSession): Response {
        val params = session.parameters
        val uriParam = params["uri"]?.get(0) ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "text/plain",
            "Missing uri parameter",
        )

        val uri = try {
            Uri.parse(uriParam)
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid URI")
        }

        val mimeType = URLConnection.guessContentTypeFromName(uri.toString()) ?: "application/octet-stream"

        val assetFileDescriptor = try {
            contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
        }

        val fileLength = assetFileDescriptor?.length ?: -1L

        val rangeHeader = session.headers["range"]
        if (rangeHeader != null && fileLength > 0) {
            try {
                val range = rangeHeader.replace("bytes=", "").split("-")
                val start = range.getOrNull(0)?.toLongOrNull() ?: 0L
                val end = range.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1)
                val length = end - start + 1

                val inputStream = contentResolver.openInputStream(uri)
                inputStream?.skip(start)

                val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, length)
                response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                response.addHeader("Accept-Ranges", "bytes")
                response.addHeader("Access-Control-Allow-Origin", "*")
                return response
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Error processing Range header" }
            }
        }

        val inputStream = contentResolver.openInputStream(uri)
        return if (inputStream != null) {
            val response = newChunkedResponse(Response.Status.OK, mimeType, inputStream)
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
        }
    }

    private fun serveRemoteContent(session: IHTTPSession): Response {
        val targetUrl = getRequiredParam(session, "url")
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing url parameter")

        val upstream = try {
            buildRequest(targetUrl, session).let { client.newCall(it).execute() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error requesting remote content from local HTTP server" }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to proxy remote content")
        }

        if (!upstream.isSuccessful) {
            upstream.close()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Remote server returned ${upstream.code}")
        }

        val body = upstream.body ?: run {
            upstream.close()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Remote response has no body")
        }

        val mimeType = body.contentType()?.toString()
            ?: URLConnection.guessContentTypeFromName(targetUrl)
            ?: "application/octet-stream"
        val contentLength = body.contentLength()
        val stream = UpstreamResponseInputStream(body.byteStream(), upstream)
        val response = if (upstream.code == 206 && contentLength >= 0) {
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, stream, contentLength)
        } else if (contentLength >= 0) {
            newFixedLengthResponse(Response.Status.OK, mimeType, stream, contentLength)
        } else {
            newChunkedResponse(Response.Status.OK, mimeType, stream)
        }

        upstream.header("Content-Range")?.let { response.addHeader("Content-Range", it) }
        upstream.header("Accept-Ranges")?.let { response.addHeader("Accept-Ranges", it) }
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun serveSubtitle(session: IHTTPSession): Response {
        val sourceName = getRequiredParam(session, "name")
            ?: getRequiredParam(session, "url")
            ?: getRequiredParam(session, "uri")
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing subtitle source")

        val subtitleText = when {
            getRequiredParam(session, "uri") != null -> readLocalText(getRequiredParam(session, "uri")!!)
            getRequiredParam(session, "url") != null -> readRemoteText(getRequiredParam(session, "url")!!, session)
            else -> null
        } ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to load subtitle")

        val converted = SubtitleVttConverter.convertToWebVtt(sourceName, subtitleText)
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Unsupported subtitle format")

        return newFixedLengthResponse(Response.Status.OK, "text/vtt", converted).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    private fun buildRequest(targetUrl: String, session: IHTTPSession): Request {
        val builder = Request.Builder().url(targetUrl)
        parseHeaderParams(session.parameters["header"]).forEach { (name, value) ->
            builder.addHeader(name, value)
        }
        session.headers["range"]?.let { builder.header("Range", it) }
        return builder.build()
    }

    private fun parseHeaderParams(values: List<String>?): List<Pair<String, String>> {
        return values.orEmpty().mapNotNull { header ->
            val separatorIndex = header.indexOf(':')
            if (separatorIndex <= 0) {
                null
            } else {
                header.substring(0, separatorIndex).trim() to header.substring(separatorIndex + 1).trim()
            }
        }
    }

    private fun readLocalText(uriValue: String): String? {
        val uri = runCatching { Uri.parse(uriValue) }.getOrNull() ?: return null
        return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }

    private fun readRemoteText(targetUrl: String, session: IHTTPSession): String? {
        return try {
            client.newCall(buildRequest(targetUrl, session)).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error reading remote subtitle" }
            null
        }
    }

    private fun getRequiredParam(session: IHTTPSession, name: String): String? {
        return session.parameters[name]?.firstOrNull()
    }

    private class UpstreamResponseInputStream(
        inputStream: InputStream,
        private val upstreamResponse: OkHttpResponse,
    ) : FilterInputStream(inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                upstreamResponse.close()
            }
        }
    }
}
