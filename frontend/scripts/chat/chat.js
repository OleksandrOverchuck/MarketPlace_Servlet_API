const CHAT_API_BASE_URL =
  typeof API_BASE_URL !== "undefined" ? API_BASE_URL : "http://localhost:8080";

const chatParams = new URLSearchParams(window.location.search);

const conversationId = chatParams.get("conversationId");

const offerId = chatParams.get("offerId");

let currentUser = null;
let conversation = null;
let messages = [];
let refreshInterval = null;

/* ===== ZAŁĄCZNIKI ===== */

const MAX_ATTACHMENT_SIZE = 5 * 1024 * 1024; // 5 MB

const ALLOWED_ATTACHMENT_EXTENSIONS = [
  "pdf",
  "png",
  "jpg",
  "jpeg",
  "gif",
  "webp",
  "doc",
  "docx",
  "xls",
  "xlsx",
  "ppt",
  "pptx",
  "txt",
  "csv",
];

let pendingFile = null;
let lastRenderSignature = null;
let isSending = false;

// messageId -> Promise<blobUrl> (żeby nie pobierać pliku przy każdym odświeżeniu)
const attachmentBlobUrls = new Map();

/* =====================================================
   START
   ===================================================== */

document.addEventListener("DOMContentLoaded", () => {
  setupChat();
});

/* =====================================================
   SETUP
   ===================================================== */

async function setupChat() {
  if (!conversationId) {
    showError("Nie znaleziono identyfikatora rozmowy.");

    return;
  }

  setupBackButton();

  try {
    await loadCurrentUser();

    await loadConversation();

    await loadMessages(true);

    setupMessageForm();

    setupAttachments();

    startAutoRefresh();
  } catch (error) {
    console.error("Błąd uruchamiania czatu:", error);

    showError("Nie udało się załadować rozmowy.");
  }
}

/* =====================================================
   CURRENT USER
   ===================================================== */

async function loadCurrentUser() {
  const response = await fetch(`${CHAT_API_BASE_URL}/api/users/me`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(
      `Nie udało się pobrać użytkownika. HTTP ${response.status}`,
    );
  }

  currentUser = await response.json();

  if (!currentUser || !currentUser.id) {
    throw new Error("Backend nie zwrócił danych użytkownika.");
  }
}

/* =====================================================
   CONVERSATION
   ===================================================== */

async function loadConversation() {
  const response = await fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}`,
    {
      method: "GET",
      credentials: "include",
    },
  );

  if (!response.ok) {
    let errorMessage = `Nie udało się pobrać rozmowy. HTTP ${response.status}`;

    try {
      const errorData = await response.json();

      if (errorData.message) {
        errorMessage = errorData.message;
      }
    } catch (error) {
      // brak JSON
    }

    throw new Error(errorMessage);
  }

  conversation = await response.json();

  if (!conversation) {
    throw new Error("Backend nie zwrócił danych rozmowy.");
  }

  renderConversationHeader();
}

/* =====================================================
   OTHER PARTICIPANT
   ===================================================== */

function getOtherParticipant() {
  if (!conversation || !conversation.participants || !currentUser) {
    return null;
  }

  return conversation.participants.find(
    (participant) => Number(participant.userId) !== Number(currentUser.id),
  );
}

/* =====================================================
   AVATAR URL
   ===================================================== */

function getAvatarUrl(avatarUrl) {
  if (!avatarUrl) {
    return null;
  }

  if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
    return avatarUrl;
  }

  if (avatarUrl.startsWith("/")) {
    return CHAT_API_BASE_URL + avatarUrl;
  }

  return CHAT_API_BASE_URL + "/" + avatarUrl;
}

/* =====================================================
   CHAT HEADER
   ===================================================== */

function renderConversationHeader() {
  const otherParticipant = getOtherParticipant();

  const nameElement = document.getElementById("chat-user-name");

  const avatarElement = document.getElementById("chat-user-avatar");

  if (!otherParticipant) {
    if (nameElement) {
      nameElement.textContent = "Rozmowa";
    }

    return;
  }

  if (nameElement) {
    nameElement.textContent = otherParticipant.nickname || "Użytkownik";
  }

  const userBox = document.getElementById("chat-user");

  if (userBox && otherParticipant.userId) {
    userBox.setAttribute("data-profile-id", otherParticipant.userId);
    userBox.setAttribute("role", "link");
    userBox.tabIndex = 0;
  }

  if (!avatarElement) {
    return;
  }

  avatarElement.innerHTML = "";

  const avatarUrl = getAvatarUrl(otherParticipant.avatarUrl);

  if (avatarUrl) {
    const image = document.createElement("img");

    image.src = avatarUrl;

    image.alt = otherParticipant.nickname || "Użytkownik";

    image.onerror = () => {
      avatarElement.innerHTML = "";

      avatarElement.textContent = getInitial(otherParticipant.nickname);
    };

    avatarElement.appendChild(image);
  } else {
    avatarElement.textContent = getInitial(otherParticipant.nickname);
  }
}

/* =====================================================
   INITIAL
   ===================================================== */

function getInitial(name) {
  if (!name) {
    return "?";
  }

  return name.trim().charAt(0).toUpperCase();
}

/* =====================================================
   LOAD MESSAGES
   ===================================================== */

async function loadMessages(shouldScroll = true) {
  const response = await fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}/messages`,
    {
      method: "GET",
      credentials: "include",
    },
  );

  if (!response.ok) {
    let errorMessage = `Nie udało się pobrać wiadomości. HTTP ${response.status}`;

    try {
      const errorData = await response.json();

      if (errorData.message) {
        errorMessage = errorData.message;
      }
    } catch (error) {
      // brak JSON
    }

    throw new Error(errorMessage);
  }

  messages = await response.json();

  if (!Array.isArray(messages)) {
    messages = [];
  }

  renderMessages(shouldScroll);
}

