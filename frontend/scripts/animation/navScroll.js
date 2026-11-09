(function () {
  const navbar = document.querySelector(".navbar");
  if (!navbar) return;

  // Od tylu px od góry strony navbar dostaje cień i gradientową linię
  const SCROLLED_AT = 12;

  // Do tylu px od góry strony navbar jest zawsze widoczny
  const ALWAYS_SHOW_BELOW = 30;

  // Ile px trzeba przescrollować w danym kierunku, żeby navbar zareagował
  // (chroni przed drganiem przy drobnych ruchach kółkiem/gładzikiem)
  const HIDE_AFTER = 10;
  const SHOW_AFTER = 6;

  let lastY = window.scrollY;
  let accumulated = 0;
  let ticking = false;

  function showNavbar() {
    navbar.classList.remove("nav-hidden");
  }

  function hideNavbar() {
    navbar.classList.add("nav-hidden");
  }

  function update() {
    ticking = false;

    const y = window.scrollY;
    const diff = y - lastY;
    lastY = y;

    navbar.classList.toggle("scrolled", y > SCROLLED_AT);

    if (y <= ALWAYS_SHOW_BELOW) {
      accumulated = 0;
      showNavbar();
      return;
    }

    if (diff === 0) return;

    // Zmiana kierunku zeruje licznik
    if (Math.sign(diff) !== Math.sign(accumulated)) {
      accumulated = 0;
    }

    accumulated += diff;

    if (accumulated > HIDE_AFTER) {
      hideNavbar();
    } else if (accumulated < -SHOW_AFTER) {
      showNavbar();
    }
  }

  window.addEventListener(
    "scroll",
    () => {
      if (!ticking) {
        ticking = true;
        window.requestAnimationFrame(update);
      }
    },
    { passive: true },
  );

  // Nawigacja klawiaturą (Tab) do elementu w navbarze ma go odsłonić
  navbar.addEventListener("focusin", showNavbar);

  update();
})();
