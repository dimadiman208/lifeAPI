package com.lifeapi.app.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifeapi.app.LifeApp
import com.lifeapi.app.data.Profile
import com.lifeapi.app.data.Undo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class BaseVM(app: Application) : AndroidViewModel(app) {
    protected val c = (app as LifeApp).container
    protected val repo = c.repo
    protected val dao = repo.dao
    protected val bodyDao = repo.body
    protected val trainDao = repo.train
    protected val moneyDao = repo.money
    protected val growDao = repo.grow
    protected val today = c.today
    protected val profile: Flow<Profile> = dao.profile().map { it ?: Profile() }

    /** Run a write; if it returns an undo and a toast is given, show the undo snackbar. */
    fun act(toast: String? = null, block: suspend () -> Undo?) {
        viewModelScope.launch {
            val undo = block()
            if (toast != null) c.snack(toast, undo)
        }
    }

    fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    protected fun <T> Flow<T>.state(): StateFlow<T?> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
