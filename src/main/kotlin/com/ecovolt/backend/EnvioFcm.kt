package com.ecovolt.backend

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

// Envío real con Firebase Cloud Messaging (Firebase Admin SDK).
// Los teléfonos se identifican con su ID de instalación de Firebase (FID), no con tokens.
class EnvioFcm(private val fcm: FirebaseMessaging) : EnvioPush {

    override fun enviar(fids: List<String>, aviso: Aviso): List<String> {
        val invalidos = mutableListOf<String>()
        // Firebase acepta hasta 500 mensajes por llamada
        for (lote in fids.chunked(500)) {
            val mensajes = lote.map { fid ->
                Message.builder()
                    .setFid(fid)
                    .setNotification(Notification.builder().setTitle(aviso.titulo).setBody(aviso.cuerpo).build())
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder().setChannelId(CANAL_ANDROID).build())
                            .build()
                    )
                    // Título y texto también en los datos, por si la app los necesita con la app abierta
                    .putAllData(aviso.datos + mapOf("titulo" to aviso.titulo, "cuerpo" to aviso.cuerpo))
                    .build()
            }
            val respuesta = fcm.sendEach(mensajes)
            respuesta.responses.forEachIndexed { i, r ->
                if (!r.isSuccessful) {
                    val codigo = r.exception?.messagingErrorCode
                    // El teléfono ya no existe (desinstaló la app) o es de otro proyecto de Firebase
                    if (codigo == MessagingErrorCode.UNREGISTERED || codigo == MessagingErrorCode.SENDER_ID_MISMATCH) {
                        invalidos.add(lote[i])
                    }
                }
            }
        }
        return invalidos
    }

    companion object {
        // Mismo canal que crea la app (CanalNotificaciones.kt)
        const val CANAL_ANDROID = "ecovolt_avisos"

        private val log = LoggerFactory.getLogger("EnvioFcm")

        // Lee la cuenta de servicio de Firebase, en este orden:
        //  1. FIREBASE_CREDENTIALS: el contenido del JSON (recomendado en Render)
        //  2. FIREBASE_CREDENTIALS_PATH: ruta al archivo JSON
        //  3. firebase-credenciales.json en la carpeta del proyecto (para correrlo en tu computadora)
        // Si no hay ninguno, devuelve null y el servidor funciona sin notificaciones.
        fun desdeEntorno(): EnvioFcm? {
            val credenciales: InputStream = System.getenv("FIREBASE_CREDENTIALS")?.takeIf { it.isNotBlank() }
                ?.let { ByteArrayInputStream(it.toByteArray()) }
                ?: System.getenv("FIREBASE_CREDENTIALS_PATH")?.takeIf { it.isNotBlank() }?.let { File(it) }
                    ?.takeIf { it.exists() }?.inputStream()
                ?: File("firebase-credenciales.json").takeIf { it.exists() }?.inputStream()
                ?: run {
                    log.warn("Notificaciones push desactivadas: falta FIREBASE_CREDENTIALS (ver README)")
                    return null
                }
            return try {
                val opciones = FirebaseOptions.builder()
                    .setCredentials(credenciales.use { GoogleCredentials.fromStream(it) })
                    .build()
                val app = FirebaseApp.getApps().firstOrNull { it.name == "ecovolt" }
                    ?: FirebaseApp.initializeApp(opciones, "ecovolt")
                log.info("Notificaciones push activadas (proyecto ${app.options.projectId})")
                EnvioFcm(FirebaseMessaging.getInstance(app))
            } catch (e: Exception) {
                log.error("No se pudo iniciar Firebase; notificaciones desactivadas: ${e.message}")
                null
            }
        }
    }
}
