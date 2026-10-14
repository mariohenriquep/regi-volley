import { expect, type APIRequestContext } from '@playwright/test';

/** Mailpit's API (docker-compose service `mailpit`): the dev mail catcher, never a real provider. */
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

interface Summary {
  ID: string;
}

/** The account links the API mails: `WEB_ORIGIN/activate#token=...` and `WEB_ORIGIN/reset-password#token=...`. */
export type LinkPath = '/activate' | '/reset-password';

/**
 * Waits for a mail to `address` that carries a link to `path` and returns the token of its fragment. Mails are
 * searched newest first, so a second link (a reset after an activation) is the one returned.
 */
export async function emailedToken(
  request: APIRequestContext,
  address: string,
  path: LinkPath,
): Promise<string> {
  const pattern = new RegExp(`${path}#token=([^\\s"<>]+)`);
  let token: string | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT}/api/v1/search`, {
          params: { query: `to:"${address}"` },
        });
        const { messages } = (await response.json()) as { messages: Summary[] };
        for (const { ID } of messages) {
          const message = await request.get(`${MAILPIT}/api/v1/message/${ID}`);
          const { Text } = (await message.json()) as { Text: string };
          const match = pattern.exec(Text);
          if (match?.[1]) {
            token = decodeURIComponent(match[1]);
            return token;
          }
        }
        return undefined;
      },
      { message: `mail to ${address} with a ${path} link`, timeout: 20_000 },
    )
    .toBeTruthy();
  return token ?? '';
}

/**
 * Every token of the links of `path` mailed to `address`, newest first. Unlike {@link emailedToken} it does not wait: use it
 * after one mail is known to exist, to see whether another arrived (a resent activation link).
 */
export async function emailedTokens(
  request: APIRequestContext,
  address: string,
  path: LinkPath,
): Promise<string[]> {
  const pattern = new RegExp(`${path}#token=([^\\s"<>]+)`);
  const response = await request.get(`${MAILPIT}/api/v1/search`, {
    params: { query: `to:"${address}"` },
  });
  const { messages } = (await response.json()) as { messages: Summary[] };
  const tokens: string[] = [];
  for (const { ID } of messages) {
    const message = await request.get(`${MAILPIT}/api/v1/message/${ID}`);
    const { Text } = (await message.json()) as { Text: string };
    const match = pattern.exec(Text);
    if (match?.[1]) tokens.push(decodeURIComponent(match[1]));
  }
  return tokens;
}
