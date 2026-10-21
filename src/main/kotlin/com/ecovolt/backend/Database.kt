package com.ecovolt.backend

import java.io.File
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

// Usuarios predefinidos que la app ya tenía (hash SHA-256 con sal).
// Se conservan tal cual y pasan a bcrypt automáticamente en su primer inicio de sesión.
private data class UsuarioInicial(val id: Int, val usuario: String, val nombre: String, val rol: String, val salt: String, val hash: String)

private val USUARIOS_INICIALES = listOf(
    UsuarioInicial(1, "director", "Director del plantel", "DIRECTOR",
        "1ea6e24e91c5247b", "b779e721e271b2b4ac86b7dd5eb67e9ea3c99514a2c100a3dc49a230ab6c5151"),
    UsuarioInicial(2, "docente1", "Docente de prueba", "DOCENTE",
        "837c705731ed6f80", "a8054f40021c3ea65e1b9c1e5538ab02744b8ef5f1f1ae43fc5f48bd61034a78"),
    UsuarioInicial(3, "alumno1", "Alumno de prueba", "ALUMNO",
        "9d403aa1a73aef97", "031f2eb08914eda4c27ae69d349fcef379c4a90333735fa5e22bab33787e9547"),
    UsuarioInicial(4, "responsable", "Responsable de lecturas", "RESPONSABLE",
        "4d379435dd70d3e7", "bf47fd604e130a212abc32663ef14e9ede070df05ab0d756f93b5227a8c1edd6"),
)

private val LECTURAS_INICIALES = listOf(
    LecturaResponse(1, "2026-09-18", "viernes", 1520.4),
    LecturaResponse(2, "2026-09-21", "lunes", 1531.9),
    LecturaResponse(3, "2026-09-25", "viernes", 1590.2),
    LecturaResponse(4, "2026-09-28", "lunes", 1594.1),
)

// Base de datos SQLite con JDBC. Una sola conexión compartida: los métodos son
// @Synchronized para que no se pisen entre peticiones simultáneas (suficiente para este proyecto).
class Database(ruta: String) : AutoCloseable {

    private val conexion: Connection

    init {
        if (ruta != ":memory:") File(ruta).absoluteFile.parentFile?.mkdirs()
        conexion = DriverManager.getConnection("jdbc:sqlite:$ruta")
        ejecutar("PRAGMA journal_mode = WAL")
        ejecutar("PRAGMA busy_timeout = 5000")
        crearTablas()
        sembrarDatos()
    }

    private fun ejecutar(sql: String) {
        conexion.createStatement().use { it.execute(sql) }
    }

