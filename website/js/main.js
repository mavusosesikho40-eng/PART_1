// Rand Training College — shared behaviour

(function () {
  var toggle = document.querySelector(".nav-toggle");
  var nav = document.querySelector(".main-nav");

  if (toggle && nav) {
    toggle.addEventListener("click", function () {
      var open = nav.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });

    nav.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", function () {
        nav.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
      });
    });
  }
})();

// Adds a shadow to the sticky header once the page has scrolled,
// so it reads as raised above the content instead of flat against it.
(function () {
  var header = document.querySelector(".site-header");
  if (!header) return;

  function onScroll() {
    header.classList.toggle("scrolled", window.scrollY > 8);
  }

  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });
})();

// Generic client-side form validation, then a real submit to the
// form's own action (Formspree) via fetch so the page can show an
// inline success/error message instead of a full-page redirect.
function validateForm(form) {
  var valid = true;
  var fields = form.querySelectorAll("[required]");

  fields.forEach(function (field) {
    var errorEl = document.getElementById(field.id + "-error");
    var fieldValid = field.checkValidity();

    if (!fieldValid) {
      valid = false;
      field.classList.add("invalid");
      if (errorEl) errorEl.classList.add("show");
    } else {
      field.classList.remove("invalid");
      if (errorEl) errorEl.classList.remove("show");
    }
  });

  return valid;
}

function wireForm(formId, statusMessage) {
  var form = document.getElementById(formId);
  if (!form) return;

  var status = form.querySelector(".form-status");
  var submitBtn = form.querySelector('button[type="submit"]');
  var submitLabel = submitBtn ? submitBtn.textContent : "";

  function showStatus(message, isSuccess) {
    if (!status) return;
    status.textContent = message;
    status.classList.toggle("success", !!isSuccess);
    status.classList.add("show");
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();

    if (!validateForm(form)) {
      showStatus("Please fix the highlighted fields and try again.", false);
      var firstInvalid = form.querySelector(".invalid");
      if (firstInvalid) firstInvalid.focus();
      return;
    }

    if (status) status.classList.remove("show", "success");
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = "Sending…";
    }

    fetch(form.action, {
      method: "POST",
      body: new FormData(form),
      headers: { Accept: "application/json" }
    })
      .then(function (response) {
        if (response.ok) {
          showStatus(statusMessage, true);
          form.reset();
          return;
        }
        return response.json().catch(function () { return null; }).then(function (data) {
          var message =
            data && data.errors && data.errors.length
              ? data.errors.map(function (e) { return e.message; }).join(" ")
              : "Something went wrong sending this. Please try again, or email us directly.";
          showStatus(message, false);
        });
      })
      .catch(function () {
        showStatus("Something went wrong sending this. Please check your connection and try again.", false);
      })
      .finally(function () {
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = submitLabel;
        }
      });
  });

  form.querySelectorAll("[required]").forEach(function (field) {
    field.addEventListener("blur", function () {
      var errorEl = document.getElementById(field.id + "-error");
      if (field.checkValidity()) {
        field.classList.remove("invalid");
        if (errorEl) errorEl.classList.remove("show");
      }
    });
  });
}

// Quick finder on the homepage jumps straight to the matching
// faculty section on the Academics page instead of just submitting
// a query string it has no backend to read.
function wireQuickFinder() {
  var form = document.querySelector(".quick-finder-inner");
  if (!form) return;

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    var faculty = form.querySelector("#qf-faculty").value;
    window.location.href = faculty ? "academics.html#" + faculty : "academics.html";
  });
}

document.addEventListener("DOMContentLoaded", function () {
  wireQuickFinder();
  wireForm(
    "contact-form",
    "Thank you — your message has been received. Our office will reply within two working days."
  );
  wireForm(
    "application-form",
    "Thank you — your application has been received. Check your email for confirmation and next steps."
  );

  var yearEl = document.getElementById("current-year");
  if (yearEl) yearEl.textContent = new Date().getFullYear();
});
