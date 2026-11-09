/*
 * Klikalne ikony / nicki użytkowników.
 * Każdy element z atrybutem data-profile-id="<id>" otwiera profil
 * użytkownika (profile.html?id=<id>) - w czacie, rozmowach i ogłoszeniach.
 * Działa też wewnątrz innych linków (np. kafelek ogłoszenia).
 */
(function () {
  function profileUrl(id) {
    return `profile.html?id=${encodeURIComponent(id)}`;
  }

  function findTarget(event) {
    return event.target.closest ? event.target.closest("[data-profile-id]") : null;
  }

  document.addEventListener(
    "click",
    (event) => {
      const el = findTarget(event);
      if (!el) return;

      const id = el.getAttribute("data-profile-id");
      if (!id) return;

      event.preventDefault();
      event.stopPropagation();
      window.location.href = profileUrl(id);
    },
    true,
  );

  document.addEventListener("keydown", (event) => {
    if (event.key !== "Enter" && event.key !== " ") return;
    const el = findTarget(event);
    if (!el || el.tagName === "A" || el.tagName === "BUTTON") return;

    event.preventDefault();
    window.location.href = profileUrl(el.getAttribute("data-profile-id"));
  });
})();
