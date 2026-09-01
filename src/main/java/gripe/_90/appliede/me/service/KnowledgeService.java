package gripe._90.appliede.me.service;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.*;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

public class KnowledgeService implements IGridService, IGridServiceProvider {
    private static final int TICKS_PER_SYNC = AppliedEConfig.CONFIG.getSyncThrottleInterval();

    private final List<IManagedGridNode> moduleNodes = new ArrayList<>();
    private final Map<UUID, Supplier<IKnowledgeProvider>> providers = new HashMap<>();
    private final EMCStorage storage = new EMCStorage(this);
    private final ReferenceCounter<TransmutationPattern> temporaryPatterns = new ReferenceCounter<>();
    private final TeamProjectEHandler.Proxy tpeHandler = new TeamProjectEHandler.Proxy();

    private final IGrid grid;
    private Set<AEItemKey> knownItemCache;
    private boolean needsEMCSync;
    private boolean needsPatternSync;
    private int ticksSinceLastSync;

    public KnowledgeService(IGrid grid) {
        this.grid = grid;
        MinecraftForge.EVENT_BUS.addListener((PlayerKnowledgeChangeEvent event) -> {
            knownItemCache = null;
            updatePatterns(true);
        });
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                knownItemCache = null;
                updatePatterns(true);
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

    public static void addTemporaryPattern(IPatternDetails pattern, IGrid grid) {
        var service = getKnowledgeService(grid);
        if (service != null) {
            service.addTemporaryPattern(pattern);
        }
    }

    public static void removeTemporaryPattern(IPatternDetails pattern, IGrid grid) {
        var service = getKnowledgeService(grid);
        if (service != null) {
            service.removeTemporaryPattern(pattern);
        }
    }

    public static void updatePatterns(boolean force, IGrid grid) {
        var service = getKnowledgeService(grid);
        if (service != null) {
            service.updatePatterns(force);
        }
    }

    private static KnowledgeService getKnowledgeService(IGrid grid) {
        if (grid != null) {
            return grid.getService(KnowledgeService.class);
        } else {
            return null;
        }
    }

    public Iterable<ICraftingProvider> getModuleProviders() {
        List<ICraftingProvider> providers = new ArrayList<>();
        for (IManagedGridNode node : moduleNodes) {
            providers.add((EMCModulePart) Objects.requireNonNull(node.getNode()).getOwner());
        }
        return providers;
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

            updatePatterns(true);
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
            updatePatterns(true);
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

        updatePatterns(false);
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

    private void addTemporaryPattern(IPatternDetails details) {
        if (details instanceof TransmutationPattern pattern && pattern.isTemp()) {
            temporaryPatterns.retain(pattern);
            needsPatternSync = true;
        }
    }

    private void removeTemporaryPattern(IPatternDetails details) {
        if (details instanceof TransmutationPattern pattern && pattern.isTemp()) {
            temporaryPatterns.release(pattern);
            needsPatternSync = true;
        }
    }

    void updatePatterns(boolean force) {
        if (force || needsPatternSync) {
            moduleNodes.forEach(ICraftingProvider::requestUpdate);
            needsPatternSync = false;
        }
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
