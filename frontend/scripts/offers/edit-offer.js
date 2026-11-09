const EDIT_OFFER_API_BASE_URL = "http://localhost:8080";
const editOfferId = new URLSearchParams(window.location.search).get("id");

let loadedOffer = null;

document.addEventListener("DOMContentLoaded", async () => {
  if (!editOfferId) {
    showEditOfferMessage("Brakuje identyfikatora ogłoszenia.", true);
    return;
  }

  bindEditForm();
  await loadCategoriesForEdit();
  await loadOfferForEdit();
});

function bindEditForm() {
  document.getElementById("edit-offer-form")?.addEventListener("submit", updateOffer);
  document.getElementById("title")?.addEventListener("input", updateCounters);
  document.getElementById("description")?.addEventListener("input", updateCounters);
}

async function loadCategoriesForEdit() {
  const select = document.getElementById("category");
  if (!select) return;

  try {
    const response = await fetch(`${EDIT_OFFER_API_BASE_URL}/api/categories`);
    const categories = await response.json();

    select.innerHTML = '<option value="" disabled>Wybierz kategorię</option>';

    categories.forEach((category) => {
      const option = document.createElement("option");
      option.value = category.id;
      option.textContent = category.name;
      select.appendChild(option);
    });
  } catch (error) {
    showEditOfferMessage("Nie udało się pobrać kategorii.", true);
  }
}

