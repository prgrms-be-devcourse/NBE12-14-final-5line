'use client';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { Search, Snowflake, Waves, Fish, Umbrella, Flag, Mountain, Footprints, ArrowUpRight } from 'lucide-react';
import { getPlaceCatalog, getPlacePage } from '@/domains/place/api/placeApi';
import PlaceExplorer from './PlaceExplorer/PlaceExplorer';
const activities = [ ['아이스스케이팅',Footprints],['해수욕장',Umbrella],['스노클링',Fish],['워터파크',Waves],['루지·레일바이크·중력 레이싱',Flag],['스키',Mountain],['눈썰매',Snowflake] ] as const;
const href=(name:string)=>`/explore/?category=${encodeURIComponent(name)}`;
function HeroSection({image}:{image?:string|null}) { return <section className="relative overflow-hidden rounded-2xl bg-[#211e38] text-white">
  {image && <img src={image} alt="겨울 액티비티 풍경" className="absolute inset-0 h-full w-full object-cover"/>}
  <div className="relative flex min-h-[400px] flex-col justify-end bg-black/35 px-6 py-9 sm:px-12 sm:py-12">
    <p className="text-xs tracking-[.2em] text-white/80">EXPLORE THE OUTSIDE</p><h1 className="mt-4 text-4xl font-bold leading-tight sm:text-5xl">이번 주말,<br/>밖에서 뭐 하지?</h1><p className="mt-4 text-sm text-white/85">마음이 향하는 활동, 나에게 맞는 장소를 찾아보세요.</p>
    <form action="/explore/" className="mt-7 flex max-w-2xl items-center gap-2 rounded-xl bg-white p-2 text-[#17172B]"><Search className="ml-2 shrink-0" size={20}/><input aria-label="장소명 또는 키워드" name="query" placeholder="어떤 장소를 찾고 있나요?" className="min-w-0 flex-1 p-2 outline-none"/><button className="shrink-0 rounded-lg bg-[#5942FF] px-4 py-3 text-sm font-semibold text-white">탐색하기</button></form></div></section>; }
function Footer(){return <footer className="mt-16 flex flex-wrap justify-between gap-6 border-t border-slate-200 py-9 text-sm text-slate-500"><div><Link href="/" className="text-xl font-bold text-[#5942FF]">OUTPICK</Link><p className="mt-2">밖으로, 더 멀리</p></div><Link href="/privacy/">개인정보처리방침</Link></footer>}
export default function HomePage(){
  const params=useSearchParams();
  const catalog=useQuery({queryKey:['home-catalog'],queryFn:()=>getPlaceCatalog()});
  const photos=useQuery({queryKey:['home-seasonal-photos'],queryFn:()=>getPlacePage(new URLSearchParams({size:'50',page:'0',sort:'name',activityTypeIds:(catalog.data?.categories.find(c=>c.name==='스키')?.activityTypeIds[0]??'').toString()})),enabled:!!catalog.data?.categories.find(c=>c.name==='스키')?.activityTypeIds.length});
  if(params.size>0)return <PlaceExplorer/>;
  const winter=['스키','아이스스케이팅','눈썰매'];
  const summer=['해수욕장','워터파크'];
  const seasonal=[5,6,7,8].includes(new Date().getMonth())?summer:winter;
  const image=photos.data?.items.find(p=>p.representativeImage)?.representativeImage;
  return <main className="mx-auto w-full max-w-[1200px] px-5 pt-6 text-[#17172B] sm:px-8"><HeroSection image={image}/>
    <section className="py-14"><h2 className="text-xl font-bold sm:text-2xl">어떤 활동을 즐기고 싶으세요?</h2>
      {catalog.isError?<p role="alert" className="mt-5 text-sm">카테고리를 불러오지 못했어요. <button onClick={()=>void catalog.refetch()} className="underline">다시 시도</button></p>:<div className="mt-7 grid grid-cols-3 gap-x-3 gap-y-7 sm:grid-cols-7">{activities.map(([name,Icon])=><Link key={name} href={href(name)} className="group flex min-w-0 flex-col items-center text-center"><span className="rounded-2xl bg-[#F3F0FF] p-5 text-[#5942FF] group-hover:bg-[#e8e2ff]"><Icon size={27} strokeWidth={1.6}/></span><span className="mt-3 text-xs font-medium leading-5">{name.replace(' 레이싱','레이싱')}</span>{catalog.data&&<span className="mt-1 text-xs text-slate-400">{catalog.data.categories.find(c=>c.name===name)?.publishedPlaceCount??0}곳</span>}</Link>)}</div>}</section>
    <section className="border-t border-slate-100 py-12"><div className="flex items-center justify-between gap-4"><div><h2 className="text-xl font-bold sm:text-2xl">지금 즐기기 좋은 액티비티</h2><p className="mt-2 text-sm text-slate-500">계절에 맞는 새로운 즐거움을 찾아보세요.</p></div><Link href="/explore/" className="shrink-0 text-sm text-[#5942FF]">전체 탐색 ↗</Link></div><div className="mt-7 grid gap-6 sm:grid-cols-3">{seasonal.map((name,i)=><SeasonCard key={name} name={name} title={['설원 위의 즐거움','특별한 겨울 데이트','신나는 겨울 나들이'][i]} ids={catalog.data?.categories.find(c=>c.name===name)?.activityTypeIds}/>)}</div><p className="mt-5 text-xs text-slate-400">계절별 활동을 소개합니다. 장소별 운영 일정과 이용 조건은 상세 안내를 확인하세요.</p></section><Footer/></main>;
}
function SeasonCard({name,title,ids}:{name:string;title:string;ids?:number[]}){
  const result=useQuery({queryKey:['home-category-image',name,ids],enabled:!!ids?.length,queryFn:()=>{const p=new URLSearchParams({size:'30',page:'0',sort:'name'});ids?.forEach(id=>p.append('activityTypeIds',String(id)));return getPlacePage(p);}});
  const image=result.data?.items.find(p=>p.representativeImage)?.representativeImage;
  return <Link href={href(name)} className="group min-w-0"><div className="aspect-[4/3] overflow-hidden rounded-2xl bg-[#F5F6F8]">{image?<img src={image} alt={`${name} 활동 사진`} className="h-full w-full object-cover"/>:<div className="flex h-full items-center justify-center text-[#5942FF]"><Snowflake size={48} strokeWidth={1}/></div>}</div><div className="mt-4 flex justify-between"><h3 className="font-semibold">{title}</h3><ArrowUpRight size={18}/></div><p className="mt-1 text-sm text-slate-500">{name}</p></Link>;
}
