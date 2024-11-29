package com.ai.indorroutemaker
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ai.indorroutemaker.ui.theme.IndorRouteMakerTheme
import com.google.android.gms.location.*
//import com.google.android.gms.maps.*
//import com.google.android.gms.maps.model.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.FusedLocationProviderClient
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.log


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    //    private var accelerometerValues by mutableStateOf("0.0, 0.0, 0.0")
    private var gyroscopeValues by mutableStateOf("0.0, 0.0, 0.0")
    private var currentLocation: Location? = null
    private var isTracking = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationRequest()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                Log.d("Permission", "Permission is available for activity recognition.");
                requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 100)
            }
        }
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "Permission granted for activity recognition.");
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 100)
        }
        else{
            Log.d("Permission", "Permission is available for activity recognition.");

        }
        for (sensor in allSensors) {
            Log.d("Sensor", "Sensor: ${sensor.name}, Type: ${sensor.type}")
        }
        setContent {
            IndorRouteMakerTheme {
                MainScreen()
            }
        }
    }
    private fun setupLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 10000 // Update location every 10 seconds
            fastestInterval = 5000 // Fastest update interval
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    currentLocation = location
                    Log.d(
                        "LocationUpdate",
                        "New location: ${location.latitude}, ${location.longitude}"
                    )
                }
            }
        }
    }
    private var arrowOffsetX by mutableStateOf(0f)
    private var arrowOffsetY by mutableStateOf(0f)
    private var velocityX by mutableStateOf(0f)
    private var velocityY by mutableStateOf(0f)
    private var pathPoints = mutableListOf<Pair<Float, Float>>()

    private val timeInterval = 0.1f // Time interval in seconds (adjust based on your needs)
    @Composable
    fun MainScreen() {
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startLocationUpdates()
            }
        }
        LaunchedEffect(Unit) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        LaunchedEffect(Unit) {
            // Arrow should start from the bottom center of its box
            arrowOffsetX = 200f // Half of the Box size (400dp)
            arrowOffsetY = 400f // This positions it at the bottom of the Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { startTracking() }) {
                Text("Start Tracking")
            }
            Button(onClick = { stopTracking() }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Stop Tracking")
            }

            SensorValuesDisplay()
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .background(Color.Gray)
                    .padding(8.dp)
            ) {
                DisplayUserPosition()
            }
        }
    }
    @Composable
    fun DisplayUserPosition() {
        val boxSize = 800.dp
        val arrowSize = 30.dp
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset(0f, 0f)) }
        var gesturePan by remember { mutableStateOf(Offset(0f, 0f)) }

        // Gesture modifier for zoom and pan
        val gestureModifier = Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale *= zoom
                scale = scale.coerceIn(0.5f, 3f)
                gesturePan += pan
                offset = Offset(
                    x = gesturePan.x.coerceIn(-2000f, 2000f),
                    y = gesturePan.y.coerceIn(-2000f, 2000f)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(boxSize)
                .background(Color.Gray)
                .then(gestureModifier)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(left = offset.x, top = offset.y)
                    scale(scale)
                }) {
                    // Center of the canvas (base position for the arrow)
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    // Adjust path points to be centered behind the arrow
                    val adjustedPathPoints = pathPoints.map { point ->
                        Offset(
                            x = centerX + point.first - pathPoints.last().first,
                            y = centerY + point.second - pathPoints.last().second
                        )
                    }

                    // Draw the path behind the arrow
                    adjustedPathPoints.forEachIndexed { index, point ->
                        if (index > 0) {
                            drawLine(
                                start = adjustedPathPoints[index - 1],
                                end = point,
                                color = Color.Green,
                                strokeWidth = 5f / scale
                            )
                        }
                    }

                    // Rotate the arrow based on the azimuth
                    rotate(azimuth) {
                        Log.d("azimuth", "azimuth value is : ${azimuth}");
                        // Draw the arrow at the center of the canvas
                        val arrowWidth = arrowSize.toPx() * scale
                        val arrowHeight = arrowSize.toPx() * scale
                        val arrowPath = Path().apply {
                            moveTo(centerX, centerY - arrowHeight / 2) // Arrow tip
                            lineTo(centerX + arrowWidth / 2, centerY + arrowHeight / 2) // Bottom-right
                            lineTo(centerX, centerY + arrowHeight / 4) // Mid-bottom
                            lineTo(centerX - arrowWidth / 2, centerY + arrowHeight / 2) // Bottom-left
                            close()
                        }
                        drawPath(path = arrowPath, color = Color.Blue)
                    }
                }
            }
        }
    }



    @Composable
    fun SensorValuesDisplay() {
        val direction = when {
            azimuth >= 337.5 || azimuth < 22.5 -> "North"
            azimuth >= 22.5 && azimuth < 67.5 -> "Northeast"
            azimuth >= 67.5 && azimuth < 112.5 -> "East"
            azimuth >= 112.5 && azimuth < 157.5 -> "Southeast"
            azimuth >= 157.5 && azimuth < 202.5 -> "South"
            azimuth >= 202.5 && azimuth < 247.5 -> "Southwest"
            azimuth >= 247.5 && azimuth < 292.5 -> "West"
            azimuth >= 292.5 && azimuth < 337.5 -> "Northwest"
            else -> "Unknown"
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.LightGray)
                .padding(4.dp)
        ) {
            Text(text = "Direction: $direction")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.LightGray)
                .padding(4.dp)
        ) {
            // Convert the accelerometer values to a string
            Text(text = "Accelerometer: ${accelerometerValues.joinToString(", ") { it.toString() }}")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.LightGray)
                .padding(4.dp)
        ) {
            // Convert the magnetometer values to a string
            Text(text = "Magnetometer: ${magnetometerValues.joinToString(", ") { it.toString() }}")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.LightGray)
                .padding(4.dp)
        ) {
            Text(text = "Linear Acceleration: $linearAccelerationValues")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.LightGray)
                .padding(4.dp)
        ) {
// Text(text = "Gyroscope: $gyroscopeValues")
            Text(text = "Direction: ${directionDegrees.toInt()}°")
            Text(text = "Step Count: $stepCount")
            Text(text = "Speed: ${"%.2f".format(currentSpeed)} m/s")
            Text(text = "Distance Traveled: ${"%.2f".format(distanceTraveled)} m")
        }
    }

    private var stepCount by mutableStateOf(0)
    private var initialStepCount: Int? = null
    private var linearAccelerationValues by mutableStateOf("0.0, 0.0, 0.0")
    private var distanceTraveled by mutableStateOf(0.0f)
    private var currentSpeed by mutableStateOf(0.0f)
    private var lastUpdateTime: Long = System.currentTimeMillis()
    private val ACCELERATION_THRESHOLD = 1.0f

    private var magnetometerValues = floatArrayOf(0f, 0f, 0f)
    private var accelerometerValues = floatArrayOf(0f, 0f, 0f)
    private var azimuth = 0f



    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelerometerValues = it.values
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magnetometerValues = it.values
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    linearAccelerationValues = it.values.joinToString(", ")
                    updateDistanceAndSpeed(it.values)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroscopeValues = it.values.joinToString(", ")
                    calculateDirection(it.values)
                }
                Sensor.TYPE_STEP_COUNTER -> {
                    val totalSteps = event.values[0].toInt()
                    if (initialStepCount == null) {
                        initialStepCount = totalSteps
                    }
                    val stepsSinceStart = totalSteps - (initialStepCount ?: 0)
                    stepCount = stepsSinceStart
                    val averageStepLength = 0.7f // Adjust as needed
                    val stepDistance = averageStepLength * stepsSinceStart
                    arrowOffsetY -= stepDistance
                    arrowOffsetY = arrowOffsetY.coerceIn(0f, 800f)
                    arrowOffsetX = arrowOffsetX.coerceIn(0f, 800f)
                    pathPoints.add(Pair(arrowOffsetX, arrowOffsetY))
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    if (it.values[0] == 1.0f) {
                        stepCount += 1
                    } else {

                    }
                }
                else -> {
                }
            }

            if (magnetometerValues.isNotEmpty() && accelerometerValues.isNotEmpty()) {
                val R = FloatArray(9)
                val I = FloatArray(9)
                val success = SensorManager.getRotationMatrix(R, I, accelerometerValues, magnetometerValues)
                if (success) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(R, orientation)
                    azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (azimuth < 0) {
                        azimuth += 360f
                    }
                }
            }
        }
    }
    private fun updateDistanceAndSpeed(acceleration: FloatArray) {
        val currentTime = System.currentTimeMillis()
        val timeDiff = (currentTime - lastUpdateTime) / 1000.0f
        if (timeDiff > 0) {
            val accelerationMagnitude = Math.sqrt(
                (acceleration[0] * acceleration[0] + acceleration[1] * acceleration[1] + acceleration[2] * acceleration[2]).toDouble()
            ).toFloat()
            if (accelerationMagnitude > ACCELERATION_THRESHOLD) {
                currentSpeed += accelerationMagnitude * timeDiff
                distanceTraveled += currentSpeed * timeDiff + 0.5f * accelerationMagnitude * timeDiff * timeDiff
            } else {
                currentSpeed *= 0.95f
            }
            lastUpdateTime = currentTime
        }
    }

    private fun startTracking() {
        isTracking = true
        startLocationUpdates()
    }
    private fun stopTracking() {
        isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }
    override fun onResume() {
        super.onResume()
        registerSensors()
    }
    override fun onPause() {
        super.onPause()
        unregisterSensors()
    }
    private var directionDegrees by mutableStateOf(0f)
    private fun calculateDirection(gyroscopeValues: FloatArray) {
        directionDegrees = Math.toDegrees(gyroscopeValues[2].toDouble()).toFloat()
    }
    private fun registerSensors() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        gyroscope?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        val linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        linearAcceleration?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Register sensors
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }


        val stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
        if (stepCounterSensor != null) {
            Log.d("Sensor", "STEP_COUNTER is available.");
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.d("Sensor", "STEP_COUNTER is NOT available.");
        }
        Log.d("Sensor", "Going to check conditions for sensors availability.")
        if (stepDetectorSensor != null) {
            Log.d("Sensor", "STEP_DETECTOR is available.")
        } else {
            Log.d("Sensor", "STEP_DETECTOR is NOT available.")
        }
        if (stepCounterSensor != null) {
            Log.d("Sensor", "STEP_COUNTER is available.")
        } else {
            Log.d("Sensor", "STEP_COUNTER is NOT available.")
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}