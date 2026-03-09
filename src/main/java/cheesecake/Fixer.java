package cheesecake;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
// import java.util.Arrays;
public class Fixer {
    public static void main(String[] args) {
        System.out.println("ClientPlayerEntity methods:");
        for(Method m : ClientPlayerEntity.class.getMethods()) {
            if(m.getName().toLowerCase().contains("pos")) System.out.println("  " + m.getName() + " -> " + m.getReturnType().getSimpleName());
        }
        System.out.println("ClientPlayerEntity fields:");
        for(Field f : ClientPlayerEntity.class.getFields()) {
            System.out.println("  " + f.getName());
        }
        System.out.println("PlayerInventory fields:");
        for(Field f : PlayerInventory.class.getFields()) {
            System.out.println("  " + f.getName());
        }
        System.out.println("Vec3d fields/methods:");
        for(Field f : Vec3d.class.getFields()) {
            System.out.println("  " + f.getName());
        }
        for(Method m : Vec3d.class.getMethods()) {
            if(m.getName().equals("x") || m.getName().equals("getX")) System.out.println("  " + m.getName());
        }
    }
}
