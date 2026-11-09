const SETTINGS_API_BASE_URL = "http://localhost:8080";

let currentSettingsUser = null;

const PHONE_COUNTRIES = {
  PL: {
    code: "+48",
    min: 9,
    max: 9,
    name: "Polska",
  },

  UA: {
    code: "+380",
    min: 9,
    max: 9,
    name: "Ukraina",
  },

  DE: {
    code: "+49",
    min: 10,
    max: 11,
    name: "Niemcy",
  },

  GB: {
    code: "+44",
    min: 10,
    max: 10,
    name: "Wielka Brytania",
  },

  FR: {
    code: "+33",
    min: 9,
    max: 9,
    name: "Francja",
  },

  CZ: {
    code: "+420",
    min: 9,
    max: 9,
    name: "Czechy",
  },

  SK: {
    code: "+421",
    min: 9,
    max: 9,
    name: "Słowacja",
  },

  AT: {
    code: "+43",
    min: 10,
    max: 13,
    name: "Austria",
  },
};

const defaultAvatarSvg = `
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="8" r="4"></circle>
    <path d="M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7"></path>
  </svg>
`;

document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("profile-settings-form");

  if (!form) {
    return;
  }

  loadSettingsUser();

  form.addEventListener("submit", handleProfileSave);

  document
    .getElementById("avatar-input")
    ?.addEventListener("change", handleAvatarUpload);

  document
    .getElementById("remove-avatar-button")
    ?.addEventListener("click", handleAvatarDelete);

  setupPhoneValidation();
});

