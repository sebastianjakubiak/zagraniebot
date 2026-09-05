package pl.zagranietyper.notification;

public final class TelegramNotificationPolicy {

    private TelegramNotificationPolicy() {
    }

    public static boolean shouldSend(
            boolean enabled,
            boolean dryRun,
            boolean bootstrap,
            boolean notifyBootstrap
    ) {
        return enabled
                && !dryRun
                && (
                !bootstrap
                        || notifyBootstrap
        );
    }
}
