package com.ecovolt.backend

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64

// Resultado de revisar la foto de un reporte: la imagen ya decodificada y lista para guardar,
// o el mensaje de error. Así la foto se decodifica una sola vez.
sealed interface FotoRevisada {
    class Valida(val bytes: ByteArray, val tipoMime: String) : FotoRevisada
    class Invalida(val mensaje: String) : FotoRevisada
}

// Mismas reglas que valida la app, repetidas aquí porque el servidor nunca debe fiarse del cliente.
// Cada función devuelve el mensaje de error, o null si el valor es válido.
object Validators {

    // La app reduce la foto a ~200 KB; 3 MB deja margen sin permitir archivos gigantes
    private const val MAX_FOTO_BYTES = 3 * 1024 * 1024
    // Los primeros bytes de un archivo dicen qué tipo de imagen es (su "firma")
    private val FIRMA_PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

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

    // Contraseña nueva al cambiarla: más estricta que la del registro (igual que pide la app)
    fun contrasenaSegura(valor: String?): String? = when {
        valor.isNullOrEmpty() -> "Escribe la nueva contraseña"
        valor.length < 8 -> "Debe tener al menos 8 caracteres"
        valor.length > 32 -> "Debe tener máximo 32 caracteres"
        valor.any { it.isWhitespace() } -> "No puede llevar espacios"
        valor.none { it.isUpperCase() } -> "Debe incluir al menos una mayúscula"
        valor.none { it.isDigit() } -> "Debe incluir al menos un número"
        valor.toByteArray(Charsets.UTF_8).size > 72 -> "Contiene demasiados caracteres especiales"
        else -> null
    }

    // ---- Registro de alumnos: código de grupo y pregunta de seguridad ----

    private val formatoCodigo = Regex("^[A-Z0-9]{6}$")

    // 6 letras o números. Se compara en mayúsculas: "eco3a1" y "ECO3A1" son el mismo código.
    fun codigoGrupo(valor: String?): String? {
        val t = valor?.trim()?.uppercase().orEmpty()
        return when {
            t.isEmpty() -> "Escribe el código de tu grupo"
            t.length != 6 -> "El código tiene 6 caracteres"
            !formatoCodigo.matches(t) -> "Solo letras y números, sin espacios"
            else -> null
        }
    }

    fun preguntaSeguridad(valor: String?): String? = when {
        valor.isNullOrBlank() -> "Elige una pregunta de seguridad"
        valor.trim() !in PREGUNTAS_SEGURIDAD -> "Elige una de las preguntas de la lista"
        else -> null
    }

    fun respuestaSeguridad(valor: String?): String? {
        val t = normalizarRespuesta(valor.orEmpty())
        return when {
            t.isEmpty() -> "Escribe tu respuesta"
            t.length < 2 -> "Debe tener al menos 2 caracteres"
            t.length > 40 -> "Debe tener máximo 40 caracteres"
            else -> null
        }
    }

    // Nombre del grupo que crea el docente, por ejemplo "3° A matutino"
    fun nombreGrupo(valor: String?): String? {
        val t = valor?.trim().orEmpty()
        return when {
            t.isEmpty() -> "Escribe el nombre del grupo"
            t.length < 2 -> "Debe tener al menos 2 caracteres"
            t.length > 30 -> "Debe tener máximo 30 caracteres"
            else -> null
        }
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

    // Cómo se obtuvo el número: con la foto (OCR) o escrito a mano. Es opcional.
    fun origen(valor: String?): String? = when (valor?.trim()) {
        null, "", "ocr", "manual" -> null
        else -> "El origen debe ser \"ocr\" o \"manual\""
    }

    // Nota para dirección (por ejemplo, cuando se cambió el medidor). Es opcional.
    fun nota(valor: String?): String? {
        val t = valor?.trim().orEmpty()
        return when {
            t.isEmpty() -> null
            t.length < 10 -> "Explica el cambio con al menos 10 caracteres"
            t.length > 300 -> "Debe tener máximo 300 caracteres"
            else -> null
        }
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

    // ---- Reportes de desperdicio o falla (POST /reportes) ----

    fun tipoReporte(valor: String?): String? = when (valor?.trim()) {
        "desperdicio", "falla" -> null
        null, "" -> "Indica si es un desperdicio o una falla"
        else -> "El tipo debe ser \"desperdicio\" o \"falla\""
    }

    fun area(valor: String?): String? {
        val t = valor?.trim().orEmpty()
        return when {
            t.isEmpty() -> "Indica el área (por ejemplo: Salón 3B)"
            t.length < 3 -> "Debe tener al menos 3 caracteres"
            t.length > 50 -> "Debe tener máximo 50 caracteres"
            else -> null
        }
    }

    fun descripcion(valor: String?): String? {
        val t = valor?.trim().orEmpty()
        return when {
            t.isEmpty() -> "Describe lo que viste"
            t.length < 10 -> "Escribe al menos 10 caracteres para que se entienda"
            t.length > 500 -> "Debe tener máximo 500 caracteres"
            else -> null
        }
    }

    // La foto es obligatoria y debe ser un JPG o PNG real (se revisa su firma, no solo lo que diga la app)
    fun foto(base64: String?): FotoRevisada {
        val t = base64?.trim().orEmpty()
        if (t.isEmpty()) return FotoRevisada.Invalida("Falta la foto del reporte")

        // Cada 4 caracteres de Base64 son 3 bytes: se revisa el tamaño ANTES de decodificar
        if (t.length > (MAX_FOTO_BYTES / 3 + 1) * 4) return FotoRevisada.Invalida("La foto es demasiado grande (máximo 3 MB)")

        val bytes = try {
            Base64.getDecoder().decode(t)
        } catch (e: IllegalArgumentException) {
            return FotoRevisada.Invalida("La foto no tiene un formato válido")
        }

        val esJpg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val esPng = bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(FIRMA_PNG)
        return when {
            esJpg -> FotoRevisada.Valida(bytes, "image/jpeg")
            esPng -> FotoRevisada.Valida(bytes, "image/png")
            else -> FotoRevisada.Invalida("La foto debe ser una imagen JPG o PNG")
        }
    }

    fun estatusReporte(valor: String?): String? = when (valor?.trim()) {
        "pendiente", "en_proceso", "resuelto" -> null
        null, "" -> "Falta el estatus"
        else -> "El estatus debe ser \"pendiente\", \"en_proceso\" o \"resuelto\""
    }

    // Filtro opcional de GET /consejos?ambito=...
    fun ambito(valor: String?): String? = when (valor) {
        null, "escuela", "hogar" -> null
        else -> "El ámbito debe ser \"escuela\" o \"hogar\""
    }

    // ID de instalación de Firebase (FID): 22 caracteres de letras, números, "-" y "_".
    // Se aceptan hasta 300 por si en el futuro se registran tokens de FCM (llevan ":").
    fun fid(valor: String?): String? = when {
        valor.isNullOrBlank() -> "Falta el identificador del teléfono"
        !Regex("^[A-Za-z0-9_:\\-]{10,300}$").matches(valor) -> "El identificador del teléfono no es válido"
        else -> null
    }

    // Evaluación de un curso: la del principio o la del final
    fun momentoEvaluacion(valor: String?): String? = when (valor?.trim()) {
        "inicial", "final" -> null
        null, "" -> "Falta el momento de la evaluación"
        else -> "El momento debe ser \"inicial\" o \"final\""
    }
}
