package pl.zagranietyper.parser;

import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Whole-market adapter for deterministic full-time shots and shots-on-target markets. */
public final class FootballShotsSyntaxAdapter {
    private static final String DIRECTION="(powyzej|ponizej|ponad|wiecej niz|mniej niz|over|under)";
    private static final String NUMBER="([0-9]+(?:[.,][0-9]+)?)";
    private static final String TOTAL="(?:strzal(?:u|y|ow)?)";
    private static final String ON_TARGET="(?:celn(?:y|e|ych|ego) strzal(?:u|y|ow)?|strzal(?:u|y|ow)? celn(?:y|e|ych)|strzal(?:u|y|ow)? w swiatlo bramki)";
    private static final Pattern PERIOD=Pattern.compile("\\b(?:1|2|pierwszej|drugiej) polow|\\bpolow|do przerwy|half|w kazdej polowie|w obu polowach");
    private static final Pattern OTHER_MARKET=Pattern.compile("\\b(?:gol|gola|gole|goli|bramk|strzeli|btts|kart|rozn|corner|faul|wygra|wygrana|zwyciestwo|zwyciezy|remis|awans|nie przegra)\\w*");
    private static final Pattern PLAYER_INITIAL=Pattern.compile("^[a-z]{1,3}[.]\\s+[^ ]+ .*");
    private static final Pattern HANDICAP_OR_COMPARISON=Pattern.compile("(?:^| )(?:handicap|hcp)(?: |$)|wiecej strzal(?:u|y|ow)? (?:od|niz)|mniej strzal(?:u|y|ow)? (?:od|niz)|liczba .*strzal.* [12][.]?druzyna");
    private static final Pattern RAW_SIGNED=Pattern.compile("(?:^|[^0-9])[+-]\\s*[0-9]");

    public ParseResult parse(String tipTitle,String homeTeam,String awayTeam) {
        if(tipTitle==null||tipTitle.isBlank()) return rejected(Status.NOT_SHOTS_LIKE,null,MarketFamily.AMBIGUOUS_GRAMMAR);
        String text=normalize(tipTitle);
        FootballFixtureStatisticType type=detectType(text);
        if(type==null) return rejected(Status.NOT_SHOTS_LIKE,null,MarketFamily.AMBIGUOUS_GRAMMAR);
        if(PERIOD.matcher(text).find()) return rejected(Status.UNSUPPORTED_PERIOD,type,MarketFamily.PERIOD_SPECIFIC);
        String compositeScan=text.replace("w swiatlo bramki","");
        if(OTHER_MARKET.matcher(compositeScan).find()||text.startsWith("obie druzyny ")||text.startsWith("kazda druzyna ")||text.startsWith("oba zespoly "))
            return rejected(Status.UNSUPPORTED_COMPOSITE,type,MarketFamily.COMPOSITE);
        if(PLAYER_INITIAL.matcher(text).matches()) return rejected(Status.UNSUPPORTED_PLAYER,type,MarketFamily.PLAYER_SPECIFIC);
        if(HANDICAP_OR_COMPARISON.matcher(text).find()) return rejected(Status.UNSUPPORTED_HANDICAP_OR_COMPARISON,type,MarketFamily.HANDICAP_OR_COMPARISON);

        String statistic=type==FootballFixtureStatisticType.SHOTS_ON_TARGET?ON_TARGET:TOTAL;
        Matcher m=Pattern.compile("^(.+?) ([+-]) ?([0-9]+[.,]5) "+statistic+"$").matcher(text);
        if(m.matches()) {
            Resolution r=resolve(m.group(1),homeTeam,awayTeam);
            if(!r.parsed()) return rejected(Status.UNSUPPORTED_PLAYER,type,MarketFamily.PLAYER_SPECIFIC);
            var comparison="+".equals(m.group(2))?FootballFixtureStatisticCondition.Comparison.OVER:FootballFixtureStatisticCondition.Comparison.UNDER;
            return parsed(type,MarketFamily.SIGNED_SHORTHAND,Category.TEAM_TOTAL,
                    FootballFixtureStatisticCondition.threshold(type,r.subject(),comparison,decimal(m.group(3))),SyntaxFamily.SIGNED_TEAM);
        }
        if(RAW_SIGNED.matcher(tipTitle).find()) return rejected(Status.UNSUPPORTED_SIGNED_NOTATION,type,MarketFamily.SIGNED_SHORTHAND);

        m=Pattern.compile("^"+DIRECTION+" "+NUMBER+" "+statistic+"$").matcher(text);
        if(m.matches()) return total(type,MarketFamily.MATCH_TOTAL,Category.MATCH_TOTAL,FootballFixtureStatisticCondition.Subject.MATCH,m.group(1),m.group(2));
        m=Pattern.compile("^liczba "+statistic+" "+DIRECTION+" "+NUMBER+"$").matcher(text);
        if(m.matches()) return total(type,MarketFamily.MATCH_TOTAL,Category.MATCH_TOTAL,FootballFixtureStatisticCondition.Subject.MATCH,m.group(1),m.group(2));
        m=Pattern.compile("^(?:w meczu obejrzymy )?(?:min[.]?|minimum|co najmniej) ([0-9]+) "+statistic+"$").matcher(text);
        if(m.matches()) return minimum(type,MarketFamily.MATCH_TOTAL,Category.MATCH_TOTAL,FootballFixtureStatisticCondition.Subject.MATCH,m.group(1));
        m=Pattern.compile("^(?:przedzial|liczba) "+statistic+" ([0-9]+) ?- ?([0-9]+)$").matcher(text);
        if(m.matches()) return range(type,MarketFamily.MATCH_TOTAL,Category.MATCH_TOTAL,FootballFixtureStatisticCondition.Subject.MATCH,m.group(1),m.group(2));

        if(type==FootballFixtureStatisticType.SHOTS_ON_TARGET) {
            m=Pattern.compile("^(.+?) strzaly celne "+DIRECTION+" "+NUMBER+"$").matcher(text);
            if(m.matches()) return teamTotal(type,m.group(1),m.group(2),m.group(3),homeTeam,awayTeam);
        }
        m=Pattern.compile("^(.+?) "+DIRECTION+" "+NUMBER+" "+statistic+"$").matcher(text);
        if(m.matches()) return teamTotal(type,m.group(1),m.group(2),m.group(3),homeTeam,awayTeam);
        m=Pattern.compile("^(.+?) (?:odda|odnotuje|zanotuje) (?:min[.]?|minimum|co najmniej) ([0-9]+|jeden|jedna|cztery) "+statistic+"$").matcher(text);
        if(m.matches()) return teamMinimum(type,m.group(1),wordNumber(m.group(2)),homeTeam,awayTeam);
        m=Pattern.compile("^(.+?) (?:przedzial|liczba) "+statistic+" ([0-9]+) ?- ?([0-9]+)$").matcher(text);
        if(m.matches()) return teamRange(type,m.group(1),m.group(2),m.group(3),homeTeam,awayTeam);
        return rejected(Status.UNSUPPORTED_GRAMMAR,type,MarketFamily.AMBIGUOUS_GRAMMAR);
    }

