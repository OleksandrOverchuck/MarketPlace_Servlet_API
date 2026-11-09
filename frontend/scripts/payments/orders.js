const ORDERS_API_BASE_URL = "http://localhost:8080";

const ORDER_STATUS_LABELS = {
  PENDING_PAYMENT: "Oczekuje na płatność",
  PAID: "Opłacone - oczekuje na wysyłkę",
  SHIPPED: "Wysłane",
  DELIVERED: "Odebrane - czeka na potwierdzenie",
  COMPLETED: "Zakończone",
  DISPUTED: "Zgłoszony problem",
  CANCELLED: "Anulowane",
  REFUNDED: "Zwrócono pieniądze",
};

let ordersTab = "purchases";
let ordersRefreshTimer = null;

document.addEventListener("DOMContentLoaded", async () => {
  // Link z powiadomienia / maila: orders.html?tab=sales otwiera "Moja sprzedaż"
  const requestedTab = new URLSearchParams(window.location.search).get("tab");

  // Czego użytkownik jeszcze nie widział (czerwona kropka w menu)
  let unread = { purchases: 0, sales: 0 };

  try {
    unread = await ordersApi("/api/orders/unread-count", "GET");
  } catch (error) {
    // niezalogowany itp. - obsłuży to loadOrders()
  }

  let initialTab = null;

  if (requestedTab === "sales" || requestedTab === "purchases") {
    initialTab = requestedTab;
  } else if (unread.sales > 0 && unread.purchases === 0) {
    // Nowość dotyczy tylko sprzedaży - od razu pokazujemy "Moja sprzedaż"
    initialTab = "sales";
  }

  if (initialTab) {
    ordersTab = initialTab;
    document.querySelectorAll("[data-orders-tab]").forEach((t) => {
      t.classList.toggle("is-active", t.dataset.ordersTab === ordersTab);
    });
  }

  document.querySelectorAll("[data-orders-tab]").forEach((tab) => {
    tab.addEventListener("click", () => {
      ordersTab = tab.dataset.ordersTab;
      document
        .querySelectorAll("[data-orders-tab]")
        .forEach((t) => t.classList.toggle("is-active", t === tab));
      loadOrders();
    });
  });

  document
    .getElementById("orders-list")
    .addEventListener("click", handleOrderAction);

  await handleCheckoutReturn();
  await loadOrders();

  // Wejście w zamówienia = nowości zobaczone, gaśnie czerwona kropka w menu
  ordersApi("/api/orders/mark-seen", "POST").catch(() => {});
});

/* Powrót ze strony płatności Stripe: ?checkout=success|cancel&order=ID */
/* Powrót ze strony płatności Stripe: ?checkout=success|cancel&order=ID */
async function handleCheckoutReturn() {
  const params = new URLSearchParams(window.location.search);
  const checkout = params.get("checkout");
  const orderId = params.get("order");

  if (!checkout) {
    return;
  }

  // Czyścimy parametry, żeby odświeżenie strony nie powtórzyło akcji
  window.history.replaceState({}, "", window.location.pathname);

  if (checkout === "cancel" && orderId) {
    try {
      await ordersApi(
        `/api/orders/${encodeURIComponent(orderId)}/cancel`,
        "POST",
      );
      showOrdersBanner("Płatność anulowana. Oferta znów jest dostępna.", false);
    } catch (error) {
      showOrdersBanner(error.message, true);
    }
    return;
  }

  if (checkout === "success" && orderId) {
    showOrdersBanner("Sprawdzamy płatność...", false);

    // Backend pyta Stripe o stan sesji; zwykle wystarcza pierwsza próba.
    for (let attempt = 0; attempt < 8; attempt += 1) {
      try {
        const order = await ordersApi(
          `/api/orders/${encodeURIComponent(orderId)}/sync`,
          "POST",
        );

        if (order.status !== "PENDING_PAYMENT") {
          showOrdersBanner(
            `✔ Zamówienie zostało złożone! Płatność za „${order.offerTitle}" została przyjęta. ` +
              `Sprzedawca ma czas na wysyłkę do ${formatOrderDate(order.shipDeadline)}.`,
            false,
          );
          return;
        }
      } catch (error) {
        console.error("Synchronizacja płatności nie powiodła się:", error);
      }

      await new Promise((resolve) => setTimeout(resolve, 2000));
    }

    showOrdersBanner(
      "Płatność jest jeszcze przetwarzana. Odśwież stronę za chwilę.",
      false,
    );
  }
}

