/*
 * Oceny zalogowanego użytkownika wyświetlane pod jego avatarem
 * (strona "Edytuj swój profil"). Dane: GET /api/users/{id}/profile.
 */
(function () {
  const API =
    typeof API_BASE_URL !== "undefined" ? API_BASE_URL : "http://localhost:8080";

  function polishVotes(n) {
    if (n === 1) return "1 ocena";
    const last = n % 10;
    const lastTwo = n % 100;
    if (last >= 2 && last <= 4 && !(lastTwo >= 12 && lastTwo <= 14)) {
      return `${n} oceny`;
    }
    return `${n} ocen`;
  }

  function starsHtml(value) {
    let html = "";
    for (let i = 1; i <= 5; i++) {
      const fill = Math.max(0, Math.min(1, value - (i - 1))) * 100;
      html += `<span class="own-star" style="--fill:${fill}%">★</span>`;
    }
    return html;
  }

  function render(profile) {
    const count = profile.ratingCount || 0;
    const html = `
      <a class="own-rating-link" href="profile.html?id=${encodeURIComponent(profile.id)}"
         title="Zobacz, jak widzą Cię inni">
        <span class="own-stars" aria-hidden="true">${starsHtml(profile.averageRating || 0)}</span>
        <span class="own-rating-text">${
          count
            ? `<strong>${profile.averageRating.toFixed(1)}</strong> · ${polishVotes(count)}`
            : "Brak ocen"
        }</span>
      </a>`;

    document.querySelectorAll("[data-own-rating]").forEach((el) => {
      el.innerHTML = html;
      el.setAttribute(
        "aria-label",
        count
          ? `Średnia ocena ${profile.averageRating.toFixed(1)} na 5, ${polishVotes(count)}`
          : "Brak ocen",
      );
    });
  }

  async function load() {
    try {
      const me = await fetch(`${API}/api/users/me`, { credentials: "include" });
      if (!me.ok) return;
      const { id } = await me.json();

      const response = await fetch(`${API}/api/users/${encodeURIComponent(id)}/profile`, {
        credentials: "include",
      });
      if (!response.ok) return;

      render(await response.json());
    } catch {
      /* oceny są dodatkiem - brak błędu dla użytkownika */
    }
  }

  document.addEventListener("DOMContentLoaded", load);
})();
