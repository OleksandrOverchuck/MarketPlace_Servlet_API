const INDEX_OFFERS_API_BASE_URL = "http://localhost:8080";

let indexCategories = [];

document.addEventListener("DOMContentLoaded", async () => {
  setupOfferSearch();
  await loadCategories();
  await loadPublicOffers();
});

async function loadCategories() {
  const categorySelect = document.getElementById("offer-category-select");
  const categoryCards = document.querySelectorAll("[data-category-name]");

  try {
    const response = await fetch(
      `${INDEX_OFFERS_API_BASE_URL}/api/categories`,
      {
        method: "GET",
      },
    );

    if (!response.ok) {
      throw new Error("Nie udało się pobrać kategorii.");
    }

    const categories = await response.json();
    indexCategories = Array.isArray(categories) ? categories : [];

    if (categorySelect) {
      categorySelect.innerHTML = `
        <option value="">Wszystkie kategorie</option>
        ${indexCategories
          .map(
            (category) =>
              `<option value="${escapeIndexHtml(String(category.id))}">${escapeIndexHtml(category.name)}</option>`,
          )
          .join("")}
      `;
    }

    categoryCards.forEach((card) => {
      const categoryName = card.dataset.categoryName;
      const category = indexCategories.find(
        (item) => item.name === categoryName,
      );

      if (!category) {
        card.disabled = true;
        card.title = "Ta kategoria nie jest dostępna.";
        return;
      }

      card.addEventListener("click", () => {
        if (categorySelect) {
          categorySelect.value = String(category.id);
        }

        searchPublicOffers(
          { categoryId: category.id },
          { scrollToOffers: true },
        );
      });
    });
  } catch (error) {
    console.error("Błąd pobierania kategorii:", error);
  }
}

function setupOfferSearch() {
  const form = document.getElementById("offer-search-form");
  const searchInput = document.getElementById("offer-search-input");
  const locationInput = document.getElementById("offer-location-input");
  const categorySelect = document.getElementById("offer-category-select");

  if (!form) {
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    await searchPublicOffers(
      {
        search: searchInput?.value.trim() || "",
        location: locationInput?.value.trim() || "",
        categoryId: categorySelect?.value || "",
      },
      { scrollToOffers: true },
    );
  });
}

async function loadPublicOffers() {
  await searchPublicOffers();
}

async function searchPublicOffers(
  filters = {},
  { scrollToOffers = false } = {},
) {
  const grid = document.getElementById("offers-grid");
  if (!grid) return;

  const params = new URLSearchParams();

  if (filters.search) {
    params.set("search", filters.search);
  }

  if (filters.location) {
    params.set("location", filters.location);
  }

  if (filters.categoryId) {
    params.set("categoryId", filters.categoryId);
  }

  grid.innerHTML = `
    <div class="offers-loading-state">Ładowanie ogłoszeń...</div>
  `;

  try {
    const query = params.toString();
    const url = `${INDEX_OFFERS_API_BASE_URL}/api/offers${query ? `?${query}` : ""}`;

    const response = await fetch(url, {
      method: "GET",
    });

    const data = await readIndexOffersResponse(response);
    if (!response.ok) {
      throw new Error(
        data?.message || data?.error || "Nie udało się pobrać ogłoszeń.",
      );
    }

    if (!Array.isArray(data) || data.length === 0) {
      grid.innerHTML = `
        <div class="offers-empty-state">
          <h3>Brak pasujących ogłoszeń</h3>
          <p>Spróbuj zmienić nazwę, miasto lub kategorię.</p>
        </div>
      `;
    } else {
      const cards = await Promise.all(data.map(renderPublicOffer));
      grid.innerHTML = cards.join("");
    }

    if (scrollToOffers) {
      document.getElementById("offers")?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }
  } catch (error) {
    console.error("Błąd pobierania ogłoszeń:", error);
    grid.innerHTML = `
      <div class="offers-empty-state offers-error-state">
        <h3>Nie udało się pobrać ogłoszeń</h3>
        <p>Sprawdź, czy backend JollyCart jest uruchomiony.</p>
      </div>
    `;
  }
}

async function renderPublicOffer(offer) {
  const hasPrice = offer.price != null;
  const price = hasPrice
    ? `${formatPublicPrice(offer.price)} zł`
    : "Cena do uzgodnienia";
  const type = getPublicTypeLabel(offer.type);
  const seller = offer.nickname || "Użytkownik";
  const imageUrl = await getFirstOfferImageUrl(offer.id);
  const imageHtml = imageUrl
    ? `<img src="${escapeIndexHtml(imageUrl)}" alt="${escapeIndexHtml(offer.title)}" loading="lazy">`
    : `<span class="offer-card-icon" aria-hidden="true">${getOfferIcon(offer.type)}</span>`;

  const pinIcon = `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21Z"></path>
      <circle cx="12" cy="9.5" r="2.5"></circle>
    </svg>`;

  return `
    <a class="offer-card" href="offer-details.html?id=${encodeURIComponent(offer.id)}">
      <div class="offer-card-image">
        ${imageHtml}
        <span class="offer-type-badge">${escapeIndexHtml(type)}</span>
      </div>
      <div class="offer-content">
        <span class="offer-category">${escapeIndexHtml(offer.categoryName || "Bez kategorii")}</span>
        <h3>${escapeIndexHtml(offer.title)}</h3>
        <span class="price${hasPrice ? "" : " is-negotiable"}">${escapeIndexHtml(price)}</span>
        <div class="offer-meta">
          <span class="location">${pinIcon}<span>${escapeIndexHtml(offer.location || "Lokalizacja nie podana")}</span></span>
          <span class="date">${formatPublicDate(offer.createdAt)}</span>
        </div>
        <div class="offer-author" data-profile-id="${escapeIndexHtml(offer.userId)}" role="link" tabindex="0" title="Zobacz profil użytkownika">
          <span class="offer-author-avatar" aria-hidden="true">${escapeIndexHtml(seller.trim().charAt(0) || "U")}</span>
          <span class="offer-author-name">${escapeIndexHtml(seller)}</span>
        </div>
      </div>
    </a>
  `;
}

async function getFirstOfferImageUrl(offerId) {
  try {
    const response = await fetch(
      `${INDEX_OFFERS_API_BASE_URL}/api/offers/${encodeURIComponent(offerId)}/images`,
    );
    if (!response.ok) return null;
    const images = await response.json();
    if (!Array.isArray(images) || !images.length) return null;
    return `${INDEX_OFFERS_API_BASE_URL}/api/offers/images/${encodeURIComponent(images[0].id)}`;
  } catch {
    return null;
  }
}

function getOfferIcon(type) {
  return { SALE: "🛍️", SERVICE: "🛠️", JOB: "💼" }[type] || "📦";
}

function getPublicTypeLabel(type) {
  return (
    { SALE: "Sprzedaż", SERVICE: "Usługa", JOB: "Oferta pracy" }[type] ||
    type ||
    "Ogłoszenie"
  );
}

function formatPublicPrice(value) {
  return new Intl.NumberFormat("pl-PL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value));
}

function formatPublicDate(value) {
  if (!value) return "brak daty";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "brak daty";
  return date.toLocaleDateString("pl-PL");
}

async function readIndexOffersResponse(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

function escapeIndexHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}
