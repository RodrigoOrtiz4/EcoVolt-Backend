package com.ecovolt.backend

// Cursos que se cargan en la base de datos la primera vez que arranca el servidor.
// Son los mismos que la app trae en CursosLocales.kt. Para agregar o cambiar cursos
// después, se editan directo en las tablas (ver docs/cursos_base_de_datos.sql en la app).
// En estos cursos la evaluación inicial y la final usan las mismas preguntas.

private val PREGUNTAS_CURSO_1 = listOf(
    PreguntaResponse(
        1001, "¿En qué unidad se mide la energía eléctrica que cobra CFE?",
        listOf(
            OpcionResponse(10010, "Watts (W)", false),
            OpcionResponse(10011, "Kilowatt-hora (kWh)", true),
            OpcionResponse(10012, "Volts (V)", false),
            OpcionResponse(10013, "Amperes (A)", false)
        ),
        "CFE cobra la energía que se usó, y esa se mide en kWh. Los watts miden la potencia de un aparato, no cuánto gastó."
    ),
    PreguntaResponse(
        1002, "Un aparato de 1,000 W está encendido 2 horas. ¿Cuánta energía usó?",
        listOf(
            OpcionResponse(10020, "0.5 kWh", false),
            OpcionResponse(10021, "1 kWh", false),
            OpcionResponse(10022, "2 kWh", true),
            OpcionResponse(10023, "2,000 kWh", false)
        ),
        "Energía = potencia × horas ÷ 1000. Entonces 1,000 W × 2 h ÷ 1000 = 2 kWh."
    ),
    PreguntaResponse(
        1003, "El viernes el medidor marcó 1,925.7 kWh y el lunes 1,938.1 kWh. ¿Cuánto se gastó el fin de semana?",
        listOf(
            OpcionResponse(10030, "12.4 kWh", true),
            OpcionResponse(10031, "1,938.1 kWh", false),
            OpcionResponse(10032, "3,863.8 kWh", false),
            OpcionResponse(10033, "No se puede saber", false)
        ),
        "El medidor va sumando. Lo gastado en un periodo es la lectura actual menos la anterior: 1,938.1 − 1,925.7 = 12.4 kWh."
    ),
    PreguntaResponse(
        1004, "¿Qué es el consumo fantasma?",
        listOf(
            OpcionResponse(10040, "La energía que gastan los aparatos cuando nadie los está usando", true),
            OpcionResponse(10041, "Un error del medidor de CFE", false),
            OpcionResponse(10042, "La energía que producen los paneles solares", false),
            OpcionResponse(10043, "La energía que se usa durante las clases", false)
        ),
        "Es la energía de equipos en espera, cargadores conectados o luces olvidadas. EcoVolt lo detecta con las lecturas de viernes y lunes."
    ),
    PreguntaResponse(
        1005, "Ves luces y un ventilador encendidos en un salón vacío. ¿Qué haces?",
        listOf(
            OpcionResponse(10050, "Nada, no es mi salón", false),
            OpcionResponse(10051, "Los apago si puedo y lo reporto en EcoVolt", true),
            OpcionResponse(10052, "Espero a que alguien más lo vea", false),
            OpcionResponse(10053, "Desconecto el medidor de la escuela", false)
        ),
        "Apagar lo que no se usa ahorra al momento, y el reporte ayuda a la dirección a encontrar dónde se repite el problema."
    )
)

