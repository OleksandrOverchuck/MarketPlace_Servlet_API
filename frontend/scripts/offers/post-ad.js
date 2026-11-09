const POST_AD_API_BASE_URL = "http://localhost:8080";

const POST_AD_MAX_IMAGES = 10;
const POST_AD_MAX_IMAGE_SIZE = 10 * 1024 * 1024;
const POST_AD_ALLOWED_IMAGE_TYPES = ["image/png", "image/jpeg"];

// Wszystkie wybrane zdjęcia. Kolejne wybory z okna plików dokładają się
// do listy, a nie zastępują poprzednich.
let selectedImages = [];

document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("post-ad-form");

  if (!form) {
    return;
  }

  checkAuthentication();
  loadCategories();

  document.getElementById("title")?.addEventListener("input", updateCounters);

  document
    .getElementById("description")
    ?.addEventListener("input", updateCounters);

  document
    .getElementById("image")
    ?.addEventListener("change", handleImagesSelected);

  form.addEventListener("submit", handleCreateOffer);

  updateCounters();
});

async function checkAuthentication() {
  try {
    const response = await fetch(`${POST_AD_API_BASE_URL}/api/auth/me`, {
      method: "GET",
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      throw new Error("Nie udało się sprawdzić sesji.");
    }

    const user = await response.json();

    showPhoneNote(user?.phone);
  } catch (error) {
    console.error("Błąd sprawdzania zalogowania:", error);
    window.location.href = "login.html";
  }
}

// Informacja, czy numer telefonu z profilu trafi do ogłoszenia.
function showPhoneNote(phone) {
  const note = document.getElementById("post-ad-phone-note");

  if (!note) {
    return;
  }

  const link = document.createElement("a");

  link.href = "settings.html";

  note.textContent = "";

  if (phone) {
    const formatted = /^\+48\d{9}$/.test(phone)
      ? phone.replace(/^(\+48)(\d{3})(\d{3})(\d{3})$/, "$1 $2 $3 $4")
      : phone;

    note.append(
      "W ogłoszeniu pokażemy Twój numer telefonu z profilu: ",
    );

    const strong = document.createElement("strong");
    strong.textContent = formatted;
    note.append(strong, ". ");

    link.textContent = "Zmień numer w profilu";
  } else {
    note.append(
      "Nie masz numeru telefonu w profilu, więc nie będzie widoczny w ogłoszeniu. ",
    );

    link.textContent = "Dodaj numer w profilu";
  }

  note.append(link);
  note.hidden = false;
}

async function loadCategories() {
  const categorySelect = document.getElementById("category");

  if (!categorySelect) {
    return;
  }

  try {
    const response = await fetch(`${POST_AD_API_BASE_URL}/api/categories`, {
      method: "GET",
      credentials: "include",
    });

    if (!response.ok) {
      throw new Error("Nie udało się pobrać kategorii.");
    }

    const categories = await response.json();

    categorySelect.innerHTML =
      '<option value="" selected disabled>Wybierz kategorię</option>';

    categories.forEach((category) => {
      const option = document.createElement("option");
      option.value = category.id;
      option.textContent = category.name;
      categorySelect.appendChild(option);
    });
  } catch (error) {
    console.error("Błąd pobierania kategorii:", error);

    categorySelect.innerHTML =
      '<option value="" selected disabled>Nie udało się pobrać kategorii</option>';

    showPostAdMessage(
      "Nie udało się pobrać kategorii. Odśwież stronę i spróbuj ponownie.",
      true,
    );
  }
}

// Współrzędne wybranej miejscowości zapisuje na polu location-picker.js
// (data-place-*). Ważne są tylko wtedy, gdy tekst w polu nadal jest tym,
// który wybrano z podpowiedzi - po ręcznej zmianie tekstu wysyłamy null.
function readLocationArea(input, locationText) {
  const empty = {
    latitude: null,
    longitude: null,
    locationRadiusMeters: null,
  };

  if (!input || !locationText) {
    return empty;
  }

  const data = input.dataset;

  if (
    data.placeFor !== locationText ||
    data.placeLat === undefined ||
    data.placeLng === undefined
  ) {
    return empty;
  }

  const latitude = Number(data.placeLat);
  const longitude = Number(data.placeLng);
  const radius = Number(data.placeRadius);

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return empty;
  }

  return {
    latitude,
    longitude,
    locationRadiusMeters: Number.isFinite(radius) ? Math.round(radius) : null,
  };
}

