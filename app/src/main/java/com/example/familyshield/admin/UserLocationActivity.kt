package com.example.familyshield.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.familyshield.databinding.ActivityUserLocationBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class UserLocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserLocationBinding
    private lateinit var mapView: MapView

    private var latitude = 0.0
    private var longitude = 0.0
    private var userName = "User"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 REQUIRED for osmdroid
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osm_pref", MODE_PRIVATE)
        )

        binding = ActivityUserLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        latitude = intent.getDoubleExtra("latitude", 0.0)
        longitude = intent.getDoubleExtra("longitude", 0.0)
        userName = intent.getStringExtra("userName") ?: "User"

        mapView = binding.osmMapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)

        val userPoint = GeoPoint(latitude, longitude)
        mapView.controller.setCenter(userPoint)

        val marker = Marker(mapView)
        marker.position = userPoint
        marker.title = userName
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        mapView.overlays.add(marker)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }
}
