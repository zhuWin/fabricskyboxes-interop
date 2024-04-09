package me.flashyreese.mods.fabricskyboxes_interop.client.config;

public enum FSBInteropMode {
    CONVERSION("mode.conversion"),
    NATIVE("mode.native");


    private final String translationKey;

    FSBInteropMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }
}
