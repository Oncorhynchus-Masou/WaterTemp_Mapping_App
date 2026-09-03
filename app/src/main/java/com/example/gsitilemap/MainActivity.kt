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

    // マーカーとMeasurementの対応表
    val markerMeasurements = remember {
        mutableMapOf<Long, Measurement>()
    }
    val mapView = remember {

        MapView(context).apply {

            getMapAsync { map ->

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
                        true
                    }

                    // FastAPIからデータ取得
                    CoroutineScope(Dispatchers.IO).launch {

                        try {

                            val measurements =
                                RetrofitClient.apiService
                                    .getMeasurements()

                            withContext(Dispatchers.Main) {

                                // 取得件数を確認
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

                                    measurements.forEach { measurement ->

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

                                        // マーカーと測定データを対応付ける
                                        markerMeasurements[marker.id.toLong()] = measurement
                                    }
                                }
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()

                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "API取得失敗: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
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

        // 地図
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // 国土地理院タイル表示
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

        // 測定データが選択されている場合だけ詳細表示
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
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(
                        text = "水温測定地点"
                    )

                    Text(
                        text = "日時：${measurement.measuredAt}"
                    )

                    Text(
                        text = "種類：${measurement.measurementType}"
                    )

                    Text(
                        text =
                            "位置：${measurement.latitude}, " +
                                    "${measurement.longitude}"
                    )

                    Text(
                        text = "読み取り数：${measurement.readings.size}件"
                    )

                    measurement.readings.forEach { reading ->

                        Text(
                            text =
                                "深度 ${reading.depthM} m　" +
                                        "水温 ${reading.waterTemperatureC} ℃"
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