/* =====================================================
   RENDER MESSAGES
   ===================================================== */

function renderMessages(shouldScroll = true) {
  const container = document.getElementById("messages-container");

  if (!container) {
    return;
  }

  /*
   * Odświeżanie co 3 s nie przerysowuje listy, jeśli nic się nie zmieniło -
   * dzięki temu obrazy z załączników nie migają.
   */
  const signature = messages.map((message) => message.id).join(",");

  if (signature === lastRenderSignature) {
    return;
  }

  lastRenderSignature = signature;

  container.innerHTML = "";

  if (!messages.length) {
    const empty = document.createElement("div");

    empty.className = "empty-chat";

    empty.textContent = "Napisz pierwszą wiadomość.";

    container.appendChild(empty);

    return;
  }

  messages.forEach((message) => {
    const isMine = Number(message.senderId) === Number(currentUser.id);

    const messageElement = document.createElement("div");

    messageElement.className = isMine ? "message mine" : "message theirs";

    const content = document.createElement("div");

    content.className = "message-content";

    const bubble = document.createElement("div");

    bubble.className = "message-bubble";

    if (message.attachment) {
      bubble.appendChild(buildAttachmentElement(message));
    }

    if (message.content) {
      const text = document.createElement("div");

      text.className = "message-text";

      text.textContent = message.content;

      bubble.appendChild(text);
    }

    const time = document.createElement("div");

    time.className = "message-time";

    time.textContent = formatMessageTime(message.createdAt);

    content.appendChild(bubble);

    content.appendChild(time);

    messageElement.appendChild(content);

    container.appendChild(messageElement);
  });

  if (shouldScroll) {
    scrollMessagesToBottom();
  }
}

/* =====================================================
   TIME
   ===================================================== */

