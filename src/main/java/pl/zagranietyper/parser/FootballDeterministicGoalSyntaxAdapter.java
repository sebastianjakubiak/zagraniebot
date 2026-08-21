package pl.zagranietyper.parser;

import pl.zagranietyper.model.FootballScorePeriod;
import pl.zagranietyper.model.UnifiedFootballMarket;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative whole-title adapter for remaining deterministic full-time goal markets. */
public final class FootballDeterministicGoalSyntaxAdapter {
    private static final String GOAL="(?:gol|gole|gola|goli|bramka|bramke|bramki|bramek)";
    private static final Pattern SIGNED=Pattern.compile("^(.+?)\\s+\\+([0-9]+)[.,]5\\s+goli$");
    private static final Pattern MIN_AFTER=Pattern.compile("^(.+?)\\s+(?:strzeli|zdobedzie)\\s+(?:co najmniej|min\\.?)\\s+([0-9]+)\\s+"+GOAL+"$");
    private static final Pattern MIN_BEFORE=Pattern.compile("^(?:przynajmniej|co najmniej)\\s+(?:jeden|jedna|1)\\s+"+GOAL+"\\s+(.+)$");
    private static final Pattern SIMPLE_SCORE=Pattern.compile("^(.+?)\\s+(?:strzeli|zdobedzie)\\s+"+GOAL+"$");
    private static final Pattern GOAL_TEAM=Pattern.compile("^(?:gol|bramka)\\s+(.+?)(?:\\s+przeciwko\\s+.+)?$");
    private static final Pattern NO_GOAL=Pattern.compile("^(.+?)\\s+bez\\s+(?:bramki|gola|goli)$");
    private static final Pattern RANGE=Pattern.compile("^zakres liczby goli:?\\s*([0-9]+)\\s*-\\s*([0-9]+)$");
    private static final Pattern EITHER=Pattern.compile("^(?:gol|bramka)\\s+(.+?)\\s+lub\\s+(.+)$");
    private static final Pattern BTTS=Pattern.compile("^(?:btts|oba zespoly strzela (?:gola|gole)|obie druzyny strzela (?:gola|gole)|gole obu ekip|gole z obu stron|bramki z obu stron)(?::?\\s*(tak|nie))?$");
    private static final Pattern BTTS_NO=Pattern.compile("^(?:obie druzyny|oba zespoly) nie strzela (?:gola|goli)$");
    private static final Pattern GOALISH=Pattern.compile("\\b(gol|gole|gola|goli|bramka|bramke|bramki|bramek|strzeli|zdobedzie|btts)\\b");
    private static final Pattern UNSAFE=Pattern.compile("\\b(polow|minucie|zawodnik|wygra|awans|kart|rzutow rozn|strzal|asyst|handicap)\\b");

