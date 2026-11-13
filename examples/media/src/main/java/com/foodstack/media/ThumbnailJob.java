package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

public class ThumbnailJob {
    private final S3Storage storage;
    private final Resizer resizer;
    private final AssetRepository assets;

    ThumbnailJob(S3Storage s, Resizer r, AssetRepository a) {
        this.storage = s; this.resizer = r; this.assets = a;
    }

    @EVL.Job(trigger = "media.uploaded")
    @Flow(value = "image-upload>thumbnail", after = "image-upload>queue")
    void onUploaded(ImageUploaded event) {
        // @flow image-upload>thumbnail>download
        byte[] original = storage.download(event.assetId());

        // @flow image-upload>thumbnail>resize
        Map<Integer, byte[]> sizes = resizer.resize(original, 128, 512, 1024);

        // @flow image-upload>thumbnail>upload
        storage.uploadAll(event.assetId(), sizes);

        assets.markReady(event.assetId());
    }
}
