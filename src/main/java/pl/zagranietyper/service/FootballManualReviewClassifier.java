package pl.zagranietyper.service;

import pl.zagranietyper.repository.FootballSettlementRepository.Candidate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.HexFormat;

public final class FootballManualReviewClassifier {
    public Result classify(Candidate c) {
        String n = normalize(c.tipTitle());
        String family;
        String blocker;
        if (n.matches(".*(zawodnik|strzeli gola|gole|asyst|strzal|celn|faul|spal|tackl|obron|interwenc|save|minut).*")) {
            family="PLAYER_PROP"; blocker="PLAYER_UNRESOLVED";
        } else if (n.matches(".*(kartk|kart|booking|punkt.{0,3}kart).*")) {
            family=n.contains("zawodnik")?"PLAYER_PROP":(n.contains("i ")||n.contains(" oraz ")?"COMPOSITE":"GENERIC_CARDS");
            blocker="BOOKMAKER_RULE_UNKNOWN";
        } else if (n.matches(".*(pierwsz.{0,8}gol|kolejn.{0,8}gol|do [0-9]+ min|w [0-9]+ min).*")) {
            family="SCORING_EVENT"; blocker="AMBIGUOUS_SEMANTICS";
        } else if (n.matches(".*(awans|zakwalifik|mistrz|zwycięzc|zwyciezc|wygra lig|out-right).*")) {
            family="ADVANCEMENT_OR_OUTRIGHT"; blocker="AMBIGUOUS_SEMANTICS";
        } else if (n.matches(".*(więcej.{0,20}niż|wiecej.{0,20}niz|mniej.{0,20}niż|mniej.{0,20}niz|\bvs\b|porówn|porown|przewag).*")) {
            family="STAT_COMPARISON"; blocker="BOOKMAKER_RULE_UNKNOWN";
        } else if (n.matches(".*(połow|polow|kwart|częśc|czesc|każdej|kazdej|half).*")) {
            family="PERIOD_STATISTIC"; blocker="UNSUPPORTED_GRAMMAR";
        } else if (n.contains(" i ") || n.contains(" oraz ") || n.contains(" + ") || n.contains(" lub ")) {
            family="COMPOSITE"; blocker="COMPOSITE_BRANCH_UNSUPPORTED";
        } else if (n.matches(".*(gol|bramk|wygra|remis|nie przegra|btts|obie druż|obie druz).*")) {
            family="GOAL_RESULT_UNKNOWN"; blocker="UNSUPPORTED_GRAMMAR";
        } else if (n.matches(".*(rzut|rożn|rozny|strzał|strzal|faul|spalony|ofsajd|saves|interwenc|tackle).*")) {
            family="OTHER_STATISTIC"; blocker="UNSUPPORTED_GRAMMAR";
        } else {
            family="UNCLASSIFIED"; blocker="OTHER";
        }
        return new Result(family, blocker);
    }
    public static String normalize(String s) { return s==null?"":s.toLowerCase(Locale.ROOT).replace('ł','l').replace('ą','a').replace('ę','e').replace('ż','z').replace('ź','z').replace('ś','s').replace('ć','c').replace('ń','n').replaceAll("\\s+"," ").trim(); }
    public static String fingerprint(Candidate c) throws Exception { String s=c.legId()+"|"+c.betId()+"|"+c.tipTitle()+"|PENDING|NONE"; return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    public record Result(String family,String blocker) {}
}
