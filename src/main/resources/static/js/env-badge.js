const badge = document.querySelector('[data-testid="env-badge"]');
if (badge) {
  fetch('/api/public/info')
    .then((response) => response.json())
    .then((info) => {
      badge.textContent = `${info.appEnv} · v${info.version}`;
      badge.dataset.env = info.appEnv;
    })
    .catch(() => { badge.textContent = 'нет связи с сервером'; });
}
