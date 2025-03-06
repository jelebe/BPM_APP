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
        // Asegúrate de usar un tema sin ActionBar en el manifest o styles.xml (por ejemplo, AppTheme.NoActionBar)
        setContentView(R.layout.activity_main)

        // Configura el Toolbar personalizado
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // Opcional: Si deseas quitar el título proporcionado por el Toolbar
        // supportActionBar?.title = "Besos por el Mundo"

        // Configurar botones de imagen "Mapa" y "Feed"
        val btnMapa = findViewById<ImageButton>(R.id.btnMapa)
        val btnFeed = findViewById<ImageButton>(R.id.btnFeed)

        btnMapa.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MapFragment())
                .commit()
        }

        btnFeed.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FeedFragment())
                .commit()
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

    // Maneja la acción del botón de Log Out en el Toolbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
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
