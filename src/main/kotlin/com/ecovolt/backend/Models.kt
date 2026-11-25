package com.ecovolt.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Lo que envía la app (los campos son opcionales para poder responder 400 si faltan) ----

@Serializable
data class LoginRequest(val usuario: String? = null, val contrasena: String? = null)

@Serializable
data class RegistroRequest(
    val nombre: String? = null,
    val usuario: String? = null,
    val contrasena: String? = null,
    // Solo alumnos: código del grupo (6 letras o números que da su docente) y pregunta de seguridad
    @SerialName("codigo_grupo") val codigoGrupo: String? = null,
    val pregunta: String? = null,
    val respuesta: String? = null
)

// Recuperar la contraseña con la pregunta de seguridad (solo alumnos)
@Serializable
data class PreguntaSeguridadRequest(val usuario: String? = null)

@Serializable
data class PreguntaSeguridadResponse(val pregunta: String)

@Serializable
data class RecuperarContrasenaRequest(val usuario: String? = null, val respuesta: String? = null, val nueva: String? = null)

// Grupos: el docente crea el grupo y comparte el código para que sus alumnos se registren
@Serializable
data class NuevoGrupoRequest(val nombre: String? = null)

@Serializable
data class CambioGrupoRequest(val activo: Boolean? = null)

@Serializable
data class GrupoResponse(
    val id: Int,
    val codigo: String,
    val nombre: String,
    val activo: Boolean,
    val alumnos: Int,
    val docente: String?,
    val creado: String
)

// Cambio de contraseña con la sesión iniciada: se pide la actual para confirmar que es la misma persona
@Serializable
data class CambioContrasenaRequest(val actual: String? = null, val nueva: String? = null)

// Lectura que la app capturó con la cámara (o escribió a mano) y el usuario confirmó.
// El id y la hora no se envían: los pone el servidor.
@Serializable
data class NuevaLecturaRequest(
    val fecha: String? = null,                          // "2026-10-16"
    val momento: String? = null,                        // "viernes" o "lunes"
    @SerialName("valor_kwh") val valorKwh: Double? = null,
    val origen: String? = null,                         // "ocr" (foto) o "manual"; si no viene, "manual"
    val nota: String? = null                            // obligatoria si la lectura es menor que la anterior
)

// Reporte de desperdicio o falla (RFN-19). La foto viaja como texto Base64 dentro del JSON.
// Ni el id, ni la fecha, ni el autor, ni el estatus se envían: los pone el servidor.
@Serializable
data class NuevoReporteRequest(
    val tipo: String? = null,                           // "desperdicio" o "falla"
    val area: String? = null,                           // "Salón 3B", "Baños planta baja", ...
    val descripcion: String? = null,
    @SerialName("foto_base64") val fotoBase64: String? = null
)

// Dirección (o el responsable) actualiza en qué va un reporte
@Serializable
data class CambioEstatusRequest(val estatus: String? = null)  // "pendiente", "en_proceso" o "resuelto"

// ---- Lo que responde el servidor ----

// Un usuario tal como se envía a la app: nunca incluye contraseña, hash ni sal
@Serializable
data class UsuarioPublico(val id: Int, val usuario: String, val nombre: String, val rol: String)

@Serializable
data class AuthResponse(val token: String, val usuario: UsuarioPublico)

// En los errores de inicio de sesión también se dice cuántos intentos quedan antes del bloqueo
@Serializable
data class ErrorResponse(
    val mensaje: String,
    val errores: Map<String, String>? = null,
    @SerialName("intentos_restantes") val intentosRestantes: Int? = null
)

@Serializable
data class MensajeResponse(val mensaje: String)

@Serializable
data class Salud(val estado: String)

