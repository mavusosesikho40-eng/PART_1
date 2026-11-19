/* Founder Command Centre: draws each view from window.BASEFLOW_SAMPLE.

   One central workspace for visibility, accountability and business health
   (Founder playbook, section 05). It answers three questions first: what
   needs my attention, what am I responsible for, and what is happening in
   Baseflow.

   Views are routed on the address's #name: overview, projects, tasks,
   clients, finance, decisions, knowledge and more (phones), plus
   #project-<id> and #task-<id>. Changes live in memory for this visit.
   Text from records is always set as text, never as HTML. */

(function () {
  'use strict';

  var S = window.BASEFLOW_SAMPLE;
  if (!S) return;

  var root = document.documentElement;
  var $ = function (id) { return document.getElementById(id); };

  /* ------------------------------------------------------------------------
     Dates and times, in the brand's mechanics: Fri 20 Nov · 19 Nov 2026 · 09:12
     ------------------------------------------------------------------------ */

  var DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  function parseDate(iso) {
    var p = iso.split('-').map(Number);
    return new Date(p[0], p[1] - 1, p[2]);
  }

  function isoDate(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function pad(n) {
    return String(n).padStart(2, '0');
  }

  function addDays(d, n) {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  }

  function dayDiff(from, to) {
    return Math.round((to - from) / 86400000);
  }

  var TODAY = parseDate(S.today);

  function dayMonth(d) {
    return d.getDate() + ' ' + MONTHS[d.getMonth()];
  }

  function shortDate(d) {
    return DAYS[d.getDay()].slice(0, 3) + ' ' + dayMonth(d);
  }

  function longDate(d) {
    return DAYS[d.getDay()] + ' ' + dayMonth(d) + ' ' + d.getFullYear();
  }

  function dueText(iso) {
    var diff = dayDiff(TODAY, parseDate(iso));
    if (diff === 0) return 'Today';
    if (diff === 1) return 'Tomorrow';
    if (diff === -1) return 'Yesterday';
    return shortDate(parseDate(iso));
  }

  // Mid-sentence: "due tomorrow", not "due Tomorrow".
  function dueInline(iso) {
    var text = dueText(iso);
    return /^(Today|Tomorrow|Yesterday)$/.test(text) ? text.toLowerCase() : text;
  }

  function weekOf(d) {
    var monday = addDays(d, -((d.getDay() + 6) % 7));
    return [monday, addDays(monday, 6)];
  }

  function inThisWeek(iso) {
    var d = parseDate(iso);
    var week = weekOf(TODAY);
    return d >= week[0] && d <= week[1];
  }

  // The sample's clock starts at S.now and runs in real time.
  var START = Number(S.now.split(':')[0]) * 3600 + Number(S.now.split(':')[1]) * 60;
  var openedAt = performance.now();
  var updatedAt = START;

  function nowSec() {
    return START + Math.floor((performance.now() - openedAt) / 1000);
  }

  function clockText(sec) {
    return pad(Math.floor(sec / 3600) % 24) + ':' + pad(Math.floor(sec / 60) % 60);
  }

  function greeting() {
    var hour = Math.floor(nowSec() / 3600);
    return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  }

  function stamp() {
    updatedAt = nowSec();
  }

  function plural(n, one, many) {
    return n + ' ' + (n === 1 ? one : many);
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
        else if (key === 'checked') node.checked = !!value;
        else if (key === 'value') node.value = value;
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

  function icon(name, extraClass) {
    var svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('class', 'icon' + (extraClass ? ' ' + extraClass : ''));
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    var use = document.createElementNS(SVG_NS, 'use');
    use.setAttribute('href', '#i-' + name);
    svg.append(use);
    return svg;
  }

  // "Discover → Design → Develop", with the arrow drawn as an icon and read as "to".
  function arrowed(parts) {
    var out = [];
    parts.forEach(function (part, i) {
      if (i) out.push(icon('arrow-right', 'arrow'), h('span', { 'class': 'visually-hidden' }, ' to '));
      out.push(part);
    });
    return out;
  }

  function gateText(gate) {
    return arrowed(gate.split(' → '));
  }

  function byId(list) {
    var map = {};
    list.forEach(function (item) { map[item.id] = item; });
    return map;
  }

  /* ------------------------------------------------------------------------
     State, copied from the sample records
     ------------------------------------------------------------------------ */

  var clone = function (value) { return JSON.parse(JSON.stringify(value)); };

  var founders = S.founders;
  var founderById = byId(founders);
  var me = S.me;
  try {
    var savedViewer = window.localStorage.getItem('baseflow-viewer');
    if (founderById[savedViewer]) me = savedViewer;
  } catch (e) { /* storage blocked: use the sample's viewer */ }

  var STAGE_ORDER = ['discover', 'design', 'develop'];
  var projects = clone(S.projects);
  projects.forEach(function (p) {
    p.checks = {};
    p.checks[p.stage] = new Set(p.done);
  });

  var tasks = clone(S.tasks).map(function (t) { return Object.assign({ updates: [] }, t); });
  var clients = clone(S.clients);
  var decisions = clone(S.decisions);
  var knowledge = clone(S.knowledge);

  var projectById = byId(projects);
  var taskById = byId(tasks);
  var clientById = byId(clients);
  var decisionById = byId(decisions);
  var docById = byId(knowledge);
  var taskSeq = 0;

  var STATUS = {
    'not-started': ['neutral', 'Not started'],
    progress: ['progress', 'In progress'],
    blocked: ['attention', 'Blocked'],
    done: ['ok', 'Done']
  };
  var PRIORITY = { high: 'High', medium: 'Medium', low: 'Low' };
  var PRIORITY_RANK = { high: 0, medium: 1, low: 2 };

  function other(id) {
    return founders.filter(function (f) { return f.id !== id; })[0];
  }

  function openTasks() {
    return tasks.filter(function (t) { return t.status !== 'done'; });
  }

  function activeProjects() {
    return projects.filter(function (p) { return p.stage !== 'discover'; });
  }

  function openOpportunities() {
    return clients.filter(function (c) { return !c.internal && c.deal === 'Open'; });
  }

  function pendingDecisions() {
    return decisions.filter(function (d) { return d.status === 'open'; });
  }

  function byDue(a, b) {
    return parseDate(a.due) - parseDate(b.due) || PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority] || a.title.localeCompare(b.title);
  }

  function byStatusThenDue(a, b) {
    var doneA = a.status === 'done' ? 1 : 0;
    var doneB = b.status === 'done' ? 1 : 0;
    if (doneA !== doneB) return doneA - doneB;
    return doneA ? parseDate(b.due) - parseDate(a.due) : byDue(a, b);
  }

  function checklistProgress(p) {
    var stage = S.stages[p.stage];
    var total = stage.groups.reduce(function (n, g) { return n + g.items.length; }, 0);
    return { done: (p.checks[p.stage] || new Set()).size, total: total };
  }

  function milestones() {
    var list = [];
    projects.forEach(function (p) {
      (p.milestones || []).forEach(function (m) {
        list.push({ title: m.title, date: m.date, project: p.id });
      });
    });
    return list
      .filter(function (m) { return dayDiff(TODAY, parseDate(m.date)) >= 0; })
      .sort(function (a, b) { return parseDate(a.date) - parseDate(b.date); });
  }

  /* ------------------------------------------------------------------------
     Small pieces used across views
     ------------------------------------------------------------------------ */

  function statusBadge(status, compact) {
    var s = STATUS[status];
    return h('span', { 'class': 'bf-status bf-status--' + s[0] + (compact ? ' bf-status--compact' : '') }, s[1]);
  }

  function badge(kind, text, compact) {
    return h('span', { 'class': 'bf-status bf-status--' + kind + (compact ? ' bf-status--compact' : '') }, text);
  }

  function avatar(founder, small) {
    return h('span', {
      'class': 'avatar' + (founder.id === 'sm' ? ' avatar--sm' : '') + (small ? ' avatar--small' : ''),
      role: 'img',
      'aria-label': founder.name,
      title: founder.name
    }, founder.initials);
  }

  function person(founder) {
    return h('span', { 'class': 'person' }, avatar(founder, true), founder.name);
  }

  function dueLabel(t) {
    var cls = 'row__date';
    if (t.status !== 'done') {
      var diff = dayDiff(TODAY, parseDate(t.due));
      if (diff < 0) return h('span', { 'class': cls + ' due--overdue' }, icon('triangle-alert', 'arrow'), 'Overdue · ' + shortDate(parseDate(t.due)));
      if (diff === 0) return h('span', { 'class': cls + ' due--today' }, 'Today');
    }
    return h('span', { 'class': cls }, dueText(t.due));
  }

  function emptyState(title, text, action) {
    return h('div', { 'class': 'empty' }, h('p', { 'class': 'empty__title' }, title), h('p', null, text), action || null);
  }

  function card(cls, titleId, title, headExtra, body) {
    return h('section', { 'class': 'card ' + (cls || ''), 'aria-labelledby': titleId },
      h('div', { 'class': 'card__head' }, h('h2', { 'class': 'card__title', id: titleId }, title), headExtra || null),
      body);
  }

  function pageHead(o) {
    return h('div', { 'class': 'pagehead' },
      h('div', { 'class': 'pagehead__text' },
        o.back ? h('a', { 'class': 'link back', href: o.back.href }, icon('chevron-left', 'arrow'), o.back.label) : null,
        o.eyebrow ? h('p', { 'class': 'eyebrow' }, o.eyebrow) : null,
        h('h1', { id: 'page-title' }, o.title),
        o.sub ? h('p', { 'class': 'pagehead__sub' }, o.sub) : null),
      h('p', { 'class': 'pagehead__fresh' }, 'Sample data · Updated ', h('time', null, clockText(updatedAt))));
  }

  function taskRow(t) {
    return h('a', { 'class': 'row', href: '#task-' + t.id, 'data-find': 'task-' + t.id },
      h('span', { 'class': 'row__main' },
        h('span', { 'class': 'row__title' }, t.title),
        h('span', { 'class': 'row__meta' }, projectById[t.project].name)),
      h('span', { 'class': 'row__side' }, dueLabel(t), statusBadge(t.status, true)));
  }

  /* ------------------------------------------------------------------------
     A · Business overview
     ------------------------------------------------------------------------ */

  function kpiTile(label, value, caption, href) {
    return h('a', { 'class': 'bf-tile', href: href },
      h('p', { 'class': 'bf-tile__label' }, label),
      h('p', { 'class': 'bf-tile__value' }, value),
      h('p', { 'class': 'bf-tile__caption' }, caption));
  }

  function kpis() {
    var open = openTasks();
    var dueThisWeek = open.filter(function (t) { return inThisWeek(t.due); }).length;
    var discover = projects.filter(function (p) { return p.stage === 'discover'; }).length;
    var opportunities = openOpportunities();
    var nextFollowUp = opportunities
      .map(function (c) { return c.followUp; })
      .filter(Boolean)
      .sort()[0];
    var pending = pendingDecisions();

    return h('section', { 'class': 'kpis', 'aria-labelledby': 'kpis-title' },
      h('h2', { 'class': 'visually-hidden', id: 'kpis-title' }, 'Business health'),
      kpiTile('Active projects', String(activeProjects().length), discover ? discover + ' more in Discover' : 'None in Discover', '#projects'),
      kpiTile('Open opportunities', String(opportunities.length), nextFollowUp ? 'Next follow-up ' + dueInline(nextFollowUp) : 'No follow-ups booked', '#clients'),
      kpiTile('Outstanding tasks', String(open.length), dueThisWeek + ' due this week', '#tasks'),
      kpiTile('Pending decisions', String(pending.length), pending.length ? 'Need both founders' : 'Nothing waiting', '#decisions'),
      h('a', { 'class': 'bf-tile bf-tile--empty', href: '#finance' },
        h('p', { 'class': 'bf-tile__label' }, 'Financial snapshot'),
        h('p', { 'class': 'bf-tile__empty-title' }, 'No figures yet'),
        h('p', { 'class': 'bf-tile__caption' }, 'Link the finance sheet to show real numbers.')));
  }

  function stageColumn(key, large, richCards) {
    var stage = S.stages[key];
    var list = projects.filter(function (p) { return p.stage === key; });
    return h('div', { 'class': 'stage' },
      h('h3', { 'class': 'stage__head' },
        h('span', { 'class': 'stage__mark stage__mark--' + key, 'aria-hidden': 'true' }),
        h('span', { 'class': 'eyebrow' }, stage.name),
        h('span', { 'class': 'stage__count' }, String(list.length), h('span', { 'class': 'visually-hidden' }, list.length === 1 ? ' project' : ' projects'))),
      large ? h('p', { 'class': 'stage__desc' }, stage.covers) : null,
      list.length
        ? h('ul', { 'class': 'stage__list' }, list.map(function (p) { return h('li', null, projectCard(p, large || richCards)); }))
        : h('p', { 'class': 'stage__empty' }, 'Nothing in ' + stage.name + ' right now.'));
  }

  function projectCard(p, large) {
    var lead = founderById[p.lead];
    var progress = checklistProgress(p);
    var next = (p.milestones || []).filter(function (m) { return dayDiff(TODAY, parseDate(m.date)) >= 0; })[0];
    var openCount = tasks.filter(function (t) { return t.project === p.id && t.status !== 'done'; }).length;
    return h('a', { 'class': 'project-card' + (large ? ' project-card--large' : ''), href: '#project-' + p.id, 'data-find': 'project-' + p.id },
      h('span', { 'class': 'project-card__name' }, p.name),
      h('span', { 'class': 'project-card__meta' }, large ? clientById[p.client].name + ' · ' + p.status : p.status),
      large ? h('span', { 'class': 'progress' },
        h('span', { 'class': 'bf-tile__bar', 'aria-hidden': 'true' }, h('span', { style: { width: Math.round(progress.done / progress.total * 100) + '%' } })),
        h('span', null, progress.done + ' of ' + progress.total + ' checks')) : null,
      large && next ? h('span', { 'class': 'project-card__meta' }, 'Next: ' + next.title + ' · ' + dueText(next.date)) : null,
      h('span', { 'class': 'project-card__foot' },
        p.illustrative ? badge('attention', 'Illustrative', true) : (large ? h('span', { 'class': 'project-card__meta' }, plural(openCount, 'open task', 'open tasks')) : h('span')),
        avatar(lead, true)));
  }

  function pipelineCard(cls, large, richCards) {
    return h('section', { 'class': 'card pipeline ' + cls, 'aria-labelledby': 'pipeline-title' },
      h('div', { 'class': 'card__head' },
        h('h2', { 'class': 'card__title', id: 'pipeline-title' }, large ? 'Pipeline by stage' : 'Project pipeline'),
        h('p', { 'class': 'card__meta' }, arrowed(['Discover', 'Design', 'Develop']))),
      h('div', { 'class': 'stages' }, STAGE_ORDER.map(function (key) { return stageColumn(key, large, richCards); })));
  }

  function myTasksCard(cls) {
    var mine = tasks.filter(function (t) { return t.owner === me && t.status !== 'done'; }).sort(byDue);
    return card(cls, 'mytasks-title', 'My tasks',
      h('a', { 'class': 'link', href: '#tasks', 'data-scope': 'mine' }, 'View all'),
      mine.length
        ? h('ul', { 'class': 'rows' }, mine.slice(0, 4).map(function (t) { return h('li', null, taskRow(t)); }))
        : emptyState('Nothing on your list', 'Tasks you own will appear here.'));
  }

  function approvalLine(d) {
    return h('p', { 'class': 'approvals' }, founders.map(function (f) {
      var yes = d.approvals.indexOf(f.id) !== -1;
      return h('span', { 'class': 'approval ' + (yes ? 'approval--yes' : 'approval--no') },
        icon(yes ? 'circle-check' : 'clock'),
        f.first + (yes ? ' approved' : ' to approve'));
    }));
  }

  function decisionBlock(d) {
    var mine = d.approvals.indexOf(me) !== -1;
    return h('div', { 'class': 'decision-row', 'data-find': 'decision-' + d.id },
      h('p', { 'class': 'row__title' }, d.title),
      h('p', { 'class': 'row__meta' }, d.context),
      approvalLine(d),
      mine
        ? h('p', { 'class': 'row__meta' }, 'Waiting on ' + other(me).first + '.')
        : h('button', { 'class': 'bf-btn bf-btn--secondary bf-btn--compact', type: 'button', 'data-approve': d.id, 'data-key': 'approve-' + d.id }, icon('check'), 'Approve'));
  }

  function approvalSummary(d) {
    var mine = d.approvals.indexOf(me) !== -1;
    var others = d.approvals.filter(function (id) { return id !== me; }).map(function (id) { return founderById[id].first; });
    if (mine) return [icon('circle-check', 'arrow'), 'You approved · waiting on ' + other(me).first];
    if (others.length) return [icon('circle-check', 'arrow'), others.join(' and ') + ' approved · waiting on you'];
    return [icon('clock', 'arrow'), 'Waiting on both of you'];
  }

  function decisionCompact(d) {
    var mine = d.approvals.indexOf(me) !== -1;
    return h('div', { 'class': 'row', 'data-find': 'decision-' + d.id },
      h('span', { 'class': 'row__main' },
        h('span', { 'class': 'row__title' }, d.title),
        h('span', { 'class': 'row__meta' }, d.context),
        h('span', { 'class': 'row__meta row__meta--status' }, approvalSummary(d))),
      mine ? null : h('span', { 'class': 'row__side' },
        h('button', { 'class': 'bf-btn bf-btn--secondary bf-btn--compact', type: 'button', 'data-approve': d.id, 'data-key': 'approve-' + d.id, 'aria-label': 'Approve: ' + d.title }, icon('check'), 'Approve')));
  }

  function bothFoundersCard(cls) {
    var pending = pendingDecisions();
    return h('section', { 'class': 'card card--soft ' + cls, 'aria-labelledby': 'both-title' },
      h('div', { 'class': 'card__head' },
        h('h2', { 'class': 'eyebrow', id: 'both-title' }, 'Needs both founders'),
        h('a', { 'class': 'link', href: '#decisions' }, 'Decisions')),
      pending.length
        ? h('ul', { 'class': 'rows' }, pending.map(function (d) { return h('li', null, decisionCompact(d)); }))
        : h('p', null, 'Nothing is waiting on both of you.'));
  }

  function deadlinesCard(cls) {
    var list = milestones().slice(0, 5);
    return card(cls, 'deadlines-title', 'Upcoming deadlines', null,
      list.length
        ? h('ul', { 'class': 'rows' }, list.map(function (m) {
          return h('li', null, h('a', { 'class': 'row', href: '#project-' + m.project },
            icon('calendar', 'row__icon'),
            h('span', { 'class': 'row__main' },
              h('span', { 'class': 'row__title' }, m.title),
              h('span', { 'class': 'row__meta' }, projectById[m.project].name)),
            h('span', { 'class': 'row__date' }, dueText(m.date))));
        }))
        : emptyState('No deadlines ahead', 'Milestones you add to projects will appear here.'));
  }

  function decisionStatus(d) {
    return d.status === 'decided' ? badge('ok', 'Decided', true) : badge('attention', 'Open', true);
  }

  function decisionMeta(d) {
    return d.status === 'decided'
      ? d.by + ' · ' + dayMonth(parseDate(d.decidedOn))
      : 'Both founders · due ' + dueInline(d.due);
  }

  function sortedDecisions() {
    return decisions.slice().sort(function (a, b) {
      if (a.status !== b.status) return a.status === 'open' ? -1 : 1;
      if (a.status === 'open') return parseDate(a.due) - parseDate(b.due);
      return parseDate(b.decidedOn) - parseDate(a.decidedOn);
    });
  }

  function decisionLogCard(cls) {
    var list = sortedDecisions().slice(0, 4);
    return card(cls, 'log-title', 'Decision log', h('a', { 'class': 'link', href: '#decisions' }, 'View all'),
      h('ul', { 'class': 'rows' }, list.map(function (d) {
        return h('li', null, h('div', { 'class': 'row' },
          h('span', { 'class': 'row__main' },
            h('span', { 'class': 'row__title' }, d.title),
            h('span', { 'class': 'row__meta' }, decisionMeta(d))),
          h('span', { 'class': 'row__side' }, decisionStatus(d))));
      })));
  }

  function viewOverview() {
    return {
      title: 'Overview',
      sticky: 'new-task',
      nodes: [
        pageHead({ eyebrow: 'Founder Command Centre', title: greeting() + ', ' + founderById[me].first }),
        kpis(),
        h('div', { 'class': 'grid' },
          pipelineCard('ov-pipeline', false, true),
          myTasksCard('ov-mytasks'),
          bothFoundersCard('ov-both'),
          deadlinesCard('ov-deadlines'),
          decisionLogCard('ov-decisions'))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     B · Project pipeline and workspaces
     ------------------------------------------------------------------------ */

  function viewProjects() {
    var discover = projects.filter(function (p) { return p.stage === 'discover'; }).length;
    return {
      title: 'Projects',
      sticky: 'new-task',
      nodes: [
        pageHead({
          eyebrow: 'Founder Command Centre',
          title: 'Projects',
          sub: plural(activeProjects().length, 'active project', 'active projects') + ' and ' + discover + ' in Discover. No building without agreement.'
        }),
        pipelineCard('', true)
      ]
    };
  }

  function nextStage(stage) {
    var i = STAGE_ORDER.indexOf(stage);
    return i >= 0 && i < STAGE_ORDER.length - 1 ? STAGE_ORDER[i + 1] : null;
  }

  function stageCard(p) {
    var stage = S.stages[p.stage];
    var checks = p.checks[p.stage] || (p.checks[p.stage] = new Set());
    var progress = checklistProgress(p);
    var d = p.decision ? decisionById[p.decision] : null;
    var decisionLink = d && d.status === 'open' && S.stages[p.stage].gate.indexOf(stage.name) === 0;

    return card('', 'stage-title', 'Stage and checklist', h('p', { 'class': 'card__meta' }, progress.done + ' of ' + progress.total + ' checks'),
      h('div', { 'class': 'stack' },
        h('div', { 'class': 'gate' },
          h('p', { 'class': 'eyebrow' }, 'Approval gate'),
          h('p', null, h('strong', null, gateText(stage.gate))),
          h('p', { 'class': 'gate__question' }, stage.question),
          h('p', { 'class': 'caption' }, 'Deliverable: ' + stage.deliverable + '. Both founders agree before the stage changes.'),
          decisionLink ? h('p', null, h('a', { 'class': 'link', href: '#decisions' }, d.title + ' · ', d.approvals.length + ' of 2 approved')) : null),
        h('div', { 'class': 'checklist' }, stage.groups.map(function (g, gi) {
          return h('div', { 'class': 'checklist__group' },
            h('h3', null, g.name),
            h('ul', null, g.items.map(function (item, ii) {
              var key = gi + ':' + ii;
              var done = checks.has(key);
              var id = 'chk-' + p.id + '-' + p.stage + '-' + gi + '-' + ii;
              return h('li', null, h('label', { 'class': 'check' + (done ? ' check--done' : ''), 'for': id },
                h('input', { type: 'checkbox', id: id, 'data-check': p.id + '|' + key, 'data-key': id, checked: done }),
                h('span', null, item)));
            })));
        }))));
  }

  function viewProject(id) {
    var p = projectById[id];
    var stage = S.stages[p.stage];
    var client = clientById[p.client];
    var projectTasks = tasks.filter(function (t) { return t.project === id; }).sort(byStatusThenDue);
    var owners = founders.map(function (f) {
      return { founder: f, open: projectTasks.filter(function (t) { return t.owner === f.id && t.status !== 'done'; }).length };
    });
    var docs = (p.docs || []).map(function (docId) { return docById[docId]; }).filter(Boolean);

    var about = card('', 'about-title', 'Client and project', p.illustrative ? badge('attention', 'Illustrative', true) : null,
      h('dl', { 'class': 'facts' },
        h('div', null, h('dt', null, 'Client'), h('dd', null, client.name)),
        h('div', null, h('dt', null, 'Solution line'), h('dd', null, p.line)),
        h('div', null, h('dt', null, 'What it is'), h('dd', null, p.summary)),
        h('div', null, h('dt', null, 'The problem'), h('dd', null, p.problem))));

    var taskCard = card('', 'ptasks-title', 'Tasks', h('button', { 'class': 'bf-btn bf-btn--secondary bf-btn--compact', type: 'button', 'data-new-task': p.id, 'data-key': 'new-task-' + p.id }, icon('plus'), 'Add task'),
      projectTasks.length
        ? h('ul', { 'class': 'rows' }, projectTasks.map(function (t) {
          return h('li', null, h('a', { 'class': 'row', href: '#task-' + t.id },
            avatar(founderById[t.owner], true),
            h('span', { 'class': 'row__main' },
              h('span', { 'class': 'row__title' }, t.title),
              h('span', { 'class': 'row__meta' }, founderById[t.owner].first + ' · ' + t.outcome)),
            h('span', { 'class': 'row__side' }, dueLabel(t), statusBadge(t.status, true))));
        }))
        : emptyState('No tasks yet', 'Add the first task, with one owner, a deadline and an outcome.'));

    var people = card('', 'people-title', 'Founder and owners', null,
      h('dl', { 'class': 'facts' },
        h('div', null, h('dt', null, 'Lead'), h('dd', null, person(founderById[p.lead]))),
        owners.map(function (o) {
          return h('div', null, h('dt', null, o.founder.first), h('dd', null, plural(o.open, 'open task', 'open tasks')));
        })));

    var dates = card('', 'dates-title', 'Deadlines and milestones', null,
      (p.milestones || []).length
        ? h('ul', { 'class': 'rows' }, p.milestones.map(function (m) {
          var past = dayDiff(TODAY, parseDate(m.date)) < 0;
          return h('li', null, h('div', { 'class': 'row' },
            icon('calendar', 'row__icon'),
            h('span', { 'class': 'row__main' }, h('span', { 'class': 'row__title' }, m.title)),
            h('span', { 'class': 'row__date' + (past ? ' muted' : '') }, dueText(m.date))));
        }))
        : emptyState('No milestones yet', 'Add the dates this project has agreed.'));

    var budget = card('', 'budget-title', 'Budget and deliverables', null,
      h('dl', { 'class': 'facts' },
        h('div', null, h('dt', null, 'Budget'), h('dd', null, p.budget)),
        h('div', null, h('dt', null, 'Deliverables'), h('dd', null,
          h('ul', { 'class': 'bullets' }, p.deliverables.map(function (x) { return h('li', null, x); }))))));

    var files = card('', 'files-title', 'Files and documentation', h('a', { 'class': 'link', href: '#knowledge' }, 'Knowledge'),
      docs.length
        ? h('ul', { 'class': 'rows' }, docs.map(docRow))
        : emptyState('No files linked', 'Briefs, designs and technical documents for this project will appear here.'));

    return {
      title: p.name,
      sticky: 'new-task',
      nodes: [
        pageHead({ back: { href: '#projects', label: 'Projects' }, eyebrow: p.line + ' · ' + stage.name, title: p.name, sub: client.name + ' · ' + p.status }),
        h('div', { 'class': 'grid' },
          h('div', { 'class': 'stack ws-main' }, about, stageCard(p), taskCard),
          h('div', { 'class': 'stack ws-side' }, people, dates, budget, files))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     C · Tasks and founder responsibilities
     ------------------------------------------------------------------------ */

  var filter = { owner: 'any', project: 'any', priority: 'any', due: 'any' };
  var updateDraftOpen = false;

  function filteredTasks() {
    return tasks.filter(function (t) {
      if (filter.owner !== 'any' && t.owner !== filter.owner) return false;
      if (filter.project !== 'any' && t.project !== filter.project) return false;
      if (filter.priority !== 'any' && t.priority !== filter.priority) return false;
      if (filter.due === 'week' && !(inThisWeek(t.due) && t.status !== 'done')) return false;
      if (filter.due === 'overdue' && !(t.status !== 'done' && dayDiff(TODAY, parseDate(t.due)) < 0)) return false;
      if (filter.due === 'later' && !(t.status !== 'done' && !inThisWeek(t.due) && dayDiff(TODAY, parseDate(t.due)) > 0)) return false;
      return true;
    }).sort(byStatusThenDue);
  }

  function selectField(id, label, value, options) {
    return h('div', { 'class': 'field' },
      h('label', { 'class': 'field__label', 'for': id }, label),
      h('div', { 'class': 'select' },
        h('select', { 'class': 'input', id: id, 'data-filter': id.replace('flt-', ''), 'data-key': id },
          options.map(function (o) { return h('option', { value: o[0], selected: o[0] === value }, o[1]); })),
        icon('chevron-down')));
  }

  function filtersRow() {
    var allPressed = filter.owner === 'any' && filter.project === 'any' && filter.priority === 'any' && filter.due === 'any';
    return h('div', { 'class': 'filters', role: 'group', 'aria-label': 'Filter tasks' },
      h('div', { 'class': 'field field--chips' },
        h('span', { 'class': 'field__label', id: 'quick-label' }, 'Show'),
        h('div', { 'class': 'chips', role: 'group', 'aria-labelledby': 'quick-label' },
          h('button', { 'class': 'chip', type: 'button', 'data-quick': 'all', 'data-key': 'quick-all', 'aria-pressed': String(allPressed) }, 'All'),
          h('button', { 'class': 'chip', type: 'button', 'data-quick': 'mine', 'data-key': 'quick-mine', 'aria-pressed': String(filter.owner === me) }, 'Mine'),
          h('button', { 'class': 'chip', type: 'button', 'data-quick': 'week', 'data-key': 'quick-week', 'aria-pressed': String(filter.due === 'week') }, 'This week'))),
      selectField('flt-owner', 'Founder', filter.owner, [['any', 'Both founders']].concat(founders.map(function (f) { return [f.id, f.first]; }))),
      selectField('flt-project', 'Project', filter.project, [['any', 'All projects']].concat(projects.map(function (p) { return [p.id, p.name]; }))),
      selectField('flt-priority', 'Priority', filter.priority, [['any', 'Any priority'], ['high', 'High'], ['medium', 'Medium'], ['low', 'Low']]),
      selectField('flt-due', 'Due', filter.due, [['any', 'Any time'], ['overdue', 'Overdue'], ['week', 'This week'], ['later', 'Later']]));
  }

  function prioritiesSection() {
    var list = S.priorities.map(function (id) { return taskById[id]; }).filter(Boolean);
    var week = weekOf(TODAY);
    return h('section', { 'class': 'priorities-section stack', 'aria-labelledby': 'prio-title' },
      h('div', { 'class': 'card__head' },
        h('h2', { 'class': 'card__title', id: 'prio-title' }, 'This week’s priorities'),
        h('p', { 'class': 'card__meta' }, 'Set on Monday ' + dayMonth(week[0]) + ' · one owner each')),
      h('ol', { 'class': 'priorities' }, list.map(function (t, i) {
        return h('li', null, h('a', { 'class': 'priority', href: '#task-' + t.id },
          h('span', { 'class': 'priority__rank' }, 'Priority ' + (i + 1)),
          h('span', { 'class': 'priority__title' }, t.title),
          h('span', { 'class': 'project-card__foot' }, person(founderById[t.owner]), statusBadge(t.status, true)),
          h('span', { 'class': 'project-card__meta' }, 'Due ' + dueInline(t.due))));
      })));
  }

  function taskListItem(t, selectedId) {
    return h('li', null, h('a', {
      'class': 'task-item',
      href: '#task-' + t.id,
      'aria-current': t.id === selectedId ? 'true' : null,
      'data-find': 'task-' + t.id
    },
      h('span', { 'class': 'task-item__title' }, t.title),
      avatar(founderById[t.owner], true),
      h('span', { 'class': 'task-item__project' }, projectById[t.project].name),
      statusBadge(t.status, true),
      dueLabel(t)));
  }

  function taskDetail(t) {
    var p = projectById[t.project];
    var stage = S.stages[p.stage];
    var due = parseDate(t.due);
    var diff = dayDiff(TODAY, due);
    var dueBadge = t.status === 'done'
      ? badge('neutral', 'Due ' + shortDate(due))
      : diff < 0 ? badge('critical', 'Overdue') : badge('neutral', diff === 0 ? 'Due today' : 'Due ' + shortDate(due));

    return h('div', { 'class': 'detail' },
      h('a', { 'class': 'link detail-back', href: '#tasks' }, icon('chevron-left', 'arrow'), 'All tasks'),
      h('div', { 'class': 'pagehead__text' },
        h('p', { 'class': 'eyebrow' }, p.name + ' · ' + stage.name),
        h('h2', { 'class': 'detail__title', id: 'detail-title' }, t.title)),
      h('div', { 'class': 'detail__badges' }, statusBadge(t.status), dueBadge, badge('neutral', PRIORITY[t.priority] + ' priority')),
      t.status === 'blocked' && t.blockedBy
        ? h('p', { 'class': 'blocked-note' }, icon('triangle-alert'), h('span', null, h('strong', null, 'Blocked. '), t.blockedBy))
        : null,
      h('dl', { 'class': 'facts detail__text' },
        h('div', null, h('dt', null, 'Owner'), h('dd', null, person(founderById[t.owner]))),
        h('div', null, h('dt', null, 'Outcome'), h('dd', null, t.outcome)),
        h('div', null, h('dt', null, 'Stage gate'), h('dd', null, gateText(stage.gate), ': ', stage.question.charAt(0).toLowerCase() + stage.question.slice(1)))),
      t.checklist && t.checklist.length ? h('div', { 'class': 'detail__section' },
        h('h3', { 'class': 'eyebrow' }, 'Checklist'),
        h('ul', null, t.checklist.map(function (item, i) {
          var id = 'tchk-' + t.id + '-' + i;
          return h('li', null, h('label', { 'class': 'check' + (item.done ? ' check--done' : ''), 'for': id },
            h('input', { type: 'checkbox', id: id, 'data-task-check': t.id + '|' + i, 'data-key': id, checked: item.done }),
            h('span', null, item.text)));
        }))) : null,
      h('div', { 'class': 'field', style: { maxWidth: '20rem' } },
        h('label', { 'class': 'field__label', 'for': 'task-status' }, 'Status'),
        h('div', { 'class': 'select' },
          h('select', { 'class': 'input', id: 'task-status', 'data-status-for': t.id, 'data-key': 'task-status' },
            Object.keys(STATUS).map(function (k) { return h('option', { value: k, selected: k === t.status }, STATUS[k][1]); })),
          icon('chevron-down'))),
      h('div', { 'class': 'detail__actions' },
        t.status !== 'done'
          ? h('button', { 'class': 'bf-btn bf-btn--secondary js-mark-done', type: 'button', 'data-done': t.id, 'data-key': 'done-' + t.id }, icon('check'), 'Mark as done')
          : null,
        h('button', { 'class': 'bf-btn bf-btn--quiet', type: 'button', 'data-toggle-update': t.id, 'data-key': 'toggle-update', 'aria-expanded': String(updateDraftOpen) }, icon('message-square'), 'Add update')),
      updateDraftOpen ? h('form', { 'class': 'update-form', 'data-update-for': t.id, novalidate: true },
        h('div', { 'class': 'field' },
          h('label', { 'class': 'field__label', 'for': 'update-text' }, 'Progress update'),
          h('textarea', { 'class': 'input', id: 'update-text', rows: '3', maxlength: '280', 'aria-describedby': 'update-hint update-error', 'data-key': 'update-text' }),
          h('p', { 'class': 'field__hint', id: 'update-hint' }, 'Done, next, blocked. Short and specific.'),
          h('p', { 'class': 'field__error', id: 'update-error', hidden: true })),
        h('div', null, h('button', { 'class': 'bf-btn bf-btn--secondary', type: 'submit', 'data-key': 'post-update' }, 'Post update'))) : null,
      h('section', { 'aria-labelledby': 'updates-title' },
        h('h3', { 'class': 'eyebrow', id: 'updates-title' }, 'Updates'),
        t.updates.length
          ? h('ol', { 'class': 'updates' }, t.updates.map(function (u) {
            return h('li', null, h('span', { 'class': 'caption' }, founderById[u.by].first + ' · ' + u.when), h('span', null, u.text));
          }))
          : h('p', { 'class': 'caption' }, 'No updates yet. Wednesday’s written update goes here: done, next, blocked.')));
  }

  function viewTasks(selectedId) {
    var list = filteredTasks();
    var selected = selectedId ? taskById[selectedId] : null;
    var open = list.filter(function (t) { return t.status !== 'done'; }).length;

    var listCard = h('section', { 'class': 'card workmode__list', 'aria-labelledby': 'tasklist-title' },
      h('div', { 'class': 'card__head' },
        h('h2', { 'class': 'card__title', id: 'tasklist-title' }, 'Shared task list'),
        h('p', { 'class': 'card__meta' }, plural(open, 'open', 'open') + ' · ' + list.length + ' shown')),
      list.length
        ? h('ul', { 'class': 'task-list' }, list.map(function (t) { return taskListItem(t, selectedId); }))
        : emptyState('No tasks match these filters', 'Try another founder or project, or show everything.',
          h('button', { 'class': 'bf-btn bf-btn--secondary bf-btn--compact', type: 'button', 'data-quick': 'all', 'data-key': 'clear-filters' }, 'Clear filters')));

    var detailCard = h('section', { 'class': 'card workmode__detail', 'aria-labelledby': selected ? 'detail-title' : 'detail-empty-title' },
      selected ? taskDetail(selected) : h('div', { 'class': 'detail' },
        h('h2', { 'class': 'card__title', id: 'detail-empty-title' }, 'Pick a task'),
        h('p', { 'class': 'muted' }, 'Its owner, outcome, checklist and updates show here.')));

    return {
      title: selected ? selected.title : 'Tasks',
      sticky: selected && selected.status !== 'done' ? 'mark-done' : (selected ? null : 'new-task'),
      stickyTask: selected ? selected.id : null,
      nodes: [
        pageHead({ eyebrow: 'Founder Command Centre', title: 'Tasks', sub: 'Every task has one owner, a deadline and a clear outcome.' }),
        h('div', { 'class': 'tasks-view', 'data-has-detail': selected ? 'true' : 'false' },
          prioritiesSection(),
          filtersRow(),
          h('div', { 'class': 'workmode' }, listCard, detailCard))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     D · Clients and opportunities
     ------------------------------------------------------------------------ */

  function clientCard(c) {
    var dealBadge = c.deal === 'Won' ? badge('ok', 'Won', true) : c.deal === 'Lost' ? badge('neutral', 'Lost', true) : badge('progress', 'Open', true);
    return h('article', { 'class': 'card client', 'aria-labelledby': 'client-' + c.id, 'data-find': 'client-' + c.id },
      h('div', { 'class': 'client__head' },
        h('h2', { 'class': 'client__name', id: 'client-' + c.id }, c.name),
        h('span', { 'class': 'detail__badges' }, c.illustrative ? badge('attention', 'Illustrative', true) : null, dealBadge)),
      h('dl', { 'class': 'facts' },
        h('div', null, h('dt', null, 'Lead source'), h('dd', null, c.source)),
        h('div', null, h('dt', null, 'Discovery'), h('dd', null, statusBadge(c.discovery, true))),
        h('div', null, h('dt', null, 'Proposal'), h('dd', null, c.proposal)),
        h('div', null, h('dt', null, 'Potential revenue'), h('dd', { 'class': 'muted' }, 'Not recorded yet')),
        h('div', null, h('dt', null, 'Next follow-up'), h('dd', null, c.followUp ? h('strong', null, dueText(c.followUp)) : 'None')),
        h('div', null, h('dt', null, 'Owner'), h('dd', null, person(founderById[c.owner])))),
      h('details', { 'class': 'history' },
        h('summary', null, 'Notes and history (' + c.history.length + ')'),
        h('ol', null, c.history.map(function (e) {
          return h('li', null, h('span', { 'class': 'caption num' }, dayMonth(parseDate(e.date))), h('span', null, e.text));
        }))));
  }

  function viewClients() {
    var list = clients.filter(function (c) { return !c.internal; });
    var open = list.filter(function (c) { return c.deal === 'Open'; })
      .sort(function (a, b) { return (a.followUp || '9999').localeCompare(b.followUp || '9999'); });
    var closed = list.filter(function (c) { return c.deal !== 'Open'; });
    return {
      title: 'Clients and opportunities',
      nodes: [
        pageHead({
          eyebrow: 'Founder Command Centre',
          title: 'Clients and opportunities',
          sub: plural(open.length, 'open opportunity', 'open opportunities') + ' · ' + closed.filter(function (c) { return c.deal === 'Won'; }).length + ' won · ' + closed.filter(function (c) { return c.deal === 'Lost'; }).length + ' lost'
        }),
        h('div', { 'class': 'cards-2' }, open.concat(closed).map(clientCard))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     E · Finance and business health (real records only)
     ------------------------------------------------------------------------ */

  function viewFinance() {
    var task = taskById.t9;
    return {
      title: 'Finance and business health',
      nodes: [
        pageHead({ eyebrow: 'Founder Command Centre', title: 'Finance and business health', sub: 'Real records only. Nothing here is estimated or invented.' }),
        emptyState('No figures yet',
          'Link the finance sheet in Google Sheets and these six figures will come from it. Until then they stay empty.',
          task ? h('a', { 'class': 'link', href: '#task-' + task.id }, icon('link', 'arrow'), task.title + ' · ' + founderById[task.owner].first + ' · due ' + dueInline(task.due)) : null),
        h('div', { 'class': 'cards-3' }, S.finance.map(function (f) {
          return h('div', { 'class': 'bf-tile finance-tile' },
            h('p', { 'class': 'bf-tile__label' }, f.name),
            h('p', { 'class': 'bf-tile__value', 'aria-label': 'No figure yet' }, '—'),
            h('p', { 'class': 'bf-tile__caption' }, f.purpose));
        }))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     F · Decisions and shared knowledge
     ------------------------------------------------------------------------ */

  function viewDecisions() {
    var pending = pendingDecisions();
    var log = sortedDecisions();
    return {
      title: 'Decisions',
      nodes: [
        pageHead({ eyebrow: 'Founder Command Centre', title: 'Decisions', sub: 'What was decided, by whom and when. Both founders agree on scope, major costs and stage changes.' }),
        h('div', { 'class': 'grid' },
          h('section', { 'class': 'stack dec-open', 'aria-labelledby': 'open-title' },
            h('h2', { 'class': 'card__title', id: 'open-title' }, 'Needs both founders'),
            pending.length
              ? h('div', { 'class': 'cards-2' }, pending.map(function (d) {
                return h('article', { 'class': 'card card--soft decision-card' }, decisionBlock(d));
              }))
              : emptyState('Nothing waiting', 'Decisions that need both founders will appear here.')),
          card('dec-log', 'declog-title', 'Decision log', null,
            h('div', { 'class': 'table-wrap' },
              h('table', { 'class': 'data-table data-table--cards' },
                h('caption', { 'class': 'visually-hidden' }, 'Decision log: what was decided, by whom and when'),
                h('thead', null, h('tr', null,
                  h('th', { scope: 'col' }, 'Decision'),
                  h('th', { scope: 'col' }, 'Who'),
                  h('th', { scope: 'col' }, 'When'),
                  h('th', { scope: 'col' }, 'Status'))),
                h('tbody', null, log.map(function (d) {
                  return h('tr', { 'data-find': 'decision-' + d.id },
                    h('td', null, d.title),
                    h('td', null, d.status === 'decided' ? d.by : 'Both founders'),
                    h('td', { 'class': 'num' }, d.status === 'decided' ? dayMonth(parseDate(d.decidedOn)) : 'Due ' + dueInline(d.due)),
                    h('td', { 'data-cell': 'status' }, decisionStatus(d)));
                }))))),
          card('dec-meet', 'meet-title', 'Meeting notes and action items', null,
            h('ul', { 'class': 'rows' }, S.meetings.map(function (m) {
              return h('li', null, h('div', { 'class': 'row' },
                icon('message-square', 'row__icon'),
                h('span', { 'class': 'row__main' },
                  h('span', { 'class': 'row__title' }, m.title),
                  h('span', { 'class': 'row__meta' }, m.note + ' ' + plural(m.actions, 'action item', 'action items') + '.')),
                h('span', { 'class': 'row__date' }, dayMonth(parseDate(m.date)))));
            }))))
      ]
    };
  }

  function docRow(doc) {
    return h('li', { 'data-find': 'doc-' + doc.id }, h('div', { 'class': 'row' },
      h('span', { 'class': 'doc-icon' }, icon(doc.secure ? 'lock' : 'file-text')),
      h('span', { 'class': 'row__main' },
        h('span', { 'class': 'row__title' }, doc.title),
        h('span', { 'class': 'row__meta' }, doc.where + ' · updated ' + dayMonth(parseDate(doc.updated)))),
      doc.secure ? badge('neutral', 'Reference only', true) : null));
  }

  function viewKnowledge() {
    var groups = [];
    knowledge.forEach(function (doc) {
      var g = groups.filter(function (x) { return x.name === doc.group; })[0];
      if (!g) groups.push(g = { name: doc.group, docs: [] });
      g.docs.push(doc);
    });
    return {
      title: 'Knowledge',
      nodes: [
        pageHead({ eyebrow: 'Founder Command Centre', title: 'Knowledge', sub: 'Where things live, so work continues when one founder is away. Titles and places only: passwords never go here.' }),
        h('div', { 'class': 'cards-2' }, groups.map(function (g, i) {
          return card('', 'kg-' + i, g.name, null, h('ul', { 'class': 'rows' }, g.docs.map(docRow)));
        }))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     More (phones): the sections the five tabs don't hold
     ------------------------------------------------------------------------ */

  function viewMore() {
    var items = [
      ['finance', 'wallet', 'Finance', 'Business health from real records'],
      ['decisions', 'scale', 'Decisions', plural(pendingDecisions().length, 'decision needs', 'decisions need') + ' both founders'],
      ['knowledge', 'book-open', 'Knowledge', 'Procedures, briefs and technical documents']
    ];
    return {
      title: 'More',
      nodes: [
        pageHead({ eyebrow: 'Founder Command Centre', title: 'More' }),
        h('section', { 'class': 'card more-list', 'aria-label': 'More sections' },
          h('ul', { 'class': 'rows' }, items.map(function (it) {
            return h('li', null, h('a', { 'class': 'row', href: '#' + it[0] },
              h('span', { 'class': 'doc-icon' }, icon(it[1])),
              h('span', { 'class': 'row__main' }, h('span', { 'class': 'row__title' }, it[2]), h('span', { 'class': 'row__meta' }, it[3])),
              icon('chevron-right', 'row__icon')));
          }))),
        card('', 'more-viewer-title', 'Viewing as', null, viewerButtons('more')),
        h('p', { 'class': 'sidenav__note' }, badge('neutral', 'Concept', true), h('span', null, 'Sample data'))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     Routing and rendering
     ------------------------------------------------------------------------ */

  var VIEWS = ['overview', 'projects', 'tasks', 'clients', 'finance', 'decisions', 'knowledge', 'more'];

  function parseRoute() {
    var name = window.location.hash.slice(1);
    if (VIEWS.indexOf(name) !== -1) return { view: name };
    var m = name.match(/^project-([a-z0-9-]+)$/);
    if (m && projectById[m[1]]) return { view: 'project', id: m[1] };
    m = name.match(/^task-([a-z0-9-]+)$/);
    if (m && taskById[m[1]]) return { view: 'tasks', task: m[1] };
    return null;
  }

  var route = parseRoute() || { view: 'overview' };

  function navKey(r, inTabs) {
    if (r.view === 'project') return 'projects';
    if (inTabs && ['finance', 'decisions', 'knowledge'].indexOf(r.view) !== -1) return 'more';
    return r.view;
  }

  function buildView(r) {
    switch (r.view) {
      case 'projects': return viewProjects();
      case 'project': return viewProject(r.id);
      case 'tasks': return viewTasks(r.task);
      case 'clients': return viewClients();
      case 'finance': return viewFinance();
      case 'decisions': return viewDecisions();
      case 'knowledge': return viewKnowledge();
      case 'more': return viewMore();
      default: return viewOverview();
    }
  }

  function viewerButtons(where) {
    return h('div', { 'class': 'viewer__options' }, founders.map(function (f) {
      return h('button', {
        'class': 'viewer__btn',
        type: 'button',
        'data-viewer': f.id,
        'data-key': 'viewer-' + where + '-' + f.id,
        'aria-pressed': String(f.id === me),
        'aria-label': 'View as ' + f.first,
        title: 'View as ' + f.first
      }, avatar(f, true), h('span', { 'class': 'viewer__name' }, f.first));
    }));
  }

  function syncShell(view) {
    document.querySelectorAll('.sidenav [data-nav]').forEach(function (a) {
      if (a.getAttribute('data-nav') === navKey(route, false)) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
    document.querySelectorAll('.tabbar [data-nav]').forEach(function (a) {
      if (a.getAttribute('data-nav') === navKey(route, true)) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
    $('viewer-options').replaceWith(Object.assign(viewerButtons('side'), { id: 'viewer-options' }));

    var sticky = $('sticky-action');
    sticky.replaceChildren();
    if (view.sticky === 'new-task') {
      sticky.append(h('button', { 'class': 'bf-btn bf-btn--primary', type: 'button', 'data-new-task': route.view === 'project' ? route.id : '', 'data-key': 'sticky-new' }, icon('plus'), 'New task'));
    } else if (view.sticky === 'mark-done') {
      sticky.append(h('button', { 'class': 'bf-btn bf-btn--primary', type: 'button', 'data-done': view.stickyTask, 'data-key': 'sticky-done' }, icon('check'), 'Mark as done'));
    }
    sticky.hidden = !view.sticky;
    $('app').setAttribute('data-sticky', String(!!view.sticky));
    root.setAttribute('data-sticky', String(!!view.sticky));
    document.title = view.title + ' · Founder Command Centre';
  }

  function render(opts) {
    opts = opts || {};
    var active = document.activeElement;
    var key = active && active.getAttribute ? active.getAttribute('data-key') : null;
    var view = buildView(route);
    var main = $('main');
    main.replaceChildren.apply(main, view.nodes);
    syncShell(view);
    if (opts.top) window.scrollTo(0, 0);
    if (opts.focusMain) {
      main.focus({ preventScroll: true });
    } else if (key) {
      var again = document.querySelector('[data-key="' + key + '"]');
      if (again) again.focus({ preventScroll: true });
    }
    if (opts.find) highlight(opts.find);
  }

  function highlight(findKey) {
    var target = document.querySelector('[data-find="' + findKey + '"]');
    if (!target) return;
    target.scrollIntoView({ block: 'center', behavior: 'auto' });
    target.classList.remove('is-found');
    void target.offsetWidth;
    target.classList.add('is-found');
  }

  var pendingFind = null;

  window.addEventListener('hashchange', function () {
    var next = parseRoute();
    if (!next) return;
    var sameTasks = route.view === 'tasks' && next.view === 'tasks';
    route = next;
    updateDraftOpen = false;
    var wide = window.matchMedia('(min-width: 1100px)').matches;
    render({ top: !(sameTasks && wide), focusMain: !(sameTasks && wide), find: pendingFind });
    if (sameTasks && wide && next.task) {
      var title = $('detail-title');
      if (title) { title.setAttribute('tabindex', '-1'); title.focus({ preventScroll: true }); }
    }
    pendingFind = null;
  });

  function go(hash, findKey) {
    pendingFind = findKey || null;
    if (window.location.hash === '#' + hash) {
      render({ find: pendingFind });
      pendingFind = null;
    } else {
      window.location.hash = hash;
    }
  }

  /* ------------------------------------------------------------------------
     Actions
     ------------------------------------------------------------------------ */

  function toast(text) {
    var node = h('div', { 'class': 'toast' }, icon('circle-check'), h('p', null, text));
    $('toasts').append(node);
    window.setTimeout(function () {
      node.classList.add('is-leaving');
      window.setTimeout(function () { node.remove(); }, 260);
    }, 5000);
  }

  function approve(id) {
    var d = decisionById[id];
    if (!d || d.status !== 'open') return;
    if (d.approvals.indexOf(me) === -1) d.approvals.push(me);
    stamp();
    var bothIn = founders.every(function (f) { return d.approvals.indexOf(f.id) !== -1; });
    if (!bothIn) {
      toast('Approved. Waiting on ' + other(me).first + '.');
      render();
      return;
    }
    d.status = 'decided';
    d.decidedOn = S.today;
    d.by = 'Both founders';
    var message = 'Decision recorded: ' + d.title + '.';
    if (d.project) {
      var p = projectById[d.project];
      var gate = S.stages[p.stage].gate;
      var next = nextStage(p.stage);
      if (next) {
        p.stage = next;
        p.status = 'Gate passed';
        if (!p.checks[next]) p.checks[next] = new Set();
        message = 'Both founders approved. ' + p.name + ' passed ' + gate.replace(' → ', ' to ') + '.';
      }
    }
    toast(message);
    render();
  }

  function markDone(id) {
    var t = taskById[id];
    if (!t || t.status === 'done') return;
    t.status = 'done';
    stamp();
    toast('Done: ' + t.title + '.');
    render();
  }

  function setViewer(id) {
    if (!founderById[id] || id === me) return;
    me = id;
    try { window.localStorage.setItem('baseflow-viewer', id); } catch (e) { /* not saved */ }
    if (filter.owner !== 'any') filter.owner = id;
    render();
  }

  document.addEventListener('click', function (ev) {
    var el = ev.target.closest('[data-approve], [data-done], [data-viewer], [data-quick], [data-new-task], [data-toggle-update], [data-scope]');
    if (!el) return;
    if (el.hasAttribute('data-approve')) approve(el.getAttribute('data-approve'));
    else if (el.hasAttribute('data-done')) markDone(el.getAttribute('data-done'));
    else if (el.hasAttribute('data-viewer')) setViewer(el.getAttribute('data-viewer'));
    else if (el.hasAttribute('data-new-task')) openTaskDialog(el.getAttribute('data-new-task') || null, el);
    else if (el.hasAttribute('data-toggle-update')) {
      updateDraftOpen = !updateDraftOpen;
      render();
      if (updateDraftOpen) { var box = $('update-text'); if (box) box.focus(); }
    } else if (el.hasAttribute('data-scope')) {
      filter = { owner: me, project: 'any', priority: 'any', due: 'any' };
    } else if (el.hasAttribute('data-quick')) {
      var quick = el.getAttribute('data-quick');
      if (quick === 'all') filter = { owner: 'any', project: 'any', priority: 'any', due: 'any' };
      if (quick === 'mine') filter.owner = filter.owner === me ? 'any' : me;
      if (quick === 'week') filter.due = filter.due === 'week' ? 'any' : 'week';
      render();
    }
  });

  document.addEventListener('change', function (ev) {
    var el = ev.target;
    if (el.hasAttribute('data-filter')) {
      filter[el.getAttribute('data-filter')] = el.value;
      render();
    } else if (el.hasAttribute('data-check')) {
      var parts = el.getAttribute('data-check').split('|');
      var p = projectById[parts[0]];
      var set = p.checks[p.stage];
      if (el.checked) set.add(parts[1]); else set.delete(parts[1]);
      stamp();
      render();
    } else if (el.hasAttribute('data-task-check')) {
      var tp = el.getAttribute('data-task-check').split('|');
      taskById[tp[0]].checklist[Number(tp[1])].done = el.checked;
      stamp();
      render();
    } else if (el.hasAttribute('data-status-for')) {
      var t = taskById[el.getAttribute('data-status-for')];
      t.status = el.value;
      stamp();
      toast('Status changed to ' + STATUS[t.status][1].toLowerCase() + '.');
      render();
    }
  });

  document.addEventListener('submit', function (ev) {
    var form = ev.target;
    if (!form.hasAttribute('data-update-for')) return;
    ev.preventDefault();
    var t = taskById[form.getAttribute('data-update-for')];
    var box = $('update-text');
    var error = $('update-error');
    var text = box.value.trim();
    if (!text) {
      error.hidden = false;
      error.replaceChildren(icon('octagon-alert'), h('span', null, 'Write what’s done, what’s next or what’s blocked.'));
      box.setAttribute('aria-invalid', 'true');
      box.focus();
      return;
    }
    t.updates.unshift({ by: me, when: 'Today ' + clockText(nowSec()), text: text });
    updateDraftOpen = false;
    stamp();
    toast('Update posted.');
    render();
  });

  /* ------------------------------------------------------------------------
     New task: one owner, a deadline and a clearly defined outcome
     ------------------------------------------------------------------------ */

  var dialog = $('task-dialog');
  var form = $('task-form');
  var opener = null;
  var showAll = false;
  var touched = {};
  var VAGUE = /^(work on|look at|look into|sort out|deal with|handle|do|check|stuff|things|misc)\b/i;

  $('f-project').replaceChildren.apply($('f-project'), projects.map(function (p) {
    return h('option', { value: p.id }, p.name);
  }));
  $('f-owner').replaceChildren.apply($('f-owner'), founders.map(function (f) {
    return [
      h('input', { type: 'radio', name: 'owner', id: 'f-owner-' + f.id, value: f.id }),
      h('label', { 'for': 'f-owner-' + f.id }, avatar(f, true), f.first)
    ];
  }).reduce(function (a, b) { return a.concat(b); }, []));

  function fieldError(input, errorNode, message) {
    errorNode.hidden = !message;
    errorNode.replaceChildren.apply(errorNode, message ? [icon('octagon-alert'), h('span', null, message)] : []);
    if (message) input.setAttribute('aria-invalid', 'true');
    else input.removeAttribute('aria-invalid');
  }

  function validateTask(focusFirst) {
    var title = $('f-title');
    var outcome = $('f-outcome');
    var due = $('f-due');
    var first = null;

    var titleText = title.value.trim();
    var titleMsg = '';
    if (!titleText) titleMsg = 'Write what will be done.';
    else if (VAGUE.test(titleText) || titleText.split(/\s+/).length < 3) titleMsg = 'Make it specific. Say what will be done, like “Draft pilot scope for coordinators”, not “work on app”.';
    fieldError(title, $('f-title-error'), showAll || touched.title ? titleMsg : '');
    if (titleMsg && !first) first = title;

    var outcomeMsg = outcome.value.trim() ? '' : 'Say what done looks like, so the owner knows when to stop.';
    fieldError(outcome, $('f-outcome-error'), showAll || touched.outcome ? outcomeMsg : '');
    if (outcomeMsg && !first) first = outcome;

    var dueMsg = '';
    if (!due.value) dueMsg = 'Pick a due date.';
    else if (dayDiff(TODAY, parseDate(due.value)) < 0) dueMsg = 'Pick today or a later date.';
    fieldError(due, $('f-due-error'), showAll || touched.due ? dueMsg : '');
    if (dueMsg && !first) first = due;

    if (focusFirst && first) first.focus();
    return !first;
  }

  function openTaskDialog(projectId, from) {
    opener = from || document.activeElement;
    showAll = false;
    touched = {};
    form.reset();
    $('f-project').value = projectId || (route.view === 'project' ? route.id : projects[0].id);
    $('f-owner-' + me).checked = true;
    $('f-due').min = S.today;
    ['f-title', 'f-outcome', 'f-due'].forEach(function (id) { $(id).removeAttribute('aria-invalid'); });
    ['f-title-error', 'f-outcome-error', 'f-due-error'].forEach(function (id) { $(id).hidden = true; });
    dialog.showModal();
    $('f-title').focus();
  }

  ['f-title', 'f-outcome', 'f-due'].forEach(function (id) {
    $(id).addEventListener('blur', function () {
      if ($(id).value !== '') touched[id.replace('f-', '')] = true;
      validateTask(false);
    });
    $(id).addEventListener('input', function () {
      if (showAll || touched[id.replace('f-', '')]) validateTask(false);
    });
  });

  form.addEventListener('submit', function (ev) {
    ev.preventDefault();
    showAll = true;
    if (!validateTask(true)) return;
    taskSeq += 1;
    var t = {
      id: 'new' + taskSeq,
      title: $('f-title').value.trim(),
      outcome: $('f-outcome').value.trim(),
      project: $('f-project').value,
      owner: form.elements.owner.value,
      due: $('f-due').value,
      priority: form.elements.priority.value,
      status: 'not-started',
      updates: []
    };
    tasks.push(t);
    taskById[t.id] = t;
    stamp();
    dialog.close();
    toast('Task added for ' + founderById[t.owner].first + ', due ' + dueInline(t.due) + '.');
    if (route.view === 'tasks') go('task-' + t.id, 'task-' + t.id);
    else render({ find: 'task-' + t.id });
  });

  form.querySelectorAll('[data-close]').forEach(function (button) {
    button.addEventListener('click', function () { dialog.close(); });
  });

  dialog.addEventListener('click', function (ev) {
    if (ev.target === dialog) dialog.close();
  });

  dialog.addEventListener('close', function () {
    var target = opener && opener.isConnected ? opener : $('new-task');
    if (target && target.offsetParent !== null) target.focus();
    else $('main').focus({ preventScroll: true });
  });

  $('new-task').addEventListener('click', function (ev) {
    openTaskDialog(route.view === 'project' ? route.id : null, ev.currentTarget);
  });

  /* ------------------------------------------------------------------------
     Search: projects, tasks, clients, decisions and notes
     ------------------------------------------------------------------------ */

  var searchInput = $('search');
  var resultsBox = $('search-results');
  var results = [];
  var activeResult = -1;

  function searchIndex() {
    var items = [];
    projects.forEach(function (p) {
      items.push({ icon: 'folder', title: p.name, meta: 'Project · ' + S.stages[p.stage].name, text: p.name + ' ' + clientById[p.client].name, hash: 'project-' + p.id });
    });
    tasks.forEach(function (t) {
      items.push({ icon: 'list-todo', title: t.title, meta: 'Task · ' + founderById[t.owner].first + ' · ' + STATUS[t.status][1], text: t.title + ' ' + t.outcome, hash: 'task-' + t.id, find: 'task-' + t.id });
    });
    clients.filter(function (c) { return !c.internal; }).forEach(function (c) {
      items.push({ icon: 'users', title: c.name, meta: 'Client · ' + c.deal, text: c.name + ' ' + c.source, hash: 'clients', find: 'client-' + c.id });
    });
    decisions.forEach(function (d) {
      items.push({ icon: 'scale', title: d.title, meta: 'Decision · ' + (d.status === 'open' ? 'Open' : 'Decided'), text: d.title + ' ' + d.context, hash: 'decisions', find: 'decision-' + d.id });
    });
    knowledge.forEach(function (doc) {
      items.push({ icon: doc.secure ? 'lock' : 'file-text', title: doc.title, meta: 'Note · ' + doc.where, text: doc.title + ' ' + doc.group, hash: 'knowledge', find: 'doc-' + doc.id });
    });
    return items;
  }

  function closeSearch() {
    resultsBox.hidden = true;
    searchInput.setAttribute('aria-expanded', 'false');
    searchInput.removeAttribute('aria-activedescendant');
    activeResult = -1;
  }

  function showResults() {
    var q = searchInput.value.trim().toLowerCase();
    if (q.length < 2) { closeSearch(); return; }
    results = searchIndex().filter(function (item) { return item.text.toLowerCase().indexOf(q) !== -1; }).slice(0, 7);
    activeResult = results.length ? 0 : -1;
    resultsBox.replaceChildren.apply(resultsBox, results.length
      ? results.map(function (item, i) {
        return h('div', { 'class': 'search__option', role: 'option', id: 'search-opt-' + i, 'aria-selected': String(i === activeResult), 'data-result': String(i) },
          icon(item.icon),
          h('span', { 'class': 'search__text' }, h('span', { 'class': 'search__title' }, item.title), h('span', { 'class': 'search__meta' }, item.meta)));
      })
      : [h('p', { 'class': 'search__empty', role: 'status' }, icon('info'), 'Nothing matches “' + searchInput.value.trim() + '”. Try a project, client or task.')]);
    resultsBox.hidden = false;
    searchInput.setAttribute('aria-expanded', 'true');
    if (activeResult >= 0) searchInput.setAttribute('aria-activedescendant', 'search-opt-0');
    else searchInput.removeAttribute('aria-activedescendant');
  }

  function moveResult(step) {
    if (!results.length) return;
    activeResult = (activeResult + step + results.length) % results.length;
    resultsBox.querySelectorAll('.search__option').forEach(function (opt, i) {
      opt.setAttribute('aria-selected', String(i === activeResult));
    });
    searchInput.setAttribute('aria-activedescendant', 'search-opt-' + activeResult);
  }

  function chooseResult(i) {
    var item = results[i];
    if (!item) return;
    closeSearch();
    searchInput.value = '';
    go(item.hash, item.find || null);
  }

  searchInput.addEventListener('input', showResults);
  searchInput.addEventListener('keydown', function (ev) {
    if (ev.key === 'ArrowDown') { ev.preventDefault(); if (resultsBox.hidden) showResults(); else moveResult(1); }
    else if (ev.key === 'ArrowUp') { ev.preventDefault(); moveResult(-1); }
    else if (ev.key === 'Enter') { ev.preventDefault(); chooseResult(activeResult); }
    else if (ev.key === 'Escape') { if (!resultsBox.hidden) { ev.preventDefault(); closeSearch(); } else searchInput.value = ''; }
  });
  searchInput.addEventListener('blur', function () { window.setTimeout(closeSearch, 120); });
  resultsBox.addEventListener('mousedown', function (ev) { ev.preventDefault(); });
  resultsBox.addEventListener('click', function (ev) {
    var opt = ev.target.closest('[data-result]');
    if (opt) chooseResult(Number(opt.getAttribute('data-result')));
  });

  /* ------------------------------------------------------------------------
     Floor mode, offline, start
     ------------------------------------------------------------------------ */

  var modeButton = $('mode-toggle');
  var darkQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function floorShowing() {
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

  function syncOnline() {
    $('offline').hidden = navigator.onLine !== false;
  }

  window.addEventListener('offline', syncOnline);
  window.addEventListener('online', function () {
    syncOnline();
    toast('You’re back online.');
  });

  $('today-label').textContent = shortDate(TODAY);
  syncModeButton();
  syncOnline();
  render();

  // The greeting follows the sample clock.
  var lastHour = Math.floor(nowSec() / 3600);
  window.setInterval(function () {
    var hour = Math.floor(nowSec() / 3600);
    if (hour !== lastHour && route.view === 'overview' && !dialog.open) {
      lastHour = hour;
      render();
    }
  }, 60000);
})();
