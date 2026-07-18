package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

@Composable
internal fun LogPanel(
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    logList: List<String>,
    logListState: LazyListState,
    onClear: () -> Unit,
    isBgServiceActive: Boolean,
    uiAlpha: Float,
    onUiAlphaChange: (Float) -> Unit,
    isParserEnabled: Boolean,
    onParserToggle: () -> Unit,
    coords: DomCoords,
    onUkrnetReload: () -> Unit,
    onMessengerReload: () -> Unit,
    coroutineScope: CoroutineScope,
    log: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
        if (!isExpanded) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                    .background(Color(0xEE1C1524), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFA773D1), shape = RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress { _, dragAmount ->
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                        }
                    }
                    .clickable { onToggle(true) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🐞 Логи (${logList.size})",
                    color = Color(0xFFD0BCFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter)
                    .background(Color(0x90F0A15))
                    .border(1.dp, Color(0xFFA773D1))
                    .padding(6.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "nan0gram логи",
                            color = Color(0xFFA773D1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onMessengerReload) {
                                Text("🔄 Mess", color = Color(0xFFD0BCFF), fontSize = 10.sp)
                            }
                            TextButton(onClick = onUkrnetReload) {
                                Text("🔄 Ukr", color = Color(0xFFD0BCFF), fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                clipboardManager.setText(AnnotatedString(logList.joinToString("\n")))
                            }) {
                                Text("📋", color = Color(0xFFC2FFD9), fontSize = 10.sp)
                            }
                            TextButton(onClick = onClear) {
                                Text("❌", color = Color(0xFFEFB8C8), fontSize = 10.sp)
                            }
                            TextButton(onClick = { onToggle(false) }) {
                                Text("➖", color = Color(0xFFCCC2DC), fontSize = 10.sp)
                            }
                        }
                    }
                    if (!isBgServiceActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Видимость: ${(uiAlpha * 100).toInt()}%",
                                color = Color(0xFFE0C3FC),
                                fontSize = 11.sp,
                                modifier = Modifier.width(130.dp)
                            )
                            Slider(
                                value = uiAlpha,
                                onValueChange = onUiAlphaChange,
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF07040A))
                            .padding(4.dp)
                    ) {
                        items(logList) { line ->
                            Text(
                                text = line,
                                color = Color(0xFFC2FFD9),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}