const BUY_API_BASE_URL = "http://localhost:8080";

async function setupBuyButton(offer) {
  const button = document.getElementById("buy-button");
  const note = document.getElementById("buy-note");

  if (!button || !offer) {
    return;
  }

  // Przycisk tylko dla ofert sprzedaży z włączoną płatnością online
  if (offer.type !== "SALE" || !offer.onlinePaymentEnabled) {
    return;
  }

  if (offer.status !== "ACTIVE" && offer.status !== "RESERVED") {
    return;
  }

  let currentUser = null;

  try {
    const response = await fetch(`${BUY_API_BASE_URL}/api/users/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.ok) {
      currentUser = await response.json();
    }
  } catch (error) {
    console.error("Nie udało się pobrać użytkownika:", error);
  }

  // Własnej oferty nie można kupić
  if (currentUser && Number(currentUser.id) === Number(offer.userId)) {
    return;
  }

  button.style.display = "block";

  if (offer.status === "RESERVED") {
    button.disabled = true;
    button.textContent = "Chwilowo niedostępne";

    if (note) {
      note.hidden = false;
      note.textContent =
        "Ktoś właśnie płaci za ten przedmiot. Jeśli płatność się nie powiedzie, oferta znów będzie dostępna.";
    }

    return;
  }

  if (note) {
    note.hidden = false;
    const shipping = Number(offer.shippingCost || 0);
    const shippingText =
      shipping > 0
        ? ` Dostawa: ${shipping.toLocaleString("pl-PL", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} zł.`
        : "";

    note.textContent =
      "Zapłać kartą - pieniądze trafią do sprzedawcy dopiero, gdy potwierdzisz odbiór towaru." +
      shippingText +
      " Pełny koszt (z opłatami) zobaczysz przed płatnością.";
  }

  button.addEventListener("click", () => {
    if (!currentUser) {
      window.location.href = "login.html";
      return;
    }

    openCheckoutModal(offer, currentUser, button);
  });
}

/* ---------- Okno z adresem dostawy (przed przejściem do Stripe) ---------- */

function openCheckoutModal(offer, currentUser, buyButton) {
  if (document.getElementById("checkout-modal")) {
    return;
  }

  const backdrop = document.createElement("div");
  backdrop.id = "checkout-modal";
  backdrop.className = "checkout-modal-backdrop";

  backdrop.innerHTML = `
    <div class="checkout-modal" role="dialog" aria-modal="true" aria-labelledby="checkout-modal-title">
      <h2 id="checkout-modal-title">Dane do wysyłki</h2>
      <p class="checkout-modal-lead">
        Podaj adres, na który sprzedawca wyśle towar. Po kliknięciu „Dalej”
        przejdziesz do płatności kartą.
      </p>

      <form id="checkout-form" novalidate>
        <label class="checkout-field">
          <span>Miasto</span>
          <input type="text" name="city" maxlength="100" autocomplete="address-level2" required />
        </label>

        <label class="checkout-field">
          <span>Ulica i numer domu / mieszkania</span>
          <input type="text" name="street" maxlength="150" autocomplete="street-address" required />
        </label>

        <div class="checkout-row">
          <label class="checkout-field">
            <span>Kod pocztowy</span>
            <input type="text" name="postalCode" inputmode="numeric" maxlength="6"
                   placeholder="00-000" autocomplete="postal-code" required />
          </label>

          <label class="checkout-field">
            <span>Numer telefonu</span>
            <input type="tel" name="phone" maxlength="20"
                   placeholder="600 123 456" autocomplete="tel" required />
          </label>
        </div>

        <div id="checkout-summary" class="checkout-summary" aria-live="polite">
          <p class="checkout-summary-loading">Liczymy koszty...</p>
        </div>

        <p id="checkout-error" class="checkout-error" role="alert" hidden></p>

        <div class="checkout-actions">
          <button type="button" class="order-button is-secondary" id="checkout-cancel">Anuluj</button>
          <button type="submit" class="order-button" id="checkout-submit">Dalej do płatności</button>
        </div>
      </form>
    </div>
  `;

  document.body.appendChild(backdrop);

  const form = backdrop.querySelector("#checkout-form");
  const errorBox = backdrop.querySelector("#checkout-error");
  const submit = backdrop.querySelector("#checkout-submit");
  const cancel = backdrop.querySelector("#checkout-cancel");

  // Tabela kosztów liczona przez serwer; "Dalej" aktywne dopiero po jej wczytaniu
  const summary = backdrop.querySelector("#checkout-summary");
  submit.disabled = true;
  loadCheckoutSummary(offer.id, summary, submit);

  // Telefon z profilu jako podpowiedź (kupujący może go zmienić)
  if (currentUser?.phone) {
    form.elements.phone.value = currentUser.phone;
  }

  // Kod pocztowy: same cyfry, myślnik dodawany automatycznie (00-000)
  form.elements.postalCode.addEventListener("input", (event) => {
    const digits = event.target.value.replace(/\D/g, "").slice(0, 5);
    event.target.value =
      digits.length > 2 ? `${digits.slice(0, 2)}-${digits.slice(2)}` : digits;
  });

  const close = () => {
    document.removeEventListener("keydown", onKeyDown);
    backdrop.remove();
  };

  const onKeyDown = (event) => {
    if (event.key === "Escape" && !cancel.disabled) {
      close();
    }
  };

  document.addEventListener("keydown", onKeyDown);
  cancel.addEventListener("click", close);
  backdrop.addEventListener("click", (event) => {
    if (event.target === backdrop && !cancel.disabled) {
      close();
    }
  });

  const showError = (message) => {
    errorBox.textContent = message;
    errorBox.hidden = !message;
  };

  form.elements.city.focus();

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    showError("");

    const city = form.elements.city.value.trim();
    const street = form.elements.street.value.trim();
    const postalCode = form.elements.postalCode.value.trim();
    const phone = form.elements.phone.value.trim();

    if (!city) {
      showError("Podaj miasto.");
      return;
    }

    if (!street) {
      showError("Podaj ulicę i numer domu.");
      return;
    }

    if (!/^\d{2}-\d{3}$/.test(postalCode)) {
      showError("Podaj kod pocztowy w formacie 00-000.");
      return;
    }

    if (!/^\+?\d{9,15}$/.test(phone.replace(/[\s\-()]/g, ""))) {
      showError("Podaj poprawny numer telefonu (np. 600 123 456).");
      return;
    }

    submit.disabled = true;
    cancel.disabled = true;
    submit.textContent = "Przekierowanie do płatności...";

    try {
      const response = await fetch(`${BUY_API_BASE_URL}/api/orders/checkout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          offerId: Number(offer.id),
          city,
          street,
          postalCode,
          phone,
        }),
      });

      const data = await response.json().catch(() => null);

      if (!response.ok || !data?.checkoutUrl) {
        throw new Error(data?.message || "Nie udało się rozpocząć płatności.");
      }

      window.location.href = data.checkoutUrl;
    } catch (error) {
      showError(error.message);
      submit.disabled = false;
      cancel.disabled = false;
      submit.textContent = "Dalej do płatności";
    }
  });
}

