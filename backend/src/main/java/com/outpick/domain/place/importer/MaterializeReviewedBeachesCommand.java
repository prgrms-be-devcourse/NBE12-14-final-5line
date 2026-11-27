package com.outpick.domain.place.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Explicit local import of uniquely matched reviewed places; keeps operation status unverified. */
public final class MaterializeReviewedBeachesCommand {
    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
    private static String value(JsonNode node, String key) { return node.path(key).asString("").trim(); }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !"--apply".equals(args[2]))
            throw new IllegalArgumentException("Usage: review.json raw_api_data.json --apply");
        String host = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
        if (!host.equals("127.0.0.1") && !host.equals("localhost"))
            throw new IllegalArgumentException("Only a local database is supported");
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(Files.readString(Path.of(args[0])));
        JsonNode raw = mapper.readTree(Files.readString(Path.of(args[1])));
        String url = "jdbc:mysql://" + host + ":" + System.getenv().getOrDefault("MYSQL_PORT", "3306") + "/" + env("MYSQL_DATABASE");
        int created = 0, linked = 0, images = 0, skipped = 0;
        try (Connection db = DriverManager.getConnection(url, env("MYSQL_USER"), env("MYSQL_PASSWORD"))) {
            db.setAutoCommit(false);
            try {
                java.util.List<JsonNode> records = new java.util.ArrayList<>();
                root.path("records").forEach(records::add);
                root.path("iceRinkPhase1").path("researchQueue").forEach(records::add);
                for (JsonNode row : records) {
                    JsonNode place = row.path("place");
                    String candidateId = value(place,"id");
                    if (candidateId.startsWith("db:") || !"MATCHED".equals(value(place,"matchStatus"))) continue;
                    String category = place.path("categories").path(0).asString("");
                    if (!java.util.Set.of("해수욕장", "워터파크", "스키", "눈썰매", "아이스스케이팅").contains(category)) continue;
                    JsonNode common = row.path("apiSource").path("common").path(0);
                    if (common.isMissingNode()) common = raw.path(candidateId).path("responses")
                            .path("detailCommon2").path("response").path("body").path("items").path("item").path(0);
                    String contentId = value(place,"contentId");
                    if (contentId.isBlank() || !contentId.equals(value(common,"contentid"))) { skipped++; continue; }
                    double lat = common.path("mapy").asDouble(Double.NaN);
                    double lon = common.path("mapx").asDouble(Double.NaN);
                    String address = value(common,"addr1");
                    if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < 33 || lat > 39 || lon < 124 || lon > 132 || address.isBlank()) { skipped++; continue; }
                    Long placeId = null;
                    try (var q = db.prepareStatement("SELECT place_id FROM source_record WHERE type='TOUR_API' AND origin_id=? ORDER BY source_record_id LIMIT 1")) {
                        q.setString(1,contentId);try(var rs=q.executeQuery()){if(rs.next())placeId=rs.getLong(1);}
                    }
                    if (placeId == null) {
                        String province = address.split("\\s+")[0];
                        Long regionId = null;
                        try (var q=db.prepareStatement("SELECT region_id FROM region WHERE parent_region_id IS NULL AND name=? ORDER BY region_id LIMIT 1")) {
                            q.setString(1,province);try(var rs=q.executeQuery()){if(rs.next())regionId=rs.getLong(1);}
                        }
                        if (regionId == null) {
                            try (var q=db.prepareStatement("INSERT INTO region(parent_region_id,code,name) VALUES(NULL,NULL,?)",Statement.RETURN_GENERATED_KEYS)) {
                                q.setString(1,province);q.executeUpdate();try(var rs=q.getGeneratedKeys()){rs.next();regionId=rs.getLong(1);}
                            }
                        }
                        try (var q=db.prepareStatement("INSERT INTO place(region_id,name,address,latitude,longitude,introduce,homepage,publish_status,operation_status) VALUES(?,?,?,?,?,?,?,0,'UNVERIFIED')",Statement.RETURN_GENERATED_KEYS)) {
                            q.setLong(1,regionId);q.setString(2,value(place,"name"));q.setString(3,address);q.setDouble(4,lat);q.setDouble(5,lon);
                            q.setString(6,value(common,"overview"));
                            String homepage=value(common,"homepage");
                            q.setString(7,homepage.startsWith("http") && homepage.length() <= 100 ? homepage : null);q.executeUpdate();
                            try(var rs=q.getGeneratedKeys()){rs.next();placeId=rs.getLong(1);}
                        }
                        long sourceId;
                        try (var q=db.prepareStatement("INSERT INTO source_record(place_id,type,origin_id,url,usage_terms_memo) VALUES(?,'TOUR_API',?,'https://www.data.go.kr/data/15101578/openapi.do','Imported from reviewed TourAPI response; opening status unverified')",Statement.RETURN_GENERATED_KEYS)) {
                            q.setLong(1,placeId);q.setString(2,contentId);q.executeUpdate();try(var rs=q.getGeneratedKeys()){rs.next();sourceId=rs.getLong(1);}
                        }
                        try(var q=db.prepareStatement("INSERT IGNORE INTO tour_api_import(content_id,place_id,source_record_id,collected_at) VALUES(?,?,?,NOW())")) {
                            q.setString(1,contentId);q.setLong(2,placeId);q.setLong(3,sourceId);q.executeUpdate();
                        }
                        try(var q=db.prepareStatement("INSERT IGNORE INTO place_activity(place_id,activity_type_id,environment_type) SELECT ?,activity_type_id,? FROM activity_type WHERE name=? ORDER BY activity_type_id LIMIT 1")) {
                            q.setLong(1,placeId);q.setString(2,category.equals("해수욕장") ? "OUTDOOR" : null);q.setString(3,category);q.executeUpdate();
                        }
                        String image=value(common,"firstimage"), license=value(common,"cpyrhtDivCd");
                        if (!image.isBlank() && (license.equals("Type1") || license.equals("Type3"))) {
                            try(var q=db.prepareStatement("INSERT IGNORE INTO place_representative_image(place_id,url,attribution,source_url,license_type,verified_at) VALUES(?,? ,?,'https://www.data.go.kr/data/15101578/openapi.do',?,NOW())")) {
                                q.setLong(1,placeId);q.setString(2,image);q.setString(3,"한국관광공사 TourAPI 제공 · " + value(place,"name"));q.setString(4,license);images+=q.executeUpdate();
                            }
                        }
                        created++;
                    } else linked++;
                    try(var q=db.prepareStatement("UPDATE place_import_candidate SET place_id=? WHERE candidate_id=? AND place_id IS NULL")) {
                        q.setLong(1,placeId);q.setString(2,candidateId);q.executeUpdate();
                    }
                }
                db.commit();
            } catch(Exception error) {db.rollback();throw error;}
        }
        System.out.printf("created=%d linked=%d images=%d skipped=%d%n",created,linked,images,skipped);
    }
}
