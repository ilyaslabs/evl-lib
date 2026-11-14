package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

public class ImageValidator {
    static final long MAX_BYTES = 10 * 1024 * 1024;
    static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    static final int QUOTA = 500;

    private final AssetRepository assets;

    ImageValidator(AssetRepository assets) { this.assets = assets; }

    @Flow("image-upload>validate")
    void check(UploadRequest r) {
        // @flow image-upload>validate>size
        if (r.size() > MAX_BYTES) throw new TooLarge(r.size());

        // @flow image-upload>validate>type
        if (!ALLOWED.contains(r.contentType())) throw new BadType(r.contentType());

        // @flow image-upload>validate>quota
        if (assets.countFor(r.userId()) >= QUOTA) throw new QuotaFull(r.userId());
    }
}
