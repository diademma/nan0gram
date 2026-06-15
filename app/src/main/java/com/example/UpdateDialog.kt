package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступно обновление") },
        text = {
            Column {
                Text("Вышла версия ${updateInfo.latestVersion}. Установить обновление?")
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(updateInfo.releaseNotes.take(300))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                UpdateChecker.openDownloadPage(context, updateInfo.downloadUrl)
                onDismiss()
            }) {
                Text("Обновить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Позже")
            }
        }
    )
}
