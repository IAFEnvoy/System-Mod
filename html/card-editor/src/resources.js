import sharedCSS from "./resources/assistant.css?raw";

// Vite tracks each snapshot independently, including added or removed templates.
function namedResources(modules) {
  return Object.fromEntries(
    Object.entries(modules).map(([path, value]) => [
      path.slice(path.lastIndexOf("/") + 1, -".json".length),
      value,
    ]),
  );
}

export const templates = namedResources(
  import.meta.glob("./resources/templates/*.json", {
    eager: true,
    import: "default",
  }),
);

export const languages = namedResources(
  import.meta.glob("./resources/lang/*.json", {
    eager: true,
    import: "default",
  }),
);

export { sharedCSS };
