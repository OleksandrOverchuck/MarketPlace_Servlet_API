const PAYOUTS_API_BASE_URL = "http://localhost:8080";
const CONNECT_JS_URL = "https://connect-js.stripe.com/v1.0/connect.js";

// Stan zależny od statusu konta
let embeddedMode = "onboarding"; // "onboarding" = dodaj / dokończ, "manage" = zmień
let redirectMode = "onboarding"; // "onboarding" = formularz na stronie Stripe, "dashboard" = panel Stripe

document.addEventListener("DOMContentLoaded", initPayouts);

async function initPayouts() {
  const embeddedButton = document.getElementById("payouts-embedded-button");
  const redirectButton = document.getElementById("payouts-redirect-button");
  const closeButton = document.getElementById("payouts-embedded-close");

  if (!embeddedButton || !redirectButton) {
    return;
  }

  embeddedButton.addEventListener("click", openEmbedded);
  redirectButton.addEventListener("click", openRedirect);
  closeButton.addEventListener("click", closeEmbedded);

  // Powrót "wstecz" ze Stripe przywraca stronę z bfcache - odświeżamy stan.
  window.addEventListener("pageshow", async (event) => {
    if (event.persisted) {
      embeddedButton.disabled = false;
      redirectButton.disabled = false;
      showPayoutsMessage("", false);
      await refreshPayoutsStatus();
    }
  });

  const returned = new URLSearchParams(window.location.search).get("payouts");

  await refreshPayoutsStatus();

  if (returned === "return") {
    showPayoutsMessage(
      "Wróciłeś ze Stripe. Weryfikacja może potrwać chwilę, odśwież stronę za moment.",
      false,
    );
  }

  if (returned === "refresh") {
    showPayoutsMessage(
      "Link wygasł. Kliknij przycisk poniżej, aby kontynuować konfigurację.",
      true,
    );
  }
}

/* ---------------------------------------------------------------
   STATUS
--------------------------------------------------------------- */

async function refreshPayoutsStatus() {
  const statusEl = document.getElementById("payouts-status");
  const embeddedButton = document.getElementById("payouts-embedded-button");
  const redirectButton = document.getElementById("payouts-redirect-button");

  try {
    const response = await fetch(
      `${PAYOUTS_API_BASE_URL}/api/payments/seller/status`,
      { method: "GET", credentials: "include" },
    );

    const data = await response.json().catch(() => null);

    if (!response.ok || !data) {
      throw new Error(data?.message || "Nie udało się sprawdzić statusu.");
    }

    embeddedButton.hidden = false;
    redirectButton.hidden = false;

    if (data.payoutsReady) {
      embeddedMode = "manage";
      redirectMode = "dashboard";
      statusEl.textContent =
        "✔ Konto do wypłat jest aktywne. Możesz włączać płatność przez JollyCart w ogłoszeniach sprzedaży.";
      embeddedButton.textContent = "Zmień konto bankowe";
      redirectButton.textContent = "Otwórz panel Stripe";
    } else if (data.accountCreated && data.detailsSubmitted) {
      embeddedMode = "onboarding";
      redirectMode = "dashboard";
      statusEl.textContent =
        "Stripe weryfikuje Twoje dane. Zwykle trwa to chwilę, odśwież stronę za moment.";
      embeddedButton.hidden = true;
      redirectButton.hidden = true;
    } else if (data.accountCreated) {
      embeddedMode = "onboarding";
      redirectMode = "onboarding";
      statusEl.textContent = "Konfiguracja konta nie została dokończona.";
      embeddedButton.textContent = "Dokończ w aplikacji";
      redirectButton.textContent = "Dokończ na stronie Stripe";
    } else {
      embeddedMode = "onboarding";
      redirectMode = "onboarding";
      statusEl.textContent = "Nie dodano jeszcze konta bankowego.";
      embeddedButton.textContent = "Dodaj konto w aplikacji";
      redirectButton.textContent = "Dodaj konto przez Stripe";
    }
  } catch (error) {
    statusEl.textContent = error.message;
    embeddedButton.hidden = true;
    redirectButton.hidden = true;
  }
}

/* ---------------------------------------------------------------
   WARIANT 1: przekierowanie na stronę Stripe
--------------------------------------------------------------- */

