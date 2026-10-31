(() => {
  "use strict";
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const finePointer = window.matchMedia("(pointer: fine)").matches;
  const $ = (s, c) => (c || document).querySelector(s);
  const $$ = (s, c) => Array.from((c || document).querySelectorAll(s));
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const lerp = (a, b, t) => a + (b - a) * t;

  if (reduced) document.body.classList.add("reduced");

  /* ---------- preloader ---------- */
  const pre = $("#preloader");
  const preFill = $("#preloader-fill");
  const prePct = $("#preloader-pct");
  const finishLoad = () => {
    if (!pre || pre.classList.contains("done")) return;
    pre.classList.add("done");
    document.body.classList.add("loaded");
    setTimeout(() => pre.classList.add("gone"), 1400);
  };
  if (reduced || !pre) {
    document.body.classList.add("loaded");
  } else {
    let p = 0;
    const tick = () => {
      p = Math.min(p + Math.random() * 16 + 6, 100);
      preFill.style.width = p + "%";
      prePct.textContent = String(Math.floor(p)).padStart(2, "0");
      if (p < 100) setTimeout(tick, 90 + Math.random() * 110);
      else setTimeout(finishLoad, 260);
    };
    tick();
    setTimeout(finishLoad, 3500); // hard cap
  }

  /* ---------- reveal on scroll ---------- */
  const revealIO = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) {
        e.target.classList.add("in");
        revealIO.unobserve(e.target);
      }
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });
  $$("[data-reveal]").forEach((el) => revealIO.observe(el));
  const duo = $(".duo-main");
  if (duo) revealIO.observe(duo);

  /* ---------- nav + progress + rAF loop ---------- */
  const nav = $("#nav");
  const progressFill = $("#progress-fill");
  let scrollTarget = window.scrollY;
  let smooth = scrollTarget;
  let lastY = scrollTarget;
  let velocity = 0;
  let navHidden = false;

  window.addEventListener("scroll", () => { scrollTarget = window.scrollY; }, { passive: true });

  /* ---------- scrollytelling ---------- */
  const story = $(".story");
  const steps = $$(".step");
  const screens = $$(".pscreen");
  const dots = $$(".story-dots i");
  let activeStep = -1;
  const setStep = (i) => {
    if (i === activeStep) return;
    activeStep = i;
    screens.forEach((img, k) => img.classList.toggle("is-active", k === i));
    dots.forEach((d, k) => d.classList.toggle("on", k === i));
    steps.forEach((s, k) => s.classList.toggle("on", k === i));
  };

  /* ---------- horizontal strip ---------- */
  const strip = $(".strip");
  const stripTrack = $("#strip-track");
  const stripFill = $("#strip-fill");
  let stripTop = 0, stripRange = 0;
  const measureStrip = () => {
    if (!strip || !stripTrack) return;
    if (reduced) { strip.style.height = "auto"; return; }
    const gutter = parseFloat(getComputedStyle(stripTrack).paddingLeft) || 0;
    const shift = Math.max(stripTrack.scrollWidth - window.innerWidth + gutter * 2, 0);
    stripRange = shift;
    strip.style.height = (window.innerHeight + shift) + "px";
    const r = strip.getBoundingClientRect();
    stripTop = r.top + window.scrollY;
  };

  /* ---------- counters ---------- */
  const animateCount = (el) => {
    const target = parseFloat(el.dataset.count || "0");
    const decimals = parseInt(el.dataset.decimals || "0", 10);
    if (reduced) { el.textContent = target.toFixed(decimals); return; }
    const t0 = performance.now();
    const dur = 1600;
    const stepFn = (t) => {
      const p = clamp((t - t0) / dur, 0, 1);
      const e = 1 - Math.pow(2, -10 * p); // easeOutExpo
      el.textContent = (target * (p === 1 ? 1 : e)).toFixed(decimals);
      if (p < 1) requestAnimationFrame(stepFn);
    };
    requestAnimationFrame(stepFn);
  };
  const countIO = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) { animateCount(e.target); countIO.unobserve(e.target); }
    });
  }, { threshold: 0.6 });
  $$(".stat-num").forEach((el) => countIO.observe(el));

  /* ---------- main rAF loop ---------- */
  const frame = () => {
    smooth = reduced ? scrollTarget : lerp(smooth, scrollTarget, 0.14);
    velocity = lerp(velocity, Math.abs(scrollTarget - lastY), 0.2);
    lastY = scrollTarget;

    if (progressFill) {
      const max = document.documentElement.scrollHeight - window.innerHeight;
      progressFill.style.transform = `scaleX(${max > 0 ? clamp(smooth / max, 0, 1) : 0})`;
    }

    if (nav) {
      nav.classList.toggle("scrolled", smooth > 30);
      const goingDown = scrollTarget > lastY - 1 && scrollTarget > smooth + 2;
      const shouldHide = scrollTarget > 500 && scrollTarget - (frame._prevY || 0) > 4;
      const shouldShow = (frame._prevY || 0) - scrollTarget > 4 || scrollTarget < 500;
      if (shouldHide && !navHidden) { nav.classList.add("hidden"); navHidden = true; }
      else if (shouldShow && navHidden) { nav.classList.remove("hidden"); navHidden = false; }
      frame._prevY = scrollTarget;
    }

    // scrollytelling active step
    if (story && steps.length) {
      const mid = window.innerHeight * 0.5;
      let idx = 0;
      steps.forEach((s, k) => {
        const r = s.getBoundingClientRect();
        if (r.top <= mid) idx = k;
      });
      setStep(idx);
    }

    // strip translate
    if (strip && stripTrack && !reduced) {
      const p = stripRange > 0 ? clamp((smooth - stripTop) / stripRange, 0, 1) : 0;
      const skew = clamp(velocity * 0.05, 0, 5);
      stripTrack.style.transform = `translate3d(${-p * stripRange}px,0,0) skewX(${-skew}deg)`;
      if (stripFill) stripFill.style.transform = `scaleX(${p})`;
    }

    soundFrame();
    requestAnimationFrame(frame);
  };
  measureStrip();
  window.addEventListener("resize", measureStrip);
  window.addEventListener("load", measureStrip);
  requestAnimationFrame(frame);

  /* ---------- hero tilt ---------- */
  const stage = $("[data-tilt]");
  const tiltInner = $("[data-tilt-inner]");
  if (stage && tiltInner && finePointer && !reduced) {
    let tx = 0, ty = 0, cx = 0, cy = 0;
    stage.addEventListener("mousemove", (e) => {
      const r = stage.getBoundingClientRect();
      tx = ((e.clientX - r.left) / r.width - 0.5) * 2;
      ty = ((e.clientY - r.top) / r.height - 0.5) * 2;
    });
    stage.addEventListener("mouseleave", () => { tx = 0; ty = 0; });
    const tiltFrame = () => {
      cx = lerp(cx, tx, 0.08);
      cy = lerp(cy, ty, 0.08);
      tiltInner.style.transform = `rotateY(${cx * 7}deg) rotateX(${-cy * 5}deg)`;
      requestAnimationFrame(tiltFrame);
    };
    requestAnimationFrame(tiltFrame);
  }

  /* ---------- magnetic buttons ---------- */
  if (finePointer && !reduced) {
    $$(".magnetic").forEach((el) => {
      el.addEventListener("mousemove", (e) => {
        const r = el.getBoundingClientRect();
        const x = e.clientX - r.left - r.width / 2;
        const y = e.clientY - r.top - r.height / 2;
        el.style.transform = `translate(${x * 0.18}px, ${y * 0.28}px)`;
      });
      el.addEventListener("mouseleave", () => { el.style.transform = ""; });
    });
  }

  /* ---------- spotlight cards ---------- */
  if (finePointer) {
    $$(".tool-card").forEach((card) => {
      card.addEventListener("mousemove", (e) => {
        const r = card.getBoundingClientRect();
        card.style.setProperty("--mx", ((e.clientX - r.left) / r.width) * 100 + "%");
        card.style.setProperty("--my", ((e.clientY - r.top) / r.height) * 100 + "%");
      });
    });
  }

  /* ---------- copy hash ---------- */
  const copyBtn = $("#copy-hash");
  const hashEl = $("#hash-value");
  const copyStatus = $("#copy-status");
  if (copyBtn && hashEl) {
    copyBtn.addEventListener("click", async () => {
      const text = hashEl.textContent.trim();
      try {
        await navigator.clipboard.writeText(text);
        copyStatus.textContent = "Copied";
      } catch {
        const ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand("copy"); copyStatus.textContent = "Copied"; }
        catch { copyStatus.textContent = "Select and copy manually"; }
        ta.remove();
      }
      blip();
      setTimeout(() => { copyStatus.textContent = ""; }, 2400);
    });
  }

  /* ---------- sound engine (WebAudio, no assets) ---------- */
  const toggle = $("#sound-toggle");
  let actx = null, master = null, whooshGain = null, whooshFilter = null, soundOn = false;
  let velSmoothed = 0;

  const buildEngine = () => {
    actx = new (window.AudioContext || window.webkitAudioContext)();
    master = actx.createGain();
    master.gain.value = 0;
    master.connect(actx.destination);

    // low ambient drone: two detuned sines through a slow-breathing lowpass
    const droneFilter = actx.createBiquadFilter();
    droneFilter.type = "lowpass";
    droneFilter.frequency.value = 220;
    const droneGain = actx.createGain();
    droneGain.gain.value = 0.05;
    [54, 54.45, 108.2].forEach((f, i) => {
      const o = actx.createOscillator();
      o.type = i === 2 ? "triangle" : "sine";
      o.frequency.value = f;
      const g = actx.createGain();
      g.gain.value = i === 2 ? 0.24 : 0.5;
      o.connect(g); g.connect(droneFilter); o.start();
    });
    const lfo = actx.createOscillator();
    lfo.frequency.value = 0.07;
    const lfoGain = actx.createGain();
    lfoGain.gain.value = 70;
    lfo.connect(lfoGain); lfoGain.connect(droneFilter.frequency); lfo.start();
    droneFilter.connect(droneGain); droneGain.connect(master);

    // scroll whoosh: filtered noise, gain follows scroll velocity
    const len = actx.sampleRate * 2;
    const buf = actx.createBuffer(1, len, actx.sampleRate);
    const data = buf.getChannelData(0);
    for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1;
    const noise = actx.createBufferSource();
    noise.buffer = buf; noise.loop = true;
    whooshFilter = actx.createBiquadFilter();
    whooshFilter.type = "bandpass";
    whooshFilter.frequency.value = 400;
    whooshFilter.Q.value = 1.1;
    whooshGain = actx.createGain();
    whooshGain.gain.value = 0;
    noise.connect(whooshFilter); whooshFilter.connect(whooshGain); whooshGain.connect(master);
    noise.start();
  };

  function soundFrame() {
    if (!soundOn || !whooshGain) return;
    velSmoothed = lerp(velSmoothed, velocity, 0.12);
    const g = clamp(velSmoothed / 110, 0, 1) * 0.12;
    whooshGain.gain.setTargetAtTime(g, actx.currentTime, 0.09);
    whooshFilter.frequency.setTargetAtTime(300 + clamp(velSmoothed, 0, 400) * 3.2, actx.currentTime, 0.12);
  }

  function blip() {
    if (!soundOn || !actx) return;
    const o = actx.createOscillator();
    const g = actx.createGain();
    o.type = "triangle";
    o.frequency.setValueAtTime(660, actx.currentTime);
    o.frequency.exponentialRampToValueAtTime(440, actx.currentTime + 0.08);
    g.gain.setValueAtTime(0.06, actx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.0001, actx.currentTime + 0.12);
    o.connect(g); g.connect(master);
    o.start(); o.stop(actx.currentTime + 0.14);
  }

  if (toggle) {
    toggle.addEventListener("click", async () => {
      if (!actx) buildEngine();
      if (!soundOn) {
        await actx.resume();
        master.gain.setTargetAtTime(0.9, actx.currentTime, 0.4);
        soundOn = true;
        toggle.setAttribute("aria-pressed", "true");
        toggle.setAttribute("aria-label", "Turn ambient sound off");
      } else {
        master.gain.setTargetAtTime(0, actx.currentTime, 0.2);
        soundOn = false;
        toggle.setAttribute("aria-pressed", "false");
        toggle.setAttribute("aria-label", "Turn ambient sound on");
        setTimeout(() => { if (!soundOn && actx) actx.suspend(); }, 600);
      }
      try { localStorage.setItem("ztv-sound", soundOn ? "1" : "0"); } catch {}
    });
  }

  // soft tick on primary buttons when sound is on
  $$(".btn, .nav-cta").forEach((el) => el.addEventListener("pointerdown", blip));
})();
