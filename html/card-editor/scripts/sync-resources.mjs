import { mkdir, readFile, readdir, unlink, writeFile } from "node:fs/promises";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";

const project = fileURLToPath(new URL("../", import.meta.url));
const assets = resolve(project, "../../src/main/resources/assets");
const destination = join(project, "src/resources");
const cardDirectory = join(assets, "system/cards");
const names = (await readdir(cardDirectory))
  .filter((name) => name.endsWith(".json"))
  .sort();

async function jsonSource(path) {
  const text = await readFile(path, "utf8");
  return JSON.stringify(JSON.parse(text), null, 2) + "\n";
}

// Read and parse all inputs before replacing any checked-in snapshot.
const cards = await Promise.all(
  names.map(async (name) => [
    name,
    await jsonSource(join(cardDirectory, name)),
  ]),
);
const languages = await Promise.all(
  ["zh_cn", "en_us"].map(async (language) => [
    `${language}.json`,
    await jsonSource(join(assets, "system/lang", `${language}.json`)),
  ]),
);
const css = await readFile(
  join(assets, "apricityui/apricity/system/assistant.css"),
  "utf8",
);

for (const [folder, files] of [
  ["templates", cards],
  ["lang", languages],
]) {
  const target = join(destination, folder);
  await mkdir(target, { recursive: true });
  for (const [name, contents] of files) {
    await writeFile(join(target, name), contents, "utf8");
  }
  if (folder === "templates") {
    const active = new Set(names);
    for (const name of await readdir(target)) {
      if (name.endsWith(".json") && !active.has(name)) {
        await unlink(join(target, name));
      }
    }
  }
}
await writeFile(join(destination, "assistant.css"), css, "utf8");
await writeFile(
  join(destination, "snapshot.json"),
  JSON.stringify(
    { snapshotDate: new Date().toISOString().slice(0, 10) },
    null,
    2,
  ) + "\n",
  "utf8",
);
console.log(
  `Synced ${cards.length} card templates, both languages and assistant.css.`,
);
