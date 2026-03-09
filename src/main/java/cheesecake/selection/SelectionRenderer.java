package cheesecake.selection;

import cheesecake.Cheesecake;
import cheesecake.api.event.events.RenderEvent;
import cheesecake.api.event.listener.AbstractGameEventListener;
import cheesecake.api.selection.ISelection;
import cheesecake.utils.IRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

public class SelectionRenderer implements IRenderer, AbstractGameEventListener {

    public static final double SELECTION_BOX_EXPANSION = .005D;

    private final SelectionManager manager;

    SelectionRenderer(Cheesecake cheesecake, SelectionManager manager) {
        this.manager = manager;
        cheesecake.getGameEventHandler().registerEventListener(this);
    }

    public static void renderSelections(MatrixStack stack, ISelection[] selections) {
        float opacity = settings.selectionOpacity.value;
        boolean ignoreDepth = settings.renderSelectionIgnoreDepth.value;
        float lineWidth = settings.selectionLineWidth.value;

        if (!settings.renderSelection.value || selections.length == 0) {
            return;
        }

        IRenderer.startLines(settings.colorSelection.value, opacity, lineWidth, ignoreDepth);

        for (ISelection selection : selections) {
            IRenderer.emitAABB(stack, selection.aabb(), SELECTION_BOX_EXPANSION);
        }

        if (settings.renderSelectionCorners.value) {
            IRenderer.glColor(settings.colorSelectionPos1.value, opacity);

            for (ISelection selection : selections) {
                IRenderer.emitAABB(stack, new Box(selection.pos1().x, selection.pos1().y, selection.pos1().z,
                        selection.pos1().x + 1, selection.pos1().y + 1, selection.pos1().z + 1));
            }

            IRenderer.glColor(settings.colorSelectionPos2.value, opacity);

            for (ISelection selection : selections) {
                IRenderer.emitAABB(stack, new Box(selection.pos2().x, selection.pos2().y, selection.pos2().z,
                        selection.pos2().x + 1, selection.pos2().y + 1, selection.pos2().z + 1));
            }
        }

        IRenderer.endLines(ignoreDepth);
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        renderSelections(event.getModelViewStack(), manager.getSelections());
    }
}
