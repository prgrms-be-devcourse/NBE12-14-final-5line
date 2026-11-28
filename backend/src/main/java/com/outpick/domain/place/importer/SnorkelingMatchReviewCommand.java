package com.outpick.domain.place.importer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Review suggestions only: name similarity never establishes identity or swim permission. */
public final class SnorkelingMatchReviewCommand {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static String required(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException(key+" required");return value;}
    private static String norm(String value){return value.replaceAll("[\\s·・()（）-]","").toLowerCase(Locale.ROOT);}
    private static String base(String value){return norm(value).replaceAll("(해수욕장|해변|해안|모래숲|일대)$","");}
    public static void main(String[] args)throws Exception {
        String host=System.getenv().getOrDefault("MYSQL_HOST","127.0.0.1");
        if(!Set.of("127.0.0.1","localhost").contains(host))throw new IllegalArgumentException("Local DB only");
        Path dir=Path.of(required("OUTPICK_SNORKELING_REVIEW_DIR"));
        JsonNode input=JSON.readTree(Files.readString(dir.resolve("snorkeling_candidate_review.json")));
        String url="jdbc:mysql://"+host+":"+System.getenv().getOrDefault("MYSQL_PORT","3306")+"/"+required("MYSQL_DATABASE");
        List<Map<String,Object>> places=new ArrayList<>();
        try(Connection db=DriverManager.getConnection(url,required("MYSQL_USER"),required("MYSQL_PASSWORD"))){
            db.setReadOnly(true);
            try(Statement st=db.createStatement();ResultSet rs=st.executeQuery("""
                SELECT p.place_id,p.name,p.name_alias,p.address,ac.name category
                FROM place p JOIN place_activity pa ON pa.place_id=p.place_id
                JOIN activity_type at ON at.activity_type_id=pa.activity_type_id
                JOIN activity_category ac ON ac.activity_category_id=at.activity_category_id
                WHERE ac.name='해수욕장'
                """)){
                while(rs.next())places.add(Map.of("id",rs.getLong(1),"name",Objects.toString(rs.getString(2),""),
                    "alias",Objects.toString(rs.getString(3),""),"address",Objects.toString(rs.getString(4),""),
                    "category",rs.getString(5)));
            }
        }
        List<Map<String,Object>> suggestions=new ArrayList<>();Set<String> candidateMatched=new HashSet<>();
        for(JsonNode c:input){
            String name=c.path("name").asString(),region=c.path("region").asString();
            String city=region.substring(region.lastIndexOf(' ')+1);
            String key=base(name);
            if(key.length()<2)continue;
            for(Map<String,Object> p:places){
                String address=p.get("address").toString();
                if(!address.contains(city))continue;
                String dbName=p.get("name").toString(),alias=p.get("alias").toString();
                String dbKey=base(dbName),aliasKey=base(alias);
                String rule=null;
                if(norm(name).equals(norm(dbName))||!alias.isBlank()&&norm(name).equals(norm(alias)))rule="EXACT_NAME_REGION";
                else if(key.equals(dbKey)||!aliasKey.isBlank()&&key.equals(aliasKey))rule="BASE_NAME_REGION";
                else if(key.length()>=3&&(dbKey.contains(key)||key.contains(dbKey))&&dbKey.length()>=3)
                    rule="PARTIAL_NAME_REGION";
                if(rule==null)continue;
                candidateMatched.add(name+"|"+region);
                suggestions.add(Map.of("candidateName",name,"candidateRegion",region,"placeId",p.get("id"),
                    "placeName",dbName,"placeAddress",address,"matchRule",rule,"identityVerified",false,
                    "entryAllowedVerified",false));
            }
        }
        Files.writeString(dir.resolve("snorkeling_beach_match_review.json"),
            JSON.writerWithDefaultPrettyPrinter().writeValueAsString(suggestions));
        System.out.printf("candidate_with_suggestion=%d suggestion_pairs=%d automatic_merge=0%n",
            candidateMatched.size(),suggestions.size());
    }
}
