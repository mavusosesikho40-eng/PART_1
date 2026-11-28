# Founder Command Centre

Baseflow's internal dashboard: one central workspace for visibility, accountability and
business health, as described in section 05 of the **Founder playbook v1.0**. It is built to
the **Brand guidelines v2.0** (November 2026), following their Product UX/UI section: the
app shell, the device frames, the component specs and the status words.

It answers three questions first: what needs my attention, what am I responsible for, and
what is happening in Baseflow.

This is a **concept with sample data**. The records in `js/sample-data.js` follow the
examples in the guidelines and the playbook. They are not business results. Changes you make
on screen last for the visit only.

## Running it

No build step and no dependencies. Open `index.html` in a browser, or serve the folder:

```
python3 -m http.server 8000
```

then visit `http://localhost:8000`.

## What's in it

| Section | What it shows | Playbook |
| --- | --- | --- |
| **Overview** | Today's date and one sentence on what is waiting on you, then the business health line: active projects, open opportunities, outstanding tasks, pending decisions and the financial snapshot. Below: what needs both founders, my tasks and the pipeline, with what's coming up and the decision log beside them. | A |
| **Projects** | A board by stage, Discover, Design and Develop. Each project opens a workspace: what it is and the problem, the current stage's checklist and approval gate, its tasks, then details, milestones, deliverables and files. | B |
| **Tasks** | This week's three priorities, then the shared task list as a table, filterable by founder, project, priority and due date. Opening a task shows its owner, due date, outcome, stage gate, checklist, status and written updates. | C |
| **Clients** | Open and closed opportunities in two tables: lead source, discovery, proposal, next follow-up or outcome, owner, and notes that open under each row. Potential revenue isn't recorded yet, and the page says so. | D |
| **Finance** | Revenue, expenses, outstanding invoices, project costs, cash position and recurring revenue. They stay empty until the finance sheet is linked: real records only, nothing estimated. | E |
| **Decisions** | Decisions that need both founders, the decision log, and meeting notes with action items. | F |
| **Knowledge** | Procedures, client briefs and proposals, technical documents and brand files, with where each one lives. Credentials are references only. | F |

Things you can do:

- **New task** (the screen's one primary action). The form asks for one owner, a deadline
  and a clear outcome, and turns away vague tasks like "work on app".
- **Approve** a decision. When both founders have approved a stage gate, the project moves
  on: approving the FSY pilot scope moves the FSY Companion App from Design to Develop.
- Tick checklist items, change a task's status, mark it done, or post a progress update
  (done, next, blocked).
- **Viewing as** switches between Liyema and Sesikho, so "My tasks" and approvals follow
  the founder.
- Search projects, tasks, clients, decisions and notes from the top bar.
- **Floor mode**, the brand's dark theme, follows the device setting until you pick one.

## How it follows the guidelines

- **App shell:** a 240px sidebar from 1280px wide, an 84px rail from 600px, and a bottom
  tab bar with five tabs on phones (Overview, Projects, Tasks, Clients, More). A 64px top bar
  holds search, the date and the primary action. On phones the primary action sits above the
  tabs, full width. Content is at most 1280px wide, centred.
- **Device sizes:** page titles, card titles, metrics, margins, card padding, gaps, button
  and input heights and icon sizes follow "Type, spacing and touch by device" for phone,
  tablet and laptop.
- **Responsive behaviour:** side panels move under the main content, the board and
  pipeline become lists, tables stack into rows below 768px, the task detail opens over the
  list below 1100px, and the dialog becomes a bottom sheet on phones.
- **Components:** buttons, inputs (label above, 2px focus and error borders), the status
  badge (28px, a dot in the text's colour, one or two words), navigation items and the
  bottom sheet follow "Core component specs".
- **Status words:** Not started, In progress, Blocked and Done for tasks; Open and Decided
  for decisions; Concept and Illustrative to label what's shown.
- **Tokens only:** colour, type, space, radius and shadow come from the brand tokens.
  Lexend for headings, Atkinson Hyperlegible for everything read or counted, Lucide icons.
- **Honesty:** every page ends with a note that it's a concept with sample data. There are
  no invented money figures.

## How the pages are laid out

The pages are written rather than boxed:

- Sections are set apart by a heading, space and hairline rules, not by a card around each
  one. The navigation sits on the canvas tint and the work sits on a plain surface.
- One tinted block per screen marks what can't move without both founders: the decisions
  waiting on approval, or a stage's approval gate.
- Lists and tables keep their frame: a rule above, a rule under every row, aligned columns.
- In lists and tables a status is a dot and a word. Blocked stands out, Done steps back,
  and the full badge is kept for the task you have open.
- People are named, not shown as initials, except in the viewer switch.
- Icons appear where they do a job: navigation, opening and closing, errors, and the lock on
  the credentials note.
- **States:** empty states say what will appear and offer a next step; errors say what went
  wrong and how to fix it; an offline banner appears if the connection drops.

## Showing real records

Everything starts from `js/sample-data.js`: founders, stages and their checklists,
projects, tasks, priorities, clients, decisions, meetings, knowledge and the finance
indicators. Replace those records, or load the same shape from your Notion workspace or
finance sheet, and every view follows.

## Files

```
index.html          The app shell, icon sprite and New task dialog
css/dashboard.css   Brand tokens, device sizes, shell, components and layouts
js/sample-data.js   The sample records
js/dashboard.js     Views, routing, actions, search and Floor mode
fonts/              Lexend and Atkinson Hyperlegible, with their licences
images/             Baseflow wordmark and app icon from the brand kit
```

## Licences

Lexend and Atkinson Hyperlegible are under the SIL Open Font License 1.1 (see `fonts/`).
Lucide icons are under the ISC licence. The Baseflow logo and app icon belong to Baseflow
Technologies (Pty) Ltd.
