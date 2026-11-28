// @vitest-environment jsdom
import '../../../../../tests/setup-dom';
import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MobilePlaceFilters from './MobilePlaceFilters';
import KakaoDirectionsButton from './KakaoDirectionsButton';
import PlaceCardSkeleton from './PlaceCardSkeleton';

beforeEach(()=>{
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function(this:HTMLDialogElement){this.setAttribute('open','');} });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function(this:HTMLDialogElement){this.removeAttribute('open');} });
});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
const filters={region:'',season:'',environment:''};
function mountFilters(onApply=vi.fn()) {
  render(<MobilePlaceFilters filters={filters} regions={[{id:1,name:'강원'}]} seasons={['겨울','여름']} onApply={onApply}/>);
  return onApply;
}
describe('탐색 편의 기능',()=>{
  it('필터는 적용 전까지 검색에 반영하지 않고 취소한 입력은 다시 열 때 초기화한다',async()=>{
    const user=userEvent.setup();const apply=mountFilters();
    await user.click(screen.getByRole('button',{name:'필터'}));
    await user.selectOptions(within(screen.getByRole('dialog')).getByRole('combobox',{name:'지역'}),'강원');
    expect(apply).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button',{name:'필터 닫기'}));
    await user.click(screen.getByRole('button',{name:'필터'}));
    expect((within(screen.getByRole('dialog')).getByRole('combobox',{name:'지역'}) as HTMLSelectElement).value).toBe('');
  });
  it('지역·시즌·실내외를 한 번에 적용하고 창을 닫는다',async()=>{
    const user=userEvent.setup();const apply=mountFilters();await user.click(screen.getByRole('button',{name:'필터'}));
    const panel=within(screen.getByRole('dialog'));
    await user.selectOptions(panel.getByRole('combobox',{name:'지역'}),'강원');
    await user.selectOptions(panel.getByRole('combobox',{name:'시즌'}),'겨울');
    await user.selectOptions(panel.getByRole('combobox',{name:'실내외'}),'실외');
    await user.click(panel.getByRole('button',{name:'필터 적용'}));
    expect(apply).toHaveBeenCalledExactlyOnceWith({region:'강원',season:'겨울',environment:'실외'});
    expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('선택한 조건 하나만 해제하고 나머지는 유지한다',async()=>{
    const user=userEvent.setup();const apply=vi.fn();
    render(<MobilePlaceFilters filters={{region:'강원',season:'겨울',environment:''}} regions={[]} seasons={['겨울']} onApply={apply}/>);
    await user.click(screen.getByRole('button',{name:'강원 필터 해제'}));
    expect(apply).toHaveBeenCalledWith({region:'',season:'겨울',environment:''});
  });
  it('키보드로 길찾기 설명을 확인하고 Escape로 닫을 수 있다',async()=>{
    const user=userEvent.setup();render(<KakaoDirectionsButton place={{name:'시험 장소',latitude:37,longitude:127}}/>);
    await user.tab();const link=screen.getByRole('link',{name:'시험 장소 카카오맵 길찾기 (새 창)'});
    expect(document.activeElement).toBe(link);expect(link.getAttribute('aria-describedby')).toBe(screen.getByRole('tooltip').id);
    await user.keyboard('{Escape}');expect(screen.queryByRole('tooltip')).toBeNull();
    expect(link.getAttribute('href')).toBe(`https://map.kakao.com/link/to/${encodeURIComponent('시험 장소')},37,127`);
  });
  it('좌표가 없으면 길찾기 버튼을 만들지 않는다',()=>{
    render(<KakaoDirectionsButton place={{name:'시험 장소',latitude:null,longitude:null}}/>);expect(screen.queryByRole('link')).toBeNull();
  });
  it('로딩 카드를 장소나 클릭 가능한 가짜 결과로 표시하지 않는다',()=>{
    render(<PlaceCardSkeleton/>);expect(screen.getByRole('status',{name:'장소 정보 로딩 중'})).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();expect(document.querySelector('[data-place-id]')).toBeNull();
  });
});
