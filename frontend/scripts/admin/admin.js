const ADMIN_API_BASE_URL = "http://localhost:8080";

const panelState = {
  me: null,
  users: { page: 0 },
  offers: { page: 0 },
  audit: { page: 0 },
  disputes: { status: "OPEN", detailToken: 0, objectUrls: [] },
};

const ROLE_LABELS = {
  USER: "Użytkownik",
  MODERATOR: "Moderator",
  ADMIN: "Administrator",
};

const STATUS_LABELS = {
  ACTIVE: "Aktywne",
  BANNED: "Zablokowane",
  INACTIVE: "Nieaktywne",
  SOLD: "Sprzedane",
};

const AUDIT_LABELS = {
  USER_BANNED: "Ban użytkownika",
  USER_UNBANNED: "Odblokowanie",
  USER_DELETED: "Usunięcie konta",
  ROLE_CHANGED: "Zmiana roli",
  OFFER_DELETED: "Usunięcie ogłoszenia",
};

document.addEventListener("DOMContentLoaded", initPanel);

/* =========================
   POMOCNICZE
   ========================= */

async function panelApi(path, options = {}) {
  const headers = {};

  if (options.body) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(`${ADMIN_API_BASE_URL}${path}`, {
    credentials: "include",
    ...options,
    headers,
  });

  if (response.status === 204) {
    return null;
  }

  let data = null;

  try {
    data = await response.json();
  } catch (error) {
    data = null;
  }

  if (!response.ok) {
    const message =
      (data && data.message) ||
      (data && Object.values(data)[0]) ||
      `Błąd ${response.status}`;

    const error = new Error(message);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}

function panelEl(tag, options = {}, children = []) {
  const element = document.createElement(tag);

  if (options.className) element.className = options.className;
  if (options.text !== undefined) element.textContent = options.text;
  if (options.type) element.type = options.type;
  if (options.href) element.href = options.href;
  if (options.title) element.title = options.title;
  if (options.colSpan) element.colSpan = options.colSpan;
  if (options.onClick) element.addEventListener("click", options.onClick);

  children.forEach((child) => element.appendChild(child));

  return element;
}

function panelCell(content) {
  const cell = document.createElement("td");

  if (content instanceof Node) {
    cell.appendChild(content);
  } else {
    cell.textContent = content === null || content === undefined ? "" : content;
  }

  return cell;
}

function panelPill(value, label) {
  return panelEl("span", { className: `panel-pill ${value}`, text: label });
}

function panelDate(value) {
  return value ? new Date(value).toLocaleString("pl-PL") : "";
}

function showPanelMessage(text, kind = "ok") {
  const box = document.getElementById("panel-message");

  box.textContent = text;
  box.className = `panel-message ${kind}`;
  box.hidden = false;

  if (kind === "ok") {
    window.clearTimeout(showPanelMessage.timer);
    showPanelMessage.timer = window.setTimeout(() => {
      box.hidden = true;
    }, 5000);
  }
}

function handlePanelError(error) {
  if (error.status === 401) {
    window.location.href = "login.html";
    return;
  }

  if (error.data && error.data.banned) {
    window.location.href = "login.html";
    return;
  }

  showPanelMessage(error.message, "error");
}

function debounce(callback, delay = 300) {
  let timer = null;

  return (...args) => {
    window.clearTimeout(timer);
    timer = window.setTimeout(() => callback(...args), delay);
  };
}

function isAdmin() {
  return panelState.me && panelState.me.role === "ADMIN";
}

function canModerate(target) {
  const me = panelState.me;

  if (target.id === me.id) return false;
  if (target.role === "ADMIN") return false;
  if (target.role === "MODERATOR" && me.role !== "ADMIN") return false;

  return true;
}

/* =========================
   START
   ========================= */

async function initPanel() {
  try {
    const me = await panelApi("/api/auth/me");

    if (me.role !== "ADMIN" && me.role !== "MODERATOR") {
      window.location.href = "index.html";
      return;
    }

    panelState.me = me;
  } catch (error) {
    window.location.href = "login.html";
    return;
  }

  const me = panelState.me;

  document.getElementById("panel-role-badge").textContent =
    ROLE_LABELS[me.role];

  document.getElementById("panel-title").textContent = isAdmin()
    ? "Panel administratora"
    : "Panel moderatora";

  document.getElementById("panel-subtitle").textContent =
    `Zalogowano jako ${me.nickname} (id ${me.id})`;

  if (isAdmin()) {
    document
      .querySelectorAll("[data-admin-only]")
      .forEach((element) => (element.hidden = false));
  }

  setupTabs();
  setupFilters();
  setupDisputes();
  setupPasswordForm();

  if (me.passwordChangeRequired) {
    document.querySelectorAll(".panel-tab").forEach((tab) => {
      if (tab.dataset.tab !== "password") tab.disabled = true;
    });

    switchTab("password");

    showPanelMessage(
      "To konto ma hasło tymczasowe. Ustaw własne hasło, aby odblokować panel.",
      "warn"
    );

    return;
  }

  refreshDisputeBadge();
  loadUsers();
}

/* =========================
   ZAKŁADKI
   ========================= */

function setupTabs() {
  document.querySelectorAll(".panel-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      if (tab.disabled) return;

      switchTab(tab.dataset.tab);
    });
  });
}

