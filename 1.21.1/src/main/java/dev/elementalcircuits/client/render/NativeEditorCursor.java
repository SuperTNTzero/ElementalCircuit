package dev.elementalcircuits.client.render;

import dev.elementalcircuits.client.editor.*;

import org.lwjgl.glfw.*;
import org.lwjgl.system.*;

import java.nio.ByteBuffer;
import java.util.*;

/** Window-owned native cursor handles. Never hides the OS pointer or follows per-frame pixels. */
public final class NativeEditorCursor implements AutoCloseable {
    private record Key(EditorPointer pointer, int scale) {}

    private final long window;
    private final Map<Key, Long> cursors = new HashMap<>();
    private Key current;

    public NativeEditorCursor(long window) {
        this.window = window;
    }

    public void show(EditorModel.View view, int scale) {
        Key key = new Key(EditorPointer.of(view), Math.max(1, Math.min(3, scale)));
        if (key.equals(current)) return;
        long cursor = cursors.computeIfAbsent(key, NativeEditorCursor::create);
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursor(window, cursor);
        current = key;
    }

    private static long create(Key key) {
        PointerBitmap bitmap = new PointerBitmap(key.pointer, key.scale);
        ByteBuffer rgba = MemoryUtil.memAlloc(bitmap.size() * bitmap.size() * 4);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int argb : bitmap.pixels())
                rgba.put((byte) (argb >>> 16))
                        .put((byte) (argb >>> 8))
                        .put((byte) argb)
                        .put((byte) (argb >>> 24));
            rgba.flip();
            GLFWImage image =
                    GLFWImage.malloc(stack).width(bitmap.size()).height(bitmap.size()).pixels(rgba);
            return GLFW.glfwCreateCursor(
                    image, bitmap.hotX(), bitmap.hotY()); // 0 safely falls back to the OS arrow.
        } finally {
            MemoryUtil.memFree(rgba);
        }
    }

    public void restore() {
        if (current != null) {
            GLFW.glfwSetCursor(window, 0);
            current = null;
        }
    }

    @Override
    public void close() {
        restore();
        for (long cursor : cursors.values()) if (cursor != 0) GLFW.glfwDestroyCursor(cursor);
        cursors.clear();
    }
}
