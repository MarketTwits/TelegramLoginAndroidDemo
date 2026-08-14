import { parsePhoneNumberFromString } from 'libphonenumber-js/max';

export const normalizeInternationalPhoneNumber = (value, allowMissingPlus = false) => {
  if (typeof value !== 'string') return null;
  const input = value.trim();
  if (!input || input.length > 40) return null;
  const internationalInput = allowMissingPlus && !input.startsWith('+') ? `+${input}` : input;
  const parsedPhone = parsePhoneNumberFromString(internationalInput, { extract: false });
  return parsedPhone?.isPossible() ? parsedPhone.number : null;
};
