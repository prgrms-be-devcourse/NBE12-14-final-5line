package com.outpick.domain.place.importer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit, read-only survey command. Never starts Spring or writes to the application DB. */
public final class OutpickTourApiSurvey {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String API = "https://apis.data.go.kr/B551011/KorService2/";
    private static final Set<String> SCOPE = Set.of("아이스스케이팅", "해수욕장", "워터파크", "루지·레일바이크·중력 레이싱", "스키", "눈썰매");
    private static final List<String> COMMON_FIELDS = List.of("시설명", "주소", "좌표", "대표 사진", "추가 사진", "시설 소개", "정상 이용요금", "장비 대여료", "운영시간", "휴무일", "개장일", "폐장일", "이용 제한", "필수 준비물", "예약 여부", "편의시설", "공식 홈페이지", "전화번호");
    private static final Map<String,List<String>> SPECIAL_FIELDS = Map.of(
        "아이스스케이팅", List.of("자유스케이팅 시간", "연령별 요금", "대여료", "정빙 시간", "실내·실외"),
        "해수욕장", List.of("개폐장 일정", "입수 통제", "안전요원", "편의시설 이용료"),
        "워터파크", List.of("시즌별 정상 요금", "이용권 종류", "파도풀", "유수풀", "슬라이드", "신장 제한"),
        "루지·레일바이크·중력 레이싱", List.of("횟수별 가격", "인원별 가격", "회차별 출발시간", "코스 길이", "전동 보조", "주행시간", "탑승 제한", "안전장비"),
        "스키", List.of("리프트권", "장비 대여료", "슬로프", "난이도", "야간 운영"),
        "눈썰매", List.of("입장료", "썰매 대여료", "이용 제한", "무빙워크", "슬로프"));
    private final Path project = Path.of("").toAbsolutePath();
    private final Path out = Path.of(required("OUTPICK_SURVEY_DIR"));
    private final String key = firstNonBlank(System.getenv("PUBLIC_DATA_API_KEY"),System.getenv("TOUR_API_KEY"));
    private final int maxCalls = Integer.parseInt(System.getenv().getOrDefault("TOUR_API_MAX_REQUESTS","900"));
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final List<Map<String,Object>> failed = new ArrayList<>();
    private final Map<String,JsonNode> searchIndex = new LinkedHashMap<>();
    private int calls,successes,cacheHits;