async function loadSettingsUser() {
  try {
    const response = await fetch(`${SETTINGS_API_BASE_URL}/api/auth/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Nie udało się pobrać danych użytkownika.");
    }

    const user = await response.json();

    currentSettingsUser = user;

    fillSettingsForm(user);
    renderSettingsAvatar(user.avatarUrl);
  } catch (error) {
    console.error("Błąd pobierania ustawień:", error);

    showSettingsMessage("Nie udało się pobrać danych profilu.", true);
  }
}

function fillSettingsForm(user) {
  document.getElementById("nickname").value = user.nickname ?? "";

  document.getElementById("email").value = user.email ?? "";

  document.getElementById("location").value = user.location ?? "";

  setPhoneFromUser(user.phone);
}

function setPhoneFromUser(phoneValue) {
  const countrySelect = document.getElementById("phone-country");

  const phoneInput = document.getElementById("phone");

  if (!countrySelect || !phoneInput) {
    return;
  }

  if (!phoneValue) {
    countrySelect.value = "PL";
    phoneInput.value = "";

    updatePhoneCountryUI();

    return;
  }

  const phone = String(phoneValue).trim();

  const countryEntry = Object.entries(PHONE_COUNTRIES).find(([, country]) =>
    phone.startsWith(country.code),
  );

  if (countryEntry) {
    const [countryKey, country] = countryEntry;

    countrySelect.value = countryKey;

    phoneInput.value = phone.substring(country.code.length).replace(/\D/g, "");

    updatePhoneCountryUI();

    return;
  }

  /*
   * Obsługa starego numeru zapisanego bez kodu kraju.
   * Traktujemy go jako polski numer.
   */
  countrySelect.value = "PL";

  phoneInput.value = phone.replace(/\D/g, "");

  updatePhoneCountryUI();
}

async function handleProfileSave(event) {
  event.preventDefault();

  const nickname = document.getElementById("nickname").value.trim();

  const phoneInput = document.getElementById("phone");

  const location = document.getElementById("location").value.trim();

  const button = document.getElementById("save-profile-button");

  const phone = phoneInput.value.trim();

  if (nickname.length < 3 || nickname.length > 30) {
    showSettingsMessage("Nazwa użytkownika musi mieć od 3 do 30 znaków.", true);

    return;
  }

  if (!validatePhoneField(true)) {
    phoneInput.focus();
    return;
  }

  const countrySelect = document.getElementById("phone-country");

  const selectedCountry = PHONE_COUNTRIES[countrySelect.value];

  const fullPhone = phone ? `${selectedCountry.code}${phone}` : null;

  button.disabled = true;

  showSettingsMessage("Zapisywanie zmian...", false);

  try {
    const response = await fetch(`${SETTINGS_API_BASE_URL}/api/users/me`, {
      method: "PUT",

      headers: {
        "Content-Type": "application/json",
      },

      credentials: "include",

      body: JSON.stringify({
        nickname,
        avatarUrl: currentSettingsUser?.avatarUrl ?? null,
        phone: fullPhone,
        location: location || null,
      }),
    });

    const data = await readJsonResponse(response);

    if (!response.ok) {
      throw new Error(
        data?.message || data?.error || "Nie udało się zapisać zmian.",
      );
    }

    currentSettingsUser = data;

    fillSettingsForm(data);
    renderSettingsAvatar(data.avatarUrl);

    showSettingsMessage("Zmiany zostały zapisane.", false);
  } catch (error) {
    console.error("Błąd zapisywania profilu:", error);

    showSettingsMessage(error.message, true);
  } finally {
    button.disabled = false;
  }
}

function setupPhoneValidation() {
  const countrySelect = document.getElementById("phone-country");

  const phoneInput = document.getElementById("phone");

  if (!countrySelect || !phoneInput) {
    return;
  }

  updatePhoneCountryUI();

  countrySelect.addEventListener("change", () => {
    phoneInput.value = "";

    updatePhoneCountryUI();

    phoneInput.focus();
  });

  phoneInput.addEventListener("input", () => {
    phoneInput.value = phoneInput.value.replace(/\D/g, "");

    validatePhoneField(false);
  });

  phoneInput.addEventListener("keydown", (event) => {
    const allowedKeys = [
      "Backspace",
      "Delete",
      "ArrowLeft",
      "ArrowRight",
      "ArrowUp",
      "ArrowDown",
      "Tab",
      "Home",
      "End",
    ];

    if (allowedKeys.includes(event.key) || event.ctrlKey || event.metaKey) {
      return;
    }

    if (!/^[0-9]$/.test(event.key)) {
      event.preventDefault();
    }
  });

  phoneInput.addEventListener("paste", () => {
    setTimeout(() => {
      phoneInput.value = phoneInput.value.replace(/\D/g, "");

      validatePhoneField(false);
    }, 0);
  });
}

function updatePhoneCountryUI() {
  const countrySelect = document.getElementById("phone-country");

  const phoneInput = document.getElementById("phone");

  const codeElement = document.getElementById("phone-country-code");

  const helpElement = document.getElementById("phone-help");

  if (!countrySelect || !phoneInput) {
    return;
  }

  const country = PHONE_COUNTRIES[countrySelect.value];

  if (!country) {
    return;
  }

  if (codeElement) {
    codeElement.textContent = country.code;
  }

  phoneInput.maxLength = country.max;

  if (country.min === country.max) {
    phoneInput.placeholder = `${"0".repeat(country.max)}`;
  } else {
    phoneInput.placeholder = `${country.min}-${country.max} cyfr`;
  }

  if (helpElement) {
    if (country.min === country.max) {
      helpElement.textContent = `${country.name}: wpisz dokładnie ${country.min} cyfr.`;
    } else {
      helpElement.textContent = `${country.name}: wpisz od ${country.min} do ${country.max} cyfr.`;
    }
  }

  phoneInput.setCustomValidity("");
}

function validatePhoneField(showMessage = true) {
  const countrySelect = document.getElementById("phone-country");

  const phoneInput = document.getElementById("phone");

  if (!countrySelect || !phoneInput) {
    return true;
  }

  const country = PHONE_COUNTRIES[countrySelect.value];

  if (!country) {
    return false;
  }

  const phone = phoneInput.value.trim();

  /*
   * Numer telefonu jest opcjonalny.
   */
  if (!phone) {
    phoneInput.setCustomValidity("");
    return true;
  }

  /*
   * Tylko cyfry.
   */
  if (!/^\d+$/.test(phone)) {
    const message = "Numer telefonu może zawierać tylko cyfry.";

    phoneInput.setCustomValidity(message);

    if (showMessage) {
      showSettingsMessage(message, true);
    }

    return false;
  }

  /*
   * Sprawdzenie wymaganej długości
   * dla wybranego kraju.
   */
  if (phone.length < country.min || phone.length > country.max) {
    let message;

    if (country.min === country.max) {
      message = `${country.name}: numer telefonu musi mieć dokładnie ${country.min} cyfr.`;
    } else {
      message = `${country.name}: numer telefonu musi mieć od ${country.min} do ${country.max} cyfr.`;
    }

    phoneInput.setCustomValidity(message);

    if (showMessage) {
      showSettingsMessage(message, true);
    }

    return false;
  }

  phoneInput.setCustomValidity("");

  return true;
}

async function handleAvatarUpload(event) {
  const file = event.target.files?.[0];

  if (!file) {
    return;
  }

  const allowedTypes = ["image/png", "image/jpeg"];

  if (!allowedTypes.includes(file.type)) {
    showSettingsMessage("Dozwolone są tylko pliki PNG i JPG.", true);

    event.target.value = "";

    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    showSettingsMessage(
      "Zdjęcie profilowe nie może być większe niż 5 MB.",
      true,
    );

    event.target.value = "";

    return;
  }

  showSettingsMessage("Przesyłanie zdjęcia...", false);

  const formData = new FormData();

  formData.append("file", file);

  try {
    const response = await fetch(
      `${SETTINGS_API_BASE_URL}/api/users/me/avatar`,
      {
        method: "POST",
        credentials: "include",
        body: formData,
      },
    );

    const data = await readJsonResponse(response);

    if (!response.ok) {
      throw new Error(
        data?.message || data?.error || "Nie udało się przesłać zdjęcia.",
      );
    }

    currentSettingsUser = data;

    renderSettingsAvatar(data.avatarUrl);

    showSettingsMessage("Zdjęcie profilowe zostało zmienione.", false);
  } catch (error) {
    console.error("Błąd przesyłania zdjęcia:", error);

    showSettingsMessage(error.message, true);
  } finally {
    event.target.value = "";
  }
}

async function handleAvatarDelete() {
  if (!currentSettingsUser?.avatarUrl) {
    showSettingsMessage("Nie masz ustawionego zdjęcia profilowego.", true);

    return;
  }

  if (!window.confirm("Czy na pewno chcesz usunąć zdjęcie profilowe?")) {
    return;
  }

  try {
    const response = await fetch(
      `${SETTINGS_API_BASE_URL}/api/users/me/avatar`,
      {
        method: "DELETE",
        credentials: "include",
      },
    );

    const data = await readJsonResponse(response);

    if (!response.ok) {
      throw new Error(
        data?.message || data?.error || "Nie udało się usunąć zdjęcia.",
      );
    }

    currentSettingsUser = data;

    renderSettingsAvatar(data.avatarUrl);

    showSettingsMessage("Zdjęcie profilowe zostało usunięte.", false);
  } catch (error) {
    console.error("Błąd usuwania zdjęcia:", error);

    showSettingsMessage(error.message, true);
  }
}

function renderSettingsAvatar(avatarUrl) {
  const previewElements = [
    document.getElementById("settings-avatar-preview"),

    document.getElementById("avatar-large-preview"),
  ];

  const src = getSettingsAvatarSrc(avatarUrl);

  previewElements.forEach((element) => {
    if (!element) {
      return;
    }

    if (src) {
      element.innerHTML = `
        <img
          src="${escapeSettingsHtml(src)}"
          alt="Zdjęcie profilowe"
          onerror="handleSettingsAvatarError(this)"
        />
      `;
    } else {
      element.innerHTML = defaultAvatarSvg;
    }
  });
}

// Zdjęcie jest w bazie, ale pliku nie da się wczytać:
// zamiast uszkodzonego obrazka pokazujemy domyślną ikonkę.
function handleSettingsAvatarError(img) {
  img.onerror = null;

  if (img.parentElement) {
    img.parentElement.innerHTML = defaultAvatarSvg;
  }
}

function getSettingsAvatarSrc(avatarUrl) {
  if (!avatarUrl) {
    return null;
  }

  if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
    return avatarUrl;
  }

  return `${SETTINGS_API_BASE_URL}${avatarUrl}`;
}

async function readJsonResponse(response) {
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

function showSettingsMessage(message, isError) {
  const element = document.getElementById("settings-message");

  if (!element) {
    return;
  }

  element.textContent = message;

  element.classList.toggle("is-error", Boolean(isError));

  element.classList.toggle("is-success", !isError);
}

function escapeSettingsHtml(value) {
  const div = document.createElement("div");

  div.textContent = value;

  return div.innerHTML;
}
