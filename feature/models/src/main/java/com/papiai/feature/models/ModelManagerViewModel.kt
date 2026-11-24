package com.papiai.feature.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.papiai.core.ai.AiModelLifecycleManager
import com.papiai.core.ai.DeviceResourceMonitor
import com.papiai.data.models.ModelDownloadRepository
import com.papiai.data.settings.SettingsRepository
import com.papiai.domain.model.AppSettings
import com.papiai.domain.model.DeviceResourceSnapshot
import com.papiai.domain.model.ModelCategory
import com.papiai.domain.model.ModelRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelManagerViewModel(
  private val modelRepository: ModelDownloadRepository,
  private val settingsRepository: SettingsRepository,
  private val resourceMonitor: DeviceResourceMonitor,
  private val lifecycleManager: AiModelLifecycleManager,
) : ViewModel() {

  private val _selectedCategory = MutableStateFlow<ModelCategory?>(null)
  val selectedCategory: StateFlow<ModelCategory?> = _selectedCategory.asStateFlow()

  val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    AppSettings(),
  )

  val models: StateFlow<List<ModelRuntimeState>> = combine(
    modelRepository.modelStates,
    _selectedCategory,
  ) { list, cat ->
    if (cat == null) list else list.filter { it.artifact.category == cat }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val deviceResources: StateFlow<DeviceResourceSnapshot> = combine(
    modelRepository.modelStates,
    lifecycleManager.activeModelFlow,
  ) { _, activeId ->
    resourceMonitor.snapshot(activeId)
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    resourceMonitor.snapshot(null),
  )

  fun setCategoryFilter(category: ModelCategory?) {
    _selectedCategory.value = category
  }

  fun downloadOrResume(modelId: String) {
    modelRepository.downloadOrResume(modelId)
  }

  fun pause(modelId: String) {
    modelRepository.pause(modelId)
  }

  fun delete(modelId: String) {
    viewModelScope.launch {
      lifecycleManager.releaseModelSlot(modelId)
      modelRepository.delete(modelId)
    }
  }

  fun releaseLoadedModels() {
    viewModelScope.launch {
      lifecycleManager.releaseAll()
    }
  }
}
