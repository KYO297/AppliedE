package gripe._90.appliede.me.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.IManagedGridNode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NullInventory;

import gripe._90.appliede.AppliedEConfig;
import gripe._90.appliede.me.misc.TransmutationPattern;
import gripe._90.appliede.mixin.misc.TransmutationOfflineAccessor;
import gripe._90.appliede.part.EMCModulePart;

import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.api.proxy.ITransmutationProxy;

public class KnowledgeService implements IGridService, IGridServiceProvider {
    private static final int TICKS_PER_SYNC = AppliedEConfig.CONFIG.getSyncThrottleInterval();

    private final List<IManagedGridNode> moduleNodes = new ArrayList<>();
    private final Map<UUID, Supplier<IKnowledgeProvider>> providers = new HashMap<>();
    private final EMCStorage storage = new EMCStorage(this);
    private final ReferenceCounter<TransmutationPattern> temporaryPatterns = new ReferenceCounter<>();
    private final Map<ICraftingCPU, List<TransmutationPattern>> trackedCPUs = new HashMap<>();
    private final TeamProjectEHandler.Proxy tpeHandler = new TeamProjectEHandler.Proxy();

    private final IGrid grid;
    private Set<AEItemKey> knownItemCache;
    private boolean needsEMCSync;
    private int ticksSinceLastSync;

    public KnowledgeService(IGrid grid) {
        this.grid = grid;
        MinecraftForge.EVENT_BUS.addListener((PlayerKnowledgeChangeEvent event) -> {
            knownItemCache = null;
            updatePatterns();
        });
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                knownItemCache = null;
                updatePatterns();
            }
        });
    }

    static Supplier<IKnowledgeProvider> retrieveProvider(UUID playerUUID) {
        return () -> {
            try {
                return ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(playerUUID);
            } catch (Throwable e) {
                return TransmutationOfflineAccessor.invokeForPlayer(playerUUID);
            }
        };
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        if (gridNode.getOwner() instanceof EMCModulePart module) {
            knownItemCache = null;
            moduleNodes.add(module.getMainNode());
            var uuid = gridNode.getOwningPlayerProfileId();

            if (uuid != null) {
                addProvider(uuid);
            }

            updatePatterns();
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        if (gridNode.getOwner() instanceof EMCModulePart module) {
            knownItemCache = null;
            moduleNodes.remove(module.getMainNode());
            providers.clear();
            tpeHandler.clear();

            for (var mainNode : moduleNodes) {
                var node = mainNode.getNode();

                if (node != null) {
                    var uuid = node.getOwningPlayerProfileId();

                    if (uuid != null) {
                        addProvider(uuid);
                    }
                }
            }

            moduleNodes.forEach(IStorageProvider::requestUpdate);
            updatePatterns();
        }
    }

    @Override
    public void onServerStartTick() {
        if (ticksSinceLastSync < TICKS_PER_SYNC) {
            ticksSinceLastSync++;
        }

        if (needsEMCSync && ticksSinceLastSync == TICKS_PER_SYNC) {
            tpeHandler.syncTeamProviders(providers);
            needsEMCSync = false;
            ticksSinceLastSync = 0;
        }

        Set<ICraftingCPU> cpus = trackedCPUs.keySet();
        if (!cpus.isEmpty()) {
            boolean changed = false;
            for (var cpu : cpus) {
                if (!cpu.isBusy()) {
                    for (var pattern : trackedCPUs.get(cpu)) {
                        removeTemporaryPattern(pattern);
                    }
                    trackedCPUs.remove(cpu);
                    changed = true;
                }
            }
            if (changed) updatePatterns();
        }
    }

    private void addProvider(UUID playerUUID) {
        providers.putIfAbsent(playerUUID, retrieveProvider(playerUUID));
    }

    List<IKnowledgeProvider> getProviders() {
        return providers.values().stream().map(Supplier::get).toList();
    }

    public Supplier<IKnowledgeProvider> getProviderFor(UUID uuid) {
        return providers.getOrDefault(uuid, tpeHandler.getProviderFor(uuid));
    }

    Supplier<IKnowledgeProvider> getProviderFor(Player player) {
        return getProviderFor(player.getUUID());
    }

    Supplier<IKnowledgeProvider> getProviderFor(IActionHost host) {
        var node = host.getActionableNode();

        if (node != null) {
            var uuid = node.getOwningPlayerProfileId();
            return uuid != null ? getProviderFor(uuid) : null;
        }

        return null;
    }

    public EMCStorage getStorage() {
        return storage;
    }

    public MEStorage getStorage(IManagedGridNode node) {
        return !moduleNodes.isEmpty() && node.equals(moduleNodes.get(0)) && node.isActive() ? storage : NullInventory.of();
    }

    public Set<AEItemKey> getKnownItems() {
        if (knownItemCache == null) {
            knownItemCache = new HashSet<>();

            for (var provider : getProviders()) {
                for (var item : provider.getKnowledge()) {
                    if (!IEMCProxy.INSTANCE.hasValue(item)) {
                        continue;
                    }

                    var key = AEItemKey.of(item.createStack());

                    if (key != null) {
                        knownItemCache.add(key);
                    }
                }
            }
        }

        return knownItemCache;
    }

    public List<IPatternDetails> getPatterns(IManagedGridNode node) {
        if (!moduleNodes.isEmpty() && node.equals(moduleNodes.get(0)) && node.isActive()) {
            var patterns = new ArrayList<IPatternDetails>();

            for (var tier = storage.getHighestTier(); tier > 1; tier--) {
                patterns.add(new TransmutationPattern(tier));
            }

            for (var item : getKnownItems()) {
                patterns.add(new TransmutationPattern(item, 1));
            }

            patterns.addAll(temporaryPatterns.keySet());
            return patterns;
        }

        return Collections.emptyList();
    }

    private void addTemporaryPattern(TransmutationPattern pattern) {
        temporaryPatterns.retain(pattern);
    }

    private void removeTemporaryPattern(TransmutationPattern pattern) {
        temporaryPatterns.release(pattern);
    }

    void updatePatterns() {
        moduleNodes.forEach(ICraftingProvider::requestUpdate);
    }

    IGrid getGrid() {
        return grid;
    }

    BigInteger getEmc() {
        var emc = BigInteger.ZERO;

        for (var entry : providers.entrySet()) {
            if (tpeHandler.notSharingEmc(entry)) {
                emc = emc.add(entry.getValue().get().getEmc());
            }
        }

        return emc;
    }

    public boolean isTrackingPlayer(Player player) {
        var uuid = player.getUUID();
        return providers.containsKey(uuid) || tpeHandler.isPlayerInTrackedTeam(uuid);
    }

    void syncEmc() {
        needsEMCSync = true;
    }

    public void trackCPU(ICraftingCPU cpu, List<TransmutationPattern> patterns) {
        trackedCPUs.put(cpu, patterns);
        for (TransmutationPattern pattern : patterns) {
            addTemporaryPattern(pattern);
        }
        updatePatterns();
    }

    private static class ReferenceCounter<T> {
        private final Map<T, Integer> counts = new HashMap<>();

        public void retain(T obj) {
            counts.merge(obj, 1, Integer::sum);
        }

        public void release(T obj) {
            counts.computeIfPresent(obj, (key, count) -> count == 1 ? null : count - 1);
        }

        public Set<T> keySet() {
            return counts.keySet();
        }
    }
}
