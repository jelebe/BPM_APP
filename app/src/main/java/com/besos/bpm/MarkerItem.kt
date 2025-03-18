package com.besos.bpm

import com.besos.bpm.LatLngModel // Importa LatLngModel desde MarkerModel.kt

data class MarkerItem(
    val imageUrl: String = "",
    val description: String = "",
    val date: String = "",
    val latLng: LatLngModel? = null // Usa LatLngModel aquí
)