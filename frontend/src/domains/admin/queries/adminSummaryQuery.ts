import { queryOptions } from '@tanstack/react-query';
import { getAdminSummary } from '../api/adminApi';

export const adminSummaryQuery = queryOptions({
  queryKey: ['admin', 'dashboard'],
  queryFn: ({ signal }) => getAdminSummary(signal),
});
