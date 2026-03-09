/*
 * This file is part of Cheesecake.
 *
 * Cheesecake is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cheesecake is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Cheesecake.  If not, see <https://www.gnu.org/licenses/>.
 */

package cheesecake.api.utils.gui;

// import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
// import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.DrawContext;
// import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.font.TextRenderer;

public class CheesecakeToast implements Toast {
    private String title;
    private String subtitle;
    // private long firstDrawTime;
    // private boolean newDisplay;
    private long totalShowTime;

    // New fields introduced by the change
    private long startTime;
    private Toast.Visibility visibility = Toast.Visibility.SHOW;

    private static final Identifier TEXTURE = Identifier.of("textures/gui/toasts.png"); // Example, adjust if needed

    public CheesecakeToast(Text titleComponent, Text subtitleComponent, long totalShowTime) {
        this.title = titleComponent.getString();
        this.subtitle = subtitleComponent == null ? null : subtitleComponent.getString();
        this.totalShowTime = totalShowTime;
    }

    @Override
    public Toast.Visibility getVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long currentTime) {
        if (this.startTime == 0) {
            this.startTime = currentTime;
        }
        if (currentTime - this.startTime >= this.totalShowTime * 1000L) {
            this.visibility = Toast.Visibility.HIDE;
        }
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long currentTime) {
        // For a standard toast, width is 160, height is 32
        int width = 160;
        int height = 32;

        // Draw the background texture (you can use your own or standard toast
        // background)
        context.drawTexturedQuad(TEXTURE, 0, width, 0, height, 0.0f, 1.0f, 0.0f, 1.0f);

        int color = 0xFFFFFF; // White color

        if (this.title != null) {
            context.drawText(textRenderer, this.title, 30, 7, color, true);
        }

        if (this.subtitle != null) {
            context.drawText(textRenderer, this.subtitle, 30, 18, color, true);
        }

        // The new draw method uses a fixed 5000L duration, overriding totalShowTime
        // Visibility returned elsewhere since draw is void now
    }

    public void setDisplayedText(Text titleComponent, Text subtitleComponent) {
        this.title = titleComponent.getString();
        this.subtitle = subtitleComponent == null ? null : subtitleComponent.getString();
        // this.newDisplay = true;
    }

    public static void addOrUpdate(ToastManager toast, Text title, Text subtitle, long totalShowTime) {
        CheesecakeToast cheesecaketoast = toast.getToast(CheesecakeToast.class, new Object());

        if (cheesecaketoast == null) {
            toast.add(new CheesecakeToast(title, subtitle, totalShowTime));
        } else {
            cheesecaketoast.setDisplayedText(title, subtitle);
        }
    }

    public static void addOrUpdate(Text title, Text subtitle) {
        addOrUpdate(MinecraftClient.getInstance().getToastManager(), title, subtitle,
                cheesecake.api.CheesecakeAPI.getSettings().toastTimer.value);
    }
}
