const OFFER_DETAILS_API_BASE_URL = "http://localhost:8080";
const offerDetailsId = new URLSearchParams(window.location.search).get("id");

document.addEventListener("DOMContentLoaded", loadOfferDetails);

async function loadOfferDetails() {
  const root = document.getElementById("offer-details-root");

  if (!root) {
    return;
  }

  if (!offerDetailsId) {
    root.innerHTML = `
      <div class="offer-details-error">
        <h2>Brakuje identyfikatora ogłoszenia.</h2>
      </div>
    `;
    return;
  }

  try {
    const response = await fetch(
      `${OFFER_DETAILS_API_BASE_URL}/api/offers/${encodeURIComponent(offerDetailsId)}`,
      {
        method: "GET",
        credentials: "include",
      },
    );

    const offer = await readDetailsResponse(response);

    if (!response.ok) {
      throw new Error(
        offer?.message || offer?.error || "Nie udało się pobrać ogłoszenia.",
      );
    }

    const imagesResponse = await fetch(
      `${OFFER_DETAILS_API_BASE_URL}/api/offers/${encodeURIComponent(offerDetailsId)}/images`,
      {
        method: "GET",
        credentials: "include",
      },
    );

    const images = imagesResponse.ok ? await imagesResponse.json() : [];

    renderOfferDetails(root, offer, Array.isArray(images) ? images : []);
    setupOfferGallery(root);
    setupImageLightbox(root);

    // Mapa lokalizacji (scripts/location/offer-map.js) - ładuje się po kliknięciu.
    if (window.JollyCartOfferMap) {
      window.JollyCartOfferMap.setup(root);
    }

    // Przycisk wiadomości musi być podpięty dopiero
    // po wyrenderowaniu HTML-a ogłoszenia.
    await setupMessageButton(offer);

    // Przycisk "Kup przez JollyCart" (scripts/payments/buy-button.js)
    if (typeof setupBuyButton === "function") {
      await setupBuyButton(offer);
    }
  } catch (error) {
    console.error("Błąd pobierania szczegółów ogłoszenia:", error);

    root.innerHTML = `
      <div class="offer-details-error">
        <h2>Nie udało się pobrać ogłoszenia</h2>
        <p>${escapeDetailsHtml(
          error.message || "Spróbuj ponownie później.",
        )}</p>
      </div>
    `;
  }
}

// Slajder zdjęć: strzałki < >, kropki, klawiatura (← →) i przeciągnięcie palcem.
function setupOfferGallery(root) {
  const gallery = root.querySelector(".offer-gallery");

  if (!gallery) {
    return;
  }

  const track = gallery.querySelector(".offer-gallery-track");
  const slides = gallery.querySelectorAll(".offer-gallery-slide");
  const total = slides.length;

  if (total < 2) {
    return;
  }

  const counter = gallery.querySelector(".offer-gallery-counter");
  const thumbsBox = root.querySelector(".offer-gallery-thumbs");
  const thumbs = root.querySelectorAll(".offer-gallery-thumb");
  let current = 0;

  function show(index) {
    current = (index + total) % total;

    track.style.transform = `translateX(-${current * 100}%)`;

    counter.textContent = `${current + 1} / ${total}`;

    thumbs.forEach((thumb, thumbIndex) => {
      const isActive = thumbIndex === current;

      thumb.classList.toggle("is-active", isActive);
      thumb.setAttribute("aria-current", isActive ? "true" : "false");
    });

    // Aktywna miniatura zawsze w widocznej części paska (bez przewijania strony).
    const activeThumb = thumbs[current];

    if (thumbsBox && activeThumb) {
      thumbsBox.scrollTo({
        left:
          activeThumb.offsetLeft -
          (thumbsBox.clientWidth - activeThumb.offsetWidth) / 2,
        behavior: "smooth",
      });
    }
  }

  gallery
    .querySelector(".offer-gallery-prev")
    .addEventListener("click", () => show(current - 1));

  gallery
    .querySelector(".offer-gallery-next")
    .addEventListener("click", () => show(current + 1));

  thumbs.forEach((thumb) => {
    thumb.addEventListener("click", () => show(Number(thumb.dataset.index)));
  });

  gallery.addEventListener("keydown", (event) => {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      show(current - 1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      show(current + 1);
    }
  });

  let startX = null;

  gallery.addEventListener(
    "touchstart",
    (event) => {
      startX = event.touches[0].clientX;
    },
    { passive: true },
  );

  gallery.addEventListener("touchend", (event) => {
    if (startX === null) {
      return;
    }

    const diff = event.changedTouches[0].clientX - startX;
    startX = null;

    if (Math.abs(diff) > 40) {
      show(diff > 0 ? current - 1 : current + 1);
    }
  });

  // Pozwala podglądowi pełnoekranowemu zsynchronizować slajder.
  gallery.goTo = show;

  show(0);
}

