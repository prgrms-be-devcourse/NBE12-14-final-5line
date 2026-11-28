'use client';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/lib/http/apiClient';
import type { PlaceOperatingInfoData } from '@/domains/place/types/placeApi.type';
import CategoryPlaceDetails, { type PlaceEvidence as Evidence } from './CategoryPlaceDetails';
export default function ResearchEvidencePanel({placeId,operatingInfo,activities=[]}:{placeId:number;hideEmpty?:boolean;operatingInfo?:PlaceOperatingInfoData|null;activities?:string[]}){
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


    add('공식 안내 대조일',operatingInfo.comparedAt?.slice(0,10));
  }
  if(evidence.isSuccess && items.length===0)return null;
  return <section id="research-evidence" className="my-7 space-y-3" aria-labelledby="research-heading"><h2 id="research-heading" className="text-lg font-bold">방문 정보</h2><p className="mt-2 text-xs leading-6 text-slate-500">요금과 운영 일정은 적용 기간과 이용 조건에 따라 달라질 수 있어요. 방문 전 원문 안내를 확인해 주세요.</p>
    {evidence.isPending&&<div role="status" aria-label="조사 자료 로딩 중" className="mt-4 space-y-3"><div className="h-4 w-2/3 rounded bg-slate-100"/><div className="h-4 w-1/2 rounded bg-slate-100"/><span className="sr-only">자료를 불러오고 있어요.</span></div>}
    {evidence.isError&&<p role="alert" className="mt-4 text-sm">조사 자료를 불러오지 못했어요. <button className="underline" onClick={()=>void evidence.refetch()}>다시 시도</button></p>}
    {evidence.isSuccess && items.length===0&&<p className="mt-4 text-sm text-slate-500">연결된 요금·운영 조사 자료가 없습니다.</p>}
    <div className="mt-5"><CategoryPlaceDetails activities={activities} items={items}/></div></section>;
}
