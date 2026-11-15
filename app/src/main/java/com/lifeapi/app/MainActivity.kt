package com.lifeapi.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifeapi.app.data.Days
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.MealSheet
import com.lifeapi.app.ui.components.ProfileSheet
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.BodyScreen
import com.lifeapi.app.ui.screens.GrowScreen
import com.lifeapi.app.ui.screens.MoneyScreen
import com.lifeapi.app.ui.screens.QuickLogSheet
import com.lifeapi.app.ui.screens.TodayScreen
import com.lifeapi.app.ui.screens.TrainScreen
import com.lifeapi.app.ui.screens.WorkoutScreen
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.LifeTheme
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.QuickLogVM
import com.lifeapi.app.ui.screens.today.todayGraph
import com.lifeapi.app.ui.screens.body.bodyGraph
import com.lifeapi.app.ui.screens.train.trainGraph
import com.lifeapi.app.ui.screens.money.moneyGraph
import com.lifeapi.app.ui.screens.grow.growGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        com.lifeapi.app.data.fatsecret.FatSecret.init(this)
        com.lifeapi.app.data.bank.Bank.init(this)
        com.lifeapi.app.data.binance.Binance.init(this)
        if (savedInstanceState == null) {
            com.lifeapi.app.data.fatsecret.FatSecret.handleCallback(intent?.data)
            if (com.lifeapi.app.data.bank.Bank.handleCallback(intent?.data)) (application as LifeApp).container.snack("Finishing bank connection…")
        }
        setContent { LifeTheme { com.lifeapi.app.ui.screens.today.AppLockGate { LifeRoot() } } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (com.lifeapi.app.data.fatsecret.FatSecret.handleCallback(intent.data)) {
            (application as LifeApp).container.snack("Finishing FatSecret sign-in…")
        }
        if (com.lifeapi.app.data.bank.Bank.handleCallback(intent.data)) {
            (application as LifeApp).container.snack("Finishing bank connection…")
        }
    }

    override fun onResume() {
        super.onResume()
        (application as LifeApp).container.refreshDay()
        com.lifeapi.app.data.fatsecret.FatSecret.autoSync()
        com.lifeapi.app.data.binance.Binance.maybeAutoSync()
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("today", "Today", Ic.Today),
    Tab("body", "Body", Ic.Body),
    Tab("money", "Money", Ic.Money),
    Tab("train", "Train", Ic.Train),
    Tab("grow", "Grow", Ic.Grow),
)

