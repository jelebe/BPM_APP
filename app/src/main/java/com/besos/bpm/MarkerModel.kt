package com.besos.bpm

// Modelo de datos para los marcadores
data class MarkerModel(
    var date: String = "", // Fecha del marcador
    var description: String = "", // Descripción del marcador
    var image: String = "", // URL de la imagen almacenada en Firebase Storage
    var latLng: LatLngModel = LatLngModel() // Coordenadas geográficas
)

// Modelo anidado para las coordenadas geográficas
data class LatLngModel(
    var lat: Double = 0.0, // Latitud
    var lng: Double = 0.0  // Longitud
)