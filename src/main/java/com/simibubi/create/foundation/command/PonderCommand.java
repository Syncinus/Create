package com.simibubi.create.foundation.command;

import java.util.Collection;

import com.simibubi.create.lib.entity.FakePlayer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.simibubi.create.foundation.networking.AllPackets;
import com.simibubi.create.foundation.ponder.PonderRegistry;

public class PonderCommand {
	public static final SuggestionProvider<CommandSourceStack> ITEM_PONDERS = SuggestionProviders.register(new ResourceLocation("all_ponders"), (iSuggestionProviderCommandContext, builder) -> SharedSuggestionProvider.suggestResource(PonderRegistry.all.keySet().stream(), builder));

	static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("ponder")
				.requires(cs -> cs.hasPermission(0))
				.executes(ctx -> openScene("index", ctx.getSource().getPlayerOrException()))
				.then(Commands.argument("scene", ResourceLocationArgument.id())
						.suggests(ITEM_PONDERS)
						.executes(ctx -> openScene(ResourceLocationArgument.getId(ctx, "scene").toString(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("targets", EntityArgument.players())
								.requires(cs -> cs.hasPermission(2))
								.executes(ctx -> openScene(ResourceLocationArgument.getId(ctx, "scene").toString(), EntityArgument.getPlayers(ctx, "targets")))
						)
				);

	}

	private static int openScene(String sceneId, ServerPlayer player) {
		return openScene(sceneId, ImmutableList.of(player));
	}

	private static int openScene(String sceneId, Collection<? extends ServerPlayer> players) {
		for (ServerPlayer player : players) {
			if (player instanceof FakePlayer)
				continue;
			AllPackets.channel.sendToClient(new SConfigureConfigPacket(SConfigureConfigPacket.Actions.openPonder.name(), sceneId), player);
//			AllPackets.channel.send(
//					PacketDistributor.PLAYER.with(() -> player),
//					new SConfigureConfigPacket(SConfigureConfigPacket.Actions.openPonder.name(), sceneId));
		}
		return Command.SINGLE_SUCCESS;
	}
}
