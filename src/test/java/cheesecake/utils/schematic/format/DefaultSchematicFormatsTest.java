package cheesecake.utils.schematic.format;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Litematica has written schema version 7 since 1.21; upstream moved the gate in cabaletta/baritone
 * e66fdea5 and refuses 6 as too old. A version 7 file that reaches the parser fails on its empty
 * regions tag here, which is fine: what this checks is only what the version gate does.
 */
public class DefaultSchematicFormatsTest {

    private static ByteArrayInputStream litematic(int version) throws Exception {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Version", version);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Test
    public void litematicaVersionSevenPassesTheGate() throws Exception {
        try {
            DefaultSchematicFormats.LITEMATICA.parse(litematic(7));
        } catch (UnsupportedOperationException e) {
            fail("a version 7 .litematic, what current Litematica writes, is rejected by the version gate: " + e.getMessage());
        } catch (RuntimeException | LinkageError pastTheGate) {
            // the parser choked on the empty tag, so the gate let it through
        }
    }

    @Test
    public void litematicaVersionSixIsTooOld() throws Exception {
        try {
            DefaultSchematicFormats.LITEMATICA.parse(litematic(6));
            fail("a version 6 .litematic reached the parser");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("too old"));
        }
    }
}
