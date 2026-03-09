package org.shipwrights.cannonical.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.shipwrights.cannonical.content.explosive.CannonicalExplosions;

public final class GunpowderBarrelPowerCommand {
    private static final double POWER_CAP = 1_000_000.0D;

    private GunpowderBarrelPowerCommand() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> dispatcher.register(
                Commands.literal("gunpowderbarrelpower")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> query(context.getSource()))
                        .then(Commands.argument("power", DoubleArgumentType.doubleArg(1.0D, POWER_CAP))
                                .executes(context -> setPower(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "power"))))));
    }

    private static int query(CommandSourceStack source) {
        double currentPower = CannonicalExplosions.getImpactPower();
        double currentRadius = CannonicalExplosions.getBlastRadius();
        source.sendSuccess(() -> Component.literal(String.format(
                "Gunpowder barrel power: %.2f (default %.2f), radius: %.2f",
                currentPower,
                CannonicalExplosions.getDefaultImpactPower(),
                currentRadius
        )), false);
        return 1;
    }

    private static int setPower(CommandSourceStack source, double power) {
        CannonicalExplosions.setImpactPower(power);
        double currentPower = CannonicalExplosions.getImpactPower();
        double currentRadius = CannonicalExplosions.getBlastRadius();
        source.sendSuccess(() -> Component.literal(String.format(
                "Set gunpowder barrel power to %.2f (radius %.2f)",
                currentPower,
                currentRadius
        )), true);
        return 1;
    }
}
