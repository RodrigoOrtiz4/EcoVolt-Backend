package com.ecovolt.backend

// Revisión de las evaluaciones de los cursos. El servidor califica con sus propias preguntas
// (las de la base de datos), así la calificación guardada no depende de lo que diga la app.
sealed class RevisionEvaluacion {
    data class Valida(
        val respuestas: Map<Int, Int>,     // pregunta -> opción elegida
        val aciertos: Int,
        val total: Int,
        val calificacion: Int              // 0 a 100, redondeada
    ) : RevisionEvaluacion()

    data class Invalida(val errores: Map<String, String>) : RevisionEvaluacion()
}

object Evaluaciones {

    // Revisa que se haya contestado cada pregunta una sola vez, con una opción de esa misma
    // pregunta, y calcula la calificación: aciertos * 100 / total (redondeada).
    fun revisar(preguntas: List<PreguntaResponse>, respuestas: List<RespuestaRequest>?): RevisionEvaluacion {
        if (preguntas.isEmpty()) {
            return RevisionEvaluacion.Invalida(mapOf("respuestas" to "Este curso no tiene preguntas para esta evaluación"))
        }
        if (respuestas.isNullOrEmpty()) {
            return RevisionEvaluacion.Invalida(mapOf("respuestas" to "Faltan las respuestas"))
        }

        val porId = preguntas.associateBy { it.id }
        val elegidas = mutableMapOf<Int, Int>()
        for (r in respuestas) {
            val preguntaId = r.preguntaId
            val opcionId = r.opcionId
            if (preguntaId == null || opcionId == null) {
                return RevisionEvaluacion.Invalida(mapOf("respuestas" to "Cada respuesta necesita pregunta_id y opcion_id"))
            }
            val pregunta = porId[preguntaId]
                ?: return RevisionEvaluacion.Invalida(mapOf("respuestas" to "La pregunta $preguntaId no es de esta evaluación"))
            if (pregunta.opciones.none { it.id == opcionId }) {
                return RevisionEvaluacion.Invalida(mapOf("respuestas" to "La opción $opcionId no es de la pregunta $preguntaId"))
            }
            if (elegidas.put(preguntaId, opcionId) != null) {
                return RevisionEvaluacion.Invalida(mapOf("respuestas" to "La pregunta $preguntaId viene contestada dos veces"))
            }
        }

        val faltan = preguntas.count { it.id !in elegidas }
        if (faltan > 0) {
            val texto = if (faltan == 1) "Falta 1 pregunta por contestar" else "Faltan $faltan preguntas por contestar"
            return RevisionEvaluacion.Invalida(mapOf("respuestas" to texto))
        }

        val aciertos = preguntas.count { p -> p.opciones.any { it.correcta && it.id == elegidas[p.id] } }
        val total = preguntas.size
        return RevisionEvaluacion.Valida(elegidas, aciertos, total, (aciertos * 100 + total / 2) / total)
    }
}
