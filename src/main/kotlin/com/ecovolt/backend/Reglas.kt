package com.ecovolt.backend

import java.time.ZoneId

// Reglas compartidas por las rutas, las notificaciones y los recordatorios

// Las fechas y horas se ponen con la hora de la escuela (México), aunque el servidor
// esté en otro país (Render usa la hora UTC).
internal val ZONA_ESCUELA: ZoneId = ZoneId.of("America/Mexico_City")

// Roles que pueden guardar lecturas: los mismos que ven el botón de la cámara en la app.
// El servidor lo vuelve a revisar porque nunca debe fiarse del cliente.
internal val ROLES_QUE_CAPTURAN = setOf("DIRECTOR", "RESPONSABLE")

// Roles que ven TODOS los reportes de desperdicio o falla (los que pueden atenderlos)
internal val ROLES_QUE_ATIENDEN_REPORTES = setOf("DIRECTOR", "RESPONSABLE")

// Roles que crean grupos y ven sus códigos: el docente (sus grupos) y el director (todos)
internal val ROLES_QUE_ADMINISTRAN_GRUPOS = setOf("DOCENTE", "DIRECTOR")

// Preguntas de seguridad que puede elegir un alumno al registrarse. La app muestra esta misma
// lista; si se cambia aquí hay que cambiarla también en la app (PreguntasSeguridad.kt).
internal val PREGUNTAS_SEGURIDAD = listOf(
    "¿Cómo se llama tu primera mascota?",
    "¿Cuál es tu comida favorita?",
    "¿Cómo se llama tu mejor amigo o amiga de la infancia?",
    "¿Cuál fue el nombre de tu primera escuela?",
    "¿En qué ciudad nació tu mamá o tu papá?",
    "¿Cuál es tu película favorita?"
)