async function loadOrders() {
  const list = document.getElementById("orders-list");

  try {
    const orders = await ordersApi(`/api/orders/${ordersTab}`, "GET");

    if (!orders.length) {
      list.innerHTML = `<p class="orders-empty">${
        ordersTab === "purchases"
          ? "Nie masz jeszcze żadnych zakupów."
          : "Nie masz jeszcze żadnej sprzedaży przez JollyCart."
      }</p>`;
      return;
    }

    list.innerHTML = orders.map(renderOrderCard).join("");
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      window.location.href = "login.html";
      return;
    }
    list.innerHTML = `<p class="orders-empty">${escapeOrdersHtml(error.message)}</p>`;
  }
}

function renderOrderCard(order) {
  const isBuyer = ordersTab === "purchases";
  const counterpart = isBuyer
    ? `Sprzedający: ${order.sellerNickname}`
    : `Kupujący: ${order.buyerNickname}`;

  const lines = [];

  if (order.status === "PAID") {
    lines.push(
      isBuyer
        ? `Sprzedawca ma czas na wysyłkę do ${formatOrderDate(order.shipDeadline)}. Jeśli nie wyśle - zwrócimy Ci pieniądze.`
        : `Wyślij towar do ${formatOrderDate(order.shipDeadline)}. Po tym terminie kupujący dostanie zwrot.`,
    );
  }

  if (order.status === "SHIPPED") {
    lines.push(`Wysłano ${formatOrderDate(order.shippedAt)}.`);
  }

  if (order.status === "DELIVERED") {
    lines.push(
      isBuyer
        ? `Potwierdź lub zgłoś problem do ${formatOrderDate(order.confirmDeadline)} - inaczej środki zostaną wypłacone sprzedawcy.`
        : `Kupujący odebrał towar. Wypłata nastąpi po jego potwierdzeniu (najpóźniej ${formatOrderDate(order.confirmDeadline)}).`,
    );
  }

  if (order.status === "DISPUTED") {
    lines.push(
      `Zgłoszenie „${order.disputeReason || "problem z zamówieniem"}" czeka na rozpatrzenie. ` +
        "Środki są wstrzymane do decyzji administratora lub moderatora.",
    );
  }

  if (order.status === "COMPLETED") {
    lines.push(`Zakończono ${formatOrderDate(order.completedAt)}.`);
  }

  const shippingBlock = renderShippingBlock(order, isBuyer);

  return `
    <article class="order-card" data-order-id="${order.id}">
      <div class="order-card-top">
        <a class="order-title" href="offer-details.html?id=${encodeURIComponent(order.offerId)}">
          ${escapeOrdersHtml(order.offerTitle)}
        </a>
        <strong class="order-amount">${formatOrderAmount(isBuyer ? order.totalAmount : order.sellerPayout)} zł</strong>
      </div>

      <div class="order-meta">
        <span class="order-status is-${order.status.toLowerCase()}">
          ${escapeOrdersHtml(ORDER_STATUS_LABELS[order.status] || order.status)}
        </span>
        <span>${escapeOrdersHtml(counterpart)}</span>
        <span>Zamówiono ${formatOrderDate(order.createdAt)}</span>
      </div>

      ${lines.map((l) => `<p class="order-note">${escapeOrdersHtml(l)}</p>`).join("")}

      ${renderBreakdownBlock(order, isBuyer)}

      ${shippingBlock}

      <div class="order-actions">${renderOrderActions(order, isBuyer)}</div>
    </article>
  `;
}

/*
 * Rozbicie kwoty. Kupujący widzi to samo, co w tabeli przed płatnością
 * (Razem = kwota na stronie Stripe). Sprzedawca widzi, co do niego trafi.
 */
