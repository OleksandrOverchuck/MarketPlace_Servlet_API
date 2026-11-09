const PF_API =
  typeof API_BASE_URL !== "undefined" ? API_BASE_URL : "http://localhost:8080";

const pfUserId = new URLSearchParams(window.location.search).get("id");

let pfProfile = null;
let pfLoggedIn = false;
let pfBusy = false;

function pfEscape(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function pfAvatarSrc(url) {
  if (!url) return null;
  if (url.startsWith("http://") || url.startsWith("https://")) return url;
  return `${PF_API}${url.startsWith("/") ? "" : "/"}${url}`;
}

function pfShowMessage(text, isError = true) {
  const box = document.getElementById("pf-message");
  box.textContent = text || "";
  box.className = "pf-message" + (text ? (isError ? " is-error" : " is-ok") : "");
}

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("pf-back").addEventListener("click", () => {
    if (window.history.length > 1) window.history.back();
    else window.location.href = "index.html";
  });

  document
    .getElementById("pf-rate-remove")
    .addEventListener("click", pfRemoveRating);

  pfLoad();
});

async function pfLoad() {
  if (!pfUserId || !/^\d+$/.test(pfUserId)) {
    pfShowMessage("Nieprawidłowy adres profilu.");
    return;
  }

  try {
    const response = await fetch(
      `${PF_API}/api/users/${encodeURIComponent(pfUserId)}/profile`,
      { credentials: "include" },
    );

    if (response.status === 404) {
      pfShowMessage("Nie znaleziono tego użytkownika.");
      return;
    }

    if (!response.ok) throw new Error();

    pfProfile = await response.json();
  } catch {
    pfShowMessage("Nie udało się wczytać profilu. Spróbuj ponownie później.");
    return;
  }

  try {
    const me = await fetch(`${PF_API}/api/users/me`, { credentials: "include" });
    pfLoggedIn = me.ok;
  } catch {
    pfLoggedIn = false;
  }

  pfRender();
}

function pfRender() {
  const p = pfProfile;

  document.title = `${p.nickname} - JollyCart`;
  document.getElementById("pf-card").hidden = false;
  document.getElementById("pf-name").textContent = p.nickname || "Użytkownik";

  // avatar
  const avatar = document.getElementById("pf-avatar");
  const initial = (p.nickname || "U").trim().charAt(0).toUpperCase() || "U";
  avatar.textContent = initial;
  const src = pfAvatarSrc(p.avatarUrl);
  if (src) {
    const img = document.createElement("img");
    img.src = src;
    img.alt = "";
    img.onerror = () => img.remove();
    avatar.appendChild(img);
  }

  // meta
  const meta = [];
  if (p.location) meta.push(p.location);
  if (p.memberSince) {
    const d = new Date(p.memberSince);
    if (!Number.isNaN(d.getTime())) {
      meta.push(
        "Na JollyCart od " +
          d.toLocaleDateString("pl-PL", { month: "long", year: "numeric" }),
      );
    }
  }
  document.getElementById("pf-meta").textContent = meta.join(" · ");

  pfRenderSummary();
  pfRenderRateBox();
  pfRenderOffers();
}

function pfPolishVotes(n) {
  if (n === 1) return "1 ocena";
  const last = n % 10;
  const lastTwo = n % 100;
  if (last >= 2 && last <= 4 && !(lastTwo >= 12 && lastTwo <= 14)) {
    return `${n} oceny`;
  }
  return `${n} ocen`;
}

function pfStarsHtml(value) {
  // value: liczba 0-5 (może być ułamkowa) - wypełnienie w %
  let html = "";
  for (let i = 1; i <= 5; i++) {
    const fill = Math.max(0, Math.min(1, value - (i - 1))) * 100;
    html += `<span class="pf-star" style="--fill:${fill}%">★</span>`;
  }
  return html;
}

function pfRenderSummary() {
  const p = pfProfile;
  document.getElementById("pf-summary-stars").innerHTML = pfStarsHtml(
    p.averageRating,
  );
  document.getElementById("pf-average").textContent = p.ratingCount
    ? p.averageRating.toFixed(1)
    : "–";
  document.getElementById("pf-count").textContent = p.ratingCount
    ? `(${pfPolishVotes(p.ratingCount)})`
    : "Brak ocen";
}

