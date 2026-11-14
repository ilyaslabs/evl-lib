package com.foodstack.media;

import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;

@EVL.Cache(ttl = "15m")
public class SignedUrlCache {
    private final Cache<UUID, URI> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(15)).build();

    @Flow("image-upload>s3>cache")
    void put(UUID id, URI url) {
        cache.put(id, url);
    }

    Optional<URI> get(UUID id) {
        return Optional.ofNullable(cache.getIfPresent(id));
    }
}
