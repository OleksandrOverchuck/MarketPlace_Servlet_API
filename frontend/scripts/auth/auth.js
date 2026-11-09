const API_BASE_URL = "http://localhost:8080";

// Sesja (ciasteczko JSESSIONID) działa tylko wtedy, gdy strona i API mają tę
// samą nazwę hosta. Dla przeglądarki 127.0.0.1 i localhost to dwie różne
// strony, więc po wejściu przez 127.0.0.1 logowanie "nie trzyma się".
// Dlatego przełączamy się automatycznie na localhost.
if (window.location.hostname === "127.0.0.1") {
  window.location.replace(
    window.location.href.replace("//127.0.0.1", "//localhost"),
  );
}

document.addEventListener("DOMContentLoaded", () => {
  setupLoginPage();
  setupRegisterPage();
  setupHomePage();
  setupSearchShortcut();
});

function setupSearchShortcut() {
  const searchLink = document.querySelector("[data-focus-search]");
  const searchInput = document.querySelector(".search-box input");

  if (!searchLink || !searchInput) {
    return;
  }

  searchLink.addEventListener("click", () => {
    setTimeout(() => {
      searchInput.focus({ preventScroll: true });
    }, 600);
  });
}

/* =========================
   LOGOWANIE
   ========================= */

function setupLoginPage() {
  const loginForm = document.querySelector(".auth-form");
  const googleButton = document.querySelector(".oauth-btn");

  /*
   * Jeżeli na stronie jest formularz logowania,
   * podpinamy obsługę logowania.
   */
  if (
    loginForm &&
    document.getElementById("email") &&
    document.getElementById("password") &&
    !document.getElementById("username")
  ) {
    loginForm.addEventListener("submit", handleLogin);
  }

  /*
   * Google działa zarówno na login.html,
   * jak i na register.html.
   */
  if (googleButton) {
    googleButton.addEventListener("click", handleGoogleLogin);
  }

  const oauthError = new URLSearchParams(window.location.search).get("error");

  if (oauthError === "account_banned") {
    showAuthMessage("Twoje konto zostało zablokowane.", true);
  } else if (oauthError) {
    showAuthMessage("Logowanie przez Google nie powiodło się.", true);
  }
}

async function handleLogin(event) {
  event.preventDefault();

  const emailInput = document.getElementById("email");
  const passwordInput = document.getElementById("password");

  if (!emailInput || !passwordInput) {
    return;
  }

  const email = emailInput.value.trim();
  const password = passwordInput.value;

  if (!email || !password) {
    showAuthMessage("Email i hasło są wymagane.", true);
    return;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        email: email,
        password: password,
      }),
    });

    if (!response.ok) {
      if (response.status === 401) {
        showAuthMessage("Nieprawidłowy email lub hasło.", true);
        return;
      }

      if (response.status === 403) {
        const banInfo = await response.json().catch(() => ({}));

        showAuthMessage(
          banInfo.reason
            ? `Konto zablokowane. Powód: ${banInfo.reason}`
            : banInfo.message || "Twoje konto zostało zablokowane.",
          true,
        );
        return;
      }

      showAuthMessage("Wystąpił błąd podczas logowania.", true);

      return;
    }

    const user = await response.json();

    console.log("Zalogowano użytkownika:", user);

    window.location.href = user.passwordChangeRequired
      ? "admin.html"
      : "index.html";
  } catch (error) {
    console.error("Błąd logowania:", error);

    showAuthMessage("Nie udało się połączyć z serwerem.", true);
  }
}

/* =========================
   GOOGLE
   ========================= */

function handleGoogleLogin() {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
}

/* =========================
   REJESTRACJA (2 kroki)
   1) formularz  -> backend wysyła kod na email
   2) kod z maila -> backend tworzy konto
   ========================= */

let pendingRegistrationEmail = null;
let resendTimerId = null;

