package com.constructiondimension.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String CATEGORY = "key.category.constructiondimension";


    public static final KeyMapping TELEPORT_KEY = new KeyMapping(
            "key.constructiondimension.teleport",
            KeyConflictContext.IN_GAME, // 游戏内上下文
            KeyModifier.CONTROL,        // 修饰键: Ctrl
            InputConstants.Type.KEYSYM, // 键盘符号类型
            GLFW.GLFW_KEY_P,            // P 键
            CATEGORY
    );

    public static void onRegisterKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(TELEPORT_KEY);
    }
}