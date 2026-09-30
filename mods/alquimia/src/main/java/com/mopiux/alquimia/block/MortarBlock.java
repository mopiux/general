package com.mopiux.alquimia.block;

import com.mopiux.alquimia.advancement.AlchemyTrigger;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.AlchemyIngredient;
import com.mopiux.alquimia.alchemy.Grinding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Mortero: clic derecho con un ingrediente para ponerlo, clic derecho con la mano vacía para
 * molerlo y clic derecho agachado para sacarlo. Un ingrediente molido aporta su camino completo.
 */
public class MortarBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(box(3, 0, 3, 13, 2, 13), box(2, 2, 2, 14, 6, 14));

    public MortarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MortarBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MortarBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        ItemStack inside = be.stack();

        // Sacar el contenido
        if (player.isShiftKeyDown() && !inside.isEmpty()) {
            if (!level.isClientSide) {
                if (!player.getInventory().add(inside.copy())) player.drop(inside.copy(), false);
                be.setStack(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Poner un ingrediente
        if (!held.isEmpty()) {
            AlchemyIngredient ing = AlchemyData.get(level).find(held);
            if (ing == null) {
                // No es un ingrediente: dejar que el ítem haga lo suyo (por ejemplo, colocar un bloque al lado)
                if (inside.isEmpty() && !level.isClientSide && held.getItem() instanceof net.minecraft.world.item.BlockItem == false) {
                    player.displayClientMessage(Component.translatable("message.alquimia.mortar.not_ingredient"), true);
                }
                return InteractionResult.PASS;
            }
            if (ing.isTransform()) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.alquimia.mortar.cannot_grind"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (inside.isEmpty() || ItemStack.isSameItemSameTags(inside, held)) {
                int room = MortarBlockEntity.MAX_ITEMS - inside.getCount();
                if (room <= 0) return InteractionResult.CONSUME;
                if (!level.isClientSide) {
                    int moved = Math.min(room, held.getCount());
                    ItemStack newStack = held.copyWithCount(inside.getCount() + moved);
                    if (!player.getAbilities().instabuild) held.shrink(moved);
                    be.setStack(newStack);
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }

        // Moler con la mano vacía
        if (!inside.isEmpty()) {
            if (Grinding.get(inside) >= 1f) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.alquimia.mortar.done"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                int p = be.progress() + 1;
                level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35f, 1.4f + level.random.nextFloat() * 0.3f);
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, inside), pos.getX() + 0.5, pos.getY() + 0.4,
                            pos.getZ() + 0.5, 6, 0.12, 0.05, 0.12, 0.05);
                }
                if (p >= MortarBlockEntity.GRIND_STEPS) {
                    ItemStack ground = inside.copy();
                    Grinding.set(ground, 1f);
                    be.setStack(ground);
                    level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
                    player.displayClientMessage(Component.translatable("message.alquimia.mortar.ground", ground.getHoverName()), true);
                    if (player instanceof ServerPlayer sp) AlchemyTrigger.INSTANCE.trigger(sp, "grind", 1);
                } else {
                    be.setProgress(p);
                }
                player.causeFoodExhaustion(0.05f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MortarBlockEntity be && !be.stack().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.stack());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
