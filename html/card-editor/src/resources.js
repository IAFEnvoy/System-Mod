import themeCSS from "./resources/assistant-theme.css?raw";
import layoutCSS from "./resources/assistant.css?raw";

// Raw snapshots are embedded in the preview, so resolve the local import before inserting CSS.
const sharedCSS = themeCSS + "\n" + layoutCSS.replace('@import "assistant-theme.css";', "");

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

export const emotions = namedResources(
  import.meta.glob("./resources/emotion/*.json", {
    eager: true,
    import: "default",
  }),
);

export const emotionDefinitions = Object.fromEntries(Object.entries(namedResources(
  import.meta.glob("./resources/emotion-definition/*.json", {
    eager: true,
    import: "default",
  }),
)).map(([id, definition]) => ["system:" + id, definition]));

const emotionTextures = import.meta.glob("./resources/textures/emotion/**/*.png", {
  eager: true,
  query: "?url&no-inline",
  import: "default",
});

export function emotionTexture(id) {
  return emotionTextures["./resources/" + String(id).replace(/^system:/, "")];
}

export { sharedCSS };
