package com.besos.bpm

import android.app.Activity
import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.LruCache
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.yalantis.ucrop.UCrop
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapAdapter
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import retrofit2.Call
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapFragment : Fragment(R.layout.map_fragment) {

    // Vistas del layout
    private lateinit var mapView: MapView
    private lateinit var createMarkerButton: FloatingActionButton

    // Firebase
    private lateinit var databaseReference: DatabaseReference
    private lateinit var storageReference: StorageReference

    // Listas para gestión de marcadores
    private val allMarkers = mutableListOf<MarkerData>()
    private val visibleMarkers = mutableListOf<Marker>()
    private val clusterMarkers = mutableListOf<Marker>()

    // Variables para el estado actual
    private var currentZoomLevel = 14.0
    private var lastBoundingBox: BoundingBox? = null

    // Variables para manejo de imágenes y diálogos
    private var selectedImageUri: Uri? = null
    private var currentCreateMarkerDialogView: View? = null
    private var createMarkerDialog: AlertDialog? = null
    private var selectLocationDialog: AlertDialog? = null
    private lateinit var progressDialog: ProgressDialog

    // Cache para polaroids
    private lateinit var polaroidCache: LruCache<String, Bitmap>
    private val polaroidWidth = 120
    private val polaroidHeight = 150

    // Data class para almacenar información de marcadores
    data class MarkerData(
        val latitude: Double,
        val longitude: Double,
        val date: String,
        val description: String,
        val imageUrl: String
    )

    // UCrop launcher
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            selectedImageUri = resultUri
            currentCreateMarkerDialogView?.findViewById<ImageView>(R.id.imagePreview)?.let { imageView ->
                Glide.with(this).load(resultUri).into(imageView)
            }
        } else {
            Toast.makeText(context, "Recorte cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    // 📸 Selección desde galería
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            context?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                val fileSizeInBytes = inputStream.available()
                val fileSizeInMB = fileSizeInBytes / (1024 * 1024).toDouble()
                if (fileSizeInMB > 9) {
                    Toast.makeText(context, "El tamaño máximo permitido es de 9 MB", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
            }
            startCrop(uri)
        } else {
            Toast.makeText(context, "No se seleccionó ninguna imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para tomar una foto desde la cámara
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val uri = bitmapToUri(bitmap)
            startCrop(uri)
        } else {
            Toast.makeText(context, "No se tomó ninguna foto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCrop(uri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "cropped.jpg"))
        val uCrop = UCrop.of(uri, destinationUri)
            .withAspectRatio(7f, 6f)
            .withMaxResultSize(1400, 1200)
        cropLauncher.launch(uCrop.getIntent(requireContext()))
    }

    private fun bitmapToUri(bitmap: Bitmap): Uri {
        val file = File.createTempFile("temp_image", ".jpg", context?.cacheDir)
        file.outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
        return Uri.fromFile(file)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        // Inicializar cache de polaroids
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Usar 1/8 de la memoria disponible
        polaroidCache = LruCache<String, Bitmap>(cacheSize)

        // Inicializar las vistas del fragmento
        mapView = view.findViewById(R.id.mapview)
        createMarkerButton = view.findViewById(R.id.createMarkerButton)

        // Configurar OSMDroid
        context?.let { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = "com.besos.bpm/1.0"
                osmdroidBasePath = File(ctx.filesDir, "osmdroid")
                osmdroidTileCache = File(ctx.externalCacheDir, "tiles")
            }
        }

        // Configuración del MapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setTilesScaledToDpi(false)
        configureMapLimits()

        // Centrar el mapa en Madrid por defecto
        val madridGeoPoint = GeoPoint(40.4168, -3.7038)
        mapView.controller.setCenter(madridGeoPoint)
        mapView.controller.setZoom(14.0)

        // Inicializar Firebase
        databaseReference = FirebaseDatabase.getInstance().reference.child("markers")
        storageReference = FirebaseStorage.getInstance().reference.child("images/")

        // Cargar los marcadores desde Firebase
        loadMarkersFromDatabase()

        // Actualizar los marcadores al cambiar el zoom o desplazarse por el mapa con delay de 150ms
        mapView.addMapListener(DelayedMapListener(object : MapAdapter() {
            override fun onZoom(event: ZoomEvent?): Boolean {
                val newZoom = mapView.zoomLevelDouble
                if (newZoom != currentZoomLevel) {
                    currentZoomLevel = newZoom
                    Log.d("MapZoom", "Nivel de zoom cambiado: $currentZoomLevel")
                    updateMapDisplay()
                }
                return true
            }

            override fun onScroll(event: ScrollEvent?): Boolean {
                // Actualizar siempre al desplazarse, incluso con mismo zoom
                Log.d("MapScroll", "Desplazamiento detectado, actualizando marcadores")
                updateMapDisplay()
                return true
            }
        }, 150))

        // Al pulsar el botón flotante se muestra el diálogo para seleccionar ubicación
        createMarkerButton.setOnClickListener { showSelectLocationDialog() }

        // Resaltar el marcador si se navega desde el FeedFragment
        arguments?.let { args ->
            val latitude = args.getDouble("latitude")
            val longitude = args.getDouble("longitude")
            if (latitude != 0.0 && longitude != 0.0) {
                highlightMarkerOnMap(latitude, longitude)
            }
        }
    }

    // Configuración de límites y niveles de zoom del MapView
    private fun configureMapLimits() {
        val boundingBox = BoundingBox(85.0, 180.0, -85.0, -180.0)
        mapView.setScrollableAreaLimitDouble(boundingBox)
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 20.0
    }

    // Carga inicial de marcadores desde Firebase
    private fun loadMarkersFromDatabase() {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allMarkers.clear()

                for (markerSnapshot in snapshot.children) {
                    val imageUrl = markerSnapshot.child("image").getValue(String::class.java) ?: ""
                    val description = markerSnapshot.child("description").getValue(String::class.java) ?: ""
                    val date = markerSnapshot.child("date").getValue(String::class.java) ?: ""
                    val lat = markerSnapshot.child("latlng/lat").getValue(Double::class.java) ?: 0.0
                    val lng = markerSnapshot.child("latlng/lng").getValue(Double::class.java) ?: 0.0

                    if (imageUrl.isNotEmpty()) {
                        allMarkers.add(MarkerData(lat, lng, date, description, imageUrl))
                    }
                }

                Log.d("MapFragment", "Cargados ${allMarkers.size} marcadores desde Firebase")
                updateMapDisplay()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error al cargar marcadores: ${error.message}")
            }
        })
    }

    // Actualizar la visualización del mapa basado en el zoom y posición actual
    private fun updateMapDisplay() {
        val currentBoundingBox = mapView.boundingBox
        val currentZoom = mapView.zoomLevelDouble

        // Solo actualizar si cambió el área visible o el zoom
        if (lastBoundingBox != currentBoundingBox || currentZoom != currentZoomLevel) {
            lastBoundingBox = currentBoundingBox
            currentZoomLevel = currentZoom

            Log.d("MapDisplay", "Zoom: $currentZoomLevel, " +
                    "Actualizando marcadores por cambio de área visible")

            // Limpiar marcadores existentes de manera más eficiente
            mapView.overlays.removeAll(visibleMarkers)
            visibleMarkers.clear()
            mapView.overlays.removeAll(clusterMarkers)
            clusterMarkers.clear()

            // Calcular el área visible actual con optimización
            val visibleMarkersData = allMarkers.filter { marker ->
                currentBoundingBox.contains(GeoPoint(marker.latitude, marker.longitude))
            }

            Log.d("MapDisplay", "Marcadores visibles: ${visibleMarkersData.size}")

            // Mostrar clusters en zoom bajo, marcadores individuales en zoom alto
            if (currentZoomLevel >= 14) {
                showIndividualMarkers(visibleMarkersData)
            } else {
                showClusters(visibleMarkersData)
            }

            mapView.invalidate()
        } else {
            Log.d("MapDisplay", "No se actualiza: misma área visible y mismo zoom")
        }
    }

    // Mostrar marcadores individuales con polaroids personalizadas
    private fun showIndividualMarkers(markersData: List<MarkerData>) {
        // Limitar la cantidad de marcadores para mejorar rendimiento
        val maxMarkersToShow = 50
        val markersToShow = if (markersData.size > maxMarkersToShow) {
            Log.d("Performance", "Demasiados marcadores (${markersData.size}). Mostrando solo $maxMarkersToShow")
            markersData.take(maxMarkersToShow)
        } else {
            markersData
        }

        markersToShow.forEach { data ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(data.latitude, data.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // Usar icono temporal mientras se carga la polaroid
                val placeholder = resources.getDrawable(R.drawable.ic_marker_icon, null)
                icon = placeholder

                title = data.description
                subDescription = data.date

                setOnMarkerClickListener { _, _ ->
                    showMarkerDialog(data.date, data.description, data.imageUrl)
                    true
                }
            }
            visibleMarkers.add(marker)
            mapView.overlays.add(marker)

            // Cargar la polaroid personalizada en segundo plano
            loadPolaroidForMarker(data.imageUrl, marker)
        }

        Log.d("MarkerCount", "Marcadores individuales mostrados: ${visibleMarkers.size}")
    }

    // Método para cargar y crear polaroids personalizadas
    private fun loadPolaroidForMarker(imageUrl: String, marker: Marker) {
        // Verificar si ya tenemos la miniatura en cache
        val cachedBitmap = polaroidCache.get(imageUrl)
        if (cachedBitmap != null) {
            marker.icon = BitmapDrawable(resources, cachedBitmap)
            mapView.invalidate()
            return
        }

        // Cargar la imagen en segundo plano y crear la polaroid
        Glide.with(requireContext())
            .asBitmap()
            .load(imageUrl)
            .override(polaroidWidth, polaroidHeight)
            .transform(CenterCrop(), RoundedCorners(8))
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // Crear la polaroid personalizada
                    val polaroidBitmap = createPolaroidBitmap(resource)

                    // Guardar en cache
                    polaroidCache.put(imageUrl, polaroidBitmap)

                    // Actualizar el marcador
                    marker.icon = BitmapDrawable(resources, polaroidBitmap)
                    mapView.invalidate()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // No hacer nada cuando se limpia la carga
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    // Usar icono por defecto si falla la carga
                    val defaultIcon = resources.getDrawable(R.drawable.ic_marker_icon, null)
                    marker.icon = defaultIcon
                }
            })
    }

    // Método para crear un bitmap con formato polaroid
    private fun createPolaroidBitmap(photoBitmap: Bitmap): Bitmap {
        // Crear un bitmap para la polaroid completa
        val polaroid = Bitmap.createBitmap(polaroidWidth, polaroidHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(polaroid)

        // Dibujar el fondo blanco de la polaroid
        val paint = Paint()
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, polaroidWidth.toFloat(), polaroidHeight.toFloat(), paint)

        // Dibujar un borde sutil
        paint.color = Color.argb(50, 0, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(1f, 1f, polaroidWidth.toFloat() - 1f, polaroidHeight.toFloat() - 1f, paint)

        // Calcular el área para la foto (dejando espacio en la parte inferior para el "marco")
        val photoAreaWidth = polaroidWidth - 16
        val photoAreaHeight = polaroidHeight - 40
        val photoLeft = 8
        val photoTop = 8

        // Dibujar la foto
        val scaledPhoto = Bitmap.createScaledBitmap(photoBitmap, photoAreaWidth, photoAreaHeight, true)
        canvas.drawBitmap(scaledPhoto, photoLeft.toFloat(), photoTop.toFloat(), null)

        // Dibujar una sombra sutil en la parte inferior para simular el marco
        val shadowPaint = Paint()
        val shadowHeight = 24f
        val shadowTop = polaroidHeight - shadowHeight - 8

        shadowPaint.shader = LinearGradient(
            0f, shadowTop,
            0f, polaroidHeight.toFloat(),
            Color.argb(30, 0, 0, 0), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(8f, shadowTop, polaroidWidth.toFloat() - 8f, polaroidHeight.toFloat() - 8f, shadowPaint)

        return polaroid
    }

    // Crear un marcador individual con polaroid
    private fun createIndividualMarker(data: MarkerData): Marker {
        val marker = Marker(mapView).apply {
            position = GeoPoint(data.latitude, data.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Usar icono temporal
            val placeholder = resources.getDrawable(R.drawable.ic_marker_icon, null)
            icon = placeholder

            title = data.description
            subDescription = data.date

            setOnMarkerClickListener { _, _ ->
                showMarkerDialog(data.date, data.description, data.imageUrl)
                true
            }
        }

        // Cargar la polaroid personalizada
        loadPolaroidForMarker(data.imageUrl, marker)

        return marker
    }

    // Mostrar clusters de marcadores
    private fun showClusters(markersData: List<MarkerData>) {
        val clusters = mutableMapOf<Pair<Int, Int>, MutableList<MarkerData>>()

        // Ajustar el tamaño de la cuadrícula según el zoom para mejor agrupación
        val gridSize = when {
            currentZoomLevel > 16.0 -> 0.3
            currentZoomLevel > 14.0 -> 0.5
            currentZoomLevel > 12.0 -> 0.7
            else -> 1.0
        }

        markersData.forEach { data ->
            val gridX = (data.latitude / gridSize).toInt()
            val gridY = (data.longitude / gridSize).toInt()
            val gridKey = Pair(gridX, gridY)

            if (!clusters.containsKey(gridKey)) {
                clusters[gridKey] = mutableListOf()
            }
            clusters[gridKey]?.add(data)
        }

        // Crear marcadores de cluster
        var clusterCount = 0
        var markersInClusters = 0

        clusters.forEach { (_, markersInCluster) ->
            if (markersInCluster.size >= 1) {
                clusterCount++
                markersInClusters += markersInCluster.size
                val centerLat = markersInCluster.map { it.latitude }.average()
                val centerLon = markersInCluster.map { it.longitude }.average()

                val clusterMarker = createClusterMarker(centerLat, centerLon, markersInCluster.size)
                clusterMarkers.add(clusterMarker)
                mapView.overlays.add(clusterMarker)
            } else {
                // Si solo hay un marcador en el cluster, mostrarlo individualmente
                markersInCluster.forEach { data ->
                    val marker = createIndividualMarker(data)
                    visibleMarkers.add(marker)
                    mapView.overlays.add(marker)
                }
            }
        }

        Log.d("ClusterInfo", "Clusters formados: $clusterCount, " +
                "Marcadores en clusters: $markersInClusters, " +
                "Marcadores individuales: ${visibleMarkers.size}")
    }

    // Crear un marcador de cluster
    private fun createClusterMarker(lat: Double, lon: Double, count: Int): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // Crear icono de cluster con el número de marcadores
            icon = BitmapDrawable(resources, createClusterIcon(count))

            title = "$count polaroids"
            setOnMarkerClickListener { _, _ ->
                // Al hacer clic en un cluster, acercarse a esa área
                mapView.controller.animateTo(GeoPoint(lat, lon), 15.0, 1000L)
                true
            }
        }
    }

    // Crear un icono de cluster
    private fun createClusterIcon(count: Int): Bitmap {
        val width = 100
        val height = 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dibujar círculo de fondo
        val circlePaint = Paint().apply {
            color = Color.parseColor("#ff6675")
            isAntiAlias = true
        }

        // Dibujar borde
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        // Dibujar texto
        val textPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // Dibujar círculo
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 3f
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        // Dibujar texto
        val text = if (count > 99) "99+" else count.toString()
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textHeight = textBounds.bottom - textBounds.top
        val textY = centerY + (textHeight / 2f) - textBounds.bottom

        canvas.drawText(text, centerX, textY, textPaint)

        return bitmap
    }

    // Muestra el diálogo con la información del marcador
    private fun showMarkerDialog(date: String, description: String, imageUrl: String) {
        val builder = AlertDialog.Builder(requireContext(), R.style.PolaroidDialogTheme)
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_marker_info, null)
        dialogView.findViewById<TextView>(R.id.markerDescription).text = description
        dialogView.findViewById<TextView>(R.id.markerDate).text = date
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_placeholder_image)
            .error(R.drawable.ic_placeholder_image)
            .into(dialogView.findViewById(R.id.marker_image))
        val dialog = builder.setView(dialogView).create()
        dialog.show()
    }

    // Muestra el diálogo para seleccionar la ubicación para crear un nuevo marcador
    private fun showSelectLocationDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_select_location, null)
        val selectLocationMapView = dialogView.findViewById<MapView>(R.id.selectLocationMapview)
        val currentCoordinates = dialogView.findViewById<TextView>(R.id.currentCoordinates)
        val selectLocationButton = dialogView.findViewById<Button>(R.id.selectLocationButton)

        selectLocationMapView.setTileSource(TileSourceFactory.MAPNIK)
        selectLocationMapView.setMultiTouchControls(true)
        selectLocationMapView.setTilesScaledToDpi(false)

        val madridGeoPoint = GeoPoint(40.4168, -3.7038)
        selectLocationMapView.controller.setCenter(madridGeoPoint)
        selectLocationMapView.controller.setZoom(14.0)

        val centerMarker = Marker(selectLocationMapView).apply {
            icon = resources.getDrawable(R.drawable.custom_blue_marker, null)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        selectLocationMapView.overlays.add(centerMarker)
        centerMarker.position = madridGeoPoint

        // Actualizar coordenadas iniciales
        updateCenterMarkerAndCoordinates(selectLocationMapView, centerMarker, currentCoordinates)

        selectLocationMapView.addMapListener(object : MapAdapter() {
            override fun onScroll(event: ScrollEvent?): Boolean {
                updateCenterMarkerAndCoordinates(selectLocationMapView, centerMarker, currentCoordinates)
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                Handler(Looper.getMainLooper()).postDelayed({
                    updateCenterMarkerAndCoordinates(selectLocationMapView, centerMarker, currentCoordinates)
                }, 100)
                return true
            }
        })

        selectLocationButton.setOnClickListener {
            val centerGeoPoint = centerMarker.position
            selectLocationDialog?.dismiss()
            showCreateMarkerDialog(centerGeoPoint)
        }

        selectLocationDialog = builder.setView(dialogView).create()
        selectLocationDialog?.show()
    }

    // Actualiza la posición del marcador central y muestra las coordenadas actuales
    private fun updateCenterMarkerAndCoordinates(mapView: MapView, marker: Marker, textView: TextView) {
        val mapCenter = mapView.mapCenter
        marker.position = GeoPoint(mapCenter.latitude, mapCenter.longitude)
        textView.text = "Latitud: ${String.format(Locale.US, "%.6f", mapCenter.latitude)}, " +
                "Longitud: ${String.format(Locale.US, "%.6f", mapCenter.longitude)}"
        mapView.invalidate()
    }

    // Muestra el diálogo para crear un nuevo marcador
    private fun showCreateMarkerDialog(geoPoint: GeoPoint) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_create_marker, null)
        currentCreateMarkerDialogView = dialogView

        val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
        val selectImageButton = dialogView.findViewById<Button>(R.id.selectImageButton)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.descriptionInput)
        val dateInput = dialogView.findViewById<EditText>(R.id.dateInput)
        val saveButton = dialogView.findViewById<Button>(R.id.saveMarkerButton)

        descriptionInput.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        dateInput.inputType = InputType.TYPE_NULL
        dateInput.keyListener = null

        selectImageButton.setOnClickListener {
            val options = arrayOf("Seleccionar de la galería", "Tomar una foto")
            AlertDialog.Builder(requireContext())
                .setTitle("Seleccionar imagen")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> selectImageLauncher.launch("image/*")
                        1 -> takePictureLauncher.launch(null)
                    }
                }
                .show()
        }

        dateInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedDate = Calendar.getInstance().apply { set(year, month, day) }
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    dateInput.setText(dateFormat.format(selectedDate.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        saveButton.setOnClickListener {
            val description = descriptionInput.text.toString().trim()
            val date = dateInput.text.toString().trim()
            if (description.isEmpty()) {
                Toast.makeText(context, "Por favor, ingresa una descripción", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (date.isEmpty()) {
                Toast.makeText(context, "Por favor, selecciona una fecha", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedImageUri == null) {
                Toast.makeText(context, "Por favor, selecciona una imagen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            uploadImageToFirebaseStorage(selectedImageUri!!, geoPoint.latitude, geoPoint.longitude, date, description)
        }

        createMarkerDialog = builder.setView(dialogView).create()
        createMarkerDialog?.show()
    }

    // Sube la imagen a Firebase Storage
    private fun uploadImageToFirebaseStorage(
        imageUri: Uri,
        latitude: Double,
        longitude: Double,
        date: String,
        description: String
    ) {
        progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Subiendo imagen...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val imageName = "${UUID.randomUUID()}.jpg"
        val imageRef = storageReference.child(imageName)
        imageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    saveNewMarker(latitude, longitude, date, description, imageUrl)

                    context?.let {
                        Toast.makeText(it.applicationContext, "Polaroid subida exitosamente", Toast.LENGTH_SHORT).show()
                    }

                    progressDialog.dismiss()
                    createMarkerDialog?.dismiss()
                    selectLocationDialog?.dismiss()
                }
            }
            .addOnFailureListener { exception ->
                context?.let {
                    Toast.makeText(it, "Error al subir la imagen: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                progressDialog.dismiss()
            }
    }

    // Guarda el nuevo marcador en Firebase
    private fun saveNewMarker(
        latitude: Double,
        longitude: Double,
        date: String,
        description: String,
        imageUrl: String
    ) {
        progressDialog.setMessage("Obteniendo ubicación...")

        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        RetrofitClient.instance.getReverseGeocode(latitude, longitude).enqueue(object :
            retrofit2.Callback<NominatimResponse> {
            override fun onResponse(call: Call<NominatimResponse>, response: retrofit2.Response<NominatimResponse>) {
                if (response.isSuccessful) {
                    val address = response.body()?.address
                    val city = address?.city ?: address?.town ?: address?.village
                    val country = address?.country

                    val newMarkerKey = databaseReference.push().key
                    if (newMarkerKey == null) {
                        Log.e("Firebase", "Error al generar una clave para el marcador")
                        Toast.makeText(context, "Error al generar una clave para el marcador", Toast.LENGTH_SHORT).show()
                        progressDialog.dismiss()
                        return
                    }

                    val markerData = mapOf(
                        "latlng" to mapOf("lat" to latitude, "lng" to longitude),
                        "date" to date,
                        "description" to description,
                        "image" to imageUrl,
                        "city" to city,
                        "country" to country,
                        "creatorUid" to currentUserUid
                    )

                    databaseReference.child(newMarkerKey).setValue(markerData)
                        .addOnSuccessListener {
                            Log.d("Marcador", "Marcador guardado correctamente")
                            // Añadir el nuevo marcador y actualizar la visualización
                            allMarkers.add(MarkerData(latitude, longitude, date, description, imageUrl))
                            updateMapDisplay()
                            progressDialog.dismiss()
                            createMarkerDialog?.dismiss()
                            selectLocationDialog?.dismiss()
                        }
                        .addOnFailureListener { exception ->
                            Log.e("Marcador", "Error al guardar el marcador: ${exception.message}")
                            Toast.makeText(context, "Error al guardar el marcador", Toast.LENGTH_SHORT).show()
                            progressDialog.dismiss()
                        }
                } else {
                    Log.e("Nominatim", "Error en la respuesta: ${response.errorBody()}")
                    Toast.makeText(context, "Error al obtener la ubicación", Toast.LENGTH_SHORT).show()
                    progressDialog.dismiss()
                }
            }

            override fun onFailure(call: Call<NominatimResponse>, t: Throwable) {
                Log.e("Nominatim", "Error en la solicitud: ${t.message}")
                Toast.makeText(context, "Error al obtener la ubicación", Toast.LENGTH_SHORT).show()
                progressDialog.dismiss()
            }
        })
    }

    // Resalta un marcador en el mapa
    private fun highlightMarkerOnMap(latitude: Double, longitude: Double) {
        val geoPoint = GeoPoint(latitude, longitude)
        mapView.controller.animateTo(geoPoint)
        mapView.controller.setZoom(16.0)

        // Buscar el marcador más cercano
        val markerToHighlight = allMarkers.minByOrNull { marker ->
            calculateDistance(
                marker.latitude,
                marker.longitude,
                geoPoint.latitude,
                geoPoint.longitude
            )
        }

        if (markerToHighlight != null) {
            val distance = calculateDistance(
                markerToHighlight.latitude,
                markerToHighlight.longitude,
                geoPoint.latitude,
                geoPoint.longitude
            )
            if (distance <= 50.0) {
                // Encontrar el marcador visual correspondiente
                val visualMarker = visibleMarkers.find { marker ->
                    marker.position.latitude == markerToHighlight.latitude &&
                            marker.position.longitude == markerToHighlight.longitude
                }
                visualMarker?.let { animateMarker(it) }
            }
        }
    }

    // Animar el marcador
    private fun animateMarker(marker: Marker) {
        val originalPosition = marker.position
        var isMovingUp = true
        val animationDuration = 3000L
        val stepInterval = 100L
        val steps = (animationDuration / stepInterval).toInt()
        val offset = 0.0001
        var currentStep = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (currentStep < steps) {
                    val newLatitude = if (isMovingUp) {
                        originalPosition.latitude + offset
                    } else {
                        originalPosition.latitude - offset
                    }
                    marker.position = GeoPoint(newLatitude, originalPosition.longitude)
                    mapView.invalidate()
                    isMovingUp = !isMovingUp
                    currentStep++
                    handler.postDelayed(this, stepInterval)
                } else {
                    marker.position = originalPosition
                    mapView.invalidate()
                }
            }
        }
        handler.post(runnable)
    }

    // Calcula la distancia entre dos puntos
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusKm = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = (Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2))
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distanceKm = earthRadiusKm * c
        return distanceKm * 1000
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        super.onCreateOptionsMenu(menu, inflater)
    }

    fun adjustMapBasedOnZoom() {
        val currentZoom = mapView.zoomLevelDouble
        val currentCenter = mapView.mapCenter

        if (currentZoom >= 14) {
            // Si estamos en zoom cercano (14+), alejar para mostrar clusters
            val targetZoom = 12.0
            mapView.controller.animateTo(currentCenter, targetZoom, 1000L)
        } else {
            // Si estamos en zoom lejano (<14), acercar a la ubicación actual
            mapView.controller.animateTo(currentCenter, 14.0, 1000L)
        }
    }

    // Metodo para centrar en una ubicación específica
    fun centerOnLocation(latitude: Double, longitude: Double) {
        val geoPoint = GeoPoint(latitude, longitude)
        mapView.controller.animateTo(geoPoint, 14.0, 1000L)
    }

    // Limpiar cache cuando se destruye la vista
    override fun onDestroyView() {
        super.onDestroyView()
        polaroidCache.evictAll()
    }
}