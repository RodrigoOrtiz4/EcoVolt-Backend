package com.ecovolt.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// Recuerda al director y al responsable que toca tomar la lectura, si todavía no la toman:
//   viernes 13:00 (inicio) y 14:30 (última llamada) → lectura de cierre
//   lunes   7:30 (inicio) y  8:30 (última llamada) → lectura de apertura
// Revisa cada minuto con la hora de México. Cada aviso se manda una sola vez por día (se anota en
// la tabla config), aunque el servidor se reinicie. Cada aviso tiene 30 minutos de margen por si
// el servidor estaba dormido (Render gratis se duerme cuando nadie lo usa).
class Recordatorios(
    private val db: Database,
    private val notificador: Notificador,
    private val zona: ZoneId
) {
    private val log = LoggerFactory.getLogger("Recordatorios")

    private data class Turno(val dia: DayOfWeek, val hora: LocalTime, val momento: String, val ultimo: Boolean)

    private val turnos = listOf(
        Turno(DayOfWeek.FRIDAY, LocalTime.of(13, 0), "viernes", ultimo = false),
        Turno(DayOfWeek.FRIDAY, LocalTime.of(14, 30), "viernes", ultimo = true),
        Turno(DayOfWeek.MONDAY, LocalTime.of(7, 30), "lunes", ultimo = false),
        Turno(DayOfWeek.MONDAY, LocalTime.of(8, 30), "lunes", ultimo = true),
    )

    fun iniciar(alcance: CoroutineScope): Job = alcance.launch {
        while (isActive) {
            try {
                revisar(LocalDateTime.now(zona))
            } catch (e: Exception) {
                log.warn("Error al revisar recordatorios: ${e.message}")
            }
            delay(60_000)
        }
    }

    // Devuelve los recordatorios que mandó en esta revisión (para las pruebas)
    fun revisar(ahora: LocalDateTime): List<String> {
        if (!notificador.activo) return emptyList()
        val enviados = mutableListOf<String>()
        val hoy = ahora.toLocalDate().toString()
        val hora = ahora.toLocalTime()
        for (t in turnos) {
            if (ahora.dayOfWeek != t.dia || hora < t.hora || hora >= t.hora.plusMinutes(30)) continue
            if (db.hayLectura(hoy, t.momento)) continue
            val clave = "recordatorio:$hoy:${t.momento}:${if (t.ultimo) "ultimo" else "inicio"}"
            if (db.marcarUnaVez(clave)) {
                notificador.recordatorioLectura(t.momento, t.ultimo)
                enviados.add(clave)
            }
        }
        return enviados
    }
}