// Pełnoekranowy podgląd zdjęć: powiększanie / pomniejszanie (przyciski +/−,
// kółko myszy, dwuklik, szczypanie palcami), przesuwanie powiększonego
// zdjęcia, strzałki < >, klawisze (Esc, ←, →, +, −, 0).
function setupImageLightbox(root) {
  document.querySelector(".image-lightbox")?.remove();

  const gallery = root.querySelector(".offer-gallery");

  if (!gallery) {
    return;
  }

  const slides = Array.from(gallery.querySelectorAll(".offer-gallery-slide"));

  const sources = slides.map((slide) => {
    const image = slide.querySelector("img");

    return { src: image.src, alt: image.alt };
  });

  const total = sources.length;

  if (!total) {
    return;
  }

  const MIN_SCALE = 1;
  const MAX_SCALE = 5;
  const BUTTON_FACTOR = 1.4;
  const WHEEL_FACTOR = 1.15;

  const overlay = document.createElement("div");

  overlay.className = "image-lightbox";
  overlay.hidden = true;
  overlay.setAttribute("role", "dialog");
  overlay.setAttribute("aria-modal", "true");
  overlay.setAttribute("aria-label", "Podgląd zdjęcia");

  overlay.innerHTML = `
    <div class="image-lightbox-stage">
      <img class="image-lightbox-img" alt="" draggable="false" />
    </div>

    <button type="button" class="image-lightbox-close" aria-label="Zamknij podgląd">&times;</button>

    <div class="image-lightbox-counter" aria-live="polite"></div>

    ${
      total > 1
        ? `
          <button type="button" class="image-lightbox-arrow image-lightbox-prev" aria-label="Poprzednie zdjęcie">&#8249;</button>
          <button type="button" class="image-lightbox-arrow image-lightbox-next" aria-label="Następne zdjęcie">&#8250;</button>
        `
        : ""
    }

    <div class="image-lightbox-toolbar">
      <button type="button" data-zoom="out" aria-label="Pomniejsz">&minus;</button>
      <span class="image-lightbox-zoom" aria-live="polite">100%</span>
      <button type="button" data-zoom="in" aria-label="Powiększ">+</button>
      <button type="button" data-zoom="reset" class="image-lightbox-reset">Resetuj</button>
    </div>
  `;

  document.body.appendChild(overlay);

  const stage = overlay.querySelector(".image-lightbox-stage");
  const img = overlay.querySelector(".image-lightbox-img");
  const counter = overlay.querySelector(".image-lightbox-counter");
  const zoomLabel = overlay.querySelector(".image-lightbox-zoom");
  const closeButton = overlay.querySelector(".image-lightbox-close");
  const zoomOutButton = overlay.querySelector('[data-zoom="out"]');
  const zoomInButton = overlay.querySelector('[data-zoom="in"]');

  let index = 0;
  let scale = 1;
  let tx = 0;
  let ty = 0;
  let lastFocused = null;

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function clampPan() {
    const maxX = Math.max(0, (img.offsetWidth * scale - stage.clientWidth) / 2);
    const maxY = Math.max(
      0,
      (img.offsetHeight * scale - stage.clientHeight) / 2,
    );

    tx = clamp(tx, -maxX, maxX);
    ty = clamp(ty, -maxY, maxY);
  }

  function applyTransform() {
    img.style.transform = `translate(${tx}px, ${ty}px) scale(${scale})`;

    zoomLabel.textContent = `${Math.round(scale * 100)}%`;

    stage.classList.toggle("is-zoomed", scale > 1);

    zoomOutButton.disabled = scale <= MIN_SCALE;
    zoomInButton.disabled = scale >= MAX_SCALE;
  }

  // Zmiana powiększenia względem punktu (domyślnie środek ekranu).
  function setScale(newScale, clientX, clientY) {
    newScale = clamp(newScale, MIN_SCALE, MAX_SCALE);

    const rect = stage.getBoundingClientRect();

    const px =
      clientX === undefined ? 0 : clientX - (rect.left + rect.width / 2);
    const py =
      clientY === undefined ? 0 : clientY - (rect.top + rect.height / 2);

    const ratio = newScale / scale;

    tx = px - (px - tx) * ratio;
    ty = py - (py - ty) * ratio;

    scale = newScale;

    if (scale === 1) {
      tx = 0;
      ty = 0;
    }

    clampPan();
    applyTransform();
  }

  function showImage(newIndex) {
    index = (newIndex + total) % total;

    img.src = sources[index].src;
    img.alt = sources[index].alt;

    counter.textContent = total > 1 ? `${index + 1} / ${total}` : "";

    scale = 1;
    tx = 0;
    ty = 0;

    applyTransform();
  }

  function openLightbox(startIndex) {
    lastFocused = document.activeElement;

    overlay.hidden = false;
    document.body.style.overflow = "hidden";

    showImage(startIndex);

    document.addEventListener("keydown", onKeyDown);

    closeButton.focus();
  }

  function closeLightbox() {
    overlay.hidden = true;
    document.body.style.overflow = "";

    document.removeEventListener("keydown", onKeyDown);

    // Slajder na stronie ustawiamy na zdjęcie, które było oglądane.
    if (typeof gallery.goTo === "function") {
      gallery.goTo(index);
    }

    if (lastFocused && typeof lastFocused.focus === "function") {
      lastFocused.focus();
    }
  }

  function onKeyDown(event) {
    switch (event.key) {
      case "Escape":
        closeLightbox();
        break;

      case "ArrowLeft":
        if (total > 1) {
          showImage(index - 1);
        }
        break;

      case "ArrowRight":
        if (total > 1) {
          showImage(index + 1);
        }
        break;

      case "+":
      case "=":
        setScale(scale * BUTTON_FACTOR);
        break;

      case "-":
      case "_":
        setScale(scale / BUTTON_FACTOR);
        break;

      case "0":
        setScale(1);
        break;

      case "Tab": {
        const focusable = Array.from(
          overlay.querySelectorAll("button:not([disabled])"),
        );

        if (!focusable.length) {
          break;
        }

        const first = focusable[0];
        const last = focusable[focusable.length - 1];

        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }

        break;
      }

      default:
        return;
    }

    if (event.key !== "Tab") {
      event.preventDefault();
    }
  }

  // Kliknięcie w zdjęcie w slajderze otwiera podgląd.
  gallery.addEventListener("click", (event) => {
    const slide = event.target.closest(".offer-gallery-slide");

    if (!slide) {
      return;
    }

    openLightbox(slides.indexOf(slide));
  });

  closeButton.addEventListener("click", closeLightbox);

  overlay
    .querySelector(".image-lightbox-prev")
    ?.addEventListener("click", () => showImage(index - 1));

  overlay
    .querySelector(".image-lightbox-next")
    ?.addEventListener("click", () => showImage(index + 1));

  overlay.querySelectorAll("[data-zoom]").forEach((button) => {
    button.addEventListener("click", () => {
      const action = button.dataset.zoom;

      if (action === "in") {
        setScale(scale * BUTTON_FACTOR);
      } else if (action === "out") {
        setScale(scale / BUTTON_FACTOR);
      } else {
        setScale(1);
      }
    });
  });

  // Kółko myszy = zoom w miejscu wskaźnika.
  stage.addEventListener(
    "wheel",
    (event) => {
      event.preventDefault();

      setScale(
        event.deltaY < 0 ? scale * WHEEL_FACTOR : scale / WHEEL_FACTOR,
        event.clientX,
        event.clientY,
      );
    },
    { passive: false },
  );

  // Dwuklik: powiększ / wróć do 100%.
  stage.addEventListener("dblclick", (event) => {
    if (scale > 1) {
      setScale(1);
    } else {
      setScale(2.5, event.clientX, event.clientY);
    }
  });

  // Przesuwanie powiększonego zdjęcia, szczypanie (2 palce),
  // przeciągnięcie w bok = następne/poprzednie zdjęcie (przy 100%),
  // kliknięcie w tło = zamknięcie.
  const pointers = new Map();

  let dragStart = null;
  let pinchStart = null;
  let moved = false;
  let downOnImage = false;

  stage.addEventListener("pointerdown", (event) => {
    stage.setPointerCapture(event.pointerId);

    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (pointers.size === 1) {
      moved = false;
      downOnImage = event.target === img;

      dragStart = { x: event.clientX, y: event.clientY, tx, ty };
    } else if (pointers.size === 2) {
      const [a, b] = Array.from(pointers.values());

      pinchStart = {
        distance: Math.hypot(a.x - b.x, a.y - b.y) || 1,
        scale,
      };

      dragStart = null;
      moved = true;
    }

    stage.classList.add("is-dragging");
  });

  stage.addEventListener("pointermove", (event) => {
    if (!pointers.has(event.pointerId)) {
      return;
    }

    pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (pointers.size === 2 && pinchStart) {
      const [a, b] = Array.from(pointers.values());

      const distance = Math.hypot(a.x - b.x, a.y - b.y);

      setScale(
        pinchStart.scale * (distance / pinchStart.distance),
        (a.x + b.x) / 2,
        (a.y + b.y) / 2,
      );

      return;
    }

    if (pointers.size === 1 && dragStart) {
      const dx = event.clientX - dragStart.x;
      const dy = event.clientY - dragStart.y;

      if (Math.abs(dx) + Math.abs(dy) > 5) {
        moved = true;
      }

      if (scale > 1) {
        tx = dragStart.tx + dx;
        ty = dragStart.ty + dy;

        clampPan();
        applyTransform();
      }
    }
  });

  function endPointer(event) {
    if (!pointers.has(event.pointerId)) {
      return;
    }

    const wasSingle = pointers.size === 1;

    pointers.delete(event.pointerId);

    if (wasSingle && dragStart && event.type === "pointerup") {
      const dx = event.clientX - dragStart.x;
      const dy = event.clientY - dragStart.y;

      if (!moved && !downOnImage) {
        closeLightbox();
      } else if (
        scale === 1 &&
        total > 1 &&
        Math.abs(dx) > 60 &&
        Math.abs(dx) > Math.abs(dy) * 1.5
      ) {
        showImage(dx > 0 ? index - 1 : index + 1);
      }
    }

    if (pointers.size < 2) {
      pinchStart = null;
    }

    if (pointers.size === 1) {
      const [remaining] = Array.from(pointers.values());

      dragStart = { x: remaining.x, y: remaining.y, tx, ty };
    } else {
      dragStart = null;
    }

    if (pointers.size === 0) {
      stage.classList.remove("is-dragging");
    }
  }

  stage.addEventListener("pointerup", endPointer);
  stage.addEventListener("pointercancel", endPointer);

  window.addEventListener("resize", () => {
    if (!overlay.hidden) {
      clampPan();
      applyTransform();
    }
  });
}

