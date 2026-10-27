// Descripciones cortas de cada mod del menú (textos ghost.desc.<mod>), en español e inglés.
// Uso: node tools/add-descriptions.js <carpeta-del-mod>
const fs = require('fs')
const path = require('path')
const dir = path.join(process.argv[2], 'common', 'src', 'main', 'resources', 'assets', 'axolotlclient', 'lang')

// [español, inglés]
const D = {
  actionbarhud: ['Mueve y personaliza los mensajes que salen encima de la barra de objetos.', 'Move and customize the messages shown above the hotbar.'],
  armorhud: ['Muestra tu armadura y lo que le queda de durabilidad.', 'Shows your armor and its remaining durability.'],
  arrowhud: ['Muestra cuántas flechas llevas.', 'Shows how many arrows you have.'],
  autoboop: ['Saluda solo a tus amigos de Hypixel cuando se conectan.', 'Automatically boops your Hypixel friends when they join.'],
  autotip: ['Da propinas automáticas en Hypixel para ganar experiencia.', 'Sends automatic tips on Hypixel to earn experience.'],
  bedwars: ['Mejoras para BedWars de Hypixel.', 'Improvements for Hypixel BedWars.'],
  'bedwars.session_statistics': ['Tus estadísticas de la sesión de BedWars: victorias, kills, camas…', 'Your BedWars session stats: wins, kills, beds…'],
  bedwars_resources: ['Muestra el hierro, oro, diamantes y esmeraldas que llevas.', 'Shows the iron, gold, diamonds and emeralds you carry.'],
  bedwars_stats_overlay: ['Estadísticas de los jugadores de tu partida.', 'Stats of the players in your game.'],
  bedwars_teamupgrades: ['Muestra las mejoras compradas por tu equipo.', 'Shows the upgrades your team has bought.'],
  blockOutlines: ['Cambia el color y el grosor del borde del bloque al que miras.', 'Changes the color and width of the outline of the block you look at.'],
  bossbarhud: ['Mueve y personaliza la barra de vida de los jefes.', 'Move and customize the boss health bar.'],
  combohud: ['Cuenta los golpes seguidos que das sin que te peguen.', 'Counts the hits in a row you land without being hit.'],
  compasshud: ['Brújula con la dirección hacia la que miras.', 'Compass showing the direction you are facing.'],
  coordshud: ['Muestra tus coordenadas y tu dirección.', 'Shows your coordinates and direction.'],
  cpshud: ['Muestra tus clics por segundo.', 'Shows your clicks per second.'],
  crosshairhud: ['Personaliza la mira: forma, color y tamaño.', 'Customize the crosshair: shape, color and size.'],
  daycounterhud: ['Muestra en qué día del mundo estás.', 'Shows the current day of the world.'],
  debugcountershud: ['Contadores técnicos: entidades, partículas y chunks.', 'Technical counters: entities, particles and chunks.'],
  fpshud: ['Muestra tus FPS.', 'Shows your FPS.'],
  freelook: ['Mira a tu alrededor sin girar a tu personaje mientras mantienes la tecla.', 'Look around without turning your character while holding the key.'],
  hotbarhud: ['Mueve y personaliza la barra de objetos.', 'Move and customize the hotbar.'],
  iconhud: ['Muestra el icono del cliente en pantalla.', 'Shows the client icon on screen.'],
  inventoryhud: ['Muestra tu inventario en pantalla.', 'Shows your inventory on screen.'],
  iphud: ['Muestra la IP del servidor en el que juegas.', 'Shows the IP of the server you are on.'],
  irltimehud: ['Muestra la hora real.', 'Shows the real-life time.'],
  itemupdatehud: ['Avisa de los objetos que recoges o pierdes.', 'Notifies you of items you pick up or lose.'],
  keystrokehud: ['Muestra las teclas que pulsas: WASD, clics y espacio.', 'Shows the keys you press: WASD, clicks and space.'],
  levelhead: ['Muestra el nivel de Hypixel encima de los jugadores.', 'Shows the Hypixel level above players.'],
  memoryhud: ['Muestra la memoria RAM que está usando el juego.', 'Shows how much RAM the game is using.'],
  motionBlur: ['Añade desenfoque de movimiento al girar la cámara.', 'Adds motion blur when you move the camera.'],
  mousemovementhud: ['Muestra cómo mueves el ratón.', 'Shows how you move the mouse.'],
  packdisplayhud: ['Muestra los packs de texturas activos.', 'Shows the active resource packs.'],
  particles: ['Cambia la cantidad, el tamaño y el color de las partículas (golpes, críticos…).', 'Changes the amount, size and color of particles (hits, crits…).'],
  pinghud: ['Muestra tu ping con el servidor.', 'Shows your ping to the server.'],
  playercounthud: ['Muestra cuántos jugadores hay en el servidor.', 'Shows how many players are on the server.'],
  playerhud: ['Muestra a tu personaje en pantalla.', 'Shows your character on screen.'],
  potionshud: ['Muestra tus efectos de pociones y cuánto les queda.', 'Shows your potion effects and their time left.'],
  reachhud: ['Muestra a qué distancia golpeas.', 'Shows the distance of your hits.'],
  rpc: ['Muestra en tu perfil de Discord que estás jugando a ghost client.', 'Shows on your Discord profile that you are playing ghost client.'],
  scoreboardhud: ['Mueve y personaliza la tabla de puntuación.', 'Move and customize the scoreboard.'],
  screenshotUtils: ['Opciones extra al hacer capturas: copiar, abrir o borrar.', 'Extra options for screenshots: copy, open or delete.'],
  scrollableTooltips: ['Desplaza con la rueda las descripciones largas de los objetos.', 'Scroll long item tooltips with the mouse wheel.'],
  speedhud: ['Muestra tu velocidad.', 'Shows your speed.'],
  subtitleshud: ['Mueve y personaliza los subtítulos de sonido.', 'Move and customize the sound subtitles.'],
  tab_overlay_hud: ['Personaliza la lista de jugadores (tecla Tab).', 'Customize the player list (Tab key).'],
  timeChanger: ['Cambia la hora del día solo para ti.', 'Changes the time of day just for you.'],
  tnttime: ['Muestra cuánto le queda a la TNT para explotar.', 'Shows how long until TNT explodes.'],
  togglesprint: ['Corre o agáchate sin tener que mantener la tecla.', 'Sprint or sneak without holding the key.'],
  tpshud: ['Muestra los ticks por segundo del servidor.', 'Shows the server ticks per second.'],
  xphud: ['Muestra tu nivel y tu experiencia.', 'Shows your level and experience.'],
  zoom: ['Acerca la vista mientras mantienes la tecla.', 'Zooms in while you hold the key.']
}

function merge (file, idx) {
  const p = path.join(dir, file)
  const raw = fs.readFileSync(p, 'utf8')
  const indent = (raw.match(/\n(\s+)"/) || [, '\t'])[1]
  const data = JSON.parse(raw)
  for (const [k, v] of Object.entries(D)) data['ghost.desc.' + k] = v[idx]
  fs.writeFileSync(p, JSON.stringify(data, null, indent) + '\n')
  console.log(`${file}: ${Object.keys(D).length} descripciones`)
}

merge('es_es.json', 0)
merge('en_us.json', 1)
