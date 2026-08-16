package dev.whayn.thyme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whayn.thyme.data.TodayDose
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThymeTheme {
                val viewModel: DoseListViewModel = viewModel(
                    factory = DoseListViewModel.factory(applicationContext)
                )
                val doses by viewModel.doses.collectAsStateWithLifecycle()
                var showAddForm by rememberSaveable { mutableStateOf(false) }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshDate()
                }

                BackHandler(enabled = showAddForm) {
                    showAddForm = false
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        if (!showAddForm) {
                            FloatingActionButton(onClick = { showAddForm = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add medication")
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when {
                            showAddForm -> AddMedicationScreen(
                                onSave = { name, strength, time, quantity ->
                                    viewModel.addDose(name, strength, time, quantity)
                                    showAddForm = false
                                },
                                onCancel = { showAddForm = false }
                            )

                            doses.isEmpty() -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No medications yet, tap + to add one")
                            }

                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(doses, key = { it.scheduled.id }) { item ->
                                    DoseRow(
                                        item = item,
                                        onToggle = { viewModel.toggle(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DoseRow(
    item: TodayDose,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = item.taken,
                onValueChange = { onToggle() },
                role = Role.Checkbox
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = item.scheduled.time.format(timeFormatter))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(text = item.medicationName)
            val detail = listOfNotNull(
                item.strength,
                "×${formatQuantity(item.scheduled.quantity)}"
            ).joinToString(" · ")
            Text(text = detail, style = MaterialTheme.typography.bodySmall)
        }

        Checkbox(checked = item.taken, onCheckedChange = null)
    }
}


private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    ThymeTheme {
//        DoseRow("Android")
//    }
//}