function formatMessageTime(dateString) {
  if (!dateString) {
    return "";
  }

  const date = new Date(dateString);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toLocaleTimeString("pl-PL", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

/* =====================================================
   MESSAGE FORM
   ===================================================== */

function setupMessageForm() {
  const form = document.getElementById("message-form");

  const input = document.getElementById("message-input");

  const button = document.getElementById("send-message-button");

  if (!form || !input || !button) {
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (isSending) {
      return;
    }

    const content = input.value.trim();

    if (!content && !pendingFile) {
      input.focus();

      return;
    }

    isSending = true;

    button.disabled = true;

    setAttachControlsDisabled(true);

    try {
      if (pendingFile) {
        await sendAttachment(pendingFile, content);

        clearPendingFile();
      } else {
        await sendMessage(content);
      }

      input.value = "";

      await loadMessages(true);

      input.focus();
    } catch (error) {
      console.error("Błąd wysyłania wiadomości:", error);

      alert(error.message || "Nie udało się wysłać wiadomości.");
    } finally {
      isSending = false;

      button.disabled = false;

      setAttachControlsDisabled(false);
    }
  });
}

/* =====================================================
   SEND MESSAGE
   ===================================================== */

async function sendMessage(content) {
  const response = await fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}/messages`,
    {
      method: "POST",

      credentials: "include",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify({
        content: content,
      }),
    },
  );

  if (!response.ok) {
    let errorMessage = `HTTP ${response.status}`;

    try {
      const errorData = await response.json();

      if (errorData.message) {
        errorMessage = errorData.message;
      }
    } catch (error) {
      // brak JSON
    }

    throw new Error(errorMessage);
  }

  return await response.json();
}

/* =====================================================
   ZAŁĄCZNIKI - WYSYŁANIE
   ===================================================== */

async function sendAttachment(file, content) {
  const formData = new FormData();

  formData.append("file", file);

  if (content) {
    formData.append("content", content);
  }

  /*
   * Nie ustawiamy Content-Type - przeglądarka sama doda
   * multipart/form-data wraz z boundary.
   */
  const response = await fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}/messages/attachments`,
    {
      method: "POST",

      credentials: "include",

      body: formData,
    },
  );

  if (!response.ok) {
    let errorMessage = `Nie udało się wysłać pliku. HTTP ${response.status}`;

    try {
      const errorData = await response.json();

      if (errorData.message) {
        errorMessage = errorData.message;
      }
    } catch (error) {
      // brak JSON
    }

    throw new Error(errorMessage);
  }

  return await response.json();
}

/* =====================================================
   ZAŁĄCZNIKI - WYBÓR PLIKU (PRZYCISK, PRZECIĄGNIĘCIE, WKLEJENIE)
   ===================================================== */

function setupAttachments() {
  const attachButton = document.getElementById("attach-file-button");

  const fileInput = document.getElementById("attachment-input");

  const removeButton = document.getElementById("attachment-remove-button");

  const messageInput = document.getElementById("message-input");

  const overlay = document.getElementById("drop-overlay");

  /* --- przycisk "Załącz plik" --- */

  if (attachButton && fileInput) {
    attachButton.addEventListener("click", () => {
      fileInput.click();
    });

    fileInput.addEventListener("change", () => {
      if (fileInput.files && fileInput.files.length > 0) {
        setPendingFile(fileInput.files[0]);
      }

      // pozwala wybrać ten sam plik ponownie po jego usunięciu
      fileInput.value = "";
    });
  }

  if (removeButton) {
    removeButton.addEventListener("click", () => {
      clearPendingFile();

      if (messageInput) {
        messageInput.focus();
      }
    });
  }

  /* --- przeciąganie pliku na czat --- */

  let dragDepth = 0;

  const hasFiles = (event) =>
    event.dataTransfer &&
    Array.from(event.dataTransfer.types || []).includes("Files");

  window.addEventListener("dragenter", (event) => {
    if (!hasFiles(event)) {
      return;
    }

    event.preventDefault();

    dragDepth += 1;

    if (overlay) {
      overlay.hidden = false;
    }
  });

  window.addEventListener("dragover", (event) => {
    if (!hasFiles(event)) {
      return;
    }

    // bez tego przeglądarka otworzyłaby upuszczony plik
    event.preventDefault();
  });

  window.addEventListener("dragleave", (event) => {
    if (!hasFiles(event)) {
      return;
    }

    dragDepth = Math.max(0, dragDepth - 1);

    if (dragDepth === 0 && overlay) {
      overlay.hidden = true;
    }
  });

  window.addEventListener("drop", (event) => {
    if (!hasFiles(event)) {
      return;
    }

    event.preventDefault();

    dragDepth = 0;

    if (overlay) {
      overlay.hidden = true;
    }

    if (isSending) {
      return;
    }

    const files = event.dataTransfer.files;

    if (!files || files.length === 0) {
      return;
    }

    if (files.length > 1) {
      alert("Można wysłać tylko jeden plik naraz. Wybrano pierwszy z nich.");
    }

    setPendingFile(files[0]);
  });

  /* --- wklejenie pliku/zrzutu ekranu (Ctrl+V) --- */

  if (messageInput) {
    messageInput.addEventListener("paste", (event) => {
      const files = event.clipboardData && event.clipboardData.files;

      if (files && files.length > 0 && !isSending) {
        event.preventDefault();

        setPendingFile(files[0]);
      }
    });
  }
}

