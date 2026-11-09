window.JOLLYCART_MAPS = {
  apiKey: "AIzaSyAQmTOrWQ21OmNhibp6UWoo0lAA66f4djU",

  language: "pl",

  // Kraje, w których szukamy miejscowości (kody ISO, małymi literami).
  countryCodes: ["pl"],

  // "(cities)" = tylko miejscowości. Pusta lista [] = wszystkie miejsca
  // (ulice, adresy, punkty).
  placeTypes: ["(cities)"],

  // Promień (w metrach) preferowania wyników blisko użytkownika.
  // Maksymalnie 50000 (limit Google).
  biasRadiusMeters: 50000,

  // Ile podpowiedzi pokazać.
  maxSuggestions: 5,
};