    private static ParseResult teamTotal(FootballFixtureStatisticType type,String raw,String direction,String number,String home,String away){
        Resolution r=resolve(raw,home,away);
        if(!r.parsed()) return rejected(playerLike(raw)?Status.UNSUPPORTED_PLAYER:r.status(),type,playerLike(raw)?MarketFamily.PLAYER_SPECIFIC:MarketFamily.TEAM_TOTAL);
        return total(type,MarketFamily.TEAM_TOTAL,Category.TEAM_TOTAL,r.subject(),direction,number);
    }
    private static ParseResult teamMinimum(FootballFixtureStatisticType type,String raw,String number,String home,String away){
        Resolution r=resolve(raw,home,away);
        if(!r.parsed()) return rejected(Status.UNSUPPORTED_PLAYER,type,MarketFamily.PLAYER_SPECIFIC);
        return minimum(type,MarketFamily.TEAM_MINIMUM,Category.TEAM_MINIMUM,r.subject(),number);
    }
    private static ParseResult teamRange(FootballFixtureStatisticType type,String raw,String min,String max,String home,String away){
        Resolution r=resolve(raw,home,away);
        if(!r.parsed()) return rejected(playerLike(raw)?Status.UNSUPPORTED_PLAYER:r.status(),type,playerLike(raw)?MarketFamily.PLAYER_SPECIFIC:MarketFamily.TEAM_RANGE);
        return range(type,MarketFamily.TEAM_RANGE,Category.TEAM_RANGE,r.subject(),min,max);
    }
    private static ParseResult total(FootballFixtureStatisticType type,MarketFamily family,Category category,FootballFixtureStatisticCondition.Subject subject,String direction,String number){
        var comparison=switch(direction){case "powyzej","ponad","wiecej niz","over"->FootballFixtureStatisticCondition.Comparison.OVER;case "ponizej","mniej niz","under"->FootballFixtureStatisticCondition.Comparison.UNDER;default->throw new IllegalArgumentException(direction);};
        return parsed(type,family,category,FootballFixtureStatisticCondition.threshold(type,subject,comparison,decimal(number)),SyntaxFamily.UNSIGNED);
    }
    private static ParseResult minimum(FootballFixtureStatisticType type,MarketFamily family,Category category,FootballFixtureStatisticCondition.Subject subject,String number){
        return parsed(type,family,category,FootballFixtureStatisticCondition.threshold(type,subject,FootballFixtureStatisticCondition.Comparison.MINIMUM,decimal(number)),SyntaxFamily.UNSIGNED);
    }
    private static ParseResult range(FootballFixtureStatisticType type,MarketFamily family,Category category,FootballFixtureStatisticCondition.Subject subject,String rawMin,String rawMax){
        BigDecimal min=decimal(rawMin),max=decimal(rawMax); if(max.compareTo(min)<0)return rejected(Status.UNSUPPORTED_GRAMMAR,type,MarketFamily.AMBIGUOUS_GRAMMAR);
        return parsed(type,family,category,FootballFixtureStatisticCondition.range(type,subject,min,max),SyntaxFamily.UNSIGNED);
    }
    private static FootballFixtureStatisticType detectType(String text){
        if(text.matches(".*(?:celn.*strzal|strzal.*celn|strzal.*swiatlo bramki|shots on target).*$"))return FootballFixtureStatisticType.SHOTS_ON_TARGET;
        if(text.matches(".*(?:strzal|shots?).*$"))return FootballFixtureStatisticType.SHOTS_TOTAL;
        return null;
    }
    private static boolean playerLike(String raw){return raw.matches("[a-z]{1,3}[.] .+")||raw.trim().split(" ").length>=2;}
    private static Resolution resolve(String raw,String home,String away){return switch(FootballTeamMarketParser.resolveParticipant(raw,home,away)){case HOME->new Resolution(FootballFixtureStatisticCondition.Subject.HOME,null);case AWAY->new Resolution(FootballFixtureStatisticCondition.Subject.AWAY,null);case AMBIGUOUS,UNRESOLVED->new Resolution(null,Status.AMBIGUOUS_PARTICIPANT);};}
    private static String wordNumber(String raw){return switch(raw){case "jeden","jedna"->"1";case "cztery"->"4";default->raw;};}
    private static BigDecimal decimal(String raw){return new BigDecimal(raw.replace(',','.'));}
    private static String normalize(String value){return Normalizer.normalize(value.replace('ł','l').replace('Ł','L'),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}.,+\\-]+"," ").replaceAll("\\s+"," ").trim().replaceAll("[.]$","").replaceAll("\\s*[—–]\\s*"," ").replaceAll("\\s+-\\s+"," ");}
    private static ParseResult parsed(FootballFixtureStatisticType type,MarketFamily family,Category category,FootballFixtureStatisticCondition c,SyntaxFamily syntax){return new ParseResult(Status.PARSED,type,family,category,c,syntax);}
    private static ParseResult rejected(Status status,FootballFixtureStatisticType type,MarketFamily family){return new ParseResult(status,type,family,null,null,SyntaxFamily.UNSIGNED);}

