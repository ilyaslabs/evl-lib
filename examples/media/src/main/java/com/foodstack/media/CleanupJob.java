package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

public class CleanupJob {
    private final AssetRepository assets;
    private final S3Storage storage;
    private final Alerts alerts = new Alerts();

    CleanupJob(AssetRepository a, S3Storage s) { this.assets = a; this.storage = s; }

    @EVL.Job(schedule = "0 0 2 * * *")
    @Flow("asset-cleanup")
    void run() {
        List<Asset> stale = assets.findPendingOlderThan(Duration.ofHours(24));
        if (stale.isEmpty()) {
            // @flow asset-cleanup>anything?>none: Nothing to clean [when no stale assets]
            return;
        }
        try {
            storage.deleteAll(stale);
        } catch (StorageException e) {
            // @flow asset-cleanup>alert: Alert on-call [on error StorageException]
            alerts.page(e);
            return;
        }
        // @flow asset-cleanup>rows: Delete rows [each stale asset]
        for (Asset a : stale) assets.delete(a);
    }
}
