package com.besos.bpm

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.text.InputType
import android.os.Handler
import android.os.Looper
import retrofit2.Call
import android.app.*
import com.yalantis.ucrop.UCrop
import com.google.firebase.auth.FirebaseAuth

class MapFragment : Fragment(R.layout.map_fragment) {

    // Vistas del layout
    private lateinit var mapView: MapView
    private lateinit var createMarkerButton: FloatingActionButton

    // Firebase
    private lateinit var databaseReference: DatabaseReference
    private lateinit var storageReference: StorageReference

    // Lista de marcadores para el mapa
    private val markerList = mutableListOf<Marker>()

    // Variables para manejo de imágenes y diálogos
    private var selectedImageUri: Uri? = null
    private var currentCreateMarkerDialogView: View? = null
    private var createMarkerDialog: AlertDialog? = null
    private var selectLocationDialog: AlertDialog? = null
    private lateinit var progressDialog: ProgressDialog


    //  UCrop launcher
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
            // Verificar el tamaño de la imagen antes de proceder
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
            // Convertir el Bitmap a Uri
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

        // Actualizar los marcadores al cambiar el zoom del mapa (con un delay de 1s)
        mapView.addMapListener(DelayedMapListener(object : org.osmdroid.events.MapAdapter() {
            override fun onZoom(event: ZoomEvent?): Boolean {
                reloadMarkers()
                return true
            }
        }, 1000))

        // Al pulsar el botón flotante se muestra el diálogo para seleccionar ubicación
        createMarkerButton.setOnClickListener { showSelectLocationDialog() }

