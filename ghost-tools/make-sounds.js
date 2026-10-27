// Genera los sonidos predeterminados de "sound effects" (WAV, 44.1 kHz, mono) con síntesis sencilla.
// Uso: node tools/make-sounds.js
const fs = require('fs')
const path = require('path')
const RATE = 44100
const out = path.join(__dirname, '..', 'mod', 'common', 'src', 'main', 'resources', 'assets', 'axolotlclient', 'ghostsounds')
fs.mkdirSync(out, { recursive: true })

const env = (t, a, d) => Math.min(1, t / a) * Math.exp(-t / d)
const sine = (f, t) => Math.sin(2 * Math.PI * f * t)
// nota con algo de armónicos (suena más "campana")
const bell = (f, t) => sine(f, t) + 0.35 * sine(f * 2.01, t) + 0.15 * sine(f * 3.02, t)
const note = (n) => 440 * 2 ** ((n - 69) / 12)

function render (seconds, fn) {
  const n = Math.floor(seconds * RATE)
  const data = new Float32Array(n)
  for (let i = 0; i < n; i++) data[i] = fn(i / RATE)
  // normalizar y suavizar el final
  let peak = 0
  for (const v of data) peak = Math.max(peak, Math.abs(v))
  for (let i = 0; i < n; i++) data[i] = (data[i] / (peak || 1)) * 0.85 * Math.min(1, (n - i) / (RATE * 0.02))
  return data
}

function wav (name, data) {
  const buf = Buffer.alloc(44 + data.length * 2)
  buf.write('RIFF', 0); buf.writeUInt32LE(36 + data.length * 2, 4); buf.write('WAVE', 8)
  buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(1, 22)
  buf.writeUInt32LE(RATE, 24); buf.writeUInt32LE(RATE * 2, 28); buf.writeUInt16LE(2, 32); buf.writeUInt16LE(16, 34)
  buf.write('data', 36); buf.writeUInt32LE(data.length * 2, 40)
  data.forEach((v, i) => buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, v)) * 32767), 44 + i * 2))
  fs.writeFileSync(path.join(out, name + '.wav'), buf)
}

// Seq: notas [midi, inicio, duración]
const seq = (notes, voice = bell, decay = 0.25) => (t) =>
  notes.reduce((s, [n, at]) => s + (t >= at ? voice(note(n), t - at) * env(t - at, 0.004, decay) : 0), 0)

wav('ding', render(0.7, seq([[84, 0], [91, 0.08]], bell, 0.22)))
wav('coins', render(0.8, seq([[83, 0], [88, 0.06], [91, 0.12], [95, 0.18]], bell, 0.16)))
wav('pop', render(0.25, t => sine(900 - 2600 * t, t) * env(t, 0.002, 0.04)))
wav('laser', render(0.45, t => Math.sign(sine(1800 * Math.exp(-6 * t), t)) * 0.5 * env(t, 0.003, 0.12)))
wav('fanfare', render(1.3, seq([[72, 0], [76, 0.12], [79, 0.24], [84, 0.38], [84, 0.52], [88, 0.52]],
  (f, t) => sine(f, t) + 0.5 * sine(f * 2, t) + 0.3 * Math.sign(sine(f, t)) * 0.3, 0.35)))
wav('bass', render(1.0, t => (sine(110 * Math.exp(-1.6 * t), t) + 0.4 * sine(55 * Math.exp(-1.6 * t), t)) * env(t, 0.005, 0.35)))
wav('sad', render(1.6, seq([[67, 0], [66, 0.32], [65, 0.64], [64, 0.96]],
  (f, t) => { const v = f * (1 + 0.01 * sine(5, t)); return sine(v, t) + 0.6 * sine(v * 2, t) + 0.4 * sine(v * 3, t) }, 0.5)))
// fantasma: "uuuuh" con vibrato que baja
wav('ghost', render(1.6, t => {
  const f = 520 * Math.exp(-0.45 * t) * (1 + 0.035 * sine(6, t))
  return (sine(f, t) + 0.25 * sine(f * 2, t) + 0.1 * sine(f * 3, t)) * Math.min(1, t / 0.15) * Math.exp(-t / 0.9)
}))
console.log(fs.readdirSync(out))
