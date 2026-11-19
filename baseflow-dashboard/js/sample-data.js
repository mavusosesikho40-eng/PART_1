/* Sample records for the Founder Command Centre.

   Everything on screen starts here. These are sample records, shaped like the
   examples in the Brand guidelines v2.0 and the Founder playbook. They are not
   business results. Per the playbook, figures should come from real records:
   there are no money figures here, so Finance shows what to connect instead.

   Dates are YYYY-MM-DD. "today" and "now" fix the sample's day and starting time;
   the clock then runs in real time. */

window.BASEFLOW_SAMPLE = {
  today: '2026-11-19',
  now: '09:12',

  founders: [
    { id: 'lf', name: 'Liyema Fandeso', first: 'Liyema', initials: 'LF' },
    { id: 'sm', name: 'Sesikho Mavuso', first: 'Sesikho', initials: 'SM' }
  ],
  me: 'sm',

  // The Baseflow Way: one checklist per stage, from the playbook.
  stages: {
    discover: {
      name: 'Discover',
      covers: 'New app ideas and opportunities',
      deliverable: 'Validated project concept',
      gate: 'Discover → Design',
      question: 'Do we understand and validate the problem?',
      groups: [
        { name: 'Problem', items: ['What problem are we trying to solve?', 'Why does this problem exist?', 'How are people solving it today?', 'What makes current solutions inadequate?', 'Is this a problem worth solving?'] },
        { name: 'People', items: ['Who experiences the problem?', 'Who will use the application?', 'Who will pay for it?', 'What do these users actually need?'] },
        { name: 'Opportunity', items: ['What value will the solution create?', 'Is there evidence of demand?', 'Is an app the best solution?', 'What makes the opportunity viable?'] }
      ]
    },
    design: {
      name: 'Design',
      covers: 'Projects being scoped and prototyped',
      deliverable: 'Approved solution blueprint',
      gate: 'Design → Develop',
      question: 'Do we agree on exactly what we are building?',
      groups: [
        { name: 'Product definition', items: ['What exactly will the application do?', 'What are its core features?', 'What can be excluded from the first version?', 'What does success look like?'] },
        { name: 'User experience', items: ['How will users interact with the application?', 'What should the user journey look like?', 'Have key screens been mapped?', 'Have wireframes or a prototype been prepared?', 'Have accessibility and usability been considered?'] },
        { name: 'Technical planning', items: ['Is the proposed solution technically feasible?', 'What technology and integrations are required?', 'What data will be collected or processed?', 'What security and privacy requirements apply?', 'What are the estimated development cost and timeline?'] },
        { name: 'Project agreement', items: ['Has the scope been agreed?', 'Are deliverables defined?', 'Has the client approved the proposal?', 'Are payment terms agreed?'] }
      ]
    },
    develop: {
      name: 'Develop',
      covers: 'Projects being built and tested',
      deliverable: 'Working product',
      gate: 'Develop → Complete',
      question: 'Does the product work and meet the agreed requirements?',
      groups: [
        { name: 'Build', items: ['Development environment established.', 'Core features developed.', 'User interface implemented.', 'Required integrations completed.', 'Progress reviewed against agreed scope.'] },
        { name: 'Test', items: ['Core functionality and user journeys tested.', 'Bugs and errors resolved.', 'Security and data handling reviewed.', 'Client or selected users have tested the application.', 'Feedback incorporated.'] },
        { name: 'Launch', items: ['Application deployed.', 'Domain and hosting configured, where required.', 'User access and onboarding established.', 'Client handover completed.', 'Documentation provided.'] },
        { name: 'Improve', items: ['User feedback collected.', 'Performance monitored.', 'Improvements prioritised.', 'Maintenance and support arrangements agreed.', 'Future development opportunities identified.'] }
      ]
    }
  },

  // Project workspaces. `done` lists checklist items ticked, as "group:item" indexes.
  projects: [
    {
      id: 'fsy', name: 'FSY Companion App', client: 'fsy', stage: 'design', status: 'Proposal sent',
      lead: 'lf', line: 'Live Operations',
      summary: 'One app for youth, counsellors and coordinators: check-ins, headcounts and the day’s programme.',
      problem: 'Coordinators collect headcounts on paper and in group chats, so counts reach them late.',
      budget: 'In the proposal. Not agreed yet.',
      deliverables: ['Approved solution blueprint', 'Pilot for one programme week'],
      done: ['0:0', '0:1', '0:3', '1:0', '1:1', '1:2', '1:3', '2:0', '3:1'],
      decision: 'd1',
      milestones: [
        { title: 'FSY proposal follow-up', date: '2026-11-20' },
        { title: 'Pilot scope agreed', date: '2026-11-23' },
        { title: 'Pilot week starts', date: '2026-12-07' }
      ],
      docs: ['k4', 'k7']
    },
    {
      id: 'tutoring', name: 'Tutoring payments flow', client: 'tutorhq', stage: 'discover', status: 'Problem interviews',
      lead: 'lf', line: 'Connected Workflows', illustrative: true,
      summary: 'One connected invoice-and-payment process for a tutoring business.',
      problem: 'Two tools that don’t fit: more than ten minutes per invoice and another ten to reconcile each payment (founding notes).',
      budget: 'Not discussed. Discover comes first.',
      deliverables: ['Validated project concept'],
      done: ['0:0', '0:1', '0:2', '1:0'],
      milestones: [
        { title: 'Second problem interview', date: '2026-11-24' }
      ],
      docs: ['k5']
    },
    {
      id: 'kiosk', name: 'Event check-in kiosk', client: 'venue', stage: 'design', status: 'Wireframes',
      lead: 'sm', line: 'Live Operations',
      summary: 'Self check-in at a front desk or gate: scan a code, confirm, move on.',
      problem: 'Paper visitor books slow the queue at peak arrival and get lost afterwards.',
      budget: 'Estimate due before the Design gate.',
      deliverables: ['Kiosk wireframes', 'Cost and timeline estimate'],
      done: ['0:0', '0:1', '1:0', '1:2'],
      milestones: [
        { title: 'Kiosk wireframes tested', date: '2026-11-25' }
      ],
      docs: ['k6']
    },
    {
      id: 'fcc', name: 'Founder Command Centre', client: 'baseflow', stage: 'develop', status: 'Build · internal',
      lead: 'sm', line: 'Business Visibility',
      summary: 'One central workspace for visibility, accountability and business health.',
      problem: 'Projects, tasks, decisions and finances sit in separate places, so the weekly review starts with hunting for facts.',
      budget: 'Internal. Uses tools we already pay for.',
      deliverables: ['Working dashboard on real records'],
      done: ['0:0', '0:2', '0:4'],
      milestones: [
        { title: 'Monthly review', date: '2026-11-27' }
      ],
      docs: ['k1', 'k8']
    }
  ],

  // One owner, a deadline and a clear outcome for every task.
  tasks: [
    { id: 't1', title: 'Follow up FSY coordinators', project: 'fsy', owner: 'sm', status: 'progress', due: '2026-11-19', priority: 'high', outcome: 'Coordinators confirm the pilot dates and group sizes.' },
    { id: 't2', title: 'Send proposal follow-up', project: 'fsy', owner: 'lf', status: 'progress', due: '2026-11-20', priority: 'high', outcome: 'FSY has the revised proposal and a call booked to approve it.' },
    { id: 't3', title: 'Map FSY check-in journey', project: 'fsy', owner: 'sm', status: 'progress', due: '2026-11-20', priority: 'high',
      outcome: 'Screens for arrival, scan, confirmation and help, approved by Liyema.',
      checklist: [
        { text: 'Key screens mapped', done: true },
        { text: 'Wireframes reviewed with a coordinator', done: true },
        { text: 'Accessibility and usability checked', done: false },
        { text: 'Prototype tested with two counsellors', done: false }
      ] },
    { id: 't4', title: 'Book TutorHQ problem interview', project: 'tutoring', owner: 'lf', status: 'not-started', due: '2026-11-20', priority: 'medium', outcome: 'A second interview booked with the person who reconciles payments.' },
    { id: 't5', title: 'Draft pilot scope for coordinators', project: 'fsy', owner: 'lf', status: 'not-started', due: '2026-11-23', priority: 'high', outcome: 'A one-page scope both founders can approve.' },
    { id: 't6', title: 'Confirm hosting approach', project: 'fsy', owner: 'sm', status: 'blocked', due: '2026-11-24', priority: 'medium', outcome: 'Hosting option chosen, with cost and data location agreed.', blockedBy: 'Waiting on FSY’s data and privacy requirements.' },
    { id: 't7', title: 'Test kiosk wireframes with front desk staff', project: 'kiosk', owner: 'sm', status: 'not-started', due: '2026-11-25', priority: 'medium', outcome: 'Three front desk staff complete check-in on the prototype without help.' },
    { id: 't8', title: 'Estimate kiosk build cost and timeline', project: 'kiosk', owner: 'lf', status: 'not-started', due: '2026-11-26', priority: 'medium', outcome: 'A cost and timeline both founders agree before the Design gate.' },
    { id: 't9', title: 'Link the finance sheet to the Command Centre', project: 'fcc', owner: 'lf', status: 'not-started', due: '2026-11-27', priority: 'medium', outcome: 'The financial snapshot shows real figures from the finance sheet.' },
    { id: 't10', title: 'Write handover checklist', project: 'fcc', owner: 'lf', status: 'not-started', due: '2026-11-27', priority: 'low', outcome: 'A checklist we use at every client handover.' },
    { id: 't11', title: 'Prepare monthly review', project: 'fcc', owner: 'sm', status: 'not-started', due: '2026-11-27', priority: 'medium', outcome: 'Finances, pipeline, active projects and strategy reviewed in 45 minutes.' },
    { id: 't12', title: 'Summarise TutorHQ interview findings', project: 'tutoring', owner: 'lf', status: 'not-started', due: '2026-12-02', priority: 'low', outcome: 'A one-page problem summary for the Discover gate.' },
    { id: 't13', title: 'Wireframe coordinator dashboard', project: 'fsy', owner: 'sm', status: 'done', due: '2026-11-18', priority: 'high', outcome: 'Coordinator dashboard wireframes reviewed by Liyema.' },
    { id: 't14', title: 'Set up Notion workspace', project: 'fcc', owner: 'lf', status: 'done', due: '2026-11-12', priority: 'medium', outcome: 'Projects, tasks and decisions live in one Notion workspace.' },
    { id: 't15', title: 'Draft FSY proposal', project: 'fsy', owner: 'sm', status: 'done', due: '2026-11-11', priority: 'high', outcome: 'Proposal sent to FSY coordinators.' }
  ],

  // Monday: the three most important priorities, each with an owner.
  priorities: ['t2', 't3', 't5'],

  clients: [
    { id: 'fsy', name: 'FSY programme', source: 'Referral', discovery: 'done', proposal: 'Sent 12 Nov', deal: 'Open', owner: 'lf', followUp: '2026-11-20',
      history: [
        { date: '2026-11-12', text: 'Proposal sent to the coordinators.' },
        { date: '2026-11-06', text: 'Discovery call: headcounts, check-ins and the day’s programme.' },
        { date: '2026-10-29', text: 'First conversation, introduced by a counsellor.' }
      ] },
    { id: 'tutorhq', name: 'TutorHQ', source: 'Founding notes', discovery: 'progress', proposal: 'Not started', deal: 'Open', owner: 'lf', followUp: '2026-11-24', illustrative: true,
      history: [
        { date: '2026-11-17', text: 'First problem interview: invoicing and payments in two tools.' }
      ] },
    { id: 'venue', name: 'Events venue', source: 'Event', discovery: 'done', proposal: 'Draft', deal: 'Open', owner: 'sm', followUp: '2026-11-25',
      history: [
        { date: '2026-11-10', text: 'Walked the front desk and gates at a busy event.' },
        { date: '2026-11-03', text: 'Met the operations manager at an industry event.' }
      ] },
    { id: 'clinic', name: 'Community clinic', source: 'LinkedIn', discovery: 'not-started', proposal: 'Not started', deal: 'Open', owner: 'sm', followUp: '2026-11-30',
      history: [
        { date: '2026-11-16', text: 'Asked about faster patient check-in at the front desk.' }
      ] },
    { id: 'college', name: 'Private training college', source: 'Referral', discovery: 'not-started', proposal: 'Not started', deal: 'Open', owner: 'lf', followUp: '2026-12-01',
      history: [
        { date: '2026-11-13', text: 'Introduced by a partner. Interested in visitor access on campus.' }
      ] },
    { id: 'club', name: 'Sports club', source: 'Website', discovery: 'done', proposal: 'Sent 20 Oct', deal: 'Lost', owner: 'lf', followUp: null,
      history: [
        { date: '2026-10-30', text: 'Decided not to go ahead this year: budget.' },
        { date: '2026-10-20', text: 'Proposal sent for a membership app.' }
      ] },
    { id: 'baseflow', name: 'Baseflow (internal)', internal: true }
  ],

  decisions: [
    { id: 'd1', title: 'Approve FSY pilot scope', context: 'Agree scope and budget before any build starts.', status: 'open', approvals: ['lf'], due: '2026-11-23', project: 'fsy' },
    { id: 'd2', title: 'Choose hosting for client apps', context: 'One hosting approach we can reuse across projects. Needs FSY’s data requirements.', status: 'open', approvals: [], due: '2026-11-27' },
    { id: 'd3', title: 'Use Notion as the central workspace', context: 'Projects, tasks, decisions and notes in one place.', status: 'decided', decidedOn: '2026-11-12', by: 'Both founders' },
    { id: 'd4', title: 'Monday priorities, Wednesday update, Friday review', context: 'A lean weekly rhythm around part-time availability.', status: 'decided', decidedOn: '2026-11-09', by: 'Both founders' },
    { id: 'd5', title: 'Take TutorHQ into Discover', context: 'Use the founding-notes case to practise the Discover checklist.', status: 'decided', decidedOn: '2026-11-05', by: 'Both founders' }
  ],

  meetings: [
    { title: 'Friday review', date: '2026-11-13', note: 'Deliverables, blockers and next milestones.', actions: 3 },
    { title: 'FSY discovery call', date: '2026-11-06', note: 'Notes and quotes from the coordinators.', actions: 4 },
    { title: 'Monthly review', date: '2026-10-30', note: 'Finances, pipeline, active projects and strategy.', actions: 2 }
  ],

  // Where things live. Titles only: no passwords or private links here, ever.
  knowledge: [
    { id: 'k1', group: 'Standard operating procedures', title: 'Founder playbook', where: 'Notion', updated: '2026-11-19' },
    { id: 'k2', group: 'Standard operating procedures', title: 'Weekly rhythm and task status labels', where: 'Notion', updated: '2026-11-09' },
    { id: 'k3', group: 'Standard operating procedures', title: 'Proposal template', where: 'Google Drive', updated: '2026-11-11' },
    { id: 'k4', group: 'Client briefs, proposals and contracts', title: 'FSY Companion App proposal', where: 'Google Drive', updated: '2026-11-12' },
    { id: 'k5', group: 'Client briefs, proposals and contracts', title: 'TutorHQ problem brief', where: 'Notion', updated: '2026-11-17' },
    { id: 'k6', group: 'Client briefs, proposals and contracts', title: 'Events venue discovery notes', where: 'Notion', updated: '2026-11-10' },
    { id: 'k7', group: 'Technical documentation', title: 'FSY check-in journey wireframes', where: 'Google Drive', updated: '2026-11-18' },
    { id: 'k8', group: 'Technical documentation', title: 'Command Centre data model', where: 'Notion', updated: '2026-11-16' },
    { id: 'k9', group: 'Brand', title: 'Brand guidelines v2.0', where: 'Google Drive', updated: '2026-11-19' },
    { id: 'k10', group: 'Credentials', title: 'Where credentials are kept', where: 'Password manager', updated: '2026-11-12', secure: true }
  ],

  // Finance and business health: the indicators the playbook asks for. No figures until the sheet is linked.
  finance: [
    { name: 'Revenue', purpose: 'Money earned by Baseflow.' },
    { name: 'Expenses', purpose: 'Money spent to operate and deliver.' },
    { name: 'Outstanding invoices', purpose: 'Money owed to Baseflow.' },
    { name: 'Project costs', purpose: 'Cost of delivering each solution.' },
    { name: 'Cash position', purpose: 'Money available to operate.' },
    { name: 'Recurring revenue', purpose: 'Ongoing support and maintenance income.' }
  ]
};
