import { useQuery } from '@tanstack/react-query';
import { currentMemberQuery } from '../queries/currentMemberQuery';

export function useCurrentMember() {
  return useQuery(currentMemberQuery);
}
