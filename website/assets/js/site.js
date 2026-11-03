// Liftr – liftr.nu
const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;

// Nav turns solid after scrolling.
const nav = document.getElementById("nav");
const onScroll = () => nav && nav.classList.toggle("is-solid", scrollY > 30);
addEventListener("scroll", onScroll, { passive: true });
onScroll();

// Fade sections in as they appear.
const revealIO = new IntersectionObserver((entries) => {
  for (const e of entries) if (e.isIntersecting) { e.target.classList.add("is-in"); revealIO.unobserve(e.target); }
}, { threshold: 0.12 });
document.querySelectorAll("[data-reveal]").forEach((el) => revealIO.observe(el));

// Hero phone follows the pointer a little.
const phone = document.getElementById("heroPhone");
if (phone && matchMedia("(pointer: fine)").matches && !reduced) {
  addEventListener("pointermove", (e) => {
    const x = e.clientX / innerWidth - 0.5, y = e.clientY / innerHeight - 0.5;
    phone.style.transform = `rotateY(${-14 + x * 12}deg) rotateX(${6 - y * 10}deg) rotateZ(2deg)`;
  });
}

// Floating notifications next to the hero phone, one after another.
const notesBox = document.getElementById("notes");
const notes = window.LIFTR_NOTES || [];
if (notesBox && notes.length) {
  let i = 0;
  const show = () => {
    const n = notes[i % notes.length];
    const el = document.createElement("div");
    el.className = "note";
    el.innerHTML = `<img src="${n.icon}" alt=""><div><b>${n.title}</b><small>${n.body}</small></div>`;
    notesBox.prepend(el);
    while (notesBox.children.length > 2) {
      const old = notesBox.lastElementChild;
      old.classList.add("out");
      setTimeout(() => old.remove(), 450);
      break;
    }
    i++;
  };
  show();
  if (!reduced) setInterval(show, 3200);
}

// Feature scrolly: swap the phone screen with the active step.
const steps = document.querySelectorAll(".step");
const screens = document.querySelectorAll("#featureGlass .scr");
const device = document.querySelector(".scrolly-device");
const stepIO = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    const key = e.target.dataset.step;
    steps.forEach((s) => s.classList.toggle("is-active", s === e.target));
    screens.forEach((s) => s.classList.toggle("is-on", s.dataset.screen === key));
    device && device.style.setProperty("--glow", e.target.dataset.glow || "rgba(255,107,0,.45)");
  }
}, { rootMargin: "-45% 0px -45% 0px" });
steps.forEach((s) => stepIO.observe(s));

// Count the hero stats up once.
document.querySelectorAll("[data-count]").forEach((el) => {
  const target = Number(el.dataset.count), suffix = el.dataset.suffix || "";
  if (reduced) { el.textContent = target + suffix; return; }
  const start = performance.now(), dur = 1400;
  const tick = (t) => {
    const p = Math.min(1, (t - start) / dur), eased = 1 - Math.pow(1 - p, 3);
    el.textContent = Math.round(target * eased) + suffix;
    if (p < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
});

// Current year in the footer.
document.querySelectorAll("[data-year]").forEach((el) => (el.textContent = new Date().getFullYear()));
