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


class FeedFragment : Fragment(R.layout.feed_fragment) {

    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var feedAdapter: FeedAdapter
    private val markerItems = mutableListOf<MarkerItem>()

    private lateinit var databaseReference: DatabaseReference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        feedRecyclerView = view.findViewById(R.id.feedRecyclerView)
        feedRecyclerView.layoutManager = LinearLayoutManager(context)
        feedAdapter = FeedAdapter(markerItems)
        feedRecyclerView.adapter = feedAdapter

        // Obtén la referencia a los marcadores en Firebase
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

                    // Solo añadimos elementos que tengan una imagen
                    if (imageUrl.isNotEmpty()) {
                        val markerItem = MarkerItem(imageUrl, description, date)
                        markerItems.add(markerItem)
                    }
                }
                // Ordenar la lista de más reciente a más antigua (se asume el formato "yyyy-MM-dd")
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                markerItems.sortByDescending { marker ->
                    try {
                        dateFormat.parse(marker.date)?.time
                    } catch (e: Exception) {
                        Log.e("FeedFragment", "Error parsing date: ${marker.date}", e)
                        Long.MIN_VALUE  // En caso de que falle el parseo, se coloca una fecha muy antigua
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
