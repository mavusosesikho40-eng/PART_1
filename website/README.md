# Rand Training College — Website

A static, dependency-free website for Rand Training College: Home, About, Academics,
Admissions (with an application form) and Contact (with an enquiry form).

## Structure

```
index.html        Home
about.html         About Us
academics.html     Programmes, grouped by faculty
admissions.html    Requirements, fees, key dates, application form
contact.html        Campus details, map, enquiry form
css/styles.css     Shared styles
js/main.js         Mobile nav toggle + client-side form validation
```

## Running locally

No build step. Either open `index.html` directly in a browser, or serve the folder:

```
python3 -m http.server 8000
```

then visit `http://localhost:8000`.

## Forms

Both forms (`#contact-form` on Contact, `#application-form` on Admissions) validate
required fields in the browser but do not submit anywhere yet — there's no backend.
To go live, either:

- Point the `<form>` at a form backend (e.g. Formspree, Netlify Forms) and remove the
  `preventDefault()` call in `js/main.js`, or
- Add a real API endpoint and send the form data with `fetch()` inside the submit
  handler in `js/main.js`.

## Deploying

This is plain HTML/CSS/JS, so it can be hosted anywhere static: GitHub Pages, Netlify,
Vercel, or any web host. For GitHub Pages: push to a repo, then enable Pages on the
`main` branch in the repo's Settings.
