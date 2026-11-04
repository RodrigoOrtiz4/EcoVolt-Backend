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

// Consejos de ahorro (RFN-25). Viven en el servidor para poder cambiarlos sin publicar otra versión de la app.
private val CONSEJOS_INICIALES = listOf(
    // ---- Escuela ----
    ConsejoResponse(1, "escuela", "Hábitos", "🙋",
        "Nombra un guardián de la energía",
        "En cada grupo, un alumno por semana se encarga de apagar luces, ventiladores y el proyector al terminar la última clase."),
    ConsejoResponse(2, "escuela", "Iluminación", "☀️",
        "Aprovecha la luz natural",
        "Abre cortinas y persianas. Si la fila de lámparas junto a la ventana no hace falta, apágala: el salón sigue bien iluminado."),
    ConsejoResponse(3, "escuela", "Equipos", "🔌",
        "Cuidado con el consumo fantasma",
        "Proyectores, bocinas y computadoras en espera siguen gastando. Desconéctalos el viernes: EcoVolt compara la lectura del viernes con la del lunes para detectarlo."),
    ConsejoResponse(4, "escuela", "Equipos", "💻",
        "Suspensión automática en el centro de cómputo",
        "Configura las computadoras para apagar la pantalla a los 5 minutos y suspenderse a los 15 minutos sin uso."),
    ConsejoResponse(5, "escuela", "Climatización", "🌡️",
        "Aire acondicionado a 24-25 °C",
        "Con el aire encendido mantén puertas y ventanas cerradas. Cada grado más frío aumenta bastante el consumo."),
    ConsejoResponse(6, "escuela", "Cocina", "🧊",
        "Refrigeradores de la cafetería",
        "Revisa que el empaque de la puerta cierre bien, no metas comida caliente y no los pegues a la pared: necesitan espacio para ventilar."),
    ConsejoResponse(7, "escuela", "Hábitos", "📋",
        "Revisión del viernes",
        "Antes de cerrar, recorre salones, baños y oficinas con una lista: luces, ventiladores, equipos y llaves de agua. Reporta en EcoVolt lo que encuentres encendido."),
    ConsejoResponse(8, "escuela", "Iluminación", "💡",
        "Cambia a focos y lámparas LED",
        "Gastan hasta 75 % menos que los focos incandescentes y duran muchos años más."),
    // ---- Hogar ----
    ConsejoResponse(9, "hogar", "Equipos", "🔌",
        "Usa multicontactos con interruptor",
        "Conecta la tele, la consola y el módem en un multicontacto y apágalo al dormir o al salir: así cortas el consumo en espera."),
    ConsejoResponse(10, "hogar", "Iluminación", "💡",
        "Focos LED en los lugares que más usas",
        "Empieza por la cocina, la sala y el comedor, que son los focos que más horas pasan encendidos."),
    ConsejoResponse(11, "hogar", "Cocina", "🧊",
        "Refrigerador eficiente",
        "Déjalo a unos 10 cm de la pared, ábrelo lo menos posible y prueba el empaque: si una hoja de papel se desliza con la puerta cerrada, hay que cambiarlo."),
    ConsejoResponse(12, "hogar", "Lavado", "🧺",
        "Lava y plancha con cargas completas",
        "Usa la lavadora con carga completa y agua fría. Junta la ropa y plancha todo de una vez para no calentar la plancha varias veces."),
    ConsejoResponse(13, "hogar", "Hábitos", "📺",
        "Apaga, no dejes en espera",
        "Apaga la tele y la consola desde el botón o el multicontacto. Bajar un poco el brillo de la pantalla también ayuda."),
    ConsejoResponse(14, "hogar", "Climatización", "🌀",
        "Ventilador y aire con medida",
        "Si usas aire acondicionado, ponlo en 24-25 °C y limpia sus filtros cada mes. Un ventilador gasta mucho menos."),
    ConsejoResponse(15, "hogar", "Hábitos", "🧾",
        "Revisa tu recibo de luz",
        "Compara los kWh de tu recibo con el periodo anterior. Si subieron sin razón, busca aparatos que se queden encendidos."),
    ConsejoResponse(16, "hogar", "Equipos", "🔋",
        "Desconecta los cargadores",
        "Cuando el celular termine de cargar, desconecta el cargador del contacto: aunque sea poco, sigue consumiendo."),
)

