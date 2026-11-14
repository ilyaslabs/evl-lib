package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

@EVL.Queue("media.uploaded")
public class ImageEvents {
    private final Broker broker;

    ImageEvents(Broker broker) { this.broker = broker; }

    @Flow("image-upload>queue")
    void publish(ImageUploaded event) {
        broker.send("media.uploaded", event.assetId().toString(), event);
    }
}
