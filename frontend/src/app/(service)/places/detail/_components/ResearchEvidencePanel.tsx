"use client";
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/lib/http/apiClient';
import { safeHttpUrl } from '@/domains/place/lib/placeLinks';
import type { PlaceOperatingInfoData } from '@/domains/place/types/placeApi.type';
type Evidence={label:string;text:string;sourceUrl:string;checkedAt:string;status:string};
export default function ResearchEvidencePanel({placeId,hideEmpty=false,operatingInfo}:{placeId:number;hideEmpty?:boolean;operatingInfo?:PlaceOperatingInfoData|null}){
 const evidence=useQuery({queryKey:['place-research-details',placeId],queryFn:async()=> (await apiClient.get<Evidence[]>(`/api/v1/places/${placeId}/research-details`)).data});
 const items:Evidence[]=[...(evidence.data ?? [])];
 const normalize=(value:string)=>value.replace(/<[^>]*>/g,' ').replace(/&nbsp;/g,' ').replace(/[\s·,，:：–—-]+/g,'').trim();
 function add(label:string,text:string|null|undefined){
  if(!text?.trim())return;
  const key=normalize(text);
  if(items.some(item=>normalize(item.text)===key))return;
  items.push({label,text,sourceUrl:operatingInfo?.sourceUrl??'',checkedAt:'',status:operatingInfo?.verificationStatus??'UNVERIFIED'});
 }
 if(operatingInfo){
  add('운영시간',operatingInfo.operatingHours);add('휴무일',operatingInfo.restDays);
  add('이용요금 원문',operatingInfo.feeInfo);add('문의전화',operatingInfo.contactPhone);
  add('수집일',operatingInfo.collectedAt?.replace('T',' ')+' (한국 시간)');
  add('검수일',operatingInfo.verifiedAt?.replace('T',' ')??'검수 완료 전');
  add('공식 안내 대조일',operatingInfo.comparedAt?.slice(0,10));
 }
 if(hideEmpty && evidence.isSuccess && items.length===0)return null;
 return <section id="research-evidence" className="my-7 rounded-2xl border border-[#e5e3ef] p-5 sm:p-6" aria-labelledby="research-heading"><h2 id="research-heading" className="text-lg font-bold">요금·운영 안내</h2><p className="mt-2 text-xs leading-6 text-slate-500">기존 조사 자료입니다. 현재 유효성과 적용 조건은 검수 대기 상태이며, 현재 판매가격이나 이용 가능 여부를 확정하지 않습니다.</p>
 {evidence.isPending&&<div role="status" aria-label="조사 자료 로딩 중" className="mt-4 space-y-3"><div className="h-4 w-2/3 rounded bg-slate-100"/><div className="h-4 w-1/2 rounded bg-slate-100"/><span className="sr-only">자료를 불러오고 있어요.</span></div>}
 {evidence.isError&&<p role="alert" className="mt-4 text-sm">조사 자료를 불러오지 못했어요. <button className="underline" onClick={()=>void evidence.refetch()}>다시 시도</button></p>}
 {evidence.isSuccess && items.length===0&&<p className="mt-4 text-sm text-slate-500">연결된 요금·운영 조사 자료가 없습니다.</p>}
 <details className="group mt-4 rounded-xl bg-[#F5F6F8] px-4" open><summary className="cursor-pointer py-4 text-sm font-semibold text-[#17172B] focus-visible:outline-2 focus-visible:outline-[#5942FF]">조사 자료 {items.length}건 · 출처 확인</summary><dl className="divide-y divide-slate-200">{items.map((item,index)=>{const url=safeHttpUrl(item.sourceUrl);return <div key={`${item.label}-${index}`} className="grid gap-2 py-4 sm:grid-cols-[145px_1fr]"><dt className="text-sm font-semibold">{item.label}<span className="mt-1 block text-[10px] font-normal text-amber-700">{item.status==='VERIFIED'?'검수 완료':'검수 대기'}</span></dt><dd className="min-w-0 break-words text-sm leading-7 text-slate-600"><p className="whitespace-pre-line">{item.text}</p><p className="mt-1 text-xs text-slate-400">{item.checkedAt && <>조사일: {item.checkedAt.slice(0,10)}</>} {url&&<a href={url} target="_blank" rel="noopener noreferrer" className="ml-2 text-[#5942FF] underline">원문 출처 ↗</a>}</p></dd></div>})}</dl></details></section>;
}
