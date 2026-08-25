package net.sortalyra.wretched_time;

import glitchcore.event.EventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonChangedEvent;
import sereneseasons.init.ModConfig;
import sereneseasons.init.ModPackets;
import sereneseasons.network.SyncSeasonCyclePacket;
import sereneseasons.season.SeasonSavedData;
import sereneseasons.season.SeasonTime;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.function.Supplier;

@EventBusSubscriber(modid = WretchedTime.MODID)
public class TimeDateSync {
    private static final String SAVED_DATA_CLASS = "sereneseasons.season.SeasonSavedData";
    private static final String DATA_IDENTIFIER = "seasons";

    public static final HashMap<Level, Long> lastDayTimes = new HashMap<>();
    public static final HashMap<Level, Integer> updateTicks = new HashMap<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        final int UPDATE_SECONDS = 1;
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

        ZonedDateTime time = ZonedDateTime.now(ZoneId.of("CET"));

        int hour = time.getHour();
        int minute = time.getMinute();
        int second = time.getSecond();

        final double SECONDS_PER_MINUTE = 60;
        final double SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
        final double SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;
        final double TICKS_PER_DAY = 24000;
        final double MINECRAFT_START_TIME = 6 * SECONDS_PER_HOUR;

        double seconds = (hour * SECONDS_PER_HOUR) + (minute * SECONDS_PER_MINUTE) + second - MINECRAFT_START_TIME;

        long timeInTicks = Math.round((seconds / SECONDS_PER_DAY) * TICKS_PER_DAY);

        int year = time.getYear();
        int month = time.getMonthValue();
//        int day = Math.min(time.getDayOfMonth(), 30);
        int day = time.getDayOfYear();

//        long daysInTicks = ((month - 1) * 30 + day) * 24000;
        long daysInTicks = day * 24000;
        long currentDayTime = daysInTicks + timeInTicks;

        for (ServerLevel level : server.getAllLevels()) {
            long dayTime = level.getDayTime();
//            long lastDayTime = lastDayTimes.getOrDefault(level, dayTime);
            long difference = currentDayTime - dayTime;

            if (difference == 0)
                return;

//            lastDayTimes.put(level, dayTime);
            if (difference < 0)
            {
//                System.out.println("lastDayTime: "+lastDayTime);
                System.out.println("currentDayTime: "+currentDayTime);
                System.out.println("CurrentTime: "+dayTime);
                System.out.println("Difference: "+difference);
            }

//            System.out.println("Time: "+ level.getDayTime());
//            System.out.println("SetTime: "+ currentDayTime);
            if (level.getDayTime() != currentDayTime) {
                level.setDayTime(currentDayTime);
            }

            SeasonSavedData savedData = getSeasonSavedData(level);
//            savedData.seasonCycleTicks = Mth.positiveModulo(savedData.seasonCycleTicks + (int)difference, SeasonTime.ZERO.getCycleDuration());
            System.out.println("season ticks: " + savedData.seasonCycleTicks);
            System.out.println("season cycle duration: " + SeasonTime.ZERO.getCycleDuration());
            currentDayTime -= 61 * TICKS_PER_DAY;
            savedData.seasonCycleTicks = Mth.positiveModulo((int)(currentDayTime), SeasonTime.ZERO.getCycleDuration());

            int ticks = updateTicks.getOrDefault(level, 0);
            if (ticks >= 20)
            {
                sendSeasonUpdate(level);
                ticks %= 20;
            }
            updateTicks.put(level, ticks + 1);
            savedData.setDirty();
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

    public static final HashMap<ResourceKey<Level>, Integer> prevServerSeasonCycleTicks = new HashMap<>();

    public static void sendSeasonUpdate(Level level)
    {
        if (level.isClientSide())
            return;

        SeasonSavedData savedData = getSeasonSavedData(level);

        // NOTE: The previous tick time is not necessary the current tick time - 1. This is why we have to store it in a map.
        SeasonTime newTime = new SeasonTime(savedData.seasonCycleTicks);
        SeasonTime prevTime = new SeasonTime(prevServerSeasonCycleTicks.computeIfAbsent(level.dimension(), (key) -> newTime.getSeasonCycleTicks()));

        Season.SubSeason prevSeason = prevTime.getSubSeason();
        Season.TropicalSeason prevTropicalSeason = prevTime.getTropicalSeason();
        Season.SubSeason newSeason = newTime.getSubSeason();
        Season.TropicalSeason newTropicalSeason = newTime.getTropicalSeason();

        // Update the previous time
        prevServerSeasonCycleTicks.put(level.dimension(), newTime.getSeasonCycleTicks());

        // Fire an event on standard season changes
        if (!prevSeason.equals(newSeason))
            EventManager.fire(new SeasonChangedEvent.Standard(level, prevSeason, newSeason));

        // Fire an event on tropical season changes
        if (!prevTropicalSeason.equals(newTropicalSeason))
            EventManager.fire(new SeasonChangedEvent.Tropical(level, prevTropicalSeason, newTropicalSeason));

        // Send the update packet
        ModPackets.HANDLER.sendToAll(new SyncSeasonCyclePacket(level.dimension(), savedData.seasonCycleTicks), ((ServerLevel)level).getServer());
    }

    public static SeasonSavedData getSeasonSavedData(Level w)
    {
        if (w.isClientSide() || !(w instanceof ServerLevel))
        {
            return null;
        }

        ServerLevel world = (ServerLevel)w;
        DimensionDataStorage saveDataManager = world.getChunkSource().getDataStorage();

        Supplier<SeasonSavedData> defaultSaveDataSupplier = () ->
        {
            SeasonSavedData savedData = new SeasonSavedData();

            int startingSeason = ModConfig.seasons.startingSubSeason;

            if (startingSeason == 0)
            {
                savedData.seasonCycleTicks = (world.random.nextInt(12)) * SeasonTime.ZERO.getSubSeasonDuration();
            }

            if (startingSeason > 0)
            {
                savedData.seasonCycleTicks = (startingSeason - 1) * SeasonTime.ZERO.getSubSeasonDuration();
            }

            savedData.setDirty(); //Mark for saving
            return savedData;
        };

        return saveDataManager.computeIfAbsent(new SavedData.Factory<>(defaultSaveDataSupplier, SeasonSavedData::load, DataFixTypes.LEVEL), SeasonSavedData.DATA_IDENTIFIER);
    }
}