private val PREGUNTAS_CURSO_2 = listOf(
    PreguntaResponse(
        2001, "¿Qué tipo de lámpara gasta menos energía para dar la misma luz?",
        listOf(
            OpcionResponse(20010, "LED", true),
            OpcionResponse(20011, "Incandescente", false),
            OpcionResponse(20012, "Halógena", false),
            OpcionResponse(20013, "Todas gastan igual", false)
        ),
        "La LED convierte más energía en luz y menos en calor, por eso gasta menos para iluminar lo mismo."
    ),
    PreguntaResponse(
        2002, "Comparada con una incandescente que da la misma luz, una lámpara LED usa…",
        listOf(
            OpcionResponse(20020, "La misma energía", false),
            OpcionResponse(20021, "Alrededor de 10 % menos", false),
            OpcionResponse(20022, "Alrededor de 75 % menos", true),
            OpcionResponse(20023, "El doble", false)
        ),
        "Una LED usa alrededor de 75 % menos energía que una incandescente y dura muchas veces más."
    ),
    PreguntaResponse(
        2003, "Se fundió un tubo fluorescente. ¿Qué se hace con él?",
        listOf(
            OpcionResponse(20030, "Tirarlo a la basura común", false),
            OpcionResponse(20031, "Llevarlo a un centro de acopio, porque contiene mercurio", true),
            OpcionResponse(20032, "Romperlo para que ocupe menos espacio", false),
            OpcionResponse(20033, "Dejarlo en el salón", false)
        ),
        "Los tubos fluorescentes contienen mercurio; deben llevarse a un centro de acopio y nunca romperse."
    ),
    PreguntaResponse(
        2004, "Si se apagan 4 lámparas LED de 30 W durante 3 horas, ¿cuánta energía se ahorra?",
        listOf(
            OpcionResponse(20040, "0.36 kWh", true),
            OpcionResponse(20041, "3.6 kWh", false),
            OpcionResponse(20042, "12 kWh", false),
            OpcionResponse(20043, "360 kWh", false)
        ),
        "4 × 30 W = 120 W. 120 W × 3 h ÷ 1000 = 0.36 kWh."
    ),
    PreguntaResponse(
        2005, "Por la mañana entra mucha luz del sol al salón. ¿Qué conviene hacer?",
        listOf(
            OpcionResponse(20050, "Encender todas las lámparas", false),
            OpcionResponse(20051, "Apagar las lámparas junto a la ventana", true),
            OpcionResponse(20052, "Cerrar las cortinas y encender las luces", false),
            OpcionResponse(20053, "Encender el proyector", false)
        ),
        "La luz natural es gratis. Si el salón se ve bien, la fila junto a la ventana puede ir apagada."
    )
)

private val PREGUNTAS_CURSO_3 = listOf(
    PreguntaResponse(
        3001, "¿Qué es el modo de espera (standby)?",
        listOf(
            OpcionResponse(30010, "La energía que usa un equipo apagado con el control pero aún conectado", true),
            OpcionResponse(30011, "Un modo que hace que el equipo no gaste nada", false),
            OpcionResponse(30012, "El tiempo que tarda en encender un equipo", false),
            OpcionResponse(30013, "Una función del medidor de CFE", false)
        ),
        "Televisiones, proyectores, bocinas y cargadores siguen gastando mientras están conectados, aunque parezcan apagados."
    ),
    PreguntaResponse(
        3002, "¿Qué parte del consumo de una casa puede ser el modo de espera?",
        listOf(
            OpcionResponse(30020, "Entre 5 y 10 %", true),
            OpcionResponse(30021, "Entre 50 y 60 %", false),
            OpcionResponse(30022, "Más de 90 %", false),
            OpcionResponse(30023, "Nada", false)
        ),
        "Los equipos en espera pueden representar entre 5 y 10 % de la energía de una casa."
    ),
    PreguntaResponse(
        3003, "¿Cuál es la mejor forma de que el aula de medios no gaste el fin de semana?",
        listOf(
            OpcionResponse(30030, "Dejar las computadoras suspendidas", false),
            OpcionResponse(30031, "Apagar solo los monitores", false),
            OpcionResponse(30032, "Apagar los equipos y la regleta (o desconectarlos) el viernes", true),
            OpcionResponse(30033, "Bajar el brillo de las pantallas", false)
        ),
        "Apagar la regleta corta la energía de todos los equipos a la vez, incluido el modo de espera."
    ),
    PreguntaResponse(
        3004, "¿A qué temperatura conviene poner el aire acondicionado?",
        listOf(
            OpcionResponse(30040, "18 °C", false),
            OpcionResponse(30041, "Alrededor de 25 °C", true),
            OpcionResponse(30042, "16 °C", false),
            OpcionResponse(30043, "La más baja posible", false)
        ),
        "Alrededor de 25 °C es cómodo y cada grado más frío hace que el equipo trabaje más y gaste más."
    ),
    PreguntaResponse(
        3005, "Mientras el aire acondicionado está encendido, conviene…",
        listOf(
            OpcionResponse(30050, "Abrir las ventanas para que circule el aire", false),
            OpcionResponse(30051, "Mantener puertas y ventanas cerradas", true),
            OpcionResponse(30052, "Dejarlo encendido al salir para que el salón siga fresco", false),
            OpcionResponse(30053, "Taparle la salida de aire", false)
        ),
        "Con puertas y ventanas abiertas el aire frío se escapa y el equipo trabaja de más."
    )
)