function setupRegisterPage() {
  const registerForm = document.getElementById("register-form");

  if (!registerForm) {
    return;
  }

  registerForm.addEventListener("submit", handleRegister);

  const verifyForm = document.getElementById("verify-form");
  const resendButton = document.getElementById("resend-code-btn");
  const backButton = document.getElementById("verify-back-btn");

  if (verifyForm) {
    verifyForm.addEventListener("submit", handleVerifyCode);
  }

  if (resendButton) {
    resendButton.addEventListener("click", handleResendCode);
  }

  if (backButton) {
    backButton.addEventListener("click", showRegisterStep);
  }
}

async function postJson(path, body) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(body),
  });

  const text = await response.text();

  let data = null;

  if (text) {
    try {
      data = JSON.parse(text);
    } catch (error) {
      data = null;
    }
  }

  return { response, data, text };
}

function extractErrorMessage(result, fallback) {
  const { response, data } = result;

  if (data && typeof data === "object") {
    if (data.message) {
      return data.message;
    }

    // błędy walidacji: { pole: "komunikat", ... }
    const messages = Object.values(data).filter(
      (value) => typeof value === "string",
    );

    if (messages.length > 0) {
      return messages.join(" ");
    }
  }

  return `${fallback} (kod HTTP: ${response.status})`;
}

async function handleRegister(event) {
  event.preventDefault();

  const nickname = document.getElementById("username").value.trim();
  const email = document.getElementById("email").value.trim();
  const password = document.getElementById("password").value;
  const confirmPassword = document.getElementById("confirmPassword").value;

  if (!nickname || !email || !password || !confirmPassword) {
    showAuthMessage("Wypełnij wszystkie pola.", true);
    return;
  }

  if (password !== confirmPassword) {
    showAuthMessage("Hasła nie są takie same.", true);
    return;
  }

  const submitButton = event.target.querySelector("button[type='submit']");
  submitButton.disabled = true;

  try {
    const result = await postJson("/api/auth/register", {
      nickname: nickname,
      email: email,
      password: password,
      confirmPassword: confirmPassword,
    });

    if (!result.response.ok) {
      showAuthMessage(
        extractErrorMessage(result, "Rejestracja nie powiodła się"),
        true,
      );
      return;
    }

    pendingRegistrationEmail = result.data?.email || email;

    showVerifyStep(result.data?.resendCooldownSeconds ?? 60);
  } catch (error) {
    console.error("Błąd rejestracji:", error);

    showAuthMessage("Nie udało się połączyć z serwerem.", true);
  } finally {
    submitButton.disabled = false;
  }
}

async function handleVerifyCode(event) {
  event.preventDefault();

  const code = document.getElementById("verificationCode").value.trim();

  if (!pendingRegistrationEmail) {
    showRegisterStep();
    return;
  }

  if (!/^\d{4,8}$/.test(code)) {
    showAuthMessage("Kod składa się wyłącznie z cyfr.", true);
    return;
  }

  const submitButton = event.target.querySelector("button[type='submit']");
  submitButton.disabled = true;

  try {
    const result = await postJson("/api/auth/verify-email", {
      email: pendingRegistrationEmail,
      code: code,
    });

    if (!result.response.ok) {
      showAuthMessage(
        extractErrorMessage(result, "Nie udało się potwierdzić kodu"),
        true,
      );
      return;
    }

    showAuthMessage(
      "Konto zostało utworzone. Za chwilę przejdziesz do logowania.",
      false,
    );

    setTimeout(() => {
      window.location.href = "login.html";
    }, 1200);
  } catch (error) {
    console.error("Błąd weryfikacji kodu:", error);

    showAuthMessage("Nie udało się połączyć z serwerem.", true);
  } finally {
    submitButton.disabled = false;
  }
}

