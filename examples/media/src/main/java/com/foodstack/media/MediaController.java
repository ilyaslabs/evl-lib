package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

public class MediaController {
    private final ImageValidator validator;
    private final AssetRepository assets;
    private final S3Storage storage;
    private final ImageEvents events;

    MediaController(ImageValidator v, AssetRepository a, S3Storage s, ImageEvents e) {
        this.validator = v; this.assets = a; this.storage = s; this.events = e;
    }

    @EVL.Endpoint(method = "POST", path = "/media", caller = "Mobile app")
    @Flow("image-upload")
    public UploadResponse upload(UploadRequest request) {
        validator.check(request);
        Asset asset = assets.savePending(request);
        URI url = storage.presignUpload(asset);
        events.publish(new ImageUploaded(asset.id()));
        return new UploadResponse(asset.id(), url);
    }

    @EVL.Endpoint(method = "GET", path = "/media/{id}")
    public AssetView get(UUID id) {
        return assets.find(id).map(AssetView::of).orElseThrow();
    }
}
