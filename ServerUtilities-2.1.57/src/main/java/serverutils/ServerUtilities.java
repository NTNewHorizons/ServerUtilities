package serverutils;

import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import serverutils.lib.command.CommandUtils;

@Mod(
        modid = ServerUtilities.MOD_ID,
        name = ServerUtilities.MOD_NAME,
        version = ServerUtilities.VERSION,
        dependencies = "required-after:gtnhlib;" + "after:navigator;",
        guiFactory = "serverutils.client.gui.GuiFactory")
public class ServerUtilities {

    public static final String MOD_ID = "serverutilities";
    public static final String MOD_NAME = "Server Utilities";
    public static final String VERSION = Tags.GRADLETOKEN_VERSION;
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);
    public static final String SERVER_FOLDER = MOD_ID + "/server/";

    @Mod.Instance(MOD_ID)
    public static ServerUtilities INST;

    @SidedProxy(
            serverSide = "serverutils.ServerUtilitiesCommon",
            clientSide = "serverutils.client.ServerUtilitiesClient")
    public static ServerUtilitiesCommon PROXY;

    public static IChatComponent lang(@Nullable ICommandSender sender, String key, Object... args) {
        return lang(key, args);
    }

    public static IChatComponent lang(String key, Object... args) {
        return new ChatComponentTranslation(key, args);
    }

    public static CommandException error(@Nullable ICommandSender sender, String key, Object... args) {
        return CommandUtils.error(lang(sender, key, args));
    }

    public static CommandException errorFeatureDisabledServer(@Nullable ICommandSender sender) {
        return error(sender, "feature_disabled_server");
    }

    private long lastWarCleanup = 0L;
    private long lastWarCaptureCheck = 0L;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        Locale.setDefault(Locale.US);
        PROXY.preInit(event);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        // Register on FML event bus for TickEvent (not Forge event bus)
        FMLCommonHandler.instance().bus().register(this);
        PROXY.init(event);
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        PROXY.postInit(event);
    }

    @Mod.EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        PROXY.onServerAboutToStart(event);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        PROXY.onServerStarting(event);
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        PROXY.onServerStarted(event);
        serverutils.data.WarManager.get().loadWars();
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        PROXY.onServerStopping(event);
        serverutils.data.WarManager.get().saveWars();
    }

    @SubscribeEvent
    public void onServerTick(cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;
        int interval = ServerUtilitiesConfig.teams.war_cleanup_interval_seconds;
        if (interval > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWarCleanup >= interval * 1000L) {
                serverutils.data.WarManager.get().cleanupExpiredWars();
                lastWarCleanup = now;
            }
        }

        // War capture checks (configurable interval)
        int captureInterval = ServerUtilitiesConfig.teams.war_capture_check_interval_seconds;
        if (captureInterval > 0) {
            long now2 = System.currentTimeMillis();
            if (now2 - lastWarCaptureCheck >= captureInterval * 1000L) {
                serverutils.data.WarCaptureManager.get().onCheck(now2);
                lastWarCaptureCheck = now2;
            }
        }
    }

    @NetworkCheckHandler
    public boolean checkModLists(Map<String, String> map, Side side) {
        return side != Side.CLIENT || map.containsKey(MOD_ID) && map.get(MOD_ID).equals(VERSION);
    }

    /**
     * Announces globally that a war has ended between two teams.
     */
    public static void announceGlobalWarEnd(String teamA, String teamB) {
        String message = "\u00a7eThe war between team " + teamA + " and team " + teamB + " has ended!";
        for (Object obj : net.minecraft.server.MinecraftServer.getServer().getConfigurationManager()
            .playerEntityList) {
            if (obj instanceof net.minecraft.entity.player.EntityPlayerMP) {
                ((net.minecraft.entity.player.EntityPlayerMP) obj)
                    .addChatMessage(new ChatComponentText(message));
            }
        }
    }

    /**
     * Announces globally that a war has become active (grace period ended) between two teams.
     */
    public static void announceGlobalWarActive(String teamA, String teamB) {
        // Try to get team titles for better display
        String titleA = teamA;
        String titleB = teamB;
        try {
            serverutils.lib.data.ForgeTeam fteamA = serverutils.lib.data.Universe.get().getTeam(teamA);
            if (fteamA != null && fteamA.isValid()) {
                titleA = fteamA.getTitle().getUnformattedText();
            }
            serverutils.lib.data.ForgeTeam fteamB = serverutils.lib.data.Universe.get().getTeam(teamB);
            if (fteamB != null && fteamB.isValid()) {
                titleB = fteamB.getTitle().getUnformattedText();
            }
        } catch (Exception e) {
            // Fall back to team IDs if there's any error
        }

        String message = "\u00a7cWAR ACTIVE! The war between team \u00a7f" + titleA + "\u00a7c and team "
            + "\u00a7f" + titleB + "\u00a7c is now active!";
        for (Object obj : net.minecraft.server.MinecraftServer.getServer().getConfigurationManager()
            .playerEntityList) {
            if (obj instanceof net.minecraft.entity.player.EntityPlayerMP) {
                ((net.minecraft.entity.player.EntityPlayerMP) obj)
                    .addChatMessage(new ChatComponentText(message));
            }
        }
    }

    /**
     * Announces globally that a surrender has been accepted between two teams.
     */
    public static void announceGlobalSurrender(String surrenderingTeam, String acceptingTeam) {
        for (Object obj : net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            if (obj instanceof net.minecraft.entity.player.EntityPlayerMP) {
                ((net.minecraft.entity.player.EntityPlayerMP) obj).addChatMessage(
                    new ChatComponentText(
                        "\u00a76Team " + surrenderingTeam + " has surrendered to team " + acceptingTeam + "! All their chunks have been ceded."));
            }
        }
    }

    /**
     * Announces globally that a truce has been accepted between two teams.
     */
    public static void announceGlobalTruce(String teamA, String teamB) {
        for (Object obj : net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            if (obj instanceof net.minecraft.entity.player.EntityPlayerMP) {
                ((net.minecraft.entity.player.EntityPlayerMP) obj).addChatMessage(
                    new ChatComponentText(
                        "\u00a7aA truce has been agreed between team " + teamA + " and team " + teamB + "!"));
            }
        }
    }

    /**
     * Announces globally that a ceasefire has been accepted between two teams.
     */
    public static void announceGlobalCeasefire(String teamA, String teamB, int durationMinutes) {
        for (Object obj : net.minecraft.server.MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            if (obj instanceof net.minecraft.entity.player.EntityPlayerMP) {
                ((net.minecraft.entity.player.EntityPlayerMP) obj).addChatMessage(
                    new ChatComponentText(
                        "\u00a79A " + durationMinutes + "-minute ceasefire has been agreed between team " + teamA + " and team " + teamB + "!"));
            }
        }
    }
}
