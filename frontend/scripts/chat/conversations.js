const CV_API =
  typeof API_BASE_URL !== "undefined" ? API_BASE_URL : "http://localhost:8080";

let cvMe = null;
let cvItems = [];
let cvActiveTab = "mine";

document.addEventListener("DOMContentLoaded", () => {
  cvSetupTabs();
  cvLoad();
});

/* =========================
   ŁADOWANIE DANYCH
   ========================= */

async function cvLoad() {
  const list = document.getElementById("cv-list");
  const message = document.getElementById("cv-message");

  try {
    const meResponse = await fetch(`${CV_API}/api/users/me`, {
      credentials: "include",
    });

    if (meResponse.status === 401 || meResponse.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!meResponse.ok) {
      throw new Error("Nie udało się pobrać danych użytkownika.");
    }

    cvMe = await meResponse.json();

    const convResponse = await fetch(`${CV_API}/api/conversations`, {
      credentials: "include",
    });

    if (!convResponse.ok) {
      throw new Error("Nie udało się pobrać rozmów.");
    }

    const conversations = await convResponse.json();

    const applications = await cvLoadApplications();

    const offerCache = new Map();

    cvItems = await Promise.all(
      (Array.isArray(conversations) ? conversations : []).map((conversation) =>
        cvBuildItem(conversation, offerCache, applications),
      ),
    );

    cvItems.sort((a, b) => b.sortTime - a.sortTime);

    // domyślnie otwieramy zakładkę, w której coś jest
    const mineCount = cvItems.filter((i) => i.isMine).length;
    const appliedCount = cvItems.length - mineCount;

    if (mineCount === 0 && appliedCount > 0) {
      cvActiveTab = "applied";
    }

    if (message) {
      message.textContent = "";
      message.className = "cv-message";
    }

    cvRender();
  } catch (error) {
    console.error("Błąd ładowania rozmów:", error);

    list.innerHTML = "";
    list.removeAttribute("aria-busy");

    if (message) {
      message.textContent = error.message || "Nie udało się pobrać rozmów.";
      message.className = "cv-message is-error";
    }
  }
}

async function cvLoadApplications() {
  const map = new Map();

  try {
    const response = await fetch(`${CV_API}/api/job-applications/me`, {
      credentials: "include",
    });

    if (!response.ok) return map;

    const data = await response.json();

    if (Array.isArray(data)) {
      data.forEach((application) => {
        map.set(Number(application.offerId), application.status);
      });
    }
  } catch (error) {
    // brak aplikacji - nic się nie dzieje
  }

  return map;
}

async function cvFetchOffer(offerId, cache) {
  if (!offerId) return null;

  if (!cache.has(offerId)) {
    cache.set(
      offerId,
      fetch(`${CV_API}/api/offers/${encodeURIComponent(offerId)}`, {
        credentials: "include",
      })
        .then((response) => (response.ok ? response.json() : null))
        .catch(() => null),
    );
  }

  return cache.get(offerId);
}

async function cvFetchLastMessage(conversationId) {
  try {
    const response = await fetch(
      `${CV_API}/api/conversations/${encodeURIComponent(conversationId)}/messages`,
      { credentials: "include" },
    );

    if (!response.ok) return null;

    const messages = await response.json();

    if (!Array.isArray(messages) || messages.length === 0) return null;

    return messages[messages.length - 1];
  } catch (error) {
    return null;
  }
}

async function cvBuildItem(conversation, offerCache, applications) {
  const [offer, lastMessage] = await Promise.all([
    cvFetchOffer(conversation.offerId, offerCache),
    cvFetchLastMessage(conversation.id),
  ]);

  const other =
    (conversation.participants || []).find(
      (participant) => Number(participant.userId) !== Number(cvMe.id),
    ) || null;

  const isMine = !!offer && Number(offer.userId) === Number(cvMe.id);

  const time =
    lastMessage?.createdAt || conversation.updatedAt || conversation.createdAt;

  return {
    conversation,
    offer,
    lastMessage,
    other,
    isMine,
    applicationStatus: applications.get(Number(conversation.offerId)) || null,
    time,
    sortTime: time ? new Date(time).getTime() || 0 : 0,
  };
}

/* =========================
   ZAKŁADKI
   ========================= */

function cvSetupTabs() {
  document.querySelectorAll(".cv-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      cvActiveTab = tab.dataset.tab;
      cvRender();
    });
  });
}

/* =========================
   RENDER
   ========================= */