function switchTab(name) {
  document.querySelectorAll(".panel-tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.tab === name);
  });

  ["users", "offers", "disputes", "audit", "password"].forEach((section) => {
    document.getElementById(`tab-${section}`).hidden = section !== name;
  });

  if (name === "users") loadUsers();
  if (name === "offers") loadOffers();
  if (name === "disputes") showDisputeOverview();
  if (name === "audit") loadAudit();
}

function setupFilters() {
  const reloadUsers = () => {
    panelState.users.page = 0;
    loadUsers();
  };

  const reloadOffers = () => {
    panelState.offers.page = 0;
    loadOffers();
  };

  document
    .getElementById("users-q")
    .addEventListener("input", debounce(reloadUsers));
  document.getElementById("users-role").addEventListener("change", reloadUsers);
  document
    .getElementById("users-status")
    .addEventListener("change", reloadUsers);

  document
    .getElementById("offers-q")
    .addEventListener("input", debounce(reloadOffers));
  document
    .getElementById("offers-user")
    .addEventListener("input", debounce(reloadOffers));
  document
    .getElementById("offers-status")
    .addEventListener("change", reloadOffers);
}

/* =========================
   STRONICOWANIE
   ========================= */

function renderPager(containerId, data, onPage) {
  const container = document.getElementById(containerId);

  container.replaceChildren();

  const info = panelEl("span", {
    text: `Wyniki: ${data.totalItems} · strona ${data.page + 1} z ${Math.max(
      data.totalPages,
      1
    )}`,
  });

  const buttons = panelEl("div", { className: "panel-actions" }, [
    panelEl("button", {
      className: "panel-btn",
      type: "button",
      text: "← Poprzednia",
      onClick: () => onPage(data.page - 1),
    }),
    panelEl("button", {
      className: "panel-btn",
      type: "button",
      text: "Następna →",
      onClick: () => onPage(data.page + 1),
    }),
  ]);

  buttons.children[0].disabled = data.page <= 0;
  buttons.children[1].disabled = data.page + 1 >= data.totalPages;

  container.appendChild(info);
  container.appendChild(buttons);
}

function renderEmpty(body, columns, text) {
  body.replaceChildren(
    panelEl("tr", {}, [panelEl("td", { className: "empty", text, colSpan: columns })])
  );
}

/* =========================
   UŻYTKOWNICY
   ========================= */

