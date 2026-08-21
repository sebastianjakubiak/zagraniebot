package pl.zagranietyper.parser;

import pl.zagranietyper.model.*;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.*;

/** Whole-title adapter for deterministic first/second-half score markets. */
public final class FootballHalfMarketSyntaxAdapter {
    private static final String GOALS="(?:gol|gola|gole|goli|bramka|bramki|bramek)";
    private static final String NUMBER="([0-9]+(?:[.,][0-9]+)?)";
    private static final String DIR="(powyzej|ponizej|over|under)";
    private static final String PERIOD_TEXT="(?:1[.]? polow(?:a|ie|y)?|pierwsz(?:a|ej) polow(?:a|ie|y)?|do przerwy|2[.]? polow(?:a|ie|y)?|drug(?:a|iej) polow(?:a|ie|y)?)";
    private static final Pattern PERIOD=Pattern.compile("("+PERIOD_TEXT+")");
    private static final Pattern UNSAFE=Pattern.compile("kart|rozn|strzal|faul|pierwsz(?:y|a) gol|1[.] gol|w kazdej polowie|w obu polowach|polowa lub|polowe lub|half time full time|ht ft");
    private static final Pattern COMPOSITE=Pattern.compile("(?: i | oraz |,|wygra mecz|btts|obie druzyny)");

    public ParseResult parse(String title,String home,String away){
        String text=normalize(title);Matcher periodMatcher=PERIOD.matcher(text);
        if(!periodMatcher.find())return rejected(Status.NOT_HALF_LIKE,text);
        FootballScorePeriod period=period(periodMatcher.group(1));
        if(UNSAFE.matcher(text).find())return rejected(Status.UNSUPPORTED_GRAMMAR,text);
        if(COMPOSITE.matcher(text).find())return rejected(Status.UNSUPPORTED_COMPOSITE,text);

        Matcher m=Pattern.compile("^[+-] ?"+NUMBER+" "+GOALS+" w "+PERIOD_TEXT+"$").matcher(text);
        if(m.matches())return total(text,period,text.startsWith("+")?"powyzej":"ponizej",m.group(1),Family.MATCH_TOTAL);
        m=Pattern.compile("^-?"+NUMBER+" "+GOALS+" w "+PERIOD_TEXT+"$").matcher(text);
        if(m.matches()&&text.startsWith("-"))return total(text,period,"ponizej",m.group(1),Family.MATCH_TOTAL);
        m=Pattern.compile("^(?:"+PERIOD_TEXT+" - )?"+DIR+" "+NUMBER+" "+GOALS+"(?: w "+PERIOD_TEXT+")?$").matcher(text);
        if(m.matches())return total(text,period,m.group(1),m.group(2),Family.MATCH_TOTAL);
        m=Pattern.compile("^1 polowa liczba goli ([0-9]+)[+]$").matcher(text);
        if(m.matches())return parsed(text,Family.MATCH_MINIMUM,new UnifiedFootballMarket.MinimumGoals(UnifiedFootballMarket.GoalSubject.MATCH,FootballScorePeriod.FIRST_HALF,Integer.parseInt(m.group(1))));

        m=Pattern.compile("^"+PERIOD_TEXT+" - (.+?) - liczba goli "+DIR+" "+NUMBER+"$").matcher(text);
        if(m.matches())return teamTotal(text,period,m.group(1),m.group(2),m.group(3),home,away);
        m=Pattern.compile("^(.+?) (?:strzeli )?"+DIR+" "+NUMBER+" "+GOALS+" w "+PERIOD_TEXT+"$").matcher(text);
        if(m.matches())return teamTotal(text,period,m.group(1),m.group(2),m.group(3),home,away);
        m=Pattern.compile("^(.+?) strzeli gola w "+PERIOD_TEXT+"$").matcher(text);
        if(m.matches())return teamScore(text,period,m.group(1),home,away);

        m=Pattern.compile("^(?:pierwsza polowa|do przerwy) ([12x])$").matcher(text);
        if(m.matches())return result(text,FootballScorePeriod.FIRST_HALF,m.group(1),null,home,away);
        m=Pattern.compile("^(.+?) wygra 1[.]? polowe?$").matcher(text);
        if(m.matches())return result(text,FootballScorePeriod.FIRST_HALF,null,m.group(1),home,away);
        m=Pattern.compile("^1[.]? polowa - wygrana (.+?)$").matcher(text);
        if(m.matches())return result(text,FootballScorePeriod.FIRST_HALF,null,m.group(1),home,away);
        m=Pattern.compile("^1[.]? polowa x$").matcher(text);
        if(m.matches())return result(text,FootballScorePeriod.FIRST_HALF,"x",null,home,away);
        m=Pattern.compile("^2[.]? polowa btts$").matcher(text);
        if(m.matches())return parsed(text,Family.HALF_BTTS,new UnifiedFootballMarket.BothTeamsToScore(FootballScorePeriod.SECOND_HALF,true));
        return rejected(Status.UNSUPPORTED_GRAMMAR,text);
    }

