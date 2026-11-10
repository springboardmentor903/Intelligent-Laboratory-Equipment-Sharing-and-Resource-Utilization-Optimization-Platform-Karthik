import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectDir = path.join(__dirname, 'lab-resource-platform');

const isWin = process.platform === 'win32';
const mvnCmd = isWin ? 'mvnw.cmd' : './mvnw';
const fullMvnPath = path.join(projectDir, mvnCmd);

console.log(`Starting Spring Boot backend server via ${mvnCmd}...`);

const child = spawn(fullMvnPath, ['spring-boot:run'], {
  cwd: projectDir,
  stdio: 'inherit',
  shell: true,
  env: {
    ...process.env,
    DB_PASSWORD: process.env.DB_PASSWORD || 'postgres',
    JWT_SECRET: process.env.JWT_SECRET || 'TPZr8jSEc6CCwxwT0kh3Z8tcm1OL5dm9yKIJibwNQ5U='
  }
});

child.on('error', (err) => {
  console.error('Failed to start backend server:', err);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
  } else {
    process.exit(code ?? 0);
  }
});

process.on('SIGINT', () => {
  child.kill('SIGINT');
});

process.on('SIGTERM', () => {
  child.kill('SIGTERM');
});

