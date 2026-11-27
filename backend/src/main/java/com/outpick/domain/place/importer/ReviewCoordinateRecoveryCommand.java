package com.outpick.domain.place.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Recovers only unambiguous coordinates already present in the reviewed TourAPI response. */
public final class ReviewCoordinateRecoveryCommand {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: input.json output.csv");
        JsonNode root = JSON.readTree(Files.readString(Path.of(args[0])));
        List<String> rows = new ArrayList<>();
        rows.add("candidate_id,content_id,latitude,longitude");
        int unresolved = 0;
        for (JsonNode record : root.path("records")) {
            JsonNode place = record.path("place");
            if (!"해수욕장".equals(place.path("categories").path(0).asString(""))) continue;
            if (!place.path("latitude").isNull() && !place.path("latitude").isMissingNode()) continue;
            if (!"MATCHED".equals(place.path("matchStatus").asString(""))) { unresolved++; continue; }
            JsonNode common = record.path("apiSource").path("common").path(0);
            String contentId = place.path("contentId").asString("");
            if (!contentId.equals(common.path("contentid").asString(""))) { unresolved++; continue; }
            double latitude = common.path("mapy").asDouble(Double.NaN);
            double longitude = common.path("mapx").asDouble(Double.NaN);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < 33 || latitude > 39 || longitude < 124 || longitude > 132) {
                unresolved++; continue;
            }
            String id = place.path("id").asString("");
            if (id.contains(",") || contentId.contains(",")) throw new IllegalArgumentException("Invalid CSV identifier");
            rows.add(id + "," + contentId + "," + latitude + "," + longitude);
        }
        Path output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        Files.write(output, rows);
        System.out.println("recovered=" + (rows.size() - 1) + " unresolved=" + unresolved);
    }
}
