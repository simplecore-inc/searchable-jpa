import type { E2EConfig } from 'e2e';
import { web } from '@e2e-dev/web';
export default {
  tests: ['scripts/tests/**/*.e2e.ts'],
  targets: [{
    engine: web(),
    app: {
      url: 'http://127.0.0.1:8764',
      command: { executable: 'node', args: ['scripts/docs-test-server.mjs'], reuseExisting: true },
    },
  }],
  assertionTimeout: 15000,
} satisfies E2EConfig;
