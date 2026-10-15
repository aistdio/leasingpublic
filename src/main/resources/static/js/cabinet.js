import { apiJson, requireRole, logout, formatMoney, formatDate, el, registerServiceWorker } from './api.js';
import './env-badge.js';
import './chat.js';

requireRole('CLIENT');
registerServiceWorker();

const $ = (testId) => document.querySelector(`[data-testid="${testId}"]`);
$('logout-button').addEventListener('click', logout);

export function showTab(name) {
  document.querySelectorAll('[data-tab]').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== name; });
}
document.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));

function showError(testId, error) {
  const node = $(testId);
  node.textContent = error.requestId ? `${error.message} (код запроса ${error.requestId})` : error.message;
  node.hidden = false;
}

function statusCell(row) {
  const text = row.overdue ? `${row.statusText}, просрочен` : row.statusText;
  return el('td', { class: `status status-${row.status.toLowerCase()}${row.overdue ? ' overdue' : ''}` }, text);
}

const money = (value) => el('td', { class: 'num' }, formatMoney(value));

function months(n) {
  const lastTwo = n % 100;
  const last = n % 10;
  if (last === 1 && lastTwo !== 11) return `${n} месяц`;
  if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) return `${n} месяца`;
  return `${n} месяцев`;
}

async function loadProfile() {
  const me = await apiJson('/api/me');
  $('client-name').replaceChildren(me.legalName, ' · ',
    el('abbr', { title: 'Идентификационный номер налогоплательщика' }, 'ИНН'), ` ${me.inn}`);
}

async function loadSchedule(contractId) {
  const rows = await apiJson(`/api/me/contracts/${encodeURIComponent(contractId)}/schedule`);
  $('schedule-table').querySelector('tbody').replaceChildren(...rows.map((p) => el('tr', { class: p.overdue ? 'overdue' : '' },
    el('td', {}, String(p.paymentId)), el('td', {}, formatDate(p.dueDate)), money(p.amount), money(p.principalPart),
    money(p.markup), money(p.vat), money(p.penalty), statusCell(p), el('td', {}, formatDate(p.paidDate)))));
}

async function loadContracts() {
  const contracts = await apiJson('/api/me/contracts');
  $('contracts-table').querySelector('tbody').replaceChildren(...contracts.map((c) => el('tr', { testid: `contract-row-${c.contractId}` },
    el('td', {}, c.contractNumber), el('td', {}, c.car), el('td', {}, c.statusText), el('td', {}, formatDate(c.startDate)),
    el('td', {}, months(c.durationInMonths)), money(c.monthlyPayment), money(c.remainingDebt),
    el('td', {}, formatDate(c.nextDueDate)))));
  $('contracts-empty').hidden = contracts.length > 0;
  const select = $('schedule-contract-select');
  select.replaceChildren(...contracts.map((c) => el('option', { value: String(c.contractId) }, `${c.contractNumber} — ${c.car}`)));
  if (contracts.length > 0) await loadSchedule(contracts[0].contractId);
}

async function loadPayments() {
  $('payments-error').hidden = true;
  const from = $('payments-from').value;
  const to = $('payments-to').value;
  const status = $('payments-status').value;
  try {
    const query = new URLSearchParams({ from, to });
    if (status) query.set('status', status);
    const [rows, summary] = await Promise.all([
      apiJson('/api/me/payments?' + query),
      apiJson('/api/me/payments/summary?' + new URLSearchParams({ from, to })),
    ]);
    $('payments-table').querySelector('tbody').replaceChildren(...rows.map((p) => el('tr', {},
      el('td', {}, p.contractNumber), el('td', {}, formatDate(p.dueDate)), money(p.amount), statusCell(p),
      el('td', {}, formatDate(p.paidDate)))));
    $('payments-total').textContent = `Оплачено: ${formatMoney(summary.paidTotal)} (${summary.paidCount}) · `
      + `Начислено: ${formatMoney(summary.accruedTotal)} (${summary.accruedCount})`;
  } catch (e) {
    showError('payments-error', e);
  }
}

const year = new Date().getFullYear();
$('payments-from').value = `${year}-01-01`;
$('payments-to').value = `${year}-12-31`;
$('payments-apply').addEventListener('click', loadPayments);
$('schedule-contract-select').addEventListener('change', (e) => loadSchedule(e.target.value).catch((err) => showError('cabinet-error', err)));

showTab('contracts');
Promise.all([loadProfile(), loadContracts(), loadPayments()]).catch((e) => showError('cabinet-error', e));
