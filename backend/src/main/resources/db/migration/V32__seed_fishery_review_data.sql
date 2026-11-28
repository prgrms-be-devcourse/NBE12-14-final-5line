-- Local source PDFs dated 2026-01-01; a newer decree exists. All rows remain NEEDS_REVIEW.
INSERT INTO fishery_resource (name,scientific_name,category) VALUES
('꽃게','Portunus trituberculatus','CRUSTACEAN'),('대게류(붉은대게 제외)','Chionoecetes spp.','CRUSTACEAN'),('붉은대게','Chionoecetes japonicus','CRUSTACEAN'),('대하','Penaeus orientalis','CRUSTACEAN'),
('새조개','Fulvia mutica','SHELLFISH'),('소라','Batillus cornutus','SHELLFISH'),('전복류','Haliotis spp.','SHELLFISH'),('코끼리조개','Panopea japonica','SHELLFISH'),('키조개','Atrina pectinata','SHELLFISH'),('가리비','Patinopecten yessoensis','SHELLFISH'),('오분자기','Sulculus diversicolor','SHELLFISH'),('마대오분자기','Sulculus diversicolor diversicolor','SHELLFISH'),('기수재첩','Corbicula japonica','SHELLFISH'),
('참문어','Octopus vulgaris','CEPHALOPOD'),('대문어','Octopus dofleini','CEPHALOPOD'),('낙지','Octopus minor','CEPHALOPOD'),('주꾸미','Amphioctopus fangsiao','CEPHALOPOD'),('살오징어','Todarodes pacificus','CEPHALOPOD'),
('넓미역','Undariopsis peterseniana','SEAWEED'),('우뭇가사리','Gelidium amansii','SEAWEED'),('톳','Hizikia fusiformis','SEAWEED'),('해삼','Stichopus japonicus','OTHER'),('성게',NULL,'OTHER');
INSERT INTO fishery_protection_rule (resource_id,rule_kind,start_month,start_day,end_month,end_day,crosses_year,region_name,excluded_region,area_description,applicability,exception_text,legal_basis,notice_number,source_basis_date,source_document,source_excerpt,official_url)
SELECT resource_id,'CLOSED_SEASON',sm,sd,em,ed,cy,region,excluded,area,scope,exception_text,basis,notice,DATE('2026-01-01'),document,excerpt,'https://www.law.go.kr/법령/수산자원관리법시행령'
FROM fishery_resource JOIN (
 SELECT '대게류(붉은대게 제외)' n,6 sm,1 sd,11 em,30 ed,0 cy,'전국' region,NULL excluded,NULL area,'GENERAL' scope,'동경 131도 30분 이동수역 등 예외는 별도 확인' exception_text,'시행령 제6조제1항 별표 1' basis,NULL notice,'별표 1(2026.1.1)' document,'대게류(붉은대게 제외): 6월 1일~11월 30일' excerpt UNION ALL
 SELECT '붉은대게',7,10,8,25,0,'전국',NULL,NULL,'GENERAL','강원 연안자망어업 예외는 비어업인에게 자동 적용하지 않음','시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','붉은대게: 7월 10일~8월 25일' UNION ALL
 SELECT '대하',5,1,6,30,0,'전국',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','대하: 5월 1일~6월 30일' UNION ALL
 SELECT '새조개',6,16,9,30,0,'전국','부산·울산·경남·전남 일부·제주',NULL,'GENERAL','일부 지역은 6월 1일 시작','시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','새조개: 6월 16일~9월 30일' UNION ALL
 SELECT '전복류',9,1,10,31,0,'전국','제주',NULL,'GENERAL','제주는 10월 1일~12월 31일','시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','전복류: 9월 1일~10월 31일' UNION ALL
 SELECT '키조개',7,1,8,31,0,'전국',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','키조개: 7월 1일~8월 31일' UNION ALL
 SELECT '참문어',5,16,6,30,0,'전국','경남·전남·제주',NULL,'GENERAL','시도 고시 확인 필요','시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','참문어: 5월 16일~6월 30일' UNION ALL
 SELECT '낙지',6,1,6,30,0,'전국','인천·경남·경기·충남 일부·전남',NULL,'GENERAL','시도 고시 확인 필요','시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','낙지: 6월 1일~6월 30일' UNION ALL
 SELECT '주꾸미',5,11,8,31,0,'전국',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','주꾸미: 5월 11일~8월 31일' UNION ALL
 SELECT '살오징어',4,1,5,31,0,'전국',NULL,NULL,'GENERAL','특정 어업의 예외는 비어업인에게 자동 적용하지 않음','시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','살오징어: 4월 1일~5월 31일' UNION ALL
 SELECT '해삼',7,1,7,31,0,'전국',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','해삼: 7월 1일~7월 31일' UNION ALL
 SELECT '우뭇가사리',11,1,3,31,1,'전국',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','우뭇가사리: 11월 1일~다음 해 3월 31일' UNION ALL
 SELECT '톳',10,1,1,31,1,'전국',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','톳: 10월 1일~다음 해 1월 31일' UNION ALL
 SELECT '넓미역',9,1,11,30,0,'제주',NULL,NULL,'GENERAL','별도 도지사 고시 가능','시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','넓미역: 제주 9월 1일~11월 30일' UNION ALL
 SELECT '오분자기',7,1,8,31,0,'제주',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','오분자기: 제주 7월 1일~8월 31일' UNION ALL
 SELECT '코끼리조개',5,1,6,30,0,'강원·경북',NULL,NULL,'GENERAL',NULL,'시행령 제6조제1항 별표 1',NULL,'별표 1(2026.1.1)','코끼리조개: 강원·경북 5월 1일~6월 30일' UNION ALL
 SELECT '가리비',3,1,6,30,0,'지정 해역',NULL,'별표 1 주7의 좌표 해역. 공간 경계 미검증','GENERAL',NULL,'시행령 제6조제1항 별표 1 주7',NULL,'별표 1(2026.1.1)','가리비: 지정 해역 3월 1일~6월 30일'
) x ON x.n=fishery_resource.name;

