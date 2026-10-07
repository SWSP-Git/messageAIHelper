package com.example.qqaihelper

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 多线程模型下载器（单例，可跨 Activity 生命周期存活）。
 *
 * 原理：GitHub 走 Fastly CDN，对**单个 TCP 连接限速**；用 12 个并发 Range
 * 请求分段下载同一文件，可显著提速（国内常见从数百 KB/s 提升到数 MB/s）。
 *
 * 特性：
 * - 可配置并发分段（默认 12，最大 64，见 [MIN_THREADS] / [MAX_THREADS]）
 * - 断点续传（保留 .part 文件与各段已下载偏移）
 * - 暂停 / 恢复 / 取消
 * - 完成后 SHA-256 完整性校验
 * - 下载到 App 外部私有目录，**无需存储权限**
 */
object ModelDownloader {

    enum class State { IDLE, PREPARING, DOWNLOADING, PAUSED, VERIFYING, DONE, ERROR, CANCELLED }

    /** 进度快照（回调到主线程） */
    data class Progress(
        val state: State,
        val downloaded: Long,
        val total: Long,
        val speedBps: Long,
        val message: String? = null,
    )

    /** 监听器：Activity 在 onResume/onPause 注册/注销 */
    interface Listener {
        fun onProgress(p: Progress)
        fun onFinished(file: File?)
    }

    /** 默认并发数 */
    const val DEFAULT_CONCURRENCY = 12
    /** 并发数下限 */
    const val MIN_THREADS = 1
    /** 并发数上限 */
    const val MAX_THREADS = 64
    private const val BUFFER = 256 * 1024
    private const val MAX_RETRY = 3

    @Volatile private var appContext: Context? = null
    @Volatile private var state: State = State.IDLE
    @Volatile private var cancelled = false

    /** 下载代次：每次 start() 递增，旧协程检测到代次变化后立即退出 */
    @Volatile private var generation = 0

    private var entry: ModelEntry? = null
    private var totalBytes = 0L
    private var segments = DEFAULT_CONCURRENCY
    private var segmentDone = LongArray(DEFAULT_CONCURRENCY)
    private var job: Job? = null

    @Volatile private var listener: Listener? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // 高并发 + 移动网络下，60s 无数据易误判超时；放宽到 120s
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // 强制 HTTP/1.1：
        // HTTP/2 会把所有 Range 请求复用**同一条 TCP 连接**（多路复用），
        // 各流共享该连接的带宽，导致「加线程数几乎不提速」。
        // 改用 HTTP/1.1 后，每个并发请求独占一条连接，才能真正并行分段下载。
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .dispatcher(Dispatcher().apply {
            maxRequests = MAX_THREADS + 4
            maxRequestsPerHost = MAX_THREADS + 4
        })
        .build()

    // ==================== 状态查询 ====================

    fun currentState(): State = state

    fun isDownloading(): Boolean =
        state == State.PREPARING || state == State.DOWNLOADING || state == State.VERIFYING

    fun currentEntry(): ModelEntry? = entry

    fun downloadedBytes(): Long = segmentDone.sum()

    fun totalSize(): Long = totalBytes

    fun setListener(l: Listener?) { listener = l }

    // ==================== 路径 ====================

