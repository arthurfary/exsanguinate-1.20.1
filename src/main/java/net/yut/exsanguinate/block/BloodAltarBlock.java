package net.yut.exsanguinate.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.yut.exsanguinate.block.entity.BloodAltarBlockEntity;
import net.yut.exsanguinate.block.entity.ModBlockEntities;
import net.yut.exsanguinate.item.LoreItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Blood Altar (shown in game as the Altar of the Lesser Zenith) - where the Carnelian
 * Oath is forged.
 *
 * Right-click it with an item to lay ONE of it on top (up to 4 items),
 * right-click with an empty hand to take the last one back. The items float
 * above the stone (BloodAltarRenderer). Once the right offering is in place
 * the altar seeps red spores and hums, waiting: kill a creature nearby and the ritual
 * begins (see BloodAltarBlockEntity and Rituals).
 *
 * The block itself is just the stone; everything it remembers lives in its
 * block entity.
 */
public class BloodAltarBlock extends BlockWithEntity {

    // Same boxes as the block model (models/block/blood_altar.json), in
    // pixels: a wide foot, a narrower column and a thick slab on top.
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1, 0, 1, 15, 3, 15),
            Block.createCuboidShape(3, 3, 3, 13, 10, 13),
            Block.createCuboidShape(0, 10, 0, 16, 14, 16));

    public BloodAltarBlock(Settings settings) {
        super(settings);
    }

    /**
     * BlockWithEntity defaults to INVISIBLE (chests draw themselves with a
     * renderer). The altar is a normal JSON model, so we ask for MODEL.
     */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BloodAltarBlockEntity(pos, state);
    }

    /** The block entity ticks on both sides: server runs the ritual, client draws it. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.BLOOD_ALTAR, BloodAltarBlockEntity::tick);
    }

    /**
     * Right click: holding something = lay one on the altar, empty hand =
     * take the last item back. The client only answers "yes, swing the arm";
     * the server does the actual moving of items and syncs the result back.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof BloodAltarBlockEntity altar)) {
            return ActionResult.PASS;
        }
        // Mid-ritual the offering is sealed.
        if (altar.isRitualActive()) {
            return ActionResult.CONSUME;
        }

        ItemStack held = player.getStackInHand(hand);
        if (!held.isEmpty()) {
            if (altar.isFull()) {
                return ActionResult.PASS;
            }
            if (!world.isClient) {
                ItemStack one = held.copy();
                one.setCount(1);
                altar.addItem(one);
                if (!player.getAbilities().creativeMode) {
                    held.decrement(1);
                }
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1.0f, 0.8f);
            }
            return ActionResult.success(world.isClient);
        }

        // Empty hand: only the main hand takes things back, so one click
        // doesn't run twice (Minecraft tries both hands).
        if (hand == Hand.MAIN_HAND && !altar.isEmpty()) {
            if (!world.isClient) {
                player.setStackInHand(hand, altar.takeLastItem());
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1.0f, 0.8f);
            }
            return ActionResult.success(world.isClient);
        }
        return ActionResult.PASS;
    }

    /**
     * Called when the block is replaced (broken, exploded...). Drop whatever
     * was lying on it, like a chest drops its contents.
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof BloodAltarBlockEntity altar) {
                ItemScatterer.spawn(world, pos, altar.getItems());
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** Lore only (see LoreItem). */
    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        LoreItem.appendLore(tooltip, getTranslationKey());
    }
}
