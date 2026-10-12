import { expect, type APIRequestContext } from '@playwright/test';

/** Mailpit's API (docker-compose service `mailpit`): the dev mail catcher, never a real provider. */
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

interface Summary {
  ID: string;
  To: { Address: string }[];
}

/** Waits for the activation mail to `address` and returns the token of its `/activate#token=...` link. */
export async function activationToken(
  request: APIRequestContext,
  address: string,
): Promise<string> {
  let messageId: string | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT}/api/v1/search`, {
          params: { query: `to:"${address}"` },
        });
        const { messages } = (await response.json()) as { messages: Summary[] };
        messageId = messages[0]?.ID;
        return messageId;
      },
      { message: `activation mail to ${address}`, timeout: 20_000 },
    )
    .toBeTruthy();

  const message = await request.get(`${MAILPIT}/api/v1/message/${messageId ?? ''}`);
  const { Text } = (await message.json()) as { Text: string };
  const match = /\/activate#token=([^\s"<>]+)/.exec(Text);
  if (!match?.[1]) throw new Error('No activation link in the mail');
  return decodeURIComponent(match[1]);
}
