import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';

/** The association's public page (US-24): open to anyone, so it goes through the client without a bearer token. */
export function usePublicAssociation(shortName: string) {
  const { publicApi } = useAuth();
  return useQuery({
    queryKey: ['public-association', shortName],
    queryFn: () =>
      unwrap(
        publicApi.GET('/api/v1/public/associations/{shortName}', {
          params: { path: { shortName } },
        }),
      ),
  });
}
