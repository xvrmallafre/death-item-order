# Death Item Order

Mod para **Minecraft Java Edition 26.3**, **Fabric Loader 0.19.5 o posterior** y **Java 25**. No requiere Fabric API. Licencia MIT.

La edición y el cargador se verificaron antes de implementar el mod:

- [Publicación oficial de Minecraft Java 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3).
- [Fabric para Minecraft 26.3](https://www.fabricmc.net/2026/09/15/263.html): recomienda Loader 0.19.5, Loom 1.17 y Gradle 9.6.0.
- [Metadatos oficiales de Mojang](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json): la entrada de 26.3 y sus metadatos indican Java 25.

Este proyecto fija Minecraft 26.3, Loader 0.19.5, Loom 1.17.1 y Gradle 9.6.0. El descriptor del mod exige exactamente Minecraft 26.3: no declara compatibilidad con Bedrock, Forge, NeoForge ni otras versiones del juego.

## Compilación

Para iniciar Gradle utiliza un **JDK 21 o posterior**, con `JAVA_HOME` apuntando a esa instalación. El proyecto utiliza un JDK 25 para compilar y ejecutar las pruebas: si no hay uno instalado, el resolver Foojay lo descarga automáticamente a la caché de Gradle. El wrapper incluido descarga Gradle; la primera compilación necesita acceso a Internet para Gradle, el JDK y las dependencias. No necesitas instalar Gradle por separado.

En WSL, `JAVA_HOME` debe apuntar a un JDK de Linux dentro de WSL; una instalación de Java en Windows no se detecta automáticamente como un JDK de Linux. Si aparece `Cannot find a Java installation ... languageVersion=25`, actualiza el repositorio y vuelve a compilar para utilizar el resolver incluido. El JDK descargado para compilar no cambia el Java del sistema: Minecraft y el servidor siguen necesitando Java 25 para ejecutarse.

En Windows, desde PowerShell en la carpeta del proyecto:

```powershell
.\gradlew.bat clean build
```

En Linux o macOS:

```sh
./gradlew clean build
```

Resultados:

- `build/libs/death-item-order-1.0.0.jar`: mod instalable.
- `build/libs/death-item-order-1.0.0-sources.jar`: fuentes Java.
- `build/distributions/death-item-order-1.0.0-project.zip`: proyecto completo con fuentes, pruebas, instrucciones y wrapper.
- `build/reports/tests/test/index.html`: informe de pruebas.

Las fuentes completas, recursos, pruebas y scripts de compilación están en este proyecto. El JAR `sources` sirve para consultar el código; para reconstruirlo utiliza también `build.gradle`, `settings.gradle`, los recursos y el wrapper de este directorio.

## Instalación

1. Detén Minecraft o el servidor y haz una copia del mundo.
2. Instala [Fabric para Minecraft 26.3](https://fabricmc.net/use/installer/) con Loader 0.19.5 o posterior. En un servidor dedicado utiliza el [instalador de servidor de Fabric](https://fabricmc.net/use/server/), con Java 25.
3. Copia **solo** `death-item-order-1.0.0.jar` en `mods/` de la instancia o del servidor. Crea esa carpeta si hace falta.
4. Arranca Minecraft con el perfil Fabric o arranca el servidor Fabric.

En multijugador basta instalarlo **en el servidor**; los clientes Java 26.3 pueden conectarse sin este mod. Para un mundo individual instálalo en la instancia Fabric del jugador: actúa en su servidor integrado. No hay configuración ni comandos adicionales.

`keepInventory` sigue teniendo su comportamiento normal. El mod empieza a identificar caídas desde que está instalado; no puede identificar retrospectivamente objetos de muertes anteriores a su instalación.

## Funcionamiento

Cuando Minecraft vacía el inventario por una muerte, el mod captura la posición de cada pila mediante la identidad de la pila real. Vincula la entidad que Minecraft crea para esa caída con tres datos: UUID del jugador, UUID nuevo para esa muerte y espacio original. No identifica objetos comparando solo su tipo o su nombre.

Los espacios son 0–8 para la barra, 9–35 para el inventario principal, 36–39 para botas, pantalones, pechera y casco, y 40 para la mano secundaria. La maldición de desaparición se procesa antes del vaciado de inventario y no se anula.

Al recoger una entidad identificada, Minecraft comprueba primero sus restricciones habituales de recogida. Si la recoge su propietario, el mod intenta colocar la cantidad real en su espacio original. Las pilas con el mismo objeto y todos sus componentes iguales se reúnen hasta su límite. Si otro objeto ocupa ese espacio, intenta trasladarlo íntegramente a un espacio vacío de los 36 espacios ordinarios. No utiliza otros huecos de armadura como almacenamiento ni retira armadura con maldición de ligamiento en supervivencia.

Si no puede restaurar una cantidad, deja que la inserción normal de Minecraft intente recogerla. Lo que no quepa sigue en la entidad del suelo. Nunca sobrescribe al ocupante ni fabrica cantidades a partir de una copia guardada. El desgaste, encantamientos, nombres y componentes arbitrarios se conservan usando las operaciones de `ItemStack`, sin reconstruir el objeto a partir de su identificador.

Las entidades de distinto propietario, muerte o espacio no se fusionan entre sí en el suelo. Las entidades normales siguen fusionándose normalmente, y las de idéntico origen pueden fusionarse. Esto evita mezclar dos pilas iguales que estaban en espacios diferentes. Los objetos de otros jugadores y los objetos nuevos se recogen con el algoritmo normal.

Cada entidad conserva su origen durante recogidas parciales. Al guardarse el mundo se guarda también esa referencia en los datos de la entidad; se recupera al cargarla, incluso si el propietario se desconecta o el servidor se reinicia. También se conserva al trasladar la entidad entre dimensiones. No hay una lista global de objetos capaz de regenerarlos: las entidades que existen son los registros pendientes. Si una entidad desaparece por lava, tiempo, destrucción u otra causa, desaparece también su registro.

Varias muertes generan UUID distintos. Si objetos de dos muertes reclaman el mismo espacio, la recogida actual aplica las mismas reglas de compatibilidad y desplazamiento; no existe prioridad automática de una muerte sobre otra.

## Alcance y comprobación

La restauración corresponde a la **recogida directa de las entidades caídas**. Si una tolva u otro sistema extrae la pila hacia un contenedor, deja de ser una caída pendiente y desde ese contenedor se usa el comportamiento normal. Tampoco se arrastra el origen a objetos que el jugador ya recogió y vuelve a soltar voluntariamente. Los mods que sustituyen por completo las caídas o la recogida deben probarse por separado.

`./gradlew build` ejecuta pruebas de lógica y de integración con Fabric Loader JUnit. Las pruebas de integración aplican los mixins a las clases reales de Minecraft 26.3 y usan pilas reales; el mundo y la conexión del jugador son dobles de prueba. No equivalen a una sesión con clientes reales ni a una prueba completa de reinicio de un servidor. Se comprueban la captura de caídas, posiciones, recogida parcial, desplazamiento, inserción normal, aislamiento, serialización y separación de caídas; además, 10.000 inventarios aleatorios comprueban que las transferencias conservan cantidades y datos.

Para comprobarlo en una partida:

1. Coloca una espada con nombre y desgaste en la primera posición de la barra, bloques en la quinta, armadura y un escudo en la mano secundaria. Muere con `keepInventory` desactivado y recoge los objetos en distinto orden.
2. Repite ocupando la primera posición con otro objeto antes de recuperar la espada. Comprueba su desplazamiento a un espacio libre.
3. Llena el inventario. Comprueba que una recuperación incompatible no sobrescribe objetos y que las cantidades que no caben permanecen en el suelo.
4. Deja una pila de bloques pendiente y recoge solo parte usando un inventario casi lleno. Haz espacio y recupera el resto.
5. Haz que otro jugador recoja un objeto: debe entrar normalmente en su inventario.
6. Deja objetos de dos muertes, desconéctate, guarda y reinicia el servidor, y recógelos después. Comprueba también objetos idénticos que estaban en espacios distintos.
7. Destruye parte de las caídas con lava o espera su desaparición. Recupera las restantes: los objetos perdidos no deben reaparecer.

Como en Minecraft, la persistencia depende del guardado del mundo. El mod no añade garantías frente a apagados abruptos entre guardados.