async function handleResendCode() {
  if (!pendingRegistrationEmail) {
    showRegisterStep();
    return;
  }

  const resendButton = document.getElementById("resend-code-btn");
  resendButton.disabled = true;

  try {
    const result = await postJson("/api/auth/resend-code", {
      email: pendingRegistrationEmail,
    });

    if (!result.response.ok) {
      showAuthMessage(
        extractErrorMessage(result, "Nie udało się wysłać kodu"),
        true,
      );

      // np. "Odczekaj X s" - przycisk wraca po krótkiej chwili
      startResendCooldown(5);
      return;
    }

    showAuthMessage("Wysłaliśmy nowy kod na Twój email.", false);

    startResendCooldown(result.data?.resendCooldownSeconds ?? 60);
  } catch (error) {
    console.error("Błąd ponownego wysyłania kodu:", error);

    showAuthMessage("Nie udało się połączyć z serwerem.", true);

    resendButton.disabled = false;
  }
}

function showVerifyStep(cooldownSeconds) {
  document.getElementById("register-form").hidden = true;
  document.getElementById("verify-form").hidden = false;

  document.getElementById("verify-email-label").textContent =
    pendingRegistrationEmail;

  const codeInput = document.getElementById("verificationCode");
  codeInput.value = "";
  codeInput.focus();

  showAuthMessage(
    "Kod został wysłany. Sprawdź swoją skrzynkę (także SPAM).",
    false,
  );

  startResendCooldown(cooldownSeconds);
}

function showRegisterStep() {
  clearInterval(resendTimerId);

  document.getElementById("verify-form").hidden = true;
  document.getElementById("register-form").hidden = false;

  const messageElement = document.querySelector(".auth-message");

  if (messageElement) {
    messageElement.textContent = "";
  }
}

function startResendCooldown(seconds) {
  const resendButton = document.getElementById("resend-code-btn");

  clearInterval(resendTimerId);

  let left = Number(seconds) || 0;

  const render = () => {
    if (left > 0) {
      resendButton.disabled = true;
      resendButton.textContent = `Wyślij kod ponownie (${left} s)`;
    } else {
      resendButton.disabled = false;
      resendButton.textContent = "Wyślij kod ponownie";
      clearInterval(resendTimerId);
    }
  };

  render();

  resendTimerId = setInterval(() => {
    left -= 1;
    render();
  }, 1000);
}

/* =========================
   POBIERANIE BŁĘDU Z BACKENDU
   ========================= */

async function readErrorMessage(response) {
  try {
    const data = await response.json();

    if (typeof data === "string") {
      return data;
    }

    if (data.message) {
      return data.message;
    }

    if (data.error) {
      return data.error;
    }

    return "";
  } catch (error) {
    return "";
  }
}

/* =========================
   AKTUALNY UŻYTKOWNIK
   ========================= */

function setupHomePage() {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  loadCurrentUser();
  loadActiveOffersCount();
}

