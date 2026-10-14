const TOKEN_KEY = 'autolease.token';
const ROLE_KEY = 'autolease.role';

export function saveSession(token, role) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ROLE_KEY, role);
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
}

export function getRole() {
  return localStorage.getItem(ROLE_KEY);
}

export function homeFor(role) {
  return role === 'MANAGER' ? '/swagger-ui.html' : '/cabinet.html';
}

export function requireRole(role) {
  if (getRole() !== role) {
    clearSession();
    location.replace('/index.html');
    throw new Error('Нет доступа');
  }
}

export function logout() {
  clearSession();
  location.replace('/index.html');
}

export class ApiError extends Error {
  constructor(status, message, requestId, body) {
    super(message);
    this.status = status;
    this.requestId = requestId;
    this.body = body;
  }
}

export async function apiJson(path, options = {}) {
  const headers = new Headers(options.headers || {});
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) headers.set('Authorization', 'Bearer ' + token);
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    clearSession();
    location.replace('/index.html?expired=1');
    throw new ApiError(401, 'Сессия истекла');
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, (body && body.message) || 'Ошибка ' + response.status,
      response.headers.get('X-Request-Id'), body);
  }
  return body;
}

const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB' });

export function formatMoney(value) {
  return value === null || value === undefined ? '—' : money.format(value);
}

export function formatDate(iso) {
  if (!iso) return '—';
  const [year, month, day] = iso.split('-');
  return `${day}.${month}.${year}`;
}

export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key === 'testid') node.dataset.testid = value;
    else if (key === 'class') node.className = value;
    else node.setAttribute(key, value);
  }
  for (const child of children) {
    node.append(child instanceof Node ? child : document.createTextNode(child ?? ''));
  }
  return node;
}

export function registerServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  }
}