private val PREGUNTAS_CURSO_4 = listOf(
    PreguntaResponse(
        4001, "En una casa con tarifa 1 (clima templado), ¿a partir de qué consumo promedio mensual CFE la pasa a la tarifa DAC?",
        listOf(
            OpcionResponse(40010, "150 kWh al mes", false),
            OpcionResponse(40011, "200 kWh al mes", false),
            OpcionResponse(40012, "Más de 250 kWh al mes", true),
            OpcionResponse(40013, "Más de 1,000 kWh al mes", false)
        ),
        "En la tarifa 1 el límite de alto consumo es de 250 kWh al mes en promedio. Si se rebasa, la casa pasa a DAC y pierde el subsidio."
    ),
    PreguntaResponse(
        4002, "¿Con qué consumo decide CFE si una casa entra a la tarifa DAC?",
        listOf(
            OpcionResponse(40020, "Con el recibo más alto del año", false),
            OpcionResponse(40021, "Con el promedio mensual de los últimos 12 meses", true),
            OpcionResponse(40022, "Con el consumo de diciembre", false),
            OpcionResponse(40023, "Con el número de personas que viven ahí", false)
        ),
        "Se usa el promedio móvil de los últimos 12 meses. Por eso un solo recibo alto no basta, pero tampoco se sale de DAC de un mes para otro."
    ),
    PreguntaResponse(
        4003, "Una escuela conectada en baja tensión tiene una demanda máxima de 18 kW. ¿Qué tarifa le corresponde?",
        listOf(
            OpcionResponse(40030, "PDBT (pequeña demanda en baja tensión)", true),
            OpcionResponse(40031, "GDBT (gran demanda en baja tensión)", false),
            OpcionResponse(40032, "DAC (doméstica de alto consumo)", false),
            OpcionResponse(40033, "Tarifa 1", false)
        ),
        "La PDBT es para servicios en baja tensión con demanda de hasta 25 kW. Arriba de 25 kW se usa la GDBT."
    ),
    PreguntaResponse(
        4004, "¿Cuál es la diferencia entre demanda (kW) y consumo (kWh)?",
        listOf(
            OpcionResponse(40040, "Son lo mismo con distinto nombre", false),
            OpcionResponse(40041, "La demanda es lo que se paga y el consumo es un impuesto", false),
            OpcionResponse(40042, "El consumo es la potencia más alta y la demanda es la energía del mes", false),
            OpcionResponse(40043, "La demanda es cuánta potencia se usa al mismo tiempo; el consumo es la energía usada en todo el periodo", true)
        ),
        "La demanda mide el pico de potencia (kW), por ejemplo todos los minisplits encendidos a la vez. El consumo suma la energía usada en el periodo (kWh)."
    ),
    PreguntaResponse(
        4005, "En las tarifas de gran demanda, ¿qué pasa si el factor de potencia baja de 90 %?",
        listOf(
            OpcionResponse(40050, "CFE da una bonificación", false),
            OpcionResponse(40051, "CFE cobra un cargo extra", true),
            OpcionResponse(40052, "No pasa nada", false),
            OpcionResponse(40053, "Se corta el servicio de inmediato", false)
        ),
        "Un factor de potencia bajo significa que la instalación usa la energía de forma poco eficiente: abajo de 90 % hay recargo y arriba hay bonificación."
    ),
    PreguntaResponse(
        4006, "Una escuela gastó 1,200 kWh y el precio de ejemplo es de \$3.00 por kWh. Si se suma el IVA de 16 %, ¿cuánto se paga por la energía?",
        listOf(
            OpcionResponse(40060, "\$3,600", false),
            OpcionResponse(40061, "\$3,616", false),
            OpcionResponse(40062, "\$4,176", true),
            OpcionResponse(40063, "\$4,800", false)
        ),
        "1,200 kWh × \$3.00 = \$3,600. El IVA es 16 % de eso (\$576), así que el total es \$3,600 + \$576 = \$4,176."
    )
)

