package com.ecovolt.backend

import java.time.LocalDate
import java.time.format.DateTimeParseException

// Mismas reglas que valida la app, repetidas aquí porque el servidor nunca debe fiarse del cliente.
// Cada función devuelve el mensaje de error, o null si el valor es válido.
object Validators {

    private val soloUsuario = Regex("^[\\p{L}\\p{N}._]+$")
    private val soloNombre = Regex("^[\\p{L} .'\\-]+$")

    fun usuario(valor: String?): String? {
        val t = valor?.trim().orEmpty()
        return when {
            t.isEmpty() -> "Escribe tu usuario"
            t.length < 4 -> "Debe tener al menos 4 caracteres"
            t.length > 20 -> "Debe tener máximo 20 caracteres"
            !soloUsuario.matches(t) -> "Solo letras, números, punto y guion bajo"
            else -> null
        }
    }

    fun contrasena(valor: String?): String? = when {
        valor.isNullOrEmpty() -> "Escribe tu contraseña"
        valor.length < 6 -> "Debe tener al menos 6 caracteres"
        valor.length > 32 -> "Debe tener máximo 32 caracteres"
        valor.any { it.isWhitespace() } -> "No puede llevar espacios"
        valor.toByteArray(Charsets.UTF_8).size > 72 -> "Contiene demasiados caracteres especiales" // límite de bcrypt
        else -> null
    }

    // ---- Lecturas del medidor (POST /lecturas) ----

    // Fecha real con formato año-mes-día (2026-10-16) y que no sea del futuro
    fun fecha(valor: String?): String? {
        val t = valor?.trim().orEmpty()
        if (t.isEmpty()) return "Falta la fecha de la lectura"
        val dia = try {
            LocalDate.parse(t) // solo acepta el formato AAAA-MM-DD
        } catch (e: DateTimeParseException) {
            return "La fecha debe tener el formato AAAA-MM-DD"
        }
        return if (dia.isAfter(LocalDate.now().plusDays(1))) "La fecha no puede ser del futuro" else null
    }

    fun momento(valor: String?): String? = when (valor?.trim()) {
        "viernes", "lunes" -> null
        null, "" -> "Falta indicar si es la lectura del viernes o del lunes"
        else -> "El momento debe ser \"viernes\" o \"lunes\""
    }

    // Mismo rango que valida la app: mayor que cero y menor que 10 millones de kWh
    fun valorKwh(valor: Double?): String? = when {
        valor == null -> "Falta el valor de la lectura"
        valor.isNaN() || valor.isInfinite() -> "El valor no es un número válido"
        valor <= 0.0 -> "Debe ser mayor que cero"
        valor >= 10_000_000.0 -> "El valor es demasiado grande"
        else -> null
    }

    fun nombre(valor: String?): String? {
        val t = valor?.trim().orEmpty()
        return when {
            t.isEmpty() -> "Escribe tu nombre"
            t.length < 3 -> "Debe tener al menos 3 caracteres"
            t.length > 40 -> "Debe tener máximo 40 caracteres"
            !soloNombre.matches(t) -> "Usa solo letras y espacios"
            else -> null
        }
    }
}
