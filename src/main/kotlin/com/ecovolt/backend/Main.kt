package com.ecovolt.backend

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.net.Inet4Address
import java.net.NetworkInterface

// Punto de arranque. Variables de entorno opcionales: PORT (3000), DB_PATH (data/ecovolt.db), JWT_SECRET
// y FIREBASE_CREDENTIALS (JSON de la cuenta de servicio de Firebase, para las notificaciones push).
fun main() {
    val puerto = System.getenv("PORT")?.toIntOrNull() ?: 3000
    val rutaDb = System.getenv("DB_PATH") ?: "data/ecovolt.db"

    val db = Database(rutaDb)
    mostrarDirecciones(puerto)

    // Notificaciones push: se activan si está FIREBASE_CREDENTIALS (ver README)
    val notificador = Notificador(db, EnvioFcm.desdeEntorno())
    Recordatorios(db, notificador, ZONA_ESCUELA).iniciar(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    // host 0.0.0.0: escucha en todas las interfaces para que el emulador y el celular puedan conectarse
    embeddedServer(Netty, port = puerto, host = "0.0.0.0") {
        modulo(db, notificador)
    }.start(wait = true)
}

private fun mostrarDirecciones(puerto: Int) {
    println("EcoVolt API (Kotlin) escuchando en el puerto $puerto")
    println("  Emulador de Android   : http://10.0.2.2:$puerto")
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
    for (red in interfaces) {
        if (!red.isUp || red.isLoopback) continue
        for (direccion in red.inetAddresses) {
            if (direccion is Inet4Address) println("  Celular físico (Wi-Fi): http://${direccion.hostAddress}:$puerto")
        }
    }
}
