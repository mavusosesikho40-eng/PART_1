/* Baseflow Dashboard: draws the sample event and keeps it live.

   The numbers start from window.BASEFLOW_SAMPLE (js/sample-data.js). A small
   simulation then checks guests in every few seconds, moves people between
   areas and plays the sample's scripted moments (a gate going offline and
   coming back). Replace simulate() with your own feed to show real numbers.

   No libraries. Text from data is always set with textContent. */

(function () {
  'use strict';

  var SAMPLE = window.BASEFLOW_SAMPLE;
  if (!SAMPLE) return;

  var root = document.documentElement;
  var $ = function (id) { return document.getElementById(id); };

  /* ------------------------------------------------------------------------
     Formatting, in the brand's mechanics: 1 248 · 14:05 · 19 Nov 2026
     ------------------------------------------------------------------------ */

  var NBSP = ' ';
  var DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  function num(n) {
    return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, NBSP);
  }

  function pad(n) {
    return String(n).padStart(2, '0');
  }

  function toSec(hhmm) {
    var parts = hhmm.split(':');
    return Number(parts[0]) * 3600 + Number(parts[1]) * 60;
  }

  function clockText(sec) {
    return pad(Math.floor(sec / 3600) % 24) + ':' + pad(Math.floor(sec / 60) % 60);
  }

  function parseDate(iso) {
    var p = iso.split('-').map(Number);
    return new Date(p[0], p[1] - 1, p[2]);
  }

  function dateText(d) {
    return d.getDate() + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear();
  }

  function dayDate(d) {
    return DAY_NAMES[d.getDay()].slice(0, 3) + ' ' + d.getDate() + ' ' + MONTHS[d.getMonth()];
  }

  function percent(part, whole) {
    return whole ? Math.round((part / whole) * 100) : 0;
  }

  function clamp(v, lo, hi) {
    return Math.min(hi, Math.max(lo, v));
  }

  function plural(n, one, many) {
    return n === 1 ? one : many;
  }

  function ago(at, t) {
    var minutes = Math.floor((t - at) / 60);
    if (minutes < 1) return 'just now';
    return minutes + ' min ago';
  }

  /* ------------------------------------------------------------------------
     A seeded random source, so the sample plays the same way each time
     ------------------------------------------------------------------------ */

  var seed = 0x19112026;

  function random() {
    seed = (seed + 0x6d2b79f5) | 0;
    var t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  function between(lo, hi) {
    return lo + random() * (hi - lo);
  }

  function pickWeighted(items, weightOf) {
    var total = 0;
    items.forEach(function (item) { total += Math.max(0, weightOf(item)); });
    if (total <= 0) return null;
    var r = random() * total;
    for (var i = 0; i < items.length; i++) {
      r -= Math.max(0, weightOf(items[i]));
      if (r <= 0) return items[i];
    }
    return items[items.length - 1];
  }

  /* ------------------------------------------------------------------------
     DOM helpers
     ------------------------------------------------------------------------ */

  function h(tag, props) {
    var node = document.createElement(tag);
    if (props) {
      Object.keys(props).forEach(function (key) {
        var value = props[key];
        if (value === null || value === undefined || value === false) return;
        if (key === 'class') node.className = value;
        else if (key === 'style') Object.assign(node.style, value);
        else node.setAttribute(key, value === true ? '' : String(value));
      });
    }
    for (var i = 2; i < arguments.length; i++) add(node, arguments[i]);
    return node;
  }

  function add(node, child) {
    if (child === null || child === undefined || child === false) return;
    if (Array.isArray(child)) {
      child.forEach(function (c) { add(node, c); });
      return;
    }
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }

  var SVG_NS = 'http://www.w3.org/2000/svg';

  function svgEl(tag, attrs) {
    var node = document.createElementNS(SVG_NS, tag);
    if (attrs) Object.keys(attrs).forEach(function (k) { node.setAttribute(k, attrs[k]); });
    return node;
  }

  function icon(name, extraClass) {
    var svg = svgEl('svg', { 'class': 'icon' + (extraClass ? ' ' + extraClass : ''), 'aria-hidden': 'true', focusable: 'false' });
    svg.append(svgEl('use', { href: '#i-' + name }));
    return svg;
  }

  function badge(kind, text) {
    return h('span', { 'class': 'bf-status bf-status--' + kind }, text);
  }

  function setBadge(node, kind, text) {
    var cls = 'bf-status bf-status--' + kind;
    if (node.className !== cls) node.className = cls;
    setText(node, text);
  }

  function setText(node, text) {
    if (node.textContent === text) return false;
    node.textContent = text;
    return true;
  }

  // New data fades in: restart a short opacity fade on the changed number.
  function flash(node) {
    node.classList.remove('is-updated');
    void node.offsetWidth;
    node.classList.add('is-updated');
  }

  // "/ 1 500", read aloud as "of 1 500"
  function ofNodes(total) {
    return [h('span', { 'aria-hidden': 'true' }, '/ '), h('span', { 'class': 'visually-hidden' }, 'of '), total];
  }

  var memo = {};

  function changed(part, key) {
    if (memo[part] === key) return false;
    memo[part] = key;
    return true;
  }

  /* ------------------------------------------------------------------------
     State, copied from the sample
     ------------------------------------------------------------------------ */

  var event = SAMPLE.event;
  var eventDate = parseDate(event.date);
  var START = toSec(event.now);
  var DOORS_OPEN = toSec(event.doorsOpen);
  var DOORS_CLOSE = toSec(event.doorsClose);
  var openedAt = performance.now();

  function now() {
    return START + Math.floor((performance.now() - openedAt) / 1000);
  }

  function secondsOpen() {
    return (performance.now() - openedAt) / 1000;
  }

  function byId(list) {
    var map = {};
    list.forEach(function (item) { map[item.id] = item; });
    return map;
  }

  var areas = SAMPLE.areas.map(function (a) { return Object.assign({}, a); });
  var sessions = SAMPLE.sessions.map(function (x) {
    return Object.assign({ delay: 0 }, x, { startSec: toSec(x.start), endSec: toSec(x.end) });
  });
  var entries = SAMPLE.entryPoints.map(function (e) {
    return Object.assign({ online: true, pending: [], offlineSince: null }, e);
  });
  var teams = SAMPLE.teams.map(function (tm) { return Object.assign({}, tm); });
  var arrivals = SAMPLE.arrivals.map(function (s) { return { from: toSec(s.from), count: s.count }; });
  var latest = SAMPLE.latestCheckIns.map(function (c) { return { code: c.code, entry: c.entry, at: toSec(c.at) }; });
  var activity = SAMPLE.activity.map(function (a, i) { return Object.assign({ id: 'a' + i }, a, { at: toSec(a.at) }); });
  var week = SAMPLE.week.map(function (d) { return { date: parseDate(d.date), count: d.count }; });

  var areaById = byId(areas);
  var sessionById = byId(sessions);
  var entryById = byId(entries);
  var teamById = byId(teams);

  var checkedIn = entries.reduce(function (sum, e) { return sum + e.count; }, 0);
  var usedCodes = {};
  latest.forEach(function (c) { usedCodes[c.code] = true; });

  // When each part of the screen last changed. Shown as "Updated hh:mm".
  var updated = { checkIns: START, areas: START, sessions: START, entries: START, teams: START };

  var dismissed = {};   // alert id -> true
  var redirects = {};   // area id -> area id new arrivals go to instead
  var transfers = [];   // people walking to a session's new room
  var activitySeq = 0;

  /* ------------------------------------------------------------------------
     Derived values
     ------------------------------------------------------------------------ */

  function onSite() {
    return areas.reduce(function (sum, a) { return sum + a.count; }, 0);
  }

  function capacity() {
    return areas.reduce(function (sum, a) { return sum + a.capacity; }, 0);
  }

  function areaLevel(a) {
    if (a.count >= a.capacity) return 'critical';
    if (a.count >= a.capacity * 0.85) return 'attention';
    return 'ok';
  }

  function sessionTimes(x) {
    var shift = (x.delay || 0) * 60;
    return { start: x.startSec + shift, end: x.endSec + shift };
  }

  function phaseOf(x, t) {
    var tt = sessionTimes(x);
    if (t >= tt.end) return 'done';
    if (t >= tt.start) return 'now';
    return 'next';
  }

  function byStart(a, b) {
    return sessionTimes(a).start - sessionTimes(b).start || a.startSec - b.startSec;
  }

  function sessionStatus(x, t) {
    var phase = phaseOf(x, t);
    var room = areaById[x.area];
    if (x.delay) return ['attention', 'Running ' + x.delay + ' min late'];
    if (x.booked > room.capacity) return ['attention', 'Room too small'];
    if (phase === 'now') return ['ok', 'On schedule'];
    if (sessionTimes(x).start - t <= 30 * 60) return ['ok', 'On schedule'];
    return ['neutral', 'Starts ' + clockText(sessionTimes(x).start)];
  }

  function overlaps(other, tt) {
    var ot = sessionTimes(other);
    return ot.start < tt.end && tt.start < ot.end;
  }

  // The smallest free place that seats everyone booked for a session.
  function roomFor(x) {
    var tt = sessionTimes(x);
    var free = areas.filter(function (a) {
      return a.id !== x.area && a.capacity >= x.booked && !sessions.some(function (o) {
        return o !== x && o.area === a.id && overlaps(o, tt);
      });
    });
    free.sort(function (a, b) { return a.capacity - b.capacity; });
    return free[0] || null;
  }

  function pull(a) {
    if (a.count >= a.capacity || redirects[a.id]) return 0;
    var p = a.pull;
    Object.keys(redirects).forEach(function (fromId) {
      if (redirects[fromId] === a.id) p += areaById[fromId].pull;
    });
    return p;
  }

  function teamLevel(tm, t) {
    if (tm.done >= tm.tasks) return ['ok', 'All done'];
    var dayShare = clamp((t - DOORS_OPEN) / (DOORS_CLOSE - DOORS_OPEN), 0, 1);
    return tm.done / tm.tasks >= dayShare - 0.1 ? ['ok', 'On track'] : ['attention', 'Behind'];
  }

  function lastUpdate() {
    return Math.max(updated.checkIns, updated.areas, updated.sessions, updated.entries, updated.teams);
  }

  /* ------------------------------------------------------------------------
     The simulation: check-ins, people moving, scripted moments
     ------------------------------------------------------------------------ */

  function slotFor(t) {
    var from = Math.floor(t / 1800) * 1800;
    for (var i = arrivals.length - 1; i >= 0; i--) {
      if (arrivals[i].from === from) return arrivals[i];
      if (arrivals[i].from < from) break;
    }
    var slot = { from: from, count: 0 };
    arrivals.push(slot);
    arrivals.sort(function (a, b) { return a.from - b.from; });
    return slot;
  }

  function newCode() {
    var code;
    do {
      code = 'BK-' + (4000 + Math.floor(random() * 6000));
    } while (usedCodes[code]);
    usedCodes[code] = true;
    return code;
  }

  function record(entry, code, at) {
    checkedIn += 1;
    entry.count += 1;
    slotFor(at).count += 1;
    var area = pickWeighted(areas, pull);
    if (area) area.count += 1;
    latest.unshift({ code: code, entry: entry.id, at: at, fresh: true });
    var t = now();
    updated.checkIns = t;
    updated.areas = t;
    updated.entries = t;
  }

  function stir() {
    var moves = Math.floor(between(0, 3));
    for (var i = 0; i < moves; i++) {
      var from = pickWeighted(areas, function (a) { return a.count; });
      var to = pickWeighted(areas, pull);
      if (from && to && from !== to) {
        from.count -= 1;
        to.count += 1;
      }
    }
    if (random() < 0.35) {
      var leaving = pickWeighted(areas, function (a) { return a.count; });
      if (leaving) leaving.count -= 1;
    }
    transfers.forEach(function (tr) {
      if (tr.left <= 0) return;
      var src = areaById[tr.from];
      var dest = areaById[tr.to];
      var n = Math.min(tr.left, src.count, dest.capacity - dest.count, Math.floor(between(3, 8)));
      if (n <= 0) { tr.left = 0; return; }
      src.count -= n;
      dest.count += n;
      tr.left -= n;
    });
  }

  function checkIn() {
    var t = now();
    if (t >= DOORS_CLOSE) return;
    var entry = pickWeighted(entries, function (e) { return e.share; });
    var code = newCode();
    if (!entry.online) {
      entry.pending.push({ code: code, at: t });
    } else {
      record(entry, code, t);
      if (latest.length > 5) latest.length = 5;
    }
    stir();
    requestRender();
  }

  function log(item) {
    activitySeq += 1;
    activity.unshift(Object.assign({ id: 'n' + activitySeq, fresh: true }, item));
    if (activity.length > 5) activity.length = 5;
  }

  function runStep(step) {
    var t = now();
    var e;
    if (step.type === 'offline') {
      e = entryById[step.entry];
      e.online = false;
      e.offlineSince = t;
      updated.entries = t;
      log({ at: t, kind: 'critical', icon: 'wifi-off', text: e.name + ' went offline.', meta: 'Its scanner is saving check-ins' });
    } else if (step.type === 'online') {
      e = entryById[step.entry];
      var waiting = e.pending.slice().sort(function (a, b) { return a.at - b.at; });
      e.online = true;
      e.pending = [];
      e.offlineSince = null;
      waiting.forEach(function (c) { record(e, c.code, c.at); });
      latest.sort(function (a, b) { return b.at - a.at; });
      if (latest.length > 5) latest.length = 5;
      updated.entries = t;
      log({
        at: t, kind: 'ok', icon: 'circle-check',
        text: e.name + ' is back online. ' + waiting.length + ' ' + plural(waiting.length, 'check-in', 'check-ins') + ' synced.',
        meta: e.place
      });
    } else if (step.type === 'task') {
      var tm = teamById[step.team];
      if (tm.done < tm.tasks) tm.done += 1;
      updated.teams = t;
      log({ at: t, kind: 'ok', icon: 'list-checks', text: step.text, meta: tm.name + ' · ' + tm.done + ' of ' + tm.tasks + ' tasks done' });
    }
    requestRender();
  }

  function simulate() {
    (function next() {
      var wait = secondsOpen() < 180 ? between(2.5, 5.5) : between(6, 14);
      window.setTimeout(function () { checkIn(); next(); }, wait * 1000);
    })();
    (SAMPLE.script || []).forEach(function (step) {
      window.setTimeout(function () { runStep(step); }, step.after * 1000);
    });
  }

  /* ------------------------------------------------------------------------
     Alerts: what needs doing now, worst first
     ------------------------------------------------------------------------ */

  function alertsNow(t) {
    var list = [];

    entries.forEach(function (e) {
      if (e.online) return;
      list.push({
        id: 'offline-' + e.id,
        kind: 'critical',
        icon: 'wifi-off',
        title: e.name + ' is offline.',
        text: 'Its scanner is saving check-ins and will sync when it reconnects.',
        meta: 'Since ' + clockText(e.offlineSince) + ' · ' + num(e.pending.length) + ' waiting to sync'
      });
    });

    areas.forEach(function (a) {
      var level = areaLevel(a);
      if (level === 'ok') return;
      var to = a.overflow ? areaById[a.overflow] : null;
      var sentTo = redirects[a.id] ? areaById[redirects[a.id]] : null;
      var title = a.name + (level === 'critical' ? ' is full.' : ' is nearly full.');
      var text = sentTo
        ? 'Floor teams are sending new arrivals to ' + sentTo.name + '.'
        : to ? 'Send new arrivals to ' + to.name + '.' : 'Hold new arrivals at the door.';
      list.push({
        id: 'area-' + a.id + '-' + level + (sentTo ? '-sent' : ''),
        kind: level,
        icon: 'users',
        title: title,
        text: text,
        meta: percent(a.count, a.capacity) + '% full · Updated ' + clockText(updated.areas),
        action: sentTo ? null : {
          label: 'Tell floor teams',
          preset: {
            kind: 'notice',
            message: title + ' ' + text,
            teams: ['front-desk', 'ushers'],
            effect: to ? { from: a.id, to: to.id } : null
          }
        }
      });
    });

    sessions.forEach(function (x) {
      if (phaseOf(x, t) === 'done') return;
      var room = areaById[x.area];
      if (x.booked <= room.capacity) return;
      var to = roomFor(x);
      list.push({
        id: 'room-' + x.id + '-' + x.area,
        kind: 'attention',
        icon: 'arrow-right-left',
        title: x.title + ' has ' + num(x.booked) + ' bookings.',
        text: room.name + ' holds ' + num(room.capacity) + '.' + (to ? ' Move it to ' + to.name + ', which holds ' + num(to.capacity) + '.' : ''),
        meta: 'Starts ' + clockText(sessionTimes(x).start) + ' · ' + room.name,
        action: to ? { label: 'Move to ' + to.name, preset: { kind: 'room', session: x.id, room: to.id } } : null
      });
    });

    var order = { critical: 0, attention: 1 };
    return list
      .filter(function (a) { return !dismissed[a.id]; })
      .sort(function (a, b) { return order[a.kind] - order[b.kind]; });
  }

  var currentAlerts = [];

  function buildAlert(a) {
    var title = h('strong');
    var text = h('span');
    var meta = h('p', { 'class': 'alert__meta' });
    var actionBtn = h('button', { 'class': 'bf-btn bf-btn--secondary', type: 'button', 'data-action': a.id });
    var dismiss = h('button', { 'class': 'bf-btn bf-btn--quiet bf-btn--icon', type: 'button', 'data-dismiss': a.id }, icon('x'));
    var node = h('div', { 'class': 'alert alert--' + a.kind + ' is-new', 'data-id': a.id },
      h('div', { 'class': 'alert__main' },
        icon(a.icon, 'alert__icon'),
        h('div', { 'class': 'alert__body' }, h('p', { 'class': 'alert__text' }, title, ' ', text), meta)),
      h('div', { 'class': 'alert__actions' }, actionBtn, dismiss));
    node.refs = { title: title, text: text, meta: meta, action: actionBtn, dismiss: dismiss };
    return node;
  }

  function renderAlerts(t) {
    var list = alertsNow(t);
    currentAlerts = list;
    var box = $('alerts');
    var key = JSON.stringify(list.map(function (a) { return [a.id, a.title, a.text, a.meta, a.action && a.action.label]; }));
    if (!changed('alerts', key)) return;

    var existing = {};
    Array.prototype.forEach.call(box.children, function (node) { existing[node.getAttribute('data-id')] = node; });

    var nodes = list.map(function (a) {
      var node = existing[a.id];
      if (!node) {
        node = buildAlert(a);
        announce(a.title + ' ' + a.text);
      }
      delete existing[a.id];
      setText(node.refs.title, a.title);
      setText(node.refs.text, a.text);
      setText(node.refs.meta, a.meta);
      node.refs.action.hidden = !a.action;
      if (a.action) setText(node.refs.action, a.action.label);
      node.refs.dismiss.setAttribute('aria-label', 'Dismiss: ' + a.title);
      return node;
    });

    Object.keys(existing).forEach(function (id) { existing[id].remove(); });

    // Re-order only when needed, so a focused button keeps its focus.
    nodes.forEach(function (node, i) {
      if (box.children[i] !== node) box.insertBefore(node, box.children[i] || null);
    });
  }

  /* ------------------------------------------------------------------------
     Rendering
     ------------------------------------------------------------------------ */

  function tileRefs(id) {
    var tile = $(id);
    return {
      tile: tile,
      value: tile.querySelector('[data-value]'),
      of: tile.querySelector('[data-of]'),
      bar: tile.querySelector('[data-bar]'),
      badge: tile.querySelector('[data-badge]'),
      updated: tile.querySelector('[data-updated]')
    };
  }

  var kpi = {
    checkedIn: tileRefs('kpi-checked-in'),
    onSite: tileRefs('kpi-on-site'),
    sessions: tileRefs('kpi-sessions'),
    entries: tileRefs('kpi-entries')
  };

  function setTile(ref, o) {
    if (setText(ref.value, o.value) && memo.ready) flash(ref.value);
    if (ref.of.textContent !== o.ofText) ref.of.replaceChildren.apply(ref.of, [].concat(o.of));
    ref.bar.style.width = (clamp(o.ratio, 0, 1) * 100).toFixed(1) + '%';
    ref.tile.classList.toggle('is-attention', o.level === 'attention');
    ref.tile.classList.toggle('is-critical', o.level === 'critical');
    setBadge(ref.badge, o.badge[0], o.badge[1]);
    setText(ref.updated, 'Updated ' + clockText(o.updated));
  }

  function renderKpis(t) {
    setTile(kpi.checkedIn, {
      value: num(checkedIn),
      of: ofNodes(num(event.expected)),
      ofText: '/ of ' + num(event.expected),
      ratio: checkedIn / event.expected,
      level: 'ok',
      badge: ['live', 'Live'],
      updated: updated.checkIns
    });

    var site = onSite();
    var cap = capacity();
    var siteLevel = site >= cap ? 'critical' : site >= cap * 0.85 ? 'attention' : 'ok';
    setTile(kpi.onSite, {
      value: num(site),
      of: ofNodes(num(cap)),
      ofText: '/ of ' + num(cap),
      ratio: site / cap,
      level: siteLevel,
      badge: [siteLevel, siteLevel === 'ok' ? 'Below capacity' : siteLevel === 'attention' ? 'Nearly full' : 'Full'],
      updated: updated.areas
    });

    var onTime = sessions.filter(function (x) { return !x.delay; }).length;
    var late = sessions.filter(function (x) { return x.delay && phaseOf(x, t) !== 'done'; }).length;
    setTile(kpi.sessions, {
      value: String(onTime),
      of: 'of ' + sessions.length,
      ofText: 'of ' + sessions.length,
      ratio: onTime / sessions.length,
      level: late ? 'attention' : 'ok',
      badge: late ? ['attention', late + ' running late'] : ['ok', 'On schedule'],
      updated: updated.sessions
    });

    var offline = entries.filter(function (e) { return !e.online; });
    setTile(kpi.entries, {
      value: String(entries.length - offline.length),
      of: 'of ' + entries.length,
      ofText: 'of ' + entries.length,
      ratio: (entries.length - offline.length) / entries.length,
      level: offline.length ? 'critical' : 'ok',
      badge: offline.length
        ? ['critical', offline.length === 1 ? offline[0].name + ' offline' : offline.length + ' offline']
        : ['ok', 'All online'],
      updated: updated.entries
    });
  }

  var areaRefs = {};

  function buildAreas() {
    var grid = $('areas-grid');
    areas.forEach(function (a) {
      var refs = {
        value: h('span'),
        of: h('small', null, ofNodes(num(a.capacity))),
        now: h('p', { 'class': 'area__now' }),
        bar: h('span'),
        badge: h('span'),
        pct: h('span')
      };
      refs.node = h('article', { 'class': 'area', 'aria-labelledby': 'area-' + a.id },
        h('h3', { 'class': 'area__name', id: 'area-' + a.id }, a.name),
        refs.now,
        h('p', { 'class': 'area__value' }, refs.value, refs.of),
        h('div', { 'class': 'bf-tile__bar', 'aria-hidden': 'true' }, refs.bar),
        h('div', { 'class': 'bf-tile__foot' }, refs.badge, refs.pct));
      grid.append(refs.node);
      areaRefs[a.id] = refs;
    });
  }

  function areaNowText(a, t) {
    var here = sessions
      .filter(function (x) { return x.area === a.id && phaseOf(x, t) !== 'done'; })
      .sort(byStart);
    if (!here.length) return a.note || 'No more sessions today';
    var x = here[0];
    if (phaseOf(x, t) === 'now') return 'Now: ' + x.title;
    return 'Next: ' + x.title + ' at ' + clockText(sessionTimes(x).start);
  }

  function renderAreas(t) {
    areas.forEach(function (a) {
      var r = areaRefs[a.id];
      var level = areaLevel(a);
      if (setText(r.value, num(a.count)) && memo.ready) flash(r.value);
      r.bar.style.width = (clamp(a.count / a.capacity, 0, 1) * 100).toFixed(1) + '%';
      r.node.classList.toggle('is-attention', level === 'attention');
      r.node.classList.toggle('is-critical', level === 'critical');
      setBadge(r.badge, level, level === 'ok' ? 'Open' : level === 'attention' ? 'Nearly full' : 'Full');
      setText(r.pct, percent(a.count, a.capacity) + '% full');
      setText(r.now, areaNowText(a, t));
    });
    setText($('areas-meta'), num(onSite()) + ' on site · Updated ' + clockText(updated.areas));
  }

  function renderSessions(t) {
    var open = sessions.filter(function (x) { return phaseOf(x, t) !== 'done'; }).sort(byStart);
    var done = sessions.filter(function (x) { return phaseOf(x, t) === 'done'; });
    var lateDone = done.filter(function (x) { return x.delay; }).length;

    var key = JSON.stringify(open.map(function (x) {
      return [x.id, x.area, x.delay, phaseOf(x, t), sessionStatus(x, t)[1], x.movedFrom ? ago(x.movedAt, t) : ''];
    })) + done.length;

    if (changed('sessions', key)) {
      $('sessions').replaceChildren.apply($('sessions'), open.map(function (x) {
        var status = sessionStatus(x, t);
        var phase = phaseOf(x, t);
        var room = areaById[x.area];
        return h('li', { 'class': 'session' },
          h('p', { 'class': 'session__time' },
            clockText(x.startSec),
            phase === 'now' ? h('span', { 'class': 'session__now' }, 'Now') : null),
          h('div', { 'class': 'session__body' },
            h('p', { 'class': 'session__title' }, x.title),
            h('p', { 'class': 'session__where' }, room.name + ' · ' + num(x.booked) + ' booked'),
            x.movedFrom ? h('p', { 'class': 'notice notice--sm' }, icon('arrow-right-left'),
              h('span', null, 'Moved from ' + areaById[x.movedFrom].name + ' · ' + ago(x.movedAt, t))) : null,
            badge(status[0], status[1])));
      }));

      var foot = '';
      if (done.length) {
        foot = done.length + ' earlier ' + plural(done.length, 'session', 'sessions') +
          (lateDone ? ' finished, ' + lateDone + ' late.' : ' finished on time.');
      }
      setText($('sessions-done'), foot);
    }
    setText($('schedule-meta'), open.length + ' to go · Updated ' + clockText(updated.sessions));
  }

  function renderEntries(t) {
    var key = JSON.stringify(entries.map(function (e) { return [e.count, e.online, e.pending.length]; }));
    if (changed('entries', key)) {
      $('entries-list').replaceChildren.apply($('entries-list'), entries.map(function (e) {
        return h('li', { 'class': 'entry' + (e.online ? '' : ' entry--offline') },
          h('span', { 'class': 'entry__icon' }, icon(e.online ? (e.kind === 'gate' ? 'scan-line' : 'user-check') : 'wifi-off')),
          h('div', null,
            h('p', { 'class': 'entry__name' }, e.name),
            h('p', { 'class': 'entry__place' }, e.online ? e.place : num(e.pending.length) + ' saved on the scanner')),
          h('div', { 'class': 'entry__side' },
            h('p', { 'class': 'entry__count' }, num(e.count), h('span', { 'class': 'visually-hidden' }, ' check-ins')),
            badge(e.online ? 'ok' : 'critical', e.online ? 'Online' : 'Offline')));
      }));
    }
    setText($('entries-meta'), num(checkedIn) + ' today · Updated ' + clockText(updated.entries));

    if (changed('latest', latest.map(function (c) { return c.code; }).join())) {
      $('checkins').replaceChildren.apply($('checkins'), latest.map(function (c) {
        var e = entryById[c.entry];
        var li = h('li', { 'class': 'checkin' + (c.fresh && memo.ready ? ' is-new' : '') },
          icon('circle-check'),
          h('span', { 'class': 'checkin__code' }, c.code),
          h('span', { 'class': 'checkin__where' }, e.name + ' · ' + clockText(c.at)),
          h('span', { 'class': 'visually-hidden' }, 'checked in'));
        c.fresh = false;
        return li;
      }));
    }
  }

  function renderTeams(t) {
    var rows = teams.map(function (tm) { return [tm.done, tm.onShift, teamLevel(tm, t)[1]]; });
    if (changed('teams', JSON.stringify(rows))) {
      $('teams-body').replaceChildren.apply($('teams-body'), teams.map(function (tm) {
        var level = teamLevel(tm, t);
        return h('tr', { 'class': level[0] === 'attention' ? 'is-attention' : null },
          h('td', null, tm.name),
          h('td', { 'class': 'is-num' }, String(tm.onShift), h('span', { 'class': 'cell-suffix' }, ' on shift')),
          h('td', null,
            h('div', { 'class': 'progress' },
              h('span', null, tm.done + ' of ' + tm.tasks, h('span', { 'class': 'cell-suffix' }, ' tasks done')),
              h('div', { 'class': 'bf-tile__bar', 'aria-hidden': 'true' },
                h('span', { style: { width: percent(tm.done, tm.tasks) + '%' } })))),
          h('td', null, badge(level[0], level[1])));
      }));
    }
    var staff = teams.reduce(function (n, tm) { return n + tm.onShift; }, 0);
    var done = teams.reduce(function (n, tm) { return n + tm.done; }, 0);
    var tasks = teams.reduce(function (n, tm) { return n + tm.tasks; }, 0);
    setText($('teams-meta'), staff + ' on shift · ' + done + ' of ' + tasks + ' tasks done');
  }

  function renderActivity() {
    if (!changed('activity', activity.map(function (a) { return a.id; }).join())) return;
    $('activity-list').replaceChildren.apply($('activity-list'), activity.map(function (a) {
      var li = h('li', { 'class': 'act act--' + a.kind + (a.fresh && memo.ready ? ' is-new' : '') },
        h('time', { 'class': 'act__time' }, clockText(a.at)),
        icon(a.icon, 'act__icon'),
        h('div', null,
          h('p', null, a.text),
          a.meta ? h('p', { 'class': 'act__meta' }, a.meta) : null));
      a.fresh = false;
      return li;
    }));
  }

  /* ------------------------------------------------------------------------
     Bar charts: one series, thin bars on one baseline, a tooltip that
     follows the pointer and the arrow keys, and a table beside each
     ------------------------------------------------------------------------ */

  function niceScale(maxValue) {
    var raw = Math.max(1, maxValue) / 5;
    var magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
    var norm = raw / magnitude;
    var step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10) * magnitude;
    var top = Math.ceil(Math.max(1, maxValue) / step) * step;
    var ticks = [];
    for (var v = 0; v <= top + step / 2; v += step) ticks.push(Math.round(v));
    return { top: top, ticks: ticks };
  }

  function BarChart(container, opts) {
    var data = [];
    var active = -1;
    var geom = null;
    var lastWidth = 0;
    var lastHeight = 0;

    container.tabIndex = 0;
    container.setAttribute('aria-label', opts.name + '. Use the left and right arrow keys to read each bar.');

    var tip = h('div', { 'class': 'tooltip', hidden: true, 'aria-hidden': 'true' });
    var spoken = h('p', { 'class': 'visually-hidden', 'aria-live': 'polite' });
    container.append(tip, spoken);

    function fontSize() {
      return parseFloat(getComputedStyle(container).getPropertyValue('--fs-caption')) || 13;
    }

    function draw() {
      var width = Math.floor(container.clientWidth);
      if (!width || !data.length) return;
      lastWidth = width;
      lastHeight = Math.floor(container.clientHeight);

      var fs = fontSize();
      // A filling chart takes the height its panel gives it; others size from width.
      var height = opts.fill && lastHeight ? lastHeight : opts.height(width);
      var scale = niceScale(Math.max.apply(null, data.map(function (d) { return d.value; })));
      var tickWidth = Math.max.apply(null, scale.ticks.map(function (v) { return num(v).length; })) * fs * 0.62;
      var m = { top: fs + 16, right: 4, bottom: fs + 14, left: Math.ceil(tickWidth) + 10 };
      var iw = width - m.left - m.right;
      var ih = height - m.top - m.bottom;
      var band = iw / data.length;
      var barWidth = Math.max(2, Math.min(24, band * 0.64, band - 2));
      var base = m.top + ih;
      var y = function (v) { return m.top + ih - (v / scale.top) * ih; };

      var svg = svgEl('svg', {
        viewBox: '0 0 ' + width + ' ' + height,
        width: width,
        height: height,
        role: 'img',
        'aria-label': opts.summary(data)
      });

      scale.ticks.forEach(function (v) {
        var yy = Math.round(y(v)) + 0.5;
        svg.append(svgEl('line', { 'class': v === 0 ? 'chart__base' : 'chart__grid', x1: m.left, x2: width - m.right, y1: yy, y2: yy }));
        var label = svgEl('text', { 'class': 'chart__tick', x: m.left - 10, y: yy, 'text-anchor': 'end', dy: '0.32em' });
        label.textContent = num(v);
        svg.append(label);
      });

      var labelStep = opts.labelStep ? opts.labelStep(band, fs) : 1;

      data.forEach(function (d, i) {
        var cx = m.left + band * (i + 0.5);
        var x0 = cx - barWidth / 2;
        var top = y(d.value);
        var barHeight = base - top;
        if (barHeight > 0.5) {
          var r = Math.min(4, barHeight, barWidth / 2);
          var path = 'M' + x0 + ',' + base + 'V' + (top + r) +
            'Q' + x0 + ',' + top + ' ' + (x0 + r) + ',' + top +
            'H' + (x0 + barWidth - r) +
            'Q' + (x0 + barWidth) + ',' + top + ' ' + (x0 + barWidth) + ',' + (top + r) +
            'V' + base + 'Z';
          svg.append(svgEl('path', { 'class': 'chart__bar' + (i === active ? ' is-active' : ''), d: path }));
        }
        if (opts.showLabel(d, i, labelStep)) {
          var tick = svgEl('text', { 'class': 'chart__tick', x: cx, y: base + fs + 8, 'text-anchor': 'middle' });
          tick.textContent = opts.label(d, band, fs);
          svg.append(tick);
        }
        if (opts.direct(d, i)) {
          var value = svgEl('text', { 'class': 'chart__value', x: cx, y: top - 7, 'text-anchor': 'middle' });
          value.textContent = num(d.value);
          svg.append(value);
        }
      });

      var old = container.querySelector('svg');
      if (old) old.replaceWith(svg);
      else container.insertBefore(svg, tip);

      geom = { m: m, band: band, width: width, y: y };
      placeTip();
    }

    function placeTip() {
      if (active < 0 || !geom || !data[active]) {
        tip.hidden = true;
        return;
      }
      var d = data[active];
      tip.replaceChildren(h('strong', null, num(d.value) + ' ' + opts.unit(d.value)), h('span', null, d.title));
      tip.hidden = false;
      var cx = geom.m.left + geom.band * (active + 0.5);
      var left = clamp(cx - tip.offsetWidth / 2, 0, Math.max(0, geom.width - tip.offsetWidth));
      var top = geom.y(d.value) - tip.offsetHeight - 12;
      tip.style.left = left + 'px';
      tip.style.top = Math.max(-tip.offsetHeight / 2, top) + 'px';
    }

    function setActive(i) {
      active = i;
      var bars = container.querySelectorAll('.chart__bar');
      Array.prototype.forEach.call(bars, function (bar, j) { bar.classList.toggle('is-active', j === i); });
      placeTip();
    }

    function indexAt(clientX) {
      if (!geom) return -1;
      var x = clientX - container.getBoundingClientRect().left - geom.m.left;
      return clamp(Math.floor(x / geom.band), 0, data.length - 1);
    }

    container.addEventListener('pointermove', function (ev) { setActive(indexAt(ev.clientX)); });
    container.addEventListener('pointerdown', function (ev) { setActive(indexAt(ev.clientX)); });
    container.addEventListener('pointerleave', function () {
      if (document.activeElement !== container) setActive(-1);
    });
    container.addEventListener('focus', function () {
      if (active < 0) setActive(data.length - 1);
    });
    container.addEventListener('blur', function () { setActive(-1); });
    container.addEventListener('keydown', function (ev) {
      var next = active;
      if (ev.key === 'ArrowRight') next = Math.min(data.length - 1, active + 1);
      else if (ev.key === 'ArrowLeft') next = Math.max(0, active - 1);
      else if (ev.key === 'Home') next = 0;
      else if (ev.key === 'End') next = data.length - 1;
      else if (ev.key === 'Escape') { setActive(-1); return; }
      else return;
      ev.preventDefault();
      setActive(next);
      var d = data[next];
      spoken.textContent = num(d.value) + ' ' + opts.unit(d.value) + ', ' + d.title;
    });

    if (window.ResizeObserver) {
      new ResizeObserver(function () {
        var widthChanged = Math.floor(container.clientWidth) !== lastWidth;
        var heightChanged = opts.fill && Math.floor(container.clientHeight) !== lastHeight;
        if (widthChanged || heightChanged) draw();
      }).observe(container);
    } else {
      window.addEventListener('resize', draw);
    }

    return {
      update: function (next) {
        data = next;
        if (active >= data.length) active = data.length - 1;
        draw();
      },
      redraw: draw
    };
  }

  var arrivalsChart = BarChart($('arrivals-chart'), {
    name: 'Arrivals today, check-ins per 30 minutes',
    fill: true,
    height: function (w) { return Math.round(clamp(w * 0.34, 190, 260)); },
    unit: function (v) { return plural(v, 'check-in', 'check-ins'); },
    // Label whole hours, or every second or third hour when bars are narrow.
    labelStep: function (band, fs) {
      var need = fs * 0.62 * 5 + 12;
      var steps = [2, 4, 6, 8];
      for (var i = 0; i < steps.length; i++) if (band * steps[i] >= need) return steps[i];
      return 12;
    },
    showLabel: function (d, i, step) { return (d.from % (step * 1800)) === 0; },
    label: function (d) { return clockText(d.from); },
    direct: function (d) { return d.peak || d.current; },
    summary: function (data) {
      var peak = data.reduce(function (p, d) { return d.value > p.value ? d : p; }, data[0]);
      return 'Check-ins per 30 minutes from ' + clockText(data[0].from) + '. Busiest: ' + peak.title + ', ' + num(peak.value) + '.';
    }
  });

  var weekChart = BarChart($('week-chart'), {
    name: 'Venue check-ins per day, last 7 days',
    height: function (w) { return Math.round(clamp(w * 0.4, 190, 240)); },
    unit: function (v) { return plural(v, 'check-in', 'check-ins'); },
    showLabel: function () { return true; },
    label: function (d, band, fs) {
      if (d.today) return 'Today';
      var full = DAY_NAMES[d.date.getDay()].slice(0, 3) + ' ' + d.date.getDate();
      return band >= full.length * fs * 0.62 + 8 ? full : DAY_NAMES[d.date.getDay()].slice(0, 3);
    },
    direct: function (d) { return d.today || d.busiest; },
    summary: function (data) {
      return data.map(function (d) { return (d.today ? 'Today' : dayDate(d.date)) + ': ' + num(d.value); }).join(', ') + '.';
    }
  });

  function renderArrivals(t) {
    var currentFrom = Math.floor(t / 1800) * 1800;
    var data = arrivals.map(function (slot) {
      var current = slot.from === currentFrom && t < DOORS_CLOSE;
      return {
        from: slot.from,
        value: slot.count,
        current: current,
        title: clockText(slot.from) + ' to ' + clockText(slot.from + 1800) + (current ? ', so far' : '')
      };
    });
    var peak = data.reduce(function (p, d) { return d.value > p.value ? d : p; }, data[0]);
    peak.peak = true;

    if (changed('arrivals', JSON.stringify(data.map(function (d) { return d.value; })) + currentFrom)) {
      arrivalsChart.update(data);
      $('arrivals-table').replaceChildren.apply($('arrivals-table'), data.map(function (d) {
        return h('tr', null, h('td', null, d.title), h('td', { 'class': 'is-num' }, num(d.value)));
      }));
    }

    var lead = $('arrivals-lead');
    var leadKey = checkedIn + '|' + peak.title + peak.value;
    if (changed('arrivals-lead', leadKey)) {
      lead.replaceChildren(
        h('strong', null, num(checkedIn)),
        ' checked in since the doors opened at ' + clockText(DOORS_OPEN) + '. Busiest half hour: ' +
        peak.title + ', with ' + num(peak.value) + '.');
    }
  }

  function renderWeek() {
    var days = week.map(function (d) {
      return { date: d.date, value: d.count, title: dayDate(d.date) + ' ' + d.date.getFullYear() };
    });
    days.push({ date: eventDate, value: checkedIn, today: true, title: 'Today, ' + dateText(eventDate) + ', so far' });
    var busiest = days.reduce(function (p, d) { return d.value > p.value ? d : p; }, days[0]);
    busiest.busiest = true;

    if (!changed('week', JSON.stringify(days.map(function (d) { return d.value; })))) return;

    weekChart.update(days);
    $('week-table').replaceChildren.apply($('week-table'), days.map(function (d) {
      return h('tr', null, h('td', null, d.today ? 'Today (so far)' : dayDate(d.date)), h('td', { 'class': 'is-num' }, num(d.value)));
    }));

    var total = days.reduce(function (n, d) { return n + d.value; }, 0);
    var before = SAMPLE.previousWeek.count;
    var change = Math.round(((total - before) / before) * 100);
    var up = total >= before;

    if (setText($('week-total'), num(total))) flash($('week-total'));
    var changeNode = $('week-change');
    changeNode.replaceChildren(
      icon(up ? 'trending-up' : 'trending-down'),
      h('span', null, (up ? 'Up from ' : 'Down from ') + num(before) + ' in the 7 days before (' + (up ? '+' : '−') + Math.abs(change) + '%).'));
    setText($('week-busiest'), (busiest.today ? 'Today' : dayDate(busiest.date)) + ' · ' + num(busiest.value));
    setText($('week-average'), num(total / days.length));
    setText(document.querySelector('#week .panel__meta'), 'Venue check-ins per day · ' + days[0].date.getDate() + ' to ' + dateText(eventDate));
  }

  function renderHeader() {
    setText($('updated-at'), clockText(lastUpdate()));
  }

  var renderQueued = false;

  function render() {
    var t = now();
    if (t < DOORS_CLOSE) slotFor(t);
    renderHeader();
    renderAlerts(t);
    renderKpis(t);
    renderAreas(t);
    renderSessions(t);
    renderArrivals(t);
    renderEntries(t);
    renderTeams(t);
    renderActivity();
    renderWeek();
    memo.ready = true;
  }

  function requestRender() {
    if (renderQueued) return;
    renderQueued = true;
    window.requestAnimationFrame(function () {
      renderQueued = false;
      render();
    });
  }

  /* ------------------------------------------------------------------------
     Announcements and toasts
     ------------------------------------------------------------------------ */

  var announcer = h('div', { 'class': 'visually-hidden', 'aria-live': 'polite' });
  document.body.append(announcer);

  function announce(text) {
    if (!memo.ready) return;
    announcer.textContent = text;
  }

  function toast(text) {
    var node = h('div', { 'class': 'toast' }, icon('circle-check'), h('p', null, text));
    $('toasts').append(node);
    window.setTimeout(function () {
      node.classList.add('is-leaving');
      window.setTimeout(function () { node.remove(); }, 260);
    }, 5000);
  }

  /* ------------------------------------------------------------------------
     Send update: room changes, delays and notices for floor teams
     ------------------------------------------------------------------------ */

  var dialog = $('update-dialog');
  var form = $('update-form');
  var field = {
    session: $('u-session'),
    room: $('u-room'),
    minutes: $('u-minutes'),
    message: $('u-message'),
    teams: $('u-teams'),
    preview: $('u-preview'),
    submitLabel: $('u-submit-label'),
    roomError: $('u-room-error'),
    messageError: $('u-message-error'),
    teamsError: $('u-teams-error')
  };
  var opener = null;
  var effect = null;
  var showErrors = false;
  var dirty = {};

  field.room.replaceChildren.apply(field.room, areas.map(function (a) {
    return h('option', { value: a.id }, a.name + ' · holds ' + num(a.capacity));
  }));

  field.teams.replaceChildren.apply(field.teams, teams.map(function (tm) {
    return h('label', { 'class': 'check', 'for': 'u-team-' + tm.id },
      h('input', { type: 'checkbox', name: 'teams', value: tm.id, id: 'u-team-' + tm.id }),
      h('span', null, tm.name),
      h('span', { 'class': 'check__count' }, '(' + tm.onShift + ')'));
  }));

  function kind() {
    return form.elements.kind.value;
  }

  function teamBox(id) {
    return $('u-team-' + id);
  }

  function chosenTeams() {
    return teams.filter(function (tm) { return teamBox(tm.id).checked; });
  }

  function staffCount() {
    return chosenTeams().reduce(function (n, tm) { return n + tm.onShift; }, 0);
  }

  function fillSessions() {
    var t = now();
    var open = sessions.filter(function (x) { return phaseOf(x, t) !== 'done'; }).sort(byStart);
    field.session.replaceChildren.apply(field.session, open.map(function (x) {
      return h('option', { value: x.id }, clockText(x.startSec) + ' · ' + x.title + ' (' + areaById[x.area].name + ')');
    }));
  }

  function suggestRoom() {
    var x = sessionById[field.session.value];
    if (!x) return;
    var room = roomFor(x) || areas.filter(function (a) { return a.id !== x.area; })[0];
    if (room) field.room.value = room.id;
  }

  function messageText() {
    var x = sessionById[field.session.value];
    if (kind() === 'room') return x ? x.title + ' moved to ' + areaById[field.room.value].name + '.' : '';
    if (kind() === 'late') {
      var minutes = Number(field.minutes.value);
      return x ? x.title + ' is running ' + minutes + ' min late. New start ' + clockText(x.startSec + minutes * 60) + '.' : '';
    }
    return field.message.value.trim();
  }

  function showError(control, node, message) {
    node.hidden = !message;
    node.replaceChildren.apply(node, message ? [icon('octagon-alert'), h('span', null, message)] : []);
    if (!control) return;
    if (message) control.setAttribute('aria-invalid', 'true');
    else control.removeAttribute('aria-invalid');
  }

  // Errors show once a field has been changed and left, or on send.
  function validate(focusFirst) {
    var first = null;
    var x = sessionById[field.session.value];

    var roomMessage = '';
    if (kind() === 'room' && x && field.room.value === x.area) {
      roomMessage = x.title + ' is already in ' + areaById[x.area].name + '. Choose another room.';
    }
    var showRoom = showErrors || dirty.room;
    showError(field.room, field.roomError, showRoom ? roomMessage : '');
    if (roomMessage && !first) first = field.room;

    var noticeMessage = '';
    if (kind() === 'notice' && !field.message.value.trim()) noticeMessage = 'Write the update floor teams should see.';
    var showNotice = showErrors || dirty.message;
    showError(field.message, field.messageError, showNotice ? noticeMessage : '');
    if (noticeMessage && !first) first = field.message;

    var teamsMessage = chosenTeams().length ? '' : 'Choose at least one team to send this to.';
    showError(null, field.teamsError, showErrors || dirty.teams ? teamsMessage : '');
    if (teamsMessage && !first) first = teamBox(teams[0].id);

    if (focusFirst && first) first.focus();
    return !first;
  }

  function refreshForm() {
    var k = kind();
    form.querySelectorAll('[data-for]').forEach(function (node) {
      node.hidden = node.getAttribute('data-for').split(' ').indexOf(k) === -1;
    });
    var text = messageText();
    field.preview.replaceChildren(
      icon(k === 'room' ? 'arrow-right-left' : k === 'late' ? 'clock' : 'megaphone'),
      h('span', null, text || 'Your message shows here.'));
    var staff = staffCount();
    field.submitLabel.textContent = staff ? 'Send to ' + staff + ' staff' : 'Send update';
    validate(false);
  }

  function openUpdate(preset, from) {
    preset = preset || {};
    opener = from || document.activeElement;
    effect = preset.effect || null;
    showErrors = false;
    dirty = {};

    fillSessions();
    form.elements.kind.value = preset.kind || 'room';

    var t = now();
    var flagged = sessions.filter(function (x) {
      return phaseOf(x, t) !== 'done' && x.booked > areaById[x.area].capacity;
    })[0];
    var sessionId = preset.session || (flagged && flagged.id) || field.session.value;
    if (sessionById[sessionId] && field.session.querySelector('option[value="' + sessionId + '"]')) {
      field.session.value = sessionId;
    }
    if (preset.room) field.room.value = preset.room;
    else suggestRoom();
    field.minutes.value = '10';
    field.message.value = preset.message || '';
    var defaults = preset.teams || ['front-desk', 'ushers'];
    teams.forEach(function (tm) { teamBox(tm.id).checked = defaults.indexOf(tm.id) !== -1; });

    refreshForm();
    dialog.showModal();
    (kind() === 'notice' ? field.message : field.session).focus();
  }

  function applyUpdate() {
    var t = now();
    var staff = staffCount();
    var sentTo = 'Sent to ' + staff + ' staff';
    var text = messageText();
    var x = sessionById[field.session.value];

    if (kind() === 'room') {
      var from = x.area;
      x.movedFrom = from;
      x.area = field.room.value;
      x.movedAt = t;
      updated.sessions = t;
      transfers.push({ from: from, to: x.area, left: Math.min(areaById[from].count, x.booked) });
      log({ at: t, kind: 'flow', icon: 'arrow-right-left', text: text, meta: sentTo });
      toast('Room change sent to ' + staff + ' staff.');
    } else if (kind() === 'late') {
      x.delay = Number(field.minutes.value);
      updated.sessions = t;
      log({ at: t, kind: 'attention', icon: 'clock', text: text, meta: sentTo });
      toast('Delay sent to ' + staff + ' staff.');
    } else {
      if (effect) {
        redirects[effect.from] = effect.to;
        updated.areas = t;
      }
      log({ at: t, kind: 'flow', icon: 'megaphone', text: text, meta: sentTo });
      toast('Update sent to ' + staff + ' staff.');
    }
    requestRender();
  }

  form.addEventListener('change', function (ev) {
    var target = ev.target;
    if (target === field.session && kind() === 'room' && !dirty.room) suggestRoom();
    if (target === field.room) dirty.room = true;
    if (target.name === 'teams') dirty.teams = true;
    refreshForm();
  });

  form.addEventListener('input', function (ev) {
    if (ev.target === field.message) refreshForm();
  });

  field.message.addEventListener('blur', function () {
    if (field.message.value !== '' || dirty.message) {
      dirty.message = true;
      validate(false);
    }
  });

  form.addEventListener('submit', function (ev) {
    ev.preventDefault();
    showErrors = true;
    if (!validate(true)) return;
    applyUpdate();
    dialog.close();
  });

  form.querySelectorAll('[data-close]').forEach(function (button) {
    button.addEventListener('click', function () { dialog.close(); });
  });

  dialog.addEventListener('click', function (ev) {
    if (ev.target === dialog) dialog.close();
  });

  dialog.addEventListener('close', function () {
    var target = opener && opener.isConnected && !opener.hidden ? opener : $('open-update');
    target.focus();
  });

  $('open-update').addEventListener('click', function (ev) {
    openUpdate(null, ev.currentTarget);
  });

  $('alerts').addEventListener('click', function (ev) {
    var button = ev.target.closest('button');
    if (!button) return;
    var id = button.getAttribute('data-action') || button.getAttribute('data-dismiss');
    var alert = currentAlerts.filter(function (a) { return a.id === id; })[0];
    if (!alert) return;
    if (button.hasAttribute('data-action') && alert.action) {
      openUpdate(alert.action.preset, button);
      return;
    }
    // Dismiss, then move focus to the next alert or the page's main action.
    var node = button.closest('.alert');
    var next = node.nextElementSibling || node.previousElementSibling;
    dismissed[id] = true;
    render();
    var focusTarget = next && next.isConnected ? next.querySelector('[data-dismiss]') : $('open-update');
    focusTarget.focus();
  });

  /* ------------------------------------------------------------------------
     Copy report (leadership): the last 7 days as rows a spreadsheet takes
     ------------------------------------------------------------------------ */

  $('copy-report').addEventListener('click', function () {
    var t = now();
    var lines = ['Venue check-ins\t' + dateText(week[0].date) + ' to ' + dateText(eventDate) + ' (sample data)', 'Day\tCheck-ins'];
    week.forEach(function (d) { lines.push(dayDate(d.date) + ' ' + d.date.getFullYear() + '\t' + d.count); });
    lines.push('Today, so far at ' + clockText(t) + '\t' + checkedIn);
    var total = week.reduce(function (n, d) { return n + d.count; }, checkedIn);
    lines.push('Total\t' + total);
    lines.push('Recorded automatically at check-in.');
    var text = lines.join('\n');

    var done = function () { toast('Report copied. Paste it into an email or a spreadsheet.'); };
    var fail = function () {
      var details = document.querySelector('#week .table-toggle');
      details.open = true;
      toast('Copying isn’t allowed here. The table is open below the chart, so you can copy it from there.');
    };
    try {
      navigator.clipboard.writeText(text).then(done, fail);
    } catch (e) {
      fail();
    }
  });

  /* ------------------------------------------------------------------------
     Floor mode and Wall screen
     ------------------------------------------------------------------------ */

  var modeButton = $('mode-toggle');
  var wallButton = $('wall-toggle');
  var darkQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function floorShowing() {
    if (root.hasAttribute('data-wall')) return true;
    var mode = root.getAttribute('data-mode');
    if (mode) return mode === 'floor';
    var theme = root.getAttribute('data-theme');
    if (theme) return theme === 'dark';
    return !!(darkQuery && darkQuery.matches);
  }

  function syncModeButton() {
    modeButton.setAttribute('aria-pressed', String(floorShowing()));
  }

  modeButton.addEventListener('click', function () {
    var next = floorShowing() ? 'light' : 'floor';
    root.setAttribute('data-mode', next);
    try { window.localStorage.setItem('baseflow-mode', next); } catch (e) { /* not saved */ }
    syncModeButton();
  });

  if (darkQuery) {
    if (darkQuery.addEventListener) darkQuery.addEventListener('change', syncModeButton);
    else if (darkQuery.addListener) darkQuery.addListener(syncModeButton);
  }

  if (window.MutationObserver) {
    new MutationObserver(syncModeButton).observe(root, { attributes: true, attributeFilter: ['data-theme'] });
  }

  function setWall(on) {
    if (on) root.setAttribute('data-wall', '');
    else root.removeAttribute('data-wall');
    wallButton.setAttribute('aria-pressed', String(on));
    wallButton.querySelector('.btn-label').textContent = on ? 'Exit wall screen' : 'Wall screen';
    syncModeButton();
    arrivalsChart.redraw();
    weekChart.redraw();

    try {
      if (on && document.fullscreenEnabled && !document.fullscreenElement) {
        var request = root.requestFullscreen();
        if (request && request.catch) request.catch(function () { /* stays in the window */ });
      } else if (!on && document.fullscreenElement) {
        var exit = document.exitFullscreen();
        if (exit && exit.catch) exit.catch(function () {});
      }
    } catch (e) { /* full screen isn't available here */ }
  }

  wallButton.addEventListener('click', function () {
    setWall(!root.hasAttribute('data-wall'));
  });

  document.addEventListener('fullscreenchange', function () {
    if (!document.fullscreenElement && root.hasAttribute('data-wall')) setWall(false);
  });

  document.addEventListener('keydown', function (ev) {
    if (ev.key === 'Escape' && root.hasAttribute('data-wall') && !dialog.open) setWall(false);
  });

  /* ------------------------------------------------------------------------
     Start
     ------------------------------------------------------------------------ */

  $('event-venue').textContent = event.venue + ' · ' + event.day;
  $('event-title').textContent = event.name;
  $('wall-event').textContent = event.name + ' · ' + event.day;
  $('wall-note').textContent = event.venue + ' · Sample event, figures are illustrative';
  $('event-date').textContent = DAY_NAMES[eventDate.getDay()] + ' ' + dateText(eventDate);
  $('event-places').textContent = areas.length + ' areas · ' + entries.length + ' entry points';

  buildAreas();
  syncModeButton();
  render();
  simulate();

  // Time moves on even when nobody checks in: sessions start, half hours roll over.
  var lastMinute = Math.floor(now() / 60);
  window.setInterval(function () {
    var minute = Math.floor(now() / 60);
    if (minute !== lastMinute) {
      lastMinute = minute;
      requestRender();
    }
  }, 1000);
})();
