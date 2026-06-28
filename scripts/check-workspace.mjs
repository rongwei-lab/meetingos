import { existsSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

const required = [
  "会议系统需求文档.md",
  "meetingos-backend/pom.xml",
  "meetingos-admin/package.json",
  "deploy/docker-compose.yml",
  ".env.example",
  "README.md"
];

let failed = false;

for (const item of required) {
  const ok = existsSync(join(root, item));
  console.log(`${ok ? "OK " : "MISS"} ${item}`);
  if (!ok) failed = true;
}

if (failed) {
  console.error("\nMeetingOS workspace is incomplete. Run the relevant setup or generation task first.");
  process.exit(1);
}

console.log("\nMeetingOS workspace structure looks ready.");
