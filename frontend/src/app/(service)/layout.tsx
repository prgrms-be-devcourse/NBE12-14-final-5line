import type { ReactNode } from 'react';
import ServiceHeader from './_components/ServiceHeader';

export default function ServiceLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-white text-[#16243b] [--service-header-height:85px] min-[1101px]:[--service-header-height:93px]
        md:has-[>.outpick-explorer]:grid md:has-[>.outpick-explorer]:h-dvh
        md:has-[>.outpick-explorer]:min-h-0 md:has-[>.outpick-explorer]:grid-rows-[auto_minmax(0,1fr)]">
      <ServiceHeader />
      {children}
    </div>
  );
}