async function loadUsers() {
  const params = new URLSearchParams({
    page: panelState.users.page,
    size: 20,
  });

  const q = document.getElementById("users-q").value.trim();
  const role = document.getElementById("users-role").value;
  const status = document.getElementById("users-status").value;

  if (q) params.set("q", q);
  if (role) params.set("role", role);
  if (status) params.set("status", status);

  try {
    const data = await panelApi(`/api/moderation/users?${params}`);
    const body = document.getElementById("users-body");

    if (data.items.length === 0) {
      renderEmpty(body, 7, "Brak użytkowników spełniających kryteria.");
    } else {
      body.replaceChildren(...data.items.map(renderUserRow));
    }

    renderPager("users-pager", data, (page) => {
      panelState.users.page = page;
      loadUsers();
    });
  } catch (error) {
    handlePanelError(error);
  }
}

function renderUserRow(user) {
  const row = document.createElement("tr");

  if (user.status === "BANNED") row.className = "banned";

  const statusCell = panelEl("div", {}, [
    panelPill(user.status, STATUS_LABELS[user.status]),
  ]);

  if (user.status === "BANNED" && user.banReason) {
    statusCell.appendChild(
      panelEl("span", {
        className: "panel-ban-reason",
        text: `Powód: ${user.banReason}`,
      })
    );
  }

  row.append(
    panelCell(user.id),
    panelCell(user.nickname),
    panelCell(user.email),
    panelCell(panelPill(user.role, ROLE_LABELS[user.role])),
    panelCell(statusCell),
    panelCell(user.offersCount),
    panelCell(renderUserActions(user))
  );

  return row;
}

function renderUserActions(user) {
  const box = panelEl("div", { className: "panel-actions" });

  box.appendChild(
    panelEl("button", {
      className: "panel-btn",
      type: "button",
      text: "Ogłoszenia",
      onClick: () => showUserOffers(user.id),
    })
  );

  if (canModerate(user)) {
    if (user.status === "ACTIVE") {
      box.appendChild(
        panelEl("button", {
          className: "panel-btn danger",
          type: "button",
          text: "Zbanuj",
          onClick: () => banUser(user),
        })
      );
    } else {
      box.appendChild(
        panelEl("button", {
          className: "panel-btn",
          type: "button",
          text: "Odblokuj",
          onClick: () => unbanUser(user),
        })
      );
    }
  }

  if (isAdmin() && user.role !== "ADMIN" && user.id !== panelState.me.id) {
    const makeModerator = user.role === "USER";

    box.appendChild(
      panelEl("button", {
        className: "panel-btn",
        type: "button",
        text: makeModerator ? "Nadaj moderatora" : "Odbierz moderatora",
        onClick: () => changeRole(user, makeModerator ? "MODERATOR" : "USER"),
      })
    );

    box.appendChild(
      panelEl("button", {
        className: "panel-btn danger",
        type: "button",
        text: "Usuń konto",
        onClick: () => deleteUser(user),
      })
    );
  }

  return box;
}

function showUserOffers(userId) {
  document.getElementById("offers-user").value = userId;
  document.getElementById("offers-q").value = "";
  document.getElementById("offers-status").value = "";
  panelState.offers.page = 0;

  switchTab("offers");
}

async function banUser(user) {
  const reason = window.prompt(
    `Powód blokady konta „${user.nickname}” (min. 3 znaki):`
  );

  if (reason === null) return;

  try {
    await panelApi(`/api/moderation/users/${user.id}/ban`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });

    showPanelMessage(`Zablokowano użytkownika ${user.nickname}.`);
    loadUsers();
  } catch (error) {
    handlePanelError(error);
  }
}

async function unbanUser(user) {
  try {
    await panelApi(`/api/moderation/users/${user.id}/unban`, {
      method: "POST",
    });

    showPanelMessage(`Odblokowano użytkownika ${user.nickname}.`);
    loadUsers();
  } catch (error) {
    handlePanelError(error);
  }
}

