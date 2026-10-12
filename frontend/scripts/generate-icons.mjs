// Writes the placeholder PWA icons (a white ball on the theme colour) into public/. No image library: a tiny PNG
// encoder over node:zlib. Run `npm run icons:generate` after changing the design; the PNGs are committed.
import { writeFile } from 'node:fs/promises';
import { deflateSync } from 'node:zlib';

const THEME = [0x0f, 0x3d, 0x75];
const WHITE = [0xff, 0xff, 0xff];

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (const byte of buf) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
};

/** @param {number} size @param {number} ballRadius fraction of the size */
function icon(size, ballRadius) {
  const rows = [];
  const centre = size / 2;
  const r = size * ballRadius;
  const seam = Math.max(1, size * 0.02);
  for (let y = 0; y < size; y++) {
    const row = Buffer.alloc(1 + size * 3);
    for (let x = 0; x < size; x++) {
      const dx = x + 0.5 - centre;
      const dy = y + 0.5 - centre;
      const d = Math.hypot(dx, dy);
      // a white ball with two seams in the theme colour
      const onSeam = Math.abs(dy) < seam || Math.abs(Math.hypot(dx - r, dy) - r * 0.9) < seam;
      const colour = d <= r && !onSeam ? WHITE : THEME;
      row.set(colour, 1 + x * 3);
    }
    rows.push(row);
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // bit depth
  header[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.concat(rows), { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const out = (name) => new URL(`../public/${name}`, import.meta.url);
await writeFile(out('pwa-192x192.png'), icon(192, 0.4));
await writeFile(out('pwa-512x512.png'), icon(512, 0.4));
// maskable: the ball stays inside the 80% safe zone
await writeFile(out('maskable-512x512.png'), icon(512, 0.3));
await writeFile(out('apple-touch-icon-180x180.png'), icon(180, 0.36));
console.log('Icons written to public/.');
