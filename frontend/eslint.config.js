import js from '@eslint/js';
import eslintConfigPrettier from 'eslint-config-prettier/flat';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default defineConfig([
  globalIgnores([
    'dist',
    'dev-dist',
    'coverage',
    'test-results',
    'playwright-report',
    'src/api/schema.gen.ts',
  ]),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      reactHooks.configs.flat['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      // No raw HTML injection anywhere (XSS; the API echoes visitor-typed text such as association names).
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'dangerouslySetInnerHTML is forbidden: render text, never HTML.',
        },
      ],
      // The access token lives in memory only (threat model D-7a).
      'no-restricted-globals': [
        'error',
        { name: 'localStorage', message: 'Never persist tokens or personal data in web storage.' },
        {
          name: 'sessionStorage',
          message: 'Never persist tokens or personal data in web storage.',
        },
        { name: 'indexedDB', message: 'Never persist tokens or personal data in browser storage.' },
      ],
      'no-restricted-properties': [
        'error',
        ...['window', 'globalThis', 'self'].flatMap((object) =>
          ['localStorage', 'sessionStorage', 'indexedDB'].map((property) => ({
            object,
            property,
            message: 'Never persist tokens or personal data in browser storage.',
          })),
        ),
      ],
    },
  },
  {
    // shadcn/ui components export variants next to the component.
    files: ['src/components/ui/**/*.tsx'],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
  {
    files: ['scripts/**/*.mjs', '*.config.js'],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
  eslintConfigPrettier,
]);
