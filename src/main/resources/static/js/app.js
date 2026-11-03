(() => {
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];

  /* Rasm yuklanmasa — chiroyli zaxira blok */
  document.addEventListener("error", (e) => {
    const img = e.target;
    if (img.tagName !== "IMG" || img.dataset.failed) return;
    img.dataset.failed = "1";
    if ("nofallback" in img.dataset) { img.style.display = "none"; return; }
    const box = document.createElement("div");
    box.className = "img-fallback";
    box.textContent = img.dataset.fallback || "FAMILY";
    img.replaceWith(box);
  }, true);

  /* Tasdiqlash: <form data-confirm="..."> */
  document.addEventListener("submit", (e) => {
    const msg = e.target.dataset && e.target.dataset.confirm;
    if (msg && !confirm(msg)) e.preventDefault();
  });

  /* Mahsulot galereyasi */
  const main = $("#mainImage");
  $$(".thumb-btn").forEach((b) => b.addEventListener("click", () => {
    if (main) main.src = b.dataset.src;
    $$(".thumb-btn").forEach((x) => x.classList.toggle("active", x === b));
  }));

  /* Admin: yuklanayotgan rasmni oldindan ko'rish */
  const imageInput = $("#imageInput");
  const preview = $("#preview");
  if (imageInput && preview) {
    imageInput.addEventListener("change", () => {
      const f = imageInput.files[0];
      if (f) { preview.src = URL.createObjectURL(f); preview.style.display = "block"; }
    });
  }

  /* Admin: sayt dizayni — tanlangan rasmni darhol ko'rsatish */
  $$("input[type=file][data-preview]").forEach((input) => {
    input.addEventListener("change", () => {
      const f = input.files[0];
      const box = input.parentElement.querySelector(".design-image__preview");
      if (!f || !box) return;
      box.innerHTML = "";
      const img = document.createElement("img");
      img.src = URL.createObjectURL(f);
      box.appendChild(img);
    });
  });

  /* Mobil menyu */
  const toggle = $("#menuToggle");
  const nav = $("#nav");
  if (toggle && nav) {
    toggle.addEventListener("click", () => nav.classList.toggle("open"));
    $$("a", nav).forEach((a) => a.addEventListener("click", () => nav.classList.remove("open")));
  }

  /* Miqdor tanlagich (mahsulot sahifasi) */
  $$("[data-qty]").forEach((box) => {
    const input = $("input", box);
    $$("[data-step]", box).forEach((b) => b.addEventListener("click", () => {
      const v = Math.min(+input.max || 20, Math.max(+input.min || 1, (+input.value || 1) + +b.dataset.step));
      input.value = v;
    }));
  });

  /* Toast (server flash xabari) */
  const toast = $("#toast");
  if (toast && toast.dataset.message) {
    toast.textContent = toast.dataset.message;
    requestAnimationFrame(() => toast.classList.add("show"));
    setTimeout(() => toast.classList.remove("show"), 2800);
  }

  /* Scroll animatsiya */
  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver((entries) => entries.forEach((en) => {
      if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); }
    }), { threshold: 0.12 });
    $$(".reveal").forEach((el) => io.observe(el));
  } else {
    $$(".reveal").forEach((el) => el.classList.add("in"));
  }
})();