    private static ParseResult total(String n,FootballScorePeriod p,String d,String raw,Family f){return parsed(n,f,new UnifiedFootballMarket.TotalGoals(UnifiedFootballMarket.GoalSubject.MATCH,p,d.equals("powyzej")||d.equals("over")?UnifiedFootballMarket.TotalDirection.OVER:UnifiedFootballMarket.TotalDirection.UNDER,decimal(raw)));}
    private static ParseResult teamTotal(String n,FootballScorePeriod p,String subject,String d,String raw,String home,String away){var s=side(subject,home,away);if(s==null)return rejected(Status.PARTICIPANT_UNRESOLVED,n);return parsed(n,Family.TEAM_TOTAL,new UnifiedFootballMarket.TotalGoals(s,p,d.equals("powyzej")||d.equals("over")?UnifiedFootballMarket.TotalDirection.OVER:UnifiedFootballMarket.TotalDirection.UNDER,decimal(raw)));}
    private static ParseResult teamScore(String n,FootballScorePeriod p,String subject,String home,String away){var s=side(subject,home,away);return s==null?rejected(Status.PARTICIPANT_UNRESOLVED,n):parsed(n,Family.TEAM_TO_SCORE,new UnifiedFootballMarket.TeamToScore(s,p,true));}
    private static ParseResult result(String n,FootballScorePeriod p,String selection,String subject,String home,String away){UnifiedFootballMarket.ResultSelection r;if(selection!=null)r=switch(selection){case"1"->UnifiedFootballMarket.ResultSelection.HOME;case"2"->UnifiedFootballMarket.ResultSelection.AWAY;default->UnifiedFootballMarket.ResultSelection.DRAW;};else{var s=side(subject,home,away);if(s==null)return rejected(Status.PARTICIPANT_UNRESOLVED,n);r=s==UnifiedFootballMarket.GoalSubject.HOME?UnifiedFootballMarket.ResultSelection.HOME:UnifiedFootballMarket.ResultSelection.AWAY;}return parsed(n,Family.HALF_RESULT,new UnifiedFootballMarket.Result(r,p));}
    private static UnifiedFootballMarket.GoalSubject side(String raw,String home,String away){return switch(FootballTeamMarketParser.resolveParticipant(raw,home,away)){case HOME->UnifiedFootballMarket.GoalSubject.HOME;case AWAY->UnifiedFootballMarket.GoalSubject.AWAY;default->null;};}
    private static FootballScorePeriod period(String raw){return raw.startsWith("2")||raw.startsWith("drug")?FootballScorePeriod.SECOND_HALF:FootballScorePeriod.FIRST_HALF;}
    private static BigDecimal decimal(String raw){return new BigDecimal(raw.replace(',','.'));}
    private static String normalize(String v){if(v==null)return"";return Normalizer.normalize(v.replace('ł','l').replace('Ł','L'),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[✅❌⏳]","").replaceAll("[—–:]"," ").replaceAll("\\s+"," ").trim();}
    private static ParseResult parsed(String n,Family f,UnifiedFootballMarket.Condition c){return new ParseResult(Status.PARSED,n,f,new UnifiedFootballMarket(java.util.List.of(c)));}
    private static ParseResult rejected(Status s,String n){return new ParseResult(s,n,Family.UNSUPPORTED,null);}
    public enum Status{PARSED,NOT_HALF_LIKE,UNSUPPORTED_GRAMMAR,UNSUPPORTED_COMPOSITE,PARTICIPANT_UNRESOLVED}
    public enum Family{MATCH_TOTAL,MATCH_MINIMUM,TEAM_TOTAL,TEAM_TO_SCORE,HALF_RESULT,HALF_BTTS,UNSUPPORTED}
    public record ParseResult(Status status,String normalized,Family family,UnifiedFootballMarket market){public boolean parsed(){return status==Status.PARSED;}}
}