    private fun crearTablas() {
        // Cada sentencia por separado: el controlador JDBC solo ejecuta una por llamada
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS usuarios (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario       TEXT NOT NULL UNIQUE COLLATE NOCASE,
                nombre        TEXT NOT NULL,
                rol           TEXT NOT NULL CHECK (rol IN ('DIRECTOR','DOCENTE','ALUMNO','RESPONSABLE')),
                password_hash TEXT NOT NULL,
                salt_legacy   TEXT
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS lecturas (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha     TEXT NOT NULL,
                momento   TEXT NOT NULL CHECK (momento IN ('viernes','lunes')),
                valor_kwh REAL NOT NULL
            )
            """.trimIndent()
        )
        ejecutar("CREATE TABLE IF NOT EXISTS config (clave TEXT PRIMARY KEY, valor TEXT NOT NULL)")
    }

    private fun contar(tabla: String): Int =
        conexion.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM $tabla").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    private fun sembrarDatos() {
        if (contar("usuarios") == 0) {
            conexion.prepareStatement(
                "INSERT INTO usuarios (id, usuario, nombre, rol, password_hash, salt_legacy) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                for (u in USUARIOS_INICIALES) {
                    ps.setInt(1, u.id)
                    ps.setString(2, u.usuario)
                    ps.setString(3, u.nombre)
                    ps.setString(4, u.rol)
                    ps.setString(5, u.hash)
                    ps.setString(6, u.salt)
                    ps.executeUpdate()
                }
            }
        }
        if (contar("lecturas") == 0) {
            conexion.prepareStatement("INSERT INTO lecturas (id, fecha, momento, valor_kwh) VALUES (?, ?, ?, ?)").use { ps ->
                for (l in LECTURAS_INICIALES) {
                    ps.setInt(1, l.id)
                    ps.setString(2, l.fecha)
                    ps.setString(3, l.momento)
                    ps.setDouble(4, l.valorKwh)
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun filaDe(rs: ResultSet) = UsuarioFila(
        id = rs.getInt("id"),
        usuario = rs.getString("usuario"),
        nombre = rs.getString("nombre"),
        rol = rs.getString("rol"),
        passwordHash = rs.getString("password_hash"),
        saltLegacy = rs.getString("salt_legacy")
    )

    // La búsqueda no distingue mayúsculas de minúsculas (columna COLLATE NOCASE)
    @Synchronized
    fun buscarUsuario(usuario: String): UsuarioFila? =
        conexion.prepareStatement(
            "SELECT id, usuario, nombre, rol, password_hash, salt_legacy FROM usuarios WHERE usuario = ?"
        ).use { ps ->
            ps.setString(1, usuario)
            ps.executeQuery().use { rs -> if (rs.next()) filaDe(rs) else null }
        }

    // Crea un usuario con rol ALUMNO. Devuelve null si el nombre de usuario ya existe.
    @Synchronized
    fun crearAlumno(usuario: String, nombre: String, passwordHash: String): UsuarioFila? {
        try {
            conexion.prepareStatement(
                "INSERT INTO usuarios (usuario, nombre, rol, password_hash) VALUES (?, ?, 'ALUMNO', ?)"
            ).use { ps ->
                ps.setString(1, usuario)
                ps.setString(2, nombre)
                ps.setString(3, passwordHash)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            if (e.message?.contains("UNIQUE", ignoreCase = true) == true) return null
            throw e
        }
        return buscarUsuario(usuario)
    }

    // Reemplaza el hash SHA-256 antiguo por uno bcrypt
    @Synchronized
    fun migrarABcrypt(id: Int, nuevoHash: String) {
        conexion.prepareStatement("UPDATE usuarios SET password_hash = ?, salt_legacy = NULL WHERE id = ?").use { ps ->
            ps.setString(1, nuevoHash)
            ps.setInt(2, id)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun lecturas(): List<LecturaResponse> =
        conexion.createStatement().use { st ->
            st.executeQuery("SELECT id, fecha, momento, valor_kwh FROM lecturas ORDER BY fecha, id").use { rs ->
                val lista = mutableListOf<LecturaResponse>()
                while (rs.next()) {
                    lista.add(LecturaResponse(rs.getInt("id"), rs.getString("fecha"), rs.getString("momento"), rs.getDouble("valor_kwh")))
                }
                lista
            }
        }

    // Guarda una lectura nueva y la devuelve con el id que le asignó SQLite
    @Synchronized
    fun agregarLectura(fecha: String, momento: String, valorKwh: Double): LecturaResponse {
        conexion.prepareStatement("INSERT INTO lecturas (fecha, momento, valor_kwh) VALUES (?, ?, ?)").use { ps ->
            ps.setString(1, fecha)
            ps.setString(2, momento)
            ps.setDouble(3, valorKwh)
            ps.executeUpdate()
        }
        val id = conexion.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getInt(1) }
        }
        return LecturaResponse(id, fecha, momento, valorKwh)
    }

    // Secreto para firmar los tokens. Se genera una sola vez y se guarda en la base de datos,
    // así las sesiones sobreviven a reinicios sin configurar nada. Se puede sobrescribir con JWT_SECRET.
    @Synchronized
    fun secretoJwt(): String {
        System.getenv("JWT_SECRET")?.takeIf { it.isNotBlank() }?.let { return it }

        val existente = conexion.prepareStatement("SELECT valor FROM config WHERE clave = 'jwt_secret'").use { ps ->
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString("valor") else null }
        }
        if (existente != null) return existente

        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val nuevo = bytes.joinToString("") { "%02x".format(it) }
        conexion.prepareStatement("INSERT INTO config (clave, valor) VALUES ('jwt_secret', ?)").use { ps ->
            ps.setString(1, nuevo)
            ps.executeUpdate()
        }
        return nuevo
    }

    override fun close() = conexion.close()
}
