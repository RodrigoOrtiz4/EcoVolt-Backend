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
    val fecha: String? = null,                          // "2026-10-16"
    val momento: String? = null,                        // "viernes" o "lunes"
    @SerialName("valor_kwh") val valorKwh: Double? = null
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

// ---- Uso interno (no se envía a la app) ----

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