async function changeRole(user, role) {
  const text =
    role === "MODERATOR"
      ? `Nadać użytkownikowi „${user.nickname}” rolę moderatora?`
      : `Odebrać użytkownikowi „${user.nickname}” rolę moderatora?`;

  if (!window.confirm(text)) return;

  try {
    await panelApi(`/api/admin/users/${user.id}/role`, {
      method: "PUT",
      body: JSON.stringify({ role }),
    });

    showPanelMessage("Zmieniono rolę użytkownika.");
    loadUsers();
  } catch (error) {
    handlePanelError(error);
  }
}

async function deleteUser(user) {
  const typed = window.prompt(
    `UWAGA: konto „${user.nickname}” zostanie TRWALE usunięte razem z ` +
      `ogłoszeniami, zdjęciami, rozmowami i plikami. Tego nie da się cofnąć.\n\n` +
      `Aby potwierdzić, wpisz nick użytkownika:`
  );

  if (typed === null) return;

  if (typed.trim() !== user.nickname) {
    showPanelMessage("Nick się nie zgadza - konto nie zostało usunięte.", "error");
    return;
  }

  try {
    await panelApi(`/api/admin/users/${user.id}`, { method: "DELETE" });

    showPanelMessage(`Usunięto konto ${user.nickname}.`);
    loadUsers();
  } catch (error) {
    handlePanelError(error);
  }
}

/* =========================
   OGŁOSZENIA
   ========================= */

async function loadOffers() {
  const params = new URLSearchParams({
    page: panelState.offers.page,
    size: 20,
  });

  const q = document.getElementById("offers-q").value.trim();
  const userId = document.getElementById("offers-user").value.trim();
  const status = document.getElementById("offers-status").value;

  if (q) params.set("q", q);
  if (userId) params.set("userId", userId);
  if (status) params.set("status", status);

  try {
    const data = await panelApi(`/api/moderation/offers?${params}`);
    const body = document.getElementById("offers-body");

    if (data.items.length === 0) {
      renderEmpty(body, 7, "Brak ogłoszeń spełniających kryteria.");
    } else {
      body.replaceChildren(...data.items.map(renderOfferRow));
    }

    renderPager("offers-pager", data, (page) => {
      panelState.offers.page = page;
      loadOffers();
    });
  } catch (error) {
    handlePanelError(error);
  }
}

function renderOfferRow(offer) {
  const row = document.createElement("tr");

  const price =
    offer.price === null || offer.price === undefined
      ? "—"
      : `${Number(offer.price).toLocaleString("pl-PL")} zł`;

  const titleLink = panelEl("a", {
    text: offer.title,
    href: `offer-details.html?id=${encodeURIComponent(offer.id)}`,
  });
  titleLink.target = "_blank";
  titleLink.rel = "noopener";

  const deleteButton = panelEl("button", {
    className: "panel-btn danger",
    type: "button",
    text: "Usuń",
    onClick: () => deleteOffer(offer),
  });

  row.append(
    panelCell(offer.id),
    panelCell(titleLink),
    panelCell(panelPill(offer.status, STATUS_LABELS[offer.status])),
    panelCell(price),
    panelCell(`${offer.userNickname} (id ${offer.userId})`),
    panelCell(panelDate(offer.createdAt)),
    panelCell(panelEl("div", { className: "panel-actions" }, [deleteButton]))
  );

  return row;
}

async function deleteOffer(offer) {
  const reason = window.prompt(
    `Usunąć ogłoszenie „${offer.title}”?\n\nPodaj powód (opcjonalnie) i kliknij OK:`
  );

  if (reason === null) return;

  const query = reason.trim()
    ? `?reason=${encodeURIComponent(reason.trim())}`
    : "";

  try {
    await panelApi(`/api/moderation/offers/${offer.id}${query}`, {
      method: "DELETE",
    });

    showPanelMessage("Ogłoszenie zostało usunięte.");
    loadOffers();
  } catch (error) {
    handlePanelError(error);
  }
}

