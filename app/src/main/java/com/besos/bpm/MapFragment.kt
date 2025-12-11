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
import android.text.InputFilter
import android.text.InputType
import android.util.LruCache
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.RotateAnimation
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
import android.view.MotionEvent
import android.view.animation.TranslateAnimation
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.EditText

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

    // Límite de caracteres para el texto largo
    private val MAX_LONG_TEXT_CHARS = 200

    // Data class para almacenar información de marcadores
    data class MarkerData(
        val latitude: Double,
        val longitude: Double,
        val date: String,
        val description: String,
        val imageUrl: String,
        val longText: String = ""
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
        val cacheSize = maxMemory / 8
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

    private fun configureMapLimits() {
        val boundingBox = BoundingBox(85.0, 180.0, -85.0, -180.0)
        mapView.setScrollableAreaLimitDouble(boundingBox)
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 20.0
    }

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
                    val longText = markerSnapshot.child("longText").getValue(String::class.java) ?: ""

                    if (imageUrl.isNotEmpty()) {
                        allMarkers.add(MarkerData(lat, lng, date, description, imageUrl, longText))
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

    private fun updateMapDisplay() {
        val currentBoundingBox = mapView.boundingBox
        val currentZoom = mapView.zoomLevelDouble

        if (lastBoundingBox != currentBoundingBox || currentZoom != currentZoomLevel) {
            lastBoundingBox = currentBoundingBox
            currentZoomLevel = currentZoom

            Log.d("MapDisplay", "Zoom: $currentZoomLevel, Actualizando marcadores por cambio de área visible")

            mapView.overlays.removeAll(visibleMarkers)
            visibleMarkers.clear()
            mapView.overlays.removeAll(clusterMarkers)
            clusterMarkers.clear()

            val visibleMarkersData = allMarkers.filter { marker ->
                currentBoundingBox.contains(GeoPoint(marker.latitude, marker.longitude))
            }

            Log.d("MapDisplay", "Marcadores visibles: ${visibleMarkersData.size}")

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

    private fun showIndividualMarkers(markersData: List<MarkerData>) {
        val maxMarkersToShow = 50
        val markersToShow = if (markersData.size > maxMarkersToShow) {
            Log.d("Performance", "Demasiados marcadores (${markersData.size}). Mostrando solo $maxMarkersToShow")
            markersData.take(maxMarkersToShow)
        } else {
            markersData
        }

        markersToShow.forEach { data ->
            val marker = createIndividualMarker(data)
            visibleMarkers.add(marker)
            mapView.overlays.add(marker)
        }

        Log.d("MarkerCount", "Marcadores individuales mostrados: ${visibleMarkers.size}")
    }

    private fun loadPolaroidForMarker(imageUrl: String, marker: Marker) {
        val cachedBitmap = polaroidCache.get(imageUrl)
        if (cachedBitmap != null) {
            marker.icon = BitmapDrawable(resources, cachedBitmap)
            mapView.invalidate()
            return
        }

        Glide.with(requireContext())
            .asBitmap()
            .load(imageUrl)
            .override(polaroidWidth, polaroidHeight)
            .transform(CenterCrop(), RoundedCorners(8))
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val polaroidBitmap = createPolaroidBitmap(resource)
                    polaroidCache.put(imageUrl, polaroidBitmap)
                    marker.icon = BitmapDrawable(resources, polaroidBitmap)
                    mapView.invalidate()
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    val defaultIcon = resources.getDrawable(R.drawable.ic_marker_icon, null)
                    marker.icon = defaultIcon
                }
            })
    }

    private fun createPolaroidBitmap(photoBitmap: Bitmap): Bitmap {
        val polaroid = Bitmap.createBitmap(polaroidWidth, polaroidHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(polaroid)

        val paint = Paint()
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, polaroidWidth.toFloat(), polaroidHeight.toFloat(), paint)

        paint.color = Color.argb(50, 0, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(1f, 1f, polaroidWidth.toFloat() - 1f, polaroidHeight.toFloat() - 1f, paint)

        val photoAreaWidth = polaroidWidth - 16
        val photoAreaHeight = polaroidHeight - 40
        val photoLeft = 8
        val photoTop = 8

        val scaledPhoto = Bitmap.createScaledBitmap(photoBitmap, photoAreaWidth, photoAreaHeight, true)
        canvas.drawBitmap(scaledPhoto, photoLeft.toFloat(), photoTop.toFloat(), null)

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

    private fun createIndividualMarker(data: MarkerData): Marker {
        val marker = Marker(mapView).apply {
            position = GeoPoint(data.latitude, data.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            val placeholder = resources.getDrawable(R.drawable.ic_marker_icon, null)
            icon = placeholder

            title = data.description
            subDescription = data.date

            setOnMarkerClickListener { _, _ ->
                showMarkerDialog(data.date, data.description, data.imageUrl, data.longText)
                true
            }
        }

        loadPolaroidForMarker(data.imageUrl, marker)

        return marker
    }

    private fun showClusters(markersData: List<MarkerData>) {
        val clusters = mutableMapOf<Pair<Int, Int>, MutableList<MarkerData>>()

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
                markersInCluster.forEach { data ->
                    val marker = createIndividualMarker(data)
                    visibleMarkers.add(marker)
                    mapView.overlays.add(marker)
                }
            }
        }

        Log.d("ClusterInfo", "Clusters formados: $clusterCount, Marcadores en clusters: $markersInClusters, Marcadores individuales: ${visibleMarkers.size}")
    }

    private fun createClusterMarker(lat: Double, lon: Double, count: Int): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = BitmapDrawable(resources, createClusterIcon(count))
            title = "$count polaroids"
            setOnMarkerClickListener { _, _ ->
                mapView.controller.animateTo(GeoPoint(lat, lon), 15.0, 1000L)
                true
            }
        }
    }

    private fun createClusterIcon(count: Int): Bitmap {
        val width = 100
        val height = 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint().apply {
            color = Color.parseColor("#ff6675")
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 3f
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        val text = if (count > 99) "99+" else count.toString()
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textHeight = textBounds.bottom - textBounds.top
        val textY = centerY + (textHeight / 2f) - textBounds.bottom

        canvas.drawText(text, centerX, textY, textPaint)

        return bitmap
    }

    private fun showMarkerDialog(date: String, description: String, imageUrl: String, longText: String = "") {
        try {
            val builder = AlertDialog.Builder(requireContext(), R.style.PolaroidDialogTheme)
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_marker_info_flippable, null)

            Log.d("MapFragment", "Dialog layout cargado")

            val frontLayout = dialogView.findViewById<LinearLayout>(R.id.frontLayout)
            val backLayout = dialogView.findViewById<LinearLayout>(R.id.backLayout)
            val markerImage = dialogView.findViewById<ImageView>(R.id.marker_image)
            val markerDescription = dialogView.findViewById<TextView>(R.id.markerDescription)
            val markerDate = dialogView.findViewById<TextView>(R.id.markerDate)
            val markerLongText = dialogView.findViewById<TextView>(R.id.markerLongText)
            val backTopTouchArea = dialogView.findViewById<LinearLayout>(R.id.backTopTouchArea)

            Log.d("MapFragment", "Vistas obtenidas: ImageView=${markerImage != null}")

            markerDescription.text = description
            markerDate.text = date

            // Aplicar límite de caracteres en la visualización (solo para seguridad)
            val displayText = if (longText.length > MAX_LONG_TEXT_CHARS) {
                longText.substring(0, MAX_LONG_TEXT_CHARS) + "..."
            } else {
                longText
            }
            markerLongText.text = if (displayText.isNotEmpty()) displayText else " "

            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_placeholder_image)
                .into(markerImage)

            frontLayout.visibility = View.VISIBLE
            backLayout.visibility = View.GONE

            dialogView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        val width = view.width
                        val x = event.x

                        if (frontLayout.visibility == View.VISIBLE) {
                            if (x > width * 0.7) {
                                flipCard(frontLayout, backLayout, true)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            backTopTouchArea.setOnClickListener {
                if (backLayout.visibility == View.VISIBLE) {
                    flipCard(backLayout, frontLayout, false)
                }
            }

            val dialog = builder.setView(dialogView).create()
            dialog.show()

            Log.d("MapFragment", "Diálogo mostrado exitosamente")

        } catch (e: Exception) {
            Log.e("MapFragment", "Error al mostrar diálogo: ${e.message}", e)
            Toast.makeText(context, "Error al mostrar la polaroid: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun flipCard(hideView: View, showView: View, toBack: Boolean) {
        try {
            val fromX = if (toBack) 0f else -1f
            val toX = if (toBack) 1f else 0f

            val animation = TranslateAnimation(
                Animation.RELATIVE_TO_SELF, fromX,
                Animation.RELATIVE_TO_SELF, toX,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f
            )
            animation.duration = 300

            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    if (toBack) {
                        showView.alpha = 0f
                        showView.visibility = View.VISIBLE
                    } else {
                        showView.alpha = 0f
                        showView.visibility = View.VISIBLE
                    }
                }

                override fun onAnimationEnd(animation: Animation?) {
                    hideView.visibility = View.GONE
                    showView.alpha = 1f
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })

            hideView.startAnimation(animation)
        } catch (e: Exception) {
            Log.e("MapFragment", "Error en flipCard: ${e.message}")
            hideView.visibility = View.GONE
            showView.visibility = View.VISIBLE
        }
    }

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

    private fun updateCenterMarkerAndCoordinates(mapView: MapView, marker: Marker, textView: TextView) {
        val mapCenter = mapView.mapCenter
        marker.position = GeoPoint(mapCenter.latitude, mapCenter.longitude)
        textView.text = "Latitud: ${String.format(Locale.US, "%.6f", mapCenter.latitude)}, " +
                "Longitud: ${String.format(Locale.US, "%.6f", mapCenter.longitude)}"
        mapView.invalidate()
    }

    private fun showCreateMarkerDialog(geoPoint: GeoPoint) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_create_marker, null)
        val noImageText = dialogView.findViewById<TextView>(R.id.noImageText)
        currentCreateMarkerDialogView = dialogView

        val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
        val selectImageButton = dialogView.findViewById<Button>(R.id.selectImageButton)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.descriptionInput)
        val longTextInput = dialogView.findViewById<EditText>(R.id.longTextInput)
        val dateInput = dialogView.findViewById<EditText>(R.id.dateInput)
        val saveButton = dialogView.findViewById<Button>(R.id.saveMarkerButton)

        descriptionInput.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        // Configurar entrada para texto largo con límite de 500 caracteres
        longTextInput.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        longTextInput.hint = "Texto adicional para la parte trasera (opcional, máximo 200 caracteres)"
        // Aplicar filtro de límite de caracteres
        longTextInput.filters = arrayOf(InputFilter.LengthFilter(MAX_LONG_TEXT_CHARS))

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
            val longText = longTextInput.text.toString().trim()
            val date = dateInput.text.toString().trim()

            // Validaciones
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

            // Validar longitud del texto largo
            if (longText.length > MAX_LONG_TEXT_CHARS) {
                Toast.makeText(context, "El texto de la parte trasera no debe exceder $MAX_LONG_TEXT_CHARS caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            uploadImageToFirebaseStorage(selectedImageUri!!, geoPoint.latitude, geoPoint.longitude, date, description, longText)
        }

        createMarkerDialog = builder.setView(dialogView).create()
        createMarkerDialog?.show()
    }

    private fun uploadImageToFirebaseStorage(
        imageUri: Uri,
        latitude: Double,
        longitude: Double,
        date: String,
        description: String,
        longText: String
    ) {
        // Validar longitud del texto antes de subir
        if (longText.length > MAX_LONG_TEXT_CHARS) {
            Toast.makeText(context, "El texto de la parte trasera excede los $MAX_LONG_TEXT_CHARS caracteres", Toast.LENGTH_SHORT).show()
            return
        }

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
                    saveNewMarker(latitude, longitude, date, description, imageUrl, longText)
                }
            }
            .addOnFailureListener { exception ->
                context?.let {
                    Toast.makeText(it, "Error al subir la imagen: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                progressDialog.dismiss()
            }
    }

    private fun saveNewMarker(
        latitude: Double,
        longitude: Double,
        date: String,
        description: String,
        imageUrl: String,
        longText: String
    ) {
        // Validación adicional de seguridad
        if (longText.length > MAX_LONG_TEXT_CHARS) {
            Log.e("Validation", "El texto largo excede los $MAX_LONG_TEXT_CHARS caracteres")
            Toast.makeText(context, "Error: El texto de la parte trasera es demasiado largo", Toast.LENGTH_SHORT).show()
            progressDialog.dismiss()
            return
        }

        progressDialog.setMessage("Obteniendo ubicación...")

        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        fun saveMarkerToFirebase(city: String? = null, country: String? = null) {
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
                "longText" to longText,
                "creatorUid" to currentUserUid
            )

            databaseReference.child(newMarkerKey).setValue(markerData)
                .addOnSuccessListener {
                    Log.d("Marcador", "Marcador guardado correctamente")
                    allMarkers.add(MarkerData(latitude, longitude, date, description, imageUrl, longText))
                    updateMapDisplay()
                    progressDialog.dismiss()
                    createMarkerDialog?.dismiss()
                    selectLocationDialog?.dismiss()

                    Toast.makeText(context, "Polaroid guardada exitosamente", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    Log.e("Marcador", "Error al guardar el marcador: ${exception.message}")
                    Toast.makeText(context, "Error al guardar el marcador en la base de datos", Toast.LENGTH_SHORT).show()
                    progressDialog.dismiss()
                }
        }

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            Log.w("Nominatim", "Timeout en la geocodificación")
            saveMarkerToFirebase()
        }

        handler.postDelayed(timeoutRunnable, 10000)

        RetrofitClient.instance.getReverseGeocode(latitude, longitude).enqueue(object :
            retrofit2.Callback<NominatimResponse> {
            override fun onResponse(call: Call<NominatimResponse>, response: retrofit2.Response<NominatimResponse>) {
                handler.removeCallbacks(timeoutRunnable)

                if (response.isSuccessful && response.body() != null) {
                    try {
                        val address = response.body()?.address
                        val city = address?.city ?: address?.town ?: address?.village
                        val country = address?.country

                        Log.d("Geocoding", "Ciudad obtenida: $city, País: $country")

                        saveMarkerToFirebase(city, country)
                    } catch (e: Exception) {
                        Log.e("Nominatim", "Error al procesar respuesta: ${e.message}")
                        saveMarkerToFirebase()
                    }
                } else {
                    Log.e("Nominatim", "Respuesta no exitosa: ${response.code()} - ${response.errorBody()?.string()}")
                    saveMarkerToFirebase()
                }
            }

            override fun onFailure(call: Call<NominatimResponse>, t: Throwable) {
                handler.removeCallbacks(timeoutRunnable)
                Log.e("Nominatim", "Error en la solicitud: ${t.message}")
                saveMarkerToFirebase()
            }
        })
    }

    private fun highlightMarkerOnMap(latitude: Double, longitude: Double) {
        val geoPoint = GeoPoint(latitude, longitude)
        mapView.controller.animateTo(geoPoint)
        mapView.controller.setZoom(16.0)

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
                val visualMarker = visibleMarkers.find { marker ->
                    marker.position.latitude == markerToHighlight.latitude &&
                            marker.position.longitude == markerToHighlight.longitude
                }
                visualMarker?.let { animateMarker(it) }
            }
        }
    }

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
            val targetZoom = 12.0
            mapView.controller.animateTo(currentCenter, targetZoom, 1000L)
        } else {
            mapView.controller.animateTo(currentCenter, 14.0, 1000L)
        }
    }

    fun centerOnLocation(latitude: Double, longitude: Double) {
        val geoPoint = GeoPoint(latitude, longitude)
        mapView.controller.animateTo(geoPoint, 14.0, 1000L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        polaroidCache.evictAll()
    }
}