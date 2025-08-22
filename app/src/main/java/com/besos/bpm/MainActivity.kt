package com.besos.bpm

import android.Manifest
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔔 Crear canal de notificación para Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "besos_channel", // ID del canal
                "Notificaciones románticas 💌", // Nombre visible en ajustes
                android.app.NotificationManager.IMPORTANCE_HIGH // Prioridad alta
            ).apply {
                description = "Mensajes de Polaroids y nuevos besos"
            }

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val btnMapa = findViewById<ImageButton>(R.id.btnMapa)
        val btnFeed = findViewById<ImageButton>(R.id.btnFeed)

        btnMapa.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment())
                .commit()
            invalidateOptionsMenu()
        }

        btnFeed.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FeedFragment())
                .commit()
            invalidateOptionsMenu()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment())
                .commit()
        }

        // Pedir permiso de notificaciones si Android >= 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        // 📲 Obtener el token FCM y guardarlo en Realtime Database
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val uid = FirebaseAuth.getInstance().currentUser?.uid

                if (uid != null) {
                    val ref = FirebaseDatabase.getInstance().getReference("users/$uid/fcmToken")
                    ref.setValue(token)
                        .addOnSuccessListener {
                            // Eliminado: No mostrar mensaje
                        }
                        .addOnFailureListener {
                            // Opcional: Puedes loguear el error si lo deseas
                            Log.e("FCM", "Error al guardar token", it)
                        }
                }
            } else {
                // Opcional: Puedes registrar el fallo si necesitas depurarlo
                // Log.w("FCM", "No se pudo obtener el token", task.exception)
            }
        }

        // ✅ Verificar actualizaciones al iniciar la app
        checkForUpdates()
    }

    // Add this missing function
    private fun checkForUpdates() {
        // Solo verificar actualizaciones si hay conexión a internet
        if (isNetworkAvailable()) {
            UpdateChecker(this).checkForUpdates()
        }
    }

    // Add this helper function
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val filterMenuItem = menu.findItem(R.id.action_filter_menu)

        filterMenuItem?.isVisible = currentFragment is FeedFragment
        menu.clear()

        when (currentFragment) {
            is MapFragment -> menuInflater.inflate(R.menu.map_toolbar_menu, menu)
            is FeedFragment -> menuInflater.inflate(R.menu.toolbar_menu, menu)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}