/* =========================
   DZIENNIK ZDARZEŃ (ADMIN)
   ========================= */

async function loadAudit() {
  if (!isAdmin()) return;

  try {
    const data = await panelApi(
      `/api/admin/audit-log?page=${panelState.audit.page}&size=30`
    );

    const body = document.getElementById("audit-body");

    if (data.items.length === 0) {
      renderEmpty(body, 5, "Dziennik jest pusty.");
    } else {
      body.replaceChildren(
        ...data.items.map((entry) => {
          const row = document.createElement("tr");

          row.append(
            panelCell(panelDate(entry.createdAt)),
            panelCell(`${entry.actorNickname || "?"} (id ${entry.actorId})`),
            panelCell(AUDIT_LABELS[entry.action] || entry.action),
            panelCell(`${entry.targetType} #${entry.targetId}`),
            panelCell(entry.details)
          );

          return row;
        })
      );
    }

    renderPager("audit-pager", data, (page) => {
      panelState.audit.page = page;
      loadAudit();
    });
  } catch (error) {
    handlePanelError(error);
  }
}

/* =========================
   ZMIANA HASŁA
   ========================= */

function setupPasswordForm() {
  const form = document.getElementById("password-form");
  const info = document.getElementById("password-info");

  if (panelState.me.authProvider !== "LOCAL") {
    info.textContent =
      "To konto loguje się przez Google i nie ma hasła do zmiany.";
    form.hidden = true;
    return;
  }

  info.textContent = panelState.me.passwordChangeRequired
    ? "Wpisz hasło tymczasowe jako obecne, a następnie ustaw własne."
    : "Użyj silnego, unikalnego hasła.";

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const currentPassword = document.getElementById("pw-current").value;
    const newPassword = document.getElementById("pw-new").value;
    const confirmPassword = document.getElementById("pw-confirm").value;

    try {
      await panelApi("/api/users/me/password", {
        method: "PUT",
        body: JSON.stringify({
          currentPassword,
          newPassword,
          confirmPassword,
        }),
      });

      form.reset();

      if (panelState.me.passwordChangeRequired) {
        window.location.reload();
        return;
      }

      showPanelMessage("Hasło zostało zmienione.");
    } catch (error) {
      handlePanelError(error);
    }
  });
}

/* =========================
   ZGŁOSZENIA PROBLEMÓW Z ZAMÓWIENIAMI
   (dostępne dla administratora i moderatora)
   ========================= */

const DISPUTE_RESOLUTION_LABELS = {
  REFUND_BUYER: "Zwrot dla kupującego",
  PAY_SELLER: "Wypłata dla sprzedawcy",
};

const DISPUTE_ORDER_STATUS_LABELS = {
  DISPUTED: "Środki wstrzymane",
  REFUNDED: "Zwrócono kupującemu",
  COMPLETED: "Wypłacono sprzedawcy",
};

function setupDisputes() {
  document.querySelectorAll("[data-dispute-status]").forEach((button) => {
    button.addEventListener("click", () => {
      panelState.disputes.status = button.dataset.disputeStatus;

      document.querySelectorAll("[data-dispute-status]").forEach((other) => {
        other.classList.toggle("active", other === button);
      });

      loadDisputes();
    });
  });
}

// Liczba aktualnych zgłoszeń przy zakładce
async function refreshDisputeBadge() {
  const badge = document.getElementById("disputes-badge");

  try {
    const data = await panelApi("/api/moderation/disputes/open-count");
    const count = data ? Number(data.count) : 0;

    badge.textContent = String(count);
    badge.hidden = count <= 0;
  } catch (error) {
    badge.hidden = true;
  }
}

function showDisputeOverview() {
  releaseDisputeImages();

  document.getElementById("dispute-detail").hidden = true;
  document.getElementById("disputes-overview").hidden = false;

  loadDisputes();
}

