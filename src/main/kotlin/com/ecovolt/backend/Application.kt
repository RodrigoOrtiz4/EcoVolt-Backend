package com.ecovolt.backend

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private const val YA_REGISTRADO = "Ese usuario ya está registrado"
private const val SIN_PREGUNTA =
    "No encontramos una pregunta de seguridad para ese usuario. Pide a tu docente o a la dirección que te ayuden."

private val FORMATO_FECHA_REPORTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Configuración del servidor: JSON, manejo de errores y las rutas de la API.
// Recibe la base de datos para poder arrancarse igual en ejecución normal o en pruebas.
fun Application.modulo(db: Database, notificador: Notificador = Notificador(db, null)) {
    val tokens = Tokens(db.secretoJwt())
    val intentos = LimiteIntentos()

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    // CORS: permite que la versión web de la app (que corre en el navegador, en otro dominio)
    // llame a esta API. La sesión viaja en el encabezado Authorization (no en cookies).
    // Con la variable CORS_ORIGENES (por ejemplo "https://ecovolt-web.onrender.com,http://localhost:8080")
    // solo se aceptan esos sitios; si no está definida se acepta cualquiera.
    install(CORS) {
        val origenes = System.getenv("CORS_ORIGENES").orEmpty()
            .split(',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
        if (origenes.isEmpty()) {
            anyHost()
        } else {
            origenes.forEach { origen ->
                val esquema = if (origen.contains("://")) origen.substringBefore("://") else "https"
                allowHost(origen.substringAfter("://"), schemes = listOf(esquema))
            }
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true      // JSON (application/json)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
        maxAgeInSeconds = 3600
    }

    install(StatusPages) {
        // Errores inesperados: se registran en consola pero nunca se filtra el detalle al cliente
        exception<Throwable> { call, causa ->
            call.application.environment.log.error("Error no controlado", causa)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error interno del servidor"))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Ruta no encontrada"))
        }
    }

    routing {
        get("/health") {
            call.respond(Salud("ok"))
        }

        route("/auth") {

            // POST /auth/login  { usuario, contrasena }
            post("/login") {
                val peticion = try {
                    call.receive<LoginRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                val usuario = peticion.usuario
                val contrasena = peticion.contrasena
                if (usuario == null || contrasena == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Faltan el usuario o la contraseña"))
                }

                // Demasiados intentos fallidos con este usuario: se bloquea un rato
                val clave = "login:" + usuario.trim().lowercase()
                intentos.bloqueadoPor(clave)?.let { faltanMs ->
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse(mensajeBloqueo(faltanMs), intentosRestantes = 0)
                    )
                }

                val fila = db.buscarUsuario(usuario.trim())
                val resultado = Passwords.verificar(contrasena, fila)
                if (fila == null || !resultado.valida) {
                    // Mensaje genérico: no se revela si el usuario existe
                    val restantes = intentos.registrarFallo(clave)
                    val mensaje = if (restantes == 0) {
                        mensajeBloqueo(LimiteIntentos.BLOQUEO_MS)
                    } else {
                        "Usuario o contraseña incorrectos"
                    }
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(mensaje, intentosRestantes = restantes)
                    )
                }
                intentos.limpiar(clave)

                if (resultado.migrar) {
                    // Venía con SHA-256: se reemplaza por bcrypt ahora que conocemos su contraseña
                    db.migrarABcrypt(fila.id, Passwords.hashear(contrasena))
                }
                call.respond(AuthResponse(tokens.crear(fila.id, fila.rol, fila.versionSesion), fila.publico()))
            }

            // POST /auth/register  { nombre, usuario, contrasena, codigo_grupo, pregunta, respuesta }
            // Solo se registran alumnos, y únicamente con el código de 6 caracteres de un grupo abierto.
            // El rol nunca se toma de la petición. El personal (director, docentes, responsable)
            // usa la cuenta que le asigna la dirección.
            post("/register") {
                val peticion = try {
                    call.receive<RegistroRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }

                val errores = mutableMapOf<String, String>()
                Validators.codigoGrupo(peticion.codigoGrupo)?.let { errores["codigo_grupo"] = it }
                Validators.nombre(peticion.nombre)?.let { errores["nombre"] = it }
                Validators.usuario(peticion.usuario)?.let { errores["usuario"] = it }
                Validators.contrasena(peticion.contrasena)?.let { errores["contrasena"] = it }
                Validators.preguntaSeguridad(peticion.pregunta)?.let { errores["pregunta"] = it }
                Validators.respuestaSeguridad(peticion.respuesta)?.let { errores["respuesta"] = it }
                if (errores.isNotEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Revisa los datos del formulario", errores))
                }

                // Aquí ya están validados, así que no pueden ser nulos
                val nombre = peticion.nombre!!.trim()
                val usuario = peticion.usuario!!.trim()
                val contrasena = peticion.contrasena!!

                val grupoId = db.grupoAbierto(peticion.codigoGrupo!!)
                if (grupoId == null) {
                    val mensaje = "Ese código de grupo no existe o ya no acepta registros. Pídeselo a tu docente."
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(mensaje, mapOf("codigo_grupo" to mensaje)))
                }

                if (db.buscarUsuario(usuario) != null) {
                    return@post call.respond(HttpStatusCode.Conflict, ErrorResponse(YA_REGISTRADO, mapOf("usuario" to YA_REGISTRADO)))
                }

                val nuevo = db.crearAlumno(
                    usuario = usuario,
                    nombre = nombre,
                    passwordHash = Passwords.hashear(contrasena),
                    grupoId = grupoId,
                    pregunta = peticion.pregunta!!.trim(),
                    respuestaHash = Respuestas.hashear(peticion.respuesta!!)
                ) ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse(YA_REGISTRADO, mapOf("usuario" to YA_REGISTRADO)))

                call.respond(HttpStatusCode.Created, AuthResponse(tokens.crear(nuevo.id, nuevo.rol, nuevo.versionSesion), nuevo.publico()))
            }

            // GET /auth/preguntas → lista de preguntas de seguridad que se pueden elegir
            get("/preguntas") {
                call.respond(PREGUNTAS_SEGURIDAD)
            }

            // POST /auth/recuperar/pregunta  { usuario } → { pregunta }
            // Primer paso para que un alumno recupere su contraseña sin ayuda.
            post("/recuperar/pregunta") {
                val peticion = try {
                    call.receive<PreguntaSeguridadRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                val usuario = peticion.usuario?.trim().orEmpty()
                if (usuario.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Escribe tu usuario", mapOf("usuario" to "Escribe tu usuario")))
                }
                val fila = db.seguridadDe(usuario)
                if (fila == null || fila.rol != "ALUMNO" || fila.pregunta == null || fila.respuestaHash == null) {
                    // Mismo mensaje si no existe, si es personal o si es una cuenta anterior sin pregunta
                    return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(SIN_PREGUNTA, mapOf("usuario" to SIN_PREGUNTA))
                    )
                }
                call.respond(PreguntaSeguridadResponse(fila.pregunta))
            }

            // POST /auth/recuperar  { usuario, respuesta, nueva }
            // Si la respuesta coincide, guarda la contraseña nueva y cierra las sesiones abiertas.
            // Después de 5 respuestas equivocadas se bloquea 15 minutos, igual que el inicio de sesión.
            post("/recuperar") {
                val peticion = try {
                    call.receive<RecuperarContrasenaRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                val usuario = peticion.usuario?.trim().orEmpty()
                val clave = "recuperar:" + usuario.lowercase()
                intentos.bloqueadoPor(clave)?.let { faltanMs ->
                    return@post call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(mensajeBloqueo(faltanMs), intentosRestantes = 0))
                }

                val errores = mutableMapOf<String, String>()
                if (usuario.isEmpty()) errores["usuario"] = "Escribe tu usuario"
                if (peticion.respuesta.isNullOrBlank()) errores["respuesta"] = "Escribe tu respuesta"
                Validators.contrasenaSegura(peticion.nueva)?.let { errores["nueva"] = it }
                if (errores.isNotEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Revisa los datos del formulario", errores))
                }

                val fila = db.seguridadDe(usuario)?.takeIf { it.rol == "ALUMNO" }
                if (!Respuestas.verificar(peticion.respuesta!!, fila?.respuestaHash) || fila == null) {
                    val restantes = intentos.registrarFallo(clave)
                    val mensaje = if (restantes == 0) mensajeBloqueo(LimiteIntentos.BLOQUEO_MS) else "La respuesta no es correcta"
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(mensaje, mapOf("respuesta" to mensaje), intentosRestantes = restantes)
                    )
                }
                intentos.limpiar(clave)
                db.cambiarContrasena(fila.id, Passwords.hashear(peticion.nueva!!))
                call.respond(MensajeResponse("Contraseña actualizada. Inicia sesión con tu nueva contraseña."))
            }

            // POST /auth/cambiar-contrasena  { actual, nueva }  (requiere token)
            // Al cambiarla se cierran las sesiones de todos los dispositivos (también la de este):
            // la app vuelve a pedir iniciar sesión con la contraseña nueva.
            post("/cambiar-contrasena") {
                val sesion = call.sesionActual(tokens, db) ?: return@post // ya respondió 401

                val peticion = try {
                    call.receive<CambioContrasenaRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }

                val clave = "cambio:${sesion.id}"
                intentos.bloqueadoPor(clave)?.let { faltanMs ->
                    return@post call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(mensajeBloqueo(faltanMs)))
                }

                val errores = mutableMapOf<String, String>()
                if (peticion.actual.isNullOrEmpty()) errores["actual"] = "Escribe tu contraseña actual"
                Validators.contrasenaSegura(peticion.nueva)?.let { errores["nueva"] = it }
                if (errores.isEmpty() && peticion.actual == peticion.nueva) {
                    errores["nueva"] = "Debe ser distinta de la contraseña actual"
                }
                if (errores.isNotEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Revisa los datos del formulario", errores))
                }

                val fila = db.buscarUsuarioPorId(sesion.id)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Debes iniciar sesión"))
                if (!Passwords.verificar(peticion.actual!!, fila).valida) {
                    // 400 y no 401: la sesión sigue siendo válida, solo se equivocó de contraseña
                    intentos.registrarFallo(clave)
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("La contraseña actual no es correcta", mapOf("actual" to "La contraseña actual no es correcta"))
                    )
                }
                intentos.limpiar(clave)

                db.cambiarContrasena(fila.id, Passwords.hashear(peticion.nueva!!))
                call.respond(MensajeResponse("Contraseña actualizada. Inicia sesión con tu nueva contraseña."))
            }
        }

        route("/lecturas") {

            // GET /lecturas  (requiere "Authorization: Bearer <token>")
            get {
                call.rolDeLaSesion(tokens, db) ?: return@get // ya respondió 401
                call.respond(db.lecturas())
            }

            // POST /lecturas  { fecha, momento, valor_kwh }  (requiere token)
            // Guarda la lectura que la app leyó del medidor con la cámara (OCR) y el usuario confirmó.
            // El OCR se hace en el celular; al servidor solo llega el número ya revisado.
            post {
                val rol = call.rolDeLaSesion(tokens, db) ?: return@post // ya respondió 401
                if (rol !in ROLES_QUE_CAPTURAN) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Solo el director o el responsable de lecturas pueden capturar lecturas")
                    )
                }

                val peticion = try {
                    call.receive<NuevaLecturaRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }

                val errores = mutableMapOf<String, String>()
                Validators.fecha(peticion.fecha)?.let { errores["fecha"] = it }
                Validators.momento(peticion.momento)?.let { errores["momento"] = it }
                Validators.valorKwh(peticion.valorKwh)?.let { errores["valor_kwh"] = it }
                Validators.origen(peticion.origen)?.let { errores["origen"] = it }
                Validators.nota(peticion.nota)?.let { errores["nota"] = it }
                if (errores.isNotEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Revisa los datos de la lectura", errores))
                }

                // Aquí ya están validados, así que no pueden ser nulos
                val valor = peticion.valorKwh!!
                val nota = peticion.nota?.trim()?.takeIf { it.isNotEmpty() }

                // El medidor solo avanza. Si el número es menor que el anterior, o se escribió mal
                // o se cambió el medidor: en ese caso se exige una nota que lo explique.
                val anterior = db.ultimaLectura()
                if (anterior != null && valor < anterior.valorKwh && nota == null) {
                    val mensaje = "La lectura es menor que la anterior (${"%.1f".format(anterior.valorKwh)} kWh). " +
                        "Corrige el número o explica en una nota si se cambió el medidor."
                    return@post call.respond(HttpStatusCode.Conflict, ErrorResponse(mensaje, mapOf("nota" to mensaje)))
                }

                val nueva = db.agregarLectura(
                    fecha = peticion.fecha!!.trim(),
                    momento = peticion.momento!!.trim(),
                    valorKwh = valor,
                    hora = LocalTime.now(ZONA_ESCUELA).format(FORMATO_HORA),
                    origen = peticion.origen?.trim()?.takeIf { it.isNotEmpty() } ?: "manual",
                    nota = nota
                )
                call.respond(HttpStatusCode.Created, nueva)

                // Lectura del lunes: lo que subió desde el viernes se gastó con la escuela cerrada
                if (nueva.momento == "lunes" && anterior != null && anterior.momento == "viernes") {
                    val finDeSemana = nueva.valorKwh - anterior.valorKwh
                    if (finDeSemana > UMBRAL_CONSUMO_FANTASMA_KWH) notificador.consumoFantasma(finDeSemana)
                }
            }
        }

        // ---- RFN-19: reportes de desperdicio o falla con foto, área y descripción ----
        route("/reportes") {

            // POST /reportes  { tipo, area, descripcion, foto_base64 }  (requiere token, cualquier rol)
            // Cualquier persona de la escuela puede reportar una luz encendida o una falla.
            post {
                val sesion = call.sesionActual(tokens, db) ?: return@post // ya respondió 401

                val peticion = try {
                    call.receive<NuevoReporteRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }

                val errores = mutableMapOf<String, String>()
                Validators.tipoReporte(peticion.tipo)?.let { errores["tipo"] = it }
                Validators.area(peticion.area)?.let { errores["area"] = it }
                Validators.descripcion(peticion.descripcion)?.let { errores["descripcion"] = it }
                val foto = Validators.foto(peticion.fotoBase64)
                if (foto is FotoRevisada.Invalida) errores["foto"] = foto.mensaje
                if (errores.isNotEmpty() || foto !is FotoRevisada.Valida) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Revisa los datos del reporte", errores))
                }

                // Aquí ya están validados. El autor sale del token y la fecha la pone el servidor:
                // así nadie puede mandar un reporte a nombre de otro ni con otra fecha.
                val nuevo = db.agregarReporte(
                    usuarioId = sesion.id,
                    tipo = peticion.tipo!!.trim(),
                    area = peticion.area!!.trim(),
                    descripcion = peticion.descripcion!!.trim(),
                    fecha = LocalDateTime.now(ZONA_ESCUELA).format(FORMATO_FECHA_REPORTE),
                    foto = foto.bytes,
                    fotoTipo = foto.tipoMime
                )
                call.respond(HttpStatusCode.Created, nuevo)
                notificador.nuevoReporte(nuevo, autorId = sesion.id)
            }

            // GET /reportes  (requiere token)
            // El director y el responsable ven todos (son quienes los atienden); los demás, solo los suyos.
            get {
                val sesion = call.sesionActual(tokens, db) ?: return@get
                val soloDe = if (sesion.rol in ROLES_QUE_ATIENDEN_REPORTES) null else sesion.id
                call.respond(db.reportes(soloDe))
            }

            // GET /reportes/{id}/foto  (requiere token) → la imagen tal cual (image/jpeg o image/png)
            get("/{id}/foto") {
                val sesion = call.sesionActual(tokens, db) ?: return@get
                val id = call.parameters["id"]?.toIntOrNull()
                val foto = id?.let { db.fotoReporte(it) }
                // Si el reporte no es suyo y no le toca atenderlo, se responde 404 igual que si no
                // existiera: así no se revela qué reportes hay.
                if (foto == null || (sesion.rol !in ROLES_QUE_ATIENDEN_REPORTES && foto.usuarioId != sesion.id)) {
                    return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Reporte no encontrado"))
                }
                call.respondBytes(foto.bytes, ContentType.parse(foto.tipoMime))
            }

            // PATCH /reportes/{id}/estatus  { estatus }  (solo director o responsable)
            // "pendiente" → "en_proceso" → "resuelto". Quien lo envió ve el cambio en su lista.
            patch("/{id}/estatus") {
                val sesion = call.sesionActual(tokens, db) ?: return@patch
                if (sesion.rol !in ROLES_QUE_ATIENDEN_REPORTES) {
                    return@patch call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Solo el director o el responsable pueden cambiar el estatus")
                    )
                }
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Reporte no encontrado"))

                val peticion = try {
                    call.receive<CambioEstatusRequest>()
                } catch (e: Exception) {
                    return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                Validators.estatusReporte(peticion.estatus)?.let { mensaje ->
                    return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse(mensaje, mapOf("estatus" to mensaje)))
                }

                val actualizado = db.cambiarEstatusReporte(id, peticion.estatus!!.trim())
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Reporte no encontrado"))
                call.respond(actualizado)
                db.autorDeReporte(id)?.let { autor -> notificador.estatusCambiado(actualizado, autor, quienCambio = sesion.id) }
            }
        }

        // ---- RFN-25: consejos de ahorro para la escuela y el hogar ----
        // GET /consejos            → todos
        // GET /consejos?ambito=hogar → solo los del hogar
        // No requiere token: son información pública y así se ven aunque la sesión haya vencido.
        get("/consejos") {
            val ambito = call.request.queryParameters["ambito"]?.trim()?.lowercase()
            Validators.ambito(ambito)?.let { mensaje ->
                return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(mensaje))
            }
            call.respond(db.consejos(ambito))
        }

        // ---- Grupos: códigos para que los alumnos se registren ----
        route("/grupos") {

            // GET /grupos → el docente ve sus grupos; el director, todos
            get {
                val sesion = call.sesionActual(tokens, db) ?: return@get
                if (sesion.rol !in ROLES_QUE_ADMINISTRAN_GRUPOS) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Solo docentes y el director ven los grupos"))
                }
                call.respond(db.grupos(if (sesion.rol == "DIRECTOR") null else sesion.id))
            }

            // POST /grupos  { nombre } → crea el grupo con un código nuevo de 6 caracteres
            post {
                val sesion = call.sesionActual(tokens, db) ?: return@post
                if (sesion.rol !in ROLES_QUE_ADMINISTRAN_GRUPOS) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Solo docentes y el director pueden crear grupos"))
                }
                val peticion = try {
                    call.receive<NuevoGrupoRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                Validators.nombreGrupo(peticion.nombre)?.let { mensaje ->
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(mensaje, mapOf("nombre" to mensaje)))
                }
                val grupo = db.crearGrupo(peticion.nombre!!.trim(), sesion.id, LocalDate.now(ZONA_ESCUELA).toString())
                call.respond(HttpStatusCode.Created, grupo)
            }

            // PATCH /grupos/{id}  { activo } → abre o cierra el código para nuevos registros
            // (los alumnos que ya se registraron no se ven afectados)
            patch("/{id}") {
                val sesion = call.sesionActual(tokens, db) ?: return@patch
                val id = call.parameters["id"]?.toIntOrNull()
                val encontrado = id?.let { db.grupo(it) }
                // Un docente solo puede cambiar sus grupos; si no es suyo se responde como si no existiera
                if (encontrado == null || sesion.rol !in ROLES_QUE_ADMINISTRAN_GRUPOS ||
                    (sesion.rol != "DIRECTOR" && encontrado.second != sesion.id)
                ) {
                    return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Grupo no encontrado"))
                }
                val peticion = try {
                    call.receive<CambioGrupoRequest>()
                } catch (e: Exception) {
                    return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                val activo = peticion.activo
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Indica si el grupo acepta registros (activo)"))
                call.respond(db.cambiarGrupoActivo(id!!, activo)!!)
            }
        }

        // ---- Notificaciones push (Firebase Cloud Messaging) ----
        route("/dispositivos") {

            // POST /dispositivos  { fid, plataforma }  (requiere token)
            // El teléfono recibirá las notificaciones de quien tiene la sesión. La app lo manda al
            // iniciar sesión y cada vez que se abre (Firebase puede cambiar el identificador).
            post {
                val sesion = call.sesionActual(tokens, db) ?: return@post
                val peticion = try {
                    call.receive<DispositivoRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                val fid = peticion.fid?.trim()
                Validators.fid(fid)?.let { mensaje ->
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(mensaje, mapOf("fid" to mensaje)))
                }
                db.registrarDispositivo(
                    fid = fid!!,
                    usuarioId = sesion.id,
                    plataforma = peticion.plataforma?.trim()?.lowercase()?.takeIf { it in setOf("android", "ios", "web") } ?: "android",
                    fecha = LocalDateTime.now(ZONA_ESCUELA).format(FORMATO_FECHA_REPORTE)
                )
                call.respond(MensajeResponse("Notificaciones activadas en este teléfono"))
            }

            // DELETE /dispositivos/{fid}  (requiere token) → al cerrar sesión deja de recibir avisos
            delete("/{fid}") {
                val sesion = call.sesionActual(tokens, db) ?: return@delete
                val fid = call.parameters["fid"]?.trim().orEmpty()
                db.eliminarDispositivo(fid, sesion.id)
                call.respond(MensajeResponse("Este teléfono ya no recibirá notificaciones"))
            }
        }

        // POST /notificaciones/prueba  (requiere token) → manda una notificación a tus propios teléfonos
        post("/notificaciones/prueba") {
            val sesion = call.sesionActual(tokens, db) ?: return@post
            if (!notificador.activo) {
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Las notificaciones no están configuradas en el servidor (falta FIREBASE_CREDENTIALS)")
                )
            }
            val nombre = db.buscarUsuarioPorId(sesion.id)?.nombre?.substringBefore(' ') ?: "EcoVolt"
            val enviados = notificador.prueba(sesion.id, nombre)
            if (enviados == 0) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("No tienes teléfonos registrados. Abre la app con tu sesión y acepta las notificaciones.")
                )
            }
            call.respond(MensajeResponse("Se envió una notificación de prueba a $enviados teléfono(s)"))
        }

        // ---- Cursos de ahorro: evaluación inicial → lecciones → evaluación final ----
        // Todas las rutas requieren token (cualquier rol). La app por ahora trae los cursos dentro;
        // estas rutas quedan listas para cuando los tome de aquí.
        route("/cursos") {

            // GET /cursos → cursos completos (lecciones con bloques y evaluaciones con opciones)
            get {
                call.sesionActual(tokens, db) ?: return@get
                call.respond(db.cursos())
            }

            // GET /cursos/progreso → avance de quien hizo la petición en cada curso que empezó
            get("/progreso") {
                val sesion = call.sesionActual(tokens, db) ?: return@get
                call.respond(db.progresoCursos(sesion.id))
            }

            // GET /cursos/resumen → por curso: cuántos empezaron, cuántos terminaron y promedios
            // (solo director o responsable)
            get("/resumen") {
                val sesion = call.sesionActual(tokens, db) ?: return@get
                if (sesion.rol !in ROLES_QUE_ATIENDEN_REPORTES) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Solo el director o el responsable pueden ver el resumen de los cursos")
                    )
                }
                call.respond(db.resumenCursos())
            }

            // GET /cursos/{id} → un curso
            get("/{id}") {
                call.sesionActual(tokens, db) ?: return@get
                val curso = call.parameters["id"]?.toIntOrNull()?.let { db.curso(it) }
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Curso no encontrado"))
                call.respond(curso)
            }

            // POST /cursos/{id}/evaluacion  { momento, respuestas: [{ pregunta_id, opcion_id }] }
            // Califica con las preguntas de la base de datos y guarda el resultado.
            // Orden obligatorio: inicial → todas las lecciones → final. Cada una se presenta una vez
            // (para repetir, se reinicia el curso con DELETE /cursos/{id}/progreso).
            post("/{id}/evaluacion") {
                val sesion = call.sesionActual(tokens, db) ?: return@post
                val curso = call.parameters["id"]?.toIntOrNull()?.let { db.curso(it) }
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Curso no encontrado"))

                val peticion = try {
                    call.receive<EvaluacionRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }
                Validators.momentoEvaluacion(peticion.momento)?.let { mensaje ->
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(mensaje, mapOf("momento" to mensaje)))
                }
                val momento = peticion.momento!!.trim()

                val progreso = db.progresoCurso(sesion.id, curso.id)
                val conflicto = when {
                    momento == "inicial" && progreso.calificacionInicial != null ->
                        "Ya presentaste la evaluación inicial de este curso. Para repetirla, reinicia el curso."
                    momento == "final" && progreso.calificacionInicial == null ->
                        "Primero presenta la evaluación inicial."
                    momento == "final" && progreso.calificacionFinal != null ->
                        "Ya presentaste la evaluación final de este curso. Para repetirla, reinicia el curso."
                    momento == "final" -> {
                        val faltan = curso.lecciones.count { it.id !in progreso.leccionesCompletadas }
                        when (faltan) {
                            0 -> null
                            1 -> "Te falta 1 lección antes de la evaluación final."
                            else -> "Te faltan $faltan lecciones antes de la evaluación final."
                        }
                    }
                    else -> null
                }
                if (conflicto != null) {
                    return@post call.respond(HttpStatusCode.Conflict, ErrorResponse(conflicto))
                }

                val preguntas = if (momento == "inicial") curso.evaluacionInicial else curso.evaluacionFinal
                when (val revision = Evaluaciones.revisar(preguntas, peticion.respuestas)) {
                    is RevisionEvaluacion.Invalida -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Revisa tus respuestas", revision.errores)
                    )
                    is RevisionEvaluacion.Valida -> {
                        db.guardarEvaluacion(
                            usuarioId = sesion.id,
                            cursoId = curso.id,
                            momento = momento,
                            respuestas = revision.respuestas,
                            calificacion = revision.calificacion,
                            fecha = LocalDate.now(ZONA_ESCUELA).toString()
                        )
                        call.respond(
                            HttpStatusCode.Created,
                            EvaluacionResponse(
                                aciertos = revision.aciertos,
                                total = revision.total,
                                calificacion = revision.calificacion,
                                progreso = db.progresoCurso(sesion.id, curso.id)
                            )
                        )
                    }
                }
            }

            // POST /cursos/{id}/lecciones/{leccionId} → marca la lección como vista (después de la inicial)
            post("/{id}/lecciones/{leccionId}") {
                val sesion = call.sesionActual(tokens, db) ?: return@post
                val curso = call.parameters["id"]?.toIntOrNull()?.let { db.curso(it) }
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Curso no encontrado"))
                val leccionId = call.parameters["leccionId"]?.toIntOrNull()
                if (leccionId == null || curso.lecciones.none { it.id == leccionId }) {
                    return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Lección no encontrada"))
                }
                if (db.progresoCurso(sesion.id, curso.id).calificacionInicial == null) {
                    return@post call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("Las lecciones se abren al terminar la evaluación inicial.")
                    )
                }
                db.completarLeccion(sesion.id, leccionId, LocalDate.now(ZONA_ESCUELA).toString())
                call.respond(db.progresoCurso(sesion.id, curso.id))
            }

            // DELETE /cursos/{id}/progreso → borra el avance para tomar el curso desde el principio
            delete("/{id}/progreso") {
                val sesion = call.sesionActual(tokens, db) ?: return@delete
                val curso = call.parameters["id"]?.toIntOrNull()?.let { db.curso(it) }
                    ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Curso no encontrado"))
                db.reiniciarCurso(sesion.id, curso.id)
                call.respond(MensajeResponse("Se borró tu avance en \"${curso.titulo}\". Empieza con la evaluación inicial."))
            }
        }
    }
}

