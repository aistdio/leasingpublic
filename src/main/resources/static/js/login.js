import { saveSession, getRole, homeFor, registerServiceWorker } from './api.js';
import './env-badge.js';

registerServiceWorker();
const form = document.getElementById('login-form');
const error = document.querySelector('[data-testid="login-error"]');
const notice = document.querySelector('[data-testid="login-notice"]');

if (new URLSearchParams(location.search).has('expired')) {
  notice.textContent = 'Сессия истекла, войдите снова.';
  notice.hidden = false;
} else if (getRole()) {
  location.replace(homeFor(getRole()));
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  error.hidden = true;
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: form.username.value.trim(), password: form.password.value }),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Ошибка входа: ' + response.status);
    saveSession(data.token, data.role);
    location.replace(homeFor(data.role));
  } catch (e) {
    error.textContent = e.message;
    error.hidden = false;
  }
});