        // Resaltar el marcador si se navega desde el FeedFragment
        arguments?.let { args ->
            val latitude = args.getDouble("latitude")
            val longitude = args.getDouble("longitude")
            Log.d("MapFragment", "Highlighting marker at lat: $latitude, lng: $longitude")
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
                for (markerSnapshot in snapshot.children) {
                    val lat = markerSnapshot.child("latlng/lat").getValue(Double::class.java)
                    val lng = markerSnapshot.child("latlng/lng").getValue(Double::class.java)
                    val date = markerSnapshot.child("date").getValue(String::class.java) ?: "Sin fecha"
                    val description = markerSnapshot.child("description").getValue(String::class.java) ?: "Sin descripción"
                    val imageUrl = markerSnapshot.child("image").getValue(String::class.java) ?: ""
                    if (lat != null && lng != null) {
                        // Redondear latitud y longitud a 6 decimales
                        val roundedLat = String.format(Locale.US, "%.6f", lat).toDouble()
                        val roundedLng = String.format(Locale.US, "%.6f", lng).toDouble()
                        addMarker(roundedLat, roundedLng, date, description, imageUrl)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error al cargar marcadores: ${error.message}")
            }
        })
    }

    // Recarga los marcadores al actualizar el zoom u otros cambios
    private fun reloadMarkers() {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                updateMarkers(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error al actualizar datos: ${error.message}")
            }
        })
    }

    // Actualiza los marcadores existentes o agrega nuevos según lo recibido de Firebase
    private fun updateMarkers(snapshot: DataSnapshot) {
        val currentMarkersMap = markerList.associateBy { Pair(it.position.latitude, it.position.longitude) }
        val newMarkersList = mutableListOf<Marker>()
        for (markerSnapshot in snapshot.children) {
            val lat = markerSnapshot.child("latlng/lat").getValue(Double::class.java)
            val lng = markerSnapshot.child("latlng/lng").getValue(Double::class.java)
            val date = markerSnapshot.child("date").getValue(String::class.java) ?: "Sin fecha"
            val description = markerSnapshot.child("description").getValue(String::class.java) ?: "Sin descripción"
            val imageUrl = markerSnapshot.child("image").getValue(String::class.java) ?: ""
            if (lat != null && lng != null) {
                // Redondear latitud y longitud a 6 decimales
                val roundedLat = String.format(Locale.US, "%.6f", lat).toDouble()
                val roundedLng = String.format(Locale.US, "%.6f", lng).toDouble()
                val key = Pair(roundedLat, roundedLng)
                val existingMarker = currentMarkersMap[key]
                if (existingMarker != null) {
                    existingMarker.title = description
                    existingMarker.snippet = date
                    newMarkersList.add(existingMarker)
                } else {
                    addMarker(roundedLat, roundedLng, date, description, imageUrl)
                }
            }
        }
        val markersToRemove = markerList.filter { it !in newMarkersList }
        markersToRemove.forEach { mapView.overlays.remove(it) }
        markerList.clear()
        markerList.addAll(newMarkersList)
        mapView.invalidate()
    }

    // Agrega un marcador al mapa y a la lista global
    private fun addMarker(latitude: Double, longitude: Double, date: String, description: String, imageUrl: String) {
        val geoPoint = GeoPoint(latitude, longitude)
        val marker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_TOP, Marker.ANCHOR_CENTER)
            val originalDrawable = resources.getDrawable(R.drawable.ic_marker_icon, null)
            val resizedBitmap = Bitmap.createScaledBitmap(
                (originalDrawable as BitmapDrawable).bitmap,
                70,
                90,
                false
            )
            icon = BitmapDrawable(resources, resizedBitmap)
            setOnMarkerClickListener { _, _ ->
                showMarkerDialog(date, description, imageUrl)
                true
            }
        }
        mapView.overlays.add(marker)
        markerList.add(marker)
        mapView.invalidate()
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

        selectLocationMapView.addMapListener(object : org.osmdroid.events.MapAdapter() {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                updateCenterMarkerAndCoordinates(selectLocationMapView, centerMarker, currentCoordinates)
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                updateCenterMarkerAndCoordinates(selectLocationMapView, centerMarker, currentCoordinates)
                return true
            }
        })

        selectLocationButton.setOnClickListener {
            val iGeoPoint = selectLocationMapView.mapCenter
            val centerGeoPoint = GeoPoint(iGeoPoint.latitude, iGeoPoint.longitude)
            dismissDialog(builder)
            showCreateMarkerDialog(centerGeoPoint)
        }

        selectLocationDialog = builder.setView(dialogView).create()
        selectLocationDialog?.show()
    }

    // Actualiza la posición del marcador central y muestra las coordenadas actuales
    private fun updateCenterMarkerAndCoordinates(mapView: MapView, marker: Marker, textView: TextView) {
        val centerIGeoPoint = mapView.mapCenter
        // Redondear latitud y longitud a 6 decimales
        val roundedLat = String.format(Locale.US, "%.6f", centerIGeoPoint.latitude).toDouble()
        val roundedLng = String.format(Locale.US, "%.6f", centerIGeoPoint.longitude).toDouble()
        val centerGeoPoint = GeoPoint(roundedLat, roundedLng)
        marker.position = centerGeoPoint
        textView.text = "Latitud: $roundedLat, Longitud: $roundedLng"
    }

    // Cierra un diálogo creado a partir de un AlertDialog.Builder
    private fun dismissDialog(builder: AlertDialog.Builder) {
        val dialog = builder.create()
        if (dialog.isShowing) dialog.dismiss()
    }

    // Muestra el diálogo para crear un nuevo marcador (con imagen, descripción y fecha)
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
                        0 -> selectImageLauncher.launch("image/*") // Seleccionar de la galería
                        1 -> takePictureLauncher.launch(null)      // Tomar una foto
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

    // Sube la imagen a Firebase Storage y llama a la función para guardar el marcador
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
                    val roundedLat = String.format(Locale.US, "%.6f", latitude).toDouble()
                    val roundedLng = String.format(Locale.US, "%.6f", longitude).toDouble()
                    saveNewMarker(roundedLat, roundedLng, date, description, imageUrl)
                    Toast.makeText(context, "Polaroid subida exitosamente", Toast.LENGTH_SHORT).show()
                    progressDialog.dismiss()
                    createMarkerDialog?.dismiss()
                    selectLocationDialog?.dismiss()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error al subir la imagen: ${exception.message}", Toast.LENGTH_SHORT).show()
                progressDialog.dismiss()
            }
    }

    // Guarda el nuevo marcador en Firebase Realtime Database y lo muestra en el mapa
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
                            addMarker(latitude, longitude, date, description, imageUrl)
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
        // Centrar el mapa en las coordenadas
        mapView.controller.animateTo(geoPoint)
        mapView.controller.setZoom(16.0)
        // Encontrar el marcador más cercano dentro de un rango de 50 metros
        val markerToHighlight = markerList.minByOrNull { marker ->
            calculateDistance(
                marker.position.latitude,
                marker.position.longitude,
                geoPoint.latitude,
                geoPoint.longitude
            )
        }
        if (markerToHighlight != null) {
            val distance = calculateDistance(
                markerToHighlight.position.latitude,
                markerToHighlight.position.longitude,
                geoPoint.latitude,
                geoPoint.longitude
            )
            if (distance <= 50.0) { // Verificar si está dentro del rango de 50 metros
                Log.d("MapFragment", "Marker found within 50 meters: $distance meters")
                animateMarker(markerToHighlight)
            } else {
                Log.e("MapFragment", "No marker found within 50 meters. Closest marker is $distance meters away.")
            }
        } else {
            Log.e("MapFragment", "No markers available in the map.")
        }
    }

    // Animar el marcador hacia arriba y abajo
    private fun animateMarker(marker: Marker) {
        val originalPosition = marker.position // Guardar la posición original del marcador
        var isMovingUp = true // Controlar si el marcador está subiendo o bajando
        val animationDuration = 3000L // Duración total de la animación en milisegundos
        val stepInterval = 100L // Intervalo entre cada paso de la animación
        val steps = (animationDuration / stepInterval).toInt() // Número de pasos
        val offset = 0.0001 // Desplazamiento en grados (ajusta según sea necesario)
        var currentStep = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (currentStep < steps) {
                    // Calcular la nueva posición del marcador
                    val newLatitude = if (isMovingUp) {
                        originalPosition.latitude + offset
                    } else {
                        originalPosition.latitude - offset
                    }
                    // Actualizar la posición del marcador
                    marker.position = GeoPoint(newLatitude, originalPosition.longitude)
                    mapView.invalidate() // Refrescar el mapa
                    // Alternar la dirección del movimiento
                    isMovingUp = !isMovingUp
                    // Incrementar el contador de pasos
                    currentStep++
                    // Programar el siguiente paso
                    handler.postDelayed(this, stepInterval)
                } else {
                    // Restaurar la posición original del marcador al final de la animación
                    marker.position = originalPosition
                    mapView.invalidate()
                }
            }
        }
        // Iniciar la animación
        handler.post(runnable)
    }

    // Calcula la distancia en metros entre dos puntos geográficos usando la fórmula de Haversine
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusKm = 6371 // Radio de la Tierra en kilómetros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = (Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2))
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distanceKm = earthRadiusKm * c
        return distanceKm * 1000 // Convertir a metros
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear() // Limpiar el menú anterior
        //inflater.inflate(R.menu.map_toolbar_menu, menu) // Usa el menú específico para MapFragment
        super.onCreateOptionsMenu(menu, inflater)
    }

}