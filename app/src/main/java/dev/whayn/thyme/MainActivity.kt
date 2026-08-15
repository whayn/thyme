package dev.whayn.thyme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whayn.thyme.data.Dose
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThymeTheme {
                var viewModel: DoseListViewModel = viewModel(
                    factory = DoseListViewModel.factory(applicationContext)
                )
                val doses by viewModel.doses.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(doses, key = { it.id }) { dose ->
                            DoseRow(
                                dose = dose,
                                taken = dose.taken,
                                onToggle = { viewModel.toggle(dose) }
                            )

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DoseRow(
    dose: Dose,
    taken: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = dose.time.format(DateTimeFormatter.ofPattern(("HH:mm"))))
        Text(text = dose.medication, modifier = Modifier.padding(start = 16.dp))
        Checkbox(checked = taken, onCheckedChange = { onToggle() })
    }
}

//@Preview(showBackground = true) 
//@Composable
//fun GreetingPreview() {
//    ThymeTheme {
//        DoseRow("Android")
//    }
//}