function validateAttachmentFile(file) {
  if (!file) {
    return "Nie wybrano pliku.";
  }

  if (file.size === 0) {
    return "Plik jest pusty.";
  }

  if (file.size > MAX_ATTACHMENT_SIZE) {
    return `Plik jest za duży (${formatFileSize(file.size)}). Maksymalny rozmiar to 5 MB.`;
  }

  const dotIndex = file.name.lastIndexOf(".");

  const extension =
    dotIndex === -1 ? "" : file.name.slice(dotIndex + 1).toLowerCase();

  if (!ALLOWED_ATTACHMENT_EXTENSIONS.includes(extension)) {
    return "Ten typ pliku nie jest obsługiwany. Dozwolone: PDF, PNG, JPG, GIF, WEBP, DOC(X), XLS(X), PPT(X), TXT, CSV.";
  }

  return null;
}

function setPendingFile(file) {
  const error = validateAttachmentFile(file);

  if (error) {
    alert(error);

    return;
  }

  pendingFile = file;

  const preview = document.getElementById("attachment-preview");

  const name = document.getElementById("attachment-preview-name");

  const size = document.getElementById("attachment-preview-size");

  if (name) {
    name.textContent = file.name;
  }

  if (size) {
    size.textContent = formatFileSize(file.size);
  }

  if (preview) {
    preview.hidden = false;
  }

  const input = document.getElementById("message-input");

  if (input) {
    input.focus();
  }
}

function clearPendingFile() {
  pendingFile = null;

  const preview = document.getElementById("attachment-preview");

  if (preview) {
    preview.hidden = true;
  }
}

function setAttachControlsDisabled(disabled) {
  const attachButton = document.getElementById("attach-file-button");

  const removeButton = document.getElementById("attachment-remove-button");

  if (attachButton) {
    attachButton.disabled = disabled;
  }

  if (removeButton) {
    removeButton.disabled = disabled;
  }
}

