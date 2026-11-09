const PAYMENT_FORM_API_BASE_URL = "http://localhost:8080";

let paymentFormPayoutsReady = false;

document.addEventListener("DOMContentLoaded", initOfferFormPayment);

async function initOfferFormPayment() {
  const typeSelect = document.getElementById("type");

  if (!document.getElementById("online-payment-field") || !typeSelect) {
    return;
  }

  typeSelect.addEventListener("change", applyOfferFormPaymentState);

  const paymentCheckbox = document.getElementById("online-payment");
  if (paymentCheckbox) {
    paymentCheckbox.addEventListener("change", updateShippingCostVisibility);
  }
  applyOfferFormPaymentState();

  try {
    const response = await fetch(
      `${PAYMENT_FORM_API_BASE_URL}/api/payments/seller/status`,
      { method: "GET", credentials: "include" },
    );

    if (response.ok) {
      const status = await response.json();
      paymentFormPayoutsReady = Boolean(status.payoutsReady);
    }
  } catch (error) {
    console.error("Nie udało się sprawdzić konta wypłat:", error);
  }

  applyOfferFormPaymentState();
}

function applyOfferFormPaymentState() {
  const field = document.getElementById("online-payment-field");
  const checkbox = document.getElementById("online-payment");
  const help = document.getElementById("online-payment-help");
  const typeSelect = document.getElementById("type");

  if (!field || !checkbox || !help || !typeSelect) {
    return;
  }

  const isSale = typeSelect.value === "SALE";

  field.hidden = !isSale;

  if (!isSale) {
    checkbox.checked = false;
    updateShippingCostVisibility();
    return;
  }

  // Zaznaczone pole zostaje aktywne, żeby dało się je odznaczyć.
  checkbox.disabled = !paymentFormPayoutsReady && !checkbox.checked;

  if (paymentFormPayoutsReady) {
    help.textContent =
      "Kupujący zapłaci kartą. Pieniądze trafią do Ciebie po potwierdzeniu odbioru towaru. Wymagana cena min. 2,00 zł.";
  } else {
    help.innerHTML =
      'Aby przyjmować płatności, <a href="settings.html#payouts">dodaj konto bankowe w ustawieniach</a>.';
  }

  updateShippingCostVisibility();
}

/* Koszt dostawy ma sens tylko, gdy zaznaczona jest płatność przez JollyCart. */
function updateShippingCostVisibility() {
  const checkbox = document.getElementById("online-payment");
  const box = document.getElementById("shipping-cost-box");
  const help = document.getElementById("shipping-cost-help");

  if (!box || !checkbox) {
    return;
  }

  const visible = checkbox.checked;
  box.hidden = !visible;

  if (help) {
    help.hidden = !visible;
  }
}

/* Zwraca liczbę >= 0 albo null, gdy pole jest puste / niepoprawne. */
function getShippingCostValue() {
  const input = document.getElementById("shipping-cost");
  const box = document.getElementById("shipping-cost-box");

  if (!input || !box || box.hidden) {
    return null;
  }

  const raw = input.value.trim().replace(",", ".");

  if (raw === "") {
    return 0;
  }

  const value = Number(raw);

  return Number.isFinite(value) && value >= 0 ? value : null;
}

function setShippingCostValue(value) {
  const input = document.getElementById("shipping-cost");

  if (input) {
    input.value = value && Number(value) > 0 ? value : "";
  }
}

function getOnlinePaymentValue() {
  const checkbox = document.getElementById("online-payment");
  const typeSelect = document.getElementById("type");

  return Boolean(
    checkbox && checkbox.checked && typeSelect && typeSelect.value === "SALE",
  );
}

function setOnlinePaymentValue(value) {
  const checkbox = document.getElementById("online-payment");

  if (checkbox) {
    checkbox.checked = Boolean(value);
  }

  applyOfferFormPaymentState();
}
