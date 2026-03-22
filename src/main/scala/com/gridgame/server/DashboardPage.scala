package com.gridgame.server

object DashboardPage {

  def html: String = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Grid Game - Server Dashboard</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; background: #0f1117; color: #e0e0e0; }
.header { background: #1a1d27; padding: 16px 24px; border-bottom: 1px solid #2a2d3a; display: flex; align-items: center; justify-content: space-between; }
.header h1 { font-size: 20px; color: #7c8aff; }
.header .status { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #888; }
.header .dot { width: 8px; height: 8px; border-radius: 50%; background: #4caf50; }
.tabs { display: flex; background: #1a1d27; border-bottom: 1px solid #2a2d3a; padding: 0 24px; }
.tab { padding: 10px 20px; cursor: pointer; color: #888; border-bottom: 2px solid transparent; font-size: 14px; }
.tab:hover { color: #ccc; }
.tab.active { color: #7c8aff; border-bottom-color: #7c8aff; }
.panel { display: none; padding: 24px; }
.panel.active { display: block; }
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 16px; margin-bottom: 16px; }
.card { background: #1a1d27; border: 1px solid #2a2d3a; border-radius: 8px; padding: 16px; }
.card h3 { font-size: 14px; color: #888; margin-bottom: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
.stat-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #1f2230; }
.stat-row:last-child { border-bottom: none; }
.stat-label { color: #888; font-size: 13px; }
.stat-value { color: #e0e0e0; font-size: 13px; font-weight: 600; }
.big-stat { text-align: center; padding: 12px; }
.big-stat .value { font-size: 36px; font-weight: 700; color: #7c8aff; }
.big-stat .label { font-size: 12px; color: #888; margin-top: 4px; }
.chart-container { position: relative; height: 220px; }

/* Events panel */
.filters { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.filters select, .filters input { background: #1a1d27; border: 1px solid #2a2d3a; color: #e0e0e0; padding: 8px 12px; border-radius: 6px; font-size: 13px; }
.filters input { width: 240px; }
.filters select { min-width: 120px; }
.event-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.event-table th { text-align: left; padding: 10px 12px; background: #1a1d27; color: #888; border-bottom: 1px solid #2a2d3a; position: sticky; top: 0; }
.event-table td { padding: 8px 12px; border-bottom: 1px solid #1f2230; vertical-align: top; }
.event-table tr:hover { background: #1f2233; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.badge.INFO { background: #1a3a2a; color: #4caf50; }
.badge.WARN { background: #3a3520; color: #ff9800; }
.badge.ERROR { background: #3a1a1a; color: #f44336; }
.badge.AUTH { background: #1a2a3a; color: #42a5f5; }
.badge.NETWORK { background: #2a1a3a; color: #ab47bc; }
.badge.GAME { background: #1a3a2a; color: #66bb6a; }
.badge.LOBBY { background: #3a3a1a; color: #ffca28; }
.badge.SECURITY { background: #3a1a1a; color: #ef5350; }
.badge.VALIDATION { background: #3a2a1a; color: #ff7043; }
.details-toggle { cursor: pointer; color: #7c8aff; text-decoration: underline; font-size: 12px; }
.details-content { display: none; margin-top: 6px; font-size: 12px; color: #999; }
.details-content.open { display: block; }
.detail-kv { display: inline-block; background: #0f1117; padding: 2px 6px; border-radius: 3px; margin: 2px 4px 2px 0; }
.auto-tail { display: flex; align-items: center; gap: 6px; }
.auto-tail input[type=checkbox] { accent-color: #7c8aff; }
.event-scroll { max-height: 600px; overflow-y: auto; }
.refresh-note { font-size: 11px; color: #555; }
</style>
</head>
<body>
<div class="header">
  <h1>Grid Game Server Dashboard</h1>
  <div class="status">
    <div class="dot" id="statusDot"></div>
    <span id="statusText">Connected</span>
    <span class="refresh-note">&bull; refreshes every 2s</span>
  </div>
</div>

<div class="tabs">
  <div class="tab active" onclick="switchTab('charts')">Charts</div>
  <div class="tab" onclick="switchTab('events')">Event Log</div>
  <div class="tab" onclick="switchTab('state')">Server State</div>
</div>

<div id="charts" class="panel active">
  <div class="grid">
    <div class="card"><div class="big-stat"><div class="value" id="playersOnline">0</div><div class="label">Players Online</div></div></div>
    <div class="card"><div class="big-stat"><div class="value" id="activeMatches">0</div><div class="label">Active Matches</div></div></div>
    <div class="card"><div class="big-stat"><div class="value" id="uptime">0s</div><div class="label">Uptime</div></div></div>
    <div class="card"><div class="big-stat"><div class="value" id="totalPackets">0</div><div class="label">Total Packets</div></div></div>
  </div>
  <div class="grid">
    <div class="card">
      <h3>Packets per Second</h3>
      <div class="chart-container"><canvas id="packetsChart"></canvas></div>
    </div>
    <div class="card">
      <h3>Players Online</h3>
      <div class="chart-container"><canvas id="playersChart"></canvas></div>
    </div>
    <div class="card">
      <h3>Packet Types</h3>
      <div class="chart-container"><canvas id="packetTypesChart"></canvas></div>
    </div>
    <div class="card">
      <h3>Security Events / sec</h3>
      <div class="chart-container"><canvas id="securityChart"></canvas></div>
    </div>
  </div>
</div>

<div id="events" class="panel">
  <div class="filters">
    <select id="filterCategory"><option value="">All Categories</option>
      <option>AUTH</option><option>NETWORK</option><option>GAME</option>
      <option>LOBBY</option><option>SECURITY</option><option>VALIDATION</option>
    </select>
    <select id="filterLevel"><option value="">All Levels</option>
      <option>INFO</option><option>WARN</option><option>ERROR</option>
    </select>
    <input type="text" id="filterSearch" placeholder="Search messages and details...">
    <input type="text" id="filterPlayer" placeholder="Player ID or name...">
    <div class="auto-tail">
      <input type="checkbox" id="autoTail" checked>
      <label for="autoTail" style="font-size:13px;color:#888;">Auto-tail</label>
    </div>
  </div>
  <div class="event-scroll" id="eventScroll">
    <table class="event-table">
      <thead><tr><th style="width:160px">Time</th><th style="width:90px">Level</th><th style="width:100px">Category</th><th>Message</th><th style="width:80px">Details</th></tr></thead>
      <tbody id="eventBody"></tbody>
    </table>
  </div>
</div>

<div id="state" class="panel">
  <div class="grid">
    <div class="card" id="stateCounters"><h3>Counters</h3></div>
    <div class="card" id="stateGauges"><h3>Gauges</h3></div>
  </div>
</div>

<script>
let packetsChart, playersChart, packetTypesChart, securityChart;
const maxPoints = 120;

function initCharts() {
  const commonOpts = {
    responsive: true, maintainAspectRatio: false,
    animation: { duration: 0 },
    scales: {
      x: { display: false },
      y: { beginAtZero: true, ticks: { color: '#666', font: { size: 11 } }, grid: { color: '#1f2230' } }
    },
    plugins: { legend: { labels: { color: '#888', font: { size: 11 } } } }
  };

  packetsChart = new Chart(document.getElementById('packetsChart'), {
    type: 'line',
    data: {
      labels: [],
      datasets: [
        { label: 'TCP In', data: [], borderColor: '#42a5f5', backgroundColor: 'rgba(66,165,245,0.1)', fill: true, tension: 0.3, pointRadius: 0 },
        { label: 'UDP In', data: [], borderColor: '#ab47bc', backgroundColor: 'rgba(171,71,188,0.1)', fill: true, tension: 0.3, pointRadius: 0 }
      ]
    },
    options: commonOpts
  });

  playersChart = new Chart(document.getElementById('playersChart'), {
    type: 'line',
    data: { labels: [], datasets: [{ label: 'Players', data: [], borderColor: '#66bb6a', backgroundColor: 'rgba(102,187,106,0.1)', fill: true, tension: 0.3, pointRadius: 0 }] },
    options: commonOpts
  });

  packetTypesChart = new Chart(document.getElementById('packetTypesChart'), {
    type: 'bar',
    data: { labels: [], datasets: [{ label: 'Count', data: [], backgroundColor: '#7c8aff' }] },
    options: { ...commonOpts, scales: { ...commonOpts.scales, x: { ticks: { color: '#666', font: { size: 10 }, maxRotation: 45 }, grid: { display: false } } } }
  });

  securityChart = new Chart(document.getElementById('securityChart'), {
    type: 'line',
    data: {
      labels: [],
      datasets: [
        { label: 'Validation', data: [], borderColor: '#ff7043', tension: 0.3, pointRadius: 0 },
        { label: 'Rate Limit', data: [], borderColor: '#ffca28', tension: 0.3, pointRadius: 0 },
        { label: 'HMAC', data: [], borderColor: '#ef5350', tension: 0.3, pointRadius: 0 }
      ]
    },
    options: commonOpts
  });
}

function formatUptime(secs) {
  if (secs < 60) return secs + 's';
  if (secs < 3600) return Math.floor(secs/60) + 'm ' + (secs%60) + 's';
  const h = Math.floor(secs/3600);
  const m = Math.floor((secs%3600)/60);
  return h + 'h ' + m + 'm';
}

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }) + '.' + String(ts % 1000).padStart(3, '0');
}

function updateCharts(data) {
  // Big stats
  document.getElementById('playersOnline').textContent = data.gauges['players.online'] || 0;
  document.getElementById('activeMatches').textContent = data.gauges['matches.active'] || 0;
  document.getElementById('uptime').textContent = formatUptime(data.uptime || 0);
  const tcp = data.counters['packets.tcp.received'] || 0;
  const udp = data.counters['packets.udp.received'] || 0;
  document.getElementById('totalPackets').textContent = (tcp + udp).toLocaleString();

  // Time-series charts
  const tcpHist = data.counterHistory['packets.tcp.received'] || [];
  const udpHist = data.counterHistory['packets.udp.received'] || [];
  const labels = tcpHist.map((_, i) => i);

  packetsChart.data.labels = labels;
  packetsChart.data.datasets[0].data = tcpHist;
  packetsChart.data.datasets[1].data = udpHist;
  packetsChart.update();

  const playerHist = data.gaugeHistory['players.online'] || [];
  playersChart.data.labels = playerHist.map((_, i) => i);
  playersChart.data.datasets[0].data = playerHist;
  playersChart.update();

  // Packet types bar chart
  const typeKeys = Object.keys(data.counters).filter(k => k.startsWith('packets.type.')).sort();
  packetTypesChart.data.labels = typeKeys.map(k => k.replace('packets.type.', ''));
  packetTypesChart.data.datasets[0].data = typeKeys.map(k => data.counters[k]);
  packetTypesChart.update();

  // Security chart
  const valHist = data.counterHistory['validation.rejected'] || [];
  const rlHist = data.counterHistory['ratelimit.rejected'] || [];
  const hmacHist = data.counterHistory['hmac.rejected'] || [];
  const secLabels = valHist.map((_, i) => i);
  securityChart.data.labels = secLabels;
  securityChart.data.datasets[0].data = valHist;
  securityChart.data.datasets[1].data = rlHist;
  securityChart.data.datasets[2].data = hmacHist;
  securityChart.update();

  // State panel
  let counterHtml = '<h3>Counters</h3>';
  Object.keys(data.counters).sort().forEach(k => {
    counterHtml += '<div class="stat-row"><span class="stat-label">' + k + '</span><span class="stat-value">' + data.counters[k].toLocaleString() + '</span></div>';
  });
  document.getElementById('stateCounters').innerHTML = counterHtml;

  let gaugeHtml = '<h3>Gauges</h3>';
  Object.keys(data.gauges).sort().forEach(k => {
    gaugeHtml += '<div class="stat-row"><span class="stat-label">' + k + '</span><span class="stat-value">' + data.gauges[k] + '</span></div>';
  });
  document.getElementById('stateGauges').innerHTML = gaugeHtml;
}

let lastEventTimestamp = 0;

function fetchEvents() {
  const cat = document.getElementById('filterCategory').value;
  const lvl = document.getElementById('filterLevel').value;
  const search = document.getElementById('filterSearch').value;
  const player = document.getElementById('filterPlayer').value;
  const autoTail = document.getElementById('autoTail').checked;

  let url = '/api/events?limit=200';
  if (cat) url += '&category=' + encodeURIComponent(cat);
  if (lvl) url += '&level=' + encodeURIComponent(lvl);
  if (search) url += '&search=' + encodeURIComponent(search);
  if (player) url += '&player=' + encodeURIComponent(player);
  if (autoTail && lastEventTimestamp > 0) url += '&since=' + (lastEventTimestamp + 1);

  fetch(url).then(r => r.json()).then(events => {
    const body = document.getElementById('eventBody');

    if (autoTail && lastEventTimestamp > 0) {
      // Prepend new events
      events.reverse().forEach(ev => {
        body.insertBefore(createEventRow(ev), body.firstChild);
        if (ev.timestamp > lastEventTimestamp) lastEventTimestamp = ev.timestamp;
      });
      // Trim to 500 rows
      while (body.children.length > 500) body.removeChild(body.lastChild);
    } else {
      // Full refresh
      body.innerHTML = '';
      events.forEach(ev => {
        body.appendChild(createEventRow(ev));
        if (ev.timestamp > lastEventTimestamp) lastEventTimestamp = ev.timestamp;
      });
    }
  }).catch(() => {});
}

function createEventRow(ev) {
  const tr = document.createElement('tr');
  const detailsId = 'd' + ev.timestamp + Math.random().toString(36).substr(2, 4);
  const hasDetails = Object.keys(ev.details).length > 0;
  const detailStr = Object.entries(ev.details).map(([k,v]) => '<span class="detail-kv"><b>' + esc(k) + ':</b> ' + esc(v) + '</span>').join(' ');

  tr.innerHTML = '<td>' + formatTime(ev.timestamp) + '</td>'
    + '<td><span class="badge ' + ev.level + '">' + ev.level + '</span></td>'
    + '<td><span class="badge ' + ev.category + '">' + ev.category + '</span></td>'
    + '<td>' + esc(ev.message)
    + (hasDetails ? '<div class="details-content" id="' + detailsId + '">' + detailStr + '</div>' : '')
    + '</td>'
    + '<td>' + (hasDetails ? '<span class="details-toggle" onclick="toggleDetails(\'' + detailsId + '\')">show</span>' : '-') + '</td>';
  return tr;
}

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

function toggleDetails(id) {
  const el = document.getElementById(id);
  if (el) el.classList.toggle('open');
}

function switchTab(name) {
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
  document.getElementById(name).classList.add('active');
  document.querySelectorAll('.tab').forEach(t => { if (t.textContent.toLowerCase().includes(name === 'state' ? 'server' : name)) t.classList.add('active'); });
  if (name === 'events') { lastEventTimestamp = 0; fetchEvents(); }
}

function pollMetrics() {
  fetch('/api/metrics').then(r => r.json()).then(updateCharts).catch(() => {
    document.getElementById('statusDot').style.background = '#f44336';
    document.getElementById('statusText').textContent = 'Disconnected';
  });
}

function pollEvents() {
  if (document.getElementById('events').classList.contains('active')) fetchEvents();
}

initCharts();
pollMetrics();
setInterval(pollMetrics, 2000);
setInterval(pollEvents, 2000);

// Debounce filter changes
['filterCategory','filterLevel','filterSearch','filterPlayer'].forEach(id => {
  document.getElementById(id).addEventListener('change', () => { lastEventTimestamp = 0; fetchEvents(); });
});
document.getElementById('filterSearch').addEventListener('input', (() => {
  let t; return () => { clearTimeout(t); t = setTimeout(() => { lastEventTimestamp = 0; fetchEvents(); }, 300); };
})());
document.getElementById('filterPlayer').addEventListener('input', (() => {
  let t; return () => { clearTimeout(t); t = setTimeout(() => { lastEventTimestamp = 0; fetchEvents(); }, 300); };
})());
</script>
</body>
</html>"""
}
