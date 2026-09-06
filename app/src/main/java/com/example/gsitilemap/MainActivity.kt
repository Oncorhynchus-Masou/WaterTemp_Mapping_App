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
import com.example.gsitilemap.model.Measurement
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // 現在選択されている測定データ
    val selectedMeasurement = remember {
        mutableStateOf<Measurement?>(null)
    }

    // プロファイル表示用の状態を追加
    val showProfile = remember {
        mutableStateOf(false)
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

        // ③ 国土地理院タイル表示
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

        // ④ 測定データ詳細
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

        if (showProfile.value) {
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                rememberScrollState()
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "水温プロファイル"
                        )

                        selectedMeasurement.value?.let { measurement ->

                            Text(
                                text = "日時：${measurement.measuredAt}"
                            )

                            Text(
                                text =
                                    "気温：${
                                        measurement.airTemperatureC?.let {
                                            "$it ℃"
                                        } ?: "未記録"
                                    }"
                            )
                            val readings = measurement.readings.sortedBy {
                                it.depthM
                            }

                            val maxDepth = readings.maxOfOrNull {
                                it.depthM
                            } ?: 1.0

                            // プロファイル全体の高さ
                            val profileHeight = (maxDepth * 80).dp

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(profileHeight)
                            ) {

                                // 線を描画
                                Canvas(
                                    modifier = Modifier.fillMaxSize()
                                ) {

                                    val xLine = 40f

                                    // 水面から地底までの縦線
                                    drawLine(
                                        color = Color.Black,
                                        start = Offset(xLine, 0f),
                                        end = Offset(xLine, size.height),
                                        strokeWidth = 4f
                                    )

                                    readings.forEach { reading ->

                                        val y =
                                            (reading.depthM / maxDepth).toFloat() *
                                                    size.height

                                        // 測定位置の横線
                                        drawLine(
                                            color = Color.Black,
                                            start = Offset(xLine, y),
                                            end = Offset(size.width - 40f, y),
                                            strokeWidth = 3f
                                        )
                                    }
                                }

                                // 深度・水温を表示
                                readings.forEach { reading ->

                                    val y =
                                        (reading.depthM / maxDepth).toFloat() *
                                                profileHeight.value

                                    Text(
                                        text =
                                            "深度 ${reading.depthM} m　" +
                                                    "水温 ${reading.waterTemperatureC} ℃",

                                        modifier = Modifier
                                            .padding(start = 50.dp)
                                            .offset(y = y.dp)
                                    )
                                }
                            }
                        }
                    }

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