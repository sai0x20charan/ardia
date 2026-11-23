package com.ardia.browser

import java.util.Locale

internal object PageBottomInset {
    fun script(insetDp: Double, viewportHeightDp: Double, blurEnabled: Boolean): String {
        val inset = format(insetDp)
        val height = format(viewportHeightDp)
        val blur = if (blurEnabled) "true" else "false"
        return """
(() => {
  if (window !== window.top) return;
  const STYLE_ID = "__ardia_bottom_inset_style";
  const BLUR_ID = "__ardia_bottom_blur";
  const APRON_ID = "__ardia_bottom_apron";
  const state = window.__ardiaBottomInset || (window.__ardiaBottomInset = {
    insetDp: 0,
    viewHeightDp: 0,
    blurEnabled: false,
    appliedPadding: 0,
    hadBodyPadding: false,
    origBodyPadding: "",
    origBodyPaddingPriority: "",
    hadBodyBoxSizing: false,
    origBodyBoxSizing: "",
    origBodyBoxSizingPriority: "",
    changedBoxSizing: false,
    lifted: new Map(),
    timer: 0,
    raf: 0,
    listening: false,
  });

  const insetCss = () => {
    if (state.insetDp <= 0 || state.viewHeightDp <= 0) return 0;
    const inner = window.innerHeight;
    if (!inner || !isFinite(inner)) return Math.round(state.insetDp);
    const ratio = inner / state.viewHeightDp;
    const scaled = state.insetDp * (isFinite(ratio) && ratio > 0 ? ratio : 1);
    return Math.max(0, Math.min(Math.round(scaled), Math.round(inner * 0.4)));
  };

  const setMask = (el, gradient) => {
    el.style.setProperty("-webkit-mask-image", gradient, "important");
    el.style.setProperty("mask-image", gradient, "important");
  };

  const setBackdrop = (el, filter) => {
    el.style.setProperty("-webkit-backdrop-filter", filter, "important");
    el.style.setProperty("backdrop-filter", filter, "important");
  };

  const parseRgba = (color) => {
    if (!color || color === "transparent") return null;
    const match = /^rgba?\(([^)]+)\)$/i.exec(color.trim());
    if (!match) return null;
    const parts = match[1].split(/[,\s/]+/).filter(Boolean);
    if (parts.length < 3) return null;
    const r = parseFloat(parts[0]);
    const g = parseFloat(parts[1]);
    const b = parseFloat(parts[2]);
    const a = parts.length >= 4 ? parseFloat(parts[3]) : 1;
    if (!isFinite(r) || !isFinite(g) || !isFinite(b) || !isFinite(a)) return null;
    return { r: Math.round(r), g: Math.round(g), b: Math.round(b), a };
  };

  const findBarSurface = (el, minWidth) => {
    let current = el;
    for (let depth = 0; depth < 4 && current; depth++) {
      const style = getComputedStyle(current);
      const rgba = parseRgba(style.backgroundColor);
      const filter = style.backdropFilter && style.backdropFilter !== "none" ? style.backdropFilter : style.webkitBackdropFilter && style.webkitBackdropFilter !== "none" ? style.webkitBackdropFilter : "";
      if ((rgba && rgba.a >= 0.75) || (filter && rgba && rgba.a >= 0.3)) {
        return { backgroundColor: style.backgroundColor, backdropFilter: filter };
      }
      let next = null;
      for (let i = 0; i < current.children.length; i++) {
        const child = current.children[i];
        if (child.getBoundingClientRect().width >= minWidth) {
          next = child;
          break;
        }
      }
      current = next;
    }
    return null;
  };

  const ensureApron = (px, surface) => {
    const root = document.documentElement;
    if (!root) return;
    let apron = document.getElementById(APRON_ID);
    if (!surface || px <= 0) {
      if (apron) apron.remove();
      return;
    }
    if (!apron) {
      apron = document.createElement("div");
      apron.id = APRON_ID;
      apron.setAttribute("aria-hidden", "true");
      apron.style.setProperty("position", "fixed", "important");
      apron.style.setProperty("left", "0", "important");
      apron.style.setProperty("right", "0", "important");
      apron.style.setProperty("bottom", "0", "important");
      apron.style.setProperty("pointer-events", "none", "important");
      apron.style.setProperty("z-index", "2147483646", "important");
      apron.style.setProperty("transform", "translateZ(0)", "important");
      root.appendChild(apron);
    } else if (apron.parentNode !== root) {
      root.appendChild(apron);
    }
    apron.style.setProperty("height", (px + 1) + "px", "important");
    apron.style.setProperty("background-color", surface.backgroundColor, "important");
    if (surface.backdropFilter && state.blurEnabled) {
      setBackdrop(apron, surface.backdropFilter);
    } else {
      apron.style.removeProperty("-webkit-backdrop-filter");
      apron.style.removeProperty("backdrop-filter");
    }
  };

  const ensureBlur = (px, apronActive) => {
    const root = document.documentElement;
    if (!root) return;
    let host = document.getElementById(BLUR_ID);
    if (!state.blurEnabled || px <= 0 || apronActive) {
      if (host) host.remove();
      return;
    }
    const feather = Math.max(18, Math.round(px * 0.3));
    const totalHeight = px + feather;
    if (!host) {
      host = document.createElement("div");
      host.id = BLUR_ID;
      host.setAttribute("aria-hidden", "true");
      host.style.setProperty("position", "fixed", "important");
      host.style.setProperty("left", "0", "important");
      host.style.setProperty("right", "0", "important");
      host.style.setProperty("bottom", "0", "important");
      host.style.setProperty("pointer-events", "none", "important");
      host.style.setProperty("z-index", "2147483647", "important");
      host.style.setProperty("overflow", "hidden", "important");
      host.style.setProperty("transform", "translateZ(0)", "important");
      const layers = [
        { blur: "4px", mask: "linear-gradient(to bottom,transparent 0%,black 35%,black 100%)" },
        { blur: "10px", mask: "linear-gradient(to bottom,transparent 20%,black 65%,black 100%)" },
        { blur: "20px", mask: "linear-gradient(to bottom,transparent 42%,black 100%)" },
      ];
      for (const spec of layers) {
        const layer = document.createElement("div");
        layer.style.setProperty("position", "absolute", "important");
        layer.style.setProperty("inset", "0", "important");
        layer.style.setProperty("pointer-events", "none", "important");
        setBackdrop(layer, "blur(" + spec.blur + ")");
        setMask(layer, spec.mask);
        host.appendChild(layer);
      }
      root.appendChild(host);
    } else if (host.parentNode !== root) {
      root.appendChild(host);
    }
    host.style.setProperty("height", totalHeight + "px", "important");
  };

  const restoreBody = () => {
    const body = document.body;
    if (!body) return;
    if (state.appliedPadding > 0) {
      if (state.hadBodyPadding) body.style.setProperty("padding-bottom", state.origBodyPadding, state.origBodyPaddingPriority);
      else body.style.removeProperty("padding-bottom");
      state.appliedPadding = 0;
    }
    if (state.changedBoxSizing) {
      if (state.hadBodyBoxSizing) body.style.setProperty("box-sizing", state.origBodyBoxSizing, state.origBodyBoxSizingPriority);
      else body.style.removeProperty("box-sizing");
      state.changedBoxSizing = false;
    }
  };

  const applyBody = (px) => {
    const body = document.body;
    if (!body) return;
    if (px <= 0) {
      restoreBody();
      return;
    }
    const computed = getComputedStyle(body);
    if (computed.position === "fixed") {
      restoreBody();
      return;
    }
    if (state.appliedPadding === 0) {
      state.origBodyPadding = body.style.getPropertyValue("padding-bottom");
      state.origBodyPaddingPriority = body.style.getPropertyPriority("padding-bottom");
      state.hadBodyPadding = state.origBodyPadding !== "";
    }
    const current = parseFloat(computed.paddingBottom) || 0;
    const base = Math.max(0, current - state.appliedPadding);
    const lockedViewport = computed.overflowY === "hidden" && document.documentElement && document.documentElement.scrollHeight <= window.innerHeight + 2;
    if (lockedViewport && computed.boxSizing !== "border-box" && !state.changedBoxSizing) {
      state.origBodyBoxSizing = body.style.getPropertyValue("box-sizing");
      state.origBodyBoxSizingPriority = body.style.getPropertyPriority("box-sizing");
      state.hadBodyBoxSizing = state.origBodyBoxSizing !== "";
      body.style.setProperty("box-sizing", "border-box");
      state.changedBoxSizing = true;
    }
    body.style.setProperty("padding-bottom", (base + px) + "px", "important");
    state.appliedPadding = px;
  };

  const restoreElement = (el, entry) => {
    if (el.isConnected) {
      if (entry.hadInline) el.style.setProperty("bottom", entry.origBottom, entry.origPriority);
      else el.style.removeProperty("bottom");
    }
    state.lifted.delete(el);
  };

  const collectFixedCandidates = (px) => {
    const candidates = new Set();
    for (const [el] of Array.from(state.lifted.entries())) {
      if (!el.isConnected) {
        state.lifted.delete(el);
        continue;
      }
      candidates.add(el);
    }
    if (px <= 0 || typeof document.elementsFromPoint !== "function") return candidates;
    const width = window.innerWidth;
    const height = window.innerHeight;
    if (width <= 0 || height <= 0) return candidates;
    const xs = [0.1, 0.3, 0.5, 0.7, 0.9];
    const ys = [height - 6, height - Math.max(18, Math.round(px * 0.5)), height - Math.max(12, px - 6)];
    const visited = new Set();
    for (const fy of ys) {
      if (fy < 0 || fy >= height) continue;
      for (const fx of xs) {
        const hits = document.elementsFromPoint(Math.round(width * fx), fy);
        for (let i = 0; i < hits.length; i++) {
          let node = hits[i];
          while (node && node !== document.body && node !== document.documentElement && !visited.has(node)) {
            visited.add(node);
            if (node.nodeType === 1 && node.id !== BLUR_ID && node.id !== APRON_ID) {
              const pos = getComputedStyle(node).position;
              if (pos === "fixed") candidates.add(node);
            }
            node = node.parentElement;
          }
        }
      }
    }
    return candidates;
  };

  const applyFixed = (px) => {
    if (px <= 0) {
      for (const [el, entry] of Array.from(state.lifted.entries())) restoreElement(el, entry);
      return null;
    }
    const candidates = collectFixedCandidates(px);
    const width = window.innerWidth;
    const height = window.innerHeight;
    let apronSurface = null;
    for (const el of candidates) {
      if (el.id === BLUR_ID || el.id === APRON_ID) continue;
      const existing = state.lifted.get(el);
      const style = getComputedStyle(el);
      if (style.position !== "fixed" || style.display === "none" || style.visibility === "hidden" || parseFloat(style.opacity) === 0 || style.bottom === "auto") {
        if (existing) restoreElement(el, existing);
        continue;
      }
      const rect = el.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0 || rect.height >= height * 0.45) {
        if (existing) restoreElement(el, existing);
        continue;
      }
      const prevShift = existing ? existing.shift : 0;
      const unshiftedBottom = rect.bottom + prevShift;
      if (unshiftedBottom <= height - px + 1 || unshiftedBottom > height + 8) {
        if (existing) restoreElement(el, existing);
        continue;
      }
      let ancestorLifted = false;
      let parent = el.parentElement;
      while (parent && parent !== document.body) {
        if (candidates.has(parent) && state.lifted.has(parent)) {
          ancestorLifted = true;
          break;
        }
        parent = parent.parentElement;
      }
      if (ancestorLifted) {
        if (existing) restoreElement(el, existing);
        continue;
      }
      const parsed = parseFloat(style.bottom);
      if (!isFinite(parsed)) {
        if (existing) restoreElement(el, existing);
        continue;
      }
      const baseBottom = Math.max(0, parsed - prevShift);
      const entry = existing || {
        origBottom: el.style.getPropertyValue("bottom"),
        origPriority: el.style.getPropertyPriority("bottom"),
        hadInline: el.style.getPropertyValue("bottom") !== "",
        shift: 0,
      };
      if (entry.shift !== px) {
        el.style.setProperty("bottom", (baseBottom + px) + "px", "important");
        entry.shift = px;
        state.lifted.set(el, entry);
      }
      if (!apronSurface && baseBottom <= 8 && rect.width >= width * 0.9) {
        apronSurface = findBarSurface(el, width * 0.9);
      }
    }
    return apronSurface;
  };

  const applyAll = () => {
    try {
      const px = insetCss();
      const root = document.documentElement;
      if (root) {
        if (px > 0) root.style.setProperty("--ardia-bottom-inset", px + "px");
        else root.style.removeProperty("--ardia-bottom-inset");
        let style = document.getElementById(STYLE_ID);
        if (px > 0 && !style) {
          style = document.createElement("style");
          style.id = STYLE_ID;
          style.textContent = "html{scroll-padding-bottom:var(--ardia-bottom-inset,0px);}";
          (document.head || root).appendChild(style);
        }
      }
      applyBody(px);
      const apronSurface = applyFixed(px);
      ensureApron(px, apronSurface);
      ensureBlur(px, Boolean(apronSurface));
    } catch (_) {}
  };

  const schedule = () => {
    if (state.timer) return;
    state.timer = setTimeout(() => {
      state.timer = 0;
      if (state.raf) cancelAnimationFrame(state.raf);
      state.raf = requestAnimationFrame(() => {
        state.raf = 0;
        applyAll();
      });
    }, 120);
  };

  window.__ardiaSetBottomInset = (nextInsetDp, nextViewHeightDp, nextBlurEnabled) => {
    state.insetDp = Number(nextInsetDp) > 0 ? Number(nextInsetDp) : 0;
    state.viewHeightDp = Number(nextViewHeightDp) > 0 ? Number(nextViewHeightDp) : 0;
    state.blurEnabled = Boolean(nextBlurEnabled);
    applyAll();
  };

  if (!state.listening) {
    state.listening = true;
    const startObserving = () => {
      applyAll();
      const target = document.body || document.documentElement;
      if (target && typeof MutationObserver === "function") {
        new MutationObserver(schedule).observe(target, { childList: true, subtree: true });
      }
    };
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", startObserving, { once: true });
    } else {
      startObserving();
    }
    window.addEventListener("load", applyAll, { passive: true });
    window.addEventListener("resize", schedule, { passive: true });
  }

  window.__ardiaSetBottomInset($inset,$height,$blur);
})();
        """.trimIndent()
    }

    fun updateCall(insetDp: Double, viewportHeightDp: Double, blurEnabled: Boolean): String =
        "window.__ardiaSetBottomInset&&window.__ardiaSetBottomInset(${format(insetDp)},${format(viewportHeightDp)},${if (blurEnabled) "true" else "false"})"

    private fun format(value: Double): String =
        String.format(Locale.US, "%.2f", value.coerceAtLeast(0.0))
}
