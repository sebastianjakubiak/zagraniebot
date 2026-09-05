package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballManualReviewRecord;
import java.util.*;

/** Read-only parser/validator for future reviewed CSV decisions. */
public final class FootballManualDecisionImporter {
    public List<ReviewRow> parseReviewRows(List<String> lines) {
        if (lines==null||lines.isEmpty()) throw new IllegalArgumentException("empty CSV");
        List<String> header=Csv.parse(lines.get(0).replace("\uFEFF", ""));
        Map<String,Integer> h=new HashMap<>(); for(int i=0;i<header.size();i++) h.put(header.get(i),i);
        for(String required:List.of("LegId","BetId","PostId","PostAuthor","Title","StateFingerprint","ManualDecision","ManualComment")) if(!h.containsKey(required)) throw new IllegalArgumentException("unexpected CSV header: missing "+required);
        Set<Long> seen=new HashSet<>(); List<ReviewRow> out=new ArrayList<>();
        for (int i=1;i<lines.size();i++) { if(lines.get(i).isBlank()) continue; List<String> c=Csv.parse(lines.get(i)); if(c.size()!=header.size()) throw new IllegalArgumentException("invalid CSV row "+(i+1)+" columns="+c.size()); long id=Long.parseLong(value(c,h,"LegId")); if(!seen.add(id)) throw new IllegalArgumentException("duplicate LegId "+id); String d=value(c,h,"ManualDecision").trim(); if(!Set.of("W","L","V","SKIP").contains(d)) throw new IllegalArgumentException("invalid decision for "+id+": "+d); String fp=value(c,h,"StateFingerprint"); if(fp.isBlank()) throw new IllegalArgumentException("empty fingerprint for "+id); out.add(new ReviewRow(id,Long.parseLong(value(c,h,"BetId")),Long.parseLong(value(c,h,"PostId")),Long.parseLong(value(c,h,"PostAuthor")),nullableLong(value(c,h,"FixtureId")),value(c,h,"FixtureDate"),value(c,h,"Home"),value(c,h,"Away"),value(c,h,"Score"),value(c,h,"Title"),value(c,h,"Family"),value(c,h,"Blocker"),value(c,h,"Evidence"),d,value(c,h,"ManualComment"),fp)); }
        return List.copyOf(out);
    }
    public List<Decision> parse(List<String> lines) {
        if (lines==null||lines.isEmpty()) return List.of();
        Set<Long> seen=new HashSet<>(); List<Decision> out=new ArrayList<>();
        for (int i=1;i<lines.size();i++) { if(lines.get(i).isBlank()) continue; List<String> c=Csv.parse(lines.get(i)); if(c.size()<3) throw new IllegalArgumentException("invalid CSV row "+(i+1)); long id=Long.parseLong(c.get(0)); if(!seen.add(id)) throw new IllegalArgumentException("duplicate LegId "+id); String d=c.get(c.size()-2).trim(); if(!Set.of("W","L","V","SKIP").contains(d)) throw new IllegalArgumentException("invalid decision for "+id); out.add(new Decision(id,d,c.get(c.size()-1))); }
        return List.copyOf(out);
    }
    public void validateCurrent(List<Decision> decisions, Set<Long>pendingIds, Map<Long,String>fingerprints) { for(var d:decisions){if(!pendingIds.contains(d.legId()))throw new IllegalArgumentException("leg is not pending or unknown: "+d.legId()); if(!fingerprints.containsKey(d.legId()))throw new IllegalArgumentException("missing state fingerprint: "+d.legId());} }
    public record Decision(long legId,String decision,String comment) {}
    private static Long nullableLong(String value) { if (value == null || value.isBlank()) return null; java.math.BigDecimal n=new java.math.BigDecimal(value.trim()); return n.longValueExact(); }
    private static String value(List<String> c, Map<String,Integer> h, String key) { Integer i=h.get(key); return i==null || i>=c.size() ? "" : c.get(i); }
    public record ReviewRow(long legId,long betId,long postId,long postAuthor,Long fixtureId,String fixtureDate,String home,String away,String score,String title,String family,String blocker,String evidence,String decision,String comment,String stateFingerprint) {}
    static final class Csv { static List<String> parse(String s){List<String> o=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){o.add(b.toString());b.setLength(0);}else b.append(c);}o.add(b.toString());return o;} }
}
