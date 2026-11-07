export const $ = (id) => document.getElementById(id);
export const clone = (value) => structuredClone(value);
export const pretty = (value) => JSON.stringify(value, null, 2);
export const object = (value) =>
  value !== null && typeof value === "object" && !Array.isArray(value);
export const own = (value, key) => Object.hasOwn(value, key);
export const at = (value, path) =>
  path.reduce((parent, key) => parent?.[key], value);
export const pathKey = (path) => JSON.stringify(path);
export const displayPath = (path) =>
  path.length
    ? "$" +
      path
        .map((key) => (typeof key === "number" ? `[${key}]` : `.${key}`))
        .join("")
    : "$";