const DETAILS_ICONS = {
  pin: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-7-6.2-7-11a7 7 0 1 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>',
  calendar:
    '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M8 3v4M16 3v4"/></svg>',
  shield:
    '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l7 3v5c0 4.6-3 8.2-7 10-4-1.8-7-5.4-7-10V6z"/><path d="M9 12l2 2 4-4"/></svg>',
  image:
    '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="1.8"/><path d="M21 16l-5-5-8 9"/></svg>',
  zoom: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6"/><path d="M16 16l4 4M11 8v6M8 11h6"/></svg>',
  phone:
    '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"/></svg>',
};

// +48123456789 -> +48 123 456 789 (pozostałe numery bez zmian)
function formatDetailsPhone(phone) {
  const polish = /^\+48(\d{3})(\d{3})(\d{3})$/.exec(phone);

  return polish ? `+48 ${polish[1]} ${polish[2]} ${polish[3]}` : phone;
}

function renderOfferDetails(root, offer, images) {
  const hasPrice = offer.price != null;

  const price = hasPrice
    ? `${formatDetailsPrice(offer.price)} zł`
    : "Cena do uzgodnienia";

  const title = escapeDetailsHtml(offer.title);
  const categoryName = offer.categoryName || "Bez kategorii";
  const sellerName = offer.nickname || "Użytkownik";
  const sellerInitial = sellerName.trim().charAt(0).toUpperCase() || "U";
  const statusNotice = getDetailsStatusNotice(offer.status);
  const mapHtml = buildOfferMapHtml(offer);

  // Numer z profilu ogłaszającego - pokazujemy tylko, jeśli go podał.
  const sellerPhone = offer.sellerPhone ? String(offer.sellerPhone).trim() : "";

  const sellerPhoneHtml = sellerPhone
    ? `
      <a
        class="od-seller-phone"
        href="tel:${escapeDetailsHtml(sellerPhone.replace(/[^\d+]/g, ""))}"
        aria-label="Zadzwoń: ${escapeDetailsHtml(formatDetailsPhone(sellerPhone))}"
      >
        ${DETAILS_ICONS.phone}
        <span>${escapeDetailsHtml(formatDetailsPhone(sellerPhone))}</span>
      </a>
    `
    : "";

  document.title = `${offer.title} – JollyCart`;

  const breadcrumbCurrent = document.getElementById("offer-breadcrumb-current");

  if (breadcrumbCurrent) {
    breadcrumbCurrent.textContent = categoryName;
  }

  const imageHtml = images.length
    ? `
      <div class="offer-gallery-wrap">
        <div
          class="offer-gallery"
          tabindex="0"
          role="region"
          aria-roledescription="karuzela"
          aria-label="Zdjęcia ogłoszenia"
        >
          <div class="offer-gallery-track">
            ${images
              .map((image, index) => {
                const imageUrl = `${OFFER_DETAILS_API_BASE_URL}/api/offers/images/${encodeURIComponent(image.id)}`;

                return `
                  <div class="offer-gallery-slide" style="--slide-bg: url('${imageUrl}')">
                    <img
                      src="${imageUrl}"
                      alt="${title} – zdjęcie ${index + 1} z ${images.length}"
                      draggable="false"
                    />
                  </div>
                `;
              })
              .join("")}
          </div>

          ${
            statusNotice
              ? `<span class="offer-gallery-status is-${statusNotice.tone}">${escapeDetailsHtml(statusNotice.label)}</span>`
              : ""
          }

          <span class="offer-gallery-hint" aria-hidden="true">
            ${DETAILS_ICONS.zoom}
            Kliknij, aby powiększyć
          </span>

          ${
            images.length > 1
              ? `
                <button
                  type="button"
                  class="offer-gallery-arrow offer-gallery-prev"
                  aria-label="Poprzednie zdjęcie"
                >&#8249;</button>

                <button
                  type="button"
                  class="offer-gallery-arrow offer-gallery-next"
                  aria-label="Następne zdjęcie"
                >&#8250;</button>

                <div class="offer-gallery-counter" aria-live="polite">
                  1 / ${images.length}
                </div>
              `
              : ""
          }
        </div>

        ${
          images.length > 1
            ? `
              <div class="offer-gallery-thumbs">
                ${images
                  .map(
                    (image, index) => `
                      <button
                        type="button"
                        class="offer-gallery-thumb"
                        data-index="${index}"
                        aria-label="Pokaż zdjęcie ${index + 1} z ${images.length}"
                      >
                        <img
                          src="${OFFER_DETAILS_API_BASE_URL}/api/offers/images/${encodeURIComponent(image.id)}"
                          alt=""
                          loading="lazy"
                          draggable="false"
                        />
                      </button>
                    `,
                  )
                  .join("")}
              </div>
            `
            : ""
        }
      </div>
    `
    : `
      <div class="offer-details-no-image">
        ${DETAILS_ICONS.image}
        <p>Sprzedający nie dodał zdjęć</p>
        ${
          statusNotice
            ? `<span class="offer-gallery-status is-${statusNotice.tone}">${escapeDetailsHtml(statusNotice.label)}</span>`
            : ""
        }
      </div>
    `;

  const updatedAt =
    offer.updatedAt &&
    offer.createdAt &&
    formatDetailsLongDate(offer.updatedAt) !==
      formatDetailsLongDate(offer.createdAt)
      ? formatDetailsLongDate(offer.updatedAt)
      : null;

  root.innerHTML = `
    <div class="od-layout">
      <section class="od-gallery-card" aria-label="Zdjęcia">
        ${imageHtml}
      </section>

      <aside class="od-summary">
        <div class="od-card od-summary-card">
          <div class="od-badges">
            <span class="od-badge od-badge-${escapeDetailsHtml(String(offer.type || "").toLowerCase())}">
              ${escapeDetailsHtml(getDetailsTypeLabel(offer.type))}
            </span>

            <span class="od-category">${escapeDetailsHtml(categoryName)}</span>
          </div>

          ${
            statusNotice
              ? `<p class="od-status-notice is-${statusNotice.tone}">${escapeDetailsHtml(statusNotice.message)}</p>`
              : ""
          }

          <h1>${title}</h1>

          <div class="od-price-block">
            <span class="od-price-label">${escapeDetailsHtml(getDetailsPriceLabel(offer.type))}</span>
            <div class="od-price${hasPrice ? "" : " is-negotiable"}">
              ${escapeDetailsHtml(price)}
            </div>
          </div>

          <ul class="od-meta">
            <li>
              ${DETAILS_ICONS.pin}
              <span>${escapeDetailsHtml(offer.location || "Lokalizacja nie została podana")}</span>
            </li>

            <li>
              ${DETAILS_ICONS.calendar}
              <span>Dodano ${escapeDetailsHtml(formatDetailsLongDate(offer.createdAt))}</span>
            </li>
          </ul>

          <button
            id="send-message-button"
            class="send-message-button"
            type="button"
            style="display: none;"
          >
            Wyślij wiadomość
          </button>

          <button
            id="buy-button"
            class="send-message-button buy-button"
            type="button"
            style="display: none;"
          >
            Kup przez JollyCart
          </button>

          <p class="od-payment-note" id="buy-note" hidden></p>

          <p class="od-login-hint" id="od-login-hint" hidden>
            Chcesz napisać do ogłaszającego?
            <a href="login.html">Zaloguj się</a>
          </p>
        </div>

        <div class="od-card od-seller-card" data-profile-id="${escapeDetailsHtml(offer.userId)}" role="link" tabindex="0" title="Zobacz profil użytkownika">
          <div class="od-seller-avatar" aria-hidden="true">
            ${escapeDetailsHtml(sellerInitial)}
          </div>

          <div class="od-seller-info">
            <strong>${escapeDetailsHtml(sellerName)}</strong>
            <span>${escapeDetailsHtml(getDetailsSellerRole(offer.type))}</span>
            ${sellerPhoneHtml}
          </div>
        </div>

        <div class="od-safety">
          <h2>${DETAILS_ICONS.shield} ${escapeDetailsHtml(getDetailsSafety(offer.type).title)}</h2>
          <ul>
            ${getDetailsSafety(offer.type)
              .tips.map((tip) => `<li>${escapeDetailsHtml(tip)}</li>`)
              .join("")}
          </ul>
        </div>
      </aside>

      <div class="od-content">
        <section class="od-card od-description">
          <h2>Opis</h2>

          <p>${escapeDetailsHtml(offer.description || "Brak opisu.")}</p>
        </section>

        <section class="od-card od-details">
          <h2>Szczegóły ogłoszenia</h2>

          <dl class="od-details-list">
            <div>
              <dt>Kategoria</dt>
              <dd>${escapeDetailsHtml(categoryName)}</dd>
            </div>

            <div>
              <dt>Typ ogłoszenia</dt>
              <dd>${escapeDetailsHtml(getDetailsTypeLabel(offer.type))}</dd>
            </div>

            <div>
              <dt>Lokalizacja</dt>
              <dd>${escapeDetailsHtml(offer.location || "Nie podano")}</dd>
            </div>

            <div>
              <dt>Data dodania</dt>
              <dd>${escapeDetailsHtml(formatDetailsLongDate(offer.createdAt))}</dd>
            </div>

            ${
              updatedAt
                ? `
                  <div>
                    <dt>Ostatnia aktualizacja</dt>
                    <dd>${escapeDetailsHtml(updatedAt)}</dd>
                  </div>
                `
                : ""
            }

            <div>
              <dt>Numer ogłoszenia</dt>
              <dd>#${escapeDetailsHtml(String(offer.id))}</dd>
            </div>
          </dl>
        </section>

        ${mapHtml}
      </div>
    </div>
  `;
}