private val PREGUNTAS_CURSO_5 = listOf(
    PreguntaResponse(
        5001, "El aula de medios tiene 10 computadoras de 150 W que se usan 5 horas al día, 20 días al mes. ¿Cuánta energía gastan al mes?",
        listOf(
            OpcionResponse(50010, "15 kWh", false),
            OpcionResponse(50011, "150 kWh", true),
            OpcionResponse(50012, "1,500 kWh", false),
            OpcionResponse(50013, "7.5 kWh", false)
        ),
        "10 × 150 W × 5 h × 20 días = 150,000 Wh. Entre 1000: 150 kWh al mes."
    ),
    PreguntaResponse(
        5002, "Un minisplit de 1,200 W se usa 2 horas al día y un refrigerador que gasta en promedio 150 W está conectado las 24 horas. ¿Cuál gasta más energía al día?",
        listOf(
            OpcionResponse(50020, "El minisplit, porque tiene más watts", false),
            OpcionResponse(50021, "Gastan lo mismo", false),
            OpcionResponse(50022, "El refrigerador: 3.6 kWh contra 2.4 kWh", true),
            OpcionResponse(50023, "No se puede saber sin el recibo", false)
        ),
        "Minisplit: 1,200 W × 2 h = 2.4 kWh. Refrigerador: 150 W × 24 h = 3.6 kWh. Importan los watts y también las horas."
    ),
    PreguntaResponse(
        5003, "Del viernes a las 15:00 al lunes a las 7:00 la escuela está cerrada (64 horas). Si los equipos en espera suman 120 W, ¿cuánto consumo fantasma hay?",
        listOf(
            OpcionResponse(50030, "7.68 kWh", true),
            OpcionResponse(50031, "0.12 kWh", false),
            OpcionResponse(50032, "76.8 kWh", false),
            OpcionResponse(50033, "1.92 kWh", false)
        ),
        "120 W × 64 h = 7,680 Wh = 7.68 kWh. Rebasa los 5 kWh con los que EcoVolt avisa de consumo fantasma."
    ),
    PreguntaResponse(
        5004, "Se cambian 20 tubos de 40 W por tubos LED de 18 W. Se usan 8 horas al día, 200 días al año. ¿Cuánto se ahorra al año?",
        listOf(
            OpcionResponse(50040, "88 kWh", false),
            OpcionResponse(50041, "176 kWh", false),
            OpcionResponse(50042, "1,280 kWh", false),
            OpcionResponse(50043, "704 kWh", true)
        ),
        "Cada tubo ahorra 22 W. 22 W × 20 tubos × 8 h × 200 días = 704,000 Wh = 704 kWh al año."
    ),
    PreguntaResponse(
        5005, "Un cambio cuesta \$6,000 y ahorra \$2,000 al año. ¿En cuánto tiempo se recupera la inversión?",
        listOf(
            OpcionResponse(50050, "1 año", false),
            OpcionResponse(50051, "3 años", true),
            OpcionResponse(50052, "6 años", false),
            OpcionResponse(50053, "Nunca se recupera", false)
        ),
        "Tiempo de recuperación = inversión ÷ ahorro anual = \$6,000 ÷ \$2,000 = 3 años."
    ),
    PreguntaResponse(
        5006, "En septiembre la escuela gastó 1,250 kWh y en octubre 1,000 kWh. ¿Cuánto bajó el consumo?",
        listOf(
            OpcionResponse(50060, "20 %", true),
            OpcionResponse(50061, "25 %", false),
            OpcionResponse(50062, "250 %", false),
            OpcionResponse(50063, "12.5 %", false)
        ),
        "Bajó 250 kWh. Se divide entre el valor anterior: 250 ÷ 1,250 = 0.20, es decir 20 %."
    )
)

