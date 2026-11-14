# Golpe Franco

Videojuego de fútbol 3D original, hecho desde cero con HTML, CSS y JavaScript
sobre **WebGL2** (motor de render propio, sin dependencias en tiempo de
ejecución). Equipos, jugadores, escudos, equipaciones, música, efectos y
comentarios son originales y se generan por código.

## Cómo jugar

- **Lo más rápido:** abre `dist/index.html` con doble clic en Chrome, Edge o
  Firefox. Es un único archivo autocontenido.
- **En desarrollo:** sirve la carpeta con cualquier servidor estático
  (`python3 -m http.server`) y abre `index.html`, que carga los módulos de `src/`.

Flujo: MENÚ → JUGAR PARTIDO → equipos → formación y táctica → dificultad →
estadio 3D → partido completo con descanso y resumen final.

### Controles

| Con balón | | Sin balón | |
|---|---|---|---|
| `W A S D` | Moverse | `Q` | Cambiar de jugador |
| `SHIFT` | Sprint | `ESPACIO` | Entrada |
| `ESPACIO` | Pase (mantener = más fuerte) | `E` | Entrada deslizante |
| `ALT` o `V` | Pase al hueco | `R` | Despeje |
| `R` | Pase elevado / centro | `C` | Contención |
| `E` | Disparo (mantener = más potencia y altura) | `ALT` o `V` | Presión de un compañero |
| `C` + `E` | Disparo colocado | `ESC` | Pausa |
| `F` | Vaselina | | |
| `C` | Proteger el balón | | |
| `X` | Regate (recorte) | | |

En los saques se apunta con `W A S D`: `ESPACIO` saca en corto, `R` en largo o
centra, `E` tira a puerta en faltas y penaltis. Si pulsas pase, tiro o elevado
antes de que te llegue el balón, la acción se ejecuta de primeras (o de cabeza).

## Estructura

```
src/
  core/      Game (bucle y orquestación), Input, SaveSystem, Config, EventBus, MathUtils
  data/      TeamsData (12 clubes, 18 jugadores cada uno), Formations
  match/     Match (estados), MatchRules, Ball, Physics, Player, PlayerAI, Team,
             TeamAI, Goalkeeper, Referee, Actions, Tactics, Replay, UserControl
  render/    GL (motor WebGL2), Mat4, Geo, Stadium, PlayerModel, MatchView,
             Camera, Textures, Crests, MenuScene
  audio/     Audio (ambiente, efectos, música), Commentary
  ui/        UI (menús), HUD
css/         style.css
tools/       build.mjs y bancos de pruebas
```

La simulación (`src/match`) no depende del render: se puede ejecutar sin
gráficos, y así se prueba. La vista dibuja «fotogramas» de simulación, lo que
permite reutilizarla tal cual para las repeticiones.

## Compilar y probar

```
node tools/build.mjs          # genera dist/index.html (necesita esbuild)
node tools/simtest.mjs --n=6  # partidos completos IA contra IA, con estadísticas
node tools/fuzz.mjs 8         # partidos con un usuario que pulsa teclas al azar
node tools/scenarios.mjs      # penaltis, faltas directas y córners forzados
node tools/botuser.mjs --n=4  # usuario automático para comprobar los controles
```

## Qué incluye esta primera versión

Estadio 3D de día y de noche con público, banquillos, túnel, vallas y
videomarcador; 22 jugadores articulados con dorsal, árbitro y asistentes; balón
con física propia (bote, rodadura, efecto, postes, red); pases raso, al hueco y
elevado, centros, disparos (raso, media altura, alto, colocado, potente,
vaselina), control, protección, regate, cabezazos, entradas, despejes; IA de
equipo con formaciones, tácticas y cuatro dificultades; porteros; saques de
centro, banda, puerta, córners, faltas con barrera, penaltis, tarjetas y fuera
de juego; celebraciones, repeticiones desde varias cámaras, comentarista y
guardado local de ajustes, tácticas e historial.

## Pendiente

- Torneo, modo carrera y personalización (aparecen en el menú como próximamente).
- Sustituciones (los 7 suplentes existen en los datos, pero aún no se pueden cambiar).
- Prórroga y tanda de penaltis, ley de la ventaja y lesiones.
- Mando y controles táctiles.
- Animaciones por captura de movimiento y modelos más detallados.
