package sk.stuba.fiit.lwjgl3;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class PackerUtil {
    public static void pack() {
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = 2048;
        settings.maxHeight = 2048;
        settings.stripWhitespaceX = true;
        settings.stripWhitespaceY = true;
        settings.paddingX = 2;
        settings.paddingY = 2;

        TexturePacker.process(settings, "assets/knight", "assets/atlas/knight", "knight");
        TexturePacker.process(settings, "assets/archer", "assets/atlas/archer", "archer");
        TexturePacker.process(settings, "assets/arrow", "assets/atlas/arrow", "arrow");
        TexturePacker.process(settings, "assets/wizzard", "assets/atlas/wizzard", "wizzard");
        TexturePacker.process(settings, "assets/firespell", "assets/atlas/firespell", "firespell");
        TexturePacker.process(settings, "assets/duck",   "assets/atlas/duck", "duck");
        TexturePacker.process(settings, "assets/turdfly",   "assets/atlas/turdfly", "turdfly");
        TexturePacker.process(settings, "assets/egg",   "assets/atlas/egg", "egg");
    }
}
