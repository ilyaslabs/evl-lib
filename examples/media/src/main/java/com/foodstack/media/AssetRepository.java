package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

@EVL.Database("postgres")
public class AssetRepository {
    private final JdbcClient db;

    AssetRepository(JdbcClient db) { this.db = db; }

    @Flow("image-upload>db")
    Asset savePending(UploadRequest r) {
        UUID id = UUID.randomUUID();
        db.sql("insert into assets(id, user_id, status) values (?, ?, 'PENDING')")
          .params(id, r.userId()).update();
        return new Asset(id, r.userId(), Status.PENDING);
    }

    @Flow("image-upload>thumbnail>ready")
    void markReady(UUID id) {
        db.sql("update assets set status = 'READY' where id = ?").param(id).update();
    }

    @Flow("asset-cleanup>find")
    List<Asset> findPendingOlderThan(Duration age) {
        return db.sql("select * from assets where status = 'PENDING' and created < now() - ?")
                 .param(age).query(Asset.class).list();
    }
}
