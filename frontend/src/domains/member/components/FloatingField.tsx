// Lucian UX/UI Prompt #025 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#025
'use client';

import { useId, useState } from 'react';
import type { CSSProperties } from 'react';

type Props = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'email' | 'password';
  autoComplete?: string;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  minLength?: number;
  maxLength?: number;
  autoFocus?: boolean;
};

export default function FloatingField({ label, value, onChange, type = 'text', autoComplete, error, required = true, disabled = false, minLength, maxLength, autoFocus: autoFocus }: Props) {
  const id = useId();
  const [focused, setFocused] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const up = focused || value.length > 0;
  const validEmail = type === 'email' && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value);
  return (
    <div className={`u025-f${up ? ' is-up' : ''}${focused ? ' is-focus' : ''}${error ? ' is-bad' : ''}${validEmail ? ' is-ok' : ''}`}>
      <div className="u025-box">
        <input id={id} className="u025-in" type={type === 'password' && showPassword ? 'text' : type}
          value={value} onChange={event => onChange(event.target.value)} onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)} autoComplete={autoComplete} autoFocus={autoFocus} required={required} disabled={disabled} minLength={minLength} maxLength={maxLength} aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined} />
        <span className="u025-line" aria-hidden="true" style={{ '--gap': up ? `${label.length * 9 + 6}px` : '0px' } as CSSProperties}><i className="l" /><i className="g" /><i className="r" /></span>
        <label className="u025-lab" htmlFor={id}>{label}</label>
        {validEmail && <span className="u025-ok" aria-hidden="true"><svg viewBox="0 0 14 14"><path d="M3 7.3l2.7 2.7L11 4.5" /></svg></span>}
        {type === 'password' && <button className="absolute top-1/2 right-3 z-[2] -translate-y-1/2 cursor-pointer border-0 bg-transparent text-[12px] font-bold text-[#555ed5]" type="button" disabled={disabled} aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
          aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}>{showPassword ? '숨기기' : '보기'}</button>}
      </div>
      {error && <div className="u025-help"><span id={`${id}-error`} aria-live="polite">{error}</span></div>}
    </div>
  );
}
