package com.ecovolt.backend

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.security.MessageDigest
import java.text.Normalizer
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

    // `version` es la versión de sesión del usuario (sube al cambiar la contraseña)
    fun crear(id: Int, rol: String, version: Int = 0): String {
        val ahora = System.currentTimeMillis()
        return JWT.create()
            .withSubject(id.toString())
            .withClaim("rol", rol)
            .withClaim("ver", version)
            .withIssuedAt(Date(ahora))
            .withExpiresAt(Date(ahora + DURACION_MS))
            .sign(algoritmo)
    }

    // true si el token es auténtico y no ha vencido
    fun esValido(token: String): Boolean = sesionDe(token) != null

    // Id (va en el "subject") y rol guardados dentro del token, o null si el token es falso
    // o ya venció. Se puede confiar en ellos porque el token va firmado por el servidor.
    // Los reportes necesitan el id para saber quién envió cada uno.
    fun sesionDe(token: String): Sesion? =
        try {
            val datos = verificador.verify(token)
            val id = datos.subject?.toIntOrNull()
            val rol = datos.getClaim("rol").asString()
            // Los tokens creados antes de existir "ver" cuentan como versión 0
            val version = datos.getClaim("ver").asInt() ?: 0
            if (id == null || rol == null) null else Sesion(id, rol, version)
        } catch (e: JWTVerificationException) {
            null
        }

    // Solo el rol ("DIRECTOR", "RESPONSABLE", ...), o null si el token no es válido
    fun rolDe(token: String): String? = sesionDe(token)?.rol

    private companion object {
        const val DURACION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

// Límite de intentos fallidos de inicio de sesión. Después de MAX_INTENTOS fallos seguidos con el
// mismo usuario, se bloquea durante BLOQUEO_MS. Se cuenta igual exista o no el usuario, para no
// revelar qué cuentas hay. Vive en memoria: se reinicia si el servidor se reinicia (suficiente aquí).
// La respuesta de seguridad se guarda como hash bcrypt (igual que una contraseña) después de
// normalizarla, para que "Firulais", " firulais " y "FIRULÁIS" cuenten como la misma respuesta.
fun normalizarRespuesta(respuesta: String): String =
    Normalizer.normalize(respuesta.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")   // quita acentos
        .replace(Regex("\\s+"), " ")      // deja un solo espacio entre palabras

object Respuestas {
    private val hashFalso: String = Passwords.hashear("respuesta-falsa-para-igualar-tiempos")

    fun hashear(respuesta: String): String = Passwords.hashear(normalizarRespuesta(respuesta))

    // Si el usuario no tiene respuesta guardada se compara contra un hash falso: tarda lo mismo
    fun verificar(respuesta: String, hash: String?): Boolean {
        val normal = normalizarRespuesta(respuesta)
        if (normal.toByteArray(Charsets.UTF_8).size > 72) return false
        val resultado = BCrypt.verifyer().verify(normal.toCharArray(), hash ?: hashFalso).verified
        return hash != null && resultado
    }
}

class LimiteIntentos(
    private val maxIntentos: Int = MAX_INTENTOS,
    private val bloqueoMs: Long = BLOQUEO_MS,
    private val reloj: () -> Long = System::currentTimeMillis
) {
    private class Registro(var fallos: Int, var desde: Long, var bloqueadoHasta: Long)

    private val registros = mutableMapOf<String, Registro>()

    // Milisegundos que faltan para desbloquear, o null si no está bloqueado
    @Synchronized
    fun bloqueadoPor(clave: String): Long? {
        val registro = registros[clave] ?: return null
        val ahora = reloj()
        if (registro.bloqueadoHasta > ahora) return registro.bloqueadoHasta - ahora
        if (registro.bloqueadoHasta != 0L || ahora - registro.desde > bloqueoMs) registros.remove(clave)
        return null
    }

    // Anota un fallo y devuelve cuántos intentos quedan (0 = acaba de quedar bloqueado)
    @Synchronized
    fun registrarFallo(clave: String): Int {
        val ahora = reloj()
        val registro = registros[clave]?.takeIf { ahora - it.desde <= bloqueoMs && it.bloqueadoHasta == 0L }
            ?: Registro(0, ahora, 0L).also { registros[clave] = it }
        registro.fallos++
        val restantes = (maxIntentos - registro.fallos).coerceAtLeast(0)
        if (restantes == 0) registro.bloqueadoHasta = ahora + bloqueoMs
        limpiarViejos(ahora)
        return restantes
    }

    @Synchronized
    fun limpiar(clave: String) {
        registros.remove(clave)
    }

    // Evita que el mapa crezca sin límite con usuarios inventados
    private fun limpiarViejos(ahora: Long) {
        if (registros.size < 1000) return
        registros.entries.removeIf { (_, r) -> r.bloqueadoHasta < ahora && ahora - r.desde > bloqueoMs }
    }

    companion object {
        const val MAX_INTENTOS = 5
        const val BLOQUEO_MS = 15L * 60 * 1000
    }
}
