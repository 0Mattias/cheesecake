package cheesecake;
import java.lang.reflect.Method;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
// import java.util.Arrays;
public class Fixer {
    public static void main(String[] args) {
        System.out.println("ClientPlayerEntity methods:");
        for(Method m : LocalPlayer.class.getMethods()) {
            if(m.getName().toLowerCase().contains("pos")) System.out.println("  " + m.getName() + " -> " + m.getReturnType().getSimpleName());
        }
        System.out.println("ClientPlayerEntity fields:");
        for(Field f : LocalPlayer.class.getFields()) {
            System.out.println("  " + f.getName());
        }
        System.out.println("PlayerInventory fields:");
        for(Field f : Inventory.class.getFields()) {
            System.out.println("  " + f.getName());
        }
        System.out.println("Vec3d fields/methods:");
        for(Field f : Vec3.class.getFields()) {
            System.out.println("  " + f.getName());
        }
        for(Method m : Vec3.class.getMethods()) {
            if(m.getName().equals("x") || m.getName().equals("getX")) System.out.println("  " + m.getName());
        }
    }
}
