package net.sortalyra.wretched_time;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.time.ZoneId;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TIME_ZONE = BUILDER
            .comment("Time zone to sync with. Uses tz database zone names or a UTC offset.")
            .comment("(e.g. America/Phoenix or UTC+2")
            .define("timeZone", "UTC", Config::validateTimeZone);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateTimeZone(final Object obj) {
        return obj instanceof String timeZone && ZoneId.getAvailableZoneIds().contains(timeZone);
    }
}
