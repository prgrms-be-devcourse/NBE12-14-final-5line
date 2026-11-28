import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { FormEvent } from 'react';
import type { AdminPlaceDetail } from '../../types/adminPlace.type';
import { changeAdminPlaceStatus, updateAdminPlace } from '../../api/adminApi';
import { adminDraftPlacesKey } from '../../queries/adminPlacesQuery';
import { placeField, placeFieldLabel, placeSecondaryButton } from '../adminPlaceStyles';

export default function AdminPlaceControls({ place }: { place: AdminPlaceDetail }) {
  const client = useQueryClient();
  const [name, setName] = useState(place.name ?? '');
  const [address, setAddress] = useState(place.address ?? '');
  const [description, setDescription] = useState(place.description ?? '');
  const [error, setError] = useState('');
  const refresh = async () => { await Promise.all([
    client.invalidateQueries({ queryKey: adminDraftPlacesKey }),
    client.invalidateQueries({ queryKey: ['admin', 'places', 'detail', place.id] }),
  ]); };
  const edit = useMutation({ mutationFn: () => updateAdminPlace(place.id, { name: name.trim(), address: address.trim(), description: description.trim() }),
    onSuccess: refresh, onError: () => setError('장소 정보를 저장하지 못했어요.') });
  const visibility = useMutation({ mutationFn: (status: AdminPlaceDetail['status']) => changeAdminPlaceStatus(place.id, status),
    onSuccess: refresh, onError: () => setError('공개 상태를 변경하지 못했어요. 검증된 출처와 공개 가능한 종목을 확인해 주세요.') });
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); setError(''); edit.mutate(); };
  return <section aria-label="장소 정보와 공개 상태" className="space-y-3 rounded-lg border border-slate-200 p-3">
    <h4 className="text-sm font-bold">정보 수정</h4>
    <form className="space-y-2" onSubmit={submit}>
      <label className={placeFieldLabel}>장소명<input className={placeField} required maxLength={50} value={name} onChange={event => setName(event.target.value)} /></label>
      <label className={placeFieldLabel}>주소<input className={placeField} required maxLength={100} value={address} onChange={event => setAddress(event.target.value)} /></label>
      <label className={placeFieldLabel}>소개<textarea className={placeField} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></label>
      <button className={placeSecondaryButton} disabled={edit.isPending} type="submit">정보 저장</button>
    </form>
    <div className="flex flex-wrap items-center gap-2 text-sm">
      <span>공개 상태: {place.status}</span>
      {(['DRAFT', 'PUBLISHED', 'HIDDEN'] as const).filter(status => status !== place.status).map(status =>
        <button key={status} type="button" className={placeSecondaryButton} disabled={visibility.isPending}
          onClick={() => { setError(''); visibility.mutate(status); }}>{status === 'PUBLISHED' ? '공개' : status === 'HIDDEN' ? '숨기기' : '검수 전환'}</button>)}
    </div>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
  </section>;
}
