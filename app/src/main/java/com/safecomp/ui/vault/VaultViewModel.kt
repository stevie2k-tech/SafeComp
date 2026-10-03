package com.safecomp.ui.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safecomp.AppContainer
import com.safecomp.data.FileKind
import com.safecomp.security.StateReason
import com.safecomp.vault.ImportProgress
import com.safecomp.vault.VaultLockedException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultItem(
    val id: String,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val importedAt: Long,
)

sealed interface VaultUiState {
    data object Loading : VaultUiState
    data class Locked(val reason: StateReason) : VaultUiState
    data class Unlocked(val items: List<VaultItem>) : VaultUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.vaultRepository

    /** Only re-evaluate when locked/unlocked actually changes, not on every location fix. */
    private val lockState = container.securityState.snapshot
        .map { it.isUnlocked to it.reason }
        .distinctUntilChanged()

    val uiState: StateFlow<VaultUiState> = combine(repository.files, lockState) { files, lock -> files to lock }
        .mapLatest { (files, lock) ->
            val (unlocked, reason) = lock
            if (!unlocked) return@mapLatest VaultUiState.Locked(reason)
            try {
                // Names are decrypted only now, only while INSIDE, and only in memory.
                val names = repository.decryptNames(files)
                VaultUiState.Unlocked(
                    files.map { VaultItem(it.id, names[it.id] ?: "(unreadable)", it.kind, it.sizeBytes, it.importedAt) },
                )
            } catch (e: VaultLockedException) {
                VaultUiState.Locked(reason)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState.Loading)

    val importProgress: StateFlow<ImportProgress?> = repository.importProgress

    /** Files picked but not yet confirmed (waiting for the "delete originals?" choice). */
    private val _pendingImport = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImport: StateFlow<List<Uri>> = _pendingImport.asStateFlow()

    fun onFilesPicked(uris: List<Uri>) {
        _pendingImport.value = uris
    }

    fun confirmImport(deleteOriginals: Boolean) {
        repository.startImport(_pendingImport.value, deleteOriginals)
        _pendingImport.value = emptyList()
    }

    fun cancelImport() {
        _pendingImport.value = emptyList()
    }

    fun dismissImportProgress() = repository.dismissImportProgress()

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repository.get(id)?.let { repository.delete(it) } }
        }
    }
}
