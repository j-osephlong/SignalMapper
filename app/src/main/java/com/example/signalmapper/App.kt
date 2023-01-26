package com.example.signalmapper

import android.os.Message
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App (viewModel: SignalMapperViewModel, onStart: () -> Unit, onStop: () -> Unit, onSave: (String) -> Unit, onLoad: () -> Unit) {
    val fabBG by animateColorAsState(targetValue = if (viewModel.serviceState == ServiceState.RUNNING) Color(0xff3d8c40) else MaterialTheme.colorScheme.error)
    val fabFG by animateColorAsState(targetValue =
        if (viewModel.serviceState == ServiceState.RUNNING)
            MaterialTheme.colorScheme.contentColorFor(Color(0xff3d8c40))
        else
            MaterialTheme.colorScheme.onError
    )
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (viewModel.serviceState == ServiceState.RUNNING) {
                    onStart()
                } else {
                    onStop()
                }
            },
            containerColor = fabBG) {
                if (viewModel.serviceState == ServiceState.RUNNING)
                    Icon(Icons.Rounded.Check, null, tint = fabFG)
                else
                    Icon(Icons.Rounded.Close, null, tint = fabFG)
            }
        }
    ) {
        AppScreen(viewModel = viewModel, onSave, onLoad)
    }
}

@Composable
fun AppScreen(viewModel: SignalMapperViewModel, onSave: (String) -> Unit, onLoad: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column{
            val cameraPositionState = rememberCameraPositionState {

            }
            val uiSettings by remember { mutableStateOf(MapUiSettings()) }
            val properties by remember {
                mutableStateOf(MapProperties(mapType = MapType.SATELLITE))
            }
            var locked by remember { mutableStateOf(true) }
            LaunchedEffect(key1 = viewModel.lastMeasurement) {
                if (viewModel.lastMeasurement != null && locked) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                        LatLng(viewModel.lastMeasurement!!.latLng.first, viewModel.lastMeasurement!!.latLng.second),
                        18f
                    )
                }
            }
            LaunchedEffect(key1 = locked) {
                if (viewModel.lastMeasurement != null && locked)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                        LatLng(viewModel.lastMeasurement!!.latLng.first, viewModel.lastMeasurement!!.latLng.second),
                        18f
                    )
            }
            Box (
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)){
                GoogleMap(
                    uiSettings = uiSettings,
                    properties = properties,
                    cameraPositionState = cameraPositionState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    viewModel.measurements.forEach {
                        Marker(
                            position = LatLng(it.latLng.first, it.latLng.second),
                            tag = "Tag",
                            title = "${it.measurements.fold(it.measurements.first().dbm) { a, b -> (a + b.dbm) / 2 }} avg. dBm",
                            snippet = "Snippet"
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable { locked = !locked }
                ) {
                    Icon(
                        if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn (modifier = Modifier.padding(12.dp, 0.dp, 12.dp, 12.dp)) {
                item {
                    Row (
                        Modifier
                            .padding(0.dp, 12.dp)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.End){
                        IconButton(onClick = { viewModel.measurements.clear() }) {
                            Icon(Icons.Rounded.Clear, null)
                        }
                        IconButton(onClick = onLoad) {
                            Icon(Icons.Rounded.FolderOpen, null)
                        }
                        IconButton(onClick = { onSave("measurements_${LocalDateTime.now()}") }) {
                            Icon(Icons.Rounded.Save, null)
                        }
                    }
                }
                items(viewModel.measurements.reversed()) {
                    Column {
                        AccuMeasurementCard(measurement = it) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                LatLng(it.latLng.first, it.latLng.second),
                                18f
                            )
                        }
                        Spacer(modifier = (Modifier.height(12.dp)))
                    }
                }
            }
        }
    }
}