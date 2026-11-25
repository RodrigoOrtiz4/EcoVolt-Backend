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

// Código del grupo de prueba que se crea con la base de datos (del docente1)
internal const val CODIGO_GRUPO_PRUEBA = "ECO3A1"

// Caracteres para generar códigos de grupo: sin O/0 ni I/1/L para que no se confundan al dictarlos
private const val CARACTERES_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

// Datos de seguridad de un alumno para recuperar su contraseña
data class SeguridadFila(val id: Int, val rol: String, val pregunta: String?, val respuestaHash: String?)

private val LECTURAS_INICIALES = listOf(
    LecturaResponse(1, "2026-09-18", "viernes", 1520.4, "13:09", "manual"),
    LecturaResponse(2, "2026-09-21", "lunes", 1531.9, "08:04", "ocr"),
    LecturaResponse(3, "2026-09-25", "viernes", 1590.2, "13:18", "ocr"),
    LecturaResponse(4, "2026-09-28", "lunes", 1594.1, "08:12", "ocr"),
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
    SELECT r.id, r.tipo, r.area, r.descripcion, r.fecha, r.estatus, u.nombre AS autor
    FROM reportes r JOIN usuarios u ON u.id = r.usuario_id
""".trimIndent()

private const val COLUMNAS_LECTURA = "id, fecha, momento, valor_kwh, hora, origen, nota"

// Base de datos SQLite con JDBC. Una sola conexión compartida: los métodos son
// @Synchronized para que no se pisen entre peticiones simultáneas (suficiente para este proyecto).
class Database(ruta: String) : AutoCloseable {

    private val conexion: Connection
    private val aleatorio = SecureRandom()

    init {
        if (ruta != ":memory:") File(ruta).absoluteFile.parentFile?.mkdirs()
        conexion = DriverManager.getConnection("jdbc:sqlite:$ruta")
        ejecutar("PRAGMA journal_mode = WAL")
        ejecutar("PRAGMA busy_timeout = 5000")
        crearTablas()
        migrarColumnas()
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
                salt_legacy   TEXT,
                version_sesion INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS lecturas (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha     TEXT NOT NULL,
                momento   TEXT NOT NULL CHECK (momento IN ('viernes','lunes')),
                valor_kwh REAL NOT NULL,
                hora      TEXT,
                origen    TEXT,
                nota      TEXT
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
                foto_tipo   TEXT NOT NULL,
                estatus     TEXT NOT NULL DEFAULT 'pendiente'
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
        crearTablasCursos()
        // Grupos escolares. El docente crea el grupo y comparte el código de 6 caracteres;
        // los alumnos lo escriben al registrarse. Con activo = 0 el código ya no acepta registros.
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS grupos (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo     TEXT    NOT NULL UNIQUE,
                nombre     TEXT    NOT NULL,
                docente_id INTEGER REFERENCES usuarios(id),
                activo     INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0,1)),
                creado     TEXT    NOT NULL
            )
            """.trimIndent()
        )
        // Teléfonos que reciben notificaciones push. Un teléfono pertenece a una sola persona:
        // si otra inicia sesión en él, pasa a ser de la nueva.
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS dispositivos (
                fid         TEXT PRIMARY KEY,
                usuario_id  INTEGER NOT NULL REFERENCES usuarios(id),
                plataforma  TEXT NOT NULL DEFAULT 'android',
                actualizado TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun sqlTablaCursos(nombre: String) =
        """
        CREATE TABLE IF NOT EXISTS $nombre (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            titulo           TEXT    NOT NULL,
            descripcion      TEXT    NOT NULL,
            icono            TEXT    NOT NULL,
            nivel            TEXT    NOT NULL CHECK (nivel IN ('basico','intermedio','avanzado')),
            duracion_minutos INTEGER NOT NULL,
            activo           INTEGER NOT NULL DEFAULT 1
        )
        """.trimIndent()

    // ---- Cursos de ahorro: contenido (cursos, lecciones, bloques, preguntas, opciones)
    //      y avance de cada usuario (progreso, lecciones vistas y respuestas) ----
    private fun crearTablasCursos() {
        ejecutar(sqlTablaCursos("cursos"))
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS lecciones (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                curso_id         INTEGER NOT NULL REFERENCES cursos(id),
                orden            INTEGER NOT NULL,
                titulo           TEXT    NOT NULL,
                resumen          TEXT    NOT NULL,
                duracion_minutos INTEGER NOT NULL,
                UNIQUE (curso_id, orden)
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS leccion_bloques (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                leccion_id INTEGER NOT NULL REFERENCES lecciones(id),
                orden      INTEGER NOT NULL,
                tipo       TEXT    NOT NULL CHECK (tipo IN ('parrafo','dato','ejemplo','lista')),
                titulo     TEXT,
                texto      TEXT    NOT NULL,
                UNIQUE (leccion_id, orden)
            )
            """.trimIndent()
        )
        // momento: 'inicial', 'final' o 'ambas' (la misma pregunta en las dos evaluaciones)
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS preguntas (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                curso_id    INTEGER NOT NULL REFERENCES cursos(id),
                momento     TEXT    NOT NULL CHECK (momento IN ('inicial','final','ambas')),
                orden       INTEGER NOT NULL,
                enunciado   TEXT    NOT NULL,
                explicacion TEXT    NOT NULL
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS opciones (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                pregunta_id INTEGER NOT NULL REFERENCES preguntas(id),
                orden       INTEGER NOT NULL,
                texto       TEXT    NOT NULL,
                correcta    INTEGER NOT NULL DEFAULT 0 CHECK (correcta IN (0,1))
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS progreso_cursos (
                usuario_id           INTEGER NOT NULL REFERENCES usuarios(id),
                curso_id             INTEGER NOT NULL REFERENCES cursos(id),
                calificacion_inicial INTEGER CHECK (calificacion_inicial BETWEEN 0 AND 100),
                calificacion_final   INTEGER CHECK (calificacion_final BETWEEN 0 AND 100),
                fecha_inicio         TEXT,
                fecha_fin            TEXT,
                PRIMARY KEY (usuario_id, curso_id)
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS progreso_lecciones (
                usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
                leccion_id INTEGER NOT NULL REFERENCES lecciones(id),
                fecha      TEXT    NOT NULL,
                PRIMARY KEY (usuario_id, leccion_id)
            )
            """.trimIndent()
        )
        ejecutar(
            """
            CREATE TABLE IF NOT EXISTS respuestas_evaluacion (
                usuario_id  INTEGER NOT NULL REFERENCES usuarios(id),
                pregunta_id INTEGER NOT NULL REFERENCES preguntas(id),
                momento     TEXT    NOT NULL CHECK (momento IN ('inicial','final')),
                opcion_id   INTEGER NOT NULL REFERENCES opciones(id),
                PRIMARY KEY (usuario_id, pregunta_id, momento)
            )
            """.trimIndent()
        )
    }

    // Las bases de datos creadas con la versión anterior no tienen las columnas nuevas:
    // se agregan aquí sin tocar los datos que ya existen.
    private fun migrarColumnas() {
        agregarColumnaSiFalta("usuarios", "version_sesion", "INTEGER NOT NULL DEFAULT 0")
        agregarColumnaSiFalta("lecturas", "hora", "TEXT")
        agregarColumnaSiFalta("lecturas", "origen", "TEXT")
        agregarColumnaSiFalta("lecturas", "nota", "TEXT")
        agregarColumnaSiFalta("reportes", "estatus", "TEXT NOT NULL DEFAULT 'pendiente'")
        // Registro de alumnos con código de grupo y pregunta de seguridad
        agregarColumnaSiFalta("usuarios", "grupo_id", "INTEGER REFERENCES grupos(id)")
        agregarColumnaSiFalta("usuarios", "pregunta_seguridad", "TEXT")
        agregarColumnaSiFalta("usuarios", "respuesta_hash", "TEXT")
        permitirNivelAvanzado()
    }

    // Las bases creadas antes solo aceptaban 'basico' e 'intermedio' en cursos.nivel. SQLite no
    // deja cambiar un CHECK, así que se crea la tabla nueva, se copian los cursos y se reemplaza.
    // Los ids no cambian, por lo que lecciones, preguntas y avances siguen apuntando bien.
    private fun permitirNivelAvanzado() {
        val sql = conexion.createStatement().use { st ->
            st.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'cursos'").use { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        } ?: return
        if (sql.contains("avanzado")) return
        conexion.autoCommit = false
        try {
            ejecutar("DROP TABLE IF EXISTS cursos_nueva")
            ejecutar(sqlTablaCursos("cursos_nueva"))
            ejecutar(
                "INSERT INTO cursos_nueva (id, titulo, descripcion, icono, nivel, duracion_minutos, activo) " +
                    "SELECT id, titulo, descripcion, icono, nivel, duracion_minutos, activo FROM cursos"
            )
            ejecutar("DROP TABLE cursos")
            ejecutar("ALTER TABLE cursos_nueva RENAME TO cursos")
            conexion.commit()
        } catch (e: Exception) {
            conexion.rollback()
            throw e
        } finally {
            conexion.autoCommit = true
        }
    }

    private fun agregarColumnaSiFalta(tabla: String, columna: String, definicion: String) {
        val existe = conexion.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($tabla)").use { rs ->
                var encontrada = false
                while (rs.next()) if (rs.getString("name").equals(columna, ignoreCase = true)) encontrada = true
                encontrada
            }
        }
        if (!existe) ejecutar("ALTER TABLE $tabla ADD COLUMN $columna $definicion")
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
            conexion.prepareStatement(
                "INSERT INTO lecturas (id, fecha, momento, valor_kwh, hora, origen) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                for (l in LECTURAS_INICIALES) {
                    ps.setInt(1, l.id)
                    ps.setString(2, l.fecha)
                    ps.setString(3, l.momento)
                    ps.setDouble(4, l.valorKwh)
                    ps.setString(5, l.hora)
                    ps.setString(6, l.origen)
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
        sembrarCursosFaltantes()
        if (contar("grupos") == 0) {
            // Grupo de prueba del docente1 para poder registrar alumnos desde el primer día
            conexion.prepareStatement(
                "INSERT INTO grupos (codigo, nombre, docente_id, creado) VALUES (?, ?, (SELECT id FROM usuarios WHERE usuario = 'docente1'), ?)"
            ).use { ps ->
                ps.setString(1, CODIGO_GRUPO_PRUEBA)
                ps.setString(2, "Grupo de prueba")
                ps.setString(3, java.time.LocalDate.now(ZONA_ESCUELA).toString())
                ps.executeUpdate()
            }
        }
    }

    // Agrega los cursos iniciales que todavía no estén en la base de datos (por ejemplo, los
    // avanzados en un servidor que ya tenía los primeros 3). Cada curso se carga una sola vez:
    // queda anotado en config, así que si después lo borran a propósito no vuelve a aparecer.
    private fun sembrarCursosFaltantes() {
        val existentes = conexion.createStatement().use { st ->
            st.executeQuery("SELECT id FROM cursos").use { rs ->
                val ids = mutableSetOf<Int>()
                while (rs.next()) ids.add(rs.getInt(1))
                ids
            }
        }
        val anotados = conexion.createStatement().use { st ->
            st.executeQuery("SELECT clave FROM config WHERE clave LIKE 'curso_sembrado:%'").use { rs ->
                val ids = mutableSetOf<Int>()
                while (rs.next()) rs.getString(1).substringAfter(':').toIntOrNull()?.let { ids.add(it) }
                ids
            }
        }
        val nuevos = CURSOS_INICIALES.filter { it.id !in existentes && it.id !in anotados }
        if (nuevos.isNotEmpty()) sembrarCursos(nuevos)
        // Se anotan después de guardarlos: si algo falla, se vuelve a intentar al reiniciar
        CURSOS_INICIALES.forEach { marcarUnaVez("curso_sembrado:${it.id}") }
    }

    // Carga los cursos iniciales en una sola transacción: o se guardan completos o no se guarda nada
    private fun sembrarCursos(cursos: List<CursoResponse>) {
        conexion.autoCommit = false
        try {
            for (c in cursos) {
                conexion.prepareStatement(
                    "INSERT INTO cursos (id, titulo, descripcion, icono, nivel, duracion_minutos) VALUES (?, ?, ?, ?, ?, ?)"
                ).use { ps ->
                    ps.setInt(1, c.id)
                    ps.setString(2, c.titulo)
                    ps.setString(3, c.descripcion)
                    ps.setString(4, c.icono)
                    ps.setString(5, c.nivel)
                    ps.setInt(6, c.duracionMinutos)
                    ps.executeUpdate()
                }
                for (l in c.lecciones) {
                    conexion.prepareStatement(
                        "INSERT INTO lecciones (id, curso_id, orden, titulo, resumen, duracion_minutos) VALUES (?, ?, ?, ?, ?, ?)"
                    ).use { ps ->
                        ps.setInt(1, l.id)
                        ps.setInt(2, c.id)
                        ps.setInt(3, l.orden)
                        ps.setString(4, l.titulo)
                        ps.setString(5, l.resumen)
                        ps.setInt(6, l.duracionMinutos)
                        ps.executeUpdate()
                    }
                    l.bloques.forEachIndexed { i, b ->
                        conexion.prepareStatement(
                            "INSERT INTO leccion_bloques (leccion_id, orden, tipo, titulo, texto) VALUES (?, ?, ?, ?, ?)"
                        ).use { ps ->
                            ps.setInt(1, l.id)
                            ps.setInt(2, i + 1)
                            ps.setString(3, b.tipo)
                            ps.setString(4, b.titulo)
                            ps.setString(5, b.texto)
                            ps.executeUpdate()
                        }
                    }
                }
                // Si las dos evaluaciones son la misma lista, cada pregunta se guarda una vez como 'ambas'
                val momentos = if (c.evaluacionInicial == c.evaluacionFinal) listOf("ambas" to c.evaluacionInicial)
                               else listOf("inicial" to c.evaluacionInicial, "final" to c.evaluacionFinal)
                for ((momento, preguntas) in momentos) {
                    preguntas.forEachIndexed { i, p ->
                        conexion.prepareStatement(
                            "INSERT INTO preguntas (id, curso_id, momento, orden, enunciado, explicacion) VALUES (?, ?, ?, ?, ?, ?)"
                        ).use { ps ->
                            ps.setInt(1, p.id)
                            ps.setInt(2, c.id)
                            ps.setString(3, momento)
                            ps.setInt(4, i + 1)
                            ps.setString(5, p.enunciado)
                            ps.setString(6, p.explicacion)
                            ps.executeUpdate()
                        }
                        p.opciones.forEachIndexed { j, o ->
                            conexion.prepareStatement(
                                "INSERT INTO opciones (id, pregunta_id, orden, texto, correcta) VALUES (?, ?, ?, ?, ?)"
                            ).use { ps ->
                                ps.setInt(1, o.id)
                                ps.setInt(2, p.id)
                                ps.setInt(3, j + 1)
                                ps.setString(4, o.texto)
                                ps.setInt(5, if (o.correcta) 1 else 0)
                                ps.executeUpdate()
                            }
                        }
                    }
                }
            }
            conexion.commit()
        } catch (e: Exception) {
            conexion.rollback()
            throw e
        } finally {
            conexion.autoCommit = true
        }
    }

    private fun filaDe(rs: ResultSet) = UsuarioFila(
        id = rs.getInt("id"),
        usuario = rs.getString("usuario"),
        nombre = rs.getString("nombre"),
        rol = rs.getString("rol"),
        passwordHash = rs.getString("password_hash"),
        saltLegacy = rs.getString("salt_legacy"),
        versionSesion = rs.getInt("version_sesion")
    )

    // La búsqueda no distingue mayúsculas de minúsculas (columna COLLATE NOCASE)
    @Synchronized
    fun buscarUsuario(usuario: String): UsuarioFila? =
        conexion.prepareStatement(
            "SELECT id, usuario, nombre, rol, password_hash, salt_legacy, version_sesion FROM usuarios WHERE usuario = ?"
        ).use { ps ->
            ps.setString(1, usuario)
            ps.executeQuery().use { rs -> if (rs.next()) filaDe(rs) else null }
        }

    // Crea un usuario con rol ALUMNO dentro de su grupo, con su pregunta de seguridad.
    // Devuelve null si el nombre de usuario ya existe.
    @Synchronized
    fun crearAlumno(
        usuario: String,
        nombre: String,
        passwordHash: String,
        grupoId: Int,
        pregunta: String,
        respuestaHash: String
    ): UsuarioFila? {
        try {
            conexion.prepareStatement(
                "INSERT INTO usuarios (usuario, nombre, rol, password_hash, grupo_id, pregunta_seguridad, respuesta_hash) " +
                    "VALUES (?, ?, 'ALUMNO', ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, usuario)
                ps.setString(2, nombre)
                ps.setString(3, passwordHash)
                ps.setInt(4, grupoId)
                ps.setString(5, pregunta)
                ps.setString(6, respuestaHash)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            if (e.message?.contains("UNIQUE", ignoreCase = true) == true) return null
            throw e
        }
        return buscarUsuario(usuario)
    }

    @Synchronized
    fun buscarUsuarioPorId(id: Int): UsuarioFila? =
        conexion.prepareStatement(
            "SELECT id, usuario, nombre, rol, password_hash, salt_legacy, version_sesion FROM usuarios WHERE id = ?"
        ).use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) filaDe(rs) else null }
        }

    // Versión de sesión actual del usuario, o null si ya no existe. Se compara con la del token.
    @Synchronized
    fun versionSesion(id: Int): Int? =
        conexion.prepareStatement("SELECT version_sesion FROM usuarios WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    // Guarda la contraseña nueva y sube la versión de sesión: así todos los tokens anteriores
    // (los de otros dispositivos) dejan de funcionar.
    @Synchronized
    fun cambiarContrasena(id: Int, nuevoHash: String) {
        conexion.prepareStatement(
            "UPDATE usuarios SET password_hash = ?, salt_legacy = NULL, version_sesion = version_sesion + 1 WHERE id = ?"
        ).use { ps ->
            ps.setString(1, nuevoHash)
            ps.setInt(2, id)
            ps.executeUpdate()
        }
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
            st.executeQuery("SELECT $COLUMNAS_LECTURA FROM lecturas ORDER BY fecha, id").use { rs ->
                val lista = mutableListOf<LecturaResponse>()
                while (rs.next()) lista.add(lecturaDe(rs))
                lista
            }
        }

    // La lectura más reciente (por fecha y, si empatan, la última que se guardó), o null si no hay
    @Synchronized
    fun ultimaLectura(): LecturaResponse? =
        conexion.createStatement().use { st ->
            st.executeQuery("SELECT $COLUMNAS_LECTURA FROM lecturas ORDER BY fecha DESC, id DESC LIMIT 1").use { rs ->
                if (rs.next()) lecturaDe(rs) else null
            }
        }

    private fun lecturaDe(rs: ResultSet) = LecturaResponse(
        id = rs.getInt("id"),
        fecha = rs.getString("fecha"),
        momento = rs.getString("momento"),
        valorKwh = rs.getDouble("valor_kwh"),
        hora = rs.getString("hora"),
        origen = rs.getString("origen"),
        nota = rs.getString("nota")
    )

    // Guarda una lectura nueva y la devuelve con el id que le asignó SQLite
    @Synchronized
    fun agregarLectura(
        fecha: String, momento: String, valorKwh: Double,
        hora: String?, origen: String?, nota: String?
    ): LecturaResponse {
        conexion.prepareStatement(
            "INSERT INTO lecturas (fecha, momento, valor_kwh, hora, origen, nota) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, fecha)
            ps.setString(2, momento)
            ps.setDouble(3, valorKwh)
            ps.setString(4, hora)
            ps.setString(5, origen)
            ps.setString(6, nota)
            ps.executeUpdate()
        }
        val id = conexion.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getInt(1) }
        }
        return LecturaResponse(id, fecha, momento, valorKwh, hora, origen, nota)
    }

    // ---- Reportes (RFN-19) ----

    private fun reporteDe(rs: ResultSet) = ReporteResponse(
        id = rs.getInt("id"),
        tipo = rs.getString("tipo"),
        area = rs.getString("area"),
        descripcion = rs.getString("descripcion"),
        fecha = rs.getString("fecha"),
        autor = rs.getString("autor"),
        estatus = rs.getString("estatus") ?: "pendiente"
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

    // Actualiza el estatus y devuelve el reporte ya cambiado, o null si no existe
    @Synchronized
    fun cambiarEstatusReporte(id: Int, estatus: String): ReporteResponse? {
        val cambiados = conexion.prepareStatement("UPDATE reportes SET estatus = ? WHERE id = ?").use { ps ->
            ps.setString(1, estatus)
            ps.setInt(2, id)
            ps.executeUpdate()
        }
        if (cambiados == 0) return null
        return conexion.prepareStatement("$CONSULTA_REPORTES WHERE r.id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) reporteDe(rs) else null }
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

    // ---- Notificaciones push ----

    // Da de alta (o reasigna) un teléfono a una persona
    @Synchronized
    fun registrarDispositivo(fid: String, usuarioId: Int, plataforma: String, fecha: String) {
        conexion.prepareStatement(
            """
            INSERT INTO dispositivos (fid, usuario_id, plataforma, actualizado) VALUES (?, ?, ?, ?)
            ON CONFLICT (fid) DO UPDATE SET
                usuario_id = excluded.usuario_id, plataforma = excluded.plataforma, actualizado = excluded.actualizado
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, fid)
            ps.setInt(2, usuarioId)
            ps.setString(3, plataforma)
            ps.setString(4, fecha)
            ps.executeUpdate()
        }
    }

    // Da de baja un teléfono, solo si es de esa persona. Devuelve si se borró.
    @Synchronized
    fun eliminarDispositivo(fid: String, usuarioId: Int): Boolean =
        conexion.prepareStatement("DELETE FROM dispositivos WHERE fid = ? AND usuario_id = ?").use { ps ->
            ps.setString(1, fid)
            ps.setInt(2, usuarioId)
            ps.executeUpdate() > 0
        }

    // Borra los teléfonos que Firebase dijo que ya no existen
    @Synchronized
    fun eliminarDispositivos(fids: Collection<String>) {
        conexion.prepareStatement("DELETE FROM dispositivos WHERE fid = ?").use { ps ->
            for (fid in fids) {
                ps.setString(1, fid)
                ps.executeUpdate()
            }
        }
    }

    @Synchronized
    fun fidsDeUsuario(usuarioId: Int): List<String> =
        conexion.prepareStatement("SELECT fid FROM dispositivos WHERE usuario_id = ?").use { ps ->
            ps.setInt(1, usuarioId)
            ps.executeQuery().use { rs -> leerTextos(rs) }
        }

    // Teléfonos de las personas con alguno de esos roles (menos los de `excepto`)
    @Synchronized
    fun fidsDeRoles(roles: Set<String>, excepto: Int? = null): List<String> {
        if (roles.isEmpty()) return emptyList()
        val marcas = roles.joinToString(",") { "?" }
        return conexion.prepareStatement(
            "SELECT d.fid FROM dispositivos d JOIN usuarios u ON u.id = d.usuario_id WHERE u.rol IN ($marcas) AND u.id <> ?"
        ).use { ps ->
            roles.forEachIndexed { i, rol -> ps.setString(i + 1, rol) }
            ps.setInt(roles.size + 1, excepto ?: -1)
            ps.executeQuery().use { rs -> leerTextos(rs) }
        }
    }

    @Synchronized
    fun fidsDeTodos(): List<String> =
        conexion.createStatement().use { st -> st.executeQuery("SELECT fid FROM dispositivos").use { rs -> leerTextos(rs) } }

    private fun leerTextos(rs: ResultSet): List<String> {
        val lista = mutableListOf<String>()
        while (rs.next()) lista.add(rs.getString(1))
        return lista
    }

    // Quién envió un reporte (para avisarle cuando cambie su estatus)
    @Synchronized
    fun autorDeReporte(id: Int): Int? =
        conexion.prepareStatement("SELECT usuario_id FROM reportes WHERE id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    // ¿Ya se tomó la lectura de ese día y momento? (para los recordatorios)
    @Synchronized
    fun hayLectura(fecha: String, momento: String): Boolean =
        conexion.prepareStatement("SELECT 1 FROM lecturas WHERE fecha = ? AND momento = ? LIMIT 1").use { ps ->
            ps.setString(1, fecha)
            ps.setString(2, momento)
            ps.executeQuery().use { rs -> rs.next() }
        }

    // Anota una clave en la tabla config. Devuelve true solo la primera vez: así un recordatorio
    // no se manda dos veces aunque el servidor se reinicie.
    @Synchronized
    fun marcarUnaVez(clave: String): Boolean =
        conexion.prepareStatement("INSERT OR IGNORE INTO config (clave, valor) VALUES (?, '1')").use { ps ->
            ps.setString(1, clave)
            ps.executeUpdate() > 0
        }

    // ---- Recuperación de contraseña con la pregunta de seguridad ----

    @Synchronized
    fun seguridadDe(usuario: String): SeguridadFila? =
        conexion.prepareStatement(
            "SELECT id, rol, pregunta_seguridad, respuesta_hash FROM usuarios WHERE usuario = ?"
        ).use { ps ->
            ps.setString(1, usuario)
            ps.executeQuery().use { rs ->
                if (rs.next()) SeguridadFila(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)) else null
            }
        }

    // ---- Grupos ----

    private val CONSULTA_GRUPOS = """
        SELECT g.id, g.codigo, g.nombre, g.activo, g.creado, u.nombre AS docente,
               (SELECT COUNT(*) FROM usuarios a WHERE a.grupo_id = g.id AND a.rol = 'ALUMNO') AS alumnos
        FROM grupos g LEFT JOIN usuarios u ON u.id = g.docente_id
    """.trimIndent()

    private fun grupoDe(rs: ResultSet) = GrupoResponse(
        id = rs.getInt("id"),
        codigo = rs.getString("codigo"),
        nombre = rs.getString("nombre"),
        activo = rs.getInt("activo") == 1,
        alumnos = rs.getInt("alumnos"),
        docente = rs.getString("docente"),
        creado = rs.getString("creado")
    )

    // Id del grupo con ese código si acepta registros; null si no existe o está cerrado
    @Synchronized
    fun grupoAbierto(codigo: String): Int? =
        conexion.prepareStatement("SELECT id FROM grupos WHERE codigo = ? AND activo = 1").use { ps ->
            ps.setString(1, codigo.trim().uppercase())
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }

    // docenteId = null → todos los grupos (director)
    @Synchronized
    fun grupos(docenteId: Int?): List<GrupoResponse> {
        val sql = CONSULTA_GRUPOS + (if (docenteId != null) " WHERE g.docente_id = ?" else "") + " ORDER BY g.id DESC"
        return conexion.prepareStatement(sql).use { ps ->
            if (docenteId != null) ps.setInt(1, docenteId)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<GrupoResponse>()
                while (rs.next()) lista.add(grupoDe(rs))
                lista
            }
        }
    }

    @Synchronized
    fun grupo(id: Int): Pair<GrupoResponse, Int?>? =
        conexion.prepareStatement("$CONSULTA_GRUPOS WHERE g.id = ?").use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) grupoDe(rs) else null }
        }?.let { g ->
            val docenteId = conexion.prepareStatement("SELECT docente_id FROM grupos WHERE id = ?").use { ps ->
                ps.setInt(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1).takeIf { !rs.wasNull() } else null }
            }
            g to docenteId
        }

    // Crea el grupo con un código aleatorio que no exista todavía
    @Synchronized
    fun crearGrupo(nombre: String, docenteId: Int, fecha: String): GrupoResponse {
        repeat(20) {
            val codigo = (1..6).map { CARACTERES_CODIGO[aleatorio.nextInt(CARACTERES_CODIGO.length)] }.joinToString("")
            try {
                val id = conexion.prepareStatement(
                    "INSERT INTO grupos (codigo, nombre, docente_id, creado) VALUES (?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setString(1, codigo)
                    ps.setString(2, nombre)
                    ps.setInt(3, docenteId)
                    ps.setString(4, fecha)
                    ps.executeUpdate()
                    ps.generatedKeys.use { rs -> rs.next(); rs.getInt(1) }
                }
                return grupo(id)!!.first
            } catch (e: SQLException) {
                // Código repetido (muy raro): se intenta con otro
                if (e.message?.contains("UNIQUE", ignoreCase = true) != true) throw e
            }
        }
        error("No se pudo generar un código de grupo único")
    }

    @Synchronized
    fun cambiarGrupoActivo(id: Int, activo: Boolean): GrupoResponse? {
        conexion.prepareStatement("UPDATE grupos SET activo = ? WHERE id = ?").use { ps ->
            ps.setInt(1, if (activo) 1 else 0)
            ps.setInt(2, id)
            ps.executeUpdate()
        }
        return grupo(id)?.first
    }

    // ---- Cursos de ahorro ----

    // Todos los cursos activos, completos (lecciones con bloques y evaluaciones con opciones)
    @Synchronized
    fun cursos(): List<CursoResponse> {
        val ids = conexion.createStatement().use { st ->
            st.executeQuery("SELECT id FROM cursos WHERE activo = 1 ORDER BY id").use { rs ->
                val lista = mutableListOf<Int>()
                while (rs.next()) lista.add(rs.getInt(1))
                lista
            }
        }
        return ids.mapNotNull { cursoCompleto(it) }
    }

    // Un curso activo con todo su contenido, o null si no existe
    @Synchronized
    fun curso(id: Int): CursoResponse? = cursoCompleto(id)

    private fun cursoCompleto(id: Int): CursoResponse? {
        val base = conexion.prepareStatement(
            "SELECT id, titulo, descripcion, icono, nivel, duracion_minutos FROM cursos WHERE id = ? AND activo = 1"
        ).use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                CursoResponse(
                    id = rs.getInt("id"),
                    titulo = rs.getString("titulo"),
                    descripcion = rs.getString("descripcion"),
                    icono = rs.getString("icono"),
                    nivel = rs.getString("nivel"),
                    duracionMinutos = rs.getInt("duracion_minutos"),
                    lecciones = emptyList(),
                    evaluacionInicial = emptyList(),
                    evaluacionFinal = emptyList()
                )
            }
        }
        val preguntas = preguntasDe(id)
        return base.copy(
            lecciones = leccionesDe(id),
            evaluacionInicial = preguntas.filter { it.first != "final" }.map { it.second },
            evaluacionFinal = preguntas.filter { it.first != "inicial" }.map { it.second }
        )
    }

    private fun leccionesDe(cursoId: Int): List<LeccionResponse> {
        val lecciones = conexion.prepareStatement(
            "SELECT id, orden, titulo, resumen, duracion_minutos FROM lecciones WHERE curso_id = ? ORDER BY orden"
        ).use { ps ->
            ps.setInt(1, cursoId)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<LeccionResponse>()
                while (rs.next()) {
                    lista.add(
                        LeccionResponse(
                            id = rs.getInt("id"),
                            orden = rs.getInt("orden"),
                            titulo = rs.getString("titulo"),
                            resumen = rs.getString("resumen"),
                            duracionMinutos = rs.getInt("duracion_minutos"),
                            bloques = emptyList()
                        )
                    )
                }
                lista
            }
        }
        return lecciones.map { l ->
            val bloques = conexion.prepareStatement(
                "SELECT tipo, titulo, texto FROM leccion_bloques WHERE leccion_id = ? ORDER BY orden"
            ).use { ps ->
                ps.setInt(1, l.id)
                ps.executeQuery().use { rs ->
                    val lista = mutableListOf<BloqueResponse>()
                    while (rs.next()) lista.add(BloqueResponse(rs.getString("tipo"), rs.getString("texto"), rs.getString("titulo")))
                    lista
                }
            }
            l.copy(bloques = bloques)
        }
    }

    // Preguntas del curso con su momento ('inicial', 'final' o 'ambas'), en orden
    private fun preguntasDe(cursoId: Int): List<Pair<String, PreguntaResponse>> {
        val preguntas = conexion.prepareStatement(
            "SELECT id, momento, enunciado, explicacion FROM preguntas WHERE curso_id = ? ORDER BY orden, id"
        ).use { ps ->
            ps.setInt(1, cursoId)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<Pair<String, PreguntaResponse>>()
                while (rs.next()) {
                    lista.add(
                        rs.getString("momento") to PreguntaResponse(
                            id = rs.getInt("id"),
                            enunciado = rs.getString("enunciado"),
                            opciones = emptyList(),
                            explicacion = rs.getString("explicacion")
                        )
                    )
                }
                lista
            }
        }
        return preguntas.map { (momento, p) ->
            val opciones = conexion.prepareStatement(
                "SELECT id, texto, correcta FROM opciones WHERE pregunta_id = ? ORDER BY orden, id"
            ).use { ps ->
                ps.setInt(1, p.id)
                ps.executeQuery().use { rs ->
                    val lista = mutableListOf<OpcionResponse>()
                    while (rs.next()) lista.add(OpcionResponse(rs.getInt("id"), rs.getString("texto"), rs.getInt("correcta") == 1))
                    lista
                }
            }
            momento to p.copy(opciones = opciones)
        }
    }

    // Avance de un usuario en todos los cursos que ha empezado
    @Synchronized
    fun progresoCursos(usuarioId: Int): List<ProgresoCursoResponse> {
        val cursos = conexion.prepareStatement(
            "SELECT curso_id FROM progreso_cursos WHERE usuario_id = ? ORDER BY curso_id"
        ).use { ps ->
            ps.setInt(1, usuarioId)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<Int>()
                while (rs.next()) lista.add(rs.getInt(1))
                lista
            }
        }
        return cursos.mapNotNull { progresoInterno(usuarioId, it) }
    }

    // Avance de un usuario en un curso (vacío si aún no lo empieza)
    @Synchronized
    fun progresoCurso(usuarioId: Int, cursoId: Int): ProgresoCursoResponse =
        progresoInterno(usuarioId, cursoId) ?: ProgresoCursoResponse(cursoId = cursoId)

    private fun progresoInterno(usuarioId: Int, cursoId: Int): ProgresoCursoResponse? {
        val base = conexion.prepareStatement(
            "SELECT calificacion_inicial, calificacion_final, fecha_inicio, fecha_fin FROM progreso_cursos WHERE usuario_id = ? AND curso_id = ?"
        ).use { ps ->
            ps.setInt(1, usuarioId)
            ps.setInt(2, cursoId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                ProgresoCursoResponse(
                    cursoId = cursoId,
                    calificacionInicial = rs.getInt("calificacion_inicial").takeUnless { rs.wasNull() },
                    calificacionFinal = rs.getInt("calificacion_final").takeUnless { rs.wasNull() },
                    fechaInicio = rs.getString("fecha_inicio"),
                    fechaFin = rs.getString("fecha_fin")
                )
            }
        }
        val lecciones = conexion.prepareStatement(
            """
            SELECT pl.leccion_id FROM progreso_lecciones pl JOIN lecciones l ON l.id = pl.leccion_id
            WHERE pl.usuario_id = ? AND l.curso_id = ? ORDER BY l.orden
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, usuarioId)
            ps.setInt(2, cursoId)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<Int>()
                while (rs.next()) lista.add(rs.getInt(1))
                lista
            }
        }
        val respuestas = conexion.prepareStatement(
            """
            SELECT r.pregunta_id, r.opcion_id FROM respuestas_evaluacion r JOIN preguntas p ON p.id = r.pregunta_id
            WHERE r.usuario_id = ? AND p.curso_id = ? AND r.momento = 'final' ORDER BY p.orden, p.id
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, usuarioId)
            ps.setInt(2, cursoId)
            ps.executeQuery().use { rs ->
                val lista = mutableListOf<RespuestaGuardada>()
                while (rs.next()) lista.add(RespuestaGuardada(rs.getInt(1), rs.getInt(2)))
                lista
            }
        }
        return base.copy(leccionesCompletadas = lecciones, respuestasFinales = respuestas)
    }

    // Marca una lección como vista (si ya lo estaba, no cambia nada)
    @Synchronized
    fun completarLeccion(usuarioId: Int, leccionId: Int, fecha: String) {
        conexion.prepareStatement(
            "INSERT OR IGNORE INTO progreso_lecciones (usuario_id, leccion_id, fecha) VALUES (?, ?, ?)"
        ).use { ps ->
            ps.setInt(1, usuarioId)
            ps.setInt(2, leccionId)
            ps.setString(3, fecha)
            ps.executeUpdate()
        }
    }

    // Guarda las respuestas y la calificación de una evaluación ya revisada, todo junto
    @Synchronized
    fun guardarEvaluacion(
        usuarioId: Int, cursoId: Int, momento: String,
        respuestas: Map<Int, Int>, calificacion: Int, fecha: String
    ) {
        conexion.autoCommit = false
        try {
            if (momento == "inicial") {
                conexion.prepareStatement(
                    """
                    INSERT INTO progreso_cursos (usuario_id, curso_id, calificacion_inicial, fecha_inicio) VALUES (?, ?, ?, ?)
                    ON CONFLICT (usuario_id, curso_id) DO UPDATE SET
                        calificacion_inicial = excluded.calificacion_inicial,
                        fecha_inicio = COALESCE(progreso_cursos.fecha_inicio, excluded.fecha_inicio)
                    """.trimIndent()
                ).use { ps ->
                    ps.setInt(1, usuarioId)
                    ps.setInt(2, cursoId)
                    ps.setInt(3, calificacion)
                    ps.setString(4, fecha)
                    ps.executeUpdate()
                }
            } else {
                conexion.prepareStatement(
                    "UPDATE progreso_cursos SET calificacion_final = ?, fecha_fin = ? WHERE usuario_id = ? AND curso_id = ?"
                ).use { ps ->
                    ps.setInt(1, calificacion)
                    ps.setString(2, fecha)
                    ps.setInt(3, usuarioId)
                    ps.setInt(4, cursoId)
                    ps.executeUpdate()
                }
            }
            conexion.prepareStatement(
                "INSERT OR REPLACE INTO respuestas_evaluacion (usuario_id, pregunta_id, momento, opcion_id) VALUES (?, ?, ?, ?)"
            ).use { ps ->
                for ((preguntaId, opcionId) in respuestas) {
                    ps.setInt(1, usuarioId)
                    ps.setInt(2, preguntaId)
                    ps.setString(3, momento)
                    ps.setInt(4, opcionId)
                    ps.executeUpdate()
                }
            }
            conexion.commit()
        } catch (e: Exception) {
            conexion.rollback()
            throw e
        } finally {
            conexion.autoCommit = true
        }
    }

    // Borra el avance de un usuario en un curso para que lo tome desde el principio
    @Synchronized
    fun reiniciarCurso(usuarioId: Int, cursoId: Int) {
        conexion.autoCommit = false
        try {
            conexion.prepareStatement(
                "DELETE FROM respuestas_evaluacion WHERE usuario_id = ? AND pregunta_id IN (SELECT id FROM preguntas WHERE curso_id = ?)"
            ).use { ps -> ps.setInt(1, usuarioId); ps.setInt(2, cursoId); ps.executeUpdate() }
            conexion.prepareStatement(
                "DELETE FROM progreso_lecciones WHERE usuario_id = ? AND leccion_id IN (SELECT id FROM lecciones WHERE curso_id = ?)"
            ).use { ps -> ps.setInt(1, usuarioId); ps.setInt(2, cursoId); ps.executeUpdate() }
            conexion.prepareStatement(
                "DELETE FROM progreso_cursos WHERE usuario_id = ? AND curso_id = ?"
            ).use { ps -> ps.setInt(1, usuarioId); ps.setInt(2, cursoId); ps.executeUpdate() }
            conexion.commit()
        } catch (e: Exception) {
            conexion.rollback()
            throw e
        } finally {
            conexion.autoCommit = true
        }
    }

    // Para dirección: cuántos empezaron y terminaron cada curso, y el promedio antes y después.
    // Los promedios solo toman a quienes terminaron, para comparar a las mismas personas.
    @Synchronized
    fun resumenCursos(): List<ResumenCursoResponse> =
        conexion.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT c.id, c.titulo,
                       COUNT(p.calificacion_inicial) AS iniciaron,
                       COUNT(p.calificacion_final)   AS terminaron,
                       AVG(CASE WHEN p.calificacion_final IS NOT NULL THEN p.calificacion_inicial END) AS promedio_inicial,
                       AVG(p.calificacion_final)     AS promedio_final
                FROM cursos c LEFT JOIN progreso_cursos p ON p.curso_id = c.id
                WHERE c.activo = 1
                GROUP BY c.id, c.titulo
                ORDER BY c.id
                """.trimIndent()
            ).use { rs ->
                val lista = mutableListOf<ResumenCursoResponse>()
                while (rs.next()) {
                    val promInicial = rs.getDouble("promedio_inicial").takeUnless { rs.wasNull() }
                    val promFinal = rs.getDouble("promedio_final").takeUnless { rs.wasNull() }
                    lista.add(
                        ResumenCursoResponse(
                            cursoId = rs.getInt("id"),
                            titulo = rs.getString("titulo"),
                            iniciaron = rs.getInt("iniciaron"),
                            terminaron = rs.getInt("terminaron"),
                            promedioInicial = promInicial?.let { Math.round(it * 10) / 10.0 },
                            promedioFinal = promFinal?.let { Math.round(it * 10) / 10.0 }
                        )
                    )
                }
                lista
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
