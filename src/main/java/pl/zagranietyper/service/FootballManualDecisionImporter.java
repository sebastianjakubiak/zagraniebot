package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballManualReviewRecord;
import java.util.*;

/** Read-only parser/validator for future reviewed CSV decisions. */
public final class FootballManualDecisionImporter {
    public List<Decision> parse(List<String> lines) {
        if (lines==null||lines.isEmpty()) return List.of();
        Set<Long> seen=new HashSet<>(); List<Decision> out=new ArrayList<>();
        for (int i=1;i<lines.size();i++) { if(lines.get(i).isBlank()) continue; List<String> c=Csv.parse(lines.get(i)); if(c.size()<3) throw new IllegalArgumentException("invalid CSV row "+(i+1)); long id=Long.parseLong(c.get(0)); if(!seen.add(id)) throw new IllegalArgumentException("duplicate LegId "+id); String d=c.get(c.size()-2).trim(); if(!Set.of("W","L","V","SKIP").contains(d)) throw new IllegalArgumentException("invalid decision for "+id); out.add(new Decision(id,d,c.get(c.size()-1))); }
        return List.copyOf(out);
    }
    public void validateCurrent(List<Decision> decisions, Set<Long>pendingIds, Map<Long,String>fingerprints) { for(var d:decisions){if(!pendingIds.contains(d.legId()))throw new IllegalArgumentException("leg is not pending or unknown: "+d.legId()); if(!fingerprints.containsKey(d.legId()))throw new IllegalArgumentException("missing state fingerprint: "+d.legId());} }
    public record Decision(long legId,String decision,String comment) {}
    static final class Csv { static List<String> parse(String s){List<String> o=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){o.add(b.toString());b.setLength(0);}else b.append(c);}o.add(b.toString());return o;} }
}
