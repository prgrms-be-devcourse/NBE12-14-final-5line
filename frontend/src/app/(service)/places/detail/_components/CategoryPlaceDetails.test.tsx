// @vitest-environment jsdom
import '../../../../../../tests/setup-dom';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import CategoryPlaceDetails, { type PlaceEvidence } from './CategoryPlaceDetails';
const evidence = (label:string,text:string):PlaceEvidence => ({label,text,sourceUrl:'https://example.com/rules',checkedAt:'',status:'NEEDS_REVIEW'});
afterEach(cleanup);
describe('종목별 방문 정보',()=>{
  it.each([['스키','스키장 운영 안내'],['워터파크','워터파크 운영 안내'],['눈썰매','눈썰매장 운영 안내'],['아이스스케이팅','자유 스케이팅 및 정빙 시간'],['해수욕장','해수욕장 개장 기간 및 운영 안내']])('%s의 운영정보만 실제 값으로 표시한다',(name,title)=>{
    render(<CategoryPlaceDetails activities={[name]} items={[evidence('운영시간','10:00~18:00')]}/>);
    expect(screen.getByRole('heading',{name:title})).toBeTruthy();
    expect(screen.queryByRole('heading',{name:'주차 및 편의시설'})).toBeNull();
  });
  it('레일바이크 인승별·차량 요금 원문을 유지한다',()=>{
    render(<CategoryPlaceDetails activities={['레일바이크']} items={[evidence('차량·인원별 요금','2인승 차량 30,000원 / 4인승 차량 40,000원')]}/>);
    expect(screen.getByRole('heading',{name:'차량·탑승 인원별 요금'})).toBeTruthy();
    expect(screen.getByText('2인승 차량 30,000원 / 4인승 차량 40,000원')).toBeTruthy();
  });
  it.each(['해수욕장','스노클링'])('%s에는 가격 미확인과 요금표를 만들지 않는다',name=>{
    render(<CategoryPlaceDetails activities={[name]} items={[evidence('입장료','미확인')]}/>);
    expect(screen.queryByText('미확인')).toBeNull();
  });
  it('확인된 위험정보와 출처를 보존하고 검수 상태를 승격하지 않는다',()=>{
    render(<CategoryPlaceDetails activities={['스노클링']} items={[evidence('현장 위험정보','출입 금지 구역 포함')]}/>);
    expect(screen.getByText('출입 금지 구역 포함')).toBeTruthy();
    expect(screen.getByText('방문 전 현재 적용 여부를 확인해 주세요.')).toBeTruthy();
    expect(screen.getByRole('link',{name:'원문 안내 ↗'}).getAttribute('href')).toBe('https://example.com/rules');
  });
});