    public ParseResult parse(String title,String home,String away){
        if(title==null||title.isBlank())return reject(Status.NOT_GOAL_LIKE);
        String t=normalize(title);
        if(!GOALISH.matcher(t).find())return reject(Status.NOT_GOAL_LIKE);
        if(!Character.isLetterOrDigit(title.stripLeading().codePointAt(0)))return reject(Status.UNSUPPORTED);
        if(UNSAFE.matcher(t).find()||t.contains(" i ")||t.contains(" / "))return reject(Status.UNSUPPORTED);
        Matcher m;
        if((m=SIGNED.matcher(t)).matches()){BigDecimal line=new BigDecimal(m.group(2)+".5");return team(Family.SIGNED_TEAM_GOALS,m.group(1),home,away,s->new UnifiedFootballMarket.TotalGoals(s,FootballScorePeriod.FULL_TIME,UnifiedFootballMarket.TotalDirection.OVER,line));}
        if((m=MIN_AFTER.matcher(t)).matches()){int n=Integer.parseInt(m.group(2));return team(Family.TEAM_MINIMUM,m.group(1),home,away,s->new UnifiedFootballMarket.MinimumGoals(s,FootballScorePeriod.FULL_TIME,n));}
        if((m=MIN_BEFORE.matcher(t)).matches())return team(Family.TEAM_TO_SCORE,m.group(1),home,away,s->new UnifiedFootballMarket.TeamToScore(s,FootballScorePeriod.FULL_TIME,true));
        if((m=NO_GOAL.matcher(t)).matches())return team(Family.TEAM_NOT_TO_SCORE,m.group(1),home,away,s->new UnifiedFootballMarket.TeamToScore(s,FootballScorePeriod.FULL_TIME,false));
        if((m=SIMPLE_SCORE.matcher(t)).matches())return team(Family.TEAM_TO_SCORE,m.group(1),home,away,s->new UnifiedFootballMarket.TeamToScore(s,FootballScorePeriod.FULL_TIME,true));
        if(t.matches("^goscie strzela gola:? tak$")||t.matches("^przynajmniej jeden gol gosci$")||t.matches("^goscie zdobeda co najmniej jedna bramke$"))return parsed(Family.TEAM_TO_SCORE,UnifiedFootballMarket.GoalSubject.AWAY,new UnifiedFootballMarket.TeamToScore(UnifiedFootballMarket.GoalSubject.AWAY,FootballScorePeriod.FULL_TIME,true));
        if((m=RANGE.matcher(t)).matches()){int a=Integer.parseInt(m.group(1)),b=Integer.parseInt(m.group(2));if(b<a)return reject(Status.UNSUPPORTED);return parsed(Family.MATCH_RANGE,UnifiedFootballMarket.GoalSubject.MATCH,new UnifiedFootballMarket.GoalRange(UnifiedFootballMarket.GoalSubject.MATCH,FootballScorePeriod.FULL_TIME,a,b));}
        if(BTTS_NO.matcher(t).matches())return parsed(Family.BOTH_TEAMS_TO_SCORE,UnifiedFootballMarket.GoalSubject.MATCH,new UnifiedFootballMarket.BothTeamsToScore(FootballScorePeriod.FULL_TIME,false));
        if((m=BTTS.matcher(t)).matches()){boolean yes=m.group(1)==null||!m.group(1).equals("nie");return parsed(Family.BOTH_TEAMS_TO_SCORE,UnifiedFootballMarket.GoalSubject.MATCH,new UnifiedFootballMarket.BothTeamsToScore(FootballScorePeriod.FULL_TIME,yes));}
        if((m=EITHER.matcher(t)).matches()){
            var a=resolve(m.group(1),home,away);var b=resolve(m.group(2),home,away);
            if(a!=null&&b!=null&&a!=b)return parsed(Family.EITHER_TEAM_TO_SCORE,UnifiedFootballMarket.GoalSubject.MATCH,new UnifiedFootballMarket.MinimumGoals(UnifiedFootballMarket.GoalSubject.MATCH,FootballScorePeriod.FULL_TIME,1));
        }
        if((m=GOAL_TEAM.matcher(t)).matches())return team(Family.TEAM_TO_SCORE,m.group(1),home,away,s->new UnifiedFootballMarket.TeamToScore(s,FootballScorePeriod.FULL_TIME,true));
        return reject(Status.UNSUPPORTED);
    }

    private static ParseResult team(Family f,String raw,String home,String away,java.util.function.Function<UnifiedFootballMarket.GoalSubject,UnifiedFootballMarket.Condition> make){var side=resolve(raw,home,away);return side==null?reject(Status.PARTICIPANT_UNRESOLVED):parsed(f,side,make.apply(side));}
    private static UnifiedFootballMarket.GoalSubject resolve(String raw,String home,String away){return switch(FootballTeamMarketParser.resolveParticipant(raw,home,away)){case HOME->UnifiedFootballMarket.GoalSubject.HOME;case AWAY->UnifiedFootballMarket.GoalSubject.AWAY;default->null;};}
    private static ParseResult parsed(Family family,UnifiedFootballMarket.GoalSubject subject,UnifiedFootballMarket.Condition condition){return new ParseResult(Status.PARSED,family,subject,new UnifiedFootballMarket(List.of(condition)),normalize(condition.toString()));}
    private static ParseResult reject(Status status){return new ParseResult(status,null,null,null,null);}
    private static String normalize(String s){return Normalizer.normalize(s.replace('ł','l').replace('Ł','L'),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}.,+\\-/]+"," ").replaceAll("\\s+"," ").trim();}
    public enum Status{PARSED,NOT_GOAL_LIKE,PARTICIPANT_UNRESOLVED,UNSUPPORTED}
    public enum Family{TEAM_TO_SCORE,TEAM_NOT_TO_SCORE,TEAM_MINIMUM,MATCH_RANGE,BOTH_TEAMS_TO_SCORE,EITHER_TEAM_TO_SCORE,SIGNED_TEAM_GOALS}
    public record ParseResult(Status status,Family family,UnifiedFootballMarket.GoalSubject subject,UnifiedFootballMarket market,String normalizedCondition){public boolean parsed(){return status==Status.PARSED;}}
}
