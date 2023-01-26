package com.example.signalmapper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuMeasurementCard (measurement: AccumulativeDBMMeasurement, onLocate: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)) {
            val mean = measurement.measurements.fold(measurement.measurements.first().dbm) { a, b -> (a + b.dbm) / 2 }

            Row  (Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically){
                Column (Modifier.weight(1f)) {
                    Text(
                        "${measurement.latLng.first}, ${measurement.latLng.second}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${measurement.measurements.size} measurements - $mean average dBm")
                }
                IconButton(onClick = onLocate) {
                    Icon(Icons.Rounded.LocationOn, null)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column (Modifier.fillMaxWidth()) {
                    val stDev = sqrt((measurement.measurements.sumOf { (it.dbm - mean).toDouble().pow(2).toInt() })/measurement.measurements.size.toDouble())

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Min: ${measurement.measurements.minOf { it.dbm }} dBm - Max: ${measurement.measurements.maxOf { it.dbm }} dBm")
                    Text("Mode: ${measurement.measurements.groupingBy { it.dbm }.eachCount().maxOf { it.key }} dBm")
                    Text("StDev: $stDev")
                    Spacer(modifier = Modifier.height(12.dp))
                    measurement.measurements.reversed().forEachIndexed() { i, meas ->
                        if (i > 10)
                            return@forEachIndexed
                        Column(Modifier.fillMaxWidth()){
                            SubMeasurementCard(measurement = meas)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    if (measurement.measurements.size > 10)
                        Card(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                            Row (
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                Text("${measurement.measurements.size - 10} older entries.")
                            }
                        }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubMeasurementCard (measurement: DBMMeasurement) {
    Card(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)) {
            Text("${measurement.latLng.first}, ${measurement.latLng.second} - ${measurement.dbm}", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(LocalDateTime.parse(measurement.dateTime).toLocalTime().toString())
        }
    }
}