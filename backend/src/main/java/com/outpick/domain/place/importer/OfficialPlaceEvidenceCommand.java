package com.outpick.domain.place.importer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores supplied official-source observations for review; never publishes unverified values. */
public final class OfficialPlaceEvidenceCommand {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static String text(JsonNode n,String key){return n.path(key).asString("").trim();}
    private static String env(String key){String s=System.getenv(key);if(s==null||s.isBlank())throw new IllegalArgumentException(key+" required");return s;}
    private static String hash(String value)throws Exception{return java.util.HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    private static LocalDateTime checked(JsonNode patch){String s=text(patch,"checkedAt");return s.isBlank()?null:LocalDate.parse(s).atStartOfDay();}
    private static String sourceUrl(JsonNode patch){
        String url=text(patch,"sourceUrl");
        if(url.isBlank()&&patch.path("sourceUrls").isArray()&&!patch.path("sourceUrls").isEmpty())url=patch.path("sourceUrls").get(0).asString("");
        return url.isBlank()?null:url;
    }
    private static long canonical(Connection db,long id)throws Exception{
        try(PreparedStatement ps=db.prepareStatement("SELECT canonical_place_id FROM place_duplicate_alias WHERE duplicate_place_id=?")){
            ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):id;}
        }
    }
    private static Long target(Connection db,JsonNode patch,String primaryId)throws Exception{
        Set<Long> ids=new HashSet<>();
        if(primaryId!=null&&primaryId.startsWith("db:"))ids.add(canonical(db,Long.parseLong(primaryId.substring(3))));
        for(JsonNode id:patch.path("placeIds"))if(id.asString().startsWith("db:"))ids.add(canonical(db,Long.parseLong(id.asString().substring(3))));
        return ids.size()==1?ids.iterator().next():null;
    }
    private static int insertFact(PreparedStatement ps,long place,String field,JsonNode value,JsonNode patch,String contentId)throws Exception{
        if(field.length()>80||value.isNull()||value.isMissingNode())return 0;
        String kind=text(patch,"sourceType"),url=sourceUrl(patch),raw=value.toString();
        if(kind.isBlank()||url==null)return 0;
        String key=hash(place+"|"+field+"|"+url+"|"+raw);
        ps.setLong(1,place);ps.setString(2,field);ps.setString(3,raw);ps.setString(4,kind);ps.setString(5,url);
        if(contentId==null||contentId.isBlank())ps.setNull(6,Types.VARCHAR);else ps.setString(6,contentId);
        if(checked(patch)==null)ps.setNull(7,Types.TIMESTAMP);else ps.setObject(7,checked(patch));
        ps.setString(8,key);return ps.executeUpdate();
    }
    private static JsonNode priceItems(JsonNode value) {
        if (value.isArray()) return value;
        if (value.isObject() && value.path("items").isArray()) return value.path("items");
        return null;
    }
    private static String optional(JsonNode node, String key) {
        String value = text(node, key);
        return value.isBlank() ? null : value;
    }
    private static Integer number(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isIntegralNumber() ? value.asInt() : null;
    }
    private static Boolean flag(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isBoolean() ? value.asBoolean() : null;
    }
    private static LocalDate date(JsonNode node, String key) {
        String value = text(node, key);
        return value.isBlank() ? null : LocalDate.parse(value);
    }
    private static int insertOnePrice(PreparedStatement ps, long place, JsonNode value,
                                      JsonNode patch, String contentId, String legacyField) throws Exception {
        if (!value.isObject() || sourceUrl(patch) == null) return 0;
        String basis = optional(value,"priceBasis");
        if (basis == null) basis = "NORMAL";
        if (!Set.of("NORMAL","DISCOUNT","COUPON","AFFILIATE").contains(basis)) return 0;
        String unit = optional(value,"priceUnit");
        if (unit != null && !Set.of("PERSON","VEHICLE","EQUIPMENT","GROUP").contains(unit)) return 0;
        Integer amount = number(value,"amountKrw");
        Boolean free = flag(value,"freeOfCharge");
        if (amount != null && amount < 0 || Boolean.TRUE.equals(free) && amount != null && amount != 0) return 0;
        // Unknown is not zero. Explicit free is stored separately from an unknown amount.
        if (amount == null && !Boolean.TRUE.equals(free) && optional(value,"rawPriceText") == null) return 0;
        String kind = optional(value,"priceKind");
        if (kind == null) return 0;
        String key;
        if (legacyField.contains(":")) {
            String[] legacy = legacyField.split(":",2);
            key = hash(place + "|" + legacy[0] + "|" + legacy[1] + "|" + sourceUrl(patch) + "|" + amount);
        } else key = hash(place + "|" + sourceUrl(patch) + "|" + legacyField + "|" + value.toString());
        Object[] fields = {place, kind, basis, optional(value,"ticketName"), optional(value,"priceType"),
                optional(value,"ageBand"), optional(value,"dayType"), optional(value,"seasonLabel"),
                date(value,"validFrom"), date(value,"validUntil"), amount, Boolean.TRUE.equals(free),
                unit, number(value,"durationMinutes"), number(value,"useCount"), number(value,"passengerCount"),
                optional(value,"equipmentType"), flag(value,"equipmentIncluded"), flag(value,"mandatoryExtraCost"),
                optional(value,"residencyCondition"), optional(value,"rawPriceText"),
                value.path("additionalConditions").isMissingNode() ? null : value.path("additionalConditions").toString(),
                text(patch,"sourceType"), sourceUrl(patch), contentId == null || contentId.isBlank() ? null : contentId,
                checked(patch), key};
        if (fields[22].toString().isBlank()) return 0;
        for (int i=0; i<fields.length; i++) ps.setObject(i+1,fields[i]);
        return ps.executeUpdate();
    }
    static int insertPrices(PreparedStatement ps,long place,String field,JsonNode value,JsonNode patch,String contentId)throws Exception{
        int count=0;
        if (field.equals("prices") || field.equals("structuredPrices")) {
            JsonNode items = priceItems(value);
            if (items == null) return 0;
            for (JsonNode item : items) count += insertOnePrice(ps,place,item,patch,contentId,field);
            return count;
        }
        if (!field.equals("price") && !field.equals("weekendPrice")) return 0;
        if (!value.isObject()) return 0;
        for (String band : List.of("adult","youth","child","skateRental")) {
            JsonNode amount = value.path(band);
            if (!amount.isIntegralNumber() || amount.asInt() < 0) continue;
            boolean rental = band.equals("skateRental");
            tools.jackson.databind.node.ObjectNode row = JSON.createObjectNode();
            row.put("priceKind",rental ? "RENTAL" : "ADMISSION");
            row.put("ageBand",rental ? "ALL" : band.toUpperCase(java.util.Locale.ROOT));
            row.put("amountKrw",amount.asInt());
            row.put("priceUnit",rental ? "EQUIPMENT" : "PERSON");
            if (rental) row.put("equipmentType","SKATES");
            String day = text(value,"dayType");
            if (!day.isBlank()) row.put("dayType",day);
            count += insertOnePrice(ps,place,row,patch,contentId,field + ":" + band);
        }
        return count;
    }
    public static void main(String[] args)throws Exception{
        boolean apply=args.length==1&&"--apply".equals(args[0]);
        if(args.length>0&&!apply)throw new IllegalArgumentException("Only --apply is supported");
        String host=System.getenv().getOrDefault("MYSQL_HOST","127.0.0.1");
        if(apply&&!Set.of("127.0.0.1","localhost").contains(host))throw new IllegalArgumentException("Local DB only");
        JsonNode root=JSON.readTree(Files.readString(Path.of(env("OUTPICK_FILTERED_REVIEW_JSON"))));
        String url="jdbc:mysql://"+host+":"+System.getenv().getOrDefault("MYSQL_PORT","3306")+"/"+env("MYSQL_DATABASE");
        int patches=0,ambiguous=0,facts=0,prices=0;
        try(Connection db=DriverManager.getConnection(url,env("MYSQL_USER"),env("MYSQL_PASSWORD"))){
            db.setReadOnly(!apply);
            db.setAutoCommit(!apply);
            try(PreparedStatement fact=db.prepareStatement("""
                    INSERT IGNORE INTO place_detail_fact
                    (place_id,field_key,value_json,source_kind,source_url,source_content_id,checked_at,verification_status,evidence_key)
                    VALUES(?,?,?,?,?,?,?,'NEEDS_REVIEW',?)
                    """);PreparedStatement price=db.prepareStatement("""
                    INSERT IGNORE INTO place_price
                    (place_id,price_kind,price_basis,ticket_name,price_type,age_band,day_type,season_label,
                     valid_from,valid_until,amount_krw,free_of_charge,price_unit,duration_minutes,use_count,
                     passenger_count,equipment_type,equipment_included,mandatory_extra_cost,residency_condition,
                     raw_price_text,additional_conditions,source_kind,source_url,source_content_id,checked_at,
                     verification_status,evidence_key)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEEDS_REVIEW',?)
                    """)){
                List<JsonNode> records=new ArrayList<>();
                for(JsonNode item:root.path("records"))records.add(item);
                for(JsonNode item:records){
                    String id=text(item.path("place"),"id"),contentId=text(item.path("place"),"contentId");
                    for(JsonNode patch:item.path("officialPatches")){
                        patches++;Long place=target(db,patch,id);if(place==null){ambiguous++;continue;}
                        JsonNode values=patch.path("values");if(!values.isObject())values=patch.path("fields");
                        if(!values.isObject())continue;
                        for(String field:values.propertyNames()){
                            if(apply){facts+=insertFact(fact,place,field,values.path(field),patch,contentId);
                                prices+=insertPrices(price,place,field,values.path(field),patch,contentId);}
                            else {facts++;if(field.equals("price")||field.equals("weekendPrice"))prices+=4; else if(field.equals("prices")||field.equals("structuredPrices")){JsonNode items=priceItems(values.path(field));if(items!=null)prices+=items.size();}}
                        }
                    }
                }
                for(JsonNode patch:root.path("iceRinkPhase1").path("patches")){
                    patches++;Long place=target(db,patch,null);if(place==null){ambiguous++;continue;}
                    JsonNode values=patch.path("fields");if(!values.isObject())continue;
                    for(String field:values.propertyNames()){
                        if(apply){facts+=insertFact(fact,place,field,values.path(field),patch,"");
                            prices+=insertPrices(price,place,field,values.path(field),patch,"");}
                        else {facts++;if(field.equals("price")||field.equals("weekendPrice"))prices+=4; else if(field.equals("prices")||field.equals("structuredPrices")){JsonNode items=priceItems(values.path(field));if(items!=null)prices+=items.size();}}
                    }
                }
                if(apply)db.commit();
            }catch(Exception e){if(apply)db.rollback();throw e;}
        }
        System.out.printf("patches=%d ambiguous=%d facts=%d price_rows=%d published=0%n",patches,ambiguous,facts,prices);
    }
}
