// Punto de entrada: espera a las fuentes (se usan también en las texturas del
// estadio y las camisetas) y arranca el juego.
import { Game } from './core/Game.js';

async function start() {
  const app = document.getElementById('app');
  try {
    await Promise.race([
      Promise.all([
        document.fonts.load('800 32px "Barlow Condensed"'),
        document.fonts.load('900 32px "Big Shoulders Display"'),
      ]),
      new Promise((r) => setTimeout(r, 1800)),
    ]);
  } catch (e) { /* se usarán las fuentes de reserva */ }
  try {
    const game = new Game({
      app,
      canvas: document.getElementById('gl'),
      ui: document.getElementById('ui'),
      hud: document.getElementById('hud'),
      fade: document.getElementById('fade'),
    });
    window.__game = game;
    game.boot();
  } catch (e) {
    console.error(e);
    const d = document.createElement('div');
    d.className = 'fatal';
    d.textContent = /WebGL2/.test(String(e && e.message))
      ? 'Este navegador no ofrece WebGL2, necesario para el 3D. Prueba con una versión reciente de Chrome, Edge o Firefox.'
      : 'No se ha podido iniciar el juego: ' + (e && e.message ? e.message : e);
    app.appendChild(d);
  }
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
else start();
