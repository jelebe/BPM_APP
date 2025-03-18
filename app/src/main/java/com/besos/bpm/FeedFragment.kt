package com.besos.bpm

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.besos.bpm.LatLngModel


class FeedFragment : Fragment(R.layout.feed_fragment) {

    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private val markerItems = mutableListOf<MarkerItem>()

    private lateinit var databaseReference: DatabaseReference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        feedRecyclerView = view.findViewById(R.id.feedRecyclerView)
        feedRecyclerView.layoutManager = LinearLayoutManager(context)
        feedAdapter = FeedAdapter(markerItems) { selectedMarker ->
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
        }
        feedRecyclerView.adapter = feedAdapter

        databaseReference = FirebaseDatabase.getInstance().reference.child("markers")
        loadFeed()
    }

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

                    if (imageUrl.isNotEmpty()) {
                        val markerItem = MarkerItem(imageUrl, description, date, LatLngModel(lat, lng))
                        markerItems.add(markerItem)
                    }
                }
                markerItems.sortByDescending { marker ->
                    try {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(marker.date)?.time
                    } catch (e: Exception) {
                        Log.e("FeedFragment", "Error parsing date: ${marker.date}", e)
                        Long.MIN_VALUE
                    }
                }
                feedAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FeedFragment", "Error cargando feed: ${error.message}")
                Toast.makeText(context, "Error cargando feed", Toast.LENGTH_SHORT).show()
            }
        })
    }
}