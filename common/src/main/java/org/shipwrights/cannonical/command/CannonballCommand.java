package org.shipwrights.cannonical.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.shipwrights.cannonical.content.projectile.CannonballProjectileEntity;
import org.shipwrights.cannonical.registry.ModItems;

public final class CannonballCommand {
    private CannonballCommand() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> dispatcher.register(
                Commands.literal("cannonball")
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.05D, 100.0D))
                                .executes(context -> execute(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "speed"))))
                        .then(Commands.literal("speed")
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.05D, 100.0D))
                                        .executes(context -> execute(
                                                context.getSource(),
                                                DoubleArgumentType.getDouble(context, "speed")))))));
    }

    private static int execute(CommandSourceStack source, double speed) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException ignored) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        CannonballProjectileEntity projectile = new CannonballProjectileEntity(player.level(), player);
        projectile.setItem(ModItems.CANNONBALL.get().getDefaultInstance());
        projectile.setPos(player.getEyePosition().add(player.getLookAngle().scale(1.0D)));
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, (float) speed, 0.0F);
        projectile.captureLaunchSpeedNow();
        player.level().addFreshEntity(projectile);

        source.sendSuccess(() -> Component.literal(String.format("Fired cannonball at speed %.2f", speed)), false);
        return 1;
    }
}
