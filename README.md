# EcoVolt Backend (Kotlin + Ktor)

API REST de EcoVolt escrita en **Kotlin** con **Ktor**. Se abre y se ejecuta desde **Android Studio**. Maneja el registro e inicio de sesión de usuarios, las lecturas del medidor, los reportes de desperdicio o falla con foto, los consejos de ahorro y los cursos de ahorro de energía (proyecto `MyApplication`).

Es equivalente al backend en Node.js: **mismas rutas, mismo formato JSON y mismos usuarios predefinidos**, por lo que la app funciona igual con cualquiera de los dos. La excepción son las rutas de `/cursos` (la app todavía no las usa), `/dispositivos`, `/notificaciones`, `/grupos` y la recuperación de contraseña, que solo están aquí. Además, aquí el registro **exige el código de grupo y la pregunta de seguridad**, así que la app nueva necesita este backend. **Para las notificaciones push usa este backend.**

- Kotlin 2.4.20, Ktor 3.5.1 (motor Netty), Gradle 9.5.1 (mismas versiones que la app)
- SQLite (`sqlite-jdbc`), contraseñas con **bcrypt**, sesiones con **JWT**
- Notificaciones push con **Firebase Admin SDK 9.12.0**

## Requisitos

- Android Studio (reciente) con su JDK incluido, o cualquier JDK 17 o superior
- Conexión a internet la primera vez (Gradle descarga las dependencias)

## Cómo ejecutarlo en Android Studio

1. Descomprime el proyecto en su **propia carpeta**, por ejemplo `C:\Users\Dell-G15\AndroidStudioProjects\ecovolt-backend-kotlin`. No lo metas dentro de la carpeta de la app.
2. En Android Studio: **File → Open** y selecciona la carpeta que contiene `settings.gradle.kts`. Si pregunta, elige **Trust Project**.
3. Espera a que termine la sincronización de Gradle (barra de progreso abajo a la derecha). La primera vez tarda varios minutos.
4. Comprueba el JDK: **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** y elige el JDK incluido de Android Studio (`jbr-21`, `jbr-17` o similar).
5. Ejecuta el servidor, con cualquiera de estas dos formas:
   - **Panel Gradle** (pestaña "Gradle" en el borde derecho): `ecovolt-backend-kotlin → Tasks → application → run`, doble clic.
   - **Terminal** (pestaña "Terminal", abajo): `.\gradlew.bat run`
6. Debe aparecer en la consola:
   ```
   EcoVolt API (Kotlin) escuchando en el puerto 3000
     Emulador de Android   : http://10.0.2.2:3000
     Celular físico (Wi-Fi): http://192.168.1.50:3000
   ... Responding at http://0.0.0.0:3000
   ```
7. Comprueba que responde abriendo `http://localhost:3000/health` en el navegador: debe mostrar `{"estado":"ok"}`.

**Para detenerlo:** botón cuadrado rojo **Stop** de Android Studio, o **Ctrl + C** si lo ejecutaste en una terminal.

> No ejecutes este backend y el de Node al mismo tiempo: ambos usan el puerto 3000. Detén uno antes de iniciar el otro.

## Cómo ejecutarlo solo con la terminal (sin Android Studio)

Dentro de la carpeta del proyecto, con un JDK 17+ instalado (`java -version`):

```
.\gradlew.bat run        (Windows)
./gradlew run            (Linux / macOS)
```

## Variables de entorno opcionales

`PORT` (por defecto 3000), `DB_PATH` (por defecto `data/ecovolt.db`) y `JWT_SECRET` (si no se define, se genera y se guarda en la base de datos).

Para la versión web de la app: `CORS_ORIGENES`, las direcciones desde donde se puede usar la API en el navegador, separadas por comas (por ejemplo `https://ecovolt-web.netlify.app,http://localhost:8080`). Si no se define, se acepta cualquier sitio; la sesión viaja en el encabezado `Authorization`, no en cookies.

Para las notificaciones push: `FIREBASE_CREDENTIALS` (el contenido del JSON de la cuenta de servicio) o `FIREBASE_CREDENTIALS_PATH` (la ruta al archivo). En tu computadora basta con dejar el archivo como `firebase-credenciales.json` en la carpeta del proyecto. Sin ninguno, el servidor funciona igual pero sin notificaciones (ver más abajo).

