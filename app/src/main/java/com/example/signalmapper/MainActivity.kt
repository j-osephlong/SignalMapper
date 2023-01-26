package com.example.signalmapper

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.toMutableStateList
import com.example.signalmapper.ui.theme.SignalMapperTheme
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader


class MainActivity : ComponentActivity() {
    private var mActivityMessenger: Messenger? = null
    private var mServiceMessenger: Messenger? = null
    private var serviceConnection: ServiceConnection? = null
    val viewModel by viewModels<SignalMapperViewModel>()

    private lateinit var saveDirResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var loadDirResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        saveDirResultLauncher = setupSaveDirChooser()
        loadDirResultLauncher = setupLoadDirChooser()

        mActivityMessenger = Messenger(signalMapperClient.SignalMapperHandler())
        val lIntent = Intent(this@MainActivity, SignalMapperService::class.java)
        lIntent.putExtra("Messenger", mActivityMessenger)
        startService(lIntent)

        setContent {
            SignalMapperTheme {

                LaunchedEffect(key1 = true) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        val permissions = arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                        )
                        requestPermissions(permissions, 0)
                    }
                }
                App(
                    viewModel,
                    { mServiceMessenger?.send(Message().apply { obj = ServiceActions.STOP }) },
                    {
                        mServiceMessenger?.send(
                            Message().apply {
                                obj = ServiceActions.START
                            }
                        )
                    },
                    this::onSaveChooseDir,
                    this::onLoadChooseDir
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val lIntent = Intent(this@MainActivity, SignalMapperService::class.java)
        if (serviceConnection == null)
            serviceConnection = ServiceConnection()
        bindService(
            lIntent,
            serviceConnection!!,
            0
        ) // mCon is an object of MyServiceConnection Class
    }

    override fun onPause() {
        super.onPause()
        if (serviceConnection != null)
            unbindService(serviceConnection!!)
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        notificationManager.apply {
            createNotificationChannel(
                NotificationChannel(
                    "signalMapperServiceNotification",
                    "SignalMapper Service Notification",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background service"
                }
            )
        }

    }

    private fun setupSaveDirChooser() =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    contentResolver.openFileDescriptor(uri, "w")?.use { fD ->
                        FileOutputStream(fD.fileDescriptor).use {
                            it.write(
                                Json.encodeToString(
                                    PackagedData.serializer(),
                                    PackagedData(
                                        viewModel.measurements.toList()
                                    )
                                ).toByteArray()
                            )
                        }
                    }
                }
            }
        }

    private fun setupLoadDirChooser() =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use {
                            val packedData = it.readText()
                            viewModel.measurements = Json.decodeFromString(PackagedData.serializer(), packedData).measurements.toMutableStateList()
                            viewModel.measurements = viewModel.measurements
                        }
                    }
                }
            }
        }

    private fun onSaveChooseDir(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "$fileName.json")
        }
        saveDirResultLauncher.launch(intent)
    }

    private fun onLoadChooseDir() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        loadDirResultLauncher.launch(intent)
    }

    private val signalMapperClient =
        object {
            @SuppressLint("HandlerLeak")
            inner class SignalMapperHandler : Handler(Looper.myLooper()!!) {
                override fun handleMessage(msg: Message) {
                    when (msg.obj as ServiceState) {
                        ServiceState.RUNNING -> viewModel.serviceState = ServiceState.RUNNING
                        ServiceState.STOPPED -> viewModel.serviceState = ServiceState.STOPPED
                        ServiceState.MEASUREMENT -> {

                            val existingGroup = viewModel.measurements.filter {
                                it.inRange(Pair(msg.data.getDouble("lat"), msg.data.getDouble("lon")), 10)
                            }
                            Log.i("ServiceClient", "Existing: $existingGroup")
                            if (existingGroup.isEmpty()) {
                                viewModel.measurements += AccumulativeDBMMeasurement.initial(
                                    Pair(msg.data.getDouble("lat"), msg.data.getDouble("lon")),
                                    msg.data.getInt("dbm")
                                )
                                viewModel.lastMeasurement = viewModel.measurements.last()
                            }
                            else {
                                viewModel.measurements[viewModel.measurements.indexOf(
                                    existingGroup[0]
                                )] = existingGroup[0].add(
                                    Pair(msg.data.getDouble("lat"), msg.data.getDouble("lon")),
                                    msg.data.getInt("dbm")
                                )
                            }
                            Log.i("ServiceClient", "Got a reading of ${msg.data.getInt("dbm")} dbm.")
                            Log.i("ServiceClient", viewModel.measurements.toString())
                        }
                    }
                }
            }

        }


    inner class ServiceConnection : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mServiceMessenger = Messenger(service)
            // where mServiceMessenger is used to send messages to Service
            // service is the binder returned from onBind method in the Service
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mServiceMessenger = null
            unbindService(this)
        }
    }
}