-- Region-specific notices are historical review candidates; no automatic legal conclusion.
INSERT INTO fishery_protection_rule (resource_id,rule_kind,start_month,start_day,end_month,end_day,region_name,area_description,legal_basis,notice_number,source_basis_date,source_document,source_excerpt)
SELECT resource_id,'CLOSED_SEASON',sm,sd,em,ed,region,area,'시도 별도 지정 금어기 현황',notice,DATE('2026-01-01'),'시도 별도 지정 금어기 현황(낙지·참문어)',excerpt
FROM fishery_resource JOIN (
 SELECT '낙지' n,6 sm,21 sd,7 em,20 ed,'인천' region,NULL area,'2016-109' notice,'인천 낙지 6월 21일~7월 20일' excerpt UNION ALL
 SELECT '낙지',6,16,7,31,'경남',NULL,'2016-424','경남 낙지 6월 16일~7월 31일' UNION ALL
 SELECT '낙지',6,21,7,20,'경기',NULL,'2017-26','경기 낙지 6월 21일~7월 20일' UNION ALL
 SELECT '낙지',4,1,5,31,'충남 가로림만','공간 경계 미검증','2016-131','충남 가로림만 낙지 4월 1일~5월 31일' UNION ALL
 SELECT '낙지',4,1,5,31,'충남 근소만','공간 경계 미검증','2017-403','충남 근소만 낙지 4월 1일~5월 31일' UNION ALL
 SELECT '낙지',6,21,7,20,'전남',NULL,'2016-176','전남 낙지 6월 21일~7월 20일' UNION ALL
 SELECT '참문어',5,24,7,8,'경남',NULL,'2021-220','경남 참문어 5월 24일~7월 8일' UNION ALL
 SELECT '참문어',5,24,7,8,'전남',NULL,'2021-228','전남 참문어 5월 24일~7월 8일' UNION ALL
 SELECT '참문어',8,1,9,15,'제주',NULL,'2021-97','제주 참문어 8월 1일~9월 15일'
) x ON x.n=fishery_resource.name;
INSERT INTO fishery_protection_rule (resource_id,rule_kind,threshold_value,threshold_unit,measurement_method,compare_operator,region_name,excluded_region,legal_basis,source_basis_date,source_document,source_excerpt)
SELECT resource_id,kind,amount,unit,measure,'<=',region,excluded,'시행령 제6조제2항 별표 2',DATE('2026-01-01'),'별표 2(2026.1.1)',excerpt
FROM fishery_resource JOIN (
 SELECT '꽃게' n,'MIN_SIZE' kind,6.4 amount,'cm' unit,'두흉갑장' measure,'전국' region,NULL excluded,'꽃게 두흉갑장 6.4cm 이하' excerpt UNION ALL
 SELECT '대게류(붉은대게 제외)','MIN_SIZE',9,'cm','두흉갑장','전국',NULL,'대게에 한해 두흉갑장 9cm 이하; 대게류 전체에 자동 적용 금지' UNION ALL
 SELECT '소라','MIN_SIZE',5,'cm','각고','전국','제주·울릉도·독도','소라 각고 5cm 이하' UNION ALL
 SELECT '소라','MIN_SIZE',7,'cm','각고','제주',NULL,'제주산 소라 각고 7cm 이하' UNION ALL
 SELECT '소라','MIN_SIZE',7,'cm','각고','울릉도·독도',NULL,'울릉도·독도산 소라 각고 7cm 이하' UNION ALL
 SELECT '전복류','MIN_SIZE',7,'cm','각장','전국','제주','전복류 각장 7cm 이하' UNION ALL
 SELECT '전복류','MIN_SIZE',10,'cm','각장','제주',NULL,'제주산 전복류 각장 10cm 이하' UNION ALL
 SELECT '키조개','MIN_SIZE',18,'cm','각장','부산·울산·강원·경북·경남',NULL,'해당 지역산 키조개 각장 18cm 이하' UNION ALL
 SELECT '오분자기','MIN_SIZE',4,'cm','각장','제주',NULL,'제주산 오분자기 각장 4cm 이하' UNION ALL
 SELECT '마대오분자기','MIN_SIZE',4,'cm','각장','제주',NULL,'제주산 마대오분자기 각장 4cm 이하' UNION ALL
 SELECT '기수재첩','MIN_SIZE',1.5,'cm','각장','전국',NULL,'기수재첩 각장 1.5cm 이하' UNION ALL
 SELECT '살오징어','MIN_SIZE',15,'cm','외투장','전국',NULL,'살오징어 외투장 15cm 이하; 어획량 예외는 비어업인에게 자동 적용 금지' UNION ALL
 SELECT '대문어','MIN_WEIGHT',600,'g','체중','전국',NULL,'대문어 600g 이하; 참문어에 적용 금지'
) x ON x.n=fishery_resource.name;