## Endpoints

Igual que el backend en Node. Los errores traen `{ "mensaje": "..." }`.

| Método | Ruta | Descripción | Requiere token |
|---|---|---|---|
| GET | `/health` | Comprueba que el servidor está activo | No |
| POST | `/auth/register` | Registro de alumnos con código de grupo y pregunta de seguridad (siempre rol ALUMNO) | No |
| GET | `/auth/preguntas` | Lista de preguntas de seguridad que se pueden elegir | No |
| POST | `/auth/recuperar/pregunta` | Devuelve la pregunta de seguridad de un alumno | No |
| POST | `/auth/recuperar` | Cambia la contraseña de un alumno si responde bien su pregunta (5 intentos; luego bloquea 15 min) | No |
| POST | `/auth/login` | Inicia sesión (máximo 5 intentos fallidos seguidos; luego bloquea 15 min) | No |
| POST | `/auth/cambiar-contrasena` | Cambia la contraseña y cierra la sesión en todos los dispositivos | Sí |
| GET | `/lecturas` | Lecturas del medidor | Sí |
| POST | `/lecturas` | Guarda una lectura capturada con la cámara (OCR) | Sí (Director o Responsable) |
| POST | `/reportes` | Envía un reporte de desperdicio o falla con foto (RFN-19) | Sí (cualquier rol) |
| GET | `/reportes` | Reportes: todos para Director/Responsable, los propios para los demás | Sí |
| GET | `/reportes/{id}/foto` | Foto de un reporte (`image/jpeg` o `image/png`) | Sí |
| PATCH | `/reportes/{id}/estatus` | Cambia el estatus de un reporte | Sí (Director o Responsable) |
| GET | `/consejos` | Consejos de ahorro para escuela y hogar (RFN-25). Filtro opcional `?ambito=escuela` o `?ambito=hogar` | No |
| POST | `/dispositivos` | Da de alta el teléfono (FID de Firebase) para recibir notificaciones push | Sí |
| DELETE | `/dispositivos/{fid}` | Da de baja el teléfono al cerrar sesión | Sí |
| POST | `/notificaciones/prueba` | Manda una notificación de prueba a tus propios teléfonos | Sí |
| GET | `/grupos` | Grupos con su código: el docente ve los suyos, el director todos | Sí (Docente o Director) |
| POST | `/grupos` | Crea un grupo; el servidor genera su código de 6 caracteres | Sí (Docente o Director) |
| PATCH | `/grupos/{id}` | Abre o cierra el código para registros nuevos (`{ "activo": false }`) | Sí (dueño del grupo o Director) |
| GET | `/cursos` | Cursos completos: lecciones con su contenido y evaluaciones inicial y final con opciones | Sí |
| GET | `/cursos/{id}` | Un curso | Sí |
| GET | `/cursos/progreso` | Tu avance en cada curso que empezaste | Sí |
| POST | `/cursos/{id}/evaluacion` | Envía la evaluación inicial o final; el servidor la califica y la guarda | Sí |
| POST | `/cursos/{id}/lecciones/{leccionId}` | Marca una lección como vista | Sí |
| DELETE | `/cursos/{id}/progreso` | Borra tu avance para tomar el curso desde el principio | Sí |
| GET | `/cursos/resumen` | Por curso: cuántos empezaron y terminaron, y el promedio inicial y final | Sí (Director o Responsable) |

- `POST /auth/register` recibe `{ "codigo_grupo": "ECO3A1", "nombre", "usuario", "contrasena", "pregunta", "respuesta" }` y responde `201`, `400` (datos inválidos o código de grupo que no existe o está cerrado, con `errores` por campo) o `409` (usuario repetido).
  - Solo se registran **alumnos**. El personal (director, docentes y responsable) usa la cuenta que le asigna la dirección.
  - `codigo_grupo`: 6 letras o números; no distingue mayúsculas. La base nueva trae el grupo de prueba **`ECO3A1`** (del `docente1`).
  - `pregunta`: una de las de `GET /auth/preguntas` (son las mismas que muestra la app). `respuesta`: 2 a 40 caracteres; se guarda con bcrypt después de quitar mayúsculas, acentos y espacios de sobra.
