package com.outpick.domain.place.importer;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** Explicit local-only materialization of reviewed candidate identities as unpublished Place drafts. */
public final class MaterializePendingCandidatesCommand {
    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
    private static String value(tools.jackson.databind.JsonNode node, String field) {
        return node.path(field).asString("").trim();
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"--apply".equals(args[0])) throw new IllegalArgumentException("Only --apply is supported");
        String host = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
        if (!host.equals("127.0.0.1") && !host.equals("localhost")) throw new IllegalArgumentException("Local DB only");
        String url = "jdbc:mysql://" + host + ":" + System.getenv().getOrDefault("MYSQL_PORT", "3306") + "/" + env("MYSQL_DATABASE");
        var json = JsonMapper.builder().build();
        int created = 0;
        try (var db = DriverManager.getConnection(url, env("MYSQL_USER"), env("MYSQL_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                List<String[]> candidates = new ArrayList<>();
                try (var q=db.prepareStatement("SELECT candidate_id,facility_name,category_name,raw_evidence FROM place_import_candidate WHERE place_id IS NULL ORDER BY candidate_id"); var rs=q.executeQuery()) {
                    while(rs.next()) candidates.add(new String[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)});
                }
                for (String[] candidate : candidates) {
                    var raw = json.readTree(candidate[3]);
                    var place = raw.path("place");
                    String region = value(place,"region");
                    if (region.isBlank()) region=value(raw,"region");
                    String address = value(place,"address");
                    if (region.isBlank() && !address.isBlank()) region=address.split("\\s+")[0];
                    if (region.isBlank()) throw new IllegalStateException("Missing region for " + candidate[0]);
                    region=region.split("\\s+")[0];
                    Long regionId=null;
                    try(var q=db.prepareStatement("SELECT region_id FROM region WHERE parent_region_id IS NULL AND name=? ORDER BY region_id LIMIT 1")) {
                        q.setString(1,region);try(var rs=q.executeQuery()){if(rs.next())regionId=rs.getLong(1);}
                    }
                    if(regionId==null) {
                        try(var q=db.prepareStatement("INSERT INTO region(parent_region_id,code,name) VALUES(NULL,NULL,?)",Statement.RETURN_GENERATED_KEYS)) {
                            q.setString(1,region);q.executeUpdate();try(var rs=q.getGeneratedKeys()){rs.next();regionId=rs.getLong(1);}
                        }
                    }
                    long placeId;
                    try(var q=db.prepareStatement("INSERT INTO place(region_id,name,address,publish_status,operation_status) VALUES(?,?,?,0,'UNVERIFIED')",Statement.RETURN_GENERATED_KEYS)) {
                        q.setLong(1,regionId);q.setString(2,candidate[1]);q.setString(3,address.isBlank()?null:address);q.executeUpdate();
                        try(var rs=q.getGeneratedKeys()){rs.next();placeId=rs.getLong(1);}
                    }
                    try(var q=db.prepareStatement("INSERT IGNORE INTO place_activity(place_id,activity_type_id) SELECT ?,activity_type_id FROM activity_type WHERE name=? ORDER BY activity_type_id LIMIT 1")) {
                        q.setLong(1,placeId);q.setString(2,candidate[2]);q.executeUpdate();
                    }
                    try(var q=db.prepareStatement("UPDATE place_import_candidate SET place_id=? WHERE candidate_id=? AND place_id IS NULL")) {
                        q.setLong(1,placeId);q.setString(2,candidate[0]);if(q.executeUpdate()!=1)throw new IllegalStateException("Candidate changed: "+candidate[0]);
                    }
                    created++;
                }
                db.commit();
            } catch(Exception error) {db.rollback();throw error;}
        }
        System.out.println("created_unpublished_place_drafts="+created);
    }
}
