import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const PROFILE_BADGE_CATALOG_VERSION = 1;
export const DEFAULT_PROFILE_BADGE_ID = 'outline';

const badge = ({ id, kind = 'LOTTIE_TGS', hash, bytes, durationMs = 3_000, labels }) => ({
  id,
  kind,
  assetPath: `/assets/profile-badges/v1/${id}/${hash.slice(0, 8)}.${kind === 'LOTTIE_TGS' ? 'tgs' : 'webp'}`,
  sha256: hash,
  sizeBytes: bytes,
  width: kind === 'LOTTIE_TGS' ? 512 : 100,
  height: kind === 'LOTTIE_TGS' ? 512 : 100,
  ...(kind === 'LOTTIE_TGS' && { framesPerSecond: 60, durationMs }),
  labels,
  enabled: true
});

export const PROFILE_BADGES = Object.freeze([
  badge({
    id: 'outline', hash: 'c9d82aa82ab5605fe475429182104cbe4ab9e63e2c2d56f4a36956b662ad28c0', bytes: 8_199,
    labels: { en: 'Outline', ru: 'Контур' }
  }),
  badge({
    id: 'festive-flags', hash: '8931648cddc84e3b3bdc782a64d89391c4aac350106324322b847136708ab18e', bytes: 16_142,
    labels: { en: 'Festive flags', ru: 'Праздничные флаги' }
  }),
  badge({
    id: 'pumpkins', hash: 'db333f7de60360856f4ca55bb9b04717796042c3dd1e1ae863108d820e3213a7', bytes: 34_454,
    labels: { en: 'Pumpkins', ru: 'Тыквы' }
  }),
  badge({
    id: 'the-thing', hash: '6f4c37a4e6f20adcc46d799cd611529e1e22a6a0aed19a4b64286549ec911f8e', bytes: 32_503,
    labels: { en: 'The Thing', ru: 'Нечто' }
  }),
  badge({
    id: 'unicorn', hash: 'b772d0d38d908e1b1350e44b4b81e60b865e2d2c2f3cd8fc7538a88a1b18b7e6', bytes: 27_878,
    durationMs: 2_950, labels: { en: 'Unicorn', ru: 'Единорог' }
  }),
  badge({
    id: 'skull', hash: '1cc66a118830159736170bd32c67169bcb6680ececc9f39bc80176d8d6a63512', bytes: 36_573,
    labels: { en: 'Skull', ru: 'Череп' }
  }),
  badge({
    id: 'max', kind: 'STATIC_WEBP', hash: '69e2586ee90f7651c945cb440a4f67e6204a5379973cdf4cc4926d30a70a41d8', bytes: 4_138,
    labels: { en: 'Max', ru: 'Макс' }
  })
]);

export const PROFILE_BADGE_IDS = new Set(PROFILE_BADGES.map(({ id }) => id));

export const profileBadgeCatalogResponse = () => ({
  version: PROFILE_BADGE_CATALOG_VERSION,
  defaultBadgeId: DEFAULT_PROFILE_BADGE_ID,
  badges: PROFILE_BADGES
});

export const profileBadgeAssetDirectory = path.join(
  path.dirname(fileURLToPath(import.meta.url)), '..', 'public', 'assets', 'profile-badges'
);