- Recuperar contraseña (solo alumnos): `POST /auth/recuperar/pregunta` con `{ "usuario" }` responde `{ "pregunta" }` o `404` (no existe, es personal o es una cuenta anterior sin pregunta). Después `POST /auth/recuperar` con `{ "usuario", "respuesta", "nueva" }` responde `200`, `400`, `401` (respuesta incorrecta, con `intentos_restantes`) o `429`. La nueva contraseña sigue las mismas reglas que al cambiarla y se cierran las sesiones abiertas.
- Los grupos se guardan en la tabla `grupos` y cada alumno queda ligado a su grupo (`usuarios.grupo_id`). Los códigos se generan sin O/0 ni I/1/L para que no se confundan al dictarlos.
- `POST /auth/login` recibe `{ "usuario", "contrasena" }` y responde `200`, `401` (con `intentos_restantes`) o `429` (bloqueado 15 minutos tras 5 fallos seguidos).
- `POST /auth/cambiar-contrasena` recibe `{ "actual", "nueva" }`. La nueva debe tener 8+ caracteres, una mayúscula y un número. Responde `200`, `400` (con `errores` en `actual` o `nueva`) o `401`. Al cambiarla sube la **versión de sesión** del usuario: todos sus tokens anteriores dejan de servir (el teléfono que la cambió también vuelve a pedir iniciar sesión).
- `GET /lecturas` requiere `Authorization: Bearer <token>`; responde `200` o `401`.
- `POST /lecturas` requiere `Authorization: Bearer <token>` y recibe `{ "fecha": "2026-10-16", "momento": "viernes", "valor_kwh": 1601.5, "origen": "ocr", "nota": null }`. `origen` (`ocr` o `manual`) y `nota` son opcionales; la `hora` la pone el servidor (hora de México).
  - Si el valor es **menor que la lectura anterior** se exige una `nota` (10 a 300 caracteres) que explique el cambio o reinicio del medidor; sin ella responde `409`.
  - `GET /lecturas` ahora también devuelve `hora`, `origen` y `nota` (vacíos en las lecturas antiguas). Responde `201` con la lectura guardada (ya con su `id`), `400` (datos inválidos, con `errores` por campo), `401` (sin sesión o sesión vencida) o `403` (el rol no puede capturar lecturas: solo `DIRECTOR` y `RESPONSABLE`).
  - El reconocimiento del número (OCR) se hace **en el celular** con ML Kit, sin internet. Al servidor solo llega el número que el usuario ya revisó y confirmó.
- `POST /reportes` requiere `Authorization: Bearer <token>` y recibe `{ "tipo": "desperdicio", "area": "Salón 3B", "descripcion": "Luces encendidas sin nadie", "foto_base64": "<JPG o PNG en Base64>" }`.
  - `tipo`: `desperdicio` o `falla`. `area`: 3 a 50 caracteres. `descripcion`: 10 a 500 caracteres. `foto_base64`: obligatoria, JPG o PNG de máximo 3 MB (se revisa la firma del archivo).
  - El autor sale del token y la fecha la pone el servidor (hora de México). Responde `201` con el reporte (sin la foto), `400` (con `errores` por campo) o `401`.
- Cada reporte trae `estatus`: `pendiente` (al crearse), `en_proceso` o `resuelto`. `PATCH /reportes/{id}/estatus` recibe `{ "estatus": "resuelto" }` y responde el reporte actualizado, `400`, `403` o `404`.
- `GET /reportes/{id}/foto` responde `404` si el reporte no existe **o** si no es tuyo y tu rol no atiende reportes.
- Los tokens duran 30 días.

## Notificaciones push (Firebase Cloud Messaging)

El servidor avisa con notificaciones push en el celular:

| Cuándo | A quién |
| --- | --- |
| Llega un reporte nuevo (`POST /reportes`) | Director y responsable (menos quien lo envió) |
| Cambia el estatus de un reporte (`PATCH /reportes/{id}/estatus`) | Quien envió el reporte |
| La lectura del lunes subió más de 5 kWh desde la del viernes (consumo fantasma) | Todos |
| Viernes 13:00 y 14:30 sin lectura de cierre; lunes 7:30 y 8:30 sin lectura de apertura (hora de México) | Director y responsable |

- Los teléfonos se identifican con su **ID de instalación de Firebase (FID)**, que es lo que Firebase recomienda ahora en lugar de los tokens. Se guardan en la tabla `dispositivos`; si otra persona inicia sesión en el mismo teléfono, el teléfono pasa a ser suyo.
- Los avisos se mandan en segundo plano (la ruta responde sin esperar a Firebase). Los teléfonos que Firebase reporta como inexistentes se borran solos.
- Cada recordatorio se manda una sola vez por día, aunque el servidor se reinicie, y tiene 30 minutos de margen. Si Render gratuito está dormido toda esa media hora, ese recordatorio no sale.
- `POST /notificaciones/prueba` responde `503` si falta la llave de Firebase y `404` si no tienes teléfonos registrados.

**Configurar Firebase:**
1. En la consola de Firebase: **Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada**. La llave es secreta: no la subas a GitHub (ya está en `.gitignore` y `.dockerignore` como `firebase-credenciales.json`).
2. **En tu computadora:** guárdala como `firebase-credenciales.json` en esta carpeta.
3. **En Render:** **Environment → Secret Files**, crea `firebase-credenciales.json` con el contenido del JSON, y agrega la variable `FIREBASE_CREDENTIALS_PATH=/etc/secrets/firebase-credenciales.json`.
4. Al arrancar debe aparecer `Notificaciones push activadas (proyecto ...)`.

La parte de la app (google-services.json) está en `docs/NOTIFICACIONES_PUSH.md` del proyecto de la app.

## Cursos de ahorro de energía

Cada curso tiene tres pasos: **evaluación inicial** (cuánto sabe la persona), **lecciones** y **evaluación final** (cuánto aprendió). La app móvil por ahora trae los cursos dentro (`CursosLocales.kt`) y guarda el avance en el teléfono; estas rutas quedan listas para cuando los tome de aquí (en `App.kt` de la app se cambia `FuenteCursosLocal()` por `FuenteCursosRemota(api)`). Solo existen en este backend de Kotlin, no en el de Node.

- La primera vez que arranca, el servidor crea las tablas y carga los **5 cursos** de la app: *Energía en mi escuela* y *Iluminación eficiente* (básicos), *Equipos y aire acondicionado* (intermedio), y *Tu recibo de luz y las tarifas de CFE* y *Auditoría energética de la escuela* (avanzados). Para agregar o cambiar cursos se editan las tablas `cursos`, `lecciones`, `leccion_bloques`, `preguntas` y `opciones`.
- En un servidor que ya tenía los 3 primeros, al reiniciar se agregan solo los cursos que falten (cada curso inicial se carga una sola vez; queda anotado en `config`). `cursos.nivel` acepta `basico`, `intermedio` y `avanzado`.
- `GET /cursos` devuelve el mismo JSON que espera `CursoDto` en la app: cada curso con `lecciones` (y sus `bloques`), `evaluacion_inicial` y `evaluacion_final` (cada pregunta con sus `opciones`). En `preguntas.momento`, `ambas` significa que la pregunta va en las dos evaluaciones.
- `POST /cursos/{id}/evaluacion` recibe `{ "momento": "inicial", "respuestas": [ { "pregunta_id": 1001, "opcion_id": 10011 }, ... ] }`. El servidor califica con sus propias preguntas (aciertos × 100 / total, redondeado) y responde `201` con `{ aciertos, total, calificacion, progreso }`.
  - `400`: falta el momento, faltan preguntas por contestar, una pregunta viene dos veces o una opción no es de su pregunta.
  - `409`: no se respetó el orden. La inicial va primero y la final solo después de todas las lecciones; cada una se presenta una vez (para repetir, `DELETE /cursos/{id}/progreso`).
