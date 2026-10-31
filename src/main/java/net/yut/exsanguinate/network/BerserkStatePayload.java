package net.yut.exsanguinate.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

/**
 * A tiny server -> client packet: "you just became berserk" (true) or "your
 * berserk just ended" (false). The client fades the blood vignette in or out
 * (BerserkVignette).
 *
 * Needed because berserk depends on combat timestamps that only the SERVER
 * keeps (BerserkMode), while the vignette is drawn on the CLIENT. It is only
 * sent when the state changes, not every tick.
 */
public record BerserkStatePayload(boolean active) implements FabricPacket {

    public static final PacketType<BerserkStatePayload> TYPE = PacketType.create(
            new Identifier(Exsanguinate.MOD_ID, "berserk_state"),
            buf -> new BerserkStatePayload(buf.readBoolean())
    );

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(active);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