// Karta z mapą pokazuje się tylko dla ogłoszeń, które mają zapisany obszar
// (wybrany z podpowiedzi Google). Starsze ogłoszenia jej nie mają.
function buildOfferMapHtml(offer) {
  if (!offer.location || offer.latitude == null || offer.longitude == null) {
    return "";
  }

  const latitude = Number(offer.latitude);
  const longitude = Number(offer.longitude);

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return "";
  }

  const radius = Number(offer.locationRadiusMeters);
  const safeRadius = Number.isFinite(radius) && radius > 0 ? radius : 2000;

  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;

  return `
    <section
      class="od-card od-map"
      data-lat="${latitude}"
      data-lng="${longitude}"
      data-radius="${safeRadius}"
    >
      <h2>Na mapie</h2>

      <p class="od-map-caption">
        Zaznaczony obszar to ${escapeDetailsHtml(offer.location)}.
        Jest orientacyjny: dokładne miejsce ustalisz z ogłaszającym.
      </p>

      <div class="od-map-stage">
        <div class="od-map-placeholder">
          ${DETAILS_ICONS.pin}

          <button type="button" class="od-map-button">Pokaż na mapie</button>

          <p class="od-map-status" role="status" hidden></p>
        </div>
      </div>

      <a
        class="od-map-link"
        href="${mapsUrl}"
        target="_blank"
        rel="noopener noreferrer"
      >
        Otwórz w Google Maps
      </a>
    </section>
  `;
}

