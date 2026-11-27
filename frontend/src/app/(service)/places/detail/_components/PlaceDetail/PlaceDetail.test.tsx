// @vitest-environment jsdom
import '../../../../../../../tests/setup-dom';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../../../../../../../tests/mocks/server';
import QueryProvider from '@/app/_providers/QueryProvider';
import PlaceDetail from './PlaceDetail';
vi.mock('next/navigation', () => ({ useSearchParams: () => new URLSearchParams(window.location.search) }));
const base='https://api.outpick.test/api/v1/places';
const body={id:1,name:'DB 장소',nameAlias:null,description:'DB에 저장된 소개',address:'확인된 주소',latitude:37.37,longitude:128.39,homepage:'https://example.com/',operationStatus:null,reviewedAt:null,representativeImage:null,
  activities:[{placeActivityId:10,activityTypeId:20,activityTypeName:'스키',environmentType:'OUTDOOR',seasons:['WINTER']}],
  notices:[{placeNoticeId:30,title:'확인된 공지',content:'공지 본문',sourceRecordId:40}],
  sources:[{sourceRecordId:40,url:'https://example.com/source',verifiedFields:'주소',verifiedAt:'2026-11-13T12:00:00'}]};
const mount=()=>render(<QueryProvider><PlaceDetail /></QueryProvider>);
beforeEach(()=>{window.history.replaceState(null,'','/places/detail/?id=1'); server.use(
  http.get(base+'/1',()=>HttpResponse.json(body)),
  http.get(base+'/1/reviews',()=>HttpResponse.json([{id:5,placeId:1,authorId:7,authorNickname:'방문자',body:'이 장소에서 즐거웠어요',createdAt:'2026-11-19T10:00:00',updatedAt:'2026-11-19T10:00:00'}])),
  http.get('https://api.outpick.test/api/v1/members/me',()=>new HttpResponse(null,{status:401}))
);});
describe('DB 장소 상세 화면',()=>{
  it('서버의 종목·공지·출처와 주소를 표시한다',async()=>{
    mount(); expect(await screen.findByRole('heading',{level:1,name:'DB 장소'})).toBeTruthy();
    expect(screen.getByRole('heading',{name:'스키'})).toBeTruthy(); expect(screen.getByText('실외 · 겨울')).toBeTruthy();
    expect(screen.getByRole('heading',{name:'확인된 공지'})).toBeTruthy();
    expect(screen.getByRole('link',{name:'출처 안내 열기 ↗'}).getAttribute('href')).toBe('https://example.com/source');
    expect(screen.getByText('2026-11-13 12:00:00')).toBeTruthy();
    expect(screen.getByRole('link',{name:'카카오맵 길찾기 ↗'}).getAttribute('href')).toContain('37.37,128.39');
    expect(await screen.findByRole('heading',{name:'이 장소의 후기'})).toBeTruthy();
    expect(await screen.findByText('이 장소에서 즐거웠어요')).toBeTruthy();
  });
  it('목록의 지도·필터 복귀 주소를 유지한다',async()=>{
    window.history.replaceState(null,'','/places/detail/?'+new URLSearchParams({id:'1',returnTo:'/?region=강원&lat=37&lng=128&level=8'}));
    mount(); await screen.findByRole('heading',{name:'DB 장소'});
    expect(new URL(screen.getByRole('link',{name:'← 장소 목록으로 돌아가기'}).getAttribute('href')!,window.location.origin).searchParams.get('level')).toBe('8');
  });
  it('알림에서 진입하면 운영 공지 영역을 중앙에 배치하고 이후 공지는 해당 제목으로 이동한다', async () => {
    const scroll = vi.fn();
    Object.defineProperty(Element.prototype, 'scrollIntoView', { configurable: true, value: scroll });
    try {
      server.use(http.get(base + '/1', () => HttpResponse.json({
        ...body,
        notices: [...body.notices, { placeNoticeId: 31, title: '두 번째 공지', content: '다른 공지 본문', sourceRecordId: 40 }],
      })));
      window.history.replaceState(null, '', '/places/detail/?id=1#place-notice-30');
      mount();
      const sectionHeading = await screen.findByRole('heading', { name: '운영 공지' });
      const heading = await screen.findByRole('heading', { name: '확인된 공지' });
      expect(heading.closest('article')?.getAttribute('data-place-notice-id')).toBe('30');
      expect(document.getElementById('place-notice-30')).toBeNull();
      await waitFor(() => expect(scroll.mock.instances[0]).toBe(sectionHeading));
      expect(scroll).toHaveBeenLastCalledWith({ block: 'center' });
      window.history.replaceState(null, '', '/places/detail/?id=1#place-notice-31');
      window.dispatchEvent(new HashChangeEvent('hashchange'));
      expect(scroll.mock.instances[1]).toBe(screen.getByRole('heading', { name: '두 번째 공지' }));
      window.history.replaceState(null, '', '/places/detail/?id=1#place-notice-999');
      window.dispatchEvent(new HashChangeEvent('hashchange'));
      expect(scroll.mock.instances[2]).toBe(sectionHeading);
    } finally {
      Reflect.deleteProperty(Element.prototype, 'scrollIntoView');
    }
  });
  it('404는 찾을 수 없음으로 안내한다',async()=>{
    server.use(http.get(base+'/1',()=>new HttpResponse(null,{status:404}))); mount();
    expect(await screen.findByRole('heading',{name:'장소를 찾을 수 없어요'})).toBeTruthy();
    expect(screen.queryByRole('button',{name:'장소 링크 복사'})).toBeNull();
  });
  it('서버 장애는 재시도 가능 오류이며 샘플 데이터로 대체하지 않는다',async()=>{
    server.use(http.get(base+'/1',()=>new HttpResponse(null,{status:500}))); mount();
    expect(await screen.findByRole('alert')).toBeTruthy(); expect(screen.getByRole('button',{name:'다시 시도'})).toBeTruthy();
    expect(screen.queryByRole('heading',{name:'DB 장소'})).toBeNull();
  });
  it('미확인 정보를 꾸미지 않고 위험한 링크는 렌더링하지 않는다',async()=>{
    server.use(http.get(base+'/1',()=>HttpResponse.json({...body,address:null,latitude:null,longitude:null,homepage:'javascript:alert(1)',activities:[],notices:[],sources:[]})));
    mount(); await screen.findByRole('heading',{name:'DB 장소'});
    expect(screen.getByText('등록된 활동 정보가 없습니다.')).toBeTruthy(); expect(screen.getByText('확인된 출처가 없습니다.')).toBeTruthy();
    expect(screen.queryByRole('link',{name:'공식 안내 열기 ↗'})).toBeNull(); expect(screen.queryByRole('link',{name:'카카오맵 길찾기 ↗'})).toBeNull();
  });
  it.each(['','?id=999oops','?id=0','?id=1&id=2'])('잘못된 ID는 요청 없이 안내한다: %s',query=>{
    window.history.replaceState(null,'','/places/detail/'+query); mount(); expect(screen.getByRole('heading',{name:'장소를 찾을 수 없어요'})).toBeTruthy();
  });
  it('스노클링 입수 상태가 미확인이면 추천하지 않고 공통 주의사항을 구분해 보여 준다', async () => {
    server.use(
      http.get(base + '/1', () => HttpResponse.json({ ...body,
        activities: [{ placeActivityId: 11, activityTypeId: 21, activityTypeName: '스노클링',
          environmentType: 'OUTDOOR', seasons: ['SUMMER'] }],
        verifiedDetails: { prices: [], activityDetails: {}, operating: {} },
      })),
      http.get(base + '/1/snorkeling-safety', () => HttpResponse.json({
        placeId: 1, siteType: 'UNKNOWN', entryStatus: 'UNKNOWN', recommendable: false,
        verifiedSafetyDetails: null, commonPrecautions: ['구명조끼 착용 권장'],
        confirmedWarnings: [], sourceUrl: null, checkedAt: null, verifiedAt: null,
      }))
    );
    mount();
    expect(await screen.findByText('입수 가능 여부 미확인')).toBeTruthy();
    expect(screen.getByText(/현재 스노클링 방문 추천 대상이 아닙니다/)).toBeTruthy();
    expect(screen.getByText(/공통 안전 수칙이며/)).toBeTruthy();
    expect(screen.getByText(/가격 미확인은 무료를 뜻하지 않습니다/)).toBeTruthy();
  });
});
