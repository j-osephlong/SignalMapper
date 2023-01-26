package com.example.signalmapper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SignalMapperViewModel : ViewModel() {
    var serviceState by mutableStateOf<ServiceState?>(null)
    var measurements = mutableStateListOf<AccumulativeDBMMeasurement>()
    var lastMeasurement by mutableStateOf<AccumulativeDBMMeasurement?>(null)

}