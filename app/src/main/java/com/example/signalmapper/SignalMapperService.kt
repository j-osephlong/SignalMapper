package com.example.signalmapper

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.*
import android.telephony.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationManagerCompat.requestLocationUpdates
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import java.lang.String
import kotlin.Double
import kotlin.Int
import kotlin.Unit
import kotlin.apply


enum class ServiceActions {
    START, STOP
}

enum class ServiceState {
    RUNNING, STOPPED, MEASUREMENT
}

class SignalMapperService : Service() {

    private lateinit var mServiceMessenger: Messenger
    var mActivityMessenger: Messenger? = null

    private val notificationChannelId = "signalMapperServiceNotification"
    private val notificationId = 102
    private var notification: Notification? = null

//    private val delay = 60000L //1 second
    private val delay = 10000L //1 second
    var handler: Handler? = null

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null

    private var lastLocation: Pair<Double, Double> = Pair(0.0, 0.0)

    inner class IncomingHandler : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            when (msg.obj as ServiceActions) {
                ServiceActions.START -> {
                    moveToForeground()
                    if (ActivityCompat.checkSelfPermission(
                            this@SignalMapperService,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            this@SignalMapperService,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    fusedLocationProviderClient.requestLocationUpdates(
                        locationRequest!!, locationCallback!!, Looper.myLooper()!!
                    )
                    runHandler()
//                    (getSystemService(TELEPHONY_SERVICE) as TelephonyManager).listen(
//                        phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO
//                    )
                    val lMsg = Message().apply {
                        obj = ServiceState.RUNNING
                    }
                    mActivityMessenger?.send(
                        lMsg
                    )
                }
                ServiceActions.STOP -> {
                    val lMsg = Message().apply {
                        obj = ServiceState.STOPPED
                    }
                    mActivityMessenger?.send(
                        lMsg
                    )
//                    (getSystemService(TELEPHONY_SERVICE) as TelephonyManager).listen(
//                        phoneStateListener, PhoneStateListener.LISTEN_NONE
//                    )
                    stopHandler()
                    fusedLocationProviderClient.removeLocationUpdates(locationCallback!!)
                    stopForeground(true)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("RecorderService", "onBind")
        return mServiceMessenger.binder
    }

    @SuppressLint("VisibleForTests")
    override fun onCreate() {
        super.onCreate()
        mServiceMessenger = Messenger(IncomingHandler())
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        buildLocationRequest()
        buildLocationCallBack()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RecorderService", "onStartCommand")
        mActivityMessenger = intent?.getParcelableExtra("Messenger")
        return super.onStartCommand(intent, flags, startId)
    }

    fun runHandler() {
        if (handler == null)
            handler = Handler(Looper.myLooper()!!)
        handler!!.postDelayed({
            postMeasurement()
            runHandler()
        }, delay)
    }

    private fun stopHandler() {
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }

    private fun postMeasurement () {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val cellData = (getSystemService(TELEPHONY_SERVICE) as TelephonyManager).allCellInfo
        val dbm = when (cellData[0]) {
            is CellInfoGsm -> (cellData[0] as CellInfoGsm).cellSignalStrength.dbm
            is CellInfoLte -> (cellData[0] as CellInfoLte).cellSignalStrength.dbm
            else -> 0
        }
        mActivityMessenger?.send(
            Message().apply {
                obj = ServiceState.MEASUREMENT
                data = Bundle().apply {
                    putInt("dbm", dbm)
                    putDouble("lat", lastLocation.first)
                    putDouble("lon", lastLocation.second)
                }
            }
        )
    }

    private fun buildLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = delay/2
            fastestInterval = delay/4
            smallestDisplacement = 0f
        }
    }

    private fun buildLocationCallBack() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    lastLocation = Pair(location.latitude, location.longitude)
                }
            }
        }
    }


    private fun getNotificationIntent(): PendingIntent {
        val openActivityIntent = Intent(
            this,
            MainActivity::class.java
        )
        openActivityIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            this@SignalMapperService, 0, openActivityIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun moveToForeground() {
        val pendingIntent = getNotificationIntent()

        notification = Notification.Builder(this, notificationChannelId)
            .setContentTitle("Signal Mapper")
            .setContentText("Currently mapping signal strength")
            .setSmallIcon(R.drawable.ic_baseline_my_location_24)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(
            notificationId,
            notification!!, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        else
            startForeground(notificationId, notification)
    }

    val phoneStateListener = object: PhoneStateListener() {
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED || androidx.core.app.ActivityCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.READ_PHONE_STATE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val dbm = when (cellInfo!![0]) {
                is CellInfoGsm -> (cellInfo[0] as CellInfoGsm).cellSignalStrength.dbm
                is CellInfoLte -> (cellInfo[0] as CellInfoLte).cellSignalStrength.dbm
                else -> 0
            }
            mActivityMessenger?.send(
                Message().apply {
                    obj = ServiceState.MEASUREMENT
                    data = Bundle().apply {
                        putInt("dbm", dbm)
                    }
                }
            )
            super.onCellInfoChanged(cellInfo)
        }

    }
}