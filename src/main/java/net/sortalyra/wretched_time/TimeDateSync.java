package net.sortalyra.wretched_time;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import sereneseasons.season.SeasonHandler;
import sereneseasons.season.SeasonSavedData;
import sereneseasons.season.SeasonTime;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@EventBusSubscriber(modid = WretchedTime.MODID)
public class TimeDateSync {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        final int UPDATE_SECONDS = 5;
        final int UPDATE_TICKS = UPDATE_SECONDS * 20;

        tickCounter++;

        if (tickCounter < UPDATE_TICKS) {
            return;
        }

        tickCounter = 0;
        MinecraftServer server = event.getServer();
        syncTime(server);
    }

    private static void syncTime(MinecraftServer server) {
        ZonedDateTime time = ZonedDateTime.now(ZoneId.of(Config.TIME_ZONE.get(), ZoneId.SHORT_IDS));

        long currentDayTime = getCurrentDayTime(time);

        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules()
                    .getRule(GameRules.RULE_DAYLIGHT)
                    .set(false, server);
//            level.getGameRules()
//                    .getRule(SSGameRules.RULE_DOSEASONCYCLE)
//                    .set(false, server);

            long currentGameTime = level.getDayTime();
            long difference = currentDayTime - currentGameTime;

            if (difference == 0)
                return;

            if (difference < 0)
            {
                System.out.println("currentDayTime: " + currentDayTime);
                System.out.println("CurrentGameTime: " + currentGameTime);
                System.out.println("Difference: " + difference);
            }

            if (level.getDayTime() != currentDayTime) {
                level.setDayTime(currentDayTime);
            }

            currentDayTime += 24000 / 24 * 6; // minecraft days start at 6, adjust so the season day starts at midnight

            SeasonSavedData savedData = SeasonHandler.getSeasonSavedData(level);
            savedData.seasonCycleTicks = Mth.positiveModulo((int)(currentDayTime), SeasonTime.ZERO.getCycleDuration());

            SeasonHandler.sendSeasonUpdate(level);
            savedData.setDirty();
        }
    }

    private static long getCurrentDayTime(ZonedDateTime time) {
        final double SECONDS_PER_MINUTE = 60;
        final double SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
        final double SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;
        final double TICKS_PER_DAY = 24000;

        final double MINECRAFT_START_TIME = 6; // minecraft tick 0 is at 6 o'clock

        int hour = time.getHour();
        int minute = time.getMinute();
        int second = time.getSecond();

        double seconds = (hour - MINECRAFT_START_TIME) * SECONDS_PER_HOUR + (minute * SECONDS_PER_MINUTE) + second;
        long timeInTicks = Math.round((seconds / SECONDS_PER_DAY) * TICKS_PER_DAY);

        int month = time.getMonthValue();
        int day = Math.min(time.getDayOfMonth(), 30);
        // tick 0 is March 1st,
        long daysInTicks = ((month - 3) * 30 + (day - 1)) * 24000;

        return daysInTicks + timeInTicks;
    }
}