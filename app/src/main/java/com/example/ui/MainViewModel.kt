package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CodeDetectionConfigEntity
import com.example.data.DetectedCodeEntity
import com.example.data.GestureActionEntity
import com.example.data.NotchConfigEntity
import com.example.data.NotchRepository
import com.example.data.SideDeckConfigEntity
import com.example.data.TextAssistantConfigEntity
import com.example.data.TextSnippetEntity
import com.example.data.TriggerStatEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val config: NotchConfigEntity = NotchConfigEntity(),
    val actions: List<GestureActionEntity> = emptyList(),
    val stats: List<TriggerStatEntity> = emptyList(),
    val sideDeckConfig: SideDeckConfigEntity = SideDeckConfigEntity(),
    val codeConfig: CodeDetectionConfigEntity = CodeDetectionConfigEntity(),
    val detectedCodes: List<DetectedCodeEntity> = emptyList(),
    val textAssistantConfig: TextAssistantConfigEntity = TextAssistantConfigEntity(),
    val textSnippets: List<TextSnippetEntity> = emptyList()
)

class MainViewModel(private val repository: NotchRepository) : ViewModel() {

    // Internal tester state
    private val _testGestureMessage = MutableStateFlow<String?>(null)
    val testGestureMessage: StateFlow<String?> = _testGestureMessage.asStateFlow()

    private var configDebounceJob: Job? = null
    private var sideDeckDebounceJob: Job? = null
    private var codeConfigDebounceJob: Job? = null
    private var textAssistantDebounceJob: Job? = null

    private val notchFlow = combine(
        repository.configFlow,
        repository.actionsFlow,
        repository.statsFlow,
        repository.sideDeckConfigFlow
    ) { config, actions, stats, sideDeckConfig ->
        MainUiState(
            config = config,
            actions = actions,
            stats = stats,
            sideDeckConfig = sideDeckConfig
        )
    }

    private val assistantFlow = combine(
        repository.codeConfigFlow,
        repository.detectedCodesFlow,
        repository.textAssistantConfigFlow,
        repository.snippetsFlow
    ) { codeConfig, detectedCodes, textConfig, snippets ->
        MainUiState(
            codeConfig = codeConfig,
            detectedCodes = detectedCodes,
            textAssistantConfig = textConfig,
            textSnippets = snippets
        )
    }

    val uiState: StateFlow<MainUiState> = combine(notchFlow, assistantFlow) { notch, assistant ->
        MainUiState(
            config = notch.config,
            actions = notch.actions,
            stats = notch.stats,
            sideDeckConfig = notch.sideDeckConfig,
            codeConfig = assistant.codeConfig,
            detectedCodes = assistant.detectedCodes,
            textAssistantConfig = assistant.textAssistantConfig,
            textSnippets = assistant.textSnippets
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
        }
    }

    fun updateConfig(config: NotchConfigEntity) {
        configDebounceJob?.cancel()
        viewModelScope.launch {
            repository.updateConfig(config)
        }
    }

    fun updateConfigDebounced(config: NotchConfigEntity, delayMs: Long = 120L) {
        configDebounceJob?.cancel()
        configDebounceJob = viewModelScope.launch {
            delay(delayMs)
            repository.updateConfig(config)
        }
    }

    fun updateSideDeckConfig(config: SideDeckConfigEntity) {
        sideDeckDebounceJob?.cancel()
        viewModelScope.launch {
            repository.updateSideDeckConfig(config)
        }
    }

    fun updateSideDeckConfigDebounced(config: SideDeckConfigEntity, delayMs: Long = 120L) {
        sideDeckDebounceJob?.cancel()
        sideDeckDebounceJob = viewModelScope.launch {
            delay(delayMs)
            repository.updateSideDeckConfig(config)
        }
    }

    fun updateCodeConfig(config: CodeDetectionConfigEntity) {
        codeConfigDebounceJob?.cancel()
        viewModelScope.launch {
            repository.updateCodeConfig(config)
        }
    }

    fun updateCodeConfigDebounced(config: CodeDetectionConfigEntity, delayMs: Long = 120L) {
        codeConfigDebounceJob?.cancel()
        codeConfigDebounceJob = viewModelScope.launch {
            delay(delayMs)
            repository.updateCodeConfig(config)
        }
    }

    fun addDetectedCode(code: DetectedCodeEntity) {
        viewModelScope.launch {
            repository.insertDetectedCode(code)
        }
    }

    fun deleteDetectedCode(id: Long) {
        viewModelScope.launch {
            repository.deleteDetectedCode(id)
        }
    }

    fun clearDetectedCodes() {
        viewModelScope.launch {
            repository.clearDetectedCodes()
        }
    }

    fun updateTextAssistantConfig(config: TextAssistantConfigEntity) {
        textAssistantDebounceJob?.cancel()
        viewModelScope.launch {
            repository.updateTextAssistantConfig(config)
        }
    }

    fun updateTextAssistantConfigDebounced(config: TextAssistantConfigEntity, delayMs: Long = 120L) {
        textAssistantDebounceJob?.cancel()
        textAssistantDebounceJob = viewModelScope.launch {
            delay(delayMs)
            repository.updateTextAssistantConfig(config)
        }
    }

    fun insertSnippet(snippet: TextSnippetEntity) {
        viewModelScope.launch {
            repository.insertSnippet(snippet)
        }
    }

    fun updateSnippet(snippet: TextSnippetEntity) {
        viewModelScope.launch {
            repository.updateSnippet(snippet)
        }
    }

    fun deleteSnippet(id: Long) {
        viewModelScope.launch {
            repository.deleteSnippet(id)
        }
    }

    fun resetSnippetsToDefault() {
        viewModelScope.launch {
            repository.resetSnippetsToDefault()
        }
    }

    fun updateAction(action: GestureActionEntity) {
        viewModelScope.launch {
            repository.updateAction(action)
        }
    }

    fun resetStats() {
        viewModelScope.launch {
            repository.resetStats()
        }
    }

    fun triggerTestGesture(gesture: String) {
        _testGestureMessage.value = "Tester triggered: $gesture!"
        viewModelScope.launch {
            repository.incrementStat(gesture)
        }
    }

    fun clearTestMessage() {
        _testGestureMessage.value = null
    }
}


class MainViewModelFactory(private val repository: NotchRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
