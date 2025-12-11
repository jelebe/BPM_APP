package com.besos.bpm

data class MarkerItem(
    val imageUrl: String,
    val description: String,
    val date: String,
    val latLng: LatLngModel?,
    val city: String?,
    val country: String?,
    val longText: String = "", // Texto largo para la parte trasera
    var isShowingBack: Boolean = false, // Estado para controlar qué cara mostrar
    var isImageLoaded: Boolean = false
)