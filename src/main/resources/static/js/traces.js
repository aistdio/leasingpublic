import { apiJson, requireRole, logout, el, registerServiceWorker } from './api.js';
import './env-badge.js';

requireRole('MANAGER');
registerServiceWorker();

const $ = (testId) => document.querySelector(`[data-testid="${testId}"]`);
$('logout-button').addEventListener('click', logout);

const time = (iso) => (iso ? new Date(iso).toLocaleString('ru-RU') : '—');
const tokens = (input, output) => `${input} входных + ${output} выходных`;
const cell = (text) => el('td', { class: 'wrap mono' }, text ?? '—');

function showError(error) {
  $('traces-error').textContent = error.message;
  $('traces-error').hidden = false;
}

async function loadList() {
  const list = await apiJson('/api/agent/traces?limit=50');
  $('traces-table').querySelector('tbody').replaceChildren(...list.map((t) => {
    const row = el('tr', { class: 'clickable', testid: 'trace-row' },
      el('td', {}, time(t.createdAt)), el('td', {}, t.username), el('td', { class: 'wrap' }, t.question),
      el('td', { class: `status-${t.status.toLowerCase()}` }, t.status),
      el('td', { class: 'num' }, t.latencyMs == null ? '—' : String(t.latencyMs)),
      el('td', { class: 'num' }, String(t.totalTokens)));
    row.addEventListener('click', () => openTrace(t.id));
    return row;
  }));
}

async function openTrace(id) {
  $('traces-error').hidden = true;
  try {
    const t = await apiJson('/api/agent/traces/' + encodeURIComponent(String(id).trim()));
    const summary = [
      ['Идентификатор', t.id], ['Вопрос', t.question], ['Пользователь', `${t.username} (клиент ${t.clientId})`],
      ['Статус', t.status], ['Правило маршрутизации', t.routingRule], ['Модель', `${t.llmProvider} / ${t.llmModel}`],
      ['Версия промпта', t.promptVersion], ['Токены', tokens(t.promptTokens, t.completionTokens)],
      ['Время ответа, миллисекунды', t.latencyMs == null ? '—' : String(t.latencyMs)], ['Окружение', t.appEnv],
      ['Код запроса', t.requestId], ['Создана', time(t.createdAt)], ['Ошибка', t.error]];
    $('trace-summary').replaceChildren(...summary.flatMap(([key, value]) => [el('dt', {}, key), el('dd', {}, value ?? '—')]));
    $('trace-steps').querySelector('tbody').replaceChildren(...t.steps.map((s) => el('tr', { class: s.error ? 'overdue' : '' },
      el('td', {}, String(s.stepNo)), el('td', {}, s.stepType), el('td', {}, s.toolName ?? '—'), cell(s.argumentsJson),
      cell(s.resultJson), cell(s.error), el('td', { class: 'num' }, String(s.latencyMs)),
      el('td', { class: 'num' }, tokens(s.promptTokens, s.completionTokens)))));
    $('trace-answer').textContent = JSON.stringify(t.answer, null, 2);
    $('trace-detail').hidden = false;
    $('trace-id-input').value = t.id;
  } catch (e) {
    showError(e);
  }
}

$('trace-open').addEventListener('click', () => openTrace($('trace-id-input').value));
$('traces-refresh').addEventListener('click', () => loadList().catch(showError));
loadList().catch(showError);
const idFromUrl = new URLSearchParams(location.search).get('id');
if (idFromUrl) openTrace(idFromUrl);
