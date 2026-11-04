package com.ecovolt.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Lo que envía la app (los campos son opcionales para poder responder 400 si faltan) ----

@Serializable
data class LoginRequest(val usuario: String? = null, val contrasena: String? = null)

@Serializable
data class RegistroRequest(val nombre: String? = null, val usuario: String? = null, val contrasena: String? = null)

// Lectura que la app capturó con la cámara (o escribió a mano) y el usuario confirmó.
// El id no se envía: lo asigna la base de datos.
@Serializable
data class NuevaLecturaRequest(
    val fecha: String? = null,
    val momento: String? = null,
    @SerialName("valor_kwh") val valorKwh: Double? = null
)

// Reporte de desperdicio o falla (RFN-19). La foto viaja como texto Base64 dentro del JSON.
// Ni el id, ni la fecha, ni el autor se envían: los pone el servidor.
@Serializable
data class NuevoReporteRequest(
    val tipo: String? = null,                           // "desperdicio" o "falla"
    val area: String? = null,                           // "Salón 3B", "Baños planta baja", ...
    val descripcion: String? = null,
    @SerialName("foto_base64") val fotoBase64: String? = null
)

// ---- Lo que responde el servidor ----

// Un usuario tal como se envía a la app: nunca incluye contraseña, hash ni sal
@Serializable
data class UsuarioPublico(val id: Int, val usuario: String, val nombre: String, val rol: String)

@Serializable
data class AuthResponse(val token: String, val usuario: UsuarioPublico)

@Serializable
data class ErrorResponse(val mensaje: String, val errores: Map<String, String>? = null)

@Serializable
data class Salud(val estado: String)

// Mismo formato JSON que ya consume la app: id, fecha, momento, valor_kwh
@Serializable
data class LecturaResponse(
    val id: Int,
    val fecha: String,
    val momento: String,
    @SerialName("valor_kwh") val valorKwh: Double
)

// Un reporte tal como se envía a la app. La foto NO va aquí (pesaría mucho la lista):
// se descarga aparte con GET /reportes/{id}/foto.
@Serializable
data class ReporteResponse(
    val id: Int,
    val tipo: String,
    val area: String,
    val descripcion: String,
    val fecha: String,       // "2026-10-28 16:45" (hora de México)
    val autor: String        // nombre de quien lo envió
)

// Consejo de ahorro (RFN-25)
@Serializable
data class ConsejoResponse(
    val id: Int,
    val ambito: String,      // "escuela" o "hogar"
    val categoria: String,   // "Iluminación", "Equipos", ...
    val icono: String,       // un emoji para la tarjeta
    val titulo: String,
    val texto: String
)

// ---- Uso interno (no se envía a la app) ----

// Quién hizo la petición, según su token: su id y su rol
data class Sesion(val id: Int, val rol: String)

// Foto guardada de un reporte. Es `class` y no `data class` porque un ByteArray
// no se compara bien con equals() de una data class.
class FotoReporte(val bytes: ByteArray, val tipoMime: String, val usuarioId: Int)

// Fila de la tabla usuarios. saltLegacy solo existe mientras la contraseña siga en SHA-256.
data class UsuarioFila(
    val id: Int,
    val usuario: String,
    val nombre: String,
    val rol: String,
    val passwordHash: String,
    val saltLegacy: String?
) {
    fun publico() = UsuarioPublico(id, usuario, nombre, rol)
}
