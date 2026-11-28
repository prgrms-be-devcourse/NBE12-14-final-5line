// @vitest-environment jsdom
import '../../../tests/setup-dom';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import ConfirmDialog from './ConfirmDialog';
afterEach(() => vi.restoreAllMocks());
it('삭제 확인은 취소에 초점을 두고 명시적 확인 전에는 삭제하지 않는다', async () => {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable:true,value:function(this:HTMLDialogElement) { this.open=true; } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable:true,value:function(this:HTMLDialogElement) { this.open=false; } });
  const onConfirm=vi.fn(), onCancel=vi.fn(), user=userEvent.setup();
  render(<ConfirmDialog open busy={false} onConfirm={onConfirm} onCancel={onCancel}/>);
  expect(document.activeElement).toBe(screen.getByRole('button',{name:'취소'}));
  expect(onConfirm).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button',{name:'취소'}));
  expect(onCancel).toHaveBeenCalledOnce();
  await user.click(screen.getByRole('button',{name:'삭제 확인'}));
  expect(onConfirm).toHaveBeenCalledOnce();
});
