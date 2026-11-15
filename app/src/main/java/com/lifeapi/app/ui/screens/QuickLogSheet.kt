package com.lifeapi.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.TagBadge
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.QuickLogVM
import kotlinx.coroutines.launch

private data class Cat(val label: String, val hue: Color, val placeholder: String, val seed: (String?, String?) -> String?)

private val CATS = listOf(
    Cat("Water", C.Health, "500 ml") { _, _ -> "250 ml" },
    Cat("Stack", C.Health, "magnesium") { s, _ -> s ?: "" },
    Cat("Sleep", C.Health, "slept 23:30-07:00") { _, _ -> "slept 23:30-07:00" },
    Cat("Meal", C.Fuel, "chicken bowl 640 kcal 52p") { _, _ -> null },
    Cat("Expense", C.Money, "coffee 4.50") { _, _ -> "" },
    Cat("Income", C.Money, "+600 freelance") { _, _ -> "+" },
    Cat("Set", C.Train, "bench 90x5") { _, _ -> "" },
    Cat("Habit", C.Grow, "read 20 pages") { _, h -> h ?: "" },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(vm: QuickLogVM, onDismiss: () -> Unit, onMealDraft: (String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val st by vm.state.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    var tfv by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    var placeholder by remember { mutableStateOf("coffee 4.50 · 500 ml · bench 90x5") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun close() { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }
    fun setText(t: String) { tfv = TextFieldValue(t, TextRange(t.length)); vm.text.value = t }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = C.Graphite1,
        scrimColor = Color(0x9E050608),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Ghost)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Log anything", style = T.title, color = C.Ink, modifier = Modifier.weight(1f))
                RoundIconButton(Ic.Close, "Close", size = 40.dp, bg = C.Track, onClick = ::close)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type it the way you'd say it", style = T.caption, color = C.InkMuted)
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D0E10))
                        .border(1.dp, C.Volt.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Ic.Search, null, tint = C.InkSubtle, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (tfv.text.isEmpty()) Text(placeholder, style = T.bodyR.copy(fontSize = 15.sp), color = C.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicTextField(
                            value = tfv,
                            onValueChange = { tfv = it; vm.text.value = it.text },
                            singleLine = true,
                            textStyle = T.bodyR.copy(fontSize = 15.sp, color = C.Ink),
                            cursorBrush = SolidColor(C.Volt),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                st?.preview?.let { p -> vm.commit(p, tfv.text, onMealDraft, ::close) }
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (tfv.text.isNotEmpty()) Icon(Ic.Close, "Clear", tint = C.InkSubtle, modifier = Modifier.size(18.dp).press { setText("") })
                }
            }

            AnimatedVisibility(st?.preview != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val p = st?.preview ?: return@AnimatedVisibility
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Graphite2).border(1.dp, Color(0xFF26282E), RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TagBadge(p.tag, C.tint(p.hue), C.hue(p.hue))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.title, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.meta, style = T.caption, color = C.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PillButton(if (p.action is com.lifeapi.app.data.QuickAction.MealDraft) "Add" else "Log", C.Volt, C.Graphite0, height = 40.dp) {
                        vm.commit(p, tfv.text, onMealDraft, ::close)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MonoLabel("Categories")
                CATS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            Column(
                                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF17181C))
                                    .border(1.dp, C.Hairline, RoundedCornerShape(16.dp))
                                    .press(haptic = true) {
                                        placeholder = c.placeholder
                                        val seed = c.seed(st?.stackHint, st?.habitHint)
                                        if (seed == null) onMealDraft("") else { setText(seed); runCatching { focus.requestFocus() } }
                                    }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(c.hue))
                                Text(c.label, style = T.smallB, color = C.Ink)
                            }
                        }
                    }
                }
            }

            val recent = st?.recent.orEmpty()
            if (recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MonoLabel("Again? One tap", modifier = Modifier.padding(bottom = 6.dp))
                    recent.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                                .press(haptic = true) { vm.repeat(r, onMealDraft, ::close) }.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(C.hue(r.hue)))
                            Text(r.label, style = T.body, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.value, style = T.small, color = C.InkMuted)
                            Icon(Ic.Plus, null, tint = C.InkSubtle, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