async function loadActiveOffersCount() {
  const countElement = document.getElementById("active-offers-count");

  if (!countElement) {
    return;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/offers/count`, {
      method: "GET",
    });

    if (!response.ok) {
      console.error("Nie udało się pobrać liczby aktywnych ogłoszeń.");
      return;
    }

    const count = await response.json();

    animateCount(countElement, Number(count));
  } catch (error) {
    console.error("Błąd pobierania liczby aktywnych ogłoszeń:", error);
  }
}

// Płynne "dolicz" od 0 do wartości docelowej.
function animateCount(element, target) {
  if (!Number.isFinite(target) || target <= 0) {
    element.textContent = "0";
    return;
  }

  const duration = 800;
  const start = performance.now();

  function step(now) {
    const progress = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);

    element.textContent = Math.round(target * eased).toLocaleString("pl-PL");

    if (progress < 1) {
      requestAnimationFrame(step);
    }
  }

  requestAnimationFrame(step);
}

async function loadCurrentUser() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      method: "GET",
      credentials: "include",
    });

    const authUserElement = document.getElementById("auth-user");

    if (!authUserElement) {
      return;
    }

    if (response.ok) {
      const user = await response.json();

      displayLoggedUser(user);

      return;
    }

    displayGuestUser();
  } catch (error) {
    console.error("Błąd pobierania użytkownika:", error);

    displayGuestUser();
  }
}

function displayLoggedUser(user) {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  document.documentElement.classList.add("logged-in");

  localStorage.setItem("loggedIn", "1");

  authUserElement.innerHTML = `
    <button
      type="button"
      class="account-button"
      aria-label="Twoje konto"
    >
      <svg
        class="account-icon"
        viewBox="0 0 24 24"
      >
        <circle
          cx="12"
          cy="8"
          r="4"
        ></circle>

        <path
          d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"
        ></path>
      </svg>

      <span>Twoje konto</span>

      <span class="account-arrow"></span>

      <span class="orders-dot orders-dot-button" hidden></span>
    </button>

    <div class="account-dropdown">
      <div class="account-user">
        <div class="account-avatar">
          ${renderAvatar(user)}
        </div>

        <div class="account-user-info">
          <p class="account-nickname">
            ${escapeHtml(user.nickname)}
          </p>

          <p class="account-id">
            id: ${escapeHtml(String(user.id))}
          </p>
        </div>
      </div>

      <div class="account-menu-title">
        Twoje konto
      </div>

      <div class="account-menu">

       <a
          href="settings.html"
          class="account-menu-item"
        >
          Profil
        </a>

        <a
          href="post-ad.html"
          class="account-menu-item account-menu-cta"
        >
          Dodaj ogłoszenie
        </a>

        <a
          href="my-offers.html"
          class="account-menu-item"
        >
          Moje ogłoszenia
        </a>

        <a
          href="conversations.html"
          class="account-menu-item"
        >
          Rozmowy
        </a>

                <a
          href="orders.html"
          class="account-menu-item"
        >
          Zamówienia
          <span class="orders-dot" hidden></span>
        </a>

        ${
          user.role === "ADMIN" || user.role === "MODERATOR"
            ? `<a
          href="admin.html"
          class="account-menu-item"
        >
          ${user.role === "ADMIN" ? "Panel administratora" : "Panel moderatora"}
        </a>`
            : ""
        }

        <a
          href="reviews.html"
          class="account-menu-item"
        >
          Oceny
        </a>
    
      </div>

      <div class="account-logout-wrapper">
        <button
          type="button"
          class="account-menu-item account-logout"
          id="logout-button"
        >
          Wyloguj
        </button>
      </div>
    </div>
  `;

  const logoutButton = document.getElementById("logout-button");

  if (logoutButton) {
    logoutButton.addEventListener("click", handleLogout);
  }

  setupAccountMenuToggle(authUserElement);

  setupNotifications();

  setupOrdersDot();
}

/*
   Czerwona kropka przy "Zamówienia": jest nowa sprzedaż (dla sprzedawcy)
   albo przesyłka wysłana (dla kupującego). Gaśnie po wejściu w zamówienia.
*/
function setupOrdersDot() {
  const link = document.querySelector('.account-menu-item[href="orders.html"]');
  const dots = document.querySelectorAll(".orders-dot");

  if (!link || dots.length === 0) {
    return;
  }

  // Na stronie zamówień kropkę gasi sama strona (orders.js)
  if (window.location.pathname.endsWith("/orders.html")) {
    return;
  }

  const setDots = (visible) => {
    dots.forEach((dot) => {
      dot.hidden = !visible;
    });
  };

  async function refreshOrdersDot() {
    try {
      const response = await fetch(`${API_BASE_URL}/api/orders/unread-count`, {
        method: "GET",
        credentials: "include",
      });

      if (!response.ok) {
        return;
      }

      const data = await response.json();

      setDots(Number(data.purchases) + Number(data.sales) > 0);
    } catch (error) {
      // brak kropki to nie błąd krytyczny
    }
  }

  link.addEventListener("click", () => setDots(false));

  refreshOrdersDot();
  window.setInterval(refreshOrdersDot, 15000);
}

/*
   Na małych ekranach menu konta otwiera się po kliknięciu
   (klasa .open). Na desktopie nadal działa hover - CSS reaguje
   na .open tylko w widoku mobilnym.
*/
let accountMenuGlobalListenersReady = false;

function setupAccountMenuToggle(authUserElement) {
  const accountButton = authUserElement.querySelector(".account-button");

  if (accountButton) {
    accountButton.addEventListener("click", () => {
      authUserElement.classList.toggle("open");
    });
  }

  if (accountMenuGlobalListenersReady) {
    return;
  }

  accountMenuGlobalListenersReady = true;

  // capture = true, żeby zamykało się także gdy inny element
  // woła stopPropagation (np. przycisk Wiadomości)
  document.addEventListener(
    "click",
    (event) => {
      const authUser = document.getElementById("auth-user");

      if (authUser && !authUser.contains(event.target)) {
        authUser.classList.remove("open");
      }
    },
    true,
  );

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      const authUser = document.getElementById("auth-user");

      if (authUser) {
        authUser.classList.remove("open");
      }
    }
  });
}

function displayGuestUser() {
  const authUserElement = document.getElementById("auth-user");

  if (!authUserElement) {
    return;
  }

  document.documentElement.classList.remove("logged-in");

  localStorage.removeItem("loggedIn");

  authUserElement.innerHTML = "";

  const loginLink = document.createElement("a");

  loginLink.href = "login.html";
  loginLink.textContent = "Logowanie";
  loginLink.className = "auth-login-link";

  authUserElement.appendChild(loginLink);
}

/* =========================
   WYLOGOWANIE
   ========================= */

async function handleLogout() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
    });

    if (response.ok || response.status === 204) {
      localStorage.removeItem("loggedIn");

      window.location.href = "index.html";

      return;
    }

    alert("Nie udało się wylogować.");
  } catch (error) {
    console.error("Błąd wylogowania:", error);

    alert("Nie udało się połączyć z serwerem.");
  }
}

/* =========================
   POMOCNICZE
   ========================= */

function getAvatarSrc(avatarUrl) {
  if (!avatarUrl) {
    return null;
  }

  if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
    return avatarUrl;
  }

  return `${API_BASE_URL}${avatarUrl}`;
}

function getDefaultAvatarSvg() {
  return `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="4"></circle>
      <path d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"></path>
    </svg>
  `;
}

function renderAvatar(user) {
  const avatarSrc = getAvatarSrc(user.avatarUrl);

  if (avatarSrc) {
    return `
      <img
        src="${escapeHtml(avatarSrc)}"
        alt="Zdjęcie profilowe"
        class="account-avatar-image"
        onerror="handleAvatarError(this)"
      />
    `;
  }

  // Brak zdjęcia: domyślna ikonka profilu
  return getDefaultAvatarSvg();
}

// Zdjęcie jest w bazie, ale pliku nie da się wczytać (np. brak go na dysku):
// zamiast uszkodzonego obrazka z napisem pokazujemy domyślną ikonkę.
function handleAvatarError(img) {
  img.onerror = null;
  img.outerHTML = getDefaultAvatarSvg();
}

function escapeHtml(value) {
  const div = document.createElement("div");

  div.textContent = value;

  return div.innerHTML;
}

function showAuthMessage(message, isError) {
  let messageElement = document.querySelector(".auth-message");

  if (!messageElement) {
    messageElement = document.createElement("p");

    messageElement.className = "auth-message";

    const form = document.querySelector(".auth-form");

    if (form) {
      form.insertAdjacentElement("beforebegin", messageElement);
    }
  }

  messageElement.textContent = message;

  messageElement.style.color = isError ? "#c0392b" : "#2f6b3a";
}

/* =========================
   POWIADOMIENIA
   ========================= */

let notificationRefreshInterval = null;

function setupNotifications() {
  const wrapper = document.querySelector(".notification-nav-item");
  const button = document.querySelector("#notification-button");
  const panel = document.querySelector("#notification-panel");

  if (!wrapper || !button || !panel) {
    console.warn("Nie znaleziono elementów powiadomień.");
    return;
  }

  if (button.dataset.notificationsReady === "1") {
    return;
  }

  button.dataset.notificationsReady = "1";

  let badge = document.querySelector("#notification-badge");

  if (!badge) {
    badge = document.createElement("span");
    badge.className = "notification-badge";
    badge.id = "notification-badge";
    button.appendChild(badge);
  }

  let header = panel.querySelector(".notification-panel-header");

  if (!header) {
    header = document.createElement("div");
    header.className = "notification-panel-header";
    panel.prepend(header);
  }

  let headerTitle = header.querySelector(".notification-panel-title");

  if (!headerTitle) {
    headerTitle = document.createElement("span");
    headerTitle.className = "notification-panel-title";
    headerTitle.textContent = "Powiadomienia";
    header.prepend(headerTitle);
  }

  let markAllButton = header.querySelector(".notification-mark-all");

  if (!markAllButton) {
    markAllButton = document.createElement("button");
    markAllButton.type = "button";
    markAllButton.className = "notification-mark-all";
    markAllButton.textContent = "Zaznacz jako przeczytane";
    header.appendChild(markAllButton);
  }

  let list = panel.querySelector(".notification-list");

  if (!list) {
    list = document.createElement("div");
    list.className = "notification-list";
    panel.appendChild(list);
  }

  async function loadNotifications() {
    try {
      const [notificationsResponse, unreadResponse] = await Promise.all([
        fetch(`${API_BASE_URL}/api/notifications`, {
          method: "GET",
          credentials: "include",
        }),

        fetch(`${API_BASE_URL}/api/notifications/unread-count`, {
          method: "GET",
          credentials: "include",
        }),
      ]);

      if (
        notificationsResponse.status === 401 ||
        notificationsResponse.status === 403 ||
        unreadResponse.status === 401 ||
        unreadResponse.status === 403
      ) {
        return;
      }

      if (!notificationsResponse.ok || !unreadResponse.ok) {
        throw new Error(
          `HTTP ${notificationsResponse.status} / ${unreadResponse.status}`,
        );
      }

      const notifications = await notificationsResponse.json();
      const unreadCount = Number(await unreadResponse.json()) || 0;

      updateNotificationBadge(unreadCount);

      renderNotifications(notifications);
    } catch (error) {
      console.error("Błąd pobierania powiadomień:", error);

      list.innerHTML = `
        <div class="notification-empty">
          Nie udało się pobrać powiadomień.
        </div>
      `;
    }
  }

  function updateNotificationBadge(unreadCount) {
    const count = Number(unreadCount) || 0;

    if (count > 0) {
      badge.textContent = count > 99 ? "99+" : String(count);
      badge.style.display = "flex";
    } else {
      badge.textContent = "";
      badge.style.display = "none";
    }

    markAllButton.disabled = count === 0;
  }

  function renderNotifications(notifications) {
    if (!Array.isArray(notifications)) {
      list.innerHTML = `
        <div class="notification-empty">
          Brak nowych powiadomień
        </div>
      `;

      updateNotificationBadge(0);
      return;
    }

    const unreadNotifications = notifications.filter(
      (notification) => !notification.read,
    );

    if (unreadNotifications.length === 0) {
      list.innerHTML = `
        <div class="notification-empty">
          Brak nowych powiadomień
        </div>
      `;

      updateNotificationBadge(0);
      return;
    }

    list.innerHTML = "";

    unreadNotifications.forEach((notification) => {
      const item = document.createElement("div");

      item.className = "notification-item";
      item.dataset.notificationId = String(notification.id ?? "");
      item.dataset.conversationId = String(notification.conversationId ?? "");

      const senderName = escapeHtml(
        notification.senderNickname || "Użytkownik",
      );

      const content = escapeHtml(notification.content || "");

      const avatarSrc = getAvatarSrc(notification.senderAvatarUrl);

      const senderInitial = escapeHtml(
        (notification.senderNickname || "Użytkownik")
          .trim()
          .charAt(0)
          .toUpperCase() || "U",
      );

      const avatarHtml = avatarSrc
        ? `
          <img
            src="${escapeHtml(avatarSrc)}"
            alt="Zdjęcie profilowe"
            class="notification-avatar-image"
            onerror="
              this.style.display='none';
              this.nextElementSibling.style.display='flex';
            "
          />
          <span
            class="notification-avatar-fallback"
            style="display:none;"
          >
            ${senderInitial}
          </span>
        `
        : `
          <span class="notification-avatar-fallback">
            ${senderInitial}
          </span>
        `;

      item.innerHTML = `
        <div class="notification-item-icon">
          ${avatarHtml}

          <span
            class="notification-item-unread-dot"
            aria-hidden="true"
          ></span>
        </div>

        <div class="notification-item-content">
          <div class="notification-item-title">
            ${senderName} wysłał wiadomość
          </div>

          <div class="notification-item-text">
            ${content}
          </div>

          <div class="notification-item-time">
            ${formatNotificationDate(notification.createdAt)}
          </div>
        </div>
      `;

      item.addEventListener("click", () => {
        openNotification(notification);
      });

      list.appendChild(item);
    });

    updateNotificationBadge(unreadNotifications.length);
  }

  async function openNotification(notification) {
    if (!notification || !notification.conversationId) {
      return;
    }

    try {
      if (!notification.read && notification.id) {
        const response = await fetch(
          `${API_BASE_URL}/api/notifications/${encodeURIComponent(
            notification.id,
          )}/read`,
          {
            method: "PATCH",
            credentials: "include",
          },
        );

        if (!response.ok) {
          console.error(
            "Nie udało się oznaczyć powiadomienia jako przeczytanego.",
            response.status,
          );

          return;
        }
      }

      let offerId = null;

      try {
        const conversationResponse = await fetch(
          `${API_BASE_URL}/api/conversations/${encodeURIComponent(
            notification.conversationId,
          )}`,
          {
            method: "GET",
            credentials: "include",
          },
        );

        if (conversationResponse.ok) {
          const conversation = await conversationResponse.json();
          offerId = conversation?.offerId ?? null;
        }
      } catch (error) {
        console.error("Błąd pobierania danych rozmowy:", error);
      }

      let chatUrl = `chat.html?conversationId=${encodeURIComponent(
        notification.conversationId,
      )}`;

      if (offerId) {
        chatUrl += `&offerId=${encodeURIComponent(offerId)}`;
      }

      window.location.href = chatUrl;
    } catch (error) {
      console.error("Błąd otwierania powiadomienia:", error);
    }
  }

  async function markAllNotificationsAsRead() {
    markAllButton.disabled = true;

    try {
      const response = await fetch(
        `${API_BASE_URL}/api/notifications/read-all`,
        {
          method: "POST",
          credentials: "include",
        },
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      list.innerHTML = `
        <div class="notification-empty">
          Brak nowych powiadomień
        </div>
      `;

      updateNotificationBadge(0);
    } catch (error) {
      console.error(
        "Błąd oznaczania wszystkich powiadomień jako przeczytane:",
        error,
      );

      markAllButton.disabled = false;

      await loadNotifications();
    }
  }

  button.addEventListener("click", async (event) => {
    event.stopPropagation();

    panel.classList.toggle("open");

    if (panel.classList.contains("open")) {
      await loadNotifications();
    }
  });

  panel.addEventListener("click", (event) => {
    event.stopPropagation();
  });

  markAllButton.addEventListener("click", async (event) => {
    event.stopPropagation();

    await markAllNotificationsAsRead();
  });

  document.addEventListener("click", () => {
    panel.classList.remove("open");
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      panel.classList.remove("open");
    }
  });

  loadNotifications();

  if (notificationRefreshInterval) {
    clearInterval(notificationRefreshInterval);
  }

  notificationRefreshInterval = window.setInterval(loadNotifications, 10000);
}

function formatNotificationDate(value) {
  if (!value) {
    return "";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toLocaleString("pl-PL", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

window.addEventListener("beforeunload", () => {
  if (notificationRefreshInterval) {
    clearInterval(notificationRefreshInterval);
    notificationRefreshInterval = null;
  }
});
