package pl.zagranietyper.parser;

import pl.zagranietyper.model.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public final class FootballPeriodStatisticSyntaxAdapter {
    private static final Pattern FH=Pattern.compile("^(.+?) \\+([0-9]+[.,][0-9]+) (?:rzutow roznych|roznych) w 1[.]? polowie$");
    private static final Pattern EACH=Pattern.compile("^(.+?) \\+([0-9]+[.,][0-9]+) (?:rzutow roznych|roznych) w kazdej polowie$");
    public Result parse(String title,String home,String away){String n=norm(title);if(!(n.contains("polow")&&(n.contains("rozn")||n.contains("strzal")||n.contains("faul")||n.contains("kart"))))return reject(Status.NOT_LIKE);if(n.contains("kart"))return reject(Status.GENERIC_CARDS);if(n.contains(" i ")||n.contains(" oraz ")||n.contains("wiecej")||n.contains("mniej"))return reject(Status.UNSUPPORTED);Matcher m=EACH.matcher(n);boolean each=m.matches();if(!each){m=FH.matcher(n);if(!m.matches())return reject(Status.UNSUPPORTED);}var side=FootballTeamMarketParser.resolveParticipant(m.group(1),home,away);if(side!=FootballParticipantResolver.Resolution.HOME&&side!=FootballParticipantResolver.Resolution.AWAY)return reject(Status.PARTICIPANT_UNRESOLVED);var subject=side==FootballParticipantResolver.Resolution.HOME?FootballFixtureStatisticCondition.Subject.HOME:FootballFixtureStatisticCondition.Subject.AWAY;var base=new FootballFixtureStatisticCondition(FootballFixtureStatisticType.CORNERS,subject,FootballFixtureStatisticCondition.Comparison.OVER,new BigDecimal(m.group(2).replace(',','.')),null);return new Result(Status.PARSED,new FootballPeriodStatisticCondition(each?FootballPeriodStatisticsSnapshot.Period.FIRST_HALF:FootballPeriodStatisticsSnapshot.Period.FIRST_HALF,base,each),each?Family.EACH_HALF_CORNERS:Family.FIRST_HALF_CORNERS,n);}
    private static Result reject(Status s){return new Result(s,null,null,null);}private static String norm(String s){return Normalizer.normalize(s.replace('ł','l').replace('Ł','L'),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[–—-]"," ").replaceAll("[^\\p{L}\\p{N}+., ]+"," ").replaceAll("\\s+"," ").trim();}
    public enum Status{PARSED,NOT_LIKE,UNSUPPORTED,PARTICIPANT_UNRESOLVED,GENERIC_CARDS}public enum Family{FIRST_HALF_CORNERS,SECOND_HALF_CORNERS,EACH_HALF_CORNERS,PERIOD_SHOTS,PERIOD_FOULS,EXPLICIT_PERIOD_CARDS,OTHER}public record Result(Status status,FootballPeriodStatisticCondition condition,Family family,String normalized){public boolean parsed(){return status==Status.PARSED;}}
}
