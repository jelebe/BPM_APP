package com.besos.bpm

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.widget.ImageButton
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configura el Toolbar personalizado
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Configurar botones de imagen "Mapa" y "Feed"
        val btnMapa = findViewById<ImageButton>(R.id.btnMapa)
        val btnFeed = findViewById<ImageButton>(R.id.btnFeed)

        btnMapa.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment())
                .commit()
            invalidateOptionsMenu() // Actualiza el menú cuando cambia el fragmento
        }

        btnFeed.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FeedFragment())
                .commit()
            invalidateOptionsMenu() // Actualiza el menú cuando cambia el fragmento
        }

        // Cargar el fragmento de mapa por defecto
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment())
                .commit()
        }
    }

    // Infla el menú del Toolbar (toolbar_menu.xml)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    // Maneja la acción del botón de Log Out y ajusta la visibilidad del menú
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        // Controlar la visibilidad del botón de filtro según el fragmento
        val filterMenuItem = menu.findItem(R.id.action_filter_menu)
        if (currentFragment is FeedFragment) {
            filterMenuItem?.isVisible = true // Mostrar el botón de filtro en FeedFragment
        } else {
            filterMenuItem?.isVisible = false // Ocultar el botón de filtro en otros fragmentos
        }

        // Limpiar el menú antes de inflar uno nuevo
        menu.clear()

        // Inflar el menú correspondiente al fragmento actual
        when (currentFragment) {
            is MapFragment -> menuInflater.inflate(R.menu.map_toolbar_menu, menu)
            is FeedFragment -> menuInflater.inflate(R.menu.toolbar_menu, menu)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    // Maneja la acción del botón de Log Out en el Toolbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                // Cerrar sesión de Firebase
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