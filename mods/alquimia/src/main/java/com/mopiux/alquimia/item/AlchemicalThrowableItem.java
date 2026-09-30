package com.mopiux.alquimia.item;

import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Elixir arrojadizo o persistente. Al lanzarse crea una poción arrojadiza de vanilla con los
 * mismos datos, así que se comporta igual que una poción normal (salpicadura o nube).
 */
public class AlchemicalThrowableItem extends AlchemicalPotionItem {
    private final boolean lingering;

    public AlchemicalThrowableItem(Properties properties, boolean lingering) {
        super(properties);
        this.lingering = lingering;
    }

    public boolean isLingering() {
        return lingering;
    }

    public ItemStack toVanilla(ItemStack stack) {
        ItemStack thrown = new ItemStack(lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION);
        if (stack.getTag() != null) thrown.setTag(stack.getTag().copy());
        return thrown;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                lingering ? SoundEvents.LINGERING_POTION_THROW : SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ThrownPotion potion = new ThrownPotion(level, player);
            potion.setItem(toVanilla(stack));
            potion.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(potion);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 0;
    }

    /** Comportamiento para dispensadores (igual que las pociones arrojadizas de vanilla). */
    public DispenseItemBehavior dispenseBehavior() {
        return (source, stack) -> new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level level, Position pos, ItemStack s) {
                ThrownPotion potion = new ThrownPotion(level, pos.x(), pos.y(), pos.z());
                potion.setItem(toVanilla(s));
                return potion;
            }

            @Override
            protected float getUncertainty() {
                return super.getUncertainty() * 0.5F;
            }

            @Override
            protected float getPower() {
                return super.getPower() * 1.25F;
            }
        }.dispense(source, stack);
    }
}
