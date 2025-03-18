package com.besos.bpm

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import com.besos.bpm.databinding.ActivitySplashBinding // Importa el archivo de vinculación

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding // Declara el objeto de vinculación

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Infla el diseño usando View Binding
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener la versión de la aplicación
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }

        // Asignar la versión al TextView usando View Binding
        binding.versionText.text = "Versión $versionName"

        // Temporizador para mostrar la pantalla de carga durante 1 segundo
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this@SplashActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }, 1000) // 1000 milisegundos = 1 segundo
    }
}