package com.ardia.browser

import java.util.Locale

internal object PageBottomInset {
    fun script(insetDp: Double, viewportHeightDp: Double): String {
        val inset = format(insetDp)
        val height = format(viewportHeightDp)
        return """
(() => {
  if (window !== window.top) return;
  const STYLE_ID = "__ardia_bottom_inset_style";
  const APRON_ID = "__ardia_bottom_apron";
  const state = window.__ardiaBottomInset || (window.__ardiaBottomInset = {
    insetDp: 0,
    viewHeightDp: 0,
    lastPx: 0,
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

  const ensureApron = (px, surface, animate) => {
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
    apron.style.setProperty("transition", animate ? "height 260ms cubic-bezier(0.2,0,0,1)" : "none", "important");
    apron.style.setProperty("height", (px + 1) + "px", "important");
    apron.style.setProperty("background-color", surface.backgroundColor, "important");
    if (surface.backdropFilter) {
      setBackdrop(apron, surface.backdropFilter);
    } else {
      apron.style.removeProperty("-webkit-backdrop-filter");
      apron.style.removeProperty("backdrop-filter");
    }
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
      if (entry.hadTransition) el.style.setProperty("transition", entry.origTransition, entry.origTransitionPriority);
      else el.style.removeProperty("transition");
    }
    state.lifted.delete(el);
  };

  const isTransparentFullOverlay = (el, style, rect, height) => {
    if (rect.height < height * 0.75) return false;
    if (style.position !== "fixed" && style.position !== "absolute") return false;
    if (style.bottom === "auto") return false;
    const bg = parseRgba(style.backgroundColor);
    if (bg && bg.a >= 0.1) return false;
    if (el.querySelector && el.querySelector("video, iframe")) return false;
    return true;
  };

  const isBottomOverlay = (el, style, height) => {
    if (style.position === "fixed") return true;
    if (style.position !== "absolute" || style.bottom === "auto") return false;
    const parent = el.offsetParent || el.parentElement;
    if (!parent) return true;
    const parentRect = parent.getBoundingClientRect();
    return parentRect.height >= height * 0.75 && parentRect.bottom >= height - 12 && parentRect.bottom <= height + 12;
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
    const xs = [0.1, 0.3, 0.5, 0.7, 0.9, 0.94];
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
            if (node.nodeType === 1 && node.id !== APRON_ID) {
              const style = getComputedStyle(node);
              if (isBottomOverlay(node, style, height)) candidates.add(node);
            }
            node = node.parentElement;
          }
        }
      }
    }
    return candidates;
  };

  const applyFixed = (px, animate) => {
    if (px <= 0) {
      for (const [el, entry] of Array.from(state.lifted.entries())) restoreElement(el, entry);
      return null;
    }
    const candidates = collectFixedCandidates(px);
    const width = window.innerWidth;
    const height = window.innerHeight;
    let apronSurface = null;
    for (const el of candidates) {
      if (el.id === APRON_ID) continue;
      const existing = state.lifted.get(el);
      const style = getComputedStyle(el);
      if (!isBottomOverlay(el, style, height) || style.display === "none" || style.visibility === "hidden" || parseFloat(style.opacity) === 0 || style.bottom === "auto") {
        if (existing) restoreElement(el, existing);
        continue;
      }
      const rect = el.getBoundingClientRect();
      const maxHeight = rect.width < width * 0.35 ? height * 0.6 : height * 0.45;
      const fullOverlay = isTransparentFullOverlay(el, style, rect, height);
      if (rect.width <= 0 || rect.height <= 0 || (!fullOverlay && rect.height >= maxHeight)) {
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
        origTransition: el.style.getPropertyValue("transition"),
        origTransitionPriority: el.style.getPropertyPriority("transition"),
        hadTransition: el.style.getPropertyValue("transition") !== "",
        shift: 0,
      };
      if (entry.shift !== px) {
        if (animate && existing) {
          el.style.setProperty("transition", "bottom 260ms cubic-bezier(0.2,0,0,1)", "important");
        } else if (!existing) {
          if (entry.hadTransition) el.style.setProperty("transition", entry.origTransition, entry.origTransitionPriority);
          else el.style.removeProperty("transition");
        }
        el.style.setProperty("bottom", (baseBottom + px) + "px", "important");
        entry.shift = px;
        state.lifted.set(el, entry);
      }
      if (!apronSurface && style.position === "fixed" && baseBottom <= 8 && rect.width >= width * 0.9) {
        apronSurface = findBarSurface(el, width * 0.9);
      }
    }
    return apronSurface;
  };

  const applyAll = () => {
    try {
      const px = insetCss();
      const animate = state.lastPx > 0 && px > 0 && state.lastPx !== px;
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
      const apronSurface = applyFixed(px, animate);
      ensureApron(px, apronSurface, animate);
      state.lastPx = px;
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

  window.__ardiaSetBottomInset = (nextInsetDp, nextViewHeightDp) => {
    state.insetDp = Number(nextInsetDp) > 0 ? Number(nextInsetDp) : 0;
    state.viewHeightDp = Number(nextViewHeightDp) > 0 ? Number(nextViewHeightDp) : 0;
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

  window.__ardiaSetBottomInset($inset,$height);
})();
        """.trimIndent()
    }

    fun updateCall(insetDp: Double, viewportHeightDp: Double): String =
        "window.__ardiaSetBottomInset&&window.__ardiaSetBottomInset(${format(insetDp)},${format(viewportHeightDp)})"

    private fun format(value: Double): String =
        String.format(Locale.US, "%.2f", value.coerceAtLeast(0.0))
}
