package com.besos.bpm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // Definir los UIDs de los dos usuarios (reemplaza con los UIDs reales)
    private val USER1_UID = "JFYdmUPY93eETaxw0TLyoVgktw22"
    private val USER2_UID = "zZDxTAG9TheoDHJsw5ZidM2kALj2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 🔔 Crear canal de notificación para Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "besos_channel",
                "Notificaciones románticas 💌",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mensajes de Polaroids y nuevos besos"
            }

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Obtener el logo de la toolbar y asignar el clic
        val toolbarLogo = findViewById<ImageView>(R.id.toolbarLogo)
        toolbarLogo.setOnClickListener {
            centerOnCurrentLocation()
        }

        val btnMapa = findViewById<ImageButton>(R.id.btnMapa)
        val btnFeed = findViewById<ImageButton>(R.id.btnFeed)

        btnMapa.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is MapFragment) {
                currentFragment.adjustMapBasedOnZoom()
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MapFragment())
                    .commit()
            }
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

        // Pedir permisos de ubicación si no los tenemos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        // 📲 Obtener el token FCM y guardarlo en Realtime Database
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val uid = FirebaseAuth.getInstance().currentUser?.uid

                if (uid != null) {
                    val ref = FirebaseDatabase.getInstance().getReference("users/$uid")

                    // Guardar el token FCM
                    ref.child("fcmToken").setValue(token)

                    // Determinar y guardar el UID del otro usuario para notificaciones
                    val otherUserId = if (uid == USER1_UID) USER2_UID else USER1_UID
                    ref.child("notifyUserId").setValue(otherUserId)

                    Log.d("FCM", "Token guardado y usuario de notificación configurado")
                }
            } else {
                Log.w("FCM", "No se pudo obtener el token", task.exception)
            }
        }

        // ✅ Verificar actualizaciones al iniciar la app
        checkForUpdates()
    }

    private fun checkForUpdates() {
        if (isNetworkAvailable()) {
            UpdateChecker(this).checkForUpdates()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    fun centerOnCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                        if (currentFragment is MapFragment) {
                            currentFragment.centerOnLocation(it.latitude, it.longitude)
                        }
                    } ?: run {
                        Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                centerOnCurrentLocation()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }
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