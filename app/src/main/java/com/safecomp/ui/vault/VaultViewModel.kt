package com.safecomp.ui.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safecomp.AppContainer
import com.safecomp.data.FileKind
import com.safecomp.security.AuthStatus
import com.safecomp.security.AuthUiState
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

    /** Inside the office, but "who you are" isn't confirmed (or can't be). */
    data class NeedsAuth(val auth: AuthUiState) : VaultUiState
    data class Unlocked(val items: List<VaultItem>) : VaultUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.vaultRepository

    /** Only re-evaluate when locked/unlocked actually changes, not on every location fix. */
    private val lockState = container.securityState.snapshot
        .map { it.isUnlocked to it.reason }
        .distinctUntilChanged()

    private val authSession = container.authSession

    /** For the screen's fingerprint/PIN prompt. */
    val authSessionForUi get() = authSession

    val uiState: StateFlow<VaultUiState> = combine(repository.files, lockState, authSession.state) { files, lock, auth ->
        Triple(files, lock, auth)
    }
        .mapLatest { (files, lock, auth) ->
            val (unlocked, reason) = lock
            // "Where you are" first: outside the office it's the hedgehog, whoever you are.
            if (!unlocked) return@mapLatest VaultUiState.Locked(reason)
            // Then "who you are": the keys load only after a fresh fingerprint/face/PIN.
            if (auth.status != AuthStatus.AUTHENTICATED || !authSession.unlockVault()) {
                return@mapLatest VaultUiState.NeedsAuth(auth.copy(status = if (auth.status == AuthStatus.AUTHENTICATED) AuthStatus.NEEDS_AUTH else auth.status))
            }
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

    /** Keys destroyed (or no screen lock): delete the unreadable files and keys, keep the office. */
    fun resetVault() {
        if (!authSession.mayReset()) return
        container.appScope.launch {
            repository.wipeAll()
            authSession.afterReset()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repository.get(id)?.let { repository.delete(it) } }
        }
    }
}
