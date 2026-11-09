/*
 * MAPA LOKALIZACJI NA STRONIE OGŁOSZENIA
 *
 * Karta .od-map (renderowana w offer-details.js) ma przycisk "Pokaż na mapie".
 * Google Maps ładujemy DOPIERO po kliknięciu, więc samo otwarcie ogłoszenia
 * nie generuje płatnego załadowania mapy.
 *
 * Na mapie zaznaczamy orientacyjny OBSZAR (koło), a nie dokładny punkt -
 * kupujący ma zobaczyć, o którą miejscowość chodzi, ale nie adres ogłaszającego.
 *
 * Bez klucza w maps-config.js zostaje tylko link "Otwórz w Google Maps".
 */
(function () {
  "use strict";

  const config = window.JOLLYCART_MAPS || {};

  const API_KEY = String(config.apiKey || "");
  const LANGUAGE = config.language || "pl";

  const hasApiKey = API_KEY.length > 10 && !API_KEY.startsWith("WKLEJ");

  const AREA_COLOR = "#0e5c53";

  let mapsPromise = null;

  function loadGoogleMaps() {
    if (mapsPromise) {
      return mapsPromise;
    }

    mapsPromise = new Promise((resolve, reject) => {
      if (window.google && window.google.maps && window.google.maps.importLibrary) {
        resolve(window.google.maps);

        return;
      }

      const callbackName = "__jollycartOfferMapReady";

      window[callbackName] = () => {
        delete window[callbackName];

        resolve(window.google.maps);
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

  async function showMap(card, stage, area) {
    const maps = await loadGoogleMaps();

    const { Map: GoogleMap, Circle } = await maps.importLibrary("maps");

    stage.innerHTML = "";
    stage.classList.add("is-live");

    const center = { lat: area.lat, lng: area.lng };

    const map = new GoogleMap(stage, {
      center,
      zoom: 11,
      // Na telefonie jeden palec przewija stronę, a nie mapę.
      gestureHandling: "cooperative",
      mapTypeControl: false,
      streetViewControl: false,
      clickableIcons: false,
    });

    const circle = new Circle({
      map,
      center,
      radius: area.radius,
      strokeColor: AREA_COLOR,
      strokeOpacity: 0.9,
      strokeWeight: 2,
      fillColor: AREA_COLOR,
      fillOpacity: 0.16,
      clickable: false,
    });

    map.fitBounds(circle.getBounds(), 32);
  }

  function readArea(card) {
    const lat = Number(card.dataset.lat);
    const lng = Number(card.dataset.lng);
    const radius = Number(card.dataset.radius);

    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return null;
    }

    return {
      lat,
      lng,
      radius: Number.isFinite(radius) && radius > 0 ? radius : 2000,
    };
  }

  function setup(root) {
    const card = root.querySelector(".od-map");

    if (!card) {
      return;
    }

    const button = card.querySelector(".od-map-button");
    const stage = card.querySelector(".od-map-stage");
    const status = card.querySelector(".od-map-status");

    if (!button || !stage) {
      return;
    }

    // Bez klucza nie da się pokazać mapy - zostaje sam link do Google Maps.
    if (!hasApiKey) {
      stage.hidden = true;

      return;
    }

    const area = readArea(card);

    if (!area) {
      stage.hidden = true;

      return;
    }

    button.addEventListener("click", async () => {
      button.disabled = true;
      button.textContent = "Ładowanie mapy…";

      if (status) {
        status.hidden = true;
      }

      try {
        await showMap(card, stage, area);
      } catch (error) {
        console.error("Błąd ładowania mapy:", error);

        button.disabled = false;
        button.textContent = "Spróbuj ponownie";

        if (status) {
          status.textContent =
            "Nie udało się załadować mapy. Skorzystaj z linku poniżej.";
          status.hidden = false;
        }
      }
    });
  }

  window.JollyCartOfferMap = { setup };
})();