internal val CURSOS_INICIALES = listOf(
    CursoResponse(
        id = 1,
        titulo = "Energía en mi escuela",
        descripcion = "Qué es un kWh, cómo se lee el medidor y por qué importa el consumo fantasma.",
        icono = "⚡",
        nivel = "basico",
        duracionMinutos = 15,
        lecciones = listOf(
            LeccionResponse(
                101, 1, "¿Qué es un kilowatt-hora?",
                "La unidad con la que se mide y se paga la energía.", 4,
                listOf(
                    BloqueResponse("parrafo", "La energía eléctrica que usa la escuela se mide en kilowatt-hora (kWh). Un kWh es la energía que usa un aparato de 1,000 watts encendido durante una hora."),
                    BloqueResponse("dato", "Energía (kWh) = potencia (W) × horas de uso ÷ 1000", "La fórmula"),
                    BloqueResponse("ejemplo", "8 lámparas de 30 W suman 240 W. En una hora gastan 0.24 kWh y en una jornada de 6 horas, 1.44 kWh.", "Un salón con 8 lámparas LED"),
                    BloqueResponse("parrafo", "Los watts dicen qué tan rápido usa energía un aparato; los kWh dicen cuánta energía usó en total. Por eso el recibo de CFE habla de kWh.")
                )
            ),
            LeccionResponse(
                102, 2, "El medidor de la escuela",
                "Cómo saber cuánto se gastó con solo dos lecturas.", 4,
                listOf(
                    BloqueResponse("parrafo", "CFE instala un medidor que va sumando todos los kWh que usa la escuela. El número nunca baja: cada lectura es igual o mayor que la anterior."),
                    BloqueResponse("parrafo", "Para saber cuánto se gastó en un periodo se resta la lectura anterior a la lectura actual."),
                    BloqueResponse("ejemplo", "Si el viernes el medidor marcaba 1,925.7 kWh y el lunes 1,938.1 kWh, el fin de semana se gastaron 12.4 kWh.", "Un fin de semana"),
                    BloqueResponse("dato", "El viernes de 13:00 a 15:00 h, al cerrar, y el lunes de 7:30 a 9:00 h, antes de abrir. La resta muestra lo que se gastó con la escuela vacía.", "Por eso EcoVolt pide dos lecturas")
                )
            ),
            LeccionResponse(
                103, 3, "Consumo fantasma",
                "La energía que se va cuando nadie está usando nada.", 4,
                listOf(
                    BloqueResponse("parrafo", "El consumo fantasma es la energía que se gasta cuando nadie usa los aparatos. Pasa sobre todo de noche y en fines de semana."),
                    BloqueResponse("lista", "Proyectores y pantallas en modo de espera\nComputadoras suspendidas en lugar de apagadas\nCargadores conectados sin teléfono\nLuces de baños o del patio encendidas el fin de semana", "Dónde se esconde"),
                    BloqueResponse("dato", "Si con la escuela cerrada se gastan más de 5 kWh en el fin de semana, la app lo marca como consumo fantasma y lo avisa en el Inicio y en Consejos.", "Cómo lo detecta EcoVolt")
                )
            ),
            LeccionResponse(
                104, 4, "Mi papel en el ahorro",
                "Lo que cada persona de la escuela puede hacer.", 3,
                listOf(
                    BloqueResponse("parrafo", "Ahorrar energía es trabajo de todos: quien toma las lecturas, la dirección que atiende los reportes y cada alumno y docente."),
                    BloqueResponse("lista", "Apaga luces y equipos al salir del salón\nAprovecha la luz natural\nReporta en EcoVolt lo que veas encendido sin uso\nComparte lo que aprendas con tu familia"),
                    BloqueResponse("ejemplo", "Elige el tipo, escribe el área (por ejemplo \"Salón 3B\") y agrega una foto sin personas. Así la dirección lo encuentra y lo resuelve más rápido.", "Un buen reporte")
                )
            )
        ),
        evaluacionInicial = PREGUNTAS_CURSO_1,
        evaluacionFinal = PREGUNTAS_CURSO_1
    ),
    CursoResponse(
        id = 2,
        titulo = "Iluminación eficiente",
        descripcion = "Qué lámparas conviene usar, cómo aprovechar la luz del sol y cuándo apagar.",
        icono = "💡",
        nivel = "basico",
        duracionMinutos = 12,
        lecciones = listOf(
            LeccionResponse(
                201, 1, "Tipos de lámparas",
                "Incandescente, fluorescente y LED.", 4,
                listOf(
                    BloqueResponse("parrafo", "No todas las lámparas gastan lo mismo para dar la misma luz. Las incandescentes convierten casi toda la energía en calor; las LED la convierten mucho más en luz."),
                    BloqueResponse("dato", "Usa alrededor de 75 % menos energía que una incandescente que ilumina lo mismo y dura muchas veces más.", "LED"),
                    BloqueResponse("parrafo", "Los tubos fluorescentes están en medio: gastan menos que las incandescentes pero más que las LED. Contienen mercurio, así que no van a la basura común: se llevan a un centro de acopio.")
                )
            ),
            LeccionResponse(
                202, 2, "Aprovecha la luz natural",
                "La luz del sol no cuesta.", 4,
                listOf(
                    BloqueResponse("parrafo", "Abrir cortinas y persianas puede ser suficiente para buena parte de la mañana."),
                    BloqueResponse("lista", "Apaga la fila de lámparas junto a la ventana si el salón se ve bien\nMantén limpias ventanas y lámparas\nAcomoda los lugares de lectura cerca de la luz", "Ideas para el salón"),
                    BloqueResponse("ejemplo", "Si un salón apaga 4 de sus 8 lámparas LED de 30 W durante 3 horas, ahorra 0.36 kWh cada día.", "Cuánto se ahorra")
                )
            ),
            LeccionResponse(
                203, 3, "Apagar al salir",
                "El hábito que más ahorra.", 4,
                listOf(
                    BloqueResponse("parrafo", "Apagar la luz al salir siempre ahorra, aunque sea por poco tiempo. Con las lámparas LED no hay que esperar para apagarlas."),
                    BloqueResponse("dato", "En cada grupo, un alumno por semana revisa luces, ventiladores y proyector al terminar la última clase.", "El guardián de la energía"),
                    BloqueResponse("parrafo", "Baños, pasillos y bodegas son los lugares donde más se olvidan las luces encendidas. Si las ves, repórtalas en EcoVolt.")
                )
            )
        ),
        evaluacionInicial = PREGUNTAS_CURSO_2,
        evaluacionFinal = PREGUNTAS_CURSO_2
    ),
    CursoResponse(
        id = 3,
        titulo = "Equipos y aire acondicionado",
        descripcion = "El modo de espera, las regletas y cómo usar el clima sin desperdiciar.",
        icono = "🔌",
        nivel = "intermedio",
        duracionMinutos = 14,
        lecciones = listOf(
            LeccionResponse(
                301, 1, "El modo de espera",
                "Equipos que gastan aunque parezcan apagados.", 5,
                listOf(
                    BloqueResponse("parrafo", "Muchos equipos siguen gastando energía aunque se apaguen con el control: televisiones, proyectores, bocinas, impresoras y cargadores. Es el modo de espera."),
                    BloqueResponse("dato", "El modo de espera puede representar entre 5 y 10 % de la energía de una casa.", "¿Cuánto es?"),
                    BloqueResponse("lista", "Tiene una luz encendida\nMuestra un reloj en la pantalla\nSe enciende con el control remoto", "Señales de que un equipo está en espera")
                )
            ),
            LeccionResponse(
                302, 2, "Regletas y desconectar",
                "Apagar varios equipos de un solo golpe.", 4,
                listOf(
                    BloqueResponse("parrafo", "Una regleta (multicontacto) con interruptor permite cortar la energía de varios equipos a la vez."),
                    BloqueResponse("ejemplo", "Conecta computadoras, bocinas y proyector a regletas y apágalas el viernes. Así no gastan todo el fin de semana.", "Aula de medios"),
                    BloqueResponse("dato", "Si no se usarán hasta el día siguiente, apágalas en lugar de suspenderlas.", "Computadoras")
                )
            ),
            LeccionResponse(
                303, 3, "Aire acondicionado y ventiladores",
                "De los equipos que más energía usan.", 5,
                listOf(
                    BloqueResponse("parrafo", "El aire acondicionado (minisplit) es de los equipos que más energía usan en una escuela. Un ventilador de techo usa decenas de watts; un minisplit, más de mil."),
                    BloqueResponse("lista", "Ponlo alrededor de 25 °C: cada grado más frío gasta más\nCierra puertas y ventanas mientras esté encendido\nMantén limpios los filtros\nApágalo al salir del salón", "Para usarlo bien"),
                    BloqueResponse("ejemplo", "Si hace poco calor, prueba primero con el ventilador y abre ventanas de lados opuestos para que corra el aire.", "Antes de encenderlo")
                )
            )
        ),
        evaluacionInicial = PREGUNTAS_CURSO_3,
        evaluacionFinal = PREGUNTAS_CURSO_3
    ),
    CursoResponse(
        id = 4,
        titulo = "Tu recibo de luz y las tarifas de CFE",
        descripcion = "Tarifa DAC, la tarifa de la escuela, demanda contra consumo, factor de potencia y cómo calcular lo que se paga.",
        icono = "🧾",
        nivel = "avanzado",
        duracionMinutos = 22,
        lecciones = listOf(
            LeccionResponse(
                401, 1, "Tarifas de las casas y la DAC",
                "Por qué un hogar puede perder el subsidio.", 6,
                listOf(
                    BloqueResponse("parrafo", "Las casas pagan con una tarifa doméstica que depende del clima de la localidad: de la 1 (clima templado) a la 1F (calor extremo). Mientras más calor hace, más energía se permite usar con subsidio."),
                    BloqueResponse("lista", "Tarifa 1: 250 kWh\nTarifa 1A: 300 kWh\nTarifa 1B: 400 kWh\nTarifa 1C: 850 kWh\nTarifas 1D, 1E y 1F: de 1,000 a 2,500 kWh", "Límite de alto consumo (promedio mensual)"),
                    BloqueResponse("dato", "Si el promedio mensual de los últimos 12 meses rebasa el límite, CFE pasa la casa a la tarifa Doméstica de Alto Consumo (DAC): sin subsidio y con cargo fijo.", "La tarifa DAC"),
                    BloqueResponse("ejemplo", "El recibo de casa es bimestral. Suma los kWh de los últimos 6 recibos y divide entre 12. Si da 270 kWh y la casa tiene tarifa 1, ya rebasó el límite de 250 kWh.", "Cómo sacar el promedio"),
                    BloqueResponse("parrafo", "Para salir de DAC hay que bajar el consumo hasta que el promedio de 12 meses quede otra vez por debajo del límite. No es inmediato: toma varios meses.")
                )
            ),
            LeccionResponse(
                402, 2, "La tarifa de la escuela",
                "PDBT, GDBT y por qué importa la demanda.", 6,
                listOf(
                    BloqueResponse("parrafo", "Una escuela no paga tarifa doméstica. Su tarifa depende de la tensión a la que está conectada y de su demanda, es decir, de cuánta potencia usa al mismo tiempo."),
                    BloqueResponse("lista", "PDBT: pequeña demanda en baja tensión, hasta 25 kW\nGDBT: gran demanda en baja tensión, más de 25 kW\nGDMTO y GDMTH: gran demanda en media tensión (escuelas grandes con transformador propio)", "Tarifas comunes"),
                    BloqueResponse("dato", "La demanda (kW) es el pico de potencia; el consumo (kWh) es la energía de todo el periodo. En las tarifas de gran demanda se cobran las dos cosas.", "Demanda contra consumo"),
                    BloqueResponse("ejemplo", "Si los 10 minisplits se encienden a la vez a las 8:00, la demanda se dispara. Encenderlos de forma escalonada da el mismo fresco con un pico menor.", "Bajar la demanda")
                )
            ),
            LeccionResponse(
                403, 3, "Factor de potencia",
                "Qué tan bien aprovecha la instalación la energía.", 5,
                listOf(
                    BloqueResponse("parrafo", "El factor de potencia indica qué parte de la energía que entrega CFE se convierte en trabajo útil. Los motores, compresores y balastros viejos lo bajan."),
                    BloqueResponse("dato", "En las tarifas de gran demanda, CFE hace un cargo si el factor de potencia baja de 90 % y da una bonificación si es mayor. El valor aparece en el recibo.", "Cargo o bonificación"),
                    BloqueResponse("lista", "Instalar un banco de capacitores (lo calcula un electricista)\nCambiar balastros y lámparas viejas por LED\nDar mantenimiento a motores, bombas y compresores", "Cómo se mejora")
                )
            ),
            LeccionResponse(
                404, 4, "Calcula lo que se paga",
                "De los kWh del medidor a pesos.", 5,
                listOf(
                    BloqueResponse("parrafo", "Para estimar el costo de la energía se multiplica el consumo por el precio del kWh de la tarifa. Después se suma el IVA de 16 %. Algunas tarifas también tienen cargo fijo y cargo por demanda."),
                    BloqueResponse("dato", "Costo ≈ (kWh × precio por kWh + cargo fijo) × 1.16", "La fórmula"),
                    BloqueResponse("ejemplo", "1,200 kWh × \$3.00 = \$3,600. Con IVA: \$3,600 × 1.16 = \$4,176. El precio real cambia cada mes y por región: tómalo del recibo.", "Con un precio de ejemplo"),
                    BloqueResponse("parrafo", "Con las lecturas de EcoVolt puedes estimar el gasto de una semana o de un fin de semana y saber cuánto dinero se perdió por consumo fantasma.")
                )
            )
        ),
        evaluacionInicial = PREGUNTAS_CURSO_4,
        evaluacionFinal = PREGUNTAS_CURSO_4
    ),
    CursoResponse(
        id = 5,
        titulo = "Auditoría energética de la escuela",
        descripcion = "Haz el inventario de equipos, calcula consumo y costo, elige qué cambiar primero y cuándo se recupera la inversión.",
        icono = "📊",
        nivel = "avanzado",
        duracionMinutos = 24,
        lecciones = listOf(
            LeccionResponse(
                501, 1, "Inventario de equipos",
                "Saber qué hay conectado y cuánto tiempo funciona.", 6,
                listOf(
                    BloqueResponse("parrafo", "Una auditoría energética empieza con un inventario: recorrer la escuela y anotar cada equipo con su potencia y el tiempo que se usa."),
                    BloqueResponse("lista", "Área (por ejemplo, \"Aula de medios\")\nEquipo y cantidad\nPotencia en watts (viene en la etiqueta o en el manual)\nHoras de uso al día y días de uso al mes", "Qué anotar de cada equipo"),
                    BloqueResponse("dato", "kWh al mes = cantidad × watts × horas al día × días al mes ÷ 1000", "La fórmula del inventario"),
                    BloqueResponse("ejemplo", "10 computadoras de 150 W, 5 horas al día, 20 días: 10 × 150 × 5 × 20 ÷ 1000 = 150 kWh al mes.", "Aula de medios")
                )
            ),
            LeccionResponse(
                502, 2, "Dónde se va la energía",
                "Los watts no lo son todo: también cuentan las horas.", 6,
                listOf(
                    BloqueResponse("parrafo", "Con el inventario se ordenan los equipos de mayor a menor consumo en kWh. Un equipo de pocos watts encendido muchas horas puede gastar más que uno potente que casi no se usa."),
                    BloqueResponse("ejemplo", "Minisplit de 1,200 W por 2 horas: 2.4 kWh al día. Refrigerador que gasta en promedio 150 W las 24 horas: 3.6 kWh al día.", "Minisplit contra refrigerador"),
                    BloqueResponse("dato", "De viernes 15:00 a lunes 7:00 pasan 64 horas. 120 W en espera durante ese tiempo son 7.68 kWh de consumo fantasma.", "El fin de semana"),
                    BloqueResponse("parrafo", "Compara el total del inventario con lo que miden las lecturas de EcoVolt. Si las lecturas dan mucho más, hay equipos que no anotaste o que se quedan encendidos.")
                )
            ),
            LeccionResponse(
                503, 3, "Calcula el ahorro",
                "Cuánto se deja de gastar con cada medida.", 6,
                listOf(
                    BloqueResponse("parrafo", "El ahorro de un cambio es la diferencia de potencia multiplicada por el tiempo de uso. Para pasarlo a dinero se multiplica por el precio del kWh."),
                    BloqueResponse("ejemplo", "20 tubos de 40 W cambiados por LED de 18 W ahorran 22 W cada uno. Con 8 horas al día y 200 días: 22 × 20 × 8 × 200 ÷ 1000 = 704 kWh al año.", "Cambio a LED"),
                    BloqueResponse("dato", "Ahorro % = (consumo anterior − consumo nuevo) ÷ consumo anterior × 100. De 1,250 a 1,000 kWh: 250 ÷ 1,250 × 100 = 20 %.", "Porcentaje de ahorro")
                )
            ),
            LeccionResponse(
                504, 4, "Qué hacer primero",
                "Elegir medidas con la recuperación de la inversión.", 6,
                listOf(
                    BloqueResponse("parrafo", "No todas las medidas cuestan lo mismo. Las de hábitos (apagar, desconectar, programar) no cuestan y van primero. Las que requieren comprar equipo se comparan por su tiempo de recuperación."),
                    BloqueResponse("dato", "Años = inversión ÷ ahorro en pesos por año. Un cambio de \$6,000 que ahorra \$2,000 al año se paga solo en 3 años.", "Tiempo de recuperación"),
                    BloqueResponse("lista", "1. Hábitos sin costo: apagar luces, regletas el viernes, suspensión de computadoras\n2. Cambios baratos con recuperación corta: LED, temporizadores\n3. Inversiones grandes: equipos de aire eficientes, capacitores\n4. Medir de nuevo con EcoVolt para comprobar el ahorro", "Orden recomendado")
                )
            )
        ),
        evaluacionInicial = PREGUNTAS_CURSO_5,
        evaluacionFinal = PREGUNTAS_CURSO_5
    )
)
