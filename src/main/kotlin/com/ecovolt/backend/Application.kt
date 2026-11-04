package com.ecovolt.backend

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val YA_REGISTRADO = "Ese usuario ya está registrado"

// La fecha de los reportes se pone con la hora de la escuela (México), aunque el servidor
// esté en otro país (Render usa la hora UTC).
private val ZONA_ESCUELA: ZoneId = ZoneId.of("America/Mexico_City")
private val FORMATO_FECHA_REPORTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

// Configuración del servidor: JSON, manejo de errores y las rutas de la API.
// Recibe la base de datos para poder arrancarse igual en ejecución normal o en pruebas.
fun Application.modulo(db: Database) {
    val tokens = Tokens(db.secretoJwt())

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
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

                val fila = db.buscarUsuario(usuario.trim())
                val resultado = Passwords.verificar(contrasena, fila)
                if (fila == null || !resultado.valida) {
                    // Mensaje genérico: no se revela si el usuario existe
                    return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Usuario o contraseña incorrectos"))
                }

                if (resultado.migrar) {
                    // Venía con SHA-256: se reemplaza por bcrypt ahora que conocemos su contraseña
                    db.migrarABcrypt(fila.id, Passwords.hashear(contrasena))
                }
                call.respond(AuthResponse(tokens.crear(fila.id, fila.rol), fila.publico()))
            }

            // POST /auth/register  { nombre, usuario, contrasena }
            // Todo registro entra como ALUMNO; el rol nunca se toma de la petición.
            post("/register") {
                val peticion = try {
                    call.receive<RegistroRequest>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("JSON no válido"))
                }

                val errores = mutableMapOf<String, String>()
                Validators.nombre(peticion.nombre)?.let { errores["nombre"] = it }
                Validators.usuario(peticion.usuario)?.let { errores["usuario"] = it }
                Validators.contrasena(peticion.contrasena)?.let { errores["contrasena"] = it }
                if (errores.isNotEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Revisa los datos del formulario", errores))
                }

                // Aquí ya están validados, así que no pueden ser nulos
                val nombre = peticion.nombre!!.trim()
                val usuario = peticion.usuario!!.trim()
                val contrasena = peticion.contrasena!!

                if (db.buscarUsuario(usuario) != null) {
                    return@post call.respond(HttpStatusCode.Conflict, ErrorResponse(YA_REGISTRADO, mapOf("usuario" to YA_REGISTRADO)))
                }

                val nuevo = db.crearAlumno(usuario, nombre, Passwords.hashear(contrasena))
                    ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse(YA_REGISTRADO, mapOf("usuario" to YA_REGISTRADO)))

                call.respond(HttpStatusCode.Created, AuthResponse(tokens.crear(nuevo.id, nuevo.rol), nuevo.publico()))
            }
        }

        route("/lecturas") {

            // GET /lecturas  (requiere "Authorization: Bearer <token>")
            get {
                call.rolDeLaSesion(tokens) ?: return@get // ya respondió 401
                call.respond(db.lecturas())
            }

            // POST /lecturas  { fecha, momento, valor_kwh }  (requiere token)
            // Guarda la lectura que la app leyó del medidor con la cámara (OCR) y el usuario confirmó.
            // El OCR se hace en el celular; al servidor solo llega el número ya revisado.
            post {
                val rol = call.rolDeLaSesion(tokens) ?: return@post // ya respondió 401
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
                if (errores.isNotEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Revisa los datos de la lectura", errores))
                }

                // Aquí ya están validados, así que no pueden ser nulos
                val nueva = db.agregarLectura(
                    fecha = peticion.fecha!!.trim(),
                    momento = peticion.momento!!.trim(),
                    valorKwh = peticion.valorKwh!!
                )
                call.respond(HttpStatusCode.Created, nueva)
            }
        }

        // ---- RFN-19: reportes de desperdicio o falla con foto, área y descripción ----
        route("/reportes") {

            // POST /reportes  { tipo, area, descripcion, foto_base64 }  (requiere token, cualquier rol)
            // Cualquier persona de la escuela puede reportar una luz encendida o una falla.
            post {
                val sesion = call.sesionActual(tokens) ?: return@post // ya respondió 401

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
            }

            // GET /reportes  (requiere token)
            // El director y el responsable ven todos (son quienes los atienden); los demás, solo los suyos.
            get {
                val sesion = call.sesionActual(tokens) ?: return@get
                val soloDe = if (sesion.rol in ROLES_QUE_ATIENDEN_REPORTES) null else sesion.id
                call.respond(db.reportes(soloDe))
            }

            // GET /reportes/{id}/foto  (requiere token) → la imagen tal cual (image/jpeg o image/png)
            get("/{id}/foto") {
                val sesion = call.sesionActual(tokens) ?: return@get
                val id = call.parameters["id"]?.toIntOrNull()
                val foto = id?.let { db.fotoReporte(it) }
                // Si el reporte no es suyo y no le toca atenderlo, se responde 404 igual que si no
                // existiera: así no se revela qué reportes hay.
                if (foto == null || (sesion.rol !in ROLES_QUE_ATIENDEN_REPORTES && foto.usuarioId != sesion.id)) {
                    return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Reporte no encontrado"))
                }
                call.respondBytes(foto.bytes, ContentType.parse(foto.tipoMime))
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
    }
}

// Roles que pueden guardar lecturas: los mismos que ven el botón de la cámara en la app.
// El servidor lo vuelve a revisar porque nunca debe fiarse del cliente.
private val ROLES_QUE_CAPTURAN = setOf("DIRECTOR", "RESPONSABLE")

// Roles que ven TODOS los reportes de desperdicio o falla (los que pueden atenderlos)
private val ROLES_QUE_ATIENDEN_REPORTES = setOf("DIRECTOR", "RESPONSABLE")

// Revisa el encabezado "Authorization: Bearer <token>".
// Si es válido devuelve quién es (id y rol); si no, responde 401 y devuelve null.
private suspend fun ApplicationCall.sesionActual(tokens: Tokens): Sesion? {
    val partes = (request.headers[HttpHeaders.Authorization] ?: "").split(" ")
    if (partes.size != 2 || partes[0] != "Bearer" || partes[1].isEmpty()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Debes iniciar sesión"))
        return null
    }
    val sesion = tokens.sesionDe(partes[1])
    if (sesion == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Tu sesión expiró. Inicia sesión de nuevo"))
    }
    return sesion
}

// Igual que sesionActual, pero solo con el rol (lo usan las rutas de lecturas)
private suspend fun ApplicationCall.rolDeLaSesion(tokens: Tokens): String? = sesionActual(tokens)?.rol
