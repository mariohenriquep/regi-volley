import { Button } from '@/components/ui/button';

/**
 * What a route shows when it throws while rendering, or when its code could not be loaded (a lazy chunk, after an update):
 * a plain message and a full reload, instead of a blank screen. It shows nothing of the error, which may carry data.
 */
export function RouteError() {
  return (
    <section role="alert" className="grid gap-4 p-4">
      <h1 className="text-2xl font-semibold">Something went wrong</h1>
      <p>The page could not be shown. Reload the app to try again.</p>
      <Button asChild className="h-11 w-fit px-4">
        <a href="/">Reload the app</a>
      </Button>
    </section>
  );
}
