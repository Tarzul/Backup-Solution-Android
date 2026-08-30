package com.rezerv.upload.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rezerv.upload.SyncTask
import com.rezerv.upload.viewmodel.TasksViewModel
import com.rezerv.upload.ui.theme.ErrorRed
import com.rezerv.upload.ui.theme.HintGray
import com.rezerv.upload.ui.theme.SuccessGreen

@Composable
fun TasksTab(
    onTaskClick: (String) -> Unit,
    onCreateTask: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val currentTasks = tasks) {
            null -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(3) { TaskCardSkeleton() }
                }
            }
            else -> {
                if (currentTasks.isEmpty()) {
                    EmptyTasksState(onCreateTask = onCreateTask)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onClick = { onTaskClick(task.id) },
                                onRun = { viewModel.runTaskNow(task) },
                                onDelete = { viewModel.deleteTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: SyncTask,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (icon, color) = when (task.lastStatus) {
                    "ok" -> Icons.Default.CheckCircle to SuccessGreen
                    "error" -> Icons.Default.Error to ErrorRed
                    else -> Icons.Default.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name.ifEmpty { "Без имени" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = syncTypeLabel(task.syncType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Запустить")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PathInfo(
                    label = if (task.leftIsWebdav) "WebDAV" else "Локально",
                    path = if (task.leftIsWebdav) task.leftWebdavPath else "Память",
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    when (task.syncType) {
                        "two_way" -> Icons.Default.SyncAlt
                        "to_left" -> Icons.AutoMirrored.Filled.ArrowBack
                        else -> Icons.AutoMirrored.Filled.ArrowForward
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                PathInfo(
                    label = if (task.rightIsWebdav) "WebDAV" else "Локально",
                    path = if (task.rightIsWebdav) task.rightWebdavPath else "Память",
                    modifier = Modifier.weight(1f),
                    alignEnd = true
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить задание?") },
            text = { Text("Задание «${task.name}» будет удалено безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun PathInfo(
    label: String,
    path: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = path.ifEmpty { "/" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyTasksState(onCreateTask: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Нет заданий",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Нажмите «+» чтобы создать первую синхронизацию",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreateTask) {
            Text("Создать задание")
        }
    }
}

@Composable
private fun TaskCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    // ✅ ИСПРАВЛЕНО: используем Modifier.background вместо color параметра
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )
                }
            }
        }
    }
}

private fun syncTypeLabel(type: String) = when (type) {
    "two_way" -> "⇄ Двусторонняя"
    "to_left" -> "← В левую папку"
    else -> "→ В правую папку"
}