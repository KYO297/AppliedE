package gripe._90.appliede.me.service;

import com.mojang.logging.LogUtils;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static gripe._90.appliede.AppliedE.MODID;


@Mod.EventBusSubscriber(modid = MODID)
public abstract class PlayerDataCache {
    private static final Map<UUID, PlayerData> DATA = new HashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void handleKnowledgeChange(PlayerKnowledgeChangeEvent event) {
        UUID player = event.getPlayerUUID();
        if (!DATA.containsKey(player)) {
            DATA.put(player, new PlayerData());
        }
        Set<ItemInfo> oldKnowledge = DATA.get(player).knowledge;
        Set<ItemInfo> newKnowledge = getProviderfor(player).getKnowledge();

        Set<ItemInfo> gained = newKnowledge.stream().filter((ItemInfo i) -> !oldKnowledge.contains(i)).collect(Collectors.toSet());
        Set<ItemInfo> lost = oldKnowledge.stream().filter((ItemInfo i) -> !newKnowledge.contains(i)).collect(Collectors.toSet());
        oldKnowledge.removeAll(lost);
        oldKnowledge.addAll(gained);

        LOGGER.info("Gained {}", gained.size());
        LOGGER.info("Lost {}", lost.size());
    }

    private static IKnowledgeProvider getProviderfor(UUID player) {
        return KnowledgeService.retrieveProvider(player).get();
    }

    private static void syncEMCfor(UUID player) {
        if (!DATA.containsKey(player)) {
            DATA.put(player, new PlayerData());
        }
        DATA.get(player).EMC = getProviderfor(player).getEmc();
    }


    private static class PlayerData {
        Set<ItemInfo> knowledge = new HashSet<>();
        BigInteger EMC = BigInteger.ZERO;
    }
}