async function loadOfferForEdit() {
  try {
    const response = await fetch(`${EDIT_OFFER_API_BASE_URL}/api/offers/${encodeURIComponent(editOfferId)}`, {
      credentials: "include",
    });

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    const data = await readEditResponse(response);
    if (!response.ok) {
      throw new Error(data?.message || data?.error || "Nie udało się pobrać ogłoszenia.");
    }

    loadedOffer = data;

    document.getElementById("title").value = data.title || "";
    document.getElementById("description").value = data.description || "";
    document.getElementById("type").value = data.type || "SALE";
    document.getElementById("status").value = data.status || "ACTIVE";
    document.getElementById("category").value = String(data.categoryId ?? "");
    document.getElementById("price").value = data.price ?? "";
    const locationField = document.getElementById("location");

    locationField.value = data.location || "";

    // Zapisany obszar mapy zostaje, dopóki ktoś nie zmieni tekstu lokalizacji.
    if (data.location && data.latitude != null && data.longitude != null) {
      locationField.dataset.placeFor = data.location.trim();
      locationField.dataset.placeLat = String(data.latitude);
      locationField.dataset.placeLng = String(data.longitude);

      if (data.locationRadiusMeters != null) {
        locationField.dataset.placeRadius = String(data.locationRadiusMeters);
      }
    }
    setOnlinePaymentValue(Boolean(data.onlinePaymentEnabled));
    setShippingCostValue(data.shippingCost);
    updateCounters();
    await loadExistingImages();
  } catch (error) {
    console.error("Błąd pobierania ogłoszenia:", error);
    showEditOfferMessage(error.message, true);
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

async function updateOffer(event) {
  event.preventDefault();

  // Jeśli ktoś dopiero co wybrał miasto z podpowiedzi, poczekaj na obszar mapy.
  const locationInput = document.getElementById("location");

  await window.JollyCartLocationPicker?.whenReady(locationInput);

  if (!loadedOffer) {
    showEditOfferMessage("Ogłoszenie nie zostało jeszcze załadowane.", true);
    return;
  }

  const title = document.getElementById("title").value.trim();
  const description = document.getElementById("description").value.trim();
  const type = document.getElementById("type").value;
  const status = document.getElementById("status").value;
  const categoryId = document.getElementById("category").value;
  const priceInput = document.getElementById("price").value.trim();
  const location = locationInput?.value.trim() || null;
  const locationArea = readLocationArea(locationInput, location);
  const imageFiles = Array.from(document.getElementById("image")?.files || []);
  const button = document.getElementById("edit-offer-submit");

  if (!title || !description || !type || !status || !categoryId) {
    showEditOfferMessage("Wypełnij wszystkie wymagane pola.", true);
    return;
  }

  let price = null;
  if (priceInput !== "") {
    price = Number(priceInput.replace(",", "."));
    if (!Number.isFinite(price) || price < 0) {
      showEditOfferMessage("Cena musi być poprawną liczbą większą lub równą 0.", true);
      return;
    }
  }

  button.disabled = true;
  showEditOfferMessage("Zapisywanie zmian...", false);

  try {
    const response = await fetch(`${EDIT_OFFER_API_BASE_URL}/api/offers/${encodeURIComponent(editOfferId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
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
        status,
        categoryId: Number(categoryId),
        onlinePaymentEnabled: getOnlinePaymentValue(),
        shippingCost: getShippingCostValue(),
      }),
    });

    const data = await readEditResponse(response);

    if (response.status === 401 || response.status === 403) {
      window.location.href = "login.html";
      return;
    }

    if (!response.ok) {
      throw new Error(data?.message || data?.error || "Nie udało się zapisać zmian.");
    }

    let failedImages = 0;

    for (const imageFile of imageFiles) {
      const formData = new FormData();
      formData.append("file", imageFile);
      const imageResponse = await fetch(
        `${EDIT_OFFER_API_BASE_URL}/api/offers/${encodeURIComponent(editOfferId)}/images`,
        { method: "POST", credentials: "include", body: formData }
      );
      if (!imageResponse.ok) {
        failedImages += 1;
      }
    }

    if (failedImages > 0) {
      throw new Error(`Zmiany zapisano, ale nie udało się dodać zdjęć: ${failedImages} z ${imageFiles.length}.`);
    }

    showEditOfferMessage("Zmiany zostały zapisane.", false);
    setTimeout(() => { window.location.href = "my-offers.html"; }, 700);
  } catch (error) {
    console.error("Błąd edycji ogłoszenia:", error);
    showEditOfferMessage(error.message, true);
    button.disabled = false;
  }
}

function updateCounters() {
  const title = document.getElementById("title");
  const description = document.getElementById("description");
  if (title) document.getElementById("title-counter").textContent = `${title.value.length}/150`;
  if (description) document.getElementById("description-counter").textContent = `${description.value.length}/5000`;
}

async function loadExistingImages() {
  const field = document.getElementById("existing-images-field");
  const container = document.getElementById("existing-images");
  if (!field || !container) return;

  try {
    const response = await fetch(`${EDIT_OFFER_API_BASE_URL}/api/offers/${encodeURIComponent(editOfferId)}/images`);
    if (!response.ok) return;
    const images = await response.json();
    if (!Array.isArray(images) || images.length === 0) return;

    field.hidden = false;
    container.innerHTML = images.map((image) => `
      <div class="existing-offer-image" data-image-id="${escapeEditHtml(String(image.id))}">
        <img src="${EDIT_OFFER_API_BASE_URL}/api/offers/images/${encodeURIComponent(image.id)}" alt="Zdjęcie ogłoszenia" />
        <button type="button" class="existing-offer-image-delete" data-delete-image="${escapeEditHtml(String(image.id))}">Usuń zdjęcie</button>
      </div>
    `).join("");

    container.querySelectorAll("[data-delete-image]").forEach((button) => {
      button.addEventListener("click", () => deleteOfferImage(button.dataset.deleteImage));
    });
  } catch (error) {
    console.error("Błąd pobierania zdjęć:", error);
  }
}

async function deleteOfferImage(imageId) {
  if (!confirm("Czy na pewno chcesz usunąć to zdjęcie?")) return;
  try {
    const response = await fetch(`${EDIT_OFFER_API_BASE_URL}/api/offers/images/${encodeURIComponent(imageId)}`, {
      method: "DELETE", credentials: "include"
    });
    if (!response.ok) {
      const data = await readEditResponse(response);
      throw new Error(data?.message || data?.error || "Nie udało się usunąć zdjęcia.");
    }
    await loadExistingImages();
  } catch (error) {
    alert(error.message || "Nie udało się usunąć zdjęcia.");
  }
}

function escapeEditHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}

function showEditOfferMessage(message, isError) {
  const element = document.getElementById("edit-offer-message");
  if (!element) return;
  element.textContent = message || "";
  element.classList.toggle("is-error", Boolean(isError));
  element.classList.toggle("is-success", !isError);
}

async function readEditResponse(response) {
  const text = await response.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return { message: text }; }
}
