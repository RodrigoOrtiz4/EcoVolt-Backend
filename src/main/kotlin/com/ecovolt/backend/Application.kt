package com.ecovolt.backend

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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

private const val YA_REGISTRADO = "Ese usuario ya está registrado"

// Configuración del servidor: JSON, manejo de errores y las rutas de la API.
// Recibe la base de datos para poder arrancarse igual en ejecución normal o en pruebas.
fun Application.modulo(db: Database) {
    val tokens = Tokens(db.secretoJwt())

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
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
    }
}

// Roles que pueden guardar lecturas: los mismos que ven el botón de la cámara en la app.
// El servidor lo vuelve a revisar porque nunca debe fiarse del cliente.
private val ROLES_QUE_CAPTURAN = setOf("DIRECTOR", "RESPONSABLE")

// Revisa el encabezado "Authorization: Bearer <token>".
// Si es válido devuelve el rol del usuario; si no, responde 401 y devuelve null.
private suspend fun ApplicationCall.rolDeLaSesion(tokens: Tokens): String? {
    val partes = (request.headers[HttpHeaders.Authorization] ?: "").split(" ")
    if (partes.size != 2 || partes[0] != "Bearer" || partes[1].isEmpty()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Debes iniciar sesión"))
        return null
    }
    val rol = tokens.rolDe(partes[1])
    if (rol == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Tu sesión expiró. Inicia sesión de nuevo"))
    }
    return rol
}