async function setupMessageButton(offer) {
  const button = document.getElementById("send-message-button");

  if (!button || !offer) {
    return;
  }

  try {
    const response = await fetch(`${OFFER_DETAILS_API_BASE_URL}/api/users/me`, {
      method: "GET",
      credentials: "include",
    });

    // Niezalogowany użytkownik nie dostaje przycisku,
    // tylko podpowiedź, żeby się zalogować.
    if (!response.ok) {
      button.style.display = "none";
      showDetailsLoginHint();
      return;
    }

    const currentUser = await response.json();

    if (!currentUser) {
      button.style.display = "none";
      showDetailsLoginHint();
      return;
    }

    /*
     * WŁAŚCICIEL OGŁOSZENIA
     *
     * Jeżeli użytkownik ogląda własne ogłoszenie,
     * pokazujemy "Chat", który otwiera stronę "Rozmowy".
     */
    if (Number(currentUser.id) === Number(offer.userId)) {
      button.style.display = "block";
      button.textContent = "Chat";

      // Właściciel przechodzi na stronę "Rozmowy",
      // gdzie widzi wszystkie swoje czaty.
      button.addEventListener("click", () => {
        window.location.href = "conversations.html";
      });

      return;
    }

    /*
     * CUDZE OGŁOSZENIE
     *
     * Pokazujemy "Wyślij wiadomość".
     */
    button.style.display = "block";
    button.textContent = "Wyślij wiadomość";

    button.addEventListener("click", async () => {
      button.disabled = true;
      button.textContent = "Otwieranie rozmowy...";

      try {
        const conversationResponse = await fetch(
          `${OFFER_DETAILS_API_BASE_URL}/api/conversations`,
          {
            method: "POST",
            credentials: "include",
            headers: {
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              otherUserId: offer.userId,
              offerId: offer.id,
            }),
          },
        );

        if (!conversationResponse.ok) {
          let errorMessage = `HTTP ${conversationResponse.status}`;

          try {
            const errorData = await conversationResponse.json();

            if (errorData.message) {
              errorMessage = errorData.message;
            }
          } catch (error) {
            // Backend nie zwrócił JSON-a.
          }

          throw new Error(errorMessage);
        }

        const conversation = await conversationResponse.json();

        if (!conversation || !conversation.id) {
          throw new Error("Backend nie zwrócił ID rozmowy.");
        }

        window.location.href =
          `chat.html?conversationId=${encodeURIComponent(conversation.id)}` +
          `&offerId=${encodeURIComponent(offer.id)}`;
      } catch (error) {
        console.error("Błąd otwierania rozmowy:", error);

        alert(error.message || "Nie udało się otworzyć rozmowy.");

        button.disabled = false;
        button.textContent = "Wyślij wiadomość";
      }
    });
  } catch (error) {
    console.error("Błąd sprawdzania zalogowanego użytkownika:", error);

    button.style.display = "none";
  }
}

