package pl.zagranietyper.model;

public enum SettlementDecision {

    W,
    L,
    V,

    /*
     * Wewnętrzny wynik silnika.
     *
     * Nigdy nie zapisujemy UNSUPPORTED do settlement_status,
     * bo DB dopuszcza wyłącznie:
     *
     * PENDING / W / L / V
     *
     * UNSUPPORTED oznacza:
     * zostaw leg jako PENDING / NONE.
     */
    UNSUPPORTED
}