function renderBreakdownBlock(order, isBuyer) {
  const row = (label, value, cls = "") =>
    `<div class="order-breakdown-row ${cls}"><span>${label}</span><strong>${formatOrderAmount(value)} zł</strong></div>`;

  if (isBuyer) {
    return `
      <div class="order-breakdown">
        <h3>Rozbicie kwoty</h3>
        ${row("Cena towaru", order.amount)}
        ${row("Dostawa", order.shippingCost)}
        ${row("Opłata serwisowa JollyCart (ochrona kupującego)", order.serviceFee)}
        ${row("Opłata operatora płatności (Stripe, szacunkowa)", order.paymentFee)}
        ${row("Razem zapłacono", order.totalAmount, "is-total")}
      </div>
    `;
  }

  return `
    <div class="order-breakdown">
      <h3>Rozliczenie</h3>
      ${row("Cena towaru", order.amount)}
      ${row("Dostawa", order.shippingCost)}
      ${row("Do wypłaty dla Ciebie", order.sellerPayout, "is-total")}
    </div>
  `;
}

/* Dane do wysyłki: sprzedawca widzi adres kupującego, kupujący - swój adres. */
function renderShippingBlock(order, isBuyer) {
  if (!order.shippingStreet && !order.shippingCity) {
    return "";
  }

  const heading = isBuyer ? "Twój adres dostawy" : "Dane do wysyłki";
  const row = (label, value) =>
    `<div class="order-shipping-row"><span>${label}</span><strong>${escapeOrdersHtml(value)}</strong></div>`;

  return `
    <div class="order-shipping">
      <h3>${heading}</h3>
      ${isBuyer ? "" : row("Odbiorca", order.buyerNickname)}
      ${row("Ulica", order.shippingStreet)}
      ${row("Kod pocztowy i miasto", `${order.shippingPostalCode || ""} ${order.shippingCity || ""}`.trim())}
      ${row("Telefon", order.shippingPhone)}
    </div>
  `;
}

function renderOrderActions(order, isBuyer) {
  const btn = (action, label, cls = "") =>
    `<button type="button" class="order-button ${cls}" data-order-action="${action}">${label}</button>`;

  if (isBuyer) {
    switch (order.status) {
      case "PENDING_PAYMENT":
        return (
          (order.checkoutUrl
            ? `<a class="order-button" href="${escapeOrdersHtml(order.checkoutUrl)}">Dokończ płatność</a>`
            : "") + btn("cancel", "Anuluj", "is-secondary")
        );
      case "SHIPPED":
        return (
          btn("received", "Otrzymałem towar") +
          btn("dispute", "Zgłoś problem", "is-danger")
        );
      case "DELIVERED":
        return (
          btn("confirm", "Wszystko OK - zwolnij środki") +
          btn("dispute", "Zgłoś problem", "is-danger")
        );
      default:
        return "";
    }
  }

  if (order.status === "PAID") {
    return btn("ship", "Oznacz jako wysłane");
  }

  return "";
}

async function handleOrderAction(event) {
  const button = event.target.closest("[data-order-action]");

  if (!button) {
    return;
  }

  const card = button.closest("[data-order-id]");
  const orderId = card.dataset.orderId;
  const action = button.dataset.orderAction;

  let body = null;

  // Zgłoszenie problemu: okno z tytułem, opisem i zdjęciami
  if (action === "dispute") {
    const orderTitle =
      card.querySelector(".order-title")?.textContent.trim() || "";

    openDisputeModal(orderId, orderTitle);
    return;
  }

  if (
    action === "confirm" &&
    !window.confirm(
      "Potwierdzasz, że towar jest w porządku? Środki trafią do sprzedawcy.",
    )
  ) {
    return;
  }

  button.disabled = true;

  try {
    await ordersApi(
      `/api/orders/${encodeURIComponent(orderId)}/${action}`,
      "POST",
      body,
    );
    showOrdersBanner("Zapisano.", false);
  } catch (error) {
    showOrdersBanner(error.message, true);
  }

  await loadOrders();
}

/* =========================
   OKNO "ZGŁOŚ PROBLEM"
   ========================= */

const DISPUTE_MAX_IMAGES = 5;
const DISPUTE_MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const DISPUTE_MAX_TITLE = 150;
const DISPUTE_MAX_DESCRIPTION = 2000;

