package com.example.auction.session.entity;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Card pool league filter selected by room host at create time. */
public enum PoolLeague {
    PREMIER_LEAGUE("Premier League", true),
    LA_LIGA("La Liga", false),
    SERIE_A("Serie A", false),
    BUNDESLIGA("Bundesliga", false),
    SUPER_LIG("Trendyol Super Lig", true);

    private final String displayName;
    private final boolean selectable;

    PoolLeague(String displayName, boolean selectable) {
        this.displayName = displayName;
        this.selectable = selectable;
    }

    public String displayName() {
        return displayName;
    }

    public boolean selectable() {
        return selectable;
    }

    public static PoolLeague fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultLeague();
        }
        try {
            return PoolLeague.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "poolLeague must be one of: " + java.util.Arrays.toString(values())
            );
        }
    }

    public static PoolLeague resolve(String raw) {
        PoolLeague league = fromWire(raw);
        if (!league.selectable) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "League not available yet: " + league.displayName()
            );
        }
        return league;
    }

    public static PoolLeague defaultLeague() {
        return PREMIER_LEAGUE;
    }

    /** Legacy Redis sessions may lack the field. */
    public static PoolLeague effective(PoolLeague stored) {
        return stored != null ? stored : defaultLeague();
    }

    public static PoolLeague effective(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return defaultLeague();
        }
        try {
            return PoolLeague.valueOf(storedName);
        } catch (IllegalArgumentException ex) {
            return defaultLeague();
        }
    }
}