function pfRenderRateBox() {
  const p = pfProfile;
  const box = document.getElementById("pf-rate-box");
  const loginHint = document.getElementById("pf-login-hint");

  box.hidden = true;
  loginHint.hidden = true;

  if (p.self) return;

  if (!pfLoggedIn) {
    loginHint.hidden = false;
    return;
  }

  box.hidden = false;

  const group = document.getElementById("pf-rate-stars");
  group.innerHTML = "";

  for (let i = 1; i <= 5; i++) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "pf-star-btn";
    btn.setAttribute("role", "radio");
    btn.setAttribute("aria-label", `${i} z 5`);
    btn.setAttribute("aria-checked", String(p.myRating === i));
    btn.dataset.value = String(i);
    btn.textContent = "★";
    btn.classList.toggle("is-on", p.myRating != null && i <= p.myRating);

    btn.addEventListener("mouseenter", () => pfPreview(i));
    btn.addEventListener("focus", () => pfPreview(i));
    btn.addEventListener("click", () => pfRate(i));

    group.appendChild(btn);
  }

  group.onmouseleave = () => pfPreview(null);
  group.addEventListener("focusout", () => pfPreview(null));

  document.getElementById("pf-rate-hint").textContent = p.myRating
    ? `Twoja ocena: ${p.myRating}/5. Kliknij inną gwiazdkę, aby ją zmienić.`
    : "Kliknij gwiazdkę, aby wystawić ocenę od 1 do 5.";

  document.getElementById("pf-rate-remove").hidden = !p.myRating;
}

function pfPreview(n) {
  const mine = pfProfile.myRating;
  document.querySelectorAll("#pf-rate-stars .pf-star-btn").forEach((btn) => {
    const v = Number(btn.dataset.value);
    const limit = n != null ? n : mine || 0;
    btn.classList.toggle("is-on", v <= limit);
  });
}

async function pfRate(stars) {
  if (pfBusy) return;
  pfBusy = true;
  pfShowMessage("");

  try {
    const response = await fetch(
      `${PF_API}/api/users/${encodeURIComponent(pfProfile.id)}/rating`,
      {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ stars }),
      },
    );

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(data.message || "Nie udało się zapisać oceny.");
    }

    pfApplySummary(data);
    pfShowMessage("Dziękujemy! Twoja ocena została zapisana.", false);
  } catch (error) {
    pfShowMessage(error.message || "Nie udało się zapisać oceny.");
  } finally {
    pfBusy = false;
  }
}

async function pfRemoveRating() {
  if (pfBusy) return;
  pfBusy = true;
  pfShowMessage("");

  try {
    const response = await fetch(
      `${PF_API}/api/users/${encodeURIComponent(pfProfile.id)}/rating`,
      { method: "DELETE", credentials: "include" },
    );

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(data.message || "Nie udało się usunąć oceny.");
    }

    pfApplySummary(data);
    pfShowMessage("Twoja ocena została usunięta.", false);
  } catch (error) {
    pfShowMessage(error.message || "Nie udało się usunąć oceny.");
  } finally {
    pfBusy = false;
  }
}

function pfApplySummary(summary) {
  pfProfile.averageRating = summary.averageRating;
  pfProfile.ratingCount = summary.ratingCount;
  pfProfile.myRating = summary.myRating;
  pfRenderSummary();
  pfRenderRateBox();
}

function pfRenderOffers() {
  const offers = pfProfile.offers || [];
  const section = document.getElementById("pf-offers-section");
  const list = document.getElementById("pf-offers");

  if (!offers.length) {
    section.hidden = true;
    return;
  }

  section.hidden = false;

  list.innerHTML = offers
    .map((offer) => {
      const price =
        offer.price != null
          ? `${Number(offer.price).toLocaleString("pl-PL")} zł`
          : "Cena do uzgodnienia";

      return `
        <a class="pf-offer" href="offer-details.html?id=${encodeURIComponent(offer.id)}">
          <span class="pf-offer-cat">${pfEscape(offer.categoryName || "Bez kategorii")}</span>
          <strong class="pf-offer-title">${pfEscape(offer.title)}</strong>
          <span class="pf-offer-price">${pfEscape(price)}</span>
          <span class="pf-offer-loc">${pfEscape(offer.location || "")}</span>
        </a>`;
    })
    .join("");
}
