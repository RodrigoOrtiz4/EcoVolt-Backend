package com.ecovolt.backend

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.security.MessageDigest
import java.util.Date

// Resultado de comprobar una contraseña. Si es válida pero el usuario aún usa SHA-256,
// `migrar` es true y quien llama debe guardar un hash bcrypt nuevo.
data class Verificacion(val valida: Boolean, val migrar: Boolean)

object Passwords {

    private const val COSTO = 10
    private const val MAX_BYTES_BCRYPT = 72

    // Hash falso para comparar cuando el usuario no existe: así tarda lo mismo
    // y no se revela por el tiempo de respuesta qué usuarios existen.
    private val hashFalso: String = hashear("contrasena-falsa-para-igualar-tiempos")

    fun hashear(contrasena: String): String =
        BCrypt.withDefaults().hashToString(COSTO, contrasena.toCharArray())

    fun verificar(contrasena: String, fila: UsuarioFila?): Verificacion {
        if (contrasena.toByteArray(Charsets.UTF_8).size > MAX_BYTES_BCRYPT) {
            return Verificacion(valida = false, migrar = false)
        }
        if (fila == null) {
            BCrypt.verifyer().verify(contrasena.toCharArray(), hashFalso)
            return Verificacion(valida = false, migrar = false)
        }
        if (fila.saltLegacy != null) {
            // Formato antiguo de la app: SHA-256("sal:contraseña"), comparado en tiempo constante
            val calculado = sha256Hex("${fila.saltLegacy}:$contrasena")
            val valida = MessageDigest.isEqual(
                calculado.toByteArray(Charsets.UTF_8),
                fila.passwordHash.lowercase().toByteArray(Charsets.UTF_8)
            )
            return Verificacion(valida = valida, migrar = valida)
        }
        val valida = BCrypt.verifyer().verify(contrasena.toCharArray(), fila.passwordHash).verified
        return Verificacion(valida = valida, migrar = false)
    }

    private fun sha256Hex(texto: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(texto.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

// Tokens de sesión (JWT). Duran 30 días y van firmados con el secreto del servidor.
class Tokens(secreto: String) {

    private val algoritmo = Algorithm.HMAC256(secreto)
    private val verificador = JWT.require(algoritmo).build()

    fun crear(id: Int, rol: String): String {
        val ahora = System.currentTimeMillis()
        return JWT.create()
            .withSubject(id.toString())
            .withClaim("rol", rol)
            .withIssuedAt(Date(ahora))
            .withExpiresAt(Date(ahora + DURACION_MS))
            .sign(algoritmo)
    }

    // true si el token es auténtico y no ha vencido
    fun esValido(token: String): Boolean = rolDe(token) != null

    // Rol guardado dentro del token ("DIRECTOR", "RESPONSABLE", ...), o null si el token
    // es falso o ya venció. Se puede confiar en él porque el token va firmado por el servidor.
    fun rolDe(token: String): String? =
        try {
            verificador.verify(token).getClaim("rol").asString()
        } catch (e: JWTVerificationException) {
            null
        }

    private companion object {
        const val DURACION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
