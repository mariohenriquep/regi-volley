// @vitest-environment node
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'vite';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { pwaOptions } from './pwa.config.ts';

describe('PWA configuration', () => {
  it('precaches the app shell only', () => {
    // Arrange
    const patterns = pwaOptions.workbox?.globPatterns ?? [];

    // Act
    const touchesApi = patterns.some((pattern) => pattern.includes('api'));

    // Assert
    expect(patterns).toEqual(['**/*.{js,css,html,svg,png,ico,webmanifest,woff2}']);
    expect(touchesApi).toBe(false);
  });

  it('caches nothing at runtime, so no API response is ever stored', () => {
    // Arrange
    const workbox = pwaOptions.workbox;

    // Act
    const runtimeCaching = workbox?.runtimeCaching;

    // Assert
    expect(runtimeCaching).toBeUndefined();
  });

  it('never answers an /api navigation with the cached app shell', () => {
    // Arrange
    const denylist = pwaOptions.workbox?.navigateFallbackDenylist ?? [];

    // Act
    const denied = (path: string) => denylist.some((pattern) => pattern.test(path));

    // Assert
    expect(pwaOptions.workbox?.navigateFallback).toBe('/index.html');
    expect(denied('/api/v1/me')).toBe(true);
    expect(denied('/api')).toBe(true);
    expect(denied('/activate')).toBe(false);
  });

  it('registers the service worker from the app bundle, so index.html needs no extra script (CSP)', () => {
    // Arrange
    const options = pwaOptions;

    // Act
    const injectRegister = options.injectRegister;

    // Assert
    expect(injectRegister).toBe(false);
    expect(options.registerType).toBe('autoUpdate');
  });

  it('activates a new version at once and takes control of the open pages', () => {
    // Arrange
    const workbox = pwaOptions.workbox;

    // Act
    const activation = { skipWaiting: workbox?.skipWaiting, clientsClaim: workbox?.clientsClaim };

    // Assert
    expect(activation).toEqual({ skipWaiting: true, clientsClaim: true });
  });

  it('describes the installable app', () => {
    // Arrange
    const manifest = pwaOptions.manifest;
    if (!manifest) throw new Error('manifest missing');

    // Act
    const sizes = (manifest.icons ?? []).map(
      (icon) => `${icon.sizes ?? ''} ${String(icon.purpose ?? 'any')}`,
    );

    // Assert
    expect(manifest.name).toBe('RegiVolley');
    expect(manifest.short_name).toBe('RegiVolley');
    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/');
    expect(manifest.theme_color).toMatch(/^#[0-9a-f]{6}$/i);
    expect(sizes).toEqual(['192x192 any', '512x512 any', '512x512 maskable']);
  });
});

describe('the built service worker', () => {
  const root = fileURLToPath(new URL('.', import.meta.url));
  let outDir = '';
  let sw = '';
  let indexHtml = '';
  let bundle = '';

  beforeAll(async () => {
    outDir = await mkdtemp(join(tmpdir(), 'rv-pwa-'));
    // Vitest runs with NODE_ENV=test, which makes Workbox emit its development build.
    const nodeEnv = process.env.NODE_ENV;
    process.env.NODE_ENV = 'production';
    await build({
      root,
      configFile: join(root, 'vite.config.ts'),
      mode: 'production',
      logLevel: 'silent',
      build: { outDir, emptyOutDir: true },
    });
    process.env.NODE_ENV = nodeEnv;
    sw = await readFile(join(outDir, 'sw.js'), 'utf8');
    indexHtml = await readFile(join(outDir, 'index.html'), 'utf8');
    const entry = /src="\/(assets\/index-[^"]+\.js)"/.exec(indexHtml)?.[1] ?? '';
    bundle = await readFile(join(outDir, entry), 'utf8');
  }, 120_000);

  afterAll(async () => {
    await rm(outDir, { recursive: true, force: true });
  });

  it('skips waiting and claims the clients', () => {
    // Arrange
    const code = sw;

    // Act
    const skips = code.includes('self.skipWaiting()');
    const claims = /clientsClaim\(\)/.test(code);

    // Assert
    expect(skips).toBe(true);
    expect(claims).toBe(true);
  });

  it('routes navigations only, never /api, and caches nothing at runtime', () => {
    // Arrange
    const code = sw;

    // Act
    const routes = code.match(/registerRoute\(/g) ?? [];

    // Assert
    expect(routes).toHaveLength(1);
    expect(code).toContain('NavigationRoute');
    expect(code).toContain('denylist:[/^\\/api(\\/|$)/]');
  });

  it('precaches the shell and no API URL', () => {
    // Arrange
    const urls = [...sw.matchAll(/url:"([^"]+)"/g)].map((match) => match[1] ?? '');

    // Act
    const apiUrls = urls.filter((url) => url.includes('api/'));

    // Assert
    expect(urls).toContain('index.html');
    expect(apiUrls).toEqual([]);
  });

  it('has no inline script in index.html and registers the worker from the bundle', () => {
    // Arrange
    const scripts = [...indexHtml.matchAll(/<script\b[^>]*>/g)].map((match) => match[0]);

    // Act
    const inline = scripts.filter((tag) => !tag.includes(' src='));

    // Assert
    expect(inline).toEqual([]);
    expect(indexHtml).not.toContain('registerSW.js');
    expect(bundle).toContain('/sw.js');
  });
});
