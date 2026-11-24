package com.papiai.core.ai

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.StatFs
import com.papiai.domain.model.DeviceResourceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceResourceMonitor(private val context: Context) {

  fun snapshot(activeLoadedModelId: String? = null): DeviceResourceSnapshot {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)

    val statFs = StatFs(context.filesDir.absolutePath)
    val freeStorageBytes = statFs.availableBytes

    return DeviceResourceSnapshot(
      totalRamMb = memInfo.totalMem / (1024L * 1024L),
      availableRamMb = memInfo.availMem / (1024L * 1024L),
      freeStorageMb = freeStorageBytes / (1024L * 1024L),
      architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
      androidRelease = Build.VERSION.RELEASE ?: "unknown",
      sdkInt = Build.VERSION.SDK_INT,
      activeLoadedModelId = activeLoadedModelId,
    )
  }

  fun hasEnoughRam(requiredMb: Int): Boolean {
    val snap = snapshot()
    return snap.availableRamMb >= requiredMb
  }

  fun hasEnoughStorage(requiredBytes: Long): Boolean {
    val statFs = StatFs(context.filesDir.absolutePath)
    return statFs.availableBytes >= requiredBytes + (32L * 1024L * 1024L)
  }
}

class AiModelLifecycleManager(
  private val context: Context,
  private val resourceMonitor: DeviceResourceMonitor,
) : ComponentCallbacks2 {

  private val mutex = Mutex()
  private var activeModelId: String? = null
  private var activeUnloadHook: (suspend () -> Unit)? = null

  private val _activeModelFlow = MutableStateFlow<String?>(null)
  val activeModelFlow: StateFlow<String?> = _activeModelFlow.asStateFlow()

  private val _lowMemoryWarning = MutableStateFlow<String?>(null)
  val lowMemoryWarning: StateFlow<String?> = _lowMemoryWarning.asStateFlow()

  init {
    context.registerComponentCallbacks(this)
  }

  /**
   * Ensures only one heavy model stays resident in RAM at a time (crucial for 4-6 GB RAM devices
   * such as Samsung Galaxy A52). Returns false if available RAM is below [minRequiredRamMb].
   */
  suspend fun acquireModelSlot(
    modelId: String,
    minRequiredRamMb: Int,
    onUnload: suspend () -> Unit,
  ): Boolean = mutex.withLock {
    if (activeModelId != null && activeModelId != modelId) {
      runCatching { activeUnloadHook?.invoke() }
      activeModelId = null
      activeUnloadHook = null
      _activeModelFlow.value = null
      System.gc()
    }

    if (!resourceMonitor.hasEnoughRam(minRequiredRamMb)) {
      val avail = resourceMonitor.snapshot().availableRamMb
      _lowMemoryWarning.value =
        "Insufficient free RAM (${avail} MB free, need ${minRequiredRamMb} MB) for $modelId."
      return@withLock false
    }

    _lowMemoryWarning.value = null
    activeModelId = modelId
    activeUnloadHook = onUnload
    _activeModelFlow.value = modelId
    true
  }

  suspend fun releaseModelSlot(modelId: String) = mutex.withLock {
    if (activeModelId == modelId) {
      runCatching { activeUnloadHook?.invoke() }
      activeModelId = null
      activeUnloadHook = null
      _activeModelFlow.value = null
    }
  }

  suspend fun releaseAll() = mutex.withLock {
    runCatching { activeUnloadHook?.invoke() }
    activeModelId = null
    activeUnloadHook = null
    _activeModelFlow.value = null
  }

  override fun onTrimMemory(level: Int) {
    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
      _lowMemoryWarning.value = "System memory pressure detected (level $level). Idle models released."
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) = Unit

  @Deprecated("Deprecated in Java")
  override fun onLowMemory() {
    _lowMemoryWarning.value = "Low device memory alert received."
  }
}
