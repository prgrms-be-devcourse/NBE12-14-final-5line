package com.outpick.domain.place.importer;

import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Read-only local snapshot for review. Does not mark any candidate or evidence verified. */
public final class PlaceEvidenceExportCommand {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static String required(String key) {String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException(key+" required");return value;}
    private static List<Map<String,Object>> rows(Connection db,String sql) throws Exception {
        try(Statement st=db.createStatement();ResultSet rs=st.executeQuery(sql)) {
            List<Map<String,Object>> out=new ArrayList<>(); ResultSetMetaData meta=rs.getMetaData();
            while(rs.next()) {Map<String,Object> item=new LinkedHashMap<>(); for(int i=1;i<=meta.getColumnCount();i++) {
                Object value=rs.getObject(i);String name=meta.getColumnLabel(i);
                if(value instanceof java.sql.Date || value instanceof Timestamp)value=value.toString();
                if(value instanceof String && (name.equals("value_json")||name.equals("additional_conditions"))) value=JSON.readTree((String)value);
                item.put(name,value);
            }out.add(item);} return out;
        }
    }
    private static void write(Path dir,String file,Object data)throws Exception {Files.writeString(dir.resolve(file),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data));}
    public static void main(String[] args)throws Exception {
        String host=System.getenv().getOrDefault("MYSQL_HOST","127.0.0.1");
        if(!Set.of("127.0.0.1","localhost").contains(host))throw new IllegalArgumentException("Local DB only");
        Path dir=Path.of(required("OUTPICK_EXPORT_DIR"));Files.createDirectories(dir);
        String url="jdbc:mysql://"+host+":"+System.getenv().getOrDefault("MYSQL_PORT","3306")+"/"+required("MYSQL_DATABASE");
        try(Connection db=DriverManager.getConnection(url,required("MYSQL_USER"),required("MYSQL_PASSWORD"))){
            db.setReadOnly(true);
            Map<String,Object> inventory=new LinkedHashMap<>();
            inventory.put("publicPlacesByCategory",rows(db,"""
                SELECT ac.name category,COUNT(DISTINCT p.place_id) published_places
                FROM activity_category ac LEFT JOIN activity_type at ON at.activity_category_id=ac.activity_category_id
                LEFT JOIN place_activity pa ON pa.activity_type_id=at.activity_type_id
                LEFT JOIN place p ON p.place_id=pa.place_id AND p.publish_status=1
                WHERE ac.name IN ('아이스스케이팅','해수욕장','스노클링','워터파크',
                  '루지·레일바이크·중력 레이싱','스키','눈썰매')
                GROUP BY ac.activity_category_id,ac.name ORDER BY ac.name
                """));
            inventory.put("candidatesByCategoryAndStatus",rows(db,"""
                SELECT category_name,review_status,COUNT(*) candidate_count,
                  SUM(CASE WHEN place_id IS NULL THEN 1 ELSE 0 END) unlinked_candidates,
                  SUM(CASE WHEN place_id IS NOT NULL THEN 1 ELSE 0 END) existing_place_linked
                FROM place_import_candidate GROUP BY category_name,review_status ORDER BY category_name,review_status
                """));
            inventory.put("totalPlaces",rows(db,"SELECT COUNT(*) total_places FROM place"));
            write(dir,"current_place_inventory.json",inventory);
            write(dir,"detail_evidence_review.json",rows(db,"SELECT fact_id,place_id,field_key,value_json,source_kind,source_url,source_content_id,checked_at,verified_at,verification_status,valid_from,valid_until FROM place_detail_fact ORDER BY fact_id"));
            write(dir,"price_evidence_review.json",rows(db,"SELECT price_id,place_id,price_kind,price_basis,ticket_name,price_type,age_band,day_type,season_label,valid_from,valid_until,amount_krw,free_of_charge,price_unit,duration_minutes,use_count,passenger_count,equipment_type,equipment_included,mandatory_extra_cost,residency_condition,raw_price_text,additional_conditions,source_kind,source_url,source_content_id,checked_at,verified_at,verification_status FROM place_price ORDER BY price_id"));
        }
        Path comparison=Path.of(required("OUTPICK_DUPLICATE_COMPARISON_CSV"));
        List<String> lines=Files.readAllLines(comparison);
        List<String> pending=new ArrayList<>();pending.add(lines.get(0));
        for(String line:lines.subList(1,lines.size()))if(line.endsWith("\"MANUAL_REVIEW\""))pending.add(line);
        Files.write(dir.resolve("duplicate_pending_review.csv"),pending);
        System.out.println("duplicate_pending_rows="+(pending.size()-1));
    }
}
