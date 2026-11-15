package com.lifeapi.app.vm.fatsecret

import android.app.Application
import com.lifeapi.app.data.fatsecret.FatSecret
import com.lifeapi.app.data.fatsecret.FsStatus
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

data class FsScreenState(val status: FsStatus, val todayMeals: Int, val todayKcal: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class FatSecretVM(app: Application) : BaseVM(app) {
    init { FatSecret.init(app) }

    val state = today.flatMapLatest { day ->
        combine(FatSecret.status, dao.meals(day)) { s, meals ->
            val fs = meals.filter { it.source == FatSecret.SOURCE }
            FsScreenState(s, fs.size, fs.sumOf { it.kcal })
        }
    }.state()

    fun save(url: String, key: String) = FatSecret.configure(url, key)
    fun connect() = FatSecret.connect { c.snack(it) }
    fun finish(code: String) = FatSecret.finishAuth(code)
    fun sync(days: Int) = FatSecret.syncNow(days)
    fun disconnect(deleteImported: Boolean) {
        FatSecret.disconnect(deleteImported)
        c.snack(if (deleteImported) "FatSecret disconnected · imported meals removed" else "FatSecret disconnected · imported meals kept")
    }
}
