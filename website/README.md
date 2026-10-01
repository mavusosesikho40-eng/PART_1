# Rand Training College — Website

A static, dependency-free website for Rand Training College: Home, About, Academics,
Admissions (with an application form) and Contact (with an enquiry form).

## Structure

```
index.html         Home
about.html         About Us
academics.html     Programmes, grouped by faculty, linking to programs/
programs/          One detail page per programme (overview, curriculum, outcomes)
admissions.html    Requirements, fees, key dates, application form
contact.html       Campus details, map, enquiry form
css/styles.css     Shared styles
js/main.js         Mobile nav toggle, form validation + submission, quick finder
images/programs/   Drop a photo per programme here — see its README.md
404.html           Branded not-found page (served automatically by GitHub Pages)
```

## Running locally

No build step. Either open `index.html` directly in a browser, or serve the folder:

```
python3 -m http.server 8000
```

then visit `http://localhost:8000`.

## Forms

Both forms (`#contact-form` on Contact, `#application-form` on Admissions) validate
required fields in the browser, then submit to the same Formspree endpoint
(`https://formspree.io/f/xaenkkkb`) via `fetch()` in `js/main.js`, showing an inline
success or error message without leaving the page. Each form sends a hidden
`form_source` field so submissions from the two pages can be told apart in the
Formspree inbox.

To point either form at a different endpoint later, change its `action` attribute —
no JavaScript changes needed. Formspree's free tier asks you to confirm your email
the first time a real submission comes in.

## Deploying

This is plain HTML/CSS/JS, so it can be hosted anywhere static: GitHub Pages, Netlify,
Vercel, or any web host. For GitHub Pages: push to a repo, then enable Pages on the
`main` branch in the repo's Settings.
