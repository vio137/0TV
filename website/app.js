'use strict';
document.documentElement.classList.add('js');
const reduced = matchMedia('(prefers-reduced-motion: reduce)');

// Gentle section reveals; everything stays visible with reduced motion or when paused.
const motionToggle = document.getElementById('motion-toggle');
let paused = reduced.matches;
function applyMotion() {
  document.body.classList.toggle('motion-paused', paused);
  if (motionToggle) { motionToggle.setAttribute('aria-pressed', String(paused)); motionToggle.textContent = paused ? 'Resume motion' : 'Pause motion'; }
}
if (motionToggle) motionToggle.addEventListener('click', () => { paused = !paused; applyMotion(); });
applyMotion();

const observer = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('visible'); observer.unobserve(e.target); } }), { threshold: .07 });
document.querySelectorAll('.reveal').forEach(e => observer.observe(e));

// Tap a screenshot to enlarge it.
const box = document.getElementById('lightbox');
const boxImg = box ? box.querySelector('img') : null;
document.querySelectorAll('.shot img').forEach(img => {
  img.style.cursor = 'zoom-in';
  img.addEventListener('click', () => {
    if (!box) return;
    boxImg.src = img.src; boxImg.alt = img.alt;
    box.showModal();
  });
});
if (box) {
  box.querySelector('.close-lightbox').addEventListener('click', () => box.close());
  box.addEventListener('click', e => { if (e.target === box) box.close(); });
}

// Tap the SHA-256 to copy it.
const hashBtn = document.getElementById('copy-hash');
const hashStatus = document.getElementById('copy-status');
if (hashBtn) hashBtn.addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(hashBtn.textContent.trim()); if (hashStatus) hashStatus.textContent = 'Copied.'; }
  catch { if (hashStatus) hashStatus.textContent = 'Copy failed - select the text instead.'; }
  setTimeout(() => { if (hashStatus) hashStatus.textContent = ''; }, 2500);
});