// Mismo umbral que la app: más de 5 kWh con la escuela cerrada es consumo fantasma
private const val UMBRAL_CONSUMO_FANTASMA_KWH = 5.0

// "Espera 15 minutos..." redondeando hacia arriba
private fun mensajeBloqueo(faltanMs: Long): String {
    val minutos = ceil(faltanMs / 60_000.0).toInt().coerceAtLeast(1)
    val unidad = if (minutos == 1) "minuto" else "minutos"
    return "Demasiados intentos fallidos. Espera $minutos $unidad e intenta de nuevo."
}

// Revisa el encabezado "Authorization: Bearer <token>".
// Si es válido devuelve quién es (id y rol); si no, responde 401 y devuelve null.
// También revisa que la versión de sesión del token siga siendo la del usuario: si cambió su
// contraseña en otro dispositivo, los tokens anteriores ya no sirven.
private suspend fun ApplicationCall.sesionActual(tokens: Tokens, db: Database): Sesion? {
    val partes = (request.headers[HttpHeaders.Authorization] ?: "").split(" ")
    if (partes.size != 2 || partes[0] != "Bearer" || partes[1].isEmpty()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Debes iniciar sesión"))
        return null
    }
    val sesion = tokens.sesionDe(partes[1])
    if (sesion == null || db.versionSesion(sesion.id) != sesion.version) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Tu sesión expiró. Inicia sesión de nuevo"))
        return null
    }
    return sesion
}

// Igual que sesionActual, pero solo con el rol (lo usan las rutas de lecturas)
private suspend fun ApplicationCall.rolDeLaSesion(tokens: Tokens, db: Database): String? = sesionActual(tokens, db)?.rol
