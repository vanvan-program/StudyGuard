package com.focusforceplus.app.study.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

internal data class UpdateScreenState(
    val mode: UpdateSource, val message: String = "", val release: StudyRelease? = null,
    val busy: Boolean = false, val percent: Int? = null, val ready: File? = null,
)

@Suppress("DEPRECATION")
internal fun verifyUpdateArchive(context: Context, file: File, release: StudyRelease) {
    val manager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val installed = manager.getPackageInfo(context.packageName, flags)
    val archive = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags)) { "无法读取安装包" }
    fun code(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    fun signers(info: PackageInfo) =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)?.toSet().orEmpty()
    require(archive.packageName == context.packageName && archive.versionName == release.version && code(archive) > code(installed)) {
        "包名或安装版本不匹配"
    }
    val known = signers(installed)
    require(known.isNotEmpty() && signers(archive) == known) { "签名与已安装版本不同" }
}

internal class StudyUpdateModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("app-update", Context.MODE_PRIVATE)
    private val folder = File(app.cacheDir, "study-updates")
    val installedVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    private val mutable = MutableStateFlow(UpdateScreenState(
        UpdateSource.entries.firstOrNull { it.name == prefs.getString("source", null) } ?: UpdateSource.AUTO,
    ))
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init {
        val release = prefs.getString("version", null)?.let { version ->
            StudyRelease(version, prefs.getLong("size", 0), prefs.getString("sha", "").orEmpty())
        }
        if (release != null && runCatching { isNewer(release.version, installedVersion) }.getOrDefault(false)) {
            val file = File(folder, release.filename)
            if (file.isFile && file.length() == release.bytes) mutable.update { it.copy(release = release, ready = file, message = "安装包已就绪") }
        }
    }

    fun select(mode: UpdateSource) {
        if (mutable.value.busy) return
        prefs.edit().putString("source", mode.name).apply()
        mutable.update { it.copy(mode = mode) }
    }

    private fun runOperation(block: suspend (UpdateSource) -> Unit) {
        if (job?.isActive == true) return
        val mode = mutable.value.mode
        mutable.update { it.copy(busy = true, percent = null) }
        job = viewModelScope.launch {
            try { block(mode) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = e.message ?: "操作失败，请重试") } }
            finally { mutable.update { it.copy(busy = false, percent = null) } }
        }
    }

    fun check() = runOperation { mode ->
        val release = StudyUpdateClient.check(mode) { route -> mutable.update { it.copy(message = "正在检查 · ${route.label}") } }
        val newer = isNewer(release.version, installedVersion)
        mutable.update { old ->
            old.copy(release = release.takeIf { newer }, ready = old.ready.takeIf { newer && old.release == release },
                message = if (newer) "可更新至 ${release.version}" else "已是最新版本")
        }
    }

    fun download() {
        val release = mutable.value.release ?: return
        runOperation { mode ->
            val file = StudyUpdateClient.download(release, mode, folder, { route, done ->
                mutable.update { it.copy(message = "正在下载 · ${route.label}", percent = (done * 100 / release.bytes).toInt()) }
            }) { verifyUpdateArchive(getApplication(), it, release) }
            prefs.edit().putString("version", release.version).putLong("size", release.bytes).putString("sha", release.sha256).apply()
            mutable.update { it.copy(ready = file, message = "${release.version} 已下载并校验") }
        }
    }

    fun cancel() {
        job?.cancel()
        mutable.update { it.copy(message = "已取消") }
    }

    fun prepareInstaller(openInstaller: (Intent) -> Unit) {
        val current = mutable.value
        val release = current.release ?: return
        val file = current.ready ?: return
        val context = getApplication<Application>()
        runOperation {
            mutable.update { it.copy(message = "正在校验安装包") }
            try {
                withContext(Dispatchers.IO) {
                    require(file.isFile && file.length() == release.bytes) { "缓存已清理，请重新下载" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var count = input.read(buffer)
                        while (count >= 0) {
                            digest.update(buffer, 0, count)
                            count = input.read(buffer)
                        }
                    }
                    require(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "缓存校验失败，请重新下载" }
                    verifyUpdateArchive(context, file, release)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                prefs.edit().remove("version").remove("size").remove("sha").apply()
                mutable.update { it.copy(ready = null) }
                throw e
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".studyupdates", file)
            mutable.update { it.copy(message = "请在系统安装器中确认更新") }
            openInstaller(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    fun status(text: String) = mutable.update { it.copy(message = text) }
}
