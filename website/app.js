'use strict';
// Tap a screenshot to enlarge it.
const box = document.getElementById('lightbox');
let boxImg = null;
document.querySelectorAll('.shot').forEach(img => {
  if (img.tagName !== 'IMG') return;
  img.style.cursor = 'zoom-in';
  img.addEventListener('click', () => {
    if (!box) return;
    if (!boxImg) { boxImg = document.createElement('img'); box.insertBefore(boxImg, box.querySelector('p')); }
    boxImg.src = img.src; boxImg.alt = img.alt;
    box.showModal();
  });
});
if (box) {
  box.querySelector('.close-lightbox').addEventListener('click', () => box.close());
  box.addEventListener('click', e => { if (e.target === box) box.close(); });
}

// Copy the SHA-256.
const hashBtn = document.getElementById('copy-hash');
const hashStatus = document.getElementById('copy-status');
const hashValue = document.getElementById('hash-value');
if (hashBtn && hashValue) hashBtn.addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(hashValue.textContent.trim()); if (hashStatus) hashStatus.textContent = 'Copied.'; }
  catch { if (hashStatus) hashStatus.textContent = 'Copy failed; select the text instead.'; }
  setTimeout(() => { if (hashStatus) hashStatus.textContent = ''; }, 2500);
});
