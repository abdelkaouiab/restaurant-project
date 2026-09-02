import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    setupFiles: './src/test/setup.ts',
    clearMocks: true,
    pool: 'forks',
    maxWorkers: 1,
    fileParallelism: false,
  },
});
