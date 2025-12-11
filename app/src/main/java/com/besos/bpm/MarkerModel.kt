package com.besos.bpm

// Modelo de datos para los marcadores
data class MarkerModel(
    var date: String = "", // Fecha del marcador
    var description: String = "", // Descripción del marcador (frente)
    var image: String = "", // URL de la imagen almacenada en Firebase Storage
    var latLng: LatLngModel = LatLngModel(), // Coordenadas geográficas
    var city: String? = null, // Ciudad
    var country: String? = null, // País
    var longText: String = "" // NUEVO: Texto largo para la parte trasera
)

// Modelo anidado para las coordenadas geográficas
data class LatLngModel(
    val lat: Double = 0.0, // Latitud
    val lng: Double = 0.0  // Longitud
)