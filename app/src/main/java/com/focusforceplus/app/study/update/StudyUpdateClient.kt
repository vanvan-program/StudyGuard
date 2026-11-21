package com.focusforceplus.app.study.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal enum class UpdateSource(val label: String, val prefix: String) {
    AUTO("自动换源", ""),
    OFFICIAL("GitHub 官方", ""),
    GH_PROXY("国内加速 · gh-proxy.com", "https://gh-proxy.com/"),
    GH_FAST("国内加速 · ghfast.top", "https://ghfast.top/");

    fun routes(): List<UpdateSource> = if (this == AUTO) listOf(OFFICIAL, GH_PROXY, GH_FAST) else listOf(this)
}

internal data class StudyRelease(val version: String, val bytes: Long, val sha256: String) {
    val filename get() = "StudyGuard-$version-release.apk"
    val url get() = "$RELEASES/download/v$version/$filename"
}

private const val RELEASES = "https://github.com/vanvan-program/StudyGuard/releases"
private const val MAX_APK = 128L * 1024 * 1024
private val versionPattern = Regex("""(0|[1-9]\d{0,5})\.(0|[1-9]\d{0,5})\.(0|[1-9]\d{0,5})(-dev)?""")
private val hashPattern = Regex("[0-9a-f]{64}")

internal fun isNewer(candidate: String, installed: String): Boolean {
    fun parts(v: String): List<Int> {
        val match = requireNotNull(versionPattern.matchEntire(v)) { "版本号无效" }
        return (1..3).map { match.groupValues[it].toInt() } + if (match.groupValues[4].isEmpty()) 1 else 0
    }
    val remote = parts(candidate)
    val local = parts(installed)
    return remote.indices.firstOrNull { remote[it] != local[it] }?.let { remote[it] > local[it] } ?: false
}

internal fun parseRelease(text: String): StudyRelease {
    val doc = Json.parseToJsonElement(text).jsonObject
    val version = doc.getValue("version").jsonPrimitive.content
    require(versionPattern.matches(version)) { "版本清单无效" }
    val name = "StudyGuard-$version-release.apk"
    val asset = doc.getValue("artifacts").jsonArray.map { it.jsonObject }.single { it["name"]?.jsonPrimitive?.content == name }
    val size = asset.getValue("bytes").jsonPrimitive.content.toLong()
    val hash = asset.getValue("sha256").jsonPrimitive.content.lowercase()
    require(size in 1..MAX_APK && hashPattern.matches(hash)) { "安装包大小或摘要无效" }
    return StudyRelease(version, size, hash)
}

internal suspend fun <T> tryUpdateSources(mode: UpdateSource, block: suspend (UpdateSource, Boolean) -> T): T {
    val routes = mode.routes()
    val failures = mutableListOf<String>()
    for ((i, route) in routes.withIndex()) {
        currentCoroutineContext().ensureActive()
        try {
            return block(route, i < routes.lastIndex)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += "${route.label}：${e.localizedMessage ?: "连接失败"}"
        }
    }
    throw IOException(failures.joinToString("\n"))
}

internal object StudyUpdateClient {
    private fun open(address: String): HttpURLConnection {
        var url = URL(address)
        repeat(7) {
            val trusted = url.host in setOf("github.com", "gh-proxy.com", "ghfast.top") ||
                url.host.endsWith(".githubusercontent.com")
            require(trusted && url.protocol == "https" && url.userInfo == null && url.port in setOf(-1, 443)) { "下载地址无效" }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "StudyGuard-Updater")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                val code = connection.responseCode
                if (code == 200) return connection
                if (code !in listOf(301,302,303,307,308)) throw IOException("HTTP $code")
                url = URL(url, connection.getHeaderField("Location") ?: throw IOException("跳转地址缺失"))
            } catch (failure: Exception) {
                connection.disconnect()
                throw failure
            }
            connection.disconnect()
        }
        throw IOException("跳转次数过多")
    }

    private suspend fun read(
        address: String, limit: Long, switchIfSlow: Boolean,
        consume: (ByteArray, Int, Long) -> Unit,
    ): Long {
        val connection = open(address)
        try {
            return connection.inputStream.use { input ->
                receive(input, limit, switchIfSlow, consume)
            }
        } finally { connection.disconnect() }
    }

    internal suspend fun receive(input: InputStream, limit: Long, switchIfSlow: Boolean,
        consume: (ByteArray, Int, Long) -> Unit, nanoTime: () -> Long = System::nanoTime): Long {
        val buffer = ByteArray(32 * 1024)
        var bytes = 0L
        var windowBytes = 0L
        val start = nanoTime()
        var window = start
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) return bytes
            bytes += n
            require(bytes <= limit) { "文件大小超出发布清单" }
            consume(buffer, n, bytes)
            val now = nanoTime()
            if (now - start > 240_000_000_000L) throw IOException("下载超时")
            if (switchIfSlow && now - window >= 8_000_000_000L) {
                if ((bytes - windowBytes) * 1_000_000_000L / (now - window) < 24 * 1024) throw IOException("线路速度过低")
                windowBytes = bytes
                window = now
            }
        }
    }

    suspend fun check(mode: UpdateSource, onRoute: (UpdateSource) -> Unit) = withContext(Dispatchers.IO) {
        tryUpdateSources(mode) { route, hasNext ->
            onRoute(route)
            val body = ByteArrayOutputStream()
            read(route.prefix + "$RELEASES/latest/download/artifacts.json", 128 * 1024L, hasNext) { chunk, n, _ -> body.write(chunk, 0, n) }
            parseRelease(body.toString("UTF-8"))
        }
    }

    suspend fun download(release: StudyRelease, mode: UpdateSource, folder: File,
        progress: (UpdateSource, Long) -> Unit, verify: (File) -> Unit): File = withContext(Dispatchers.IO) {
        folder.mkdirs()
        tryUpdateSources(mode) { route, hasNext ->
            progress(route, 0)
            val part = File.createTempFile("download-", ".apk", folder)
            try {
                val hash = MessageDigest.getInstance("SHA-256")
                val count = part.outputStream().buffered().use { output ->
                    read(route.prefix + release.url, release.bytes, hasNext) { chunk, n, done ->
                        output.write(chunk, 0, n)
                        hash.update(chunk, 0, n)
                        progress(route, done)
                    }
                }
                require(count == release.bytes && hash.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "安装包完整性校验失败" }
                verify(part)
                val ready = File(folder, release.filename)
                java.nio.file.Files.move(part.toPath(), ready.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                ready
            } finally { part.delete() }
        }
    }
}