    public enum Category{MATCH_TOTAL,TEAM_TOTAL,TEAM_MINIMUM,TEAM_RANGE}
    public enum MarketFamily{MATCH_TOTAL,TEAM_TOTAL,TEAM_MINIMUM,TEAM_RANGE,SIGNED_SHORTHAND,HANDICAP_OR_COMPARISON,COMPOSITE,PERIOD_SPECIFIC,PLAYER_SPECIFIC,AMBIGUOUS_GRAMMAR}
    public enum SyntaxFamily{UNSIGNED,SIGNED_TEAM}
    public enum Status{PARSED,NOT_SHOTS_LIKE,AMBIGUOUS_PARTICIPANT,UNSUPPORTED_PERIOD,UNSUPPORTED_PLAYER,UNSUPPORTED_HANDICAP_OR_COMPARISON,UNSUPPORTED_COMPOSITE,UNSUPPORTED_SIGNED_NOTATION,UNSUPPORTED_GRAMMAR}
    public record ParseResult(Status status,FootballFixtureStatisticType statisticType,MarketFamily marketFamily,Category category,FootballFixtureStatisticCondition condition,SyntaxFamily syntaxFamily){public boolean parsed(){return status==Status.PARSED;}}
    private record Resolution(FootballFixtureStatisticCondition.Subject subject,Status status){boolean parsed(){return subject!=null;}}
}
