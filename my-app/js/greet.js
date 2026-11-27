/**
 * greet.js — proves that a relative ES6 module import resolves through the
 * virtual origin. If the MIME type or the origin were wrong, this file would
 * never execute and #status would stay on "loading…".
 */
export function greet(name) {
  return `Hello, ${name}!`;
}

export const FIXTURE_VERSION = '1.0.0';
