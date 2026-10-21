// Backend de EcoVolt en Kotlin con Ktor. Mismas versiones de Kotlin, Ktor y serialización que la app.
plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "com.ecovolt"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.5.1"

dependencies {
    // Servidor Ktor (motor Netty), JSON y manejo de errores
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Base de datos SQLite (se usa con JDBC directo, sin ORM)
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    // Contraseñas con bcrypt y tokens JWT
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.auth0:java-jwt:4.4.0")

    // Registro de mensajes en consola
    implementation("ch.qos.logback:logback-classic:1.5.12")
}

application {
    mainClass.set("com.ecovolt.backend.MainKt")
}
