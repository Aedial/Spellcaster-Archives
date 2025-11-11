package com.spellarchives;

/**
 * Server/common-side proxy. Client-specific logic is implemented in {@code ClientProxy}.
 */
public class CommonProxy {
    /**
     * Invoked during Forge pre-initialization on the appropriate side.
     */
    public void preInit() {}

    /**
     * Invoked during Forge initialization on the appropriate side.
     */
    public void init() {}

    /**
     * Invoked during Forge post-initialization on the appropriate side.
     */
    public void postInit() {}
}
