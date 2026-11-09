const MY_OFFERS_API_BASE_URL = "http://localhost:8080";

let myOffers = [];
let myOfferImages = {};
let activeStatusFilter = "ALL";

document.addEventListener("DOMContentLoaded", () => {
  setupStatusFilters();
  loadMyOffers();
});

/* =========================
   ŁADOWANIE
   ========================= */

async function loadMyOffers() {
  const grid = document.getElementById("my-offers-grid");
  const message = document.getElementById("my-offers-message");

  if (!grid) return;

  showSkeletons(grid);
  clearMyOffersMessage(message);

  try {
    const response = await fetch(`${MY_OFFERS_API_BASE_URL}/api/offers/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    const data = await readMyOffersResponse(response);

    if (!response.ok) {
      throw new Error(
        data?.message || data?.error || "Nie udało się pobrać ogłoszeń.",
      );
    }

    myOffers = Array.isArray(data) ? data : [];

    // zdjęcia pobieramy równolegle, żeby karty nie ładowały się po kolei
    const urls = await Promise.all(
      myOffers.map((offer) => getFirstImageUrl(offer.id)),
    );
    myOfferImages = {};
    myOffers.forEach((offer, index) => {
      myOfferImages[offer.id] = urls[index];
    });

    renderMyOffers();
  } catch (error) {
    console.error("Błąd pobierania własnych ogłoszeń:", error);

    grid.innerHTML = "";
    grid.setAttribute("aria-busy", "false");

    if (message) {
      message.textContent =
        "Nie udało się pobrać ogłoszeń. Sprawdź połączenie i odśwież stronę.";
      message.className = "my-offers-message is-error";
    }
  }
}

async function getFirstImageUrl(offerId) {
  try {
    const response = await fetch(
      `${MY_OFFERS_API_BASE_URL}/api/offers/${encodeURIComponent(offerId)}/images`,
    );

    if (!response.ok) return null;

    const images = await response.json();

    if (!Array.isArray(images) || images.length === 0) return null;

    return `${MY_OFFERS_API_BASE_URL}/api/offers/images/${encodeURIComponent(images[0].id)}`;
  } catch (error) {
    return null;
  }
}

function showSkeletons(grid) {
  grid.setAttribute("aria-busy", "true");
  grid.innerHTML = Array.from(
    { length: 3 },
    () => '<div class="my-offer-skeleton"></div>',
  ).join("");
}

function clearMyOffersMessage(message) {
  if (!message) return;
  message.textContent = "";
  message.className = "my-offers-message";
}

/* =========================
   FILTRY
   ========================= */

function setupStatusFilters() {
  document.querySelectorAll(".my-offers-filter").forEach((button) => {
    button.addEventListener("click", () => {
      activeStatusFilter = button.dataset.filter;
      renderMyOffers();
    });
  });
}

function updateFilters() {
  const filters = document.getElementById("my-offers-filters");

  if (!filters) return;

  filters.hidden = myOffers.length === 0;

  const counts = { ALL: myOffers.length, ACTIVE: 0, INACTIVE: 0, SOLD: 0 };

  myOffers.forEach((offer) => {
    if (counts[offer.status] !== undefined) counts[offer.status] += 1;
  });

  filters.querySelectorAll(".my-offers-filter").forEach((button) => {
    const key = button.dataset.filter;
    const isActive = key === activeStatusFilter;

    button.classList.toggle("is-active", isActive);
    button.setAttribute("aria-pressed", String(isActive));
    button.querySelector("[data-count]").textContent = String(counts[key] ?? 0);
  });
}

/* =========================
   RENDEROWANIE
   ========================= */

function renderMyOffers() {
  const grid = document.getElementById("my-offers-grid");

  if (!grid) return;

  grid.setAttribute("aria-busy", "false");

  updateFilters();

  if (myOffers.length === 0) {
    grid.innerHTML = `
      <div class="my-offers-empty">
        <h2>Nie masz jeszcze żadnych ogłoszeń</h2>
        <p>Dodaj pierwsze ogłoszenie, aby pojawiło się tutaj oraz na ogólnej liście.</p>
        <a href="post-ad.html" class="my-offers-empty-button">Dodaj ogłoszenie</a>
      </div>
    `;
    return;
  }

  const visible =
    activeStatusFilter === "ALL"
      ? myOffers
      : myOffers.filter((offer) => offer.status === activeStatusFilter);

  if (visible.length === 0) {
    grid.innerHTML = `
      <div class="my-offers-empty my-offers-empty--filtered">
        <h2>Brak ogłoszeń w tej grupie</h2>
        <p>Zmień filtr powyżej, aby zobaczyć pozostałe ogłoszenia.</p>
      </div>
    `;
    return;
  }

  grid.innerHTML = visible.map(renderMyOfferCard).join("");

  grid.querySelectorAll("[data-delete-offer]").forEach((button) => {
    button.addEventListener("click", () =>
      deleteOffer(button.dataset.deleteOffer),
    );
  });
}

function renderMyOfferCard(offer) {
  const status = getStatusLabel(offer.status);
  const type = getTypeLabel(offer.type);
  const price =
    offer.price == null
      ? "Cena do uzgodnienia"
      : `${formatPrice(offer.price)} zł`;
  const detailsUrl = `offer-details.html?id=${encodeURIComponent(offer.id)}`;
  const imageUrl = myOfferImages[offer.id];

  const media = imageUrl
    ? `<img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(offer.title)}" loading="lazy" />`
    : `<span class="my-offer-noimage">Brak zdjęcia</span>`;

  return `
    <article class="my-offer-card">
      <a class="my-offer-media" href="${detailsUrl}" aria-label="Podgląd ogłoszenia: ${escapeHtml(offer.title)}">
        ${media}
        <span class="my-offer-status status-${String(offer.status).toLowerCase()}">${escapeHtml(status)}</span>
        <span class="my-offer-type">${escapeHtml(type)}</span>
      </a>
      <div class="my-offer-card-content">
        <span class="my-offer-category">${escapeHtml(offer.categoryName || "Bez kategorii")}</span>
        <h2>${escapeHtml(offer.title)}</h2>
        <p class="my-offer-description">${escapeHtml(offer.description)}</p>
        <div class="my-offer-meta">
          <strong class="${offer.price == null ? "is-negotiable" : ""}">${escapeHtml(price)}</strong>
          <span>${escapeHtml(offer.location || "Lokalizacja nie podana")}</span>
        </div>
        <div class="my-offer-date">Dodano: ${formatDate(offer.createdAt)}</div>
      </div>
      <div class="my-offer-actions">
        <a class="my-offer-view" href="${detailsUrl}">Podgląd</a>
        <a class="my-offer-edit" href="edit-offer.html?id=${encodeURIComponent(offer.id)}">Edytuj</a>
        <button class="my-offer-delete" type="button" data-delete-offer="${escapeHtml(String(offer.id))}">Usuń</button>
      </div>
    </article>
  `;
}

/* =========================
   USUWANIE
   ========================= */

async function deleteOffer(id) {
  if (!confirm("Czy na pewno chcesz usunąć to ogłoszenie?")) return;

  try {
    const response = await fetch(
      `${MY_OFFERS_API_BASE_URL}/api/offers/${encodeURIComponent(id)}`,
      {
        method: "DELETE",
        credentials: "include",
      },
    );

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      const data = await readMyOffersResponse(response);
      throw new Error(
        data?.message || data?.error || "Nie udało się usunąć ogłoszenia.",
      );
    }

    await loadMyOffers();
  } catch (error) {
    alert(error.message || "Nie udało się usunąć ogłoszenia.");
  }
}

/* =========================
   POMOCNICZE
   ========================= */

function getStatusLabel(status) {
  return (
    {
      ACTIVE: "Aktywne",
      INACTIVE: "Nieaktywne",
      SOLD: "Sprzedane",
      RESERVED: "Zarezerwowane",
    }[status] ||
    status ||
    "Nieznany"
  );
}

function getTypeLabel(type) {
  return (
    { SALE: "Sprzedaż", SERVICE: "Usługa", JOB: "Oferta pracy" }[type] ||
    type ||
    ""
  );
}

function formatPrice(value) {
  return new Intl.NumberFormat("pl-PL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value));
}

function formatDate(value) {
  if (!value) return "brak daty";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "brak daty";
  return date.toLocaleDateString("pl-PL");
}

async function readMyOffersResponse(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}
