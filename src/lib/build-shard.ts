// CI parallelizes the build across separate jobs (see .github/workflows/ci.yml):
// one dedicated "primary" job for the ~5.4k regular-route pages, and N equal
// "chapters" jobs splitting the book-chapter route's ~75k pages (dwarfs every
// other route combined). BUILD_ROLE decouples the two so neither job carries
// the other's work — a plain local `pnpm build` (no env vars set) renders
// everything, same as before this existed.
const ROLE = process.env.BUILD_ROLE; // "primary" | "chapters" | undefined (local/unsharded)
const SHARD = Number(process.env.BUILD_SHARD ?? 0);
const SHARD_COUNT = Number(process.env.BUILD_SHARD_COUNT ?? 1);
const UNSHARDED = SHARD_COUNT === 1;

export const isPrimaryShard = UNSHARDED || ROLE === "primary";
const rendersChapters = UNSHARDED || ROLE === "chapters";

export function shardPaths<T>(paths: T[]): T[] {
  if (!rendersChapters) return [];
  if (UNSHARDED) return paths;
  return paths.filter((_, i) => i % SHARD_COUNT === SHARD);
}
