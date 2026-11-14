package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

public class CleanupJob {
    private final AssetRepository assets;
    private final S3Storage storage;

    CleanupJob(AssetRepository a, S3Storage s) { this.assets = a; this.storage = s; }

    @EVL.Job(schedule = "0 0 2 * * *")
    @Flow("asset-cleanup")
    void run() {
        List<Asset> stale = assets.findPendingOlderThan(Duration.ofHours(24));
        storage.deleteAll(stale);
        // @flow asset-cleanup>rows
        assets.deleteAll(stale);
    }
}
