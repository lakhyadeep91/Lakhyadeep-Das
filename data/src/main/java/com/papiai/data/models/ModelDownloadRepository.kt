package com.papiai.data.models

import android.content.Context
import android.os.StatFs
import android.app.ActivityManager
import com.papiai.data.local.InstalledModelDao
import com.papiai.data.local.InstalledModelEntity
import com.papiai.domain.model.ModelArtifact
import com.papiai.domain.model.ModelCatalogRegistry
import com.papiai.domain.model.ModelInstallStatus
import com.papiai.domain.model.ModelRuntimeState
import com.papiai.domain.model.SupportedLanguage
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ModelDownloadRepository(
  private val context: Context,
  private val installedModelDao: InstalledModelDao,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val activeJobs = ConcurrentHashMap<String, Job>()

  private data class TransientDownloadState(
    val status: ModelInstallStatus,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
  )

  private val transientStates = MutableStateFlow<Map<String, TransientDownloadState>>(emptyMap())

  val modelsRootDir: File
    get() = File(context.filesDir, "papi_models").apply { mkdirs() }

  val tesseractRootDir: File
    get() = File(modelsRootDir, "tesseract").apply {
      File(this, "tessdata").mkdirs()
    }

  val modelStates: StateFlow<List<ModelRuntimeState>> = combine(
    installedModelDao.observeInstalled(),
    transientStates,
  ) { installedRows, transient ->
    val installedMap = installedRows.associateBy { it.modelId }
    val availRamMb = availableRamMb()
    val freeStorageBytes = freeStorageBytes()

    ModelCatalogRegistry.allModels.map { artifact ->
      val installed = installedMap[artifact.id]
      val tr = transient[artifact.id]
      val partialBytes = computePartialBytes(artifact)
      val hasEnoughRam = availRamMb >= artifact.minFreeRamMb
      val hasEnoughStorage = freeStorageBytes >= artifact.totalBytes + (32L * 1024L * 1024L)

      when {
        tr != null -> ModelRuntimeState(
          artifact = artifact,
          status = tr.status,
          downloadedBytes = tr.downloadedBytes,
          totalBytes = if (tr.totalBytes > 0L) tr.totalBytes else artifact.totalBytes,
          verifiedSha256 = installed?.verifiedSha256,
          errorMessage = tr.errorMessage,
          localDirPath = installed?.localDirPath,
          hasEnoughRam = hasEnoughRam,
          hasEnoughStorage = hasEnoughStorage,
        )
        installed != null && File(installed.localDirPath).exists() -> ModelRuntimeState(
          artifact = artifact,
          status = ModelInstallStatus.INSTALLED,
          downloadedBytes = installed.downloadedBytes,
          totalBytes = installed.totalBytes,
          verifiedSha256 = installed.verifiedSha256,
          localDirPath = installed.localDirPath,
          hasEnoughRam = hasEnoughRam,
          hasEnoughStorage = hasEnoughStorage,
        )
        partialBytes > 0L -> ModelRuntimeState(
          artifact = artifact,
          status = ModelInstallStatus.PAUSED,
          downloadedBytes = partialBytes,
          totalBytes = artifact.totalBytes,
          hasEnoughRam = hasEnoughRam,
          hasEnoughStorage = hasEnoughStorage,
        )
        else -> ModelRuntimeState(
          artifact = artifact,
          status = ModelInstallStatus.NOT_INSTALLED,
          downloadedBytes = 0L,
          totalBytes = artifact.totalBytes,
          hasEnoughRam = hasEnoughRam,
          hasEnoughStorage = hasEnoughStorage,
        )
      }
    }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  fun downloadOrResume(modelId: String) {
    val artifact = ModelCatalogRegistry.findById(modelId) ?: return
    if (activeJobs[modelId]?.isActive == true) return

    val job = scope.launch {
      try {
        if (freeStorageBytes() < artifact.totalBytes + (32L * 1024L * 1024L)) {
          setTransient(
            modelId,
            TransientDownloadState(
              status = ModelInstallStatus.ERROR,
              downloadedBytes = computePartialBytes(artifact),
              totalBytes = artifact.totalBytes,
              errorMessage = "Insufficient free storage. Need at least ${(artifact.totalBytes / (1024 * 1024)) + 32} MB free.",
            ),
          )
          return@launch
        }

        val targetBaseDir = modelBaseDir(artifact).apply { mkdirs() }
        var totalExpected = artifact.files.sumOf { it.expectedBytes }
        var completedFilesBytes = 0L
        val fileDigests = mutableListOf<String>()

        for (fileSpec in artifact.files) {
          val destFile = File(targetBaseDir, fileSpec.relativePath)
          destFile.parentFile?.mkdirs()
          val partFile = File(destFile.parentFile, "${destFile.name}.part")

          if (!destFile.exists()) {
            var existingLength = if (partFile.exists()) partFile.length() else 0L
            val requestBuilder = Request.Builder().url(fileSpec.downloadUrl)
            if (existingLength > 0L) {
              requestBuilder.addHeader("Range", "bytes=$existingLength-")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
              if (!response.isSuccessful && response.code != 206) {
                throw IllegalStateException("HTTP ${response.code} downloading ${fileSpec.relativePath}")
              }
              val isAppend = response.code == 206 && existingLength > 0L
              if (!isAppend) {
                existingLength = 0L
              }
              val body = response.body ?: throw IllegalStateException("Empty response body")
              val contentLen = body.contentLength()
              val fileTotal = if (contentLen > 0L) existingLength + contentLen else fileSpec.expectedBytes
              if (fileTotal > 0L && totalExpected < completedFilesBytes + fileTotal) {
                totalExpected = completedFilesBytes + fileTotal
              }

              var bytesForFile = existingLength
              body.byteStream().use { input ->
                FileOutputStream(partFile, isAppend).use { output ->
                  val buffer = ByteArray(16 * 1024)
                  var lastEmit = 0L
                  while (isActive) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesForFile += read
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 150L) {
                      lastEmit = now
                      setTransient(
                        modelId,
                        TransientDownloadState(
                          status = ModelInstallStatus.DOWNLOADING,
                          downloadedBytes = completedFilesBytes + bytesForFile,
                          totalBytes = totalExpected,
                        ),
                      )
                    }
                  }
                }
              }
              if (!isActive) throw CancellationException("Paused by user")
            }

            if (partFile.exists()) {
              if (destFile.exists()) destFile.delete()
              if (!partFile.renameTo(destFile)) {
                throw IllegalStateException("Failed to finalize ${destFile.name}")
              }
            }
          }

          setTransient(
            modelId,
            TransientDownloadState(
              status = ModelInstallStatus.VERIFYING,
              downloadedBytes = completedFilesBytes + destFile.length(),
              totalBytes = totalExpected,
            ),
          )

          val sha = computeSha256(destFile)
          if (fileSpec.expectedSha256 != null &&
            !sha.equals(fileSpec.expectedSha256, ignoreCase = true)
          ) {
            destFile.delete()
            throw IllegalStateException("SHA-256 verification failed for ${destFile.name}")
          }
          fileDigests += sha
          completedFilesBytes += destFile.length()
        }

        val finalDirPath = if (artifact.isZipArchive) {
          val zipFile = File(targetBaseDir, artifact.files.first().relativePath)
          val extractRoot = File(modelsRootDir, artifact.extractDirName ?: artifact.id)
          extractZipSafely(zipFile, extractRoot)
          zipFile.delete()
          resolveActualModelDir(extractRoot).absolutePath
        } else {
          targetBaseDir.absolutePath
        }

        val combinedSha = if (fileDigests.size == 1) fileDigests.first() else sha256String(fileDigests.joinToString(":"))
        installedModelDao.upsert(
          InstalledModelEntity(
            modelId = artifact.id,
            localDirPath = finalDirPath,
            downloadedBytes = completedFilesBytes,
            totalBytes = completedFilesBytes.coerceAtLeast(artifact.totalBytes),
            verifiedSha256 = combinedSha,
            installedAtMs = System.currentTimeMillis(),
          )
        )
        clearTransient(modelId)
      } catch (_: CancellationException) {
        setTransient(
          modelId,
          TransientDownloadState(
            status = ModelInstallStatus.PAUSED,
            downloadedBytes = computePartialBytes(artifact),
            totalBytes = artifact.totalBytes,
          ),
        )
      } catch (t: Throwable) {
        setTransient(
          modelId,
          TransientDownloadState(
            status = ModelInstallStatus.ERROR,
            downloadedBytes = computePartialBytes(artifact),
            totalBytes = artifact.totalBytes,
            errorMessage = t.message ?: "Download failed",
          ),
        )
      } finally {
        activeJobs.remove(modelId)
      }
    }

    activeJobs[modelId] = job
  }

  fun pause(modelId: String) {
    activeJobs[modelId]?.cancel()
    activeJobs.remove(modelId)
    val artifact = ModelCatalogRegistry.findById(modelId) ?: return
    setTransient(
      modelId,
      TransientDownloadState(
        status = ModelInstallStatus.PAUSED,
        downloadedBytes = computePartialBytes(artifact),
        totalBytes = artifact.totalBytes,
      ),
    )
  }

  suspend fun delete(modelId: String) = withContext(Dispatchers.IO) {
    activeJobs[modelId]?.cancel()
    activeJobs.remove(modelId)
    val artifact = ModelCatalogRegistry.findById(modelId)
    if (artifact != null) {
      if (artifact.id.startsWith("tess-")) {
        artifact.files.forEach { spec ->
          File(tesseractRootDir, spec.relativePath).delete()
          File(tesseractRootDir, "${spec.relativePath}.part").delete()
        }
      } else {
        modelBaseDir(artifact).deleteRecursively()
        artifact.extractDirName?.let { File(modelsRootDir, it).deleteRecursively() }
      }
    }
    installedModelDao.deleteById(modelId)
    clearTransient(modelId)
  }

  suspend fun deleteAllModels() = withContext(Dispatchers.IO) {
    activeJobs.values.forEach { it.cancel() }
    activeJobs.clear()
    modelsRootDir.deleteRecursively()
    modelsRootDir.mkdirs()
    installedModelDao.clearAll()
    transientStates.value = emptyMap()
  }

  suspend fun isTesseractLanguageInstalled(language: SupportedLanguage): Boolean {
    val file = File(tesseractRootDir, "tessdata/${language.tesseractCode}.traineddata")
    return file.exists() && file.length() > 10_000L
  }

  suspend fun getVoskModelDir(language: SupportedLanguage): File? {
    val modelId = when (language) {
      SupportedLanguage.ENGLISH -> "vosk-en"
      SupportedLanguage.HINDI -> "vosk-hi"
      SupportedLanguage.ODIA -> return null
    }
    val row = installedModelDao.getById(modelId) ?: return null
    val dir = File(row.localDirPath)
    return if (dir.exists() && dir.isDirectory) dir else null
  }

  suspend fun getInstalledModelDir(modelId: String): File? {
    val row = installedModelDao.getById(modelId) ?: return null
    val dir = File(row.localDirPath)
    return if (dir.exists()) dir else null
  }

  private fun modelBaseDir(artifact: ModelArtifact): File {
    return if (artifact.id.startsWith("tess-")) {
      tesseractRootDir
    } else {
      File(modelsRootDir, artifact.id)
    }
  }

  private fun computePartialBytes(artifact: ModelArtifact): Long {
    val base = modelBaseDir(artifact)
    return artifact.files.sumOf { spec ->
      val f = File(base, spec.relativePath)
      val p = File(f.parentFile, "${f.name}.part")
      when {
        f.exists() -> f.length()
        p.exists() -> p.length()
        else -> 0L
      }
    }
  }

  private fun extractZipSafely(zipFile: File, targetDir: File) {
    if (targetDir.exists()) targetDir.deleteRecursively()
    targetDir.mkdirs()
    val canonicalTarget = targetDir.canonicalPath
    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
      var entry = zis.nextEntry
      val buffer = ByteArray(16 * 1024)
      while (entry != null) {
        val outFile = File(targetDir, entry.name)
        val canonicalOut = outFile.canonicalPath
        if (!canonicalOut.startsWith(canonicalTarget + File.separator) && canonicalOut != canonicalTarget) {
          throw SecurityException("Blocked unsafe ZIP entry: ${entry.name}")
        }
        if (entry.isDirectory) {
          outFile.mkdirs()
        } else {
          outFile.parentFile?.mkdirs()
          FileOutputStream(outFile).use { fos ->
            while (true) {
              val count = zis.read(buffer)
              if (count == -1) break
              fos.write(buffer, 0, count)
            }
          }
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }
  }

  private fun resolveActualModelDir(extractRoot: File): File {
    val children = extractRoot.listFiles()?.filter { it.isDirectory } ?: return extractRoot
    return if (children.size == 1 && !File(extractRoot, "conf").exists()) {
      children.first()
    } else {
      extractRoot
    }
  }

  private fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { fis ->
      val buf = ByteArray(16 * 1024)
      while (true) {
        val read = fis.read(buf)
        if (read == -1) break
        digest.update(buf, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun sha256String(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun availableRamMb(): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    return memInfo.availMem / (1024L * 1024L)
  }

  private fun freeStorageBytes(): Long {
    val stat = StatFs(context.filesDir.absolutePath)
    return stat.availableBytes
  }

  private fun setTransient(modelId: String, state: TransientDownloadState) {
    transientStates.update { it + (modelId to state) }
  }

  private fun clearTransient(modelId: String) {
    transientStates.update { it - modelId }
  }
}