// id, fecha, momento y valor_kwh como siempre. hora, origen y nota son nuevos y pueden venir
// vacíos en las lecturas antiguas; las versiones anteriores de la app simplemente los ignoran.
@Serializable
data class LecturaResponse(
    val id: Int,
    val fecha: String,
    val momento: String,
    @SerialName("valor_kwh") val valorKwh: Double,
    val hora: String? = null,       // "13:18" (hora de México)
    val origen: String? = null,     // "ocr" o "manual"
    val nota: String? = null        // explicación cuando se cambió o reinició el medidor
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
    val autor: String,       // nombre de quien lo envió
    val estatus: String      // "pendiente", "en_proceso" o "resuelto"
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

// ---- Notificaciones push ----

// POST /dispositivos: el teléfono se da de alta con su ID de instalación de Firebase (FID)
@Serializable
data class DispositivoRequest(val fid: String? = null, val plataforma: String? = null)

// ---- Cursos de ahorro de energía ----
// Mismo formato que CursoDto en la app: un curso trae sus lecciones (con bloques) y sus dos
// evaluaciones (con opciones). Las opciones incluyen cuál es la correcta porque la app
// muestra la revisión al terminar; la calificación oficial la calcula el servidor.

@Serializable
data class CursoResponse(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val icono: String,                                          // emoji
    val nivel: String,                                          // "basico" o "intermedio"
    @SerialName("duracion_minutos") val duracionMinutos: Int,
    val lecciones: List<LeccionResponse>,
    @SerialName("evaluacion_inicial") val evaluacionInicial: List<PreguntaResponse>,
    @SerialName("evaluacion_final") val evaluacionFinal: List<PreguntaResponse>
)

@Serializable
data class LeccionResponse(
    val id: Int,
    val orden: Int,
    val titulo: String,
    val resumen: String,
    @SerialName("duracion_minutos") val duracionMinutos: Int,
    val bloques: List<BloqueResponse>
)

@Serializable
data class BloqueResponse(
    val tipo: String,              // "parrafo", "dato", "ejemplo" o "lista"
    val texto: String,             // en "lista", un elemento por renglón
    val titulo: String? = null
)

@Serializable
data class PreguntaResponse(
    val id: Int,
    val enunciado: String,
    val opciones: List<OpcionResponse>,
    val explicacion: String
)

@Serializable
data class OpcionResponse(val id: Int, val texto: String, val correcta: Boolean)

// Lo que envía la app al terminar una evaluación
@Serializable
data class EvaluacionRequest(
    val momento: String? = null,                    // "inicial" o "final"
    val respuestas: List<RespuestaRequest>? = null
)

@Serializable
data class RespuestaRequest(
    @SerialName("pregunta_id") val preguntaId: Int? = null,
    @SerialName("opcion_id") val opcionId: Int? = null
)

@Serializable
data class RespuestaGuardada(
    @SerialName("pregunta_id") val preguntaId: Int,
    @SerialName("opcion_id") val opcionId: Int
)

// Avance de la persona en un curso
@Serializable
data class ProgresoCursoResponse(
    @SerialName("curso_id") val cursoId: Int,
    @SerialName("calificacion_inicial") val calificacionInicial: Int? = null,     // 0 a 100
    @SerialName("calificacion_final") val calificacionFinal: Int? = null,         // 0 a 100
    @SerialName("lecciones_completadas") val leccionesCompletadas: List<Int> = emptyList(),
    @SerialName("respuestas_finales") val respuestasFinales: List<RespuestaGuardada> = emptyList(),
    @SerialName("fecha_inicio") val fechaInicio: String? = null,                  // AAAA-MM-DD
    @SerialName("fecha_fin") val fechaFin: String? = null
)

// Respuesta de POST /cursos/{id}/evaluacion
@Serializable
data class EvaluacionResponse(
    val aciertos: Int,
    val total: Int,
    val calificacion: Int,
    val progreso: ProgresoCursoResponse
)

// Resumen por curso para dirección (GET /cursos/resumen)
@Serializable
data class ResumenCursoResponse(
    @SerialName("curso_id") val cursoId: Int,
    val titulo: String,
    val iniciaron: Int,                                                       // presentaron la inicial
    val terminaron: Int,                                                      // presentaron la final
    @SerialName("promedio_inicial") val promedioInicial: Double? = null,      // de quienes terminaron
    @SerialName("promedio_final") val promedioFinal: Double? = null
)

// ---- Uso interno (no se envía a la app) ----

// Quién hizo la petición, según su token: su id, su rol y la versión de sesión con la que entró.
// La versión sube cada vez que cambia su contraseña: los tokens anteriores dejan de servir.
data class Sesion(val id: Int, val rol: String, val version: Int = 0)

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
    val saltLegacy: String?,
    val versionSesion: Int = 0
) {
    fun publico() = UsuarioPublico(id, usuario, nombre, rol)
}
