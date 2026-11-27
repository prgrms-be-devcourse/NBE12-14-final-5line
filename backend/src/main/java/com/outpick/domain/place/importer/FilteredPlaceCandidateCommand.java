package com.outpick.domain.place.importer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit local-only staging import. Never changes existing place, activity, favorite, or review rows. */
public final class FilteredPlaceCandidateCommand {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private record Candidate(String id, String name, String category, String contentId, JsonNode evidence) { }

    private static String text(JsonNode n, String field) { return n.path(field).asString("").trim(); }
    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
    private static void add(Map<String,Candidate> result, Candidate candidate) {
        if (candidate.id().isBlank() || candidate.name().isBlank() || candidate.category().isBlank())
            throw new IllegalArgumentException("Candidate has missing identity");
        if (result.putIfAbsent(candidate.id(), candidate) != null)
            throw new IllegalArgumentException("Duplicate candidate ID: " + candidate.id());
    }
    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 1 && "--apply".equals(args[0]);
        if (args.length > 0 && !apply) throw new IllegalArgumentException("Only --apply is supported");
        String host = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
        if (apply && !Set.of("127.0.0.1", "localhost").contains(host))
            throw new IllegalArgumentException("Apply is restricted to a local database host");
        Path jsonFile = Path.of(env("OUTPICK_FILTERED_REVIEW_JSON"));
        Path excludedFile = Path.of(env("OUTPICK_EXCLUDED_FACILITIES_JSON"));
        Path reportFile = Path.of(env("OUTPICK_IMPORT_DRY_RUN_REPORT"));
        JsonNode input = JSON.readTree(Files.readString(jsonFile));
        JsonNode excluded = JSON.readTree(Files.readString(excludedFile));
        if (excluded.size() != 8) throw new IllegalStateException("Expected exactly eight excluded facilities");
        Map<String,Candidate> candidates = new LinkedHashMap<>();
        for (JsonNode record : input.path("records")) {
            JsonNode place = record.path("place");
            add(candidates, new Candidate(text(place,"id"), text(place,"name"),
                    place.path("categories").path(0).asString(""), text(place,"contentId"), record));
        }
        for (JsonNode record : input.path("iceRinkPhase1").path("researchQueue")) {
            JsonNode place = record.path("place");
            add(candidates, new Candidate(text(place,"id"), text(place,"name"),
                    "아이스스케이팅", text(place,"contentId"), record));
        }
        for (JsonNode record : input.path("snorkelingCandidates")) {
            String name = text(record,"name");
            add(candidates, new Candidate("snorkel:" + name, name, "스노클링", "", record));
        }
        if (candidates.size() != 626) throw new IllegalStateException("Expected 626 filtered candidates, got " + candidates.size());
        for (JsonNode record : excluded) {
            String id = text(record,"id");
            if (!id.isBlank() && candidates.containsKey(id)) throw new IllegalStateException("Excluded ID remains: " + id);
            if (id.isBlank() && "스노클링".equals(record.path("categories").path(0).asString(""))
                    && candidates.containsKey("snorkel:" + text(record,"name")))
                throw new IllegalStateException("Excluded snorkeling candidate remains: " + text(record,"name"));
        }
        Map<String,Integer> byCategory = new LinkedHashMap<>();
        List<String> invalidDbIds = new ArrayList<>(), contentIdConflicts = new ArrayList<>();
        List<Map<String,Object>> exclusionAudit = new ArrayList<>();
        String url = "jdbc:mysql://" + host + ":" + System.getenv().getOrDefault("MYSQL_PORT","3306") + "/" + env("MYSQL_DATABASE");
        try (Connection db = DriverManager.getConnection(url, env("MYSQL_USER"), env("MYSQL_PASSWORD"))) {
            db.setReadOnly(!apply);
            try (PreparedStatement place = db.prepareStatement("SELECT place_id FROM place WHERE place_id=?");
                 PreparedStatement source = db.prepareStatement("SELECT origin_id FROM source_record WHERE place_id=? AND type='TOUR_API' LIMIT 1");
                 PreparedStatement refs = db.prepareStatement("SELECT (SELECT COUNT(*) FROM favorite WHERE place_id=?), (SELECT COUNT(*) FROM review WHERE place_id=?)")) {
                for (Candidate c : candidates.values()) {
                    byCategory.merge(c.category(), 1, Integer::sum);
                    if (!c.id().startsWith("db:")) continue;
                    long id = Long.parseLong(c.id().substring(3));
                    place.setLong(1,id);try(ResultSet rs=place.executeQuery()) {if(!rs.next()) invalidDbIds.add(c.id());}
                    if(!c.contentId().isBlank()) {
                        source.setLong(1,id);try(ResultSet rs=source.executeQuery()) {
                            if(rs.next() && !c.contentId().equals(rs.getString(1)))contentIdConflicts.add(c.id());
                        }
                    }
                }
                for (JsonNode e : excluded) if (text(e,"id").startsWith("db:")) {
                    long id = Long.parseLong(text(e,"id").substring(3));refs.setLong(1,id);refs.setLong(2,id);
                    try(ResultSet rs=refs.executeQuery()) {rs.next();exclusionAudit.add(Map.of("id",id,"name",text(e,"name"),
                            "favorites",rs.getLong(1),"reviews",rs.getLong(2),"action","CATEGORY_ONLY_AFTER_APPROVAL"));}
                }
            }
            Map<String,Object> report = new LinkedHashMap<>();
            report.put("candidateCount",candidates.size());report.put("excludedCount",excluded.size());
            report.put("categoryCounts",byCategory);report.put("missingDatabaseIds",invalidDbIds);
            report.put("contentIdConflicts",contentIdConflicts);report.put("exclusionAudit",exclusionAudit);
            report.put("sourceFlaggedDuplicateGroups",input.path("duplicateContentIdReview").size()
                    + input.path("iceRinkPhase1").path("duplicateReviewGroups").size());
            report.put("existingPlacesChanged",0);report.put("candidateRowsApplied",0);
            if(apply) {
                if(!invalidDbIds.isEmpty()||!contentIdConflicts.isEmpty())throw new IllegalStateException("Dry-run validation failed; no rows applied");
                int inserted = 0;
                try(PreparedStatement insert = db.prepareStatement("""
                        INSERT IGNORE INTO place_import_candidate
                          (candidate_id, place_id, category_name, facility_name, content_id, review_status, recommendable, raw_evidence)
                        VALUES (?, ?, ?, ?, ?, 'PENDING', FALSE, ?)
                        """)) {
                    db.setAutoCommit(false);
                    try {
                        for(Candidate c:candidates.values()) {
                            insert.setString(1,c.id());
                            if(c.id().startsWith("db:"))insert.setLong(2,Long.parseLong(c.id().substring(3)));
                            else insert.setNull(2,java.sql.Types.BIGINT);
                            insert.setString(3,c.category());insert.setString(4,c.name());
                            if(c.contentId().isBlank())insert.setNull(5,java.sql.Types.VARCHAR);else insert.setString(5,c.contentId());
                            insert.setString(6,c.evidence().toString());
                            inserted += insert.executeUpdate();
                        }
                        db.commit();
                    } catch(Exception error) {db.rollback();throw error;}
                }
                report.put("candidateRowsApplied",inserted);
            }
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile,JSON.writeValueAsString(report));
            System.out.printf("candidates=%d excluded=%d missing_db_ids=%d content_id_conflicts=%d staged=%s%n",
                    candidates.size(),excluded.size(),invalidDbIds.size(),contentIdConflicts.size(),report.get("candidateRowsApplied"));
        }
    }
}