function cvRender() {
  const list = document.getElementById("cv-list");

  list.removeAttribute("aria-busy");

  const mine = cvItems.filter((item) => item.isMine);
  const applied = cvItems.filter((item) => !item.isMine);

  document.getElementById("cv-count-mine").textContent = String(mine.length);
  document.getElementById("cv-count-applied").textContent = String(
    applied.length,
  );

  document.querySelectorAll(".cv-tab").forEach((tab) => {
    const active = tab.dataset.tab === cvActiveTab;

    tab.classList.toggle("is-active", active);
    tab.setAttribute("aria-selected", String(active));
  });

  const visible = cvActiveTab === "mine" ? mine : applied;

  if (visible.length === 0) {
    list.innerHTML =
      cvActiveTab === "mine"
        ? `<div class="cv-empty">
             <h2>Brak rozmów o Twoich ogłoszeniach</h2>
             <p>Gdy ktoś napisze do Ciebie w sprawie Twojego ogłoszenia, rozmowa pojawi się tutaj.</p>
           </div>`
        : `<div class="cv-empty">
             <h2>Brak rozmów o Twoich aplikacjach</h2>
             <p>Gdy napiszesz do autora cudzego ogłoszenia lub zaaplikujesz na ofertę, rozmowa pojawi się tutaj.</p>
           </div>`;
    return;
  }

  list.innerHTML = visible.map(cvRenderRow).join("");
}

function cvRenderRow(item) {
  const { conversation, offer, lastMessage, other } = item;

  const nickname = other?.nickname || "Użytkownik";

  const avatarSrc = cvAvatarSrc(other?.avatarUrl);

  const initial = nickname.trim().charAt(0) || "U";

  const avatar = avatarSrc
    ? `<img src="${cvEscape(avatarSrc)}" alt="" onerror="this.remove()">${cvEscape(initial)}`
    : cvEscape(initial);

  const offerTitle = offer
    ? `<span class="cv-offer-title">${cvEscape(offer.title)}</span>`
    : `<span class="cv-offer-title is-deleted">Ogłoszenie zostało usunięte</span>`;

  const typeChip = offer
    ? `<span class="cv-chip type-${String(offer.type).toLowerCase()}">${cvEscape(cvTypeLabel(offer.type))}</span>`
    : "";

  const statusChip = item.applicationStatus
    ? `<span class="cv-chip status-${String(item.applicationStatus).toLowerCase()}">Aplikacja: ${cvEscape(cvApplicationLabel(item.applicationStatus))}</span>`
    : "";

  let preview = `<span class="cv-preview is-empty">Brak wiadomości</span>`;

  if (lastMessage) {
    const mineMessage = Number(lastMessage.senderId) === Number(cvMe.id);

    preview = `<span class="cv-preview">${mineMessage ? "Ty: " : ""}${cvEscape(lastMessage.content || (lastMessage.attachment ? "📎 " + lastMessage.attachment.fileName : ""))}</span>`;
  }

  const profileAttr = other?.userId
    ? ` data-profile-id="${cvEscape(other.userId)}" role="link" tabindex="0" title="Zobacz profil użytkownika"`
    : "";

  let href = `chat.html?conversationId=${encodeURIComponent(conversation.id)}&from=conversations`;

  if (conversation.offerId) {
    href += `&offerId=${encodeURIComponent(conversation.offerId)}`;
  }

  return `
    <a class="cv-row" href="${href}">
      <span class="cv-avatar"${profileAttr}>${avatar}</span>

      <span class="cv-main">
        <span class="cv-top">
          <span class="cv-user"${profileAttr}>${cvEscape(nickname)}</span>
          <span class="cv-time">${cvFormatTime(item.time)}</span>
        </span>

        <span class="cv-offer">
          <span class="cv-offer-label">Dotyczy:</span>
          ${offerTitle}
          ${typeChip}
          ${statusChip}
        </span>

        ${preview}
      </span>

      <span class="cv-open" aria-hidden="true">›</span>
    </a>
  `;
}

/* =========================
   POMOCNICZE
   ========================= */

function cvTypeLabel(type) {
  return (
    { SALE: "Sprzedaż", SERVICE: "Usługa", JOB: "Oferta pracy" }[type] ||
    type ||
    "Ogłoszenie"
  );
}

function cvApplicationLabel(status) {
  return (
    { PENDING: "oczekuje", ACCEPTED: "przyjęta", REJECTED: "odrzucona" }[
      status
    ] || status
  );
}

function cvAvatarSrc(avatarUrl) {
  if (!avatarUrl) return null;

  if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
    return avatarUrl;
  }

  return `${CV_API}${avatarUrl.startsWith("/") ? "" : "/"}${avatarUrl}`;
}

function cvFormatTime(value) {
  if (!value) return "";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "";

  const now = new Date();

  const sameDay = date.toDateString() === now.toDateString();

  if (sameDay) {
    return date.toLocaleTimeString("pl-PL", {
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  return date.toLocaleDateString("pl-PL", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

function cvEscape(value) {
  const div = document.createElement("div");

  div.textContent = value ?? "";

  return div.innerHTML;
}