function openDisputeModal(orderId, orderTitle) {
  if (document.getElementById("dispute-modal")) {
    return;
  }

  const files = [];

  const overlay = document.createElement("div");

  overlay.id = "dispute-modal";
  overlay.className = "dispute-modal";

  overlay.innerHTML = `
    <div class="dispute-dialog" role="dialog" aria-modal="true" aria-labelledby="dispute-heading">
      <form class="dispute-form" novalidate>
        <h2 id="dispute-heading">Zgłoś problem z zamówieniem</h2>

        <p class="dispute-order">${escapeOrdersHtml(orderTitle)}</p>

        <p class="dispute-info">
          Po wysłaniu zgłoszenia środki zostaną wstrzymane. Administrator lub
          moderator przejrzy opis i zdjęcia, a następnie zdecyduje, czy zwrócić
          pieniądze Tobie, czy wypłacić je sprzedawcy.
        </p>

        <label class="dispute-field">
          <span>Tytuł zgłoszenia <b>*</b></span>
          <input type="text" name="title" maxlength="${DISPUTE_MAX_TITLE}"
                 placeholder="Np. Towar nie działa / inny niż w opisie" autocomplete="off" />
        </label>

        <label class="dispute-field">
          <span>Opis problemu <b>*</b></span>
          <textarea name="description" rows="6" maxlength="${DISPUTE_MAX_DESCRIPTION}"
                    placeholder="Opisz dokładnie, co jest nie tak z towarem lub przesyłką..."></textarea>
          <small class="dispute-counter" data-dispute-counter>0 / ${DISPUTE_MAX_DESCRIPTION}</small>
        </label>

        <div class="dispute-field">
          <span>Zdjęcia (opcjonalnie)</span>

          <div class="dispute-previews" data-dispute-previews></div>

          <button type="button" class="order-button is-secondary" data-dispute-add>
            Dodaj zdjęcia
          </button>

          <input type="file" data-dispute-file multiple hidden
                 accept=".png,.jpg,.jpeg,image/png,image/jpeg" />

          <small class="dispute-hint">
            Do ${DISPUTE_MAX_IMAGES} zdjęć, PNG lub JPG, maks. 5 MB każde.
          </small>
        </div>

        <p class="dispute-error" data-dispute-error role="alert" hidden></p>

        <div class="dispute-buttons">
          <button type="button" class="order-button is-secondary" data-dispute-cancel>
            Anuluj
          </button>
          <button type="submit" class="order-button is-danger" data-dispute-submit>
            Wyślij zgłoszenie
          </button>
        </div>
      </form>
    </div>
  `;

  document.body.appendChild(overlay);
  document.body.classList.add("dispute-modal-open");

  const form = overlay.querySelector("form");
  const titleInput = form.elements.title;
  const descriptionInput = form.elements.description;
  const counter = overlay.querySelector("[data-dispute-counter]");
  const fileInput = overlay.querySelector("[data-dispute-file]");
  const previews = overlay.querySelector("[data-dispute-previews]");
  const addButton = overlay.querySelector("[data-dispute-add]");
  const errorBox = overlay.querySelector("[data-dispute-error]");
  const submitButton = overlay.querySelector("[data-dispute-submit]");
  const cancelButton = overlay.querySelector("[data-dispute-cancel]");

  let submitting = false;

  function showError(message) {
    errorBox.textContent = message || "";
    errorBox.hidden = !message;
  }

  function close() {
    if (submitting) {
      return;
    }

    previews
      .querySelectorAll("img")
      .forEach((img) => URL.revokeObjectURL(img.src));

    document.removeEventListener("keydown", onKeydown);
    document.body.classList.remove("dispute-modal-open");
    overlay.remove();
  }

  function onKeydown(event) {
    if (event.key === "Escape") {
      close();
    }
  }

  function renderPreviews() {
    previews
      .querySelectorAll("img")
      .forEach((img) => URL.revokeObjectURL(img.src));

    previews.replaceChildren();

    files.forEach((file, index) => {
      const item = document.createElement("div");

      item.className = "dispute-preview";

      const img = document.createElement("img");

      img.src = URL.createObjectURL(file);
      img.alt = file.name;

      const remove = document.createElement("button");

      remove.type = "button";
      remove.className = "dispute-preview-remove";
      remove.setAttribute("aria-label", `Usuń zdjęcie ${file.name}`);
      remove.textContent = "×";
      remove.addEventListener("click", () => {
        files.splice(index, 1);
        renderPreviews();
      });

      item.append(img, remove);
      previews.appendChild(item);
    });

    addButton.disabled = files.length >= DISPUTE_MAX_IMAGES;
  }

  function addFiles(selected) {
    showError("");

    for (const file of selected) {
      const name = file.name.toLowerCase();
      const typeOk = file.type === "image/png" || file.type === "image/jpeg";
      const extOk =
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");

      if (!typeOk || !extOk) {
        showError(`„${file.name}": dozwolone są tylko pliki PNG i JPG.`);
        continue;
      }

      if (file.size > DISPUTE_MAX_IMAGE_BYTES) {
        showError(`„${file.name}" jest większe niż 5 MB.`);
        continue;
      }

      if (files.length >= DISPUTE_MAX_IMAGES) {
        showError(`Możesz dodać najwyżej ${DISPUTE_MAX_IMAGES} zdjęć.`);
        break;
      }

      files.push(file);
    }

    renderPreviews();
  }

  addButton.addEventListener("click", () => fileInput.click());

  fileInput.addEventListener("change", () => {
    addFiles(Array.from(fileInput.files));
    fileInput.value = "";
  });

  descriptionInput.addEventListener("input", () => {
    counter.textContent = `${descriptionInput.value.length} / ${DISPUTE_MAX_DESCRIPTION}`;
  });

  cancelButton.addEventListener("click", close);

  overlay.addEventListener("mousedown", (event) => {
    if (event.target === overlay) {
      close();
    }
  });

  document.addEventListener("keydown", onKeydown);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (submitting) {
      return;
    }

    const title = titleInput.value.trim();
    const description = descriptionInput.value.trim();

    if (!title) {
      showError("Podaj tytuł zgłoszenia.");
      titleInput.focus();
      return;
    }

    if (!description) {
      showError("Opisz, jaki jest problem.");
      descriptionInput.focus();
      return;
    }

    const formData = new FormData();

    formData.append("title", title);
    formData.append("description", description);
    files.forEach((file) => formData.append("files", file, file.name));

    submitting = true;
    submitButton.disabled = true;
    cancelButton.disabled = true;
    submitButton.textContent = "Wysyłanie...";
    showError("");

    try {
      const response = await fetch(
        `${ORDERS_API_BASE_URL}/api/orders/${encodeURIComponent(orderId)}/dispute`,
        {
          method: "POST",
          credentials: "include",
          // Bez Content-Type - przeglądarka sama doda boundary dla multipart
          body: formData,
        },
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        const error = new Error(
          data?.message ||
            (response.status === 413
              ? "Zdjęcia są za duże. Zmniejsz ich liczbę lub rozmiar."
              : "Nie udało się wysłać zgłoszenia."),
        );

        error.status = response.status;
        throw error;
      }

      submitting = false;
      close();

      showOrdersBanner(
        "Zgłoszenie zostało wysłane. Środki są wstrzymane do decyzji administratora lub moderatora.",
        false,
      );

      await loadOrders();
    } catch (error) {
      if (error.status === 401 || error.status === 403) {
        window.location.href = "login.html";
        return;
      }

      submitting = false;
      submitButton.disabled = false;
      cancelButton.disabled = false;
      submitButton.textContent = "Wyślij zgłoszenie";
      showError(error.message);
    }
  });

  renderPreviews();
  titleInput.focus();
}

async function ordersApi(path, method, body) {
  const response = await fetch(`${ORDERS_API_BASE_URL}${path}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    credentials: "include",
    body: body ? JSON.stringify(body) : undefined,
  });

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(data?.message || "Operacja nie powiodła się.");
    error.status = response.status;
    throw error;
  }

  return data;
}

function showOrdersBanner(message, isError) {
  const banner = document.getElementById("orders-banner");

  banner.textContent = message;
  banner.hidden = !message;
  banner.classList.toggle("is-error", Boolean(isError));
}

function formatOrderAmount(value) {
  return Number(value).toLocaleString("pl-PL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatOrderDate(value) {
  if (!value) {
    return "-";
  }

  return new Date(value).toLocaleString("pl-PL", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function escapeOrdersHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
