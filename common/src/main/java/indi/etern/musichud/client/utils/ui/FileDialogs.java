package indi.etern.musichud.client.utils.ui;

import com.mojang.blaze3d.platform.Window;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeResource;
import org.lwjgl.system.Pointer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Native file dialogs backed by SDL3.
 * <p>
 * SDL requires the dialog to be created on the main thread, but invokes the result
 * callback asynchronously on an unspecified thread. Dialog creation is therefore
 * marshalled to the Minecraft main thread, and results are delivered back on the
 * ModernUI UI thread as an empty list when the user cancels or an error occurs.
 */
public final class FileDialogs {
    private static final Map<Long, Request> PENDING = new ConcurrentHashMap<>();
    private static final AtomicLong IDS = new AtomicLong();

    private static final SDL_DialogFileCallback CALLBACK = new SDL_DialogFileCallback() {
        @Override
        public void invoke(long userdata, long filelist, int filter) {
            Request request = PENDING.remove(userdata);
            if (request == null) {
                return;
            }
            List<String> paths;
            try {
                paths = readFileList(filelist);
            } finally {
                request.free();
            }
            MuiModApi.postToUiThread(() -> request.onResult.accept(paths));
        }
    };

    private FileDialogs() {
    }

    /**
     * Shows a native "open file" dialog with a single extension filter.
     *
     * @param extensions file extensions without a leading dot, e.g. {@code List.of("mp3", "flac")}
     */
    public static void openFiles(String title, String filterLabel, List<String> extensions,
                                 @Nullable String defaultLocation, boolean allowMany,
                                 Consumer<List<String>> onResult) {
        Minecraft.getInstance().execute(() -> {
            Filter filter = buildFilter(filterLabel, extensions);
            Request request = new Request(onResult, filter);
            long id = IDS.incrementAndGet();
            PENDING.put(id, request);
            withProperties(props -> {
                if (filter != null) {
                    SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filter.buffer().address());
                    SDLProperties.SDL_SetNumberProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1L);
                }
                applyCommonProperties(props, title, defaultLocation, allowMany);
                SDLDialog.SDL_ShowFileDialogWithProperties(SDLDialog.SDL_FILEDIALOG_OPENFILE, CALLBACK, id, props);
            });
        });
    }

    /**
     * Shows a native folder selection dialog.
     */
    public static void openFolder(String title, @Nullable String defaultLocation,
                                  Consumer<List<String>> onResult) {
        Minecraft.getInstance().execute(() -> {
            Request request = new Request(onResult, null);
            long id = IDS.incrementAndGet();
            PENDING.put(id, request);
            withProperties(props -> {
                applyCommonProperties(props, title, defaultLocation, false);
                SDLDialog.SDL_ShowFileDialogWithProperties(SDLDialog.SDL_FILEDIALOG_OPENFOLDER, CALLBACK, id, props);
            });
        });
    }

    private static void applyCommonProperties(int props, @Nullable String title,
                                              @Nullable String defaultLocation, boolean allowMany) {
        Window window = Minecraft.getInstance().getWindow();
        SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER, window.handle());
        SDLProperties.SDL_SetBooleanProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_MANY_BOOLEAN, allowMany);
        if (title != null) {
            SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
        }
        if (defaultLocation != null) {
            SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, defaultLocation);
        }
    }

    private static void withProperties(IntConsumer action) {
        int props = SDLProperties.SDL_CreateProperties();
        try {
            action.accept(props);
        } finally {
            SDLProperties.SDL_DestroyProperties(props);
        }
    }

    @Nullable
    private static Filter buildFilter(@Nullable String label, List<String> extensions) {
        if (extensions.isEmpty()) {
            return null;
        }
        SDL_DialogFileFilter.Buffer buffer = SDL_DialogFileFilter.calloc(1);
        ByteBuffer name = MemoryUtil.memUTF8(label == null ? String.join(", ", extensions) : label);
        ByteBuffer pattern = MemoryUtil.memUTF8(String.join(";", extensions));
        buffer.name(name).pattern(pattern);
        return new Filter(buffer, name, pattern);
    }

    private static List<String> readFileList(long filelist) {
        if (filelist == MemoryUtil.NULL) {
            MusicHud.LOGGER.warn("SDL file dialog failed: {}", SDLError.SDL_GetError());
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (int i = 0; ; i++) {
            long pointer = MemoryUtil.memGetAddress(filelist + (long) i * Pointer.POINTER_SIZE);
            if (pointer == MemoryUtil.NULL) {
                break;
            }
            paths.add(MemoryUtil.memUTF8(pointer));
        }
        return paths;
    }

    private record Filter(SDL_DialogFileFilter.Buffer buffer, ByteBuffer name, ByteBuffer pattern)
            implements NativeResource {
        @Override
        public void free() {
            buffer.free();
            MemoryUtil.memFree(name);
            MemoryUtil.memFree(pattern);
        }
    }

    private record Request(Consumer<List<String>> onResult, @Nullable Filter filter) {
        void free() {
            if (filter != null) {
                filter.free();
            }
        }
    }
}