// Consulta base de los reportes: se une con usuarios para mandar el nombre de quien lo envió.
// La foto no se incluye; se pide aparte.
private val CONSULTA_REPORTES = """
    SELECT r.id, r.tipo, r.area, r.descripcion, r.fecha, u.nombre AS autor
    FROM reportes r JOIN usuarios u ON u.id = r.usuario_id
""".trimIndent()

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
        // Reportes de desperdicio o falla. La foto se guarda dentro de la base de datos (BLOB)
        // para que todo quede en un solo archivo, igual que el resto de los datos.
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS reportes (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario_id  INTEGER NOT NULL REFERENCES usuarios(id),
                tipo        TEXT NOT NULL CHECK (tipo IN ('desperdicio','falla')),
                area        TEXT NOT NULL,
                descripcion TEXT NOT NULL,
                fecha       TEXT NOT NULL,
                foto        BLOB NOT NULL,
                foto_tipo   TEXT NOT NULL
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS consejos (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                ambito    TEXT NOT NULL CHECK (ambito IN ('escuela','hogar')),
                categoria TEXT NOT NULL,
                icono     TEXT NOT NULL,
                titulo    TEXT NOT NULL,
                texto     TEXT NOT NULL
            )
            """.trimIndent()
        )
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
        if (contar("consejos") == 0) {
            conexion.prepareStatement(
                "INSERT INTO consejos (id, ambito, categoria, icono, titulo, texto) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                for (c in CONSEJOS_INICIALES) {
                    ps.setInt(1, c.id)
                    ps.setString(2, c.ambito)
                    ps.setString(3, c.categoria)
                    ps.setString(4, c.icono)
                    ps.setString(5, c.titulo)
                    ps.setString(6, c.texto)
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

    // ---- Reportes (RFN-19) ----

    private fun reporteDe(rs: ResultSet) = ReporteResponse(
        id = rs.getInt("id"),
        tipo = rs.getString("tipo"),
        area = rs.getString("area"),
        descripcion = rs.getString("descripcion"),
        fecha = rs.getString("fecha"),
        autor = rs.getString("autor")
    )

    // Guarda un reporte con su foto y lo devuelve ya con su id (sin la foto)
    @Synchronized
    fun agregarReporte(
        usuarioId: Int, tipo: String, area: String, descripcion: String,
        fecha: String, foto: ByteArray, fotoTipo: String
    ): ReporteResponse {
        conexion.prepareStatement(
            "INSERT INTO reportes (usuario_id, tipo, area, descripcion, fecha, foto, foto_tipo) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setInt(1, usuarioId)
            ps.setString(2, tipo)
            ps.setString(3, area)
            ps.setString(4, descripcion)
            ps.setString(5, fecha)
            ps.setBytes(6, foto)
            ps.setString(7, fotoTipo)
            ps.executeUpdate()
        }
        val id = conexion.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getInt(1) }
        }
        return conexion.prepareStatement("$CONSULTA_REPORTES WHERE r.id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> rs.next(); reporteDe(rs) }
        }
    }

    // Los más nuevos primero. Con usuarioId solo devuelve los de esa persona; con null, todos.
    @Synchronized
    fun reportes(usuarioId: Int?): List<ReporteResponse> {
        val sql = if (usuarioId == null) "$CONSULTA_REPORTES ORDER BY r.id DESC"
                  else "$CONSULTA_REPORTES WHERE r.usuario_id = ? ORDER BY r.id DESC"
        return conexion.prepareStatement(sql).use { ps ->
            if (usuarioId != null) ps.setInt(1, usuarioId)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<ReporteResponse>()
                while (rs.next()) lista.add(reporteDe(rs))
                lista
            }
        }
    }

    // La foto de un reporte y de quién es (para revisar el permiso), o null si no existe
    @Synchronized
    fun fotoReporte(id: Int): FotoReporte? =
        conexion.prepareStatement("SELECT foto, foto_tipo, usuario_id FROM reportes WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) FotoReporte(rs.getBytes("foto"), rs.getString("foto_tipo"), rs.getInt("usuario_id")) else null
            }
        }

    // ---- Consejos (RFN-25) ----

    // Con ambito ("escuela" u "hogar") filtra; con null devuelve todos
    @Synchronized
    fun consejos(ambito: String?): List<ConsejoResponse> {
        val sql = if (ambito == null) "SELECT * FROM consejos ORDER BY id"
                  else "SELECT * FROM consejos WHERE ambito = ? ORDER BY id"
        return conexion.prepareStatement(sql).use { ps ->
            if (ambito != null) ps.setString(1, ambito)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<ConsejoResponse>()
                while (rs.next()) {
                    lista.add(
                        ConsejoResponse(
                            id = rs.getInt("id"),
                            ambito = rs.getString("ambito"),
                            categoria = rs.getString("categoria"),
                            icono = rs.getString("icono"),
                            titulo = rs.getString("titulo"),
                            texto = rs.getString("texto")
                        )
                    )
                }
                lista
            }
        }
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
