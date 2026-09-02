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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.gsitilemap.api.RetrofitClient



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

    var message by remember {
        mutableStateOf("サーバーに接続しています...")
    }

    LaunchedEffect(Unit) {
        try {
            val measurements = RetrofitClient.apiService.getMeasurements()

            message = buildString {
                append("取得成功：${measurements.size}件\n\n")

                measurements.forEach { measurement ->
                    append("ID: ${measurement.id}\n")
                    append("日時: ${measurement.measuredAt}\n")
                    append("位置: ${measurement.latitude}, ${measurement.longitude}\n")
                    append("種類: ${measurement.measurementType}\n")
                    append("読み取り数: ${measurement.readings.size}\n")

                    measurement.readings.forEach { reading ->
                        append(
                            "  深度: ${reading.depthM} m / " +
                                    "水温: ${reading.waterTemperatureC} ℃\n"
                        )
                    }

                    append("\n")
                }
            }
        } catch (e: Exception) {
            message = "取得失敗：${e.message}"
        }
    }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(Style.Builder().fromJson(GSI_STYLE)) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(35.6812, 139.7671)) // 東京駅付近
                        .zoom(12.0)
                        .build()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp)
            )
        }

        Surface(
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clickable {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://maps.gsi.go.jp/development/ichiran.html")
                        )
                    )
                }
        ) {
            Text(
                text = "地理院タイル（国土地理院）",
                modifier = Modifier.padding(8.dp)
            )
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