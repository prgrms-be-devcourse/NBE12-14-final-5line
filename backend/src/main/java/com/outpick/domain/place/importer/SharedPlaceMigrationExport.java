package com.outpick.domain.place.importer;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/** Exports only curated place content, never accounts, sessions, reviews or favorites. */
public final class SharedPlaceMigrationExport {
    private static Connection db;
    private static final StringBuilder sql = new StringBuilder("-- Reviewed 626 candidates plus 88 follow-up snorkeling candidates. No historic Place IDs are inserted.\n");
    private static String literal(Object value) {
        if (value == null) return "NULL";
        if (value.toString().isEmpty()) return "''";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean b) return b ? "1" : "0";
        return "CONVERT(0x" + HexFormat.of().formatHex(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + " USING utf8mb4)";
    }
    private static List<Map<String,Object>> rows(String query, Object... args) throws Exception {
        try(var q=db.prepareStatement(query)) {for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);
            try(var rs=q.executeQuery()){List<Map<String,Object>> result=new ArrayList<>();var meta=rs.getMetaData();while(rs.next()) {Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),rs.getObject(i));result.add(row);}return result;}
        }
    }
    private static void line(String text){sql.append(text).append(";\n");}
    private static String columns(Map<String,Object> row){return String.join(",",row.keySet());}
    private static String values(Map<String,Object> row){return row.values().stream().map(SharedPlaceMigrationExport::literal).collect(Collectors.joining(","));}
    private static void insertChild(String table,Map<String,Object> row,String primary,String guard){
        row.remove(primary); row.remove("place_id");
        line("INSERT INTO "+table+"(place_id,"+columns(row)+") SELECT @shared_place,"+values(row)+" WHERE "+guard);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Output migration path required");
        String host=System.getenv().getOrDefault("MYSQL_HOST","127.0.0.1");
        if(!Set.of("localhost","127.0.0.1").contains(host))throw new IllegalArgumentException("Local export only");
        try(var connection=DriverManager.getConnection("jdbc:mysql://"+host+":"+System.getenv().getOrDefault("MYSQL_PORT","3306")+"/"+System.getenv("MYSQL_DATABASE"),System.getenv("MYSQL_USER"),System.getenv("MYSQL_PASSWORD"))){
            db=connection;db.setReadOnly(true);
            var places=rows("SELECT p.* FROM place p WHERE EXISTS(SELECT 1 FROM place_import_candidate c WHERE c.place_id=p.place_id) ORDER BY p.place_id");
            for(var place:places){
                Object old=place.get("place_id");
                var candidates=rows("SELECT * FROM place_import_candidate WHERE place_id=? ORDER BY candidate_id",old);
                var sources=rows("SELECT * FROM source_record WHERE place_id=? ORDER BY source_record_id",old);
                List<String> identities=new ArrayList<>();
                for(var source:sources) if(source.get("origin_id")!=null)identities.add("(s.type="+literal(source.get("type"))+" AND s.origin_id="+literal(source.get("origin_id"))+")");
                line("SET @shared_place = "+(identities.isEmpty()?"NULL":"(SELECT MIN(s.place_id) FROM source_record s WHERE "+String.join(" OR ",identities)+")"));
                // Existing staging IDs are trusted only when the facility name and full evidence match.
                for(var candidate:candidates)line("SET @shared_place=COALESCE(@shared_place,(SELECT c.place_id FROM place_import_candidate c WHERE c.candidate_id="+literal(candidate.get("candidate_id"))+" AND c.facility_name="+literal(candidate.get("facility_name"))+" AND c.raw_evidence=CAST("+literal(candidate.get("raw_evidence"))+" AS JSON) LIMIT 1))");
                var region=rows("SELECT * FROM region WHERE region_id=?",place.get("region_id")).getFirst();
                line("INSERT INTO region(name) SELECT "+literal(region.get("name"))+" WHERE NOT EXISTS(SELECT 1 FROM region WHERE parent_region_id IS NULL AND name="+literal(region.get("name"))+")");
                line("SET @shared_region=(SELECT MIN(region_id) FROM region WHERE parent_region_id IS NULL AND name="+literal(region.get("name"))+")");
                place.remove("place_id");place.remove("region_id");
                line("INSERT INTO place(region_id,"+columns(place)+") SELECT @shared_region,"+values(place)+" WHERE @shared_place IS NULL");
                line("SET @shared_place=COALESCE(@shared_place,LAST_INSERT_ID())");
                line("UPDATE place SET publish_status="+literal(place.get("publish_status"))+" WHERE place_id=@shared_place");
                for(var source:sources){
                    Object type=source.get("type"),origin=source.get("origin_id");source.remove("verifier_id");
                    insertChild("source_record",source,"source_record_id","NOT EXISTS(SELECT 1 FROM source_record WHERE place_id=@shared_place AND type <=> "+literal(type)+" AND origin_id <=> "+literal(origin)+")");
                    if("TOUR_API".equals(type)&&origin!=null)line("INSERT IGNORE INTO tour_api_import(content_id,place_id,source_record_id,collected_at) SELECT "+literal(origin)+",@shared_place,MIN(source_record_id),NOW() FROM source_record WHERE place_id=@shared_place AND type='TOUR_API' AND origin_id="+literal(origin)+" HAVING MIN(source_record_id) IS NOT NULL");
                }
                for(var activity:rows("SELECT a.*,t.name activity_name,c.name category_name FROM place_activity a JOIN activity_type t ON t.activity_type_id=a.activity_type_id JOIN activity_category c ON c.activity_category_id=t.activity_category_id WHERE a.place_id=?",old)){
                    String cat=literal(activity.get("category_name")),name=literal(activity.get("activity_name"));
                    line("INSERT INTO activity_category(name) SELECT "+cat+" WHERE NOT EXISTS(SELECT 1 FROM activity_category WHERE name="+cat+")");
                    line("INSERT INTO activity_type(activity_category_id,name) SELECT MIN(activity_category_id),"+name+" FROM activity_category WHERE name="+cat+" HAVING NOT EXISTS(SELECT 1 FROM activity_type t JOIN activity_category c ON c.activity_category_id=t.activity_category_id WHERE t.name="+name+" AND c.name="+cat+")");
                    line("SET @shared_type=(SELECT MIN(t.activity_type_id) FROM activity_type t JOIN activity_category c ON c.activity_category_id=t.activity_category_id WHERE t.name="+name+" AND c.name="+cat+")");
                    line("INSERT IGNORE INTO place_activity(place_id,activity_type_id,environment_type) VALUES(@shared_place,@shared_type,"+literal(activity.get("environment_type"))+")");
                    for(var season:rows("SELECT season FROM place_activity_season WHERE place_activity_id=?",activity.get("place_activity_id")))line("INSERT IGNORE INTO place_activity_season(place_activity_id,season) SELECT place_activity_id,"+literal(season.get("season"))+" FROM place_activity WHERE place_id=@shared_place AND activity_type_id=@shared_type");
                }
                for(String table:List.of("place_representative_image","place_operating_info"))for(var child:rows("SELECT * FROM "+table+" WHERE place_id=?",old))insertChild(table,child,"place_id","NOT EXISTS(SELECT 1 FROM "+table+" WHERE place_id=@shared_place)");
                for(String table:List.of("place_detail_fact","place_price","place_tag"))for(var child:rows("SELECT * FROM "+table+" WHERE place_id=?",old)){
                    String primary=table.equals("place_detail_fact")?"fact_id":table.equals("place_price")?"price_id":"tag_id";
                    var match=new LinkedHashMap<>(child);match.remove(primary);match.remove("place_id");
                    String guard="NOT EXISTS(SELECT 1 FROM "+table+" WHERE place_id=@shared_place AND "+match.entrySet().stream().map(e->"`"+e.getKey()+"` <=> "+literal(e.getValue())).collect(Collectors.joining(" AND "))+")";
                    if (child.get("evidence_key") != null) guard="NOT EXISTS(SELECT 1 FROM "+table+" WHERE evidence_key="+literal(child.get("evidence_key"))+")";
                    insertChild(table,child,primary,guard);
                }
                for(var candidate:candidates){candidate.remove("place_id");line("INSERT IGNORE INTO place_import_candidate(place_id,"+columns(candidate)+") VALUES(@shared_place,"+values(candidate)+")");}
            }
            Files.writeString(Path.of(args[0]),sql);System.out.println("exported_places="+places.size()+" bytes="+sql.length());
        }
    }
}