function getDetailsPriceLabel(type) {
  return (
    {
      SALE: "Cena",
      SERVICE: "Cena usługi",
      JOB: "Wynagrodzenie",
    }[type] || "Cena"
  );
}

function getDetailsSellerRole(type) {
  return (
    {
      SALE: "Sprzedający",
      SERVICE: "Usługodawca",
      JOB: "Pracodawca",
    }[type] || "Ogłaszający"
  );
}

function getDetailsStatusNotice(status) {
  if (status === "SOLD") {
    return {
      tone: "sold",
      label: "Sprzedane",
      message: "To ogłoszenie zostało oznaczone jako sprzedane.",
    };
  }

  if (status === "RESERVED") {
    return {
      tone: "inactive",
      label: "Zarezerwowane",
      message:
        "Ktoś właśnie kupuje ten przedmiot. Oferta jest chwilowo niedostępna.",
    };
  }

  if (status === "INACTIVE") {
    return {
      tone: "inactive",
      label: "Nieaktywne",
      message: "To ogłoszenie jest obecnie nieaktywne.",
    };
  }

  return null;
}

function getDetailsSafety(type) {
  if (type === "JOB") {
    return {
      title: "Bezpieczna rekrutacja",
      tips: [
        "Nie płać za udział w rekrutacji ani za szkolenia.",
        "Nie wysyłaj skanów dokumentów bez sprawdzenia firmy.",
        "Pytaj o szczegóły umowy przed podjęciem pracy.",
      ],
    };
  }

  return {
    title: "Bezpieczna transakcja",
    tips: [
      "Umów się w publicznym, dobrze oświetlonym miejscu.",
      "Sprawdź przedmiot lub usługę przed zapłatą.",
      "Nie wysyłaj zaliczek nieznajomym.",
    ],
  };
}

function formatDetailsLongDate(value) {
  if (!value) {
    return "brak daty";
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? "brak daty"
    : date.toLocaleDateString("pl-PL", {
        day: "numeric",
        month: "long",
        year: "numeric",
      });
}

function showDetailsLoginHint() {
  document.getElementById("od-login-hint")?.removeAttribute("hidden");
}

function getDetailsTypeLabel(type) {
  return (
    {
      SALE: "Sprzedaż",
      SERVICE: "Usługa",
      JOB: "Oferta pracy",
    }[type] ||
    type ||
    "Ogłoszenie"
  );
}

function formatDetailsPrice(value) {
  return new Intl.NumberFormat("pl-PL", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(value));
}

function formatDetailsDate(value) {
  if (!value) {
    return "brak daty";
  }

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? "brak daty"
    : date.toLocaleDateString("pl-PL");
}

async function readDetailsResponse(response) {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return {
      message: text,
    };
  }
}

function escapeDetailsHtml(value) {
  const div = document.createElement("div");

  div.textContent = value ?? "";

  return div.innerHTML;
}

window.addEventListener("pageshow", (event) => {
  if (!event.persisted) {
    return;
  }

  window.location.reload();
});