INSERT INTO fishery_protection_rule (resource_id,rule_kind,start_month,start_day,end_month,end_day,region_name,excluded_region,area_description,legal_basis,source_basis_date,source_document,source_excerpt)
SELECT resource_id,'CLOSED_SEASON',sm,sd,em,ed,region,excluded,area,'시행령 제6조제1항 별표 1',DATE('2026-01-01'),'별표 1(2026.1.1)',excerpt
FROM fishery_resource JOIN (
 SELECT '소라' n,6 sm,1 sd,8 em,31 ed,'제주' region,'추자도' excluded,NULL area,'제주(추자도 제외) 소라 6월 1일~8월 31일' excerpt UNION ALL
 SELECT '소라',7,1,8,31,'제주 추자도',NULL,NULL,'추자도 소라 7월 1일~8월 31일' UNION ALL
 SELECT '소라',6,1,7,31,'경북 울릉도·독도',NULL,NULL,'울릉도·독도 소라 6월 1일~7월 31일' UNION ALL
 SELECT '소라',6,1,6,30,'전남 여수시 삼산면',NULL,NULL,'여수시 삼산면 소라 6월 1일~6월 30일' UNION ALL
 SELECT '전복류',10,1,12,31,'제주',NULL,NULL,'제주 전복류 10월 1일~12월 31일' UNION ALL
 SELECT '새조개',6,1,9,30,'부산·울산·경남·전남·제주','전남 무안군·영광군',NULL,'지역별 새조개 6월 1일~9월 30일' UNION ALL
 SELECT '대게류(붉은대게 제외)',6,1,10,31,'동경 131도 30분 이동수역',NULL,'공간 경계 미검증','대게류 이동수역 6월 1일~10월 31일' UNION ALL
 SELECT '대게류(붉은대게 제외)',4,1,7,20,'강원 고성군 현내면 지정 해역',NULL,'지경리·저진리 해안선 및 좌표; 공간 경계 미검증','고성군 지정 해역 대게류 4월 1일~7월 20일' UNION ALL
 SELECT '대게류(붉은대게 제외)',10,1,11,30,'강원 고성군 현내면 지정 해역',NULL,'지경리·저진리 해안선 및 좌표; 공간 경계 미검증','고성군 지정 해역 대게류 10월 1일~11월 30일' UNION ALL
 SELECT '대게류(붉은대게 제외)',3,1,4,30,'별표 1 주5·주6 지정 해역',NULL,'원문 좌표 경계 미검증','별표 1 주5·주6 해역 대게류 3월 1일~4월 30일 추가 제한'
) x ON x.n=fishery_resource.name;

INSERT INTO fishery_protection_rule (resource_id,rule_kind,region_name,area_description,legal_basis,source_basis_date,source_document,source_excerpt)
SELECT resource_id,'UNRESOLVED',region,area,basis,DATE('2026-01-01'),document,excerpt FROM fishery_resource JOIN (
 SELECT '꽃게' n,'전국' region,NULL area,'시행령 제6조제1항 별표 1' basis,'별표 1(2026.1.1)' document,'꽃게 금어기는 해양수산부장관 별도 고시 확인 필요' excerpt UNION ALL
 SELECT '성게','전국',NULL,'첨부자료 미기재','사용자 제공 검수표','성게 금어기·금지체장·지역별 채취 허용 여부 UNKNOWN' UNION ALL
 SELECT '가리비','지정 해역','북위 36도04분10.74초 동경129도24분51.72초; 북위36도04분10.75초 동경129도34분51.66초; 북위36도09분10.71초 동경129도24분51.71초; 북위36도09분10.71초 동경129도34분51.66초','시행령 별표 1 주7','별표 1(2026.1.1)','가리비 해역 좌표 원문 순서 보존; Polygon 생성 금지' UNION ALL
 SELECT '대게류(붉은대게 제외)','별표 1 주5 해역','북위36도44분 동경129도48분; 북위36도44분 동경129도51분; 북위36도41분 동경129도48분; 북위36도41분 동경129도51분','시행령 별표 1 주5','별표 1(2026.1.1)','대게류 주5 해역 좌표 원문 순서 보존; Polygon 생성 금지' UNION ALL
 SELECT '대게류(붉은대게 제외)','별표 1 주6 해역','북위36도29분 동경129도48분; 북위36도29분 동경129도51분; 북위36도26분 동경129도48분; 북위36도26분 동경129도51분','시행령 별표 1 주6','별표 1(2026.1.1)','대게류 주6 해역 좌표 원문 순서 보존; Polygon 생성 금지'
) x ON x.n=fishery_resource.name;