async function openRedirect() {
  const button = document.getElementById("payouts-redirect-button");

  button.disabled = true;
  showPayoutsMessage("", false);

  const path =
    redirectMode === "dashboard"
      ? "/api/payments/seller/dashboard"
      : "/api/payments/seller/onboarding";

  try {
    const response = await fetch(`${PAYOUTS_API_BASE_URL}${path}`, {
      method: "POST",
      credentials: "include",
    });

    const data = await response.json().catch(() => null);

    if (!response.ok || !data?.url) {
      throw new Error(data?.message || "Nie udało się otworzyć konfiguracji.");
    }

    window.location.href = data.url;
  } catch (error) {
    showPayoutsMessage(error.message, true);
    button.disabled = false;
  }
}

/* ---------------------------------------------------------------
   WARIANT 2: formularz Stripe osadzony w aplikacji
--------------------------------------------------------------- */

function loadConnectJs() {
  return new Promise((resolve, reject) => {
    if (
      window.StripeConnect &&
      typeof window.StripeConnect.init === "function"
    ) {
      resolve();
      return;
    }

    // Stripe wywołuje StripeConnect.onLoad po załadowaniu skryptu.
    window.StripeConnect = window.StripeConnect || {};
    window.StripeConnect.onLoad = () => resolve();

    if (!document.querySelector(`script[src="${CONNECT_JS_URL}"]`)) {
      const script = document.createElement("script");
      script.src = CONNECT_JS_URL;
      script.async = true;
      script.onerror = () =>
        reject(new Error("Nie udało się załadować formularza Stripe."));
      document.head.appendChild(script);
    }
  });
}

async function fetchEmbeddedSession(mode) {
  const response = await fetch(
    `${PAYOUTS_API_BASE_URL}/api/payments/seller/embedded-session?mode=${encodeURIComponent(mode)}`,
    { method: "POST", credentials: "include" },
  );

  const data = await response.json().catch(() => null);

  if (!response.ok || !data?.clientSecret || !data?.publishableKey) {
    throw new Error(data?.message || "Nie udało się otworzyć formularza.");
  }

  return data;
}

async function openEmbedded() {
  const embeddedButton = document.getElementById("payouts-embedded-button");
  const redirectButton = document.getElementById("payouts-redirect-button");
  const wrapper = document.getElementById("payouts-embedded");
  const container = document.getElementById("payouts-embedded-container");

  embeddedButton.disabled = true;
  showPayoutsMessage("", false);

  const mode = embeddedMode;

  try {
    const [first] = await Promise.all([
      fetchEmbeddedSession(mode),
      loadConnectJs(),
    ]);

    // Pierwszy secret mamy już pobrany; przy odświeżeniu sesji pobieramy nowy.
    let firstSecret = first.clientSecret;

    const instance = window.StripeConnect.init({
      publishableKey: first.publishableKey,
      fetchClientSecret: async () => {
        if (firstSecret) {
          const secret = firstSecret;
          firstSecret = null;
          return secret;
        }
        return (await fetchEmbeddedSession(mode)).clientSecret;
      },
    });

    const component = instance.create(
      mode === "manage" ? "account-management" : "account-onboarding",
    );

    if (mode === "onboarding") {
      component.setOnExit(async () => {
        closeEmbedded();
        await refreshPayoutsStatus();
        showPayoutsMessage(
          "Dane zostały wysłane do Stripe. Weryfikacja może potrwać chwilę.",
          false,
        );
      });
    }

    container.innerHTML = "";
    container.appendChild(component);

    wrapper.hidden = false;
    embeddedButton.hidden = true;
    redirectButton.hidden = true;
  } catch (error) {
    showPayoutsMessage(error.message, true);
  } finally {
    embeddedButton.disabled = false;
  }
}

async function closeEmbedded() {
  document.getElementById("payouts-embedded").hidden = true;
  document.getElementById("payouts-embedded-container").innerHTML = "";
  await refreshPayoutsStatus();
}

/* --------------------------------------------------------------- */

function showPayoutsMessage(message, isError) {
  const el = document.getElementById("payouts-message");

  if (!el) {
    return;
  }

  el.textContent = message;
  el.style.color = isError ? "#a83e32" : "#093f38";
}