@Composable
fun LifeRoot() {
    val nav = rememberNavController()
    val container = (androidx.compose.ui.platform.LocalContext.current.applicationContext as LifeApp).container
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "today"
    val inWorkout = route.startsWith("workout")

    var bodySeg by com.lifeapi.app.ui.Segments.body
    var quickOpen by remember { mutableStateOf(false) }
    var quickSeed by remember { mutableStateOf("") }
    var mealDraft by remember { mutableStateOf<String?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    val quickVm: QuickLogVM = viewModel()

    fun go(r: String) {
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openQuick(seed: String = "") { quickSeed = seed; quickVm.text.value = seed; quickOpen = true }
    val actions = remember(nav) {
        object : com.lifeapi.app.ui.AppActions {
            override fun quickLog(seed: String) = openQuick(seed)
            override fun meal(draft: String) { mealDraft = draft }
            override fun tab(route: String) = go(route)
            override fun bodySegment(seg: Int) { com.lifeapi.app.ui.Segments.body.intValue = seg }
            override fun growSegment(seg: Int) { com.lifeapi.app.ui.Segments.grow.intValue = seg }
        }
    }
    val fab by com.lifeapi.app.ui.Fab.override

    androidx.compose.runtime.CompositionLocalProvider(
        com.lifeapi.app.ui.LocalNav provides nav,
        com.lifeapi.app.ui.LocalActions provides actions,
    ) {
    Box(Modifier.fillMaxSize().background(C.Graphite0)) {
        NavHost(nav, startDestination = "today", modifier = Modifier.fillMaxSize()) {
            composable("today") {
                TodayScreen(
                    openStack = { bodySeg = 0; go("body") },
                    openMeal = { mealDraft = "" },
                    openSpend = { openQuick() },
                    openTab = { go(it) },
                    openProfile = { nav.navigate("settings") },
                )
            }
            composable("body") { BodyScreen(bodySeg, onSeg = { bodySeg = it }, openMeal = { mealDraft = "" }) }
            composable("money") { MoneyScreen() }
            composable("train") { TrainScreen(onOpenWorkout = { nav.navigate("workout/$it") }) }
            composable("grow") { GrowScreen() }
            composable("workout/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                WorkoutScreen(e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            todayGraph(nav)
            bodyGraph(nav)
            trainGraph(nav)
            moneyGraph(nav)
            growGraph(nav)
        }

        // Snackbar
        SnackHost(container, bottomOffset = if (route in TABS.map { it.route }) (if (com.lifeapi.app.ui.Fab.visible) 160.dp else 92.dp) else 16.dp)

        if (!inWorkout && route in TABS.map { it.route }) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                com.lifeapi.app.ui.screens.train.PausedWorkoutBar(onOpen = { nav.navigate("workout/$it") })
                val f = fab
                if (com.lifeapi.app.ui.Fab.visible) Box(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    Row(
                        Modifier
                            .shadow(20.dp, RoundedCornerShape(26.dp), ambientColor = C.Volt.copy(alpha = 0.3f), spotColor = C.Volt.copy(alpha = 0.3f))
                            .height(52.dp).clip(RoundedCornerShape(26.dp)).background(C.Volt)
                            .press(haptic = true) { if (f != null) f.onClick() else openQuick() }
                            .padding(start = 16.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(f?.icon ?: Ic.Plus, null, tint = C.Graphite0, modifier = Modifier.size(20.dp))
                        Text(f?.label ?: "Log", style = T.section, color = C.Graphite0)
                    }
                }
                BottomBar(route) { tab -> go(tab) }
            }
        }
    }
    }

    if (quickOpen) {
        QuickLogSheet(
            vm = quickVm,
            onDismiss = { quickOpen = false },
            onMealDraft = { quickOpen = false; mealDraft = it },
        )
    }
    mealDraft?.let { draft ->
        MealSheet(initialItems = draft, onDismiss = { mealDraft = null }) { m ->
            quickVm.addMeal(m)
        }
    }
    if (profileOpen) {
        val p by container.repo.dao.profile().collectAsStateWithLifecycle(null)
        p?.let { prof ->
            val scope = rememberCoroutineScope()
            ProfileSheet(prof, onDismiss = { profileOpen = false }) { np -> scope.launch { container.repo.dao.saveProfile(np) } }
        }
    }
}

@Composable
private fun BottomBar(route: String, onTab: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Graphite0)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp).padding(horizontal = 4.dp)) {
            TABS.forEach { t ->
                val sel = route == t.route
                Column(
                    Modifier.weight(1f).fillMaxSize().press { onTab(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (sel) C.Graphite3 else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, t.label, tint = if (sel) C.Ink else C.InkSubtle, modifier = Modifier.size(20.dp)) }
                    Text(
                        t.label, style = T.tiny.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (sel) C.Ink else C.InkSubtle, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SnackHost(container: AppContainer, bottomOffset: androidx.compose.ui.unit.Dp) {
    var current by remember { mutableStateOf<Snack?>(null) }
    var stamp by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        container.snacks.collect { current = it; stamp = Days.now() }
    }
    LaunchedEffect(stamp) {
        if (current != null) { delay(3200); current = null }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = bottomOffset), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(180)) + scaleIn(initialScale = 0.96f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f),
        ) {
            val s = current
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(16.dp))
                    .height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Ink)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s?.text ?: "", style = T.body, color = C.Graphite0, modifier = Modifier.weight(1f), maxLines = 1)
                if (s?.undo != null) {
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).press {
                            val u = s.undo
                            current = null
                            scope.launch { u() }
                        }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Undo", style = T.body.copy(fontWeight = FontWeight.Bold), color = Color(0xFF3B4A10)) }
                }
            }
        }
    }
}
