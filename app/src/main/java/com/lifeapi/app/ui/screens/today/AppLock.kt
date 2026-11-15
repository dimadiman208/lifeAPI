package com.lifeapi.app.ui.screens.today

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeapi.app.LifeApp
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/** PIN lives in app-private SharedPreferences as a salted SHA-256 hash — never in Room or backups. */
object LockStore {
    private fun prefs(c: Context) = c.getSharedPreferences("lock", Context.MODE_PRIVATE)
    private fun hash(salt: ByteArray, pin: String) = MessageDigest.getInstance("SHA-256").run { update(salt); digest(pin.toByteArray()) }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun hasPin(c: Context) = prefs(c).contains("hash")
    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit().putString("salt", hex(salt)).putString("hash", hex(hash(salt, pin))).apply()
    }
    fun clearPin(c: Context) = prefs(c).edit().remove("salt").remove("hash").apply()
    fun check(c: Context, pin: String): Boolean {
        val p = prefs(c)
        val salt = p.getString("salt", null)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray() ?: return false
        return hex(hash(salt, pin)) == p.getString("hash", null)
    }
    fun bio(c: Context) = prefs(c).getBoolean("bio", false)
    fun setBio(c: Context, on: Boolean) = prefs(c).edit().putBoolean("bio", on).apply()

    fun bioAvailable(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
}

fun promptBiometric(activity: FragmentActivity, onOk: () -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle("Unlock LifeAPI").setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK).build(),
    )
}

@Composable
fun AppLockGateImpl(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val container = (ctx.applicationContext as LifeApp).container
    val profile by container.repo.dao.profile().collectAsStateWithLifecycle(null)
    var loaded by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var stoppedAt by remember { mutableLongStateOf(0L) }
    val p = profile

    LaunchedEffect(p != null) {
        if (p != null && !loaded) { locked = p.lockEnabled && LockStore.hasPin(ctx); loaded = true }
    }
    // hide content in the app switcher when "hide amounts" is on
    LaunchedEffect(p?.hideBalances) {
        val w = (ctx as? FragmentActivity)?.window ?: return@LaunchedEffect
        if (p?.hideBalances == true) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, p?.lockEnabled, p?.lockAfterSec) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val pp = p
                    if (pp != null && pp.lockEnabled && LockStore.hasPin(ctx) && stoppedAt > 0 &&
                        System.currentTimeMillis() - stoppedAt > pp.lockAfterSec * 1000L
                    ) locked = true
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (!loaded) { Box(Modifier.fillMaxSize().background(C.Graphite0)); return }
    if (locked) LockScreen(onUnlock = { locked = false })
    else content()
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val bio = remember { LockStore.bio(ctx) && LockStore.bioAvailable(ctx) }
    LaunchedEffect(Unit) { if (bio && activity != null) promptBiometric(activity, onUnlock) }
    PinPad(title = "Enter PIN", sub = "LifeAPI is locked", showBio = bio && activity != null, onBio = { activity?.let { promptBiometric(it, onUnlock) } }) { pin ->
        LockStore.check(ctx, pin).also { if (it) onUnlock() }
    }
}

/** 4-digit PIN pad. [onPin] returns true if accepted; false shakes and clears. */
@Composable
fun PinPad(title: String, sub: String, showBio: Boolean = false, onBio: () -> Unit = {}, onPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun tap(d: String) {
        if (pin.length >= 4) return
        pin += d
        if (pin.length == 4) {
            val ok = onPin(pin)
            if (!ok) scope.launch {
                for (x in listOf(14f, -12f, 8f, -4f, 0f)) shake.animateTo(x, tween(50))
                pin = ""
            } else pin = ""
        }
    }
    Column(
        Modifier.fillMaxSize().background(C.Graphite0).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(C.HealthTint), contentAlignment = Alignment.Center) {
            Icon(TIc.Lock, null, tint = C.Health, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = T.title, color = C.Ink)
        Text(sub, style = T.small, color = C.InkMuted)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.offset(x = shake.value.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) C.Volt else C.Track).border(1.dp, C.Ghost, CircleShape))
            }
        }
        Spacer(Modifier.height(36.dp))
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (showBio) "bio" else "", "0", "del"))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(if (k.length == 1) C.Graphite2 else androidx.compose.ui.graphics.Color.Transparent)
                                .press(enabled = k.isNotEmpty(), haptic = true) {
                                    when (k) { "del" -> pin = pin.dropLast(1); "bio" -> onBio(); else -> tap(k) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(TIc.Backspace, "Delete", tint = C.InkMuted, modifier = Modifier.size(24.dp))
                                "bio" -> Icon(TIc.Finger, "Fingerprint", tint = C.Health, modifier = Modifier.size(28.dp))
                                "" -> {}
                                else -> Text(k, style = T.title.copy(fontSize = 26.sp), color = C.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
