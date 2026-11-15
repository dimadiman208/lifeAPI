package com.lifeapi.app.ui.screens.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

/**
 * Bottom sheet with a fixed maximum height (90% of the screen): title + Cancel pinned on top, the primary button
 * pinned at the bottom, and only the middle scrolls. Grows with content up to the cap — never past it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    title: String,
    onDismiss: () -> Unit,
    confirm: String = "Save",
    hue: Color = C.Health,
    canConfirm: Boolean = true,
    onConfirm: () -> Unit,
    onDelete: (() -> Unit)? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = C.Graphite1,
        scrimColor = Color(0x9E050608),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Ghost)) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxH).imePadding().navigationBarsPadding()) {
            // ---- pinned header
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = T.title.copy(fontSize = 20.sp), color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onDelete != null) RoundIconButton(Ic.Trash, "Delete", size = 38.dp, bg = C.Track, tint = C.Negative, onClick = onDelete)
                Text(
                    "Cancel", style = T.smallB, color = C.InkMuted,
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).press(onClick = onDismiss).padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
            // ---- scrolling body
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            // ---- pinned action
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Divider))
            PillButton(
                confirm, hue, C.Graphite0, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                height = 52.dp, radius = 16.dp, enabled = canConfirm, style = T.section, onClick = onConfirm,
            )
        }
    }
}

/**
 * Section header used by all four Body sections: hue icon, title, value (+ goal), trailing slot and a round "+".
 */
@Composable
fun BodySectionHeader(
    icon: ImageVector,
    title: String,
    value: String? = null,
    goal: String? = null,
    hue: Color = C.Health,
    onAdd: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(hue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = hue, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(title, style = T.section, color = C.Ink, maxLines = 1)
            if (value != null) Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = hue, maxLines = 1)
                if (goal != null) Text(" / $goal", style = T.caption, color = C.InkSubtle, maxLines = 1)
            }
        }
        trailing()
        if (onAdd != null) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(hue).press(haptic = true, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.Plus, "Add", tint = C.Graphite0, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Card container for a Body section — same surface for all four. */
@Composable
fun BodySection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(C.Graphite1).border(1.dp, C.Hairline, shape).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * One entry row: leading (time / date in mono), main text, trailing text. Tap = edit, swipe left = delete.
 * The row springs back after a swipe; the list drops it once the delete lands (undo via snackbar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryRow(
    lead: String,
    main: String,
    trailing: String? = null,
    sub: String? = null,
    highlight: Boolean = false,
    trailingColor: Color = C.Ink,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val st = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
        false
    })
    SwipeToDismissBox(
        st,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A1715)).padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Ic.Trash, "Delete", tint = C.Negative, modifier = Modifier.size(18.dp)) }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(if (highlight) C.Graphite3 else C.Graphite2)
                .press(onClick = onEdit).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(lead, style = T.mono, color = if (highlight) C.Ink else C.InkSubtle)
            Column(Modifier.weight(1f)) {
                Text(main, style = T.body, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) Text(trailing, style = T.body.copy(fontWeight = FontWeight.SemiBold), color = trailingColor)
        }
    }
}

/** Quiet placeholder line inside a section. */
@Composable
fun EmptyLine(text: String) {
    Text(text, style = T.caption, color = C.InkFaint, modifier = Modifier.padding(vertical = 4.dp))
}