- `POST /cursos/{id}/lecciones/{leccionId}` responde el `progreso`, `404` si la lección no es de ese curso o `409` si aún no presenta la evaluación inicial.
- `progreso` trae `curso_id`, `calificacion_inicial`, `calificacion_final`, `lecciones_completadas`, `respuestas_finales` (para la revisión), `fecha_inicio` y `fecha_fin`.
- `GET /cursos/resumen` promedia la calificación inicial **solo de quienes terminaron**, para comparar a las mismas personas antes y después.

## Bases de datos existentes

Al arrancar, el servidor agrega solo las columnas nuevas que falten (`usuarios.version_sesion`, `usuarios.grupo_id/pregunta_seguridad/respuesta_hash`, `lecturas.hora/origen/nota`, `reportes.estatus`), crea las tablas de cursos, grupos y dispositivos si no existen, permite el nivel `avanzado` en `cursos` y agrega los cursos nuevos, **sin borrar datos**. Los alumnos que ya existían siguen entrando igual; solo no tienen pregunta de seguridad (para recuperar su contraseña los ayuda la dirección). No hace falta recrear `data/ecovolt.db` ni el disco de Render.

## Usuarios predefinidos

`director`, `docente1`, `alumno1` y `responsable`, con las mismas contraseñas de siempre. Se guardan con su hash SHA-256 original y pasan a **bcrypt automáticamente la primera vez que cada uno inicia sesión**.

La base de datos de este backend (`data/ecovolt.db`) es **independiente** de la del backend en Node: los usuarios registrados en uno no existen en el otro.

## Conectar la app

En `AppConfig.kt` de la app, `BASE_URL`:
- **Emulador:** `http://10.0.2.2:3000`
- **Celular físico:** la IP que muestra el servidor al arrancar, con el celular y la computadora en la misma red Wi-Fi.

Si el emulador o el celular no conectan, permite el puerto 3000 en el Firewall de Windows (PowerShell como administrador):

```
New-NetFirewallRule -DisplayName "EcoVolt API" -Direction Inbound -Protocol TCP -LocalPort 3000 -Action Allow -Profile Any
```

## Estructura

```
src/main/kotlin/com/ecovolt/backend/
  Main.kt          Arranque del servidor
  Application.kt   Configuración de Ktor y rutas (/auth, /lecturas, /reportes, /consejos, /grupos, /cursos, /health)
  Database.kt      SQLite, tablas (usuarios, grupos, lecturas, reportes, consejos, cursos y avance) y datos iniciales
  CursosIniciales.kt  Los 5 cursos que se cargan la primera vez (los mismos de la app)
  Evaluaciones.kt  Revisión y calificación de las evaluaciones de los cursos
  Notificador.kt   A quién avisar de cada cosa (reportes, estatus, consumo fantasma, recordatorios)
  EnvioFcm.kt      Envío con Firebase Cloud Messaging (Firebase Admin SDK) y lectura de la llave
  Recordatorios.kt Recordatorios de lectura de viernes y lunes
  Reglas.kt        Zona horaria, roles con permisos especiales y preguntas de seguridad
  Security.kt      bcrypt (contraseñas y respuestas de seguridad), migración desde SHA-256 y tokens JWT
  Validators.kt    Validaciones de usuario, contraseña, nombre, lecturas, reportes y cursos
  Models.kt        Formatos JSON de entrada y salida
src/main/resources/logback.xml   Nivel de mensajes en consola
```

## Problemas frecuentes

| Mensaje | Solución |
|---|---|
| `Address already in use` / `bind` | El puerto 3000 está ocupado, probablemente por el backend de Node. Deténlo. |
| `Could not resolve ...` | Sin internet o proxy durante la sincronización de Gradle. Revisa la conexión y pulsa *Sync* de nuevo. |
| `Unsupported class file major version` o `JAVA_HOME is not set` | Configura el *Gradle JDK* (paso 4) o ejecuta desde el panel Gradle en lugar de la terminal. |
| No aparece `Tasks` en el panel Gradle | **File → Settings → Experimental** y desmarca *Do not build Gradle task list during Gradle sync*; luego sincroniza. |
| La app dice "No se pudo conectar" | Revisa que `http://localhost:3000/health` responda y el Firewall (ver arriba). |
