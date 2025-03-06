package com.besos.bpm

import android.view.View
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow

// Subclase personalizada de InfoWindow
class CustomInfoWindow(private val view: View, private val mapView: MapView) : InfoWindow(view, mapView) {

    init {
        // Configurar el ancla del InfoWindow al inicializarse
        //anchor = floatArrayOf(0.5f, 1.0f) // Centrar el InfoWindow sobre el marcador
    }

    // Sobrescribir onClose para actualizar el mapa cuando se cierre el InfoWindow
    override fun onClose() {
        mapView.post { mapView.invalidate() } // Actualizar el mapa después de cerrar
    }

    // Sobrescribir onOpen para actualizar el mapa cuando se abra el InfoWindow
    override fun onOpen(item: Any?) {
        mapView.post { mapView.invalidate() } // Actualizar el mapa después de abrir
    }
}
