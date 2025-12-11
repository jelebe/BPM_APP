package com.besos.bpm

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import android.app.AlertDialog
import android.app.DatePickerDialog

class FeedFragment : Fragment(R.layout.feed_fragment) {

    // Vistas del layout
    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private val markerItems = mutableListOf<MarkerItem>()
    private lateinit var databaseReference: DatabaseReference

    // Variables para los filtros
    private var selectedCity: String? = null
    private var selectedCountry: String? = null
    private var startDate: Date? = null
    private var endDate: Date? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        // Configurar RecyclerView
        feedRecyclerView = view.findViewById(R.id.feedRecyclerView)
        feedRecyclerView.layoutManager = LinearLayoutManager(context)

        // Modificado: Ahora el adaptador tiene dos callbacks
        feedAdapter = FeedAdapter(
            markerItems,
            onMarkerClickListener = { selectedMarker ->
                // Navegar al MapFragment y pasar los datos del marcador seleccionado
                val bundle = Bundle().apply {
                    putDouble("latitude", selectedMarker.latLng?.lat ?: 0.0)
                    putDouble("longitude", selectedMarker.latLng?.lng ?: 0.0)
                }
                val mapFragment = MapFragment().apply { arguments = bundle }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, mapFragment)
                    .addToBackStack(null)
                    .commit()
            },
            onFlipClickListener = { markerToFlip, position ->
                // Girar la polaroid (cambiar entre frente y dorso)
                markerToFlip.isShowingBack = !markerToFlip.isShowingBack
                feedAdapter.notifyItemChanged(position)
            }
        )
        feedRecyclerView.adapter = feedAdapter

        // Inicializar Firebase
        databaseReference = FirebaseDatabase.getInstance().reference.child("markers")
        loadFeed()

        // Habilitar el menú de opciones
        setHasOptionsMenu(true)
    }

    // Cargar marcadores desde Firebase
    private fun loadFeed() {
        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                markerItems.clear()
                for (markerSnapshot in snapshot.children) {
                    val imageUrl = markerSnapshot.child("image").getValue(String::class.java) ?: ""
                    val description = markerSnapshot.child("description").getValue(String::class.java) ?: ""
                    val date = markerSnapshot.child("date").getValue(String::class.java) ?: ""
                    val lat = markerSnapshot.child("latlng/lat").getValue(Double::class.java) ?: 0.0
                    val lng = markerSnapshot.child("latlng/lng").getValue(Double::class.java) ?: 0.0
                    val city = markerSnapshot.child("city").getValue(String::class.java)
                    val country = markerSnapshot.child("country").getValue(String::class.java)
                    // NUEVO: Cargar el texto largo de la parte trasera
                    val longText = markerSnapshot.child("longText").getValue(String::class.java) ?: ""

                    if (imageUrl.isNotEmpty()) {
                        val markerItem = MarkerItem(
                            imageUrl = imageUrl,
                            description = description,
                            date = date,
                            latLng = LatLngModel(lat, lng),
                            city = city,
                            country = country,
                            longText = longText,
                            isShowingBack = false // Por defecto mostrar el frente
                        )
                        markerItems.add(markerItem)
                    }
                }
                applyFiltersAndSort()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FeedFragment", "Error cargando feed: ${error.message}")
                Toast.makeText(context, "Error cargando feed", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Aplicar filtros y ordenar los marcadores
    private fun applyFiltersAndSort() {
        val filteredMarkers = markerItems.filter { marker ->
            (selectedCity == null || marker.city == selectedCity) &&
                    (selectedCountry == null || marker.country == selectedCountry) &&
                    (startDate == null || parseDate(marker.date)?.after(startDate) == true) &&
                    (endDate == null || parseDate(marker.date)?.before(endDate) == true)
        }.sortedByDescending { marker ->
            try {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(marker.date)?.time
            } catch (e: Exception) {
                Log.e("FeedFragment", "Error parsing date: ${marker.date}", e)
                Long.MIN_VALUE
            }
        }
        feedAdapter.updateData(filteredMarkers)
    }

    // Función auxiliar para convertir una cadena de fecha en un objeto Date
    private fun parseDate(dateString: String): Date? {
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateString)
        } catch (e: Exception) {
            Log.e("FeedFragment", "Error parsing date: $dateString", e)
            null
        }
    }

    // Inflar el menú de opciones
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear() // Limpiar el menú anterior (asegúrate de que esto sea necesario)
        inflater.inflate(R.menu.toolbar_menu, menu) // Usa el menú específico para FeedFragment
        super.onCreateOptionsMenu(menu, inflater)
    }

    // Manejar las acciones del menú
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter_city -> {
                showCityFilterDialog()
                true
            }
            R.id.action_filter_country -> {
                showCountryFilterDialog()
                true
            }
            R.id.action_filter_date_range -> {
                showDateRangeFilterDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Limpia el menú antes de inflar uno nuevo
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
    }

    // Mostrar diálogo para filtrar por ciudad
    private fun showCityFilterDialog() {
        val cities = markerItems.mapNotNull { it.city }.distinct()
        if (cities.isEmpty()) {
            Toast.makeText(context, "No hay ciudades disponibles para filtrar", Toast.LENGTH_SHORT).show()
            return
        }
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Selecciona una ciudad")
            .setItems(cities.toTypedArray()) { _, which ->
                selectedCity = cities[which]
                applyFiltersAndSort()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Mostrar diálogo para filtrar por país
    private fun showCountryFilterDialog() {
        val countries = markerItems.mapNotNull { it.country }.distinct()
        if (countries.isEmpty()) {
            Toast.makeText(context, "No hay países disponibles para filtrar", Toast.LENGTH_SHORT).show()
            return
        }
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Selecciona un país")
            .setItems(countries.toTypedArray()) { _, which ->
                selectedCountry = countries[which]
                applyFiltersAndSort()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Mostrar diálogo para filtrar por rango de fechas
    private fun showDateRangeFilterDialog() {
        val calendar = Calendar.getInstance()
        val datePickerStart = DatePickerDialog(requireContext())
        val datePickerEnd = DatePickerDialog(requireContext())

        datePickerStart.setOnDateSetListener { _, year, month, day ->
            startDate = Calendar.getInstance().apply { set(year, month, day) }.time
            datePickerEnd.show()
        }

        datePickerEnd.setOnDateSetListener { _, year, month, day ->
            endDate = Calendar.getInstance().apply { set(year, month, day) }.time
            applyFiltersAndSort()
        }

        datePickerStart.show()
    }

    // Limpiar todos los filtros
    private fun clearFilters() {
        selectedCity = null
        selectedCountry = null
        startDate = null
        endDate = null
        applyFiltersAndSort()
    }
}