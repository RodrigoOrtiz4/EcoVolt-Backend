package com.ecovolt.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.Locale

// Una notificación push: título, texto y datos extra para la app
data class Aviso(val titulo: String, val cuerpo: String, val datos: Map<String, String> = emptyMap())

// Quién entrega las notificaciones. En el servidor real es Firebase Cloud Messaging (EnvioFcm);
// en las pruebas, uno falso. Devuelve los identificadores que Firebase dijo que ya no existen.
interface EnvioPush {
    fun enviar(fids: List<String>, aviso: Aviso): List<String>
}

// Decide a quién avisar de cada cosa y manda las notificaciones en segundo plano, para que
// las rutas respondan rápido aunque Firebase tarde. Si Firebase no está configurado (envio = null)
// no hace nada: el resto del servidor funciona igual.
class Notificador(
    private val db: Database,
    private val envio: EnvioPush?,
    private val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val log = LoggerFactory.getLogger("Notificador")

    val activo: Boolean get() = envio != null

    // Alguien envió un reporte → al director y al responsable (menos a quien lo envió)
    fun nuevoReporte(reporte: ReporteResponse, autorId: Int) {
        val tipo = if (reporte.tipo == "falla") "Falla" else "Desperdicio"
        mandar(
            db.fidsDeRoles(ROLES_QUE_ATIENDEN_REPORTES, excepto = autorId),
            Aviso(
                titulo = "Nuevo reporte: $tipo en ${reporte.area}",
                cuerpo = recortar(reporte.descripcion, 120),
                datos = mapOf("pantalla" to "reportes", "reporte_id" to reporte.id.toString())
            )
        )
    }

    // Cambió el estatus de un reporte → a quien lo envió (si no fue él mismo quien lo cambió)
    fun estatusCambiado(reporte: ReporteResponse, autorId: Int, quienCambio: Int) {
        if (autorId == quienCambio) return
        val estatus = when (reporte.estatus) {
            "en_proceso" -> "En proceso"
            "resuelto" -> "Resuelto"
            else -> "Pendiente"
        }
        val cuerpo = if (reporte.estatus == "resuelto") {
            "Gracias por avisar. La dirección ya atendió tu reporte en ${reporte.area}."
        } else {
            "La dirección está atendiendo tu reporte en ${reporte.area}."
        }
        mandar(
            db.fidsDeUsuario(autorId),
            Aviso(
                titulo = "Tu reporte ${folio(reporte.id)} está: $estatus",
                cuerpo = cuerpo,
                datos = mapOf("pantalla" to "reportes", "reporte_id" to reporte.id.toString())
            )
        )
    }

    // La lectura del lunes muestra consumo con la escuela cerrada → a todos
    fun consumoFantasma(kwh: Double) {
        mandar(
            db.fidsDeTodos(),
            Aviso(
                titulo = "Consumo fantasma el fin de semana",
                cuerpo = "Con la escuela cerrada se gastaron ${String.format(Locale.ROOT, "%.1f", kwh)} kWh. " +
                    "Revisa qué se quedó encendido y repórtalo en EcoVolt.",
                datos = mapOf("pantalla" to "inicio")
            )
        )
    }

    // Toca tomar la lectura y todavía no se hace → al director y al responsable
    fun recordatorioLectura(momento: String, ultimoAviso: Boolean) {
        val (nombre, horario) = if (momento == "viernes") {
            "la lectura de cierre del viernes" to "13:00 a 15:00 h"
        } else {
            "la lectura de apertura del lunes" to "7:30 a 9:00 h"
        }
        val titulo = if (ultimoAviso) "Falta $nombre" else "Hoy toca $nombre"
        mandar(
            db.fidsDeRoles(ROLES_QUE_CAPTURAN),
            Aviso(
                titulo = titulo.replaceFirstChar { it.uppercase() },
                cuerpo = "Tómala de $horario para que EcoVolt pueda calcular el consumo del fin de semana.",
                datos = mapOf("pantalla" to "lectura")
            )
        )
    }

    // Notificación de prueba a los teléfonos de una persona. Devuelve a cuántos se mandó.
    fun prueba(usuarioId: Int, nombre: String): Int {
        val fids = db.fidsDeUsuario(usuarioId)
        mandar(fids, Aviso("¡Hola, $nombre!", "Las notificaciones de EcoVolt funcionan en este teléfono."))
        return fids.size
    }

    private fun mandar(fids: List<String>, aviso: Aviso) {
        val entrega = envio ?: return
        if (fids.isEmpty()) return
        alcance.launch {
            try {
                val invalidos = entrega.enviar(fids, aviso)
                if (invalidos.isNotEmpty()) db.eliminarDispositivos(invalidos)
            } catch (e: Exception) {
                // Una notificación que no llega no debe tumbar nada
                log.warn("No se pudo enviar la notificación \"${aviso.titulo}\": ${e.message}")
            }
        }
    }

    private fun folio(id: Int) = "ECO-" + id.toString().padStart(3, '0')

    private fun recortar(texto: String, maximo: Int) =
        if (texto.length <= maximo) texto else texto.take(maximo - 1).trimEnd() + "…"
}
