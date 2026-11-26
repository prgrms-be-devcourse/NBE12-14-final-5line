import { apiClient } from '@/shared/lib/http/apiClient';

export type Member = { id: number; email: string; nickname: string; role?: string };
export type SignupInput = { email: string; password: string; nickname: string };
export type LoginInput = { email: string; password: string };
export type ResetConfirmInput = { token: string; newPassword: string; newPasswordConfirmation: string };

const authPath = '/api/v1/auth';
const memberPath = '/api/v1/members/me';

export async function csrfHeader(): Promise<Record<string, string>> {
  const { data } = await apiClient.get<{ token: string; headerName: string }>(`${authPath}/csrf`);
  return { [data.headerName]: data.token };
}

export async function signup(input: SignupInput): Promise<Member> {
  const { data } = await apiClient.post<Member>(`${authPath}/signup`, input, { headers: await csrfHeader() });
  return data;
}

export async function login(input: LoginInput): Promise<Member> {
  const { data } = await apiClient.post<Member>(`${authPath}/login`, input, { headers: await csrfHeader() });
  return data;
}

export async function currentMember(signal?: AbortSignal): Promise<Member> {
  const { data } = await apiClient.get<Member>(memberPath, { signal });
  return data;
}

export async function logout(): Promise<void> {
  await apiClient.post(`${authPath}/logout`, undefined, { headers: await csrfHeader() });
}

export async function requestPasswordReset(email: string): Promise<string> {
  const { data } = await apiClient.post<{ message: string }>(`${authPath}/password-reset/request`,
    { email }, { headers: await csrfHeader() });
  return data.message;
}

export async function validatePasswordReset(token: string): Promise<void> {
  await apiClient.get(`${authPath}/password-reset/validate`, { params: { token } });
}

export async function confirmPasswordReset(input: ResetConfirmInput): Promise<string> {
  const { data } = await apiClient.post<{ message: string }>(`${authPath}/password-reset/confirm`,
    input, { headers: await csrfHeader() });
  return data.message;
}

export async function withdraw(password: string): Promise<string> {
  const { data } = await apiClient.delete<{ message: string }>(memberPath, {
    data: { password, confirmed: true },
    headers: await csrfHeader(),
  });
  return data.message;
}
