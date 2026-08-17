import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const profileEmojiAssetDirectory = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  'public',
  'assets',
  'profile-emojis'
);

const catalogPath = path.join(profileEmojiAssetDirectory, 'v3', 'catalog.json');
const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf8'));

if (catalog.version !== 3 || catalog.format !== 'LOTTIE_TGS' || !Array.isArray(catalog.sets)) {
  throw new Error('Invalid profile emoji catalog');
}

export const DEFAULT_PROFILE_EMOJI = Object.freeze({ ...catalog.defaultEmoji });
const emojiKeys = new Set();
for (const set of catalog.sets) {
  if (!set.id || !Array.isArray(set.emojis) || set.emojis.length === 0) {
    throw new Error(`Invalid profile emoji set: ${set.id ?? 'unknown'}`);
  }
  for (const emoji of set.emojis) emojiKeys.add(`${set.id}/${emoji.id}`);
}
if (!emojiKeys.has(`${DEFAULT_PROFILE_EMOJI.setId}/${DEFAULT_PROFILE_EMOJI.emojiId}`)) {
  throw new Error('Default profile emoji is missing from the catalog');
}

export const isProfileEmoji = (selection) => Boolean(
  selection &&
  typeof selection.setId === 'string' &&
  typeof selection.emojiId === 'string' &&
  emojiKeys.has(`${selection.setId}/${selection.emojiId}`)
);

export const resolveStoredProfileEmoji = (profile) => {
  const stored = { setId: profile.emoji_set_id, emojiId: profile.emoji_id };
  if (isProfileEmoji(stored)) return stored;
  return { ...DEFAULT_PROFILE_EMOJI };
};

export const profileEmojiCatalogResponse = () => catalog;
