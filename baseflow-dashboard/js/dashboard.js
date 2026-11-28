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
  var MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  function parseDate(iso) {
    var p = iso.split('-').map(Number);
    return new Date(p[0], p[1] - 1, p[2]);
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
    return d.getDate() + ' ' + MONTHS[d.getMonth()].slice(0, 3);
  }

  function shortDate(d) {
    return DAYS[d.getDay()].slice(0, 3) + ' ' + dayMonth(d);
  }

  // "Thursday 19 November"
  function fullDate(d) {
    return DAYS[d.getDay()] + ' ' + d.getDate() + ' ' + MONTHS[d.getMonth()];
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

  // The sample's clock starts at S.now and runs in real time. It stamps updates.
  var START = Number(S.now.split(':')[0]) * 3600 + Number(S.now.split(':')[1]) * 60;
  var openedAt = performance.now();

  function nowSec() {
    return START + Math.floor((performance.now() - openedAt) / 1000);
  }

  function clockText(sec) {
    return pad(Math.floor(sec / 3600) % 24) + ':' + pad(Math.floor(sec / 60) % 60);
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

  function first(id) {
    return founderById[id].first;
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

  function upcoming(list) {
    return list
      .filter(function (m) { return dayDiff(TODAY, parseDate(m.date)) >= 0; })
      .sort(function (a, b) { return parseDate(a.date) - parseDate(b.date); });
  }

  function milestones() {
    var list = [];
    projects.forEach(function (p) {
      (p.milestones || []).forEach(function (m) {
        list.push({ title: m.title, date: m.date, project: p.id });
      });
    });
    return upcoming(list);
  }

  function sortedDecisions() {
    return decisions.slice().sort(function (a, b) {
      if (a.status !== b.status) return a.status === 'open' ? -1 : 1;
      if (a.status === 'open') return parseDate(a.due) - parseDate(b.due);
      return parseDate(b.decidedOn) - parseDate(a.decidedOn);
    });
  }

  /* ------------------------------------------------------------------------
     Small pieces used across views
     ------------------------------------------------------------------------ */

  // In lists and tables a status is a dot and a word; the badge is kept for
  // the one task you have open.
  function state(kind, text) {
    return h('span', { 'class': 'state state--' + kind }, text);
  }

  function taskState(status) {
    return state(STATUS[status][0], STATUS[status][1]);
  }

  function decisionState(d) {
    return d.status === 'decided' ? state('ok', 'Decided') : state('attention', 'Open');
  }

  function statusBadge(status) {
    return h('span', { 'class': 'bf-status bf-status--' + STATUS[status][0] }, STATUS[status][1]);
  }

  function avatar(founder) {
    return h('span', {
      'class': 'avatar' + (founder.id === 'sm' ? ' avatar--sm' : ''),
      'aria-hidden': 'true'
    }, founder.initials);
  }

  function dueEl(t) {
    var d = parseDate(t.due);
    if (t.status !== 'done') {
      var diff = dayDiff(TODAY, d);
      if (diff < 0) return h('span', { 'class': 'due due--overdue' }, 'Overdue · ' + dayMonth(d));
      if (diff <= 1) return h('span', { 'class': 'due due--soon' }, dueText(t.due));
    }
    return h('span', { 'class': 'due' }, dueText(t.due));
  }

  function emptyState(title, text, action) {
    return h('div', { 'class': 'empty' },
      h('p', { 'class': 'empty__title' }, title),
      text ? h('p', null, text) : null,
      action || null);
  }

  function pageHead(o) {
    return h('header', { 'class': 'pagehead' },
      o.back ? h('a', { 'class': 'link back', href: o.back.href }, icon('chevron-left', 'arrow'), o.back.label) : null,
      o.kicker ? h('p', { 'class': 'pagehead__kicker' }, o.kicker) : null,
      h('h1', { id: 'page-title' }, o.title),
      o.sub ? h('p', { 'class': 'pagehead__sub' + (o.lead ? ' pagehead__sub--lead' : '') }, o.sub) : null);
  }

  function section(o) {
    var body = Array.prototype.slice.call(arguments, 1);
    return h('section', { 'class': 'section' + (o.cls ? ' ' + o.cls : ''), 'aria-labelledby': o.id },
      h('div', { 'class': 'section__head' },
        h('h2', { 'class': 'section__title', id: o.id }, o.title,
          o.count !== undefined ? h('span', { 'class': 'count' }, String(o.count)) : null),
        o.aside || null),
      o.note ? h('p', { 'class': 'section__note' }, o.note) : null,
      body);
  }

  function fact(label, value) {
    return h('div', null, h('dt', null, label), h('dd', null, value));
  }

  function taskRow(t, meta) {
    return h('a', { 'class': 'item item--task', href: '#task-' + t.id, 'data-find': 'task-' + t.id },
      h('span', { 'class': 'item__main' },
        h('span', { 'class': 'item__title' }, t.title),
        h('span', { 'class': 'item__meta' }, meta || projectById[t.project].name)),
      dueEl(t),
      taskState(t.status));
  }

  // A date block for agendas: "Fri" over "20", read out in full.
  function agendaItem(iso, title, meta, href) {
    var d = parseDate(iso);
    var diff = dayDiff(TODAY, d);
    var when = diff === 0 ? 'today' : diff === 1 ? 'tomorrow' : null;
    var inner = [
      h('span', { 'class': 'date' + (diff < 0 ? ' date--past' : ''), 'aria-hidden': 'true' },
        h('span', { 'class': 'date__day' }, DAYS[d.getDay()].slice(0, 3)),
        h('span', { 'class': 'date__num' }, String(d.getDate())),
        d.getMonth() !== TODAY.getMonth() ? h('span', { 'class': 'date__month' }, MONTHS[d.getMonth()].slice(0, 3)) : null),
      h('span', { 'class': 'item__main' },
        h('span', { 'class': 'visually-hidden' }, shortDate(d) + ': '),
        h('span', { 'class': 'item__title' }, title),
        meta || when ? h('span', { 'class': 'item__meta' }, [meta, when].filter(Boolean).join(' · ')) : null)
    ];
    return h('li', null, href
      ? h('a', { 'class': 'agenda__item', href: href }, inner)
      : h('div', { 'class': 'agenda__item' }, inner));
  }

  function pageFoot() {
    return h('footer', { 'class': 'pagefoot' }, 'Concept with sample data. These are examples, not business results.');
  }

  /* ------------------------------------------------------------------------
     A · Business overview
     ------------------------------------------------------------------------ */

  // One plain sentence about what is waiting on the founder viewing.
  function overviewLead() {
    var soon = tasks.filter(function (t) {
      return t.owner === me && t.status !== 'done' && dayDiff(TODAY, parseDate(t.due)) <= 1;
    }).length;
    var waiting = pendingDecisions().filter(function (d) { return d.approvals.indexOf(me) === -1; }).length;
    var name = first(me);
    var taskPart = soon ? plural(soon, 'task', 'tasks') + ' due by tomorrow' : '';
    var decisionPart = waiting ? plural(waiting, 'decision', 'decisions') + ' to approve' : '';
    if (taskPart && decisionPart) return name + ', you have ' + taskPart + ' and ' + decisionPart + '.';
    if (taskPart) return name + ', you have ' + taskPart + '. No decisions are waiting on you.';
    if (decisionPart) return name + ', nothing of yours is due by tomorrow. You have ' + decisionPart + '.';
    return name + ', nothing of yours is due by tomorrow and no decisions are waiting on you.';
  }

  function stat(href, label, value, note, extraClass) {
    return h('a', { 'class': 'stat' + (extraClass ? ' ' + extraClass : ''), href: href },
      h('span', { 'class': 'stat__label' }, label),
      h('span', { 'class': 'stat__value' }, value),
      h('span', { 'class': 'stat__note' }, note));
  }

  function stats() {
    var open = openTasks();
    var dueThisWeek = open.filter(function (t) { return inThisWeek(t.due); }).length;
    var discover = projects.filter(function (p) { return p.stage === 'discover'; }).length;
    var opportunities = openOpportunities();
    var nextFollowUp = opportunities.map(function (c) { return c.followUp; }).filter(Boolean).sort()[0];
    var pending = pendingDecisions();

    return h('section', { 'class': 'stats', 'aria-labelledby': 'stats-title' },
      h('h2', { 'class': 'visually-hidden', id: 'stats-title' }, 'Business health'),
      stat('#projects', 'Active projects', String(activeProjects().length), discover ? discover + ' more in Discover' : 'None in Discover'),
      stat('#clients', 'Open opportunities', String(opportunities.length), nextFollowUp ? 'Next follow-up ' + dueInline(nextFollowUp) : 'No follow-ups booked'),
      stat('#tasks', 'Outstanding tasks', String(open.length), dueThisWeek + ' due this week'),
      stat('#decisions', 'Pending decisions', String(pending.length), pending.length ? 'Need both founders' : 'Nothing waiting'),
      stat('#finance', 'Financial snapshot', 'Not linked yet', 'No real figures to show', 'stat--empty'));
  }

  function approvalText(d) {
    var mine = d.approvals.indexOf(me) !== -1;
    var others = d.approvals.filter(function (id) { return id !== me; }).map(first);
    if (mine) return 'You approved, waiting on ' + other(me).first;
    if (others.length) return others.join(' and ') + ' approved, waiting on you';
    return 'Waiting on both of you';
  }

  function decisionItem(d) {
    var mine = d.approvals.indexOf(me) !== -1;
    return h('li', { 'class': 'decision', 'data-find': 'decision-' + d.id },
      h('div', { 'class': 'decision__text' },
        h('p', { 'class': 'decision__title', id: 'dec-' + d.id }, d.title),
        h('p', null, d.context),
        h('p', { 'class': 'decision__meta' }, approvalText(d) + ' · due ' + dueInline(d.due))),
      mine ? null : h('button', {
        'class': 'bf-btn bf-btn--secondary bf-btn--compact',
        type: 'button',
        'data-approve': d.id,
        'data-key': 'approve-' + d.id,
        'aria-describedby': 'dec-' + d.id
      }, 'Approve'));
  }

  // The one tinted block on a screen: what can't move without both founders.
  function bothFounders() {
    var pending = pendingDecisions();
    return h('section', { 'class': 'callout', 'aria-labelledby': 'both-title' },
      h('div', { 'class': 'section__head' },
        h('h2', { 'class': 'section__title', id: 'both-title' }, 'Needs both founders',
          h('span', { 'class': 'count' }, String(pending.length)))),
      h('p', { 'class': 'section__note' }, 'Scope, major costs and stage changes need you both.'),
      pending.length
        ? h('ul', { 'class': 'decisions' }, pending.map(decisionItem))
        : h('p', null, 'Nothing is waiting on both of you.'));
  }

  function myTasks() {
    var mine = tasks.filter(function (t) { return t.owner === me && t.status !== 'done'; }).sort(byDue);
    var shown = mine.slice(0, 5);
    return section({
      id: 'mytasks-title',
      title: 'My tasks',
      count: mine.length,
      aside: mine.length > shown.length ? h('a', { 'class': 'link', href: '#tasks', 'data-scope': 'mine' }, 'See all ' + mine.length) : null
    }, mine.length
      ? h('ul', { 'class': 'list' }, shown.map(function (t) { return h('li', null, taskRow(t)); }))
      : emptyState('Nothing on your list', 'Tasks you own will show here.'));
  }

  function flow() {
    return section({ id: 'pipeline-title', title: 'Pipeline', cls: 'pipeline' },
      h('div', { 'class': 'flow' }, STAGE_ORDER.map(function (key) {
        var stage = S.stages[key];
        var list = projects.filter(function (p) { return p.stage === key; });
        return h('div', { 'class': 'flow__stage' },
          h('h3', { 'class': 'flow__name' },
            h('span', { 'class': 'mark mark--' + key, 'aria-hidden': 'true' }),
            stage.name,
            h('span', { 'class': 'count' }, String(list.length), h('span', { 'class': 'visually-hidden' }, list.length === 1 ? ' project' : ' projects'))),
          list.length
            ? h('ul', { 'class': 'flow__list' }, list.map(function (p) {
              return h('li', null,
                h('a', { 'class': 'flow__project', href: '#project-' + p.id, 'data-find': 'project-' + p.id }, p.name),
                h('span', { 'class': 'item__meta' }, p.status + (p.illustrative ? ' · Illustrative' : '')));
            }))
            : h('p', { 'class': 'item__meta' }, 'Nothing here right now.'));
      })));
  }

  function comingUp() {
    var list = milestones().slice(0, 5);
    return section({ id: 'deadlines-title', title: 'Coming up' },
      list.length
        ? h('ul', { 'class': 'agenda' }, list.map(function (m) {
          return agendaItem(m.date, m.title, projectById[m.project].name, '#project-' + m.project);
        }))
        : emptyState('No deadlines ahead', 'Milestones you add to projects will show here.'));
  }

  function recentDecisions() {
    var list = decisions
      .filter(function (d) { return d.status === 'decided'; })
      .sort(function (a, b) { return parseDate(b.decidedOn) - parseDate(a.decidedOn); })
      .slice(0, 3);
    return section({
      id: 'log-title',
      title: 'Decision log',
      aside: h('a', { 'class': 'link', href: '#decisions' }, 'Full log')
    }, h('ul', { 'class': 'list' }, list.map(function (d) {
      return h('li', null, h('div', { 'class': 'item' },
        h('span', { 'class': 'item__main' },
          h('span', { 'class': 'item__title' }, d.title),
          h('span', { 'class': 'item__meta' }, d.by + ' · ' + dayMonth(parseDate(d.decidedOn))))));
    })));
  }

  function viewOverview() {
    return {
      title: 'Overview',
      sticky: 'new-task',
      nodes: [
        pageHead({ title: fullDate(TODAY), sub: overviewLead(), lead: true }),
        stats(),
        h('div', { 'class': 'columns' },
          h('div', { 'class': 'columns__main' }, bothFounders(), myTasks(), flow()),
          h('div', { 'class': 'columns__side' }, comingUp(), recentDecisions()))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     B · Project pipeline and workspaces
     ------------------------------------------------------------------------ */

  function projectCard(p) {
    var progress = checklistProgress(p);
    var next = upcoming(p.milestones || [])[0];
    var openCount = tasks.filter(function (t) { return t.project === p.id && t.status !== 'done'; }).length;
    return h('a', { 'class': 'pcard', href: '#project-' + p.id, 'data-find': 'project-' + p.id },
      h('span', { 'class': 'pcard__name' }, p.name),
      h('span', { 'class': 'pcard__meta' }, clientById[p.client].name + ' · ' + p.status),
      h('span', { 'class': 'meter' },
        h('span', { 'class': 'meter__bar', 'aria-hidden': 'true' }, h('span', { style: { width: Math.round(progress.done / progress.total * 100) + '%' } })),
        h('span', null, progress.done + ' of ' + progress.total + ' checks')),
      next ? h('span', { 'class': 'pcard__next' }, 'Next: ' + next.title + ', ' + dueInline(next.date)) : null,
      h('span', { 'class': 'pcard__meta' }, first(p.lead) + ' leads · ' + plural(openCount, 'open task', 'open tasks')),
      p.illustrative ? h('span', { 'class': 'pcard__flag' }, 'Illustrative example') : null);
  }

  function board() {
    return h('div', { 'class': 'board-wrap' }, h('div', { 'class': 'board' }, STAGE_ORDER.map(function (key) {
      var stage = S.stages[key];
      var list = projects.filter(function (p) { return p.stage === key; });
      return h('section', { 'class': 'lane', 'aria-labelledby': 'lane-' + key },
        h('div', { 'class': 'lane__head' },
          h('h2', { 'class': 'lane__name', id: 'lane-' + key }, h('span', { 'class': 'mark mark--' + key, 'aria-hidden': 'true' }), stage.name),
          h('span', { 'class': 'count' }, String(list.length), h('span', { 'class': 'visually-hidden' }, list.length === 1 ? ' project' : ' projects'))),
        h('p', { 'class': 'lane__covers' }, stage.covers),
        list.length
          ? h('ul', { 'class': 'lane__list' }, list.map(function (p) { return h('li', null, projectCard(p)); }))
          : h('p', { 'class': 'lane__empty' }, 'Nothing in ' + stage.name + ' right now.'));
    })));
  }

  function viewProjects() {
    var discover = projects.filter(function (p) { return p.stage === 'discover'; }).length;
    return {
      title: 'Projects',
      sticky: 'new-task',
      nodes: [
        pageHead({
          title: 'Projects',
          sub: plural(activeProjects().length, 'active project', 'active projects') + ' and ' + discover + ' in Discover. Each stage ends at a gate both founders approve.'
        }),
        board()
      ]
    };
  }

  function nextStage(stage) {
    var i = STAGE_ORDER.indexOf(stage);
    return i >= 0 && i < STAGE_ORDER.length - 1 ? STAGE_ORDER[i + 1] : null;
  }

  function stageSection(p) {
    var stage = S.stages[p.stage];
    var checks = p.checks[p.stage] || (p.checks[p.stage] = new Set());
    var progress = checklistProgress(p);
    var d = p.decision ? decisionById[p.decision] : null;

    return section({ id: 'stage-title', title: stage.name + ' checklist', count: progress.done + ' of ' + progress.total },
      h('div', { 'class': 'gate' },
        h('p', { 'class': 'gate__label' }, 'Approval gate: ', gateText(stage.gate)),
        h('p', { 'class': 'gate__question' }, stage.question),
        h('p', { 'class': 'gate__note' }, 'Deliverable: ' + stage.deliverable + '. Both founders agree before the stage changes.'),
        d && d.status === 'open'
          ? h('p', { 'class': 'gate__note' }, h('a', { 'class': 'link', href: '#decisions' }, d.title), ' · ' + d.approvals.length + ' of 2 approved')
          : null),
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
      })));
  }

  function viewProject(id) {
    var p = projectById[id];
    var stage = S.stages[p.stage];
    var client = clientById[p.client];
    var projectTasks = tasks.filter(function (t) { return t.project === id; }).sort(byStatusThenDue);
    var openCount = projectTasks.filter(function (t) { return t.status !== 'done'; }).length;
    var docs = (p.docs || []).map(function (docId) { return docById[docId]; }).filter(Boolean);

    var about = section({ id: 'about-title', title: 'About' },
      h('dl', { 'class': 'about' },
        h('div', null, h('dt', null, 'What it is'), h('dd', null, p.summary)),
        h('div', null, h('dt', null, 'The problem'), h('dd', null, p.problem))));

    var taskSection = section({
      id: 'ptasks-title',
      title: 'Tasks',
      count: openCount + ' open',
      aside: h('button', { 'class': 'bf-btn bf-btn--secondary bf-btn--compact', type: 'button', 'data-new-task': p.id, 'data-key': 'new-task-' + p.id }, icon('plus'), 'Add task')
    }, projectTasks.length
      ? taskTable(projectTasks, { hideProject: true, caption: 'Tasks for ' + p.name })
      : emptyState('No tasks yet', 'Add the first one, with an owner, a deadline and an outcome.'));

    var details = section({ id: 'facts-title', title: 'Details' },
      h('dl', { 'class': 'facts' },
        fact('Client', client.name),
        fact('Lead', founderById[p.lead].name),
        fact('Solution line', p.line),
        fact('Budget', p.budget),
        fact('Open tasks', founders.map(function (f) {
          return f.first + ' ' + projectTasks.filter(function (t) { return t.owner === f.id && t.status !== 'done'; }).length;
        }).join(', '))));

    var dates = section({ id: 'dates-title', title: 'Milestones' },
      (p.milestones || []).length
        ? h('ul', { 'class': 'agenda' }, p.milestones.map(function (m) { return agendaItem(m.date, m.title); }))
        : emptyState('No milestones yet', 'Add the dates this project has agreed.'));

    var deliverables = section({ id: 'deliv-title', title: 'Deliverables' },
      h('ul', { 'class': 'bullets' }, p.deliverables.map(function (x) { return h('li', null, x); })));

    var files = section({ id: 'files-title', title: 'Files', aside: h('a', { 'class': 'link', href: '#knowledge' }, 'Knowledge') },
      docs.length
        ? h('ul', { 'class': 'list' }, docs.map(docRow))
        : emptyState('No files linked', 'Briefs, designs and technical documents for this project will show here.'));

    return {
      title: p.name,
      sticky: 'new-task',
      nodes: [
        pageHead({
          back: { href: '#projects', label: 'Projects' },
          kicker: client.name + ' · ' + p.line,
          title: p.name,
          sub: stage.name + ' stage · ' + p.status + (p.illustrative ? ' · Illustrative example' : '')
        }),
        h('div', { 'class': 'columns' },
          h('div', { 'class': 'columns__main' }, about, stageSection(p), taskSection),
          h('div', { 'class': 'columns__side' }, details, dates, deliverables, files))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     C · Tasks and founder responsibilities
     ------------------------------------------------------------------------ */

  var NO_FILTER = { owner: 'any', project: 'any', priority: 'any', due: 'any' };
  var filter = Object.assign({}, NO_FILTER);
  var updateDraftOpen = false;

  function filtering() {
    return Object.keys(NO_FILTER).some(function (k) { return filter[k] !== 'any'; });
  }

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

  function toolbar(shown) {
    return h('div', { 'class': 'toolbar', role: 'group', 'aria-label': 'Filter tasks' },
      selectField('flt-owner', 'Founder', filter.owner, [['any', 'Both founders']].concat(founders.map(function (f) {
        return [f.id, f.first + (f.id === me ? ' (you)' : '')];
      }))),
      selectField('flt-project', 'Project', filter.project, [['any', 'All projects']].concat(projects.map(function (p) { return [p.id, p.name]; }))),
      selectField('flt-priority', 'Priority', filter.priority, [['any', 'Any priority'], ['high', 'High'], ['medium', 'Medium'], ['low', 'Low']]),
      selectField('flt-due', 'Due', filter.due, [['any', 'Any time'], ['overdue', 'Overdue'], ['week', 'This week'], ['later', 'Later']]),
      h('div', { 'class': 'toolbar__end' },
        filtering() ? h('button', { 'class': 'bf-btn bf-btn--quiet bf-btn--compact', type: 'button', 'data-clear': '', 'data-key': 'clear-filters' }, 'Clear filters') : null,
        h('p', { 'class': 'toolbar__count' }, plural(shown, 'task', 'tasks'))));
  }

  function noMatches() {
    return emptyState('No tasks match these filters', 'Try another founder or project, or clear the filters.',
      h('button', { 'class': 'bf-btn bf-btn--secondary bf-btn--compact', type: 'button', 'data-clear': '', 'data-key': 'clear-filters-empty' }, 'Clear filters'));
  }

  function prioritiesSection() {
    var list = S.priorities.map(function (id) { return taskById[id]; }).filter(Boolean);
    return section({
      id: 'prio-title',
      title: 'This week’s priorities',
      cls: 'priorities-section',
      aside: h('p', { 'class': 'section__aside' }, 'Set on Monday ' + dayMonth(weekOf(TODAY)[0]))
    }, h('ol', { 'class': 'priorities' }, list.map(function (t, i) {
      return h('li', null, h('a', { 'class': 'priority', href: '#task-' + t.id },
        h('span', { 'class': 'priority__num', 'aria-hidden': 'true' }, String(i + 1)),
        h('span', { 'class': 'priority__body' },
          h('span', { 'class': 'priority__title' }, t.title),
          h('span', { 'class': 'item__meta' }, first(t.owner) + ' · due ' + dueInline(t.due)),
          taskState(t.status))));
    })));
  }

  // The shared task list as a table. Below 600px each row stacks: title, one
  // line of detail, and the status on the right.
  function taskTable(list, opts) {
    opts = opts || {};
    return h('div', { 'class': 'table-wrap' },
      h('table', { 'class': 'table table--stack' },
        h('caption', { 'class': 'visually-hidden' }, opts.caption || 'Tasks'),
        h('thead', null, h('tr', null,
          h('th', { scope: 'col' }, 'Task'),
          h('th', { scope: 'col', 'class': 'col-owner' }, 'Owner'),
          h('th', { scope: 'col', 'class': 'col-priority' }, 'Priority'),
          h('th', { scope: 'col', 'class': 'col-due' }, 'Due'),
          h('th', { scope: 'col', 'class': 'col-status' }, 'Status'))),
        h('tbody', null, list.map(function (t) {
          return h('tr', { 'data-href': 'task-' + t.id, 'data-find': 'task-' + t.id, 'class': t.status === 'done' ? 'is-done' : null },
            h('td', { 'data-cell': 'title' },
              h('a', { 'class': 'cell-link', href: '#task-' + t.id }, t.title),
              opts.hideProject ? null : h('span', { 'class': 'item__meta' }, projectById[t.project].name)),
            h('td', { 'data-cell': 'meta' }, first(t.owner)),
            h('td', { 'data-cell': 'meta' }, PRIORITY[t.priority]),
            h('td', { 'data-cell': 'meta' }, dueEl(t)),
            h('td', { 'data-cell': 'status' }, taskState(t.status)),
            h('td', { 'data-cell': 'mobile' }, first(t.owner) + ' · ' + PRIORITY[t.priority] + ' priority · ', dueEl(t)));
        }))));
  }

  function compactList(list, selectedId) {
    return h('ul', { 'class': 'list list--compact', 'aria-label': 'Tasks' }, list.map(function (t) {
      return h('li', null, h('a', {
        'class': 'citem' + (t.status === 'done' ? ' is-done' : ''),
        href: '#task-' + t.id,
        'aria-current': t.id === selectedId ? 'true' : null,
        'data-find': 'task-' + t.id
      },
        h('span', { 'class': 'citem__title' }, t.title),
        dueEl(t),
        h('span', { 'class': 'citem__meta' }, projectById[t.project].name + ' · ' + first(t.owner)),
        taskState(t.status)));
    }));
  }

  function taskDetail(t) {
    var p = projectById[t.project];
    var stage = S.stages[p.stage];
    var due = parseDate(t.due);
    var diff = dayDiff(TODAY, due);
    var dueWords = shortDate(due);
    if (t.status !== 'done') {
      if (diff < 0) dueWords = 'Overdue, was due ' + shortDate(due);
      else if (diff === 0) dueWords = 'Today, ' + shortDate(due);
      else if (diff === 1) dueWords = 'Tomorrow, ' + shortDate(due);
    }
    var checklist = t.checklist || [];
    var ticked = checklist.filter(function (c) { return c.done; }).length;

    return h('article', { 'class': 'detail', 'aria-labelledby': 'detail-title' },
      h('a', { 'class': 'link detail-back', href: '#tasks' }, icon('chevron-left', 'arrow'), 'All tasks'),
      h('header', { 'class': 'detail__head' },
        h('a', { 'class': 'detail__project', href: '#project-' + p.id }, p.name + ' · ' + stage.name),
        h('h2', { 'class': 'detail__title', id: 'detail-title' }, t.title),
        statusBadge(t.status)),
      t.status === 'blocked' && t.blockedBy
        ? h('p', { 'class': 'blocked-note' }, icon('triangle-alert'), h('span', null, h('strong', null, 'Blocked. '), t.blockedBy))
        : null,
      h('dl', { 'class': 'facts' },
        fact('Owner', founderById[t.owner].name),
        fact('Due', dueWords),
        fact('Priority', PRIORITY[t.priority]),
        fact('Outcome', t.outcome),
        fact('Stage gate', [gateText(stage.gate), h('span', { 'class': 'facts__sub' }, stage.question)])),
      checklist.length ? h('section', { 'class': 'detail__section', 'aria-labelledby': 'tchk-title' },
        h('h3', { id: 'tchk-title' }, 'Checklist', h('span', { 'class': 'count' }, ticked + ' of ' + checklist.length)),
        h('ul', null, checklist.map(function (item, i) {
          var id = 'tchk-' + t.id + '-' + i;
          return h('li', null, h('label', { 'class': 'check' + (item.done ? ' check--done' : ''), 'for': id },
            h('input', { type: 'checkbox', id: id, 'data-task-check': t.id + '|' + i, 'data-key': id, checked: item.done }),
            h('span', null, item.text)));
        }))) : null,
      h('div', { 'class': 'detail__controls' },
        h('div', { 'class': 'field' },
          h('label', { 'class': 'field__label', 'for': 'task-status' }, 'Status'),
          h('div', { 'class': 'select' },
            h('select', { 'class': 'input', id: 'task-status', 'data-status-for': t.id, 'data-key': 'task-status' },
              Object.keys(STATUS).map(function (k) { return h('option', { value: k, selected: k === t.status }, STATUS[k][1]); })),
            icon('chevron-down'))),
        t.status !== 'done'
          ? h('button', { 'class': 'bf-btn bf-btn--secondary js-mark-done', type: 'button', 'data-done': t.id, 'data-key': 'done-' + t.id }, 'Mark as done')
          : null,
        h('button', { 'class': 'bf-btn bf-btn--quiet', type: 'button', 'data-toggle-update': t.id, 'data-key': 'toggle-update', 'aria-expanded': String(updateDraftOpen) }, 'Add update')),
      updateDraftOpen ? h('form', { 'class': 'update-form', 'data-update-for': t.id, novalidate: true },
        h('div', { 'class': 'field' },
          h('label', { 'class': 'field__label', 'for': 'update-text' }, 'Progress update'),
          h('textarea', { 'class': 'input', id: 'update-text', rows: '3', maxlength: '280', 'aria-describedby': 'update-hint update-error', 'data-key': 'update-text' }),
          h('p', { 'class': 'field__hint', id: 'update-hint' }, 'Done, next, blocked. Short and specific.'),
          h('p', { 'class': 'field__error', id: 'update-error', hidden: true })),
        h('div', null, h('button', { 'class': 'bf-btn bf-btn--secondary', type: 'submit', 'data-key': 'post-update' }, 'Post update'))) : null,
      h('section', { 'class': 'detail__section', 'aria-labelledby': 'updates-title' },
        h('h3', { id: 'updates-title' }, 'Updates'),
        t.updates.length
          ? h('ol', { 'class': 'updates' }, t.updates.map(function (u) {
            return h('li', null, h('span', { 'class': 'item__meta' }, first(u.by) + ' · ' + u.when), h('span', null, u.text));
          }))
          : h('p', { 'class': 'muted' }, 'No updates yet. Wednesday’s written update goes here: done, next, blocked.')));
  }

  function viewTasks(selectedId) {
    var shown = filteredTasks();
    var selected = selectedId ? taskById[selectedId] : null;
    var open = openTasks();
    var week = open.filter(function (t) { return inThisWeek(t.due); }).length;

    var body;
    if (selected) {
      body = h('div', { 'class': 'split' },
        h('div', { 'class': 'split__list' }, shown.length ? compactList(shown, selectedId) : noMatches()),
        h('div', { 'class': 'split__detail' }, taskDetail(selected)));
    } else {
      body = shown.length ? taskTable(shown, { caption: 'Shared task list' }) : noMatches();
    }

    return {
      title: selected ? selected.title : 'Tasks',
      sticky: selected && selected.status !== 'done' ? 'mark-done' : (selected ? null : 'new-task'),
      stickyTask: selected ? selected.id : null,
      nodes: [
        pageHead({ title: 'Tasks', sub: plural(open.length, 'open task', 'open tasks') + ', ' + week + ' due this week' }),
        h('div', { 'class': 'tasks-view', 'data-has-detail': selected ? 'true' : 'false' },
          prioritiesSection(),
          h('section', { 'class': 'section tasklist', 'aria-labelledby': 'tasklist-title' },
            h('div', { 'class': 'section__head' }, h('h2', { 'class': 'section__title', id: 'tasklist-title' }, 'Shared task list')),
            toolbar(shown.length),
            body))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     D · Clients and opportunities
     ------------------------------------------------------------------------ */

  var openHistory = new Set();

  function clientRows(c, closed) {
    var isOpen = openHistory.has(c.id);
    var soon = c.followUp && dayDiff(TODAY, parseDate(c.followUp)) <= 1;
    var outcome = c.deal === 'Won' ? state('ok', 'Won') : state('neutral', c.deal);
    var mobile = [c.source, 'Proposal: ' + c.proposal];
    if (closed) mobile.push('Discovery ' + STATUS[c.discovery][1].toLowerCase());
    else if (c.followUp) mobile.push('Follow up ' + dueInline(c.followUp));
    mobile.push(first(c.owner));

    return [
      h('tr', { 'data-find': 'client-' + c.id, 'class': isOpen ? 'is-open' : null },
        h('td', { 'data-cell': 'title' },
          h('span', { 'class': 'cell-title' }, c.name),
          c.illustrative ? h('span', { 'class': 'item__meta' }, 'Illustrative example') : null),
        h('td', { 'data-cell': 'meta', 'class': 'col-source' }, c.source),
        h('td', { 'data-cell': closed ? 'meta' : 'status' }, taskState(c.discovery)),
        h('td', { 'data-cell': 'meta' }, c.proposal),
        closed
          ? h('td', { 'data-cell': 'status' }, outcome)
          : h('td', { 'data-cell': 'meta' }, h('span', { 'class': 'due' + (soon ? ' due--soon' : '') }, c.followUp ? dueText(c.followUp) : 'None')),
        h('td', { 'data-cell': 'meta' }, first(c.owner)),
        h('td', { 'data-cell': 'mobile' }, mobile.join(' · ')),
        h('td', { 'data-cell': 'action' },
          h('button', {
            'class': 'disclosure',
            type: 'button',
            'aria-expanded': String(isOpen),
            'aria-controls': 'hist-' + c.id,
            'data-history': c.id,
            'data-key': 'hist-' + c.id
          }, 'Notes', h('span', { 'class': 'count' }, String(c.history.length)), icon('chevron-down')))),
      h('tr', { 'class': 'history-row', id: 'hist-' + c.id, hidden: !isOpen },
        h('td', { colspan: '7' },
          h('ol', { 'class': 'history', 'aria-label': 'Notes and history for ' + c.name }, c.history.map(function (e) {
            return h('li', null, h('span', { 'class': 'item__meta' }, dayMonth(parseDate(e.date))), h('span', null, e.text));
          }))))
    ];
  }

  function clientTable(list, closed) {
    return h('div', { 'class': 'table-wrap' },
      h('table', { 'class': 'table table--stack clients-table' },
        h('caption', { 'class': 'visually-hidden' }, closed ? 'Closed opportunities' : 'Open opportunities'),
        h('thead', null, h('tr', null,
          h('th', { scope: 'col' }, 'Client'),
          h('th', { scope: 'col', 'class': 'col-source' }, 'Lead source'),
          h('th', { scope: 'col', 'class': 'col-discovery' }, 'Discovery'),
          h('th', { scope: 'col', 'class': 'col-proposal' }, 'Proposal'),
          h('th', { scope: 'col', 'class': 'col-next' }, closed ? 'Outcome' : 'Next follow-up'),
          h('th', { scope: 'col', 'class': 'col-owner' }, 'Owner'),
          h('th', { scope: 'col', 'class': 'col-notes' }, h('span', { 'class': 'visually-hidden' }, 'Notes')))),
        h('tbody', null, list.map(function (c) { return clientRows(c, closed); }))));
  }

  function viewClients() {
    var list = clients.filter(function (c) { return !c.internal; });
    var open = list.filter(function (c) { return c.deal === 'Open'; })
      .sort(function (a, b) { return (a.followUp || '9999').localeCompare(b.followUp || '9999'); });
    var closed = list.filter(function (c) { return c.deal !== 'Open'; });
    var won = closed.filter(function (c) { return c.deal === 'Won'; }).length;
    return {
      title: 'Clients and opportunities',
      nodes: [
        pageHead({
          title: 'Clients and opportunities',
          sub: open.length + ' open, ' + won + ' won and ' + (closed.length - won) + ' lost. Potential revenue isn’t recorded for any of them yet.'
        }),
        section({ id: 'open-title', title: 'Open', count: open.length }, clientTable(open, false)),
        closed.length ? section({ id: 'closed-title', title: 'Closed', count: closed.length }, clientTable(closed, true)) : null
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
        pageHead({ title: 'Finance and business health', sub: 'Real records only. Nothing here is estimated.' }),
        h('div', { 'class': 'notice' },
          h('h2', { 'class': 'notice__title' }, 'No figures yet'),
          h('p', null, 'These six figures will come from the finance sheet in Google Sheets once it’s linked. Until then they stay empty.'),
          task ? h('p', { 'class': 'notice__task' },
            h('a', { 'class': 'link', href: '#task-' + task.id }, task.title),
            h('span', { 'class': 'muted' }, ' · ' + first(task.owner) + ', due ' + dueInline(task.due))) : null),
        h('dl', { 'class': 'ledger', 'aria-label': 'Finance indicators' }, S.finance.map(function (f) {
          return h('div', { 'class': 'ledger__row' },
            h('dt', null, h('span', { 'class': 'ledger__name' }, f.name), h('span', { 'class': 'item__meta' }, f.purpose)),
            h('dd', { 'class': 'ledger__value' }, h('span', { 'aria-hidden': 'true' }, '—'), h('span', { 'class': 'visually-hidden' }, 'No figure yet')));
        }))
      ]
    };
  }

  /* ------------------------------------------------------------------------
     F · Decisions and shared knowledge
     ------------------------------------------------------------------------ */

  function decisionTable() {
    return h('div', { 'class': 'table-wrap' },
      h('table', { 'class': 'table table--stack' },
        h('caption', { 'class': 'visually-hidden' }, 'Decision log: what was decided, by whom and when'),
        h('thead', null, h('tr', null,
          h('th', { scope: 'col' }, 'Decision'),
          h('th', { scope: 'col', 'class': 'col-by' }, 'Who and when'),
          h('th', { scope: 'col', 'class': 'col-state' }, 'Status'))),
        h('tbody', null, sortedDecisions().map(function (d) {
          var who = d.status === 'decided' ? d.by : 'Both founders';
          var when = d.status === 'decided' ? dayMonth(parseDate(d.decidedOn)) : 'Due ' + dueInline(d.due);
          return h('tr', { 'data-find': 'decision-' + d.id },
            h('td', { 'data-cell': 'title' }, h('span', { 'class': 'cell-title' }, d.title), h('span', { 'class': 'item__meta' }, d.context)),
            h('td', { 'data-cell': 'meta' }, who, h('span', { 'class': 'item__meta cell-sub' }, when)),
            h('td', { 'data-cell': 'status' }, decisionState(d)),
            h('td', { 'data-cell': 'mobile' }, who + ' · ' + when));
        }))));
  }

  function viewDecisions() {
    var pending = pendingDecisions().length;
    return {
      title: 'Decisions',
      nodes: [
        pageHead({ title: 'Decisions', sub: pending + ' open and ' + (decisions.length - pending) + ' decided. What was decided, by whom and when.' }),
        h('div', { 'class': 'columns' },
          h('div', { 'class': 'columns__main' },
            bothFounders(),
            section({ id: 'declog-title', title: 'Decision log' }, decisionTable())),
          h('div', { 'class': 'columns__side' },
            section({ id: 'meet-title', title: 'Meeting notes' },
              h('ul', { 'class': 'agenda' }, S.meetings.map(function (m) {
                return agendaItem(m.date, m.title, m.note + ' ' + plural(m.actions, 'action item', 'action items') + '.');
              })))))
      ]
    };
  }

  function docRow(doc) {
    return h('li', { 'data-find': 'doc-' + doc.id }, h('div', { 'class': 'item' },
      h('span', { 'class': 'item__main' },
        h('span', { 'class': 'item__title' }, doc.secure ? icon('lock', 'inline-icon') : null, doc.title),
        h('span', { 'class': 'item__meta' }, doc.where + (doc.secure ? ' · reference only' : '') + ' · updated ' + dayMonth(parseDate(doc.updated))))));
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
        pageHead({ title: 'Knowledge', sub: 'Where things live, so work carries on when one of you is away. Titles and places only: passwords never go here.' }),
        h('div', { 'class': 'kgrid' }, groups.map(function (g, i) {
          return section({ id: 'kg-' + i, title: g.name, count: g.docs.length }, h('ul', { 'class': 'list' }, g.docs.map(docRow)));
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
        pageHead({ title: 'More' }),
        h('ul', { 'class': 'list more-list', 'aria-label': 'More sections' }, items.map(function (it) {
          return h('li', null, h('a', { 'class': 'item item--nav', href: '#' + it[0] },
            icon(it[1], 'item__icon'),
            h('span', { 'class': 'item__main' }, h('span', { 'class': 'item__title' }, it[2]), h('span', { 'class': 'item__meta' }, it[3])),
            icon('chevron-right', 'item__icon')));
        })),
        section({ id: 'more-viewer-title', title: 'Viewing as' }, viewerButtons('more'))
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
      }, avatar(f), h('span', { 'class': 'viewer__name' }, f.first));
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
      sticky.append(h('button', { 'class': 'bf-btn bf-btn--primary', type: 'button', 'data-done': view.stickyTask, 'data-key': 'sticky-done' }, 'Mark as done'));
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
    main.replaceChildren.apply(main, view.nodes.filter(Boolean).concat(pageFoot()));
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

  // A found item is marked for a moment: rows get a tint, everything else a ring.
  function highlight(findKey) {
    var target = document.querySelector('[data-find="' + findKey + '"]');
    if (!target) return;
    var cls = target.tagName === 'TR' ? 'is-found-row' : 'is-found';
    target.scrollIntoView({ block: 'center', behavior: 'auto' });
    target.classList.remove(cls);
    void target.offsetWidth;
    target.classList.add(cls);
  }

  var pendingFind = null;

  window.addEventListener('hashchange', function () {
    var next = parseRoute();
    if (!next) return;
    var sameTasks = route.view === 'tasks' && next.view === 'tasks';
    route = next;
    updateDraftOpen = false;
    var wide = window.matchMedia('(min-width: 1100px)').matches;
    var keepPlace = sameTasks && wide && !!next.task;
    render({ top: !keepPlace, focusMain: !keepPlace, find: pendingFind });
    if (keepPlace) {
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
    var el = ev.target.closest('[data-approve], [data-done], [data-viewer], [data-clear], [data-new-task], [data-toggle-update], [data-scope], [data-history]');
    if (!el) {
      // A whole table row opens its task; the title link is the keyboard route.
      var row = ev.target.closest('tr[data-href]');
      var selecting = window.getSelection && String(window.getSelection()).length > 0;
      if (row && !selecting && !ev.target.closest('a, button, input, select, textarea, label')) go(row.getAttribute('data-href'));
      return;
    }
    if (el.hasAttribute('data-approve')) approve(el.getAttribute('data-approve'));
    else if (el.hasAttribute('data-done')) markDone(el.getAttribute('data-done'));
    else if (el.hasAttribute('data-viewer')) setViewer(el.getAttribute('data-viewer'));
    else if (el.hasAttribute('data-new-task')) openTaskDialog(el.getAttribute('data-new-task') || null, el);
    else if (el.hasAttribute('data-toggle-update')) {
      updateDraftOpen = !updateDraftOpen;
      render();
      if (updateDraftOpen) { var box = $('update-text'); if (box) box.focus(); }
    } else if (el.hasAttribute('data-scope')) {
      filter = Object.assign({}, NO_FILTER, { owner: me });
    } else if (el.hasAttribute('data-clear')) {
      filter = Object.assign({}, NO_FILTER);
      render();
      // The button that was pressed is gone now, so focus moves to the first filter.
      var ownerSelect = document.querySelector('[data-key="flt-owner"]');
      if (ownerSelect && !document.activeElement.closest('.toolbar')) ownerSelect.focus({ preventScroll: true });
    } else if (el.hasAttribute('data-history')) {
      var cid = el.getAttribute('data-history');
      if (openHistory.has(cid)) openHistory.delete(cid); else openHistory.add(cid);
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
      render();
    } else if (el.hasAttribute('data-task-check')) {
      var tp = el.getAttribute('data-task-check').split('|');
      taskById[tp[0]].checklist[Number(tp[1])].done = el.checked;
      render();
    } else if (el.hasAttribute('data-status-for')) {
      var t = taskById[el.getAttribute('data-status-for')];
      t.status = el.value;
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
      h('label', { 'for': 'f-owner-' + f.id }, f.first)
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
    var firstBad = null;

    var titleText = title.value.trim();
    var titleMsg = '';
    if (!titleText) titleMsg = 'Write what will be done.';
    else if (VAGUE.test(titleText) || titleText.split(/\s+/).length < 3) titleMsg = 'Make it specific. Say what will be done, like “Draft pilot scope for coordinators”, not “work on app”.';
    fieldError(title, $('f-title-error'), showAll || touched.title ? titleMsg : '');
    if (titleMsg && !firstBad) firstBad = title;

    var outcomeMsg = outcome.value.trim() ? '' : 'Say what done looks like, so the owner knows when to stop.';
    fieldError(outcome, $('f-outcome-error'), showAll || touched.outcome ? outcomeMsg : '');
    if (outcomeMsg && !firstBad) firstBad = outcome;

    var dueMsg = '';
    if (!due.value) dueMsg = 'Pick a due date.';
    else if (dayDiff(TODAY, parseDate(due.value)) < 0) dueMsg = 'Pick today or a later date.';
    fieldError(due, $('f-due-error'), showAll || touched.due ? dueMsg : '');
    if (dueMsg && !firstBad) firstBad = due;

    if (focusFirst && firstBad) firstBad.focus();
    return !firstBad;
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
    dialog.close();
    toast('Task added for ' + first(t.owner) + ', due ' + dueInline(t.due) + '.');
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
      items.push({ icon: 'list-todo', title: t.title, meta: 'Task · ' + first(t.owner) + ' · ' + STATUS[t.status][1], text: t.title + ' ' + t.outcome, hash: 'task-' + t.id, find: 'task-' + t.id });
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
      : [h('p', { 'class': 'search__empty', role: 'status' }, 'Nothing matches “' + searchInput.value.trim() + '”. Try a project, client or task.')]);
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
})();
