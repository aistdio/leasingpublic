import { apiJson, formatMoney, el } from './api.js';

const $ = (testId) => document.querySelector(`[data-testid="${testId}"]`);
const messages = $('chat-messages');
const form = $('chat-form');
const input = $('chat-input');
const send = $('chat-send');

function scrollDown() {
  messages.scrollTop = messages.scrollHeight;
}

function figureText(figure) {
  const value = figure.unit === 'RUB' ? formatMoney(figure.value) : `${figure.value} ${figure.unit}`;
  return figure.period ? `${figure.label}: ${value} (${figure.period})` : `${figure.label}: ${value}`;
}

function answerCard(answer) {
  const raw = el('pre', { class: 'raw-json', testid: 'chat-raw-json', hidden: '' }, JSON.stringify(answer, null, 2));
  const toggle = el('button', { type: 'button', class: 'link', testid: 'chat-toggle-json' }, 'Показать JSON');
  toggle.addEventListener('click', () => {
    raw.hidden = !raw.hidden;
    toggle.textContent = raw.hidden ? 'Показать JSON' : 'Скрыть JSON';
  });
  const copy = el('button', { type: 'button', class: 'link', testid: 'chat-copy-trace' }, 'Скопировать');
  copy.addEventListener('click', () => navigator.clipboard && navigator.clipboard.writeText(answer.trace_id));
  return el('div', { class: 'card answer', testid: 'chat-answer-card' },
    el('p', { testid: 'chat-answer-text' }, answer.answer),
    el('ul', { class: 'figures' }, ...(answer.figures || []).map((f) => el('li', { testid: 'chat-figure' }, figureText(f)))),
    el('div', { class: 'warnings' }, ...(answer.warnings || []).map((w) => el('span', { class: 'badge warn', testid: 'chat-warning' }, w))),
    el('div', { class: 'meta' },
      el('span', {}, 'Инструменты: ' + ((answer.tools_used || []).join(', ') || 'нет')),
      el('span', { testid: 'chat-trace-id' }, 'Трассировка: ' + answer.trace_id), copy, toggle),
    raw);
}

function errorCard(error) {
  const traceId = error.body && error.body.trace_id;
  return el('div', { class: 'card error', testid: 'chat-error' }, error.message,
    traceId ? el('div', { class: 'meta' }, 'Трассировка: ' + traceId) : '',
    error.requestId ? el('div', { class: 'meta' }, 'Код запроса: ' + error.requestId) : '');
}

async function ask(question) {
  const text = question.trim();
  if (!text) return;
  messages.append(el('div', { class: 'bubble user', testid: 'chat-question' }, text));
  const pending = el('div', { class: 'bubble pending', testid: 'chat-pending' }, 'Ассистент думает…');
  messages.append(pending);
  scrollDown();
  send.disabled = true;
  try {
    const answer = await apiJson('/api/agent/ask', { method: 'POST', body: JSON.stringify({ question: text }) });
    pending.replaceWith(answerCard(answer));
  } catch (e) {
    pending.replaceWith(errorCard(e));
  } finally {
    send.disabled = false;
    scrollDown();
  }
}

form.addEventListener('submit', (event) => {
  event.preventDefault();
  const question = input.value;
  input.value = '';
  ask(question);
});
input.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    form.requestSubmit();
  }
});
document.querySelectorAll('[data-testid="chat-suggestion"]').forEach((button) =>
  button.addEventListener('click', () => ask(button.textContent)));
