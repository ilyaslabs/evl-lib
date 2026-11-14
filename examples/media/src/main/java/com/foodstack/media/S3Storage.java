package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

@EVL.Storage("Amazon S3")
public class S3Storage {
    private final S3Presigner presigner;
    private final SignedUrlCache urlCache;
    private final String bucket = "foodstack-media";

    S3Storage(S3Presigner p, SignedUrlCache c) { this.presigner = p; this.urlCache = c; }

    @Flow("image-upload>s3")
    URI presignUpload(Asset asset) {
        // @flow image-upload>s3>key
        String key = "media/" + asset.userId() + "/" + asset.id();

        // @flow image-upload>s3>presign
        URI url = presigner.presignPut(bucket, key, Duration.ofMinutes(15));

        urlCache.put(asset.id(), url);
        return url;
    }

    @Flow("asset-cleanup>objects")
    void deleteAll(List<Asset> stale) {
        stale.forEach(a -> client.deleteObject(bucket, keyOf(a)));
    }
}
