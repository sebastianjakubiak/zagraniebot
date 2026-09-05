package pl.zagranietyper.notification;

import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedLeg;
import pl.zagranietyper.service.NewTipsPollingService;

public final class TelegramTipMessageFormatter {

    private TelegramTipMessageFormatter() {
    }

    public static String format(
            String tipsterName,
            NewTipsPollingService.DetectedBet detectedBet
    ) {
        ParsedBet bet =
                detectedBet.bet();

        StringBuilder message =
                new StringBuilder();

        message.append(
                "🔥 NOWY TYP — "
        );

        message.append(
                tipsterName
        );

        message.append(
                "\n\n"
        );

        message.append(
                detectedBet.articleTitle()
        );

        message.append(
                "\n"
        );

        message.append(
                betLabel(
                        bet
                )
        );

        if (
                bet.displayedOdds() != null
        ) {
            message.append(
                    " @"
            );

            message.append(
                    bet.displayedOdds()
                            .stripTrailingZeros()
                            .toPlainString()
            );
        }

        for (
                ParsedLeg leg :
                bet.legs()
        ) {
            message.append(
                    "\n• "
            );

            message.append(
                    leg.tipTitle()
            );

            if (
                    leg.tipOdds() != null
            ) {
                message.append(
                        " @"
                );

                message.append(
                        leg.tipOdds()
                                .stripTrailingZeros()
                                .toPlainString()
                );
            }

            if (
                    leg.operator() != null
                            && !leg.operator().isBlank()
            ) {
                message.append(
                        " — "
                );

                message.append(
                        leg.operator()
                );
            }
        }

        if (
                bet.type()
                        == BetType.MULTI_UNVERIFIED
        ) {
            message.append(
                    "\n⚠️ Grupowanie typów wymaga ręcznej weryfikacji."
            );
        }

        message.append(
                "\n\n"
        );

        message.append(
                detectedBet.articleUrl()
        );

        return message.toString();
    }

    private static String betLabel(
            ParsedBet bet
    ) {
        return switch (
                bet.type()
        ) {
            case SINGLE ->
                    "SINGLE";

            case COMBINED ->
                    "AKO";

            case MULTI_UNVERIFIED ->
                    "MULTI_UNVERIFIED";
        };
    }
}