    /** 下载目标文件（App 外部私有目录，无需权限，卸载时自动清理） */
    fun targetFile(context: Context, model: ModelEntry): File {
        val dir = File(context.getExternalFilesDir(null), "model_downloads")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, model.id + ".zip")
    }

    /** 已下载完成（存在正式文件）时返回该文件 */
    fun downloadedFile(context: Context, model: ModelEntry): File? {
        val f = targetFile(context, model)
        return if (f.exists() && f.length() > 0) f else null
    }

    // ==================== 断点进度持久化 ====================

    private fun progressFile(context: Context, model: ModelEntry): File =
        File(targetFile(context, model).absolutePath + ".progress")

    /** 读取已保存的分段进度；线程数或文件不符时返回全 0 */
    private fun loadProgress(context: Context, model: ModelEntry, threads: Int): LongArray {
        val arr = LongArray(threads)
        return try {
            val f = progressFile(context, model)
            if (!f.exists()) return arr
            val parts = f.readText().trim().split(",")
            if (parts.size != threads) return arr
            for (i in 0 until threads) arr[i] = parts[i].toLongOrNull() ?: 0L
            arr
        } catch (t: Throwable) { arr }
    }

    /** 保存分段进度（节流调用） */
    private fun saveProgress(context: Context, model: ModelEntry, arr: LongArray) {
        try {
            val text = arr.joinToString(",")
            progressFile(context, model).writeText(text)
        } catch (t: Throwable) { /* 忽略 */ }
    }

    // ==================== 控制 ====================

    /** 当前配置的并发数 */
    fun threadCount(): Int = segments

    /**
     * 开始下载。
     * @param threads 并发线程数（会被限制在 [MIN_THREADS]..[MAX_THREADS]）
     */
    fun start(context: Context, model: ModelEntry, threads: Int = DEFAULT_CONCURRENCY) {
        if (isDownloading()) return
        appContext = context.applicationContext
        entry = model
        cancelled = false
        totalBytes = model.sizeBytes
        segments = threads.coerceIn(MIN_THREADS, MAX_THREADS)
        // 递增代次：让上一次任务的协程立即失效（cancel 是异步的，不能只靠它）
        val myGen = ++generation
        // 恢复断点：读取上次保存的各段进度（线程数不同则无法对应，重置）
        val arr = loadProgress(context, model, segments)
        segmentDone = arr
        AppLogger.d("下载启动: " + segments + " 线程(代次 " + myGen + ")，已恢复 " + arr.sum() + " 字节")

        startForegroundService()
        job?.cancel()
        job = scope.launch { runDownload(model, myGen, arr) }
        notifyProgress()
    }

    fun pause() {
        if (!isDownloading()) return
        cancelled = true
        job?.cancel()
        state = State.PAUSED
        stopForegroundService()
        notifyProgress()
    }

    fun resume() {
        if (isDownloading()) return
        val model = entry ?: return
        cancelled = false
        state = State.PREPARING
        val myGen = ++generation
        val arr = segmentDone
        startForegroundService()
        job?.cancel()
        job = scope.launch { runDownload(model, myGen, arr) }
        notifyProgress()
    }

    /** 取消并清理已下载的临时文件 */
    fun cancel() {
        cancelled = true
        job?.cancel()
        state = State.CANCELLED
        val ctx = appContext
        val model = entry
        if (ctx != null && model != null) {
            runCatching { File(targetFile(ctx, model).absolutePath + ".part").delete() }
            runCatching { progressFile(ctx, model).delete() }
        }
        segmentDone = LongArray(segments)
        stopForegroundService()
        notifyProgress()
    }

    /** 重置到空闲（下载成功后调用，允许重新下载） */
    fun reset() {
        if (isDownloading()) return
        state = State.IDLE
        segmentDone = LongArray(segments)
        notifyProgress()
    }

    // ==================== 前台服务 ====================

    private fun startForegroundService() {
        val ctx = appContext ?: return
        runCatching {
            val i = android.content.Intent(ctx, DownloadService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
    }

    private fun stopForegroundService() {
        val ctx = appContext ?: return
        runCatching { ctx.stopService(android.content.Intent(ctx, DownloadService::class.java)) }
    }

    // ==================== 主流程 ====================

    private suspend fun runDownload(model: ModelEntry, myGen: Int, arr: LongArray) {
        val ctx = appContext ?: return
        val dest = targetFile(ctx, model)
        val part = File(dest.absolutePath + ".part")

        try {
            if (totalBytes <= 0L) {
                val probed = probeSize(model.url)
                if (probed > 0L) totalBytes = probed
            }

            state = State.PREPARING
            notifyProgress()

            // 预分配文件长度（仅首次）
            if (!part.exists() || part.length() != totalBytes) {
                if (totalBytes > 0L) {
                    RandomAccessFile(part, "rw").use { it.setLength(totalBytes) }
                }
                AppLogger.d("下载: 已预分配文件 " + totalBytes + " 字节")
            }

            state = State.DOWNLOADING
            val ok = downloadAll(model, part, myGen, arr)
            if (cancelled) { state = State.PAUSED; notifyProgress(); return }
            if (!ok) {
                AppLogger.e("下载失败: 部分分段未完成（已完成 " + segmentDone.sum() + "/" + totalBytes + " 字节）")
                fail("下载失败，请检查网络后重试")
                return
            }

            // 校验
            state = State.VERIFYING
            notifyProgress()
            if (!verifySha256(part, model.sha256)) {
                AppLogger.e("校验失败: SHA-256 不匹配，文件已删除")
                part.delete()
                segmentDone = LongArray(segments)
                fail("校验失败：文件可能损坏，已删除，请重试")
                return
            }
            AppLogger.d("下载完成并通过 SHA-256 校验")

            // 落盘：.part -> 正式文件
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            // 清理断点进度文件
            runCatching { progressFile(ctx, model).delete() }
            state = State.DONE
            notifyProgress()
            stopForegroundService()
            postFinished(dest)
        } catch (t: Throwable) {
            if (cancelled) { state = State.PAUSED; notifyProgress() }
            else fail(t.message ?: "未知错误")
        }
    }

    private suspend fun downloadAll(model: ModelEntry, part: File, myGen: Int, arr: LongArray): Boolean {
        if (totalBytes <= 0L) {
            AppLogger.d("下载: 未知文件大小，回退单线程")
            return downloadSingle(model, part, myGen, arr)
        }
        val segSize = totalBytes / segments
        if (segSize <= 0L) {
            AppLogger.d("下载: 分段过小，回退单线程")
            return downloadSingle(model, part, myGen, arr)
        }
        // 先探测服务器是否支持分段（Range）；不支持则回退单线程
        if (!probeRangeSupport(model.url)) {
            AppLogger.d("下载: 服务器不支持分段（未返回 206），回退单线程")
            return downloadSingle(model, part, myGen, arr)
        }
        AppLogger.d("下载开始: " + segments + " 线程，总大小 " + totalBytes + " 字节")

        val startTime = System.currentTimeMillis()
        val baseline = arr.sum()

        // 进度循环（同时定期持久化断点）
        val ctx = appContext
        val progressJob = scope.launch {
            while (isActive) {
                if (myGen != generation) break
                val done = arr.sum()
                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                val speed = (done - baseline) * 1000 / elapsed
                notifyProgress(done, speed)
                if (ctx != null) saveProgress(ctx, model, arr)
                delay(500)
            }
        }

        val results = coroutineScope {
            (0 until segments).map { i ->
                async {
                    val start = i.toLong() * segSize
                    val end = if (i == segments - 1) totalBytes - 1 else (i + 1).toLong() * segSize - 1
                    downloadSegment(model.url, part, i, start, end, myGen, arr)
                }
            }.awaitAll()
        }

        progressJob.cancel()
        return results.all { it }
    }

    private suspend fun downloadSegment(url: String, part: File, index: Int, start: Long, end: Long, myGen: Int, arr: LongArray): Boolean {
        if (index >= arr.size) return true   // 线程数已变更，本段作废
        var attempt = 0
        while (attempt < MAX_RETRY) {
            if (cancelled || myGen != generation) return false   // 旧任务立即退出
            val from = start + arr[index]
            if (from > end) return true
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=" + from + "-" + end)
                    .header("Accept-Encoding", "identity")
                    .header("User-Agent", "messageAIHelper")
                    .build()
                client.newCall(req).execute().use { resp ->
                    // 分段下载必须要求 206（Partial Content）；
                    // 若返回 200，说明服务器忽略了 Range，会把整文件写入本段位置导致数据错乱。
                    if (resp.code != 206) {
                        AppLogger.e("段 " + index + " 期望 206，实到 " + resp.code)
                        attempt++
                        return@use
                    }
                    val body = resp.body ?: run { AppLogger.e("段 " + index + " 响应体为空"); attempt++; return@use }
                    val input = body.byteStream()
                    RandomAccessFile(part, "rw").use { raf ->
                        raf.seek(from)
                        val buf = ByteArray(BUFFER)
                        var pos = from
                        while (pos <= end) {
                            if (cancelled) return false
                            val toRead = minOf(BUFFER.toLong(), end - pos + 1).toInt()
                            val n = input.read(buf, 0, toRead)
                            if (n <= 0) break
                            raf.write(buf, 0, n)
                            pos += n
                            arr[index] += n
                        }
                    }
                }
                if (start + arr[index] > end) return true
                attempt++
            } catch (t: Throwable) {
                attempt++
                AppLogger.e("段 " + index + " 第 " + attempt + " 次失败: " + t.javaClass.simpleName + ": " + t.message)
                if (attempt < MAX_RETRY) delay(1000L * attempt)
            }
        }
        return index < arr.size && start + arr[index] > end
    }

    private suspend fun downloadSingle(model: ModelEntry, part: File, myGen: Int, arr: LongArray): Boolean {
        return try {
            val req = Request.Builder()
                .url(model.url)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "messageAIHelper")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false
                val len = body.contentLength()
                if (len > 0) totalBytes = len
                body.byteStream().use { input ->
                    java.io.FileOutputStream(part, false).use { out ->
                        val buf = ByteArray(BUFFER)
                        var done = 0L
                        while (true) {
                            if (cancelled || myGen != generation) return false
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (arr.isNotEmpty()) arr[0] = done
                            notifyProgress(done, 0L)
                        }
                    }
                }
            }
            true
        } catch (t: Throwable) {
            AppLogger.e("单线程下载失败: " + t.javaClass.simpleName + ": " + t.message)
            false
        }
    }

    /** 探测服务器是否支持分段下载（Range 返回 206） */
    private fun probeRangeSupport(url: String): Boolean {
        return try {
            val req = Request.Builder().url(url)
                .header("Range", "bytes=0-0")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "messageAIHelper")
                .build()
            client.newCall(req).execute().use { resp -> resp.code == 206 }
        } catch (t: Throwable) {
            AppLogger.e("探测分段支持失败: ${t.message}")
            false
        }
    }

    private fun probeSize(url: String): Long {
        return try {
            val req = Request.Builder().url(url).head().header("User-Agent", "messageAIHelper").build()
            client.newCall(req).execute().use { resp ->
                resp.header("Content-Length")?.toLongOrNull() ?: -1L
            }
        } catch (t: Throwable) { -1L }
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        if (expected.isBlank()) return true
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            val hex = md.digest().joinToString("") { String.format("%02x", it) }
            hex.equals(expected.trim(), ignoreCase = true)
        } catch (t: Throwable) {
            false
        }
    }

    // ==================== 回调 ====================

    private fun fail(message: String) {
        state = State.ERROR
        notifyProgress(message = message)
        stopForegroundService()
        postFinished(null)
    }

    private fun notifyProgress(downloaded: Long = segmentDone.sum(), speed: Long = 0L, message: String? = null) {
        val p = Progress(state, downloaded, totalBytes, speed, message)
        updateForegroundNotification(p)
        val l = listener ?: return
        mainHandler.post { l.onProgress(p) }
    }

    /** 同步前台服务通知栏进度 */
    private fun updateForegroundNotification(p: Progress) {
        val ctx = appContext ?: return
        val pct = if (p.total > 0) (p.downloaded * 100 / p.total).toInt() else 0
        val text = when (p.state) {
            State.PREPARING -> ctx.getString(R.string.download_notif_preparing)
            State.DONE -> ctx.getString(R.string.download_notif_done)
            else -> ctx.getString(R.string.download_notif_progress, pct, ModelCatalog.humanSize(p.speedBps) + "/s")
        }
        DownloadService.updateNotification(ctx.getString(R.string.download_notif_title), text, pct)
    }

    private fun postFinished(file: File?) {
        val l = listener ?: return
        mainHandler.post { l.onFinished(file) }
    }
}