/* ---------- Podsumowanie płatności (tabela kosztów z backendu) ---------- */

function formatCheckoutMoney(value) {
  return `${Number(value).toLocaleString("pl-PL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })} zł`;
}

async function loadCheckoutSummary(offerId, container, submitButton) {
  try {
    const response = await fetch(
      `${BUY_API_BASE_URL}/api/orders/quote?offerId=${encodeURIComponent(offerId)}`,
      { method: "GET", credentials: "include" },
    );

    const quote = await response.json().catch(() => null);

    if (!response.ok || !quote) {
      throw new Error(quote?.message || "Nie udało się wyliczyć kosztów.");
    }

    const rows = [
      ["Cena towaru", quote.itemPrice],
      ["Dostawa", quote.shippingCost],
      ["Opłata serwisowa JollyCart (ochrona kupującego, 3%)", quote.serviceFee],
      ["Opłata operatora płatności (Stripe, szacunkowa)", quote.paymentFee],
    ];

    container.innerHTML = `
      <table class="checkout-summary-table">
        <thead>
          <tr><th>Pozycja</th><th>Kwota</th></tr>
        </thead>
        <tbody>
          ${rows
            .map(
              ([label, value]) =>
                `<tr><td>${label}</td><td>${formatCheckoutMoney(value)}</td></tr>`,
            )
            .join("")}
        </tbody>
        <tfoot>
          <tr><td>Razem do zapłaty</td><td>${formatCheckoutMoney(quote.total)}</td></tr>
        </tfoot>
      </table>
      <p class="checkout-summary-note">
        W razie uzasadnionego problemu z zamówieniem zwracamy całą wpłaconą kwotę.
      </p>
    `;

    submitButton.disabled = false;
  } catch (error) {
    container.innerHTML = "";
    const errorLine = document.createElement("p");
    errorLine.className = "checkout-error";
    errorLine.setAttribute("role", "alert");
    errorLine.textContent = error.message;
    container.appendChild(errorLine);
  }
}
