// @vitest-environment jsdom
import '../../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { server } from '../../../../../../tests/mocks/server';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import FavoritePlaceButton from './FavoritePlaceButton';
const api='https://api.outpick.test/api/v1';
const path=`${api}/members/me/favorites`;
function mount(){render(<QueryClientProvider client={createQueryClient()}><FavoritePlaceButton placeId={1} placeName="시험 장소" iconOnly/></QueryClientProvider>);}
describe('카드 북마크',()=>{
  it('비회원은 아이콘 링크로 로그인 안내하고 저장 요청은 보내지 않는다',async()=>{
    const save=vi.fn(()=>HttpResponse.json({}));
    server.use(http.get(`${api}/members/me`,()=>new HttpResponse(null,{status:401})),http.post(path,save));
    mount();const login=await screen.findByRole('link',{name:'시험 장소 로그인하고 관심 장소 저장'});
    expect(login.getAttribute('href')).toMatch(/^\/login\/?$/);expect(login.textContent).toBe('');expect(save).not.toHaveBeenCalled();
  });
  it('저장·해제 후 아이콘 상태와 완료 안내가 실제 목록에 맞게 바뀐다',async()=>{
    const user=userEvent.setup();let saved=false;
    server.use(
      http.get(`${api}/members/me`,()=>HttpResponse.json({id:9,role:'USER'})),
      http.get(`${api}/auth/csrf`,()=>HttpResponse.json({token:'test',headerName:'X-CSRF-TOKEN'})),
      http.get(path,()=>HttpResponse.json(saved?[{id:1,publishStatus:'PUBLISHED',name:'시험 장소',isNotified:false}]:[])),
      http.post(path,async({request})=>{expect(await request.json()).toEqual({placeId:1});saved=true;return HttpResponse.json({placeId:1,message:'관심 장소에 저장했어요.'});}),
      http.delete(`${path}/1`,()=>{saved=false;return HttpResponse.json({placeId:1,message:'관심 장소에서 해제했어요.'});})
    );
    mount();const save=await screen.findByRole('button',{name:'시험 장소 관심 장소 저장'});expect(save.getAttribute('aria-pressed')).toBe('false');
    await user.click(save);const remove=await screen.findByRole('button',{name:'시험 장소 관심 장소 해제'});expect(remove.getAttribute('aria-pressed')).toBe('true');
    await waitFor(()=>expect(remove.hasAttribute('disabled')).toBe(false));await user.click(remove);
    expect(await screen.findByRole('button',{name:'시험 장소 관심 장소 저장'})).toBeTruthy();expect(await screen.findByText('관심 장소에서 해제했어요.')).toBeTruthy();
  });
  it('저장 실패 시 저장된 아이콘으로 바꾸지 않고 오류를 보여 준다',async()=>{
    const user=userEvent.setup();
    server.use(http.get(`${api}/members/me`,()=>HttpResponse.json({id:9,role:'USER'})),http.get(`${api}/auth/csrf`,()=>HttpResponse.json({token:'test',headerName:'X-CSRF-TOKEN'})),http.get(path,()=>HttpResponse.json([])),http.post(path,()=>new HttpResponse(null,{status:500})));
    mount();await user.click(await screen.findByRole('button',{name:'시험 장소 관심 장소 저장'}));
    expect(await screen.findByRole('alert')).toBeTruthy();expect(screen.getByRole('button',{name:'시험 장소 관심 장소 저장'}).getAttribute('aria-pressed')).toBe('false');
  });
});
