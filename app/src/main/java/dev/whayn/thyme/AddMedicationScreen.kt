package dev.whayn.thyme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalTime

@Composable
fun AddMedicationScreen(
    onSave: (name: String, strength: String?, time: LocalTime, quantity: Double) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var strength by rememberSaveable { mutableStateOf("") }
    var hour by rememberSaveable { mutableStateOf("8") }
    var minute by rememberSaveable { mutableStateOf("00") }
    var quantity by rememberSaveable { mutableStateOf("1") }

    val time: LocalTime? = run {
        val h = hour.toIntOrNull()
        val m = minute.toIntOrNull()
        if (h != null && m != null && h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }
    val parsedQuantity = quantity.toDoubleOrNull()
    val isValid = name.isNotBlank() && time != null && parsedQuantity != null && parsedQuantity > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Medication") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = strength,
            onValueChange = { strength = it },
            label = { Text("Strength (optional)") },
            placeholder = { Text("500mg") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = hour,
                onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                label = { Text("Hour") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minute,
                onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                label = { Text("Minute") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Qty") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = {
                    if (time != null && parsedQuantity != null) {
                        onSave(name, strength, time, parsedQuantity)
                    }
                },
                enabled = isValid
            ) { Text("Save") }
        }
    }

}