async function handleCreateOffer(event) {
  event.preventDefault();

  // Jeśli ktoś dopiero co wybrał miasto z podpowiedzi, poczekaj na obszar mapy.
  const locationInput = document.getElementById("location");

  await window.JollyCartLocationPicker?.whenReady(locationInput);

  const title = document.getElementById("title").value.trim();
  const description = document.getElementById("description").value.trim();
  const type = document.getElementById("type").value;
  const categoryId = document.getElementById("category").value;
  const priceInput = document.getElementById("price").value.trim();
  const location = locationInput?.value.trim() || null;
  const locationArea = readLocationArea(locationInput, location);
  const submitButton = document.getElementById("post-ad-submit");

  if (!title) {
    showPostAdMessage("Tytuł ogłoszenia jest wymagany.", true);
    return;
  }

  if (!description) {
    showPostAdMessage("Opis ogłoszenia jest wymagany.", true);
    return;
  }

  if (!type) {
    showPostAdMessage("Wybierz typ ogłoszenia.", true);
    return;
  }

  if (!categoryId) {
    showPostAdMessage("Wybierz kategorię.", true);
    return;
  }

  let price = null;

  if (priceInput !== "") {
    const normalizedPrice = priceInput.replace(",", ".");
    const parsedPrice = Number(normalizedPrice);

    if (!Number.isFinite(parsedPrice) || parsedPrice < 0) {
      showPostAdMessage(
        "Cena musi być poprawną liczbą większą lub równą 0.",
        true,
      );
      return;
    }

    price = parsedPrice;
  }

  submitButton.disabled = true;
  showPostAdMessage("Dodawanie ogłoszenia...", false);

  try {
    const response = await fetch(`${POST_AD_API_BASE_URL}/api/offers`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        title,
        description,
        price,
        location,
        latitude: locationArea.latitude,
        longitude: locationArea.longitude,
        locationRadiusMeters: locationArea.locationRadiusMeters,
        type,
        categoryId: Number(categoryId),
        onlinePaymentEnabled: getOnlinePaymentValue(),
        shippingCost: getShippingCostValue(),
      }),
    });

    const data = await readJsonResponse(response);

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      throw new Error(
        data?.message || data?.error || "Nie udało się dodać ogłoszenia.",
      );
    }

    if (selectedImages.length > 0) {
      const failedImages = await uploadOfferImages(data.id, selectedImages);

      if (failedImages.length > 0) {
        showPostAdMessage(
          `Ogłoszenie zostało dodane, ale nie udało się przesłać zdjęć: ${failedImages.length} z ${selectedImages.length}. Możesz dodać je później w edycji ogłoszenia.`,
          true,
        );
      } else {
        showPostAdMessage(
          selectedImages.length === 1
            ? "Ogłoszenie i zdjęcie zostały dodane. Za chwilę przejdziesz do listy ogłoszeń."
            : "Ogłoszenie i zdjęcia zostały dodane. Za chwilę przejdziesz do listy ogłoszeń.",
          false,
        );
      }
    } else {
      showPostAdMessage(
        "Ogłoszenie zostało dodane. Za chwilę przejdziesz do listy ogłoszeń.",
        false,
      );
    }

    setTimeout(() => {
      window.location.href = "index.html#offers";
    }, 1000);
  } catch (error) {
    console.error("Błąd dodawania ogłoszenia:", error);
    showPostAdMessage(error.message, true);
    submitButton.disabled = false;
  }
}

// Wysyła zdjęcia po kolei (backend przyjmuje jedno zdjęcie na żądanie,
// więc każde z nich ma osobny limit 10 MB). Zwraca listę nieudanych.
async function uploadOfferImages(offerId, files) {
  const failed = [];

  for (const file of files) {
    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await fetch(
        `${POST_AD_API_BASE_URL}/api/offers/${encodeURIComponent(offerId)}/images`,
        {
          method: "POST",
          credentials: "include",
          body: formData,
        },
      );

      if (!response.ok) {
        failed.push(file);
      }
    } catch (error) {
      console.error("Błąd wysyłania zdjęcia:", error);
      failed.push(file);
    }
  }

  return failed;
}

function handleImagesSelected(event) {
  const input = event.target;
  const files = Array.from(input.files || []);
  const errors = [];

  files.forEach((file) => {
    if (!POST_AD_ALLOWED_IMAGE_TYPES.includes(file.type)) {
      errors.push(`„${file.name}” – dozwolone są tylko PNG i JPG.`);
      return;
    }

    if (file.size > POST_AD_MAX_IMAGE_SIZE) {
      errors.push(`„${file.name}” – plik jest większy niż 10 MB.`);
      return;
    }

    const isDuplicate = selectedImages.some(
      (image) =>
        image.name === file.name &&
        image.size === file.size &&
        image.lastModified === file.lastModified,
    );

    if (isDuplicate) {
      return;
    }

    if (selectedImages.length >= POST_AD_MAX_IMAGES) {
      errors.push(
        `Możesz dodać maksymalnie ${POST_AD_MAX_IMAGES} zdjęć.`,
      );
      return;
    }

    selectedImages.push(file);
  });

  // Czyścimy input, żeby można było wybrać ten sam plik ponownie
  // (np. po usunięciu) i dokładać kolejne zdjęcia.
  input.value = "";

  renderImagePreviews();

  if (errors.length > 0) {
    showPostAdMessage([...new Set(errors)].join(" "), true);
  } else {
    showPostAdMessage("", false);
  }
}

function removeSelectedImage(index) {
  selectedImages.splice(index, 1);
  renderImagePreviews();
  showPostAdMessage("", false);
}

function moveSelectedImageToFront(index) {
  const [image] = selectedImages.splice(index, 1);
  selectedImages.unshift(image);
  renderImagePreviews();
}

function renderImagePreviews() {
  const container = document.getElementById("image-previews");

  if (!container) {
    return;
  }

  container.querySelectorAll("img").forEach((img) => {
    URL.revokeObjectURL(img.src);
  });

  container.innerHTML = "";

  selectedImages.forEach((file, index) => {
    const item = document.createElement("div");
    item.className = "image-preview-item";

    const img = document.createElement("img");
    img.src = URL.createObjectURL(file);
    img.alt = file.name;

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "image-preview-remove";
    removeButton.setAttribute("aria-label", `Usuń zdjęcie ${file.name}`);
    removeButton.textContent = "×";
    removeButton.addEventListener("click", () => removeSelectedImage(index));

    item.append(img, removeButton);

    if (index === 0) {
      const badge = document.createElement("span");
      badge.className = "image-preview-badge";
      badge.textContent = "Główne";
      item.appendChild(badge);
    } else {
      const mainButton = document.createElement("button");
      mainButton.type = "button";
      mainButton.className = "image-preview-main";
      mainButton.textContent = "Ustaw jako główne";
      mainButton.addEventListener("click", () =>
        moveSelectedImageToFront(index),
      );
      item.appendChild(mainButton);
    }

    container.appendChild(item);
  });
}

function updateCounters() {
  const title = document.getElementById("title");
  const description = document.getElementById("description");
  const titleCounter = document.getElementById("title-counter");
  const descriptionCounter = document.getElementById("description-counter");

  if (title && titleCounter) {
    titleCounter.textContent = `${title.value.length}/150`;
  }

  if (description && descriptionCounter) {
    descriptionCounter.textContent = `${description.value.length}/5000`;
  }
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

function showPostAdMessage(message, isError) {
  const element = document.getElementById("post-ad-message");

  if (!element) {
    return;
  }

  element.textContent = message;
  element.classList.toggle("is-error", Boolean(isError));
  element.classList.toggle("is-success", !isError);
}
