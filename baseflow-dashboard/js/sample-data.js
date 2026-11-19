/* Sample event for the Baseflow Dashboard.

   Everything the dashboard shows starts from this file. The figures are
   illustrative. To show a real event, replace these values, or load the same
   shape from your own feed, and the screen follows.

   Times are 24-hour "HH:MM" on the event day. Counts are whole people.
   A guest's booking code identifies them on screen; no names are shown. */

window.BASEFLOW_SAMPLE = {
  event: {
    name: 'Careers Expo',
    day: 'Day 2 of 3',
    venue: 'Main venue',
    date: '2026-11-19',
    now: '14:05',          // the sample opens at this time and then runs in real time
    doorsOpen: '08:00',
    doorsClose: '16:00',   // no new check-ins after this
    expected: 1500         // guests booked for today
  },

  // Places in the venue. `pull` is how likely a new arrival heads there;
  // `overflow` is the room floor teams send people to when it fills up;
  // `note` describes a place that has no sessions.
  areas: [
    { id: 'main-hall', name: 'Main hall', capacity: 800, count: 512, pull: 3 },
    { id: 'hall-b', name: 'Hall B', capacity: 300, count: 276, pull: 2.5, overflow: 'hall-c' },
    { id: 'hall-c', name: 'Hall C', capacity: 300, count: 84, pull: 1 },
    { id: 'expo-floor', name: 'Exhibition floor', capacity: 400, count: 215, pull: 2, note: 'Employer stands until 16:00' },
    { id: 'room-1', name: 'Room 1', capacity: 60, count: 41, pull: 0.6 },
    { id: 'room-3', name: 'Room 3', capacity: 120, count: 18, pull: 0.3, note: 'Free this afternoon' }
  ],

  // Today's programme. `delay` is minutes late; `booked` is seats reserved.
  sessions: [
    { id: 's1', title: 'Welcome and keynote', area: 'main-hall', start: '09:00', end: '10:00', booked: 900 },
    { id: 's2', title: 'Writing a strong CV', area: 'hall-b', start: '10:00', end: '11:00', booked: 240 },
    { id: 's3', title: 'Careers in renewable energy', area: 'hall-c', start: '10:00', end: '11:00', booked: 180 },
    { id: 's4', title: 'Starting your own business', area: 'main-hall', start: '11:00', end: '12:00', booked: 520 },
    { id: 's5', title: 'LinkedIn basics', area: 'room-1', start: '11:00', end: '12:00', booked: 55 },
    { id: 's6', title: 'Learnerships and apprenticeships', area: 'hall-c', start: '12:00', end: '13:00', booked: 210 },
    { id: 's7', title: 'Budgeting your first salary', area: 'room-1', start: '13:00', end: '14:00', booked: 48 },
    { id: 's8', title: 'First jobs in tech', area: 'main-hall', start: '14:00', end: '15:00', booked: 610 },
    { id: 's9', title: 'CV clinic', area: 'hall-b', start: '14:00', end: '15:30', booked: 300, delay: 10 },
    { id: 's10', title: 'Interview skills workshop', area: 'room-1', start: '14:30', end: '15:30', booked: 96 },
    { id: 's11', title: 'Employer speed meetings', area: 'hall-c', start: '15:00', end: '16:00', booked: 260 },
    { id: 's12', title: 'Closing and prize draw', area: 'main-hall', start: '16:00', end: '16:30', booked: 720 }
  ],

  // Front desks and gates. Counts are today's check-ins at each one.
  entryPoints: [
    { id: 'desk-1', name: 'Desk 1', place: 'Front desk', kind: 'desk', count: 412, share: 3 },
    { id: 'desk-2', name: 'Desk 2', place: 'Front desk', kind: 'desk', count: 389, share: 3 },
    { id: 'gate-1', name: 'Gate 1', place: 'North entrance', kind: 'gate', count: 301, share: 2 },
    { id: 'gate-2', name: 'Gate 2', place: 'South entrance', kind: 'gate', count: 146, share: 2 }
  ],

  latestCheckIns: [
    { code: 'BK-4172', entry: 'desk-1', at: '14:05' },
    { code: 'BK-4168', entry: 'gate-2', at: '14:04' },
    { code: 'BK-4170', entry: 'desk-2', at: '14:04' },
    { code: 'BK-4163', entry: 'gate-1', at: '14:03' },
    { code: 'BK-4159', entry: 'desk-1', at: '14:02' }
  ],

  // Check-ins per 30 minutes since the doors opened. The last slot is in progress.
  arrivals: [
    { from: '08:00', count: 86 },
    { from: '08:30', count: 154 },
    { from: '09:00', count: 205 },
    { from: '09:30', count: 180 },
    { from: '10:00', count: 121 },
    { from: '10:30', count: 98 },
    { from: '11:00', count: 84 },
    { from: '11:30', count: 72 },
    { from: '12:00', count: 61 },
    { from: '12:30', count: 54 },
    { from: '13:00', count: 66 },
    { from: '13:30', count: 58 },
    { from: '14:00', count: 9 }
  ],

  teams: [
    { id: 'front-desk', name: 'Front desk', onShift: 6, done: 5, tasks: 6 },
    { id: 'ushers', name: 'Ushers', onShift: 8, done: 4, tasks: 6 },
    { id: 'security', name: 'Security', onShift: 5, done: 4, tasks: 4 },
    { id: 'technical', name: 'Technical', onShift: 3, done: 3, tasks: 4 },
    { id: 'catering', name: 'Catering', onShift: 6, done: 2, tasks: 4 }
  ],

  // Newest first. kind: ok, attention, critical, flow (a change) or info.
  activity: [
    { at: '14:04', kind: 'attention', icon: 'users', text: 'Hall B reached 92% full.', meta: 'Ushers can see it on their phones' },
    { at: '14:02', kind: 'attention', icon: 'clock', text: 'CV clinic is running 10 min late.', meta: 'Sent to 14 staff' },
    { at: '13:51', kind: 'ok', icon: 'circle-check', text: 'Gate 1 is back online. 6 check-ins synced.', meta: 'North entrance' },
    { at: '13:40', kind: 'ok', icon: 'list-checks', text: 'Security finished the 13:30 sweep of the Exhibition floor.', meta: 'Security' },
    { at: '13:15', kind: 'info', icon: 'info', text: 'Budgeting your first salary started in Room 1.', meta: 'On schedule' }
  ],

  // Venue check-ins per day before today. Today's figure is the live count.
  week: [
    { date: '2026-11-13', count: 1180 },
    { date: '2026-11-14', count: 1342 },
    { date: '2026-11-15', count: 806 },
    { date: '2026-11-16', count: 1296 },
    { date: '2026-11-17', count: 1405 },
    { date: '2026-11-18', count: 1873 }
  ],
  previousWeek: { from: '2026-11-06', to: '2026-11-12', count: 7512 },

  // What happens on its own while the sample runs (seconds after opening).
  script: [
    { after: 35, type: 'offline', entry: 'gate-2' },
    { after: 60, type: 'task', team: 'catering', text: 'Catering restocked water in Hall B.' },
    { after: 85, type: 'online', entry: 'gate-2' }
  ]
};
