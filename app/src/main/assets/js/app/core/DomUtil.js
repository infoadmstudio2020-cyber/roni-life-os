/**
 * DOM and String Utilities
 * Pure Vanilla JavaScript Helper Functions
 * Part of RONi Android Library Generator Architecture
 */

/**
 * Escapes unsafe characters for HTML output to prevent XSS.
 * @param {string|unknown} str 
 * @returns {string}
 */
export function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Strips HTML tags from a string.
 * @param {string} str 
 * @returns {string}
 */
export function stripHtml(str) {
  if (!str) return '';
  return String(str).replace(/<[^>]*>?/gm, '');
}

/**
 * Helper to safely create an element with class name and attributes.
 * @param {string} tag 
 * @param {Object} [attrs={}] 
 * @param {string|HTMLElement|Array<string|HTMLElement>} [children] 
 * @returns {HTMLElement}
 */
export function createElement(tag, attrs = {}, children = null) {
  const el = document.createElement(tag);
  for (const [key, val] of Object.entries(attrs)) {
    if (key === 'className' || key === 'class') {
      el.className = val;
    } else if (key === 'style' && typeof val === 'object') {
      Object.assign(el.style, val);
    } else if (key.startsWith('on') && typeof val === 'function') {
      const evt = key.slice(2).toLowerCase();
      el.addEventListener(evt, val);
    } else if (val !== null && val !== undefined) {
      el.setAttribute(key, val);
    }
  }
  if (children) {
    if (Array.isArray(children)) {
      children.forEach(child => {
        if (typeof child === 'string') {
          el.appendChild(document.createTextNode(child));
        } else if (child instanceof HTMLElement) {
          el.appendChild(child);
        }
      });
    } else if (typeof children === 'string') {
      el.textContent = children;
    } else if (children instanceof HTMLElement) {
      el.appendChild(children);
    }
  }
  return el;
}
