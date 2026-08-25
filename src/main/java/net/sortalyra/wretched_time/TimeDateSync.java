package net.sortalyra.wretched_time;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.LocalDateTime;
import java.util.HashMap;

public class TimeDateSync {
    public static final HashMap<Level, Long> lastDayTimes = new HashMap<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {

        tickCounter++;

        if (tickCounter < 20) {
            return;
        }

        tickCounter = 0;
        MinecraftServer server = event.getServer();
        syncTime(server);
    }

    private static void syncTime(MinecraftServer server) {

        LocalDateTime now = LocalDateTime.now();

        int hour = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();

        double seconds = (hour * 3600.0) + (minute * 60.0) + second - (6*3600.0);

        long minecraftTime = Math.round((seconds / 86400.0) * 24000.0);

        int year = now.getYear();
        int month = now.getMonthValue();
        int day = Math.min(now.getDayOfMonth(), 30);

        long days = (((day) + ((month) * 30L))* 24000);




        for (ServerLevel level : server.getAllLevels()) {


            long dayTime = level.getDayTime();
            long lastDayTime = lastDayTimes.getOrDefault(level, dayTime);
            lastDayTimes.put(level, dayTime);

            long difference = dayTime - lastDayTime;
            if (difference == 0)
                return;

            if (difference < 0)
            {
                System.out.println("lastDayTime: "+lastDayTime);
                System.out.println("CurrentTime: "+dayTime);
                System.out.println("Difference: "+difference);
            }


            System.out.println("Time: "+ level.getDayTime());
            System.out.println("SetTime: "+ (days+minecraftTime));
            if (level.getDayTime() != (days+minecraftTime)) {
                level.setDayTime(days + minecraftTime);
            }
/*
            level.getGameRules()
                    .getRule(GameRules.RULE_DAYLIGHT)
                    .set(false, server);





            level.getGameRules()
                    .getRule(GameRules.RULE_DAYLIGHT)
                    .set(true, server);


            level.getGameRules()
                    .getRule(GameRules.RULE_DAYLIGHT)
                    .set(false, server);


 */

        }
    }
}