    private static final class Place {
        String id,name,address,region,source,sourceId,contentId,contentTypeId,matchStatus="NOT_CHECKED";
        Double latitude,longitude;
        Set<String> categories=new LinkedHashSet<>();
        boolean dbRegistered;
        Map<String,Object> toMap() {
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("id",id);m.put("name",name);m.put("address",address);m.put("region",region);
            m.put("latitude",latitude);m.put("longitude",longitude);m.put("categories",categories);
            m.put("source",source);m.put("sourceId",sourceId);m.put("dbRegistered",dbRegistered);
            m.put("contentId",contentId);m.put("contentTypeId",contentTypeId);m.put("matchStatus",matchStatus);
            return m;
        }
    }
    private OutpickTourApiSurvey() { if (key==null || key.isBlank()) throw new IllegalArgumentException("TourAPI key environment variable is required"); }
    public static void main(String[] args) throws Exception { new OutpickTourApiSurvey().run(); }
    private void run() throws Exception {
        Files.createDirectories(out.resolve("cache"));
        Map<String,Place> places=loadDatabase();
        addCurated(places);
        addOfficialBeaches(places);
        loadSearchIndex();
        List<Place> targets=places.values().stream().filter(p->p.categories.stream().anyMatch(SCOPE::contains)).toList();
        Map<String,Object> raw=new LinkedHashMap<>();
        List<Map<String,Object>> missing=new ArrayList<>();
        int processed=0;
        for (Place p:targets) {
            Map<String,Object> responses=new LinkedHashMap<>();
            if (p.contentId==null) matchContent(p,responses);
            if (p.contentId!=null) {
                p.matchStatus="MATCHED";
                JsonNode common=request("detailCommon2",p.contentId,p.contentTypeId,1,null,p.id);
                responses.put("detailCommon2",common);
                if (common!=null) {
                    JsonNode item=firstItem(common);
                    if (item!=null && !item.isMissingNode()) p.contentTypeId=text(item,"contenttypeid");
                }
                if (p.contentTypeId!=null && !p.contentTypeId.isBlank()) {
                    responses.put("detailIntro2",request("detailIntro2",p.contentId,p.contentTypeId,1,null,p.id));
                    responses.put("detailInfo2",pages("detailInfo2",p));
                    responses.put("detailImage2",pages("detailImage2",p));
                } else recordFailure(p.id,"contentTypeId","NOT_RESOLVED");
            }
            raw.put(p.id,Map.of("contentId",p.contentId==null?"":p.contentId,"contentTypeId",p.contentTypeId==null?"":p.contentTypeId,"responses",responses));
            missing.add(missing(p,responses));
            processed++;
            if (processed%20==0) save(places,raw,missing);
        }
        save(places,raw,missing);
        System.out.printf("survey_targets=%d requests_success=%d requests_failed=%d cache_hits=%d%n",targets.size(),successes,failed.size(),cacheHits);
    }
    private Map<String,Place> loadDatabase() throws Exception {
        String database=required("MYSQL_DATABASE"),user=required("MYSQL_USER"),password=required("MYSQL_PASSWORD");
        String host=System.getenv().getOrDefault("MYSQL_HOST","127.0.0.1"),port=System.getenv().getOrDefault("MYSQL_PORT","3306");
        Map<String,Place> result=new LinkedHashMap<>();
        String sql="SELECT p.place_id,p.name,p.address,p.latitude,p.longitude,s.type,s.origin_id,c.name AS category "
            +"FROM place p LEFT JOIN source_record s ON s.place_id=p.place_id "
            +"LEFT JOIN place_activity a ON a.place_id=p.place_id LEFT JOIN activity_type t ON t.activity_type_id=a.activity_type_id "
            +"LEFT JOIN activity_category c ON c.activity_category_id=t.activity_category_id ORDER BY p.place_id";
        try(var connection=DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+database,user,password)) {
            connection.setReadOnly(true);
            try(var statement=connection.prepareStatement(sql);var rows=statement.executeQuery()) {
                while(rows.next()) {
                    String id="db:"+rows.getLong("place_id");
                    Place p=result.computeIfAbsent(id,unused->{Place v=new Place();v.id=id;v.dbRegistered=true;return v;});
                    p.name=rows.getString("name");p.address=rows.getString("address");
                    if(rows.getBigDecimal("latitude")!=null)p.latitude=rows.getDouble("latitude");
                    if(rows.getBigDecimal("longitude")!=null)p.longitude=rows.getDouble("longitude");
                    String source=rows.getString("type"),origin=rows.getString("origin_id");
                    if(source!=null && (p.source==null || "TOUR_API".equals(source))) {p.source=source;p.sourceId=origin;if("TOUR_API".equals(source))p.contentId=origin;}
                    if(rows.getString("category")!=null)p.categories.add(rows.getString("category"));
                }
            }
        }
        return result;
    }
    private void addCurated(Map<String,Place> places) throws IOException {
        Path file=out.resolve("inputs/curated-candidates.tsv");
        if(!Files.exists(file))return;
        for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
            String[] x=line.split("\\|",-1);if(x.length!=3)continue;
            String category=x[0].trim(),region=x[1].trim(),name=x[2].trim();
            if(category.equals("루지·레일바이크·중력 레이싱"))continue;
            Place found=places.values().stream().filter(p->p.categories.contains(category)&&normalize(p.name).equals(normalize(name))).findFirst().orElse(null);
            if(found!=null)continue;
            Place p=new Place();p.id="candidate:"+category+":"+normalize(name);p.name=name;p.region=region;p.source="USER_CANDIDATE";p.categories.add(category);places.putIfAbsent(p.id,p);
        }
    }
    private void addOfficialBeaches(Map<String,Place> places) throws IOException {
        Path file=Path.of(System.getenv().getOrDefault("OUTPICK_BEACH_CSV",System.getProperty("user.home")+"/Downloads/해양수산부_해수욕장정보목록_20210501.csv"));
        if(!Files.exists(file)) {recordFailure("beach-list","source-file","MISSING_2021_CSV");return;}
        for(String line:Files.readAllLines(file,Charset.forName("MS949")).stream().skip(1).toList()) {
            String[] x=line.split(",",-1);if(x.length<3)continue;
            String name=x[2].trim(),region=x[0].trim()+" "+x[1].trim();if(name.isBlank())continue;
            String beach=normalize(name).replace("해수욕장","").replace("해변","");
            boolean exists=places.values().stream().anyMatch(p->p.categories.contains("해수욕장")&&normalize(p.name).replace("해수욕장","").replace("해변","").equals(beach)&&regionMatches(region,p.address,p.region));
            if(exists)continue;
            Place p=new Place();p.id="beach:"+normalize(region)+":"+beach;p.name=name+"해수욕장";p.region=region;p.source="OFFICIAL_BEACH_LIST_2021";p.categories.add("해수욕장");places.putIfAbsent(p.id,p);
        }
    }
    private void loadSearchIndex() throws IOException {
        Path raw=project.resolve("data-collection/raw");if(!Files.isDirectory(raw))return;
        try(var paths=Files.walk(raw,2)) {
            for(Path path:paths.filter(p->p.getFileName().toString().startsWith("page-")&&p.toString().contains("tour-")&&p.toString().endsWith(".json")).toList()) {
                try {for(JsonNode item:items(JSON.readTree(Files.readString(path))))searchIndex.putIfAbsent(normalize(text(item,"title"))+":"+text(item,"contentid"),item);}
                catch(Exception ignored) { /* A broken old cache must not stop the new survey. */ }
            }
        }
    }
    private void matchContent(Place p,Map<String,Object> responses) {
        List<JsonNode> candidates=new ArrayList<>();
        for(JsonNode item:searchIndex.values())if(titleMatch(p.name,text(item,"title"),p.categories.contains("해수욕장")))candidates.add(item);
        if(candidates.isEmpty()) {
            JsonNode search=request("searchKeyword2",null,null,1,p.name,p.id);
            responses.put("searchKeyword2",search);
            if(search!=null)for(JsonNode item:items(search))if(titleMatch(p.name,text(item,"title"),p.categories.contains("해수욕장")))candidates.add(item);
        }
        List<JsonNode> matched=candidates.stream().filter(item->locationMatch(p,item)).toList();
        Map<String,JsonNode> unique=new LinkedHashMap<>();for(JsonNode item:matched)unique.put(text(item,"contentid"),item);
        if(unique.size()==1) {JsonNode item=unique.values().iterator().next();p.contentId=text(item,"contentid");p.contentTypeId=text(item,"contenttypeid");}
        else p.matchStatus=unique.isEmpty()?(candidates.isEmpty()?"NO_TOURAPI_MATCH":"LOCATION_UNCERTAIN"):"MULTIPLE_MATCHES_REVIEW";
    }
    private Object pages(String operation,Place p) {
        List<JsonNode> all=new ArrayList<>();
        for(int page=1;page<=5;page++) {
            JsonNode response=request(operation,p.contentId,p.contentTypeId,page,null,p.id);
            if(response==null)break;
            all.add(response);
            int total=response.path("response").path("body").path("totalCount").asInt(0);
            if(page*100>=total)break;
            if(page==5)recordFailure(p.id,operation,"PAGE_LIMIT");
        }
        return all;
    }
    private JsonNode request(String operation,String contentId,String type,int page,String keyword,String placeId) {
        String identity=keyword!=null?hash(keyword):contentId+(type==null?"":"-"+type);
        Path file=out.resolve("cache").resolve(operation).resolve(identity+"-"+page+".json");
        try {
            if(Files.exists(file)) {cacheHits++;return JSON.readTree(Files.readString(file));}
            if(operation.equals("detailCommon2")||operation.equals("detailIntro2")) {
                Path old=project.resolve("data-collection/raw/tour-details/"+contentId+(operation.equals("detailCommon2")?"-common":"-intro")+".json");
                if(Files.exists(old)) {cacheHits++;return JSON.readTree(Files.readString(old));}
            }
            if(calls>=maxCalls) {recordFailure(placeId,operation,"CALL_LIMIT");return null;}
            Map<String,String> params=new LinkedHashMap<>();params.put("serviceKey",key);params.put("MobileOS","ETC");params.put("MobileApp","OUTPICK");params.put("_type","json");params.put("numOfRows","100");params.put("pageNo",Integer.toString(page));
            if(keyword!=null)params.put("keyword",keyword);else {
                params.put("contentId",contentId);
                if(operation.equals("detailImage2")) params.put("imageYN","Y");
                else if(!operation.equals("detailCommon2")) params.put("contentTypeId",type);
            }
            String query=params.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).reduce((a,b)->a+"&"+b).orElseThrow();
            URI uri=URI.create(API+operation+"?"+query);
            for(int attempt=0;attempt<2;attempt++) {
                if(calls>=maxCalls)break;
                calls++;
                try {
                    HttpRequest req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("User-Agent","OUTPICK-survey/1.0").GET().build();
                    HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if(res.statusCode()!=200) {if(attempt==1)recordFailure(placeId,operation,"HTTP_"+res.statusCode());continue;}
                    String body=res.body().replace(key,"[REDACTED]");JsonNode parsed=JSON.readTree(body);
                    String code=parsed.path("response").path("header").path("resultCode").asString("");
                    if(code.isBlank())code=parsed.path("resultCode").asString("");
                    if(!code.equals("0000")&&!code.equals("00")) {recordFailure(placeId,operation,"API_"+code);return null;}
                    Files.createDirectories(file.getParent());Files.writeString(file,body,StandardCharsets.UTF_8);
                    successes++;Thread.sleep(200);return parsed;
                } catch(InterruptedException e) {Thread.currentThread().interrupt();recordFailure(placeId,operation,"INTERRUPTED");return null;}
                catch(Exception e) {if(attempt==1)recordFailure(placeId,operation,"REQUEST_ERROR_"+e.getClass().getSimpleName());}
            }
        } catch(Exception e) {recordFailure(placeId,operation,"CACHE_ERROR_"+e.getClass().getSimpleName());}
        return null;
    }
    private Map<String,Object> missing(Place p,Map<String,Object> responses) {
        JsonNode common=(JsonNode)responses.get("detailCommon2"),intro=(JsonNode)responses.get("detailIntro2");
        JsonNode c=firstItem(common),i=firstItem(intro);
        String info=joined(responses.get("detailInfo2")),images=joined(responses.get("detailImage2"));
        String facts=(i==null?"":i.toString())+info;
        Map<String,String> states=new LinkedHashMap<>();
        boolean failedCommon=p.contentId!=null&&common==null,failedIntro=p.contentId!=null&&intro==null;
        put(states,"시설명",has(c,"title"),failedCommon);put(states,"주소",has(c,"addr1"),failedCommon);put(states,"좌표",has(c,"mapx")&&has(c,"mapy"),failedCommon);
        put(states,"대표 사진",has(c,"firstimage"),failedCommon);put(states,"추가 사진",images.contains("imageurl")||images.contains("originimgurl"),p.contentId!=null&&responses.get("detailImage2")==null);
        put(states,"시설 소개",has(c,"overview"),failedCommon);
        put(states,"정상 이용요금",has(i,"usefee","usefeeleports","usefeeculture")||contains(info,"이용요금","입장료","리프트권"),failedIntro);
        put(states,"장비 대여료",contains(facts,"대여료","장비대여"),failedIntro);
        put(states,"운영시간",has(i,"usetime","usetimeleports","usetimeculture","opentimefood"),failedIntro);
        put(states,"휴무일",has(i,"restdate","restdateleports","restdateculture","restdatefood"),failedIntro);
        put(states,"개장일",has(i,"opendate","openperiod")||contains(info,"개장일","개장기간"),failedIntro);
        put(states,"폐장일",contains(facts,"폐장일","폐장기간"),failedIntro);
        put(states,"이용 제한",has(i,"expagerange","expagerangeleports")||contains(facts,"이용제한","탑승제한","신장제한"),failedIntro);
        put(states,"필수 준비물",contains(facts,"준비물","필수장비"),failedIntro);
        put(states,"예약 여부",has(i,"reservation")||contains(info,"예약"),failedIntro);
        put(states,"편의시설",has(i,"parking","parkingleports","chkbabycarriage")||contains(info,"샤워장","탈의실","주차장"),failedIntro);
        put(states,"공식 홈페이지",has(c,"homepage"),failedCommon);
        put(states,"전화번호",has(c,"tel")||has(i,"infocenter","infocenterleports","infocenterfood","infocenterculture"),failedCommon&&failedIntro);
        String price=firstText(i,"usefee","usefeeleports","usefeeculture");
        if(price.matches("(?i)^(무료|0\\s*원|free)$"))states.put("정상 이용요금","FREE");
        for(String category:p.categories)for(String field:SPECIAL_FIELDS.getOrDefault(category,List.of())) {
            String token=specialToken(field);put(states,category+" / "+field,contains(facts,token),failedIntro);
        }
        SPECIAL_FIELDS.forEach((category,fields)->{
            if(!p.categories.contains(category))for(String field:fields)states.put(category+" / "+field,"NOT_APPLICABLE");
        });
        if(p.contentId==null)states.replaceAll((k,v)->v.equals("NOT_APPLICABLE")?v:"UNMATCHED");
        List<String> absent=new ArrayList<>();states.forEach((k,v)->{if(v.equals("NOT_PROVIDED")||v.equals("REQUEST_FAILED")||v.equals("UNMATCHED"))absent.add(k);});
        Map<String,Object> result=new LinkedHashMap<>();result.put("placeId",p.id);result.put("name",p.name);result.put("categories",p.categories);result.put("contentId",p.contentId);result.put("matchStatus",p.matchStatus);result.put("fieldStatus",states);result.put("missing",absent);return result;
    }
    private void save(Map<String,Place> places,Map<String,Object> raw,List<Map<String,Object>> missing) throws IOException {
        List<Map<String,Object>> list=places.values().stream().map(Place::toMap).toList();
        write("places.json",list);write("raw_api_data.json",raw);write("missing_fields.json",missing);write("failed_requests.json",failed);
        Map<String,Map<String,int[]>> coverage=new LinkedHashMap<>();
        for(Map<String,Object> entry:missing) {
            @SuppressWarnings("unchecked") Set<String> cats=(Set<String>)entry.get("categories");
            @SuppressWarnings("unchecked") Map<String,String> statuses=(Map<String,String>)entry.get("fieldStatus");
            for(String cat:cats)for(var field:statuses.entrySet()) {
                if(field.getKey().contains(" / ")&&!field.getKey().startsWith(cat+" / "))continue;
                int[] n=coverage.computeIfAbsent(cat,k->new LinkedHashMap<>()).computeIfAbsent(field.getKey(),k->new int[4]);n[0]++;
                switch(field.getValue()) {case "PRESENT"->n[1]++;case "FREE"->n[2]++;case "REQUEST_FAILED"->n[3]++;default->{}}
            }
        }
        StringBuilder csv=new StringBuilder("category,field,total,present,free,request_failed,coverage_percent\n");
        coverage.forEach((cat,fields)->fields.forEach((field,n)->csv.append(quote(cat)).append(',').append(quote(field)).append(',').append(n[0]).append(',').append(n[1]).append(',').append(n[2]).append(',').append(n[3]).append(',').append(String.format(java.util.Locale.ROOT,"%.1f",100.0*(n[1]+n[2])/n[0])).append('\n')));
        Files.writeString(out.resolve("coverage.csv"),csv.toString(),StandardCharsets.UTF_8);
    }
    private void write(String name,Object value) throws IOException {Path tmp=out.resolve(name+".tmp");Files.writeString(tmp,JSON.writeValueAsString(value),StandardCharsets.UTF_8);Files.move(tmp,out.resolve(name),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
    private static List<JsonNode> items(JsonNode root) {if(root==null)return List.of();JsonNode n=root.path("response").path("body").path("items").path("item");if(n.isArray()){List<JsonNode> x=new ArrayList<>();n.forEach(x::add);return x;}return n.isObject()?List.of(n):List.of();}
    private static JsonNode firstItem(JsonNode root){List<JsonNode> x=items(root);return x.isEmpty()?null:x.get(0);}
    private static String text(JsonNode node,String field){return node==null?"":node.path(field).asString("").trim();}
    private static boolean has(JsonNode node,String...fields){for(String f:fields)if(!text(node,f).isBlank())return true;return false;}
    private static String firstText(JsonNode node,String...fields){for(String f:fields){String s=text(node,f);if(!s.isBlank())return s;}return "";}
    private static boolean contains(String source,String...tokens){for(String t:tokens)if(source.contains(t))return true;return false;}
    private static void put(Map<String,String> result,String field,boolean exists,boolean failed){result.put(field,failed?"REQUEST_FAILED":exists?"PRESENT":"NOT_PROVIDED");}
    private static String specialToken(String field){return switch(field){case "실내·실외"->"실내";case "개폐장 일정"->"개장";case "연령별 요금"->"어린이";case "횟수별 가격"->"회";case "인원별 가격"->"인승";case "회차별 출발시간"->"출발";case "장비 대여료","대여료","썰매 대여료"->"대여";default->field;};}
    private static String joined(Object value){return value==null?"":value.toString();}
    private static String normalize(String s){return s==null?"":s.replaceAll("[^가-힣a-zA-Z0-9]","").toLowerCase(java.util.Locale.ROOT);}
    private static boolean titleMatch(String expected,String actual,boolean beach){String a=normalize(expected),b=normalize(actual);if(a.equals(b))return true;if(beach){a=a.replace("해수욕장","").replace("해변","");b=b.replace("해수욕장","").replace("해변","");return a.length()>=2&&b.contains(a);}return false;}
    private static boolean regionMatches(String region,String address,String other){String a=region==null?"":region,b=(address==null?"":address)+" "+(other==null?"":other);for(String part:a.split("\\s+"))if(part.length()>=2&&part.matches(".*[시군구]$")&&b.contains(part))return true;return false;}
    private static boolean locationMatch(Place p,JsonNode item){String addr=text(item,"addr1");if(p.latitude!=null&&p.longitude!=null)try {double lat=Double.parseDouble(text(item,"mapy")),lon=Double.parseDouble(text(item,"mapx"));if(Math.abs(lat-p.latitude)<0.01&&Math.abs(lon-p.longitude)<0.01)return true;}catch(Exception ignored){}return regionMatches(p.region!=null?p.region:p.address,addr,null);}
    private static String hash(String s){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(bytes).substring(0,24);}catch(Exception e){throw new IllegalStateException(e);}}
    private static String quote(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private static String firstNonBlank(String...values){for(String v:values)if(v!=null&&!v.isBlank())return v;return null;}
    private static String required(String key){String v=System.getenv(key);if(v==null||v.isBlank())throw new IllegalArgumentException(key+" environment variable required");return v;}
    private void recordFailure(String place,String operation,String reason){Map<String,Object> e=new LinkedHashMap<>();e.put("placeId",place);e.put("operation",operation);e.put("reason",reason);failed.add(e);}
}
