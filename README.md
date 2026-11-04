# EcoVolt Backend (Kotlin + Ktor)

API REST de EcoVolt escrita en **Kotlin** con **Ktor**. Se abre y se ejecuta desde **Android Studio**. Maneja el registro e inicio de sesión de usuarios, las lecturas del medidor, los reportes de desperdicio o falla con foto y los consejos de ahorro (proyecto `MyApplication`).

Es equivalente al backend en Node.js: **mismas rutas, mismo formato JSON y mismos usuarios predefinidos**, por lo que la app funciona igual con cualquiera de los dos.

- Kotlin 2.4.20, Ktor 3.5.1 (motor Netty), Gradle 9.5.1 (mismas versiones que la app)
- SQLite (`sqlite-jdbc`), contraseñas con **bcrypt**, sesiones con **JWT**

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

## Endpoints

Igual que el backend en Node. Los errores traen `{ "mensaje": "..." }`.

| Método | Ruta | Descripción | Requiere token |
|---|---|---|---|
| GET | `/health` | Comprueba que el servidor está activo | No |
| POST | `/auth/register` | Crea una cuenta (siempre con rol ALUMNO) | No |
| POST | `/auth/login` | Inicia sesión | No |
| GET | `/lecturas` | Lecturas del medidor | Sí |
| POST | `/lecturas` | Guarda una lectura capturada con la cámara (OCR) | Sí (Director o Responsable) |
| POST | `/reportes` | Envía un reporte de desperdicio o falla con foto (RFN-19) | Sí (cualquier rol) |
| GET | `/reportes` | Reportes: todos para Director/Responsable, los propios para los demás | Sí |
| GET | `/reportes/{id}/foto` | Foto de un reporte (`image/jpeg` o `image/png`) | Sí |
| GET | `/consejos` | Consejos de ahorro para escuela y hogar (RFN-25). Filtro opcional `?ambito=escuela` o `?ambito=hogar` | No |

- `POST /auth/register` recibe `{ "nombre", "usuario", "contrasena" }` y responde `201`, `400` (datos inválidos, con `errores` por campo) o `409` (usuario repetido).
- `POST /auth/login` recibe `{ "usuario", "contrasena" }` y responde `200` o `401`.
- `GET /lecturas` requiere `Authorization: Bearer <token>`; responde `200` o `401`.
- `POST /lecturas` requiere `Authorization: Bearer <token>` y recibe `{ "fecha": "2026-10-16", "momento": "viernes", "valor_kwh": 1601.5 }`. Responde `201` con la lectura guardada (ya con su `id`), `400` (datos inválidos, con `errores` por campo), `401` (sin sesión o sesión vencida) o `403` (el rol no puede capturar lecturas: solo `DIRECTOR` y `RESPONSABLE`).
  - El reconocimiento del número (OCR) se hace **en el celular** con ML Kit, sin internet. Al servidor solo llega el número que el usuario ya revisó y confirmó.
- `POST /reportes` requiere `Authorization: Bearer <token>` y recibe `{ "tipo": "desperdicio", "area": "Salón 3B", "descripcion": "Luces encendidas sin nadie", "foto_base64": "<JPG o PNG en Base64>" }`.
  - `tipo`: `desperdicio` o `falla`. `area`: 3 a 50 caracteres. `descripcion`: 10 a 500 caracteres. `foto_base64`: obligatoria, JPG o PNG de máximo 3 MB (se revisa la firma del archivo).
  - El autor sale del token y la fecha la pone el servidor (hora de México). Responde `201` con el reporte (sin la foto), `400` (con `errores` por campo) o `401`.
- `GET /reportes/{id}/foto` responde `404` si el reporte no existe **o** si no es tuyo y tu rol no atiende reportes.
- Los tokens duran 30 días.

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
  Application.kt   Configuración de Ktor y rutas (/auth, /lecturas, /reportes, /consejos, /health)
  Database.kt      SQLite, tablas (usuarios, lecturas, reportes, consejos) y datos iniciales
  Security.kt      bcrypt, migración desde SHA-256 y tokens JWT (id + rol)
  Validators.kt    Validaciones de usuario, contraseña, nombre, lecturas y reportes (tipo, área, descripción, foto)
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