async function loadDisputes() {
  const list = document.getElementById("disputes-list");
  const status = panelState.disputes.status;

  list.replaceChildren(
    panelEl("p", { className: "dispute-empty", text: "Ładowanie…" })
  );

  try {
    const disputes = await panelApi(
      `/api/moderation/disputes?status=${encodeURIComponent(status)}`
    );

    // Użytkownik zdążył przełączyć panel - ignorujemy spóźnioną odpowiedź.
    if (status !== panelState.disputes.status) {
      return;
    }

    if (!disputes || !disputes.length) {
      list.replaceChildren(
        panelEl("p", {
          className: "dispute-empty",
          text:
            status === "OPEN"
              ? "Brak aktualnych zgłoszeń."
              : "Brak rozwiązanych zgłoszeń.",
        })
      );
      return;
    }

    list.replaceChildren(...disputes.map(renderDisputeRow));
  } catch (error) {
    list.replaceChildren();
    handlePanelError(error);
  }
}

function renderDisputeRow(dispute) {
  const resolved = dispute.status === "RESOLVED";

  const meta = [
    `Ogłoszenie: ${dispute.offerTitle}`,
    `Kupujący: ${dispute.buyerNickname}`,
    `Sprzedający: ${dispute.sellerNickname}`,
    `Zgłoszono: ${panelDate(dispute.createdAt)}`,
  ];

  if (dispute.imageCount > 0) {
    meta.push(`Zdjęcia: ${dispute.imageCount}`);
  }

  if (resolved) {
    meta.push(
      `Rozwiązał(a): ${dispute.resolverNickname || "-"}, ${panelDate(
        dispute.resolvedAt
      )}`
    );
  }

  const top = panelEl("div", { className: "dispute-row-top" }, [
    panelEl("strong", { className: "dispute-row-title", text: dispute.title }),
  ]);

  if (resolved && dispute.resolution) {
    top.appendChild(
      panelPill(
        dispute.resolution,
        DISPUTE_RESOLUTION_LABELS[dispute.resolution] || dispute.resolution
      )
    );
  } else {
    top.appendChild(panelPill("OPEN", "Czeka na decyzję"));
  }

  const row = panelEl(
    "button",
    {
      className: "dispute-row",
      type: "button",
      onClick: () => openDisputeDetail(dispute.id),
    },
    [
      top,
      panelEl("span", { className: "dispute-row-meta", text: meta.join(" · ") }),
    ]
  );

  return row;
}

/* ---------- podgląd jednego zgłoszenia ---------- */

function releaseDisputeImages() {
  panelState.disputes.objectUrls.forEach((url) => URL.revokeObjectURL(url));
  panelState.disputes.objectUrls = [];
}

async function openDisputeDetail(id) {
  const token = ++panelState.disputes.detailToken;

  try {
    const detail = await panelApi(`/api/moderation/disputes/${id}`);

    if (token !== panelState.disputes.detailToken) {
      return;
    }

    releaseDisputeImages();

    document.getElementById("disputes-overview").hidden = true;

    const box = document.getElementById("dispute-detail");

    box.hidden = false;
    box.replaceChildren(...buildDisputeDetail(detail));

    loadDisputeImages(detail, token);
  } catch (error) {
    handlePanelError(error);
  }
}

