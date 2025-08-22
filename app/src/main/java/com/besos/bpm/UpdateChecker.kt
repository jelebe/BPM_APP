package com.besos.bpm

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class UpdateChecker(private val context: Context) {

    private val githubRepoOwner = "jelebe"
    private val githubRepoName = "BPM_APP"
    private val client = OkHttpClient()

    fun checkForUpdates() {
        Log.d("UpdateChecker", "Iniciando verificación de actualizaciones")

        // Verificar si el contexto es válido y no está destruido
        val activity = context as? Activity
        if (activity != null && activity.isDestroyed) {
            Log.d("UpdateChecker", "Activity está destruida, no se puede mostrar diálogo")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latestRelease = getLatestRelease()
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionName = packageInfo.versionName ?: "0.0.0"

                Log.d("UpdateChecker", "Versión actual: $currentVersionName")
                Log.d("UpdateChecker", "Última release: ${latestRelease?.tagName}")

                if (latestRelease != null) {
                    Log.d("UpdateChecker", "Comparando versiones: $currentVersionName vs ${latestRelease.tagName}")
                    val isUpdateAvailable = isNewVersionAvailable(currentVersionName, latestRelease.tagName)
                    Log.d("UpdateChecker", "¿Actualización disponible? $isUpdateAvailable")

                    if (isUpdateAvailable) {
                        withContext(Dispatchers.Main) {
                            // Verificar nuevamente si la activity sigue válida
                            if (activity == null || !activity.isDestroyed) {
                                showUpdateDialog(latestRelease)
                            } else {
                                Log.d("UpdateChecker", "Activity no disponible para mostrar diálogo")
                            }
                        }
                    } else {
                        Log.d("UpdateChecker", "No hay actualizaciones disponibles")
                    }
                } else {
                    Log.d("UpdateChecker", "No se encontró ninguna release en GitHub")
                }
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Error al verificar actualizaciones: ${e.message}", e)
            }
        }
    }

    private suspend fun getLatestRelease(): Release? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$githubRepoOwner/$githubRepoName/releases/latest"
                Log.d("UpdateChecker", "Solicitando URL: $url")

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                Log.d("UpdateChecker", "Respuesta de GitHub: ${response.code}")

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.d("UpdateChecker", "Respuesta JSON: $responseBody")

                    val json = JSONObject(responseBody)
                    val tagName = json.getString("tag_name")
                    val assets = json.getJSONArray("assets")

                    Log.d("UpdateChecker", "Tag name: $tagName")
                    Log.d("UpdateChecker", "Número de assets: ${assets.length()}")

                    if (assets.length() > 0) {
                        // Buscar el asset APK
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val downloadUrl = asset.getString("browser_download_url")
                            if (downloadUrl.endsWith(".apk")) {
                                Log.d("UpdateChecker", "APK encontrado: $downloadUrl")
                                return@withContext Release(tagName, downloadUrl)
                            }
                        }
                        Log.d("UpdateChecker", "No se encontró ningún asset APK")
                    } else {
                        Log.d("UpdateChecker", "No se encontraron assets en la release")
                    }
                } else {
                    Log.d("UpdateChecker", "La solicitud a GitHub no fue exitosa")
                }
                null
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Error al obtener release: ${e.message}", e)
                null
            }
        }
    }

    private fun isNewVersionAvailable(currentVersion: String, latestVersionTag: String): Boolean {
        return try {
            // Normalizar versiones (eliminar 'v' prefix si existe)
            val normalizedCurrent = currentVersion.replace("^v".toRegex(), "")
            val normalizedLatest = latestVersionTag.replace("^v".toRegex(), "")

            val currentParts = normalizedCurrent.split(".").map { it.toInt() }
            val latestParts = normalizedLatest.split(".").map { it.toInt() }

            // Comparar cada parte de la versión (major, minor, patch)
            for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val latestPart = latestParts.getOrElse(i) { 0 }

                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            false
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Error comparando versiones: ${e.message}", e)
            false
        }
    }

    private fun showUpdateDialog(release: Release) {
        AlertDialog.Builder(context)
            .setTitle("Nueva actualización disponible")
            .setMessage("Hay una nueva versión (${release.tagName}) disponible. ¿Quieres actualizar ahora?")
            .setPositiveButton("Actualizar") { _, _ ->
                downloadAndInstallUpdate(release.downloadUrl)
            }
            .setNegativeButton("Más tarde", null)
            .setCancelable(false)
            .show()
    }

    private fun downloadAndInstallUpdate(downloadUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val apkFile = File(context.externalCacheDir, "update.apk")
                    FileOutputStream(apkFile).use { output ->
                        response.body?.byteStream()?.copyTo(output)
                    }

                    withContext(Dispatchers.Main) {
                        installApk(apkFile)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al descargar la actualización", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al descargar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            context.startActivity(installIntent)

            // Cerrar la aplicación después de iniciar la instalación
            Handler(Looper.getMainLooper()).postDelayed({
                (context as? Activity)?.finishAffinity()
            }, 1000)

        } catch (e: Exception) {
            Toast.makeText(context, "Error al instalar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    data class Release(val tagName: String, val downloadUrl: String)
}

class InstallCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_REPLACED) {
            if (intent.data?.schemeSpecificPart == context.packageName) {
                Toast.makeText(context, "Actualización completada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}