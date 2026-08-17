import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const emojiSourceRoot = path.join(repositoryRoot, 'emoji');
const outputRoot = path.join(
  repositoryRoot,
  'backend',
  'public',
  'assets',
  'profile-emojis',
  'v3'
);

const DEFAULT_EMOJI = Object.freeze({
  setId: 'spotty-persik',
  emojiId: 'e-0007fab99d521710'
});
const MAX_COMPRESSED_BYTES = 64 * 1024;
const MAX_UNCOMPRESSED_BYTES = 2 * 1024 * 1024;

const sets = [
  {
    id: 'spotty-persik',
    labels: { en: 'Spotty & Persik', ru: 'Спотти и Персик' }
  },
  {
    id: 'status-uzbek',
    labels: { en: 'Status Uzbek', ru: 'Узбекские статусы' }
  },
  {
    id: 'animated-icons',
    labels: { en: 'Animated Icons', ru: 'Анимированные иконки' }
  },
  {
    id: 'diggy',
    labels: { en: 'Diggy', ru: 'Дигги' }
  },
  {
    id: 'neon',
    labels: { en: 'Neon', ru: 'Неон' }
  }
];

const sha256 = (bytes) => crypto.createHash('sha256').update(bytes).digest('hex');

const normalizedLabel = (value) => typeof value === 'string'
  ? value.replaceAll('_', ' ').replace(/\s+/gu, ' ').trim()
  : '';

const isMeaningfulLabel = (value) => {
  if (value.length < 2 || value.length > 64 || /^\d+$/u.test(value)) return false;
  return !/^(?:comp(?:osition)?|pre[ -]?comp|layer|shape|path|surface|null|group|ellipse|rectangle|vector|solid|контур|композиция)(?:[ -]?\d+)?$/iu.test(value);
};

const searchableMetadata = (lottie, set, index) => {
  const candidates = [
    lottie.nm,
    ...(Array.isArray(lottie.layers) ? lottie.layers.map(({ nm }) => nm) : [])
  ].map(normalizedLabel).filter(isMeaningfulLabel);
  const uniqueCandidates = [...new Set(candidates)];
  return {
    name: uniqueCandidates[0] ?? `${set.labels.en} ${index + 1}`,
    keywords: [...new Set([...Object.values(set.labels), ...uniqueCandidates])].slice(0, 16)
  };
};

const normalizedEntries = (setId) => {
  const directory = path.join(emojiSourceRoot, 'new', setId);
  if (!fs.existsSync(directory)) {
    throw new Error(`Missing normalized emoji set: ${path.relative(repositoryRoot, directory)}`);
  }
  return fs.readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.tgs'))
    .map((entry) => ({ id: path.basename(entry.name, '.tgs'), file: path.join(directory, entry.name) }))
    .sort((left, right) => left.id.localeCompare(right.id));
};

const inspectTgs = ({ id, file }, set, index) => {
  const compressed = fs.readFileSync(file);
  if (compressed.length === 0 || compressed.length > MAX_COMPRESSED_BYTES) {
    throw new Error(`${file} is outside the Telegram TGS compressed-size limit`);
  }

  let raw;
  try {
    raw = zlib.gunzipSync(compressed);
  } catch (error) {
    throw new Error(`${file} is not a gzip-compressed TGS`, { cause: error });
  }
  if (raw.length > MAX_UNCOMPRESSED_BYTES) {
    throw new Error(`${file} expands beyond the application safety limit`);
  }

  const lottie = JSON.parse(raw);
  const width = Number(lottie.w);
  const height = Number(lottie.h);
  const framesPerSecond = Number(lottie.fr);
  const durationMs = Math.round(((Number(lottie.op) - Number(lottie.ip)) / framesPerSecond) * 1_000);
  if (width !== 512 || height !== 512) throw new Error(`${file} must have a 512x512 canvas`);
  if (!Number.isFinite(framesPerSecond) || framesPerSecond <= 0 || framesPerSecond > 120) {
    throw new Error(`${file} has an invalid frame rate`);
  }
  if (!Number.isFinite(durationMs) || durationMs <= 0 || durationMs > 3_000) {
    throw new Error(`${file} exceeds the Telegram animation duration limit`);
  }

  const hash = sha256(compressed);
  const metadata = searchableMetadata(lottie, set, index);
  return {
    id,
    ...metadata,
    assetPath: `/assets/profile-emojis/v3/${hash}.tgs`,
    sha256: hash,
    sizeBytes: compressed.length,
    width,
    height,
    framesPerSecond,
    durationMs,
    enabled: true,
    compressed
  };
};

fs.rmSync(path.dirname(outputRoot), { recursive: true, force: true });
fs.mkdirSync(outputRoot, { recursive: true });

const assets = new Map();
const catalogSets = sets.map((set) => {
  const entries = set.entries ?? normalizedEntries(set.id);
  if (entries.length === 0) throw new Error(`Emoji set ${set.id} is empty`);
  const emojis = entries.map((entry, index) => inspectTgs(entry, set, index))
    .map(({ compressed, ...emoji }) => {
      assets.set(emoji.sha256, compressed);
      return emoji;
    });
  if (new Set(emojis.map(({ id }) => id)).size !== emojis.length) {
    throw new Error(`Emoji set ${set.id} contains duplicate IDs`);
  }
  return {
    id: set.id,
    labels: set.labels,
    thumbnailEmojiId: emojis[0].id,
    emojis
  };
});

for (const [hash, bytes] of assets) {
  fs.writeFileSync(path.join(outputRoot, `${hash}.tgs`), bytes, { mode: 0o644 });
}

const catalog = {
  version: 3,
  format: 'LOTTIE_TGS',
  defaultEmoji: DEFAULT_EMOJI,
  sets: catalogSets
};
fs.writeFileSync(path.join(outputRoot, 'catalog.json'), `${JSON.stringify(catalog, null, 2)}\n`);

const emojiCount = catalogSets.reduce((total, set) => total + set.emojis.length, 0);
console.log(
  `Built ${catalogSets.length} sets, ${emojiCount} emoji entries, ` +
  `${assets.size} unique TGS assets in ${path.relative(repositoryRoot, outputRoot)}`
);