function buildDisputeDetail(detail) {
  const resolved = detail.status === "RESOLVED";

  const back = panelEl("button", {
    className: "panel-btn dispute-back",
    type: "button",
    text: "← Wróć do listy",
    onClick: showDisputeOverview,
  });

  // Tytuł ogłoszenia (link do ogłoszenia, którego dotyczy zamówienie)
  const offerLink = panelEl("a", {
    className: "dispute-offer-link",
    text: detail.offerTitle,
    href: `offer-details.html?id=${encodeURIComponent(detail.offerId)}`,
  });

  offerLink.target = "_blank";
  offerLink.rel = "noopener";

  const heading = panelEl("div", { className: "dispute-heading" }, [
    panelEl("span", { className: "dispute-label", text: "Ogłoszenie" }),
    offerLink,
  ]);

  const facts = panelEl("dl", { className: "dispute-facts" });

  const addFact = (label, value) => {
    facts.appendChild(panelEl("dt", { text: label }));
    facts.appendChild(panelEl("dd", { text: value }));
  };

  addFact("Kupujący", `${detail.buyerNickname} (id ${detail.buyerId})`);
  addFact("Sprzedający", `${detail.sellerNickname} (id ${detail.sellerId})`);
  addFact(
    "Kwota zamówienia",
    `${Number(detail.totalAmount).toLocaleString("pl-PL", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })} zł`
  );
  addFact(
    "Do wypłaty sprzedawcy",
    `${Number(detail.sellerPayout).toLocaleString("pl-PL", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })} zł`
  );
  addFact("Zgłoszono", panelDate(detail.createdAt));
  addFact(
    "Status zamówienia",
    DISPUTE_ORDER_STATUS_LABELS[detail.orderStatus] || detail.orderStatus
  );

  const problem = panelEl("section", { className: "dispute-section" }, [
    panelEl("span", { className: "dispute-label", text: "Zgłoszenie klienta" }),
    panelEl("h3", { className: "dispute-title", text: detail.title }),
    panelEl("p", { className: "dispute-description", text: detail.description }),
  ]);

  const nodes = [back, heading, facts, problem];

  if (detail.images && detail.images.length) {
    const gallery = panelEl("div", {
      className: "dispute-gallery",
      title: "",
    });

    gallery.id = "dispute-gallery";

    nodes.push(
      panelEl("section", { className: "dispute-section" }, [
        panelEl("span", {
          className: "dispute-label",
          text: `Zdjęcia (${detail.images.length})`,
        }),
        gallery,
      ])
    );
  } else {
    nodes.push(
      panelEl("p", {
        className: "panel-muted",
        text: "Klient nie dołączył zdjęć.",
      })
    );
  }

  nodes.push(resolved ? buildResolvedBlock(detail) : buildDecisionBlock(detail));

  return nodes;
}

function buildResolvedBlock(detail) {
  const block = panelEl("section", { className: "dispute-section dispute-decision" }, [
    panelEl("span", { className: "dispute-label", text: "Decyzja" }),
    panelPill(
      detail.resolution,
      DISPUTE_RESOLUTION_LABELS[detail.resolution] || detail.resolution
    ),
    panelEl("p", {
      className: "panel-muted",
      text: `Rozstrzygnął(a): ${detail.resolverNickname || "-"}, ${panelDate(
        detail.resolvedAt
      )}`,
    }),
  ]);

  if (detail.resolverNote) {
    block.appendChild(
      panelEl("p", {
        className: "dispute-description",
        text: `Uzasadnienie: ${detail.resolverNote}`,
      })
    );
  }

  return block;
}

function buildDecisionBlock(detail) {
  const note = document.createElement("textarea");

  note.className = "dispute-note";
  note.rows = 3;
  note.maxLength = 500;
  note.placeholder = "Uzasadnienie decyzji (opcjonalnie)";

  const refund = panelEl("button", {
    className: "panel-btn danger",
    type: "button",
    text: "Zwróć pieniądze kupującemu",
  });

  const pay = panelEl("button", {
    className: "panel-btn dispute-pay",
    type: "button",
    text: "Wypłać sprzedawcy",
  });

  const decide = async (resolution, question) => {
    if (!window.confirm(question)) {
      return;
    }

    refund.disabled = true;
    pay.disabled = true;

    try {
      await panelApi(`/api/moderation/disputes/${detail.id}/resolve`, {
        method: "POST",
        body: JSON.stringify({ resolution, note: note.value.trim() }),
      });

      showPanelMessage(
        resolution === "REFUND_BUYER"
          ? "Zgłoszenie rozwiązane: pieniądze wróciły do kupującego."
          : "Zgłoszenie rozwiązane: środki zostały wypłacone sprzedawcy."
      );

      refreshDisputeBadge();

      // Po decyzji pokazujemy listę rozwiązanych
      panelState.disputes.status = "RESOLVED";

      document.querySelectorAll("[data-dispute-status]").forEach((button) => {
        button.classList.toggle("active", button.dataset.disputeStatus === "RESOLVED");
      });

      showDisputeOverview();
    } catch (error) {
      refund.disabled = false;
      pay.disabled = false;

      handlePanelError(error);
    }
  };

  refund.addEventListener("click", () =>
    decide(
      "REFUND_BUYER",
      `Zwrócić ${detail.buyerNickname} pieniądze za „${detail.offerTitle}”? ` +
        "Tej decyzji nie można cofnąć."
    )
  );

  pay.addEventListener("click", () =>
    decide(
      "PAY_SELLER",
      `Wypłacić środki sprzedawcy ${detail.sellerNickname} za „${detail.offerTitle}”? ` +
        "Tej decyzji nie można cofnąć."
    )
  );

  return panelEl("section", { className: "dispute-section dispute-decision" }, [
    panelEl("span", { className: "dispute-label", text: "Decyzja" }),
    panelEl("p", {
      className: "panel-muted",
      text: "Środki są wstrzymane. Wybierz, co zrobić z zamówieniem.",
    }),
    note,
    panelEl("div", { className: "panel-actions" }, [refund, pay]),
  ]);
}

/*
 * Zdjęcia zgłoszenia są prywatne (wymagają zalogowania jako administrator /
 * moderator), więc pobieramy je z ciasteczkami i pokazujemy z blob URL.
 */
async function loadDisputeImages(detail, token) {
  const gallery = document.getElementById("dispute-gallery");

  if (!gallery) {
    return;
  }

  for (const image of detail.images) {
    const figure = panelEl("button", {
      className: "dispute-thumb",
      type: "button",
      title: image.fileName,
    });

    gallery.appendChild(figure);

    try {
      const response = await fetch(
        `${ADMIN_API_BASE_URL}/api/moderation/disputes/${detail.id}/images/${image.id}`,
        { credentials: "include" }
      );

      if (!response.ok) {
        throw new Error(`Błąd ${response.status}`);
      }

      const blob = await response.blob();

      // Użytkownik wyszedł ze zgłoszenia w trakcie pobierania
      if (token !== panelState.disputes.detailToken) {
        return;
      }

      const url = URL.createObjectURL(blob);

      panelState.disputes.objectUrls.push(url);

      const img = document.createElement("img");

      img.src = url;
      img.alt = image.fileName;

      figure.appendChild(img);
      figure.addEventListener("click", () => openDisputeLightbox(url, image.fileName));
    } catch (error) {
      console.error("Nie udało się pobrać zdjęcia zgłoszenia:", error);

      figure.disabled = true;
      figure.textContent = "Nie udało się wczytać zdjęcia";
    }
  }
}

function openDisputeLightbox(url, name) {
  const overlay = panelEl("div", { className: "dispute-lightbox" });

  const img = document.createElement("img");

  img.src = url;
  img.alt = name;

  const closeButton = panelEl("button", {
    className: "dispute-lightbox-close",
    type: "button",
    text: "×",
    title: "Zamknij",
  });

  const close = () => {
    document.removeEventListener("keydown", onKey);
    overlay.remove();
  };

  const onKey = (event) => {
    if (event.key === "Escape") close();
  };

  closeButton.addEventListener("click", close);
  overlay.addEventListener("click", (event) => {
    if (event.target === overlay) close();
  });
  document.addEventListener("keydown", onKey);

  overlay.append(img, closeButton);
  document.body.appendChild(overlay);
}
