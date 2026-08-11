const root = document.documentElement;
const themeButton = document.querySelector('.theme-button');
const themeIcon = document.querySelector('.theme-icon');
const status = document.querySelector('.status');
const statusText = document.querySelector('.status-text');
const systemDark = window.matchMedia('(prefers-color-scheme: dark)');

const savedTheme = localStorage.getItem('theme');
const applyTheme = (theme) => {
  const dark = theme === 'dark' || (theme === 'auto' && systemDark.matches);
  root.dataset.theme = dark ? 'dark' : 'light';
  themeIcon.textContent = theme === 'auto' ? '◐' : dark ? '☀' : '☾';
  document.querySelector('meta[name="theme-color"]').content = dark ? '#17212b' : '#ffffff';
};

let theme = ['auto', 'light', 'dark'].includes(savedTheme) ? savedTheme : 'auto';
applyTheme(theme);
themeButton.addEventListener('click', () => {
  const themes = ['auto', 'light', 'dark'];
  theme = themes[(themes.indexOf(theme) + 1) % themes.length];
  localStorage.setItem('theme', theme);
  applyTheme(theme);
});
systemDark.addEventListener('change', () => theme === 'auto' && applyTheme(theme));

fetch('/api/health/ready', { headers: { Accept: 'application/json' } })
  .then(async (response) => {
    if (!response.ok) throw new Error('not ready');
    const health = await response.json();
    status.classList.add('ready');
    statusText.textContent = health.telegram === 'configured'
      ? 'Server, database, and Telegram are ready'
      : 'Server is ready · add TELEGRAM_CLIENT_ID to enable sign-in';
  })
  .catch(() => {
    status.classList.add('error');
    statusText.textContent = 'Service is not ready yet';
  });