function formatFileSize(bytes) {
  const size = Number(bytes) || 0;

  if (size < 1024) {
    return `${size} B`;
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(0)} KB`;
  }

  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/* =====================================================
   ZAŁĄCZNIKI - WYŚWIETLANIE I POBIERANIE
   ===================================================== */

function getAttachmentIcon(attachment) {
  const type = attachment.contentType || "";

  if (type === "application/pdf") {
    return "📄";
  }

  if (type.includes("word")) {
    return "📝";
  }

  if (type.includes("excel") || type.includes("spreadsheet") || type === "text/csv") {
    return "📊";
  }

  if (type.includes("powerpoint") || type.includes("presentation")) {
    return "📽️";
  }

  return "📎";
}

function buildAttachmentElement(message) {
  const attachment = message.attachment;

  const wrapper = document.createElement("div");

  wrapper.className = "message-attachment";

  const isImage = (attachment.contentType || "").startsWith("image/");

  if (isImage) {
    const image = document.createElement("img");

    image.className = "attachment-image";

    image.alt = attachment.fileName;

    image.title = attachment.fileName;

    image.addEventListener("click", () => {
      openAttachment(message);
    });

    getAttachmentBlobUrl(message)
      .then((url) => {
        image.src = url;
      })
      .catch((error) => {
        console.error("Błąd wczytywania obrazu:", error);

        wrapper.replaceChildren(buildAttachmentFileButton(message));
      });

    wrapper.appendChild(image);

    return wrapper;
  }

  wrapper.appendChild(buildAttachmentFileButton(message));

  return wrapper;
}

function buildAttachmentFileButton(message) {
  const attachment = message.attachment;

  const button = document.createElement("button");

  button.type = "button";

  button.className = "attachment-file";

  button.title = "Otwórz / pobierz";

  const icon = document.createElement("span");

  icon.className = "attachment-file-icon";

  icon.textContent = getAttachmentIcon(attachment);

  const info = document.createElement("span");

  info.className = "attachment-file-info";

  const name = document.createElement("span");

  name.className = "attachment-file-name";

  name.textContent = attachment.fileName;

  const size = document.createElement("span");

  size.className = "attachment-file-size";

  size.textContent = formatFileSize(attachment.fileSize);

  info.appendChild(name);

  info.appendChild(size);

  button.appendChild(icon);

  button.appendChild(info);

  button.addEventListener("click", () => {
    openAttachment(message);
  });

  return button;
}

/*
 * Plik pobieramy przez fetch z ciasteczkiem sesji (endpoint wymaga logowania),
 * a następnie pokazujemy go jako blob URL.
 */
function getAttachmentBlobUrl(message) {
  if (attachmentBlobUrls.has(message.id)) {
    return attachmentBlobUrls.get(message.id);
  }

  const promise = fetch(
    `${CHAT_API_BASE_URL}/api/conversations/${encodeURIComponent(
      conversationId,
    )}/messages/${encodeURIComponent(message.id)}/attachment`,
    {
      method: "GET",
      credentials: "include",
    },
  )
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`Nie udało się pobrać pliku. HTTP ${response.status}`);
      }

      const blob = await response.blob();

      return URL.createObjectURL(blob);
    })
    .catch((error) => {
      // przy błędzie pozwalamy spróbować ponownie przy następnym kliknięciu
      attachmentBlobUrls.delete(message.id);

      throw error;
    });

  attachmentBlobUrls.set(message.id, promise);

  return promise;
}

async function openAttachment(message) {
  const attachment = message.attachment;

  try {
    const url = await getAttachmentBlobUrl(message);

    const type = attachment.contentType || "";

    // PDF i obrazy otwieramy w nowej karcie, resztę pobieramy
    if (type === "application/pdf" || type.startsWith("image/")) {
      window.open(url, "_blank", "noopener");

      return;
    }

    const link = document.createElement("a");

    link.href = url;

    link.download = attachment.fileName;

    document.body.appendChild(link);

    link.click();

    link.remove();
  } catch (error) {
    console.error("Błąd otwierania załącznika:", error);

    alert(error.message || "Nie udało się pobrać pliku.");
  }
}

/* =====================================================
   AUTO REFRESH
   ===================================================== */

function startAutoRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval);
  }

  refreshInterval = setInterval(async () => {
    try {
      await loadMessages(false);
    } catch (error) {
      console.error("Błąd odświeżania wiadomości:", error);
    }
  }, 3000);
}

/* =====================================================
   SCROLL
   ===================================================== */

function scrollMessagesToBottom() {
  const container = document.getElementById("messages-container");

  if (!container) {
    return;
  }

  container.scrollTop = container.scrollHeight;
}

/* =====================================================
   WRÓĆ Z CZATU
   ===================================================== */

function setupBackButton() {
  const button = document.getElementById("back-button");

  if (!button) {
    return;
  }

  button.addEventListener("click", () => {
    /*
     * Czat otwarty z listy rozmów - wracamy do listy.
     */

    if (chatParams.get("from") === "conversations") {
      window.location.href = "conversations.html";

      return;
    }

    /*
     * Jeżeli czat został otwarty
     * z konkretnego ogłoszenia,
     * wracamy bezpośrednio do niego.
     */

    if (offerId) {
      window.location.href = `offer-details.html?id=${encodeURIComponent(
        offerId,
      )}`;

      return;
    }

    /*
     * Awaryjnie, gdyby czat został
     * otwarty bez offerId.
     */

    window.location.href = "index.html#offers";
  });
}

/* =====================================================
   ERROR
   ===================================================== */

function showError(message) {
  const container = document.getElementById("messages-container");

  if (!container) {
    return;
  }

  container.innerHTML = "";

  const error = document.createElement("div");

  error.className = "empty-chat";

  error.textContent = message;

  container.appendChild(error);
}

/* =====================================================
   CLEANUP
   ===================================================== */

window.addEventListener("beforeunload", () => {
  attachmentBlobUrls.forEach((promise) => {
    promise.then((url) => URL.revokeObjectURL(url)).catch(() => {});
  });

  if (refreshInterval) {
    clearInterval(refreshInterval);

    refreshInterval = null;
  }
});
