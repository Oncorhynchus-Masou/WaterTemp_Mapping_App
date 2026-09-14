package com.example.gsitilemap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import androidx.compose.runtime.mutableStateOf
import com.example.gsitilemap.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.MarkerOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import org.maplibre.android.annotations.Marker
import org.maplibre.android.maps.MapLibreMap
import androidx.compose.material3.Button
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.activity.compose.rememberLauncherForActivityResult
import android.annotation.SuppressLint
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import com.example.gsitilemap.model.Measurement
import com.example.gsitilemap.model.MeasurementCreate
import com.example.gsitilemap.model.ReadingCreate


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(applicationContext)

        setContent {
            GsiTileMap()
        }
    }
}

@Composable
fun GsiTileMap() {

    val context = LocalContext.current
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val currentLatitude = remember { mutableStateOf<Double?>(null) }
    val currentLongitude = remember { mutableStateOf<Double?>(null) }
    val currentAccuracy = remember { mutableStateOf<Float?>(null) }
    val locationMessage = remember { mutableStateOf("位置情報を取得していません") }

    // 登録画面用
    val registrationLatitude = remember { mutableStateOf("") }
    val registrationLongitude = remember { mutableStateOf("") }
    val registrationAccuracy = remember { mutableStateOf<Float?>(null) }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                getCurrentLocation(
                    fusedLocationClient = fusedLocationClient,
                    onLocationReceived = { location ->

                        currentLatitude.value = location.latitude
                        currentLongitude.value = location.longitude
                        currentAccuracy.value = location.accuracy
                        locationMessage.value = "位置情報取得成功"

                        // 登録画面用
                        registrationLatitude.value =
                            location.latitude.toString()

                        registrationLongitude.value =
                            location.longitude.toString()

                        registrationAccuracy.value =
                            location.accuracy
                    },
                    onError = {
                        locationMessage.value = "位置情報の取得に失敗しました"
                    }
                )
            } else {
                locationMessage.value = "位置情報の権限がありません"
            }
        }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 現在選択されている測定データ
    val selectedMeasurement = remember {
        mutableStateOf<Measurement?>(null)
    }

    // プロファイル表示用の状態を追加
    val showProfile = remember {
        mutableStateOf(false)
    }

    // 測定登録画面を表示するか
    val showRegistration = remember {
        mutableStateOf(false)
    }

    // 気温
    val registrationAirTemperature = remember {
        mutableStateOf("")
    }

    // 深度・水温の入力行
    val registrationReadings = remember {
        mutableStateListOf(
            Pair("0.0", "")
        )
    }

    // 測定場所
    val registrationMeasuredAt = remember {
        mutableStateOf("")
    }

    // 登録中のメッセージ
    val registrationMessage = remember {
        mutableStateOf("")
    }

    //
    val isRegistering = remember {
        mutableStateOf(false)
    }

    // 選択されたCSVファイル
    val selectedCsvUri = remember {
        mutableStateOf<Uri?>(null)
    }

    // CSVファイル選択
    val csvFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            selectedCsvUri.value = uri
        }

    // プロファイル上で選択された実測深度
    val selectedProfileDepth = remember {
        mutableStateOf<Double?>(null)
    }

    // プロファイル上で選択された実測水温
    val selectedProfileTemperature = remember {
        mutableStateOf<Double?>(null)
    }

    // APIから取得した全測定データ
    val allMeasurements = remember {
        mutableStateOf<List<Measurement>>(emptyList())
    }

    // 選択中の年
    val selectedYear = remember {
        mutableStateOf("すべて")
    }

    // 選択中の月
    val selectedMonth = remember {
        mutableStateOf("すべて")
    }

    // マーカーとMeasurementの対応表
    val markerMeasurements = remember {
        mutableMapOf<Long, Measurement>()
    }

    // 現在地図に表示しているマーカー
    val markers = remember {
        mutableStateListOf<Marker>()
    }

    // MapLibreMap本体
    val mapState = remember {
        mutableStateOf<MapLibreMap?>(null)
    }

    val mapView = remember {
        MapView(context).apply {

            getMapAsync { map ->

                mapState.value = map

                map.setStyle(
                    Style.Builder().fromJson(GSI_STYLE)
                ) {

                    // 初期位置
                    map.cameraPosition =
                        CameraPosition.Builder()
                            .target(
                                LatLng(
                                    35.6812,
                                    139.7671
                                )
                            )
                            .zoom(12.0)
                            .build()

                    // マーカーがタップされたとき
                    map.setOnMarkerClickListener { marker ->

                        selectedMeasurement.value =
                            markerMeasurements[marker.id.toLong()]

                        showProfile.value = false

                        true
                    }

                    map.addOnMapClickListener { _ ->
                        selectedMeasurement.value = null
                        showProfile.value = false
                        true
                    }

                    // FastAPIからデータ取得
                    CoroutineScope(Dispatchers.IO).launch {

                        try {

                            val measurements =
                                RetrofitClient.apiService
                                    .getMeasurements()

                            withContext(Dispatchers.Main) {

                                allMeasurements.value = measurements

                                android.widget.Toast.makeText(
                                    context,
                                    "API取得成功: ${measurements.size}件",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()

                                if (measurements.isNotEmpty()) {

                                    val first = measurements.first()

                                    map.cameraPosition =
                                        CameraPosition.Builder()
                                            .target(
                                                LatLng(
                                                    first.latitude,
                                                    first.longitude
                                                )
                                            )
                                            .zoom(14.0)
                                            .build()
                                }
                            }

                        } catch (e: Exception) {

                            e.printStackTrace()

                            withContext(Dispatchers.Main) {

                                android.widget.Toast.makeText(
                                    context,
                                    "API取得失敗: ${e.javaClass.simpleName}\n${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    // 年月が変更されたらマーカーを更新
    LaunchedEffect(
        allMeasurements.value,
        selectedYear.value,
        selectedMonth.value,
        mapState.value
    ) {

        val map = mapState.value ?: return@LaunchedEffect

        // 現在のマーカーを削除
        markers.forEach { marker ->
            map.removeAnnotation(marker)
        }

        markers.clear()
        markerMeasurements.clear()

        // フィルター
        val filteredMeasurements =
            allMeasurements.value.filter { measurement ->

                val year =
                    measurement.measuredAt.substring(0, 4)

                val month =
                    measurement.measuredAt.substring(5, 7)

                val yearMatches =
                    selectedYear.value == "すべて" ||
                            year == selectedYear.value

                val monthMatches =
                    selectedMonth.value == "すべて" ||
                            month == selectedMonth.value

                yearMatches && monthMatches
            }

        // フィルター後のデータからマーカーを作成
        filteredMeasurements.forEach { measurement ->

            val position = LatLng(
                measurement.latitude,
                measurement.longitude
            )

            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("水温測定地点")
                    .snippet(
                        "${measurement.measurementType} / " +
                                "${measurement.readings.size}件"
                    )
            )

            markers.add(marker)

            markerMeasurements[
                marker.id.toLong()
            ] = measurement
        }

        // 現在選択しているデータが
        // フィルター対象外になった場合は閉じる
        if (
            selectedMeasurement.value != null &&
            selectedMeasurement.value !in filteredMeasurements
        ) {
            selectedMeasurement.value = null
        }
    }

    // MapViewのライフサイクル処理
    DisposableEffect(
        lifecycleOwner,
        mapView
    ) {

        val observer =
            LifecycleEventObserver { _, event ->

                when (event) {

                    Lifecycle.Event.ON_START ->
                        mapView.onStart()

                    Lifecycle.Event.ON_RESUME ->
                        mapView.onResume()

                    Lifecycle.Event.ON_PAUSE ->
                        mapView.onPause()

                    Lifecycle.Event.ON_STOP ->
                        mapView.onStop()

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        ) {
            mapView.onStart()
        }

        if (
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
        ) {
            mapView.onResume()
        }

        onDispose {

            lifecycleOwner.lifecycle.removeObserver(
                observer
            )

            mapView.onDestroy()
        }
    }

    // 画面
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // ① 地図
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // ② 年月フィルター
        Surface(
            color = Color.White.copy(alpha = 0.95f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 240.dp)
        ) {

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(8.dp)
            ) {

                // 年
                var yearExpanded by remember {
                    mutableStateOf(false)
                }

                Box {

                    Text(
                        text = "年：${selectedYear.value}",
                        modifier = Modifier
                            .clickable {
                                yearExpanded = true
                            }
                            .padding(8.dp)
                    )

                    DropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = {
                            yearExpanded = false
                        }
                    ) {

                        DropdownMenuItem(
                            text = {
                                Text("すべて")
                            },
                            onClick = {

                                selectedYear.value =
                                    "すべて"

                                yearExpanded = false
                            }
                        )

                        allMeasurements.value
                            .map {
                                it.measuredAt
                                    .substring(0, 4)
                            }
                            .distinct()
                            .sortedDescending()
                            .forEach { year ->

                                DropdownMenuItem(
                                    text = {
                                        Text(year)
                                    },
                                    onClick = {

                                        selectedYear.value =
                                            year

                                        yearExpanded = false
                                    }
                                )
                            }
                    }
                }

                // 月
                var monthExpanded by remember {
                    mutableStateOf(false)
                }

                Box {

                    Text(
                        text = "月：${
                            if (
                                selectedMonth.value ==
                                "すべて"
                            ) {
                                "すべて"
                            } else {
                                "${selectedMonth.value.toInt()}月"
                            }
                        }",
                        modifier = Modifier
                            .clickable {
                                monthExpanded = true
                            }
                            .padding(8.dp)
                    )

                    DropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = {
                            monthExpanded = false
                        }
                    ) {

                        DropdownMenuItem(
                            text = {
                                Text("すべて")
                            },
                            onClick = {

                                selectedMonth.value =
                                    "すべて"

                                monthExpanded = false
                            }
                        )

                        (1..12).forEach { month ->

                            DropdownMenuItem(
                                text = {
                                    Text("${month}月")
                                },
                                onClick = {

                                    selectedMonth.value =
                                        month
                                            .toString()
                                            .padStart(2, '0')

                                    monthExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // ③ 測定登録ボタン
        Surface(
            color = Color.White.copy(alpha = 0.95f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 8.dp,
                    bottom = 60.dp
                )
                .clickable {

                    val now = java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date())

                    registrationMeasuredAt.value = now

                    // 新しい登録なので、以前の位置情報をリセット
                    registrationLatitude.value = ""
                    registrationLongitude.value = ""
                    registrationAccuracy.value = null

                    registrationAirTemperature.value = ""
                    registrationReadings.clear()
                    registrationReadings.add(
                        Pair("0.0", "")
                    )
                    selectedCsvUri.value = null

                    showRegistration.value = true
                }
        ) {
            Text(
                text = "＋",
                modifier = Modifier.padding(14.dp)
            )
        }

        // ④ 国土地理院タイル表示
        Surface(
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clickable {

                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://maps.gsi.go.jp/development/ichiran.html"
                            )
                        )
                    )
                }
        ) {

            Text(
                text = "地理院タイル（国土地理院）",
                modifier = Modifier.padding(8.dp)
            )
        }

        // ⑤ 測定データ詳細
        selectedMeasurement.value?.let { measurement ->

            Surface(
                color = Color.White.copy(alpha = 0.95f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(
                            rememberScrollState()
                        )
                ) {

                    Text(
                        text = "水温測定地点"
                    )

                    Text(
                        text =
                            "日時：${measurement.measuredAt}"
                    )

                    Text(
                        text =
                            "種類：${measurement.measurementType}"
                    )

                    Text(
                        text =
                            "位置：${measurement.latitude}, " +
                                    "${measurement.longitude}"
                    )

                    Text(
                        text =
                            "気温：${
                                measurement.airTemperatureC?.let {
                                    "$it ℃"
                                } ?: "未記録"
                            }"
                    )

                    Text(
                        text =
                            "読み取り数：${measurement.readings.size}件"
                    )

                    measurement.readings.forEach { reading ->

                        Text(
                            text =
                                "深度 ${reading.depthM} m　" +
                                        "水温 ${reading.waterTemperatureC} ℃"
                        )
                    }

                    if (measurement.measurementType == "profile") {
                        Button(
                            onClick = {
                                showProfile.value = true
                            }
                        ) {
                            Text("水温プロファイルを見る")
                        }
                    }
                }
            }
        }

        //テストボタン
        Button(
            onClick = {
                val fineGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val coarseGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fineGranted || coarseGranted) {
                    getCurrentLocation(
                        fusedLocationClient = fusedLocationClient,
                        onLocationReceived = { location ->
                            currentLatitude.value = location.latitude
                            currentLongitude.value = location.longitude
                            currentAccuracy.value = location.accuracy
                            locationMessage.value = "位置情報取得成功"
                        },
                        onError = {
                            locationMessage.value = "位置情報の取得に失敗しました"
                        }
                    )
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        ) {
            Text("現在地を取得")
        }

        // テストの結果表示
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
        ) {
            Text(locationMessage.value)

            currentLatitude.value?.let {
                Text("緯度: $it")
            }

            currentLongitude.value?.let {
                Text("経度: $it")
            }

            currentAccuracy.value?.let {
                Text("水平精度: %.1f m".format(it))
            }
        }

        if (showProfile.value) {
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {

                    selectedMeasurement.value?.let { measurement ->

                        val readings = measurement.readings.sortedBy {
                            it.depthM
                        }

                        val maxDepth = readings.maxOfOrNull {
                            it.depthM
                        } ?: 1.0

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {

                            // =========================
                            // ヘッダー
                            // =========================

                            Text(
                                text = "水温プロファイル"
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = "日時：${measurement.measuredAt}"
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            Text(
                                text =
                                    "気温：${
                                        measurement.airTemperatureC?.let {
                                            "$it ℃"
                                        } ?: "未記録"
                                    }"
                            )

                            Spacer(
                                modifier = Modifier.height(16.dp)
                            )

                            // =========================
                            // プロファイル領域
                            // =========================

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(readings) {
                                            detectTapGestures { offset ->

                                                // タップ位置から深度を計算
                                                val tappedDepth =
                                                    (offset.y / size.height) * maxDepth

                                                // 最も近い実測データを探す
                                                val nearestReading =
                                                    readings.minByOrNull { reading ->
                                                        kotlin.math.abs(
                                                            reading.depthM - tappedDepth
                                                        )
                                                    }

                                                // 選択した実測値を保存
                                                nearestReading?.let { reading ->

                                                    selectedProfileDepth.value =
                                                        reading.depthM

                                                    selectedProfileTemperature.value =
                                                        reading.waterTemperatureC
                                                }
                                            }
                                        }
                                ) {
                                    // -----------------------------
                                    // レイアウト
                                    // -----------------------------
                                    val axisX = 32.dp.toPx()
                                    val profileStartX = 72.dp.toPx()
                                    val rightMargin = 16.dp.toPx()

                                    val smallTickLength = 16.dp.toPx()
                                    val middleTickLength = 28.dp.toPx()

                                    // -----------------------------
                                    // 縦軸
                                    // -----------------------------
                                    drawLine(
                                        color = Color.Black,
                                        start = Offset(axisX, 0f),
                                        end = Offset(axisX, size.height),
                                        strokeWidth = 3.dp.toPx()
                                    )

                                    // -----------------------------
                                    // 目盛り
                                    // 水面～底を10等分
                                    // -----------------------------
                                    for (i in 0..10) {
                                        val y = i / 10f * size.height

                                        val tickLength =
                                            if (i == 5) {
                                                middleTickLength
                                            } else {
                                                smallTickLength
                                            }

                                        drawLine(
                                            color = Color.Black,
                                            start = Offset(axisX, y),
                                            end = Offset(axisX + tickLength, y),
                                            strokeWidth = 3.dp.toPx()
                                        )
                                    }

                                    // -----------------------------
                                    // 水温プロファイル
                                    // -----------------------------
                                    readings.zipWithNext().forEach { (upper, lower) ->

                                        val upperY =
                                            (upper.depthM / maxDepth)
                                                .toFloat() * size.height

                                        val lowerY =
                                            (lower.depthM / maxDepth)
                                                .toFloat() * size.height

                                        val upperColor =
                                            waterTemperatureColor(
                                                upper.waterTemperatureC
                                            )

                                        val lowerColor =
                                            waterTemperatureColor(
                                                lower.waterTemperatureC
                                            )

                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    upperColor,
                                                    lowerColor
                                                ),
                                                startY = upperY,
                                                endY = lowerY
                                            ),
                                            topLeft = Offset(
                                                profileStartX,
                                                upperY
                                            ),
                                            size = Size(
                                                width = size.width -
                                                        profileStartX -
                                                        rightMargin,
                                                height = lowerY - upperY
                                            )
                                        )
                                    }

                                    // -----------------------------
                                    // 実測点
                                    // -----------------------------
                                    readings.forEach { reading ->

                                        val y =
                                            (reading.depthM / maxDepth)
                                                .toFloat() * size.height

                                        drawLine(
                                            color = waterTemperatureColor(
                                                reading.waterTemperatureC
                                            ),
                                            start = Offset(
                                                profileStartX,
                                                y
                                            ),
                                            end = Offset(
                                                size.width - rightMargin,
                                                y
                                            ),
                                            strokeWidth = 4.dp.toPx()
                                        )
                                    }
                                }

                                // -----------------------------
                                // 水面
                                // -----------------------------
                                Surface(
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 76.dp)
                                ) {
                                    Text(
                                        text = "水面",
                                        modifier = Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                }

                                // -----------------------------
                                // 底・最深深度
                                // -----------------------------
                                Surface(
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 76.dp)
                                ) {
                                    Text(
                                        text = "底　${maxDepth} m",
                                        modifier = Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                }
                                // -----------------------------
                                // 選択された実測値
                                // -----------------------------
                                selectedProfileDepth.value?.let { depth ->

                                    val temperature =
                                        selectedProfileTemperature.value

                                    Surface(
                                        color = Color.White.copy(alpha = 0.95f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(
                                                top = 16.dp,
                                                end = 32.dp
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Text(
                                                text = "深度：${depth} m"
                                            )

                                            Text(
                                                text = "水温：${temperature} ℃"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =========================
                    // 閉じるボタン
                    // =========================

                    Text(
                        text = "×",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable {
                                showProfile.value = false
                            }
                            .padding(16.dp)
                    )
                }
            }
        }

        // =========================
        // 測定登録画面
        // =========================

        if (showRegistration.value) {

            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxSize()
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    // -------------------------
                    // ヘッダー
                    // -------------------------

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {

                        Text(
                            text = "測定登録",
                            modifier = Modifier.align(Alignment.CenterStart)
                        )

                        IconButton(
                            onClick = {
                                showRegistration.value = false
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("×")
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "測定日時"
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    OutlinedTextField(
                        value = registrationMeasuredAt.value,
                        onValueChange = {
                            registrationMeasuredAt.value = it
                        },
                        label = {
                            Text("測定日時")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "測定位置"
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {

                            val hasFineLocation =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                            val hasCoarseLocation =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                            if (hasFineLocation || hasCoarseLocation) {

                                getCurrentLocation(
                                    fusedLocationClient = fusedLocationClient,
                                    onLocationReceived = { location ->

                                        registrationLatitude.value =
                                            location.latitude.toString()

                                        registrationLongitude.value =
                                            location.longitude.toString()

                                        registrationAccuracy.value =
                                            location.accuracy
                                    },
                                    onError = {
                                        // 今回は何もしない
                                    }
                                )

                            } else {

                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("現在地を取得")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    if (registrationLatitude.value.isNotBlank() &&
                        registrationLongitude.value.isNotBlank()
                    ){

                        OutlinedTextField(
                            value = registrationLatitude.value,
                            onValueChange = {
                                registrationLatitude.value = it
                            },
                            label = {
                                Text("緯度")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedTextField(
                            value = registrationLongitude.value,
                            onValueChange = {
                                registrationLongitude.value = it
                            },
                            label = {
                                Text("経度")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        registrationAccuracy.value?.let { accuracy ->

                            Text(
                                text = "水平精度: %.1f m".format(
                                    accuracy
                                )
                            )
                        }

                    } else {

                        Text(
                            text = "位置情報が取得されていません"
                        )
                    }

                    // -------------------------
                    // 気温データ
                    // -------------------------
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = registrationAirTemperature.value,
                        onValueChange = {
                            registrationAirTemperature.value = it
                        },
                        label = {
                            Text("気温（℃）")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // -------------------------
                    // 手動測定データ
                    // -------------------------

                    Text(
                        text = "水温測定データ"
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    registrationReadings.forEachIndexed { index, reading ->

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            OutlinedTextField(
                                value = reading.first,
                                onValueChange = { value ->

                                    registrationReadings[index] =
                                        Pair(value, reading.second)
                                },
                                label = {
                                    Text("深度（m）")
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = reading.second,
                                onValueChange = { value ->

                                    registrationReadings[index] =
                                        Pair(reading.first, value)
                                },
                                label = {
                                    Text("水温（℃）")
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )
                    }

                    Button(
                        onClick = {

                            registrationReadings.add(
                                Pair("", "")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("データを追加")
                    }

                    Spacer(
                        modifier = Modifier.height(32.dp)
                    )

                    HorizontalDivider()

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    if (registrationMessage.value.isNotBlank()) {

                        Text(
                            text = registrationMessage.value
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )
                    }

                    // -------------------------
                    // 登録ボタン
                    // -------------------------

                    Button(
                        onClick = {

                            // ---------------------------------
                            // 入力チェック
                            // ---------------------------------

                            val latitude =
                                registrationLatitude.value.toDoubleOrNull()

                            val longitude =
                                registrationLongitude.value.toDoubleOrNull()

                            val airTemperature =
                                registrationAirTemperature.value.toDoubleOrNull()

                            val readings = registrationReadings.mapIndexed { index, reading ->

                                val depth = reading.first.toDoubleOrNull()
                                val temperature = reading.second.toDoubleOrNull()

                                Pair(depth, temperature)
                            }

                            when {

                                registrationMeasuredAt.value.isBlank() -> {
                                    registrationMessage.value =
                                        "測定日時を入力してください"
                                }

                                latitude == null ||
                                        latitude !in -90.0..90.0 -> {
                                    registrationMessage.value =
                                        "緯度を正しく入力してください"
                                }

                                longitude == null ||
                                        longitude !in -180.0..180.0 -> {
                                    registrationMessage.value =
                                        "経度を正しく入力してください"
                                }

                                airTemperature == null ||
                                        airTemperature !in -50.0..60.0 -> {
                                    registrationMessage.value =
                                        "気温を正しく入力してください"
                                }

                                readings.any { reading ->
                                    val depth = reading.first

                                    depth == null ||
                                            depth !in 0.0..10000.0
                                } -> {
                                    registrationMessage.value =
                                        "深度を正しく入力してください"
                                }

                                readings.any { reading ->
                                    val temperature = reading.second

                                    temperature == null ||
                                            temperature !in -10.0..60.0
                                } -> {
                                    registrationMessage.value =
                                        "水温を正しく入力してください"
                                }

                                else ->  {

                                    // ---------------------------------
                                    // 登録データ作成
                                    // ---------------------------------

                                    val measurement = MeasurementCreate(

                                        measuredAt =
                                            registrationMeasuredAt.value,

                                        latitude =
                                            latitude,

                                        longitude =
                                            longitude,

                                        airTemperatureC =
                                            airTemperature,

                                        positionSource =
                                            "smartphone",

                                        positioningMode =
                                            null,

                                        horizontalAccuracyM =
                                            registrationAccuracy.value?.toDouble(),

                                        verticalAccuracyM =
                                            null,

                                        satelliteCount =
                                            null,

                                        deviceName =
                                            "Android smartphone",

                                        measurementType =
                                            if (readings.size == 1) {
                                                "spot"
                                            } else {
                                                "profile"
                                            },

                                        readings =
                                            readings.map { reading ->

                                                val depth = reading.first ?: 0.0
                                                val temperature = reading.second ?: 0.0

                                                ReadingCreate(
                                                    depthM = depth,
                                                    depthUncertaintyM = null,
                                                    waterTemperatureC = temperature
                                                )
                                            }
                                    )

                                    // ---------------------------------
                                    // API登録
                                    // ---------------------------------

                                    isRegistering.value = true
                                    registrationMessage.value =
                                        "登録しています..."

                                    CoroutineScope(Dispatchers.IO).launch {

                                        try {

                                            val registeredMeasurement =
                                                RetrofitClient.apiService
                                                    .createMeasurement(
                                                        measurement
                                                    )

                                            // 最新データを再取得
                                            val measurements =
                                                RetrofitClient.apiService
                                                    .getMeasurements()

                                            withContext(Dispatchers.Main) {

                                                allMeasurements.value =
                                                    measurements

                                                isRegistering.value = false

                                                registrationMessage.value =
                                                    "登録成功"

                                                android.widget.Toast.makeText(
                                                    context,
                                                    "測定データを登録しました",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()

                                                // 登録画面を閉じる
                                                showRegistration.value =
                                                    false
                                            }

                                        } catch (e: Exception) {

                                            e.printStackTrace()

                                            withContext(Dispatchers.Main) {

                                                isRegistering.value = false

                                                registrationMessage.value =
                                                    "登録失敗: ${e.message}"
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isRegistering.value,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text =
                                if (isRegistering.value) {
                                    "登録中..."
                                } else {
                                    "登録"
                                }
                        )
                    }
                }
            }
        }
    }
}

private const val GSI_STYLE = """
{
  "version": 8,
  "sources": {
    "gsi-standard": {
      "type": "raster",
      "tiles": [
        "https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "minzoom": 5,
      "maxzoom": 18
    }
  },
  "layers": [
    {
      "id": "gsi-standard-layer",
      "type": "raster",
      "source": "gsi-standard"
    }
  ]
}
"""

fun waterTemperatureColor(
    temperature: Double
): Color {

    val temp = temperature.coerceIn(0.0, 33.0)

    return when {
        temp <= 5.0 -> {
            // 紺 → 青
            val ratio = (temp / 5.0).toFloat()

            Color(
                red = 0f,
                green = (50f + 50f * ratio) / 255f,
                blue = (120f + 135f * ratio) / 255f
            )
        }

        temp <= 10.0 -> {
            // 青 → 水色
            val ratio = ((temp - 5.0) / 5.0).toFloat()

            Color(
                red = 0f,
                green = (100f + 155f * ratio) / 255f,
                blue = 1f
            )
        }

        temp <= 15.0 -> {
            // 水色 → シアン
            val ratio = ((temp - 10.0) / 5.0).toFloat()

            Color(
                red = 0f,
                green = 1f,
                blue = 1f - 0.3f * ratio
            )
        }

        temp <= 20.0 -> {
            // シアン → 緑
            val ratio = ((temp - 15.0) / 5.0).toFloat()

            Color(
                red = 0f,
                green = 1f,
                blue = 0.7f - 0.7f * ratio
            )
        }

        temp <= 25.0 -> {
            // 緑 → 黄
            val ratio = ((temp - 20.0) / 5.0).toFloat()

            Color(
                red = ratio,
                green = 1f,
                blue = 0f
            )
        }

        temp <= 30.0 -> {
            // 黄 → オレンジ
            val ratio = ((temp - 25.0) / 5.0).toFloat()

            Color(
                red = 1f,
                green = 1f - 0.5f * ratio,
                blue = 0f
            )
        }

        else -> {
            // オレンジ → 赤
            val ratio = ((temp - 30.0) / 3.0).toFloat()

            Color(
                red = 1f,
                green = 0.5f - 0.5f * ratio,
                blue = 0f
            )
        }
    }
}

@SuppressLint("MissingPermission")
fun getCurrentLocation(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationReceived: (Location) -> Unit,
    onError: () -> Unit
) {
    val cancellationTokenSource = CancellationTokenSource()

    fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationTokenSource.token
    ).addOnSuccessListener { location ->
        if (location != null) {
            onLocationReceived(location)
        } else {
            onError()
        }
    }.addOnFailureListener {
        onError()
    }
}