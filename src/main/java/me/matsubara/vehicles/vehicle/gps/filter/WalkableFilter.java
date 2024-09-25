package me.matsubara.vehicles.vehicle.gps.filter;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.patheloper.api.pathing.filter.PathFilter;
import org.patheloper.api.pathing.filter.PathValidationContext;
import org.patheloper.api.snapshot.SnapshotManager;
import org.patheloper.api.wrapper.PathBlock;
import org.patheloper.api.wrapper.PathPosition;

public class WalkableFilter implements PathFilter {

    private final int height;

    public WalkableFilter(int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("Height must be greater than 0");
        }
        this.height = height;
    }

    @Override
    public boolean filter(@NonNull PathValidationContext context) {
        SnapshotManager snapshotManager = context.getSnapshotManager();
        PathBlock block = snapshotManager.getBlock(context.getPosition());
        return canStandOn(block, snapshotManager);
    }

    private boolean canStandOn(@NotNull PathBlock block, @NotNull SnapshotManager snapshotManager) {
        PathBlock below = snapshotManager.getBlock(block.getPathPosition().add(0.0d, -1.0d, 0.0d));
        return below.isSolid() && areBlocksAbovePassable(block.getPathPosition(), snapshotManager);
    }

    private boolean areBlocksAbovePassable(PathPosition position, SnapshotManager snapshotManager) {
        for (int i = 0; i < height; i++) {
            PathBlock block = snapshotManager.getBlock(position.add(0.0d, i, 0.0d));
            if (!block.isPassable()) {
                return false; // The node is not passable, so it's excluded.
            }
        }

        return true; // The node is safe.
    }
}