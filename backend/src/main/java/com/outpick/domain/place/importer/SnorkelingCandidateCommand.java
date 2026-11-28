package com.outpick.domain.place.importer;

import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.sql.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Compares the supplied snorkel investigation list with local data; stages only review candidates. */
public final class SnorkelingCandidateCommand {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static String required(String k){String v=System.getenv(k);if(v==null||v.isBlank())throw new IllegalArgumentException(k+" required");return v;}
    private static String norm(String s){return s.replaceAll("[\\s·・()（）-]","").toLowerCase(Locale.ROOT);}
    private static String province(String region){
        String value=region.split(" ")[0];
        return switch(value){case "경상북도"->"경북";case "경상남도"->"경남";
            case "강원특별자치도"->"강원";case "제주특별자치도"->"제주";
            case "부산광역시"->"부산";case "울산광역시"->"울산";default->value;};
    }
    private static String sha(String s)throws Exception{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0,24);}
    private static String kindHint(String name){
        if(name.contains("방파제"))return "BREAKWATER";
        if(name.contains("어촌체험"))return "FISHING_VILLAGE";
        if(name.contains("해수욕장"))return "POSSIBLE_DESIGNATED_BEACH";
        if(name.contains("해변"))return "POSSIBLE_NATURAL_BEACH";
        if(name.contains("포구")||name.endsWith("항")||name.contains("항 일대"))return "PORT";
        if(name.contains("해안"))return "UNKNOWN";
        return "UNKNOWN";
    }
    private record Candidate(String name,String region,String kindHint){}
    private static List<Candidate> parse(Path source)throws Exception{
        List<Candidate> result=new ArrayList<>();boolean section=false;String region="";
        for(String line:Files.readAllLines(source)){
            if(line.startsWith("## 2.")){section=true;continue;}
            if(section&&line.startsWith("## 3."))break;
            if(!section)continue;
            if(line.startsWith("### ")){region=line.substring(4).trim();continue;}
            if(line.startsWith("- ")&&!region.isBlank()){
                String name=line.substring(2).trim();result.add(new Candidate(name,region,kindHint(name)));
            }
        }return result;
    }
    public static void main(String[] args)throws Exception{
        boolean apply=args.length==1&&args[0].equals("--apply");if(args.length>0&&!apply)throw new IllegalArgumentException("Only --apply supported");
        String host=System.getenv().getOrDefault("MYSQL_HOST","127.0.0.1");
        if(!Set.of("127.0.0.1","localhost").contains(host))throw new IllegalArgumentException("Local DB only");
        List<Candidate> input=parse(Path.of(required("OUTPICK_SNORKELING_LIST")));
        Path output=Path.of(required("OUTPICK_SNORKELING_REVIEW_DIR"));Files.createDirectories(output);
        String url="jdbc:mysql://"+host+":"+System.getenv().getOrDefault("MYSQL_PORT","3306")+"/"+required("MYSQL_DATABASE");
        List<Map<String,Object>> report=new ArrayList<>();int newCandidates=0,reuseCandidate=0,possibleBeach=0,ambiguous=0;
        try(Connection db=DriverManager.getConnection(url,required("MYSQL_USER"),required("MYSQL_PASSWORD"))){
            db.setAutoCommit(false);db.setReadOnly(!apply);
            Map<String,List<Map<String,Object>>> places=new HashMap<>();
            try(Statement st=db.createStatement();ResultSet rs=st.executeQuery("SELECT p.place_id,p.name,p.name_alias,p.address,p.publish_status,r.name region_name FROM place p LEFT JOIN region r ON r.region_id=p.region_id")){
                while(rs.next()){
                    Map<String,Object> row=new LinkedHashMap<>();for(String k:List.of("place_id","name","name_alias","address","publish_status","region_name"))row.put(k,rs.getObject(k));
                    for(String k:List.of("name","name_alias"))if(row.get(k)!=null)places.computeIfAbsent(norm(row.get(k).toString()),x->new ArrayList<>()).add(row);
                }
            }
            Map<String,Set<String>> existingNames=new HashMap<>();
            try(Statement st=db.createStatement();ResultSet rs=st.executeQuery("""
                    SELECT facility_name,JSON_UNQUOTE(JSON_EXTRACT(raw_evidence,'$.region'))
                    FROM place_import_candidate WHERE category_name='스노클링'
                    """)){
                while(rs.next())existingNames.computeIfAbsent(norm(rs.getString(1)),x->new HashSet<>())
                        .add(Objects.toString(rs.getString(2),""));
            }
            Set<String> seen=new HashSet<>();
            try(PreparedStatement insert=db.prepareStatement("""
                INSERT IGNORE INTO place_import_candidate(candidate_id,place_id,category_name,facility_name,
                  review_status,recommendable,raw_evidence)
                VALUES(?,NULL,'스노클링',?,'PENDING',FALSE,?)
                """)){
                for(Candidate c:input){
                    String key=norm(c.name())+"|"+c.region();
                    if(!seen.add(key))continue;
                    List<Map<String,Object>> matches=places.getOrDefault(norm(c.name()),List.of());
                    List<Long> likely=new ArrayList<>();
                    for(Map<String,Object> p:matches){
                        String addr=Objects.toString(p.get("address"),"");
                        String region=Objects.toString(p.get("region_name"),"");
                        String local=c.region().substring(c.region().lastIndexOf(' ')+1);
                        if(addr.contains(local)||region.contains(local))likely.add(((Number)p.get("place_id")).longValue());
                    }
                    String candidateProvince=province(c.region());
                    boolean prior=existingNames.getOrDefault(norm(c.name()),Set.of()).stream()
                            .anyMatch(region -> province(region).equals(candidateProvince));
                    String decision=prior?"EXISTING_CANDIDATE_REVIEW":likely.size()==1?"POSSIBLE_EXISTING_PLACE_REVIEW":
                            likely.size()>1?"AMBIGUOUS_PLACE_REVIEW":"NEW_PLACE_REVIEW";
                    if(prior)reuseCandidate++;else newCandidates++;
                    if(likely.size()==1&&c.kindHint().equals("POSSIBLE_DESIGNATED_BEACH"))possibleBeach++;
                    if(likely.size()>1)ambiguous++;
                    Map<String,Object> row=new LinkedHashMap<>();row.put("name",c.name());row.put("region",c.region());
                    row.put("typeHintFromNameOnly",c.kindHint());row.put("decision",decision);
                    row.put("possibleExistingPlaceIds",likely);row.put("intakeStatus","UNKNOWN");
                    row.put("recommendable",false);row.put("officialBeachStatus","UNKNOWN");
                    report.add(row);
                    if(apply&&!prior){insert.setString(1,"snorkel-list:"+sha(key));insert.setString(2,c.name());
                        insert.setString(3,JSON.writeValueAsString(row));insert.executeUpdate();}
                }
            }
            if(apply)db.commit();else db.rollback();
        }
        Files.writeString(output.resolve("snorkeling_candidate_review.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.printf("input=%d unique=%d existing_candidate_name=%d new_review_candidates=%d possible_existing_beach=%d ambiguous=%d apply=%s%n",
          input.size(),report.size(),reuseCandidate,newCandidates,possibleBeach,ambiguous,apply);
    }
}
