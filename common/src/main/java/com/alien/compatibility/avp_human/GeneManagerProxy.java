package com.alien.compatibility.avp_human;

import com.human.common.gameplay.entity.manager.GeneManager;
import com.human.common.model.GeneCarrier;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

public sealed interface GeneManagerProxy {

    static GeneManagerProxy getOrCreate(LivingEntity livingEntity) {
        return AVPHuman.MOD.isLoaded()
            ? new Wrapper(((GeneCarrier) livingEntity).getOrCreateGeneManager())
            : EMPTY.INSTANCE;
    }

    default GeneContainerProxy getGeneContainer() {
        return switch (this) {
            case EMPTY ignored -> GeneContainerProxy.EMPTY.INSTANCE;
            case Wrapper wrapper -> new GeneContainerProxy.Wrapper(wrapper.geneManager().getGeneContainer());
        };
    }

    default void ifPresent(Consumer<Wrapper> consumer) {
        if (this instanceof Wrapper wrapper) {
            consumer.accept(wrapper);
        }
    }

    /**
     * ⚠ TEMPORARY DIAGNOSTIC — remove once the ovomorph royal-promotion gene report is settled.
     * <p>
     * Returns "active=N dormant=M" for logging, or "avp_human-absent" when the proxy is EMPTY. Reads
     * {@code GeneMap.getBackingMap()} on both maps, which is the same storage {@code GeneContainer.transfer} moves, so
     * a before/after pair around a transfer says exactly whether the copy happened.
     * </p>
     */
    default String describeGeneCounts() {
        return switch (this) {
            case EMPTY ignored -> "avp_human-absent";
            case Wrapper wrapper -> {
                var container = wrapper.geneManager().getGeneContainer();
                yield "active=" + container.getActiveGeneMap().getBackingMap().size()
                    + " dormant=" + container.getDormantGeneMap().getBackingMap().size();
            }
        };
    }

    default void transfer(GeneManagerProxy other, boolean activateDormantGenes) {
        transfer(other.getGeneContainer(), activateDormantGenes);
    }

    default void transfer(GeneContainerProxy other, boolean activateDormantGenes) {
        switch (this) {
            case EMPTY ignored -> {/* NO-OP */}
            case Wrapper thisWrapper -> {
                switch (other) {
                    case GeneContainerProxy.EMPTY ignored -> {/* NO-OP */}
                    case GeneContainerProxy.Wrapper wrapper -> thisWrapper.geneManager()
                        .getGeneContainer()
                        .transfer(wrapper.geneContainer(), activateDormantGenes);
                }
            }
        }
    }

    enum EMPTY implements GeneManagerProxy {
        INSTANCE
    }

    record Wrapper(GeneManager geneManager) implements GeneManagerProxy {}
}
