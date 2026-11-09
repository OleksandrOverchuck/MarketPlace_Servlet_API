/*
 * WYBÓR LOKALIZACJI (Google Maps)
 *
 * Każde pole <input data-location-picker> dostaje:
 *   - podpowiedzi miejscowości w trakcie pisania (Places API (New)),
 *   - przycisk "Użyj mojej lokalizacji" (geolokalizacja przeglądarki
 *     + Geocoding API, żeby zamienić współrzędne na nazwę miasta),
 *   - panel z mapą pod polem: kliknięcie w mapę wybiera miejscowość
 *     (Geocoding API), a wybrany obszar jest na niej zaznaczony kołem.
 *
 * Jeśli nie ma klucza API albo Google się nie załaduje, pole zostaje
 * zwykłym polem tekstowym - formularze dalej działają.
 */
(function () {
  "use strict";

  const config = window.JOLLYCART_MAPS || {};

  const API_KEY = String(config.apiKey || "");
  const LANGUAGE = config.language || "pl";
  const COUNTRY_CODES = config.countryCodes || ["pl"];
  const PLACE_TYPES = Array.isArray(config.placeTypes)
    ? config.placeTypes
    : ["(cities)"];
  const BIAS_RADIUS = Math.min(Number(config.biasRadiusMeters) || 50000, 50000);
  const MAX_SUGGESTIONS = Number(config.maxSuggestions) || 5;

  const MIN_CHARS = 2;
  const DEBOUNCE_MS = 250;

  const hasApiKey = API_KEY.length > 10 && !API_KEY.startsWith("WKLEJ");

  const ICONS = {
    locate:
      '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3.5"/><circle cx="12" cy="12" r="7.5"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3"/></svg>',
    pin: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-7-6.2-7-11a7 7 0 1 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>',
  };

  let userCoords = null;
  let mapsPromise = null;
  let librariesPromise = null;
  let authFailed = false;

  /* =========================
     ŁADOWANIE GOOGLE MAPS
     ========================= */

  // Skrypt Google ładujemy dopiero przy pierwszym użyciu pola,
  // żeby nie obciążać stron i nie generować zbędnych zapytań.
  function loadGoogleMaps() {
    if (mapsPromise) {
      return mapsPromise;
    }

    mapsPromise = new Promise((resolve, reject) => {
      if (
        window.google &&
        window.google.maps &&
        window.google.maps.importLibrary
      ) {
        resolve(window.google.maps);
        return;
      }

      const callbackName = "__jollycartMapsReady";

      window[callbackName] = () => {
        delete window[callbackName];
        resolve(window.google.maps);
      };

      // Google wywołuje tę funkcję, gdy klucz jest błędny / zablokowany.
      window.gm_authFailure = () => {
        authFailed = true;
      };

      const script = document.createElement("script");

      script.src =
        "https://maps.googleapis.com/maps/api/js" +
        `?key=${encodeURIComponent(API_KEY)}` +
        "&v=weekly&loading=async" +
        `&language=${encodeURIComponent(LANGUAGE)}` +
        `&callback=${callbackName}`;

      script.async = true;

      script.onerror = () => {
        reject(new Error("Nie udało się załadować Google Maps."));
      };

      document.head.appendChild(script);
    }).catch((error) => {
      mapsPromise = null;

      throw error;
    });

    return mapsPromise;
  }

  function loadLibraries() {
    if (!librariesPromise) {
      librariesPromise = loadGoogleMaps()
        .then(async (maps) => {
          const [places, geocoding] = await Promise.all([
            maps.importLibrary("places"),
            maps.importLibrary("geocoding"),
          ]);

          return { places, geocoding };
        })
        .catch((error) => {
          librariesPromise = null;

          throw error;
        });
    }

    return librariesPromise;
  }

  /* =========================
     OBSZAR LOKALIZACJI (mapa na stronie ogłoszenia)
     ========================= */

  // Po wyborze miejscowości zapisujemy na polu orientacyjny środek i promień
  // obszaru (data-place-*). Formularze wysyłają je razem z ogłoszeniem.
  // data-place-for to tekst, do którego należą współrzędne - jeśli ktoś
  // potem ręcznie zmieni tekst, współrzędne przestają być ważne.
  const MIN_AREA_RADIUS = 800;
  const MAX_AREA_RADIUS = 50000;
  const DEFAULT_AREA_RADIUS = 2000;

  // Trwające pobieranie obszaru dla danego pola (żeby formularz mógł poczekać).
  const pendingAreas = new WeakMap();

  function roundCoord(value) {
    return Math.round(value * 10000) / 10000;
  }

  function distanceMeters(a, b) {
    const R = 6371000;
    const toRad = (deg) => (deg * Math.PI) / 180;

    const dLat = toRad(b.lat() - a.lat());
    const dLng = toRad(b.lng() - a.lng());

    const h =
      Math.sin(dLat / 2) ** 2 +
      Math.cos(toRad(a.lat())) *
        Math.cos(toRad(b.lat())) *
        Math.sin(dLng / 2) ** 2;

    return 2 * R * Math.asin(Math.sqrt(h));
  }

  function radiusFromViewport(viewport) {
    if (!viewport) {
      return DEFAULT_AREA_RADIUS;
    }

    const diagonal = distanceMeters(
      viewport.getNorthEast(),
      viewport.getSouthWest(),
    );

    const radius = Math.round(diagonal / 2);

    return Math.min(Math.max(radius, MIN_AREA_RADIUS), MAX_AREA_RADIUS);
  }

  function areaFromGeometry(geometry) {
    if (!geometry || !geometry.location) {
      return null;
    }

    return {
      lat: roundCoord(geometry.location.lat()),
      lng: roundCoord(geometry.location.lng()),
      radius: radiusFromViewport(geometry.viewport),
    };
  }

  async function fetchAreaForPrediction(prediction) {
    const place = prediction.toPlace();

    await place.fetchFields({ fields: ["location", "viewport"] });

    return areaFromGeometry({
      location: place.location,
      viewport: place.viewport,
    });
  }

  function setArea(input, text, area) {
    const normalized = String(text || "").trim();

    if (!area || !normalized) {
      clearArea(input);

      return;
    }

    input.dataset.placeFor = normalized;
    input.dataset.placeLat = String(area.lat);
    input.dataset.placeLng = String(area.lng);
    input.dataset.placeRadius = String(area.radius);
  }

  function clearArea(input) {
    delete input.dataset.placeFor;
    delete input.dataset.placeLat;
    delete input.dataset.placeLng;
    delete input.dataset.placeRadius;
  }

  function trackArea(input, promise) {
    const tracked = promise.catch((error) => {
      console.error("Nie udało się pobrać obszaru lokalizacji:", error);

      clearArea(input);
    });

    pendingAreas.set(input, tracked);

    return tracked;
  }

  // Dla formularzy: poczekaj, aż skończy się pobieranie obszaru po wyborze miasta.
  window.JollyCartLocationPicker = {
    whenReady(input) {
      return input && pendingAreas.get(input)
        ? pendingAreas.get(input)
        : Promise.resolve();
    },
  };

  /* =========================
     GEOLOKALIZACJA
     ========================= */

  function getBrowserPosition() {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error("Ta przeglądarka nie obsługuje geolokalizacji."));
        return;
      }

      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 5 * 60 * 1000,
      });
    });
  }

  function geolocationErrorMessage(error) {
    if (error && error.code === 1) {
      return "Brak zgody na lokalizację. Zezwól na nią w ustawieniach przeglądarki albo wpisz miasto ręcznie.";
    }

    if (error && error.code === 3) {
      return "Ustalanie lokalizacji trwa zbyt długo. Spróbuj ponownie.";
    }

    if (error && error.code === 2) {
      return "Nie udało się ustalić Twojej lokalizacji.";
    }

    return (
      (error && error.message) || "Nie udało się ustalić Twojej lokalizacji."
    );
  }

  // Zamienia współrzędne na nazwę miejscowości (Geocoding API).
  async function reverseGeocodeCity(coords) {
    const { geocoding } = await loadLibraries();

    const geocoder = new geocoding.Geocoder();

    let results = [];

    try {
      const response = await geocoder.geocode({
        location: coords,
        language: LANGUAGE,
      });

      results = response.results || [];
    } catch (error) {
      // Miejsce bez danych (np. morze, pustkowie) - to nie jest błąd klucza
      // ani sieci, tylko brak miejscowości w tym punkcie.
      const code = String((error && (error.code || error.message)) || "");

      if (code.includes("ZERO_RESULTS")) {
        return null;
      }

      throw error;
    }

    const preferredTypes = [
      "locality",
      "postal_town",
      "administrative_area_level_3",
      "administrative_area_level_2",
    ];

    // Nazwę i obszar bierzemy z TEGO SAMEGO wyniku, który sam jest miejscowością
    // (a nie z adresu ulicy) - dzięki temu nie zapisujemy dokładnego punktu,
    // a przy kliknięciu poza miastem (wieś, pole) wybierana jest gmina/powiat
    // zamiast braku wyniku.
    for (const type of preferredTypes) {
      for (const result of results) {
        if (!(result.types || []).includes(type) || !result.geometry) {
          continue;
        }

        const component = (result.address_components || []).find((item) =>
          item.types.includes(type),
        );

        const area = areaFromGeometry(result.geometry);

        if (component && area) {
          return { name: component.long_name, area };
        }
      }
    }

    return null;
  }

  /* =========================
     PODPOWIEDZI MIEJSCOWOŚCI
     ========================= */

  async function fetchCitySuggestions(text, state) {
    const { places } = await loadLibraries();

    if (!state.sessionToken) {
      state.sessionToken = new places.AutocompleteSessionToken();
    }

    const request = {
      input: text,
      sessionToken: state.sessionToken,
      language: LANGUAGE,
      includedRegionCodes: COUNTRY_CODES,
    };

    if (PLACE_TYPES.length > 0) {
      request.includedPrimaryTypes = PLACE_TYPES;
    }

    // Jeśli znamy lokalizację użytkownika - wyniki w pobliżu są wyżej.
    if (userCoords) {
      request.locationBias = {
        center: userCoords,
        radius: BIAS_RADIUS,
      };
    }

    const { suggestions } =
      await places.AutocompleteSuggestion.fetchAutocompleteSuggestions(request);

    return (suggestions || [])
      .map((suggestion) => suggestion.placePrediction)
      .filter(Boolean)
      .slice(0, MAX_SUGGESTIONS)
      .map((prediction) => ({
        main:
          (prediction.mainText && prediction.mainText.text) ||
          prediction.text.text,
        secondary:
          (prediction.secondaryText && prediction.secondaryText.text) || "",
        prediction,
      }));
  }

  /* =========================
     POLE Z PODPOWIEDZIAMI
     ========================= */

  let pickerCounter = 0;

  function enhanceInput(input) {
    pickerCounter += 1;

    const listId = `location-picker-list-${pickerCounter}`;

    const state = {
      rows: [],
      activeIndex: -1,
      sessionToken: null,
      debounceTimer: null,
      requestId: 0,
      busy: false,
      suppressInput: false,
    };

    const wrapper = document.createElement("div");

    wrapper.className = "location-picker";

    input.parentNode.insertBefore(wrapper, input);

    wrapper.appendChild(input);

    input.classList.add("location-picker-input");

    input.setAttribute("autocomplete", "off");
    input.setAttribute("role", "combobox");
    input.setAttribute("aria-autocomplete", "list");
    input.setAttribute("aria-expanded", "false");
    input.setAttribute("aria-controls", listId);

    const locateButton = document.createElement("button");

    locateButton.type = "button";
    locateButton.className = "location-picker-locate";
    locateButton.setAttribute("aria-label", "Użyj mojej lokalizacji");
    locateButton.title = "Użyj mojej lokalizacji";
    locateButton.innerHTML = ICONS.locate;

    const list = document.createElement("div");

    list.className = "location-picker-list";
    list.id = listId;
    list.setAttribute("role", "listbox");
    list.hidden = true;

    wrapper.append(locateButton, list);

    /* ---------- pomocnicze ---------- */

    function setValue(value) {
      input.value = value;

      // Wpisanie wartości przez kod nie ma uruchamiać kolejnego wyszukiwania
      // (to byłoby zbędne, płatne zapytanie do Google).
      state.suppressInput = true;

      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));

      state.suppressInput = false;
    }

    function closeList() {
      list.hidden = true;
      list.innerHTML = "";

      state.rows = [];
      state.activeIndex = -1;

      input.setAttribute("aria-expanded", "false");
      input.removeAttribute("aria-activedescendant");
    }

    function setActive(index) {
      const options = list.querySelectorAll('[role="option"]');

      state.activeIndex = index;

      options.forEach((option, optionIndex) => {
        const isActive = optionIndex === index;

        option.classList.toggle("is-active", isActive);
        option.setAttribute("aria-selected", isActive ? "true" : "false");

        if (isActive) {
          input.setAttribute("aria-activedescendant", option.id);

          option.scrollIntoView({ block: "nearest" });
        }
      });
    }

    function renderList(rows, statusText, showAttribution) {
      state.rows = rows;
      state.activeIndex = -1;

      list.innerHTML = "";

      rows.forEach((row, index) => {
        const option = document.createElement("div");

        option.className = `location-picker-option is-${row.type}`;
        option.id = `${listId}-option-${index}`;
        option.setAttribute("role", "option");
        option.setAttribute("aria-selected", "false");

        if (row.type === "locate") {
          option.innerHTML = `${ICONS.locate}<span class="location-picker-main">Użyj mojej lokalizacji</span>`;
        } else {
          const text = document.createElement("span");

          text.className = "location-picker-text";

          const main = document.createElement("span");

          main.className = "location-picker-main";
          main.textContent = row.main;

          text.appendChild(main);

          if (row.secondary) {
            const secondary = document.createElement("span");

            secondary.className = "location-picker-secondary";
            secondary.textContent = row.secondary;

            text.appendChild(secondary);
          }

          option.innerHTML = ICONS.pin;

          option.appendChild(text);
        }

        // mousedown + preventDefault: pole nie traci fokusu przed kliknięciem.
        option.addEventListener("mousedown", (event) => {
          event.preventDefault();
        });

        option.addEventListener("click", () => selectRow(index));

        option.addEventListener("mousemove", () => {
          if (state.activeIndex !== index) {
            setActive(index);
          }
        });

        list.appendChild(option);
      });

      if (statusText) {
        const status = document.createElement("div");

        status.className = "location-picker-status";
        status.setAttribute("role", "status");
        status.textContent = statusText;

        list.appendChild(status);
      }

      if (showAttribution) {
        const attribution = document.createElement("div");

        attribution.className = "location-picker-attribution";
        attribution.textContent = "Powered by Google";

        list.appendChild(attribution);
      }

      list.hidden = false;

      input.setAttribute("aria-expanded", "true");
      input.removeAttribute("aria-activedescendant");
    }

    function selectRow(index) {
      const row = state.rows[index];

      if (!row) {
        return;
      }

      if (row.type === "locate") {
        useMyLocation();

        return;
      }

      setValue(row.main);

      // Obszar miejscowości (środek + promień) pobieramy w tle; formularz
      // czeka na to przy wysyłaniu (JollyCartLocationPicker.whenReady).
      clearArea(input);

      trackArea(
        input,
        fetchAreaForPrediction(row.prediction).then((area) =>
          setArea(input, row.main, area),
        ),
      );

      // Po wybraniu miejsca kończy się "sesja" Google - następna dostanie nowy token.
      state.sessionToken = null;

      closeList();
    }

    function emptyRows() {
      return navigator.geolocation ? [{ type: "locate" }] : [];
    }

    /* ---------- wyszukiwanie ---------- */

    async function search(text) {
      const requestId = ++state.requestId;

      try {
        const rows = (await fetchCitySuggestions(text, state)).map((item) => ({
          type: "place",
          ...item,
        }));

        // Odpowiedź przyszła spóźniona (użytkownik już wpisał coś innego).
        if (requestId !== state.requestId || document.activeElement !== input) {
          return;
        }

        if (rows.length === 0) {
          renderList([], "Nie znaleziono pasujących miejscowości.", true);
        } else {
          renderList(rows, "", true);
        }
      } catch (error) {
        console.error("Błąd podpowiedzi lokalizacji:", error);

        if (requestId === state.requestId) {
          renderList(
            [],
            authFailed
              ? "Klucz Google Maps jest nieprawidłowy lub zablokowany. Wpisz miasto ręcznie."
              : "Podpowiedzi są chwilowo niedostępne. Wpisz miasto ręcznie.",
            false,
          );
        }
      }
    }

    function handleInput() {
      if (state.suppressInput) {
        return;
      }

      clearTimeout(state.debounceTimer);

      const text = input.value.trim();

      if (text.length < MIN_CHARS) {
        state.requestId += 1;

        const rows = emptyRows();

        if (rows.length && document.activeElement === input) {
          renderList(rows, "", false);
        } else {
          closeList();
        }

        return;
      }

      state.debounceTimer = setTimeout(() => search(text), DEBOUNCE_MS);
    }

    /* ---------- "użyj mojej lokalizacji" ---------- */

    async function useMyLocation() {
      if (state.busy) {
        return;
      }

      state.busy = true;

      locateButton.classList.add("is-loading");
      locateButton.disabled = true;

      state.requestId += 1;

      renderList([], "Ustalam Twoją lokalizację…", false);

      try {
        const position = await getBrowserPosition();

        userCoords = {
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        };

        const place = await reverseGeocodeCity(userCoords);

        if (!place) {
          renderList(
            [],
            "Nie udało się rozpoznać miejscowości. Wpisz ją ręcznie.",
            false,
          );

          return;
        }

        setValue(place.name);

        setArea(input, place.name, place.area);

        state.sessionToken = null;

        closeList();
      } catch (error) {
        console.error("Błąd ustalania lokalizacji:", error);

        let message;

        if (error && typeof error.code === "number") {
          // Błąd geolokalizacji przeglądarki (zgoda, timeout, brak pozycji).
          message = geolocationErrorMessage(error);
        } else if (authFailed) {
          message = "Klucz Google Maps jest nieprawidłowy lub zablokowany.";
        } else {
          message = "Nie udało się ustalić miejscowości. Wpisz ją ręcznie.";
        }

        renderList([], message, false);
      } finally {
        state.busy = false;

        locateButton.classList.remove("is-loading");
        locateButton.disabled = false;
      }
    }

    /* ---------- panel z mapą (kliknięcie wybiera miejscowość) ---------- */

    function setupMapPanel() {
      const field = input.closest(".post-ad-field");

      // Mapa potrzebuje szerokości - pole lokalizacji zajmuje cały wiersz.
      if (field) {
        field.classList.add("post-ad-field-full");
      }

      const panel = document.createElement("div");

      panel.className = "location-map";

      const stage = document.createElement("div");

      stage.className = "location-map-stage";

      const status = document.createElement("div");

      status.className = "location-map-status";
      status.setAttribute("role", "status");
      status.textContent =
        "Możesz też kliknąć na mapie, aby wybrać miejscowość.";

      panel.append(stage, status);

      wrapper.insertAdjacentElement("afterend", panel);

      const DEFAULT_STATUS = status.textContent;

      function setStatus(text, isError = false) {
        status.textContent = text;
        status.classList.toggle("is-error", isError);
      }

      let map = null;
      let circle = null;
      let CircleClass = null;
      let mapRequestId = 0;

      // Obszar zapisany na polu jest ważny tylko dopóki tekst się zgadza.
      function readArea() {
        const data = input.dataset;

        if (
          !data.placeFor ||
          data.placeFor !== input.value.trim() ||
          data.placeLat === undefined ||
          data.placeLng === undefined
        ) {
          return null;
        }

        const lat = Number(data.placeLat);
        const lng = Number(data.placeLng);
        const radius = Number(data.placeRadius);

        if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
          return null;
        }

        return {
          lat,
          lng,
          radius:
            Number.isFinite(radius) && radius > 0
              ? radius
              : DEFAULT_AREA_RADIUS,
        };
      }

      // Rysuje na mapie obszar wybranej miejscowości (z podpowiedzi,
      // z geolokalizacji, z kliknięcia albo z zapisanego ogłoszenia).
      function render() {
        if (!map || !CircleClass) {
          return;
        }

        const area = readArea();

        if (!area) {
          if (circle) {
            circle.setMap(null);
            circle = null;
          }

          return;
        }

        const center = { lat: area.lat, lng: area.lng };

        if (!circle) {
          circle = new CircleClass({
            map,
            center,
            radius: area.radius,
            strokeColor: "#0e5c53",
            strokeOpacity: 0.9,
            strokeWeight: 2,
            fillColor: "#0e5c53",
            fillOpacity: 0.16,
            clickable: false,
          });
        } else {
          circle.setOptions({ center, radius: area.radius });
        }

        map.fitBounds(circle.getBounds(), 32);
      }

      async function handleMapClick(event) {
        if (!event.latLng) {
          return;
        }

        const requestId = ++mapRequestId;

        const coords = {
          lat: event.latLng.lat(),
          lng: event.latLng.lng(),
        };

        setStatus("Ustalam miejscowość…");
        panel.classList.add("is-loading");

        try {
          const place = await reverseGeocodeCity(coords);

          // Użytkownik kliknął już gdzie indziej.
          if (requestId !== mapRequestId) {
            return;
          }

          if (!place || !place.area) {
            setStatus(
              "W tym miejscu nie znaleziono miejscowości. Kliknij bliżej miasta lub wsi albo wpisz ją ręcznie.",
              true,
            );

            return;
          }

          setValue(place.name);
          setArea(input, place.name, place.area);

          state.sessionToken = null;

          closeList();

          setStatus(`Wybrano: ${place.name}. ${DEFAULT_STATUS}`);
        } catch (error) {
          console.error("Błąd wyboru lokalizacji z mapy:", error);

          if (requestId === mapRequestId) {
            setStatus(
              authFailed
                ? "Klucz Google Maps jest nieprawidłowy lub zablokowany."
                : "Nie udało się ustalić miejscowości (błąd Google Maps). Spróbuj ponownie lub wpisz ją ręcznie.",
              true,
            );
          }
        } finally {
          if (requestId === mapRequestId) {
            panel.classList.remove("is-loading");
          }
        }
      }

      loadGoogleMaps()
        .then(async (maps) => {
          const { Map: GoogleMap, Circle } = await maps.importLibrary("maps");

          CircleClass = Circle;

          map = new GoogleMap(stage, {
            // Środek Polski, dopóki nic nie wybrano.
            center: { lat: 52.07, lng: 19.48 },
            zoom: 6,
            // Na telefonie jeden palec przewija stronę, a nie mapę.
            gestureHandling: "cooperative",
            mapTypeControl: false,
            streetViewControl: false,
            clickableIcons: false,
          });

          map.addListener("click", handleMapClick);

          render();
        })
        .catch((error) => {
          console.error(
            "Nie udało się załadować mapy wyboru lokalizacji:",
            error,
          );

          // Bez mapy pole działa dalej jak zwykłe pole z podpowiedziami.
          panel.hidden = true;
        });

      // Każda zmiana obszaru na polu (też z edit-offer.js) odświeża mapę.
      new MutationObserver(render).observe(input, {
        attributes: true,
        attributeFilter: [
          "data-place-for",
          "data-place-lat",
          "data-place-lng",
          "data-place-radius",
        ],
      });

      // Ręczna zmiana tekstu unieważnia obszar - zdejmujemy koło z mapy.
      input.addEventListener("input", render);
    }

    /* ---------- zdarzenia ---------- */

    input.addEventListener("input", handleInput);

    // Wczytujemy Google dopiero, gdy ktoś zacznie używać pola.
    input.addEventListener("focus", () => {
      loadLibraries().catch(() => {});

      if (input.value.trim().length < MIN_CHARS) {
        const rows = emptyRows();

        if (rows.length) {
          renderList(rows, "", false);
        }
      }
    });

    input.addEventListener("click", () => {
      if (list.hidden && input.value.trim().length < MIN_CHARS) {
        const rows = emptyRows();

        if (rows.length) {
          renderList(rows, "", false);
        }
      }
    });

    input.addEventListener("keydown", (event) => {
      const optionCount = state.rows.length;

      if (event.key === "ArrowDown" && !list.hidden && optionCount) {
        event.preventDefault();

        setActive((state.activeIndex + 1) % optionCount);
      } else if (event.key === "ArrowUp" && !list.hidden && optionCount) {
        event.preventDefault();

        setActive((state.activeIndex - 1 + optionCount) % optionCount);
      } else if (
        event.key === "Enter" &&
        !list.hidden &&
        state.activeIndex >= 0
      ) {
        // Enter wybiera podpowiedź, a nie wysyła formularza.
        event.preventDefault();

        selectRow(state.activeIndex);
      } else if (event.key === "Escape" && !list.hidden) {
        event.preventDefault();

        closeList();
      }
    });

    locateButton.addEventListener("click", () => {
      input.focus();

      useMyLocation();
    });

    wrapper.addEventListener("focusout", (event) => {
      if (!wrapper.contains(event.relatedTarget)) {
        clearTimeout(state.debounceTimer);

        state.requestId += 1;

        closeList();
      }
    });

    setupMapPanel();
  }

  function init() {
    const inputs = document.querySelectorAll("input[data-location-picker]");

    if (!inputs.length) {
      return;
    }

    if (!hasApiKey) {
      console.warn(
        "[JollyCart] Brak klucza Google Maps w scripts/location/maps-config.js - pola lokalizacji działają jako zwykłe pola tekstowe.",
      );

      return;
    }

    inputs.forEach(enhanceInput);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
