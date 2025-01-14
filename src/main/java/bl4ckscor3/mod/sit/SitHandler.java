package bl4ckscor3.mod.sit;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber(modid=Sit.MODID)
public class SitHandler
{
	@SubscribeEvent
	public static void onRightClickBlock(RightClickBlock event)
	{
		if(!event.getWorld().isRemote && event.getFace() == EnumFacing.UP && !SitUtil.isPlayerSitting(event.getEntityPlayer()))
		{
			World world = event.getWorld();
			BlockPos pos = event.getPos();
			IBlockState state = world.getBlockState(pos);
			Block block = world.getBlockState(pos).getBlock();
			EntityPlayer player = event.getEntityPlayer();

			if(isValidBlock(world, pos, state, block) && isPlayerInRange(player, pos) && !SitUtil.isOccupied(world, pos) && player.getHeldItemMainhand().isEmpty())
			{
				IBlockState stateAbove = world.getBlockState(pos.up());

				if(!stateAbove.getBlock().isAir(stateAbove, world, pos.up()))
					return;
				else if(block instanceof BlockSlab && (!state.getProperties().containsKey(BlockSlab.HALF) || state.getValue(BlockSlab.HALF) != BlockSlab.EnumBlockHalf.BOTTOM))
					return;
				else if(block instanceof BlockStairs && (!state.getProperties().containsKey(BlockStairs.HALF) || state.getValue(BlockStairs.HALF) != BlockStairs.EnumHalf.BOTTOM))
					return;

				EntitySit sit = new EntitySit(world, pos);

				if(SitUtil.addSitEntity(world, pos, sit))
				{
					world.spawnEntity(sit);
					player.startRiding(sit);
				}
			}
		}
	}

	@SubscribeEvent
	public static void onBreak(BreakEvent event)
	{
		if(!event.getWorld().isRemote)
		{
			EntitySit entity = SitUtil.getSitEntity(event.getWorld(), event.getPos());

			if(entity != null && SitUtil.removeSitEntity(event.getWorld(), event.getPos()))
				entity.setDead();
		}
	}

	@SubscribeEvent
	public static void onEntityMount(EntityMountEvent event)
	{
		if(!event.getWorldObj().isRemote && event.isDismounting())
		{
			Entity entity = event.getEntityBeingMounted();

			if(entity instanceof EntitySit && SitUtil.removeSitEntity(event.getWorldObj(), entity.getPosition()))
				entity.setDead();
		}
	}

	/**
	 * Returns whether or not the given block can be sat on
	 * @param world The world to check in
	 * @param pos The position to check at
	 * @param state The block state at the given position in the given world
	 * @param block The block to check
	 * @return true if the given block can be sat one, false otherwhise
	 */
	private static boolean isValidBlock(World world, BlockPos pos, IBlockState state, Block block)
	{
		boolean isValid = block instanceof BlockSlab || block instanceof BlockStairs || isModBlock(world, pos, block);

		if(!isValid && block instanceof BlockBed)
		{
			state = world.getBlockState(pos.offset(state.getValue(BlockBed.PART) == EnumPartType.HEAD ? state.getValue(BlockBed.FACING).getOpposite() : state.getValue(BlockBed.FACING)));

			if(!(state.getBlock() instanceof BlockBed)) //it's half a bed!
				isValid = true;
		}

		return isValid;
	}

	/**
	 * Checks whether the given block is a specific block from a mod. Used to support stairs/slabs from other mods that don't work with Sit by default.
	 * @param world The world to check in
	 * @param pos The position to check at
	 * @param block The block to check
	 * @return true if the block is a block to additionally support, false otherwise
	 */
	private static boolean isModBlock(World world, BlockPos pos, Block block)
	{
		if(Loader.isModLoaded("immersiveengineering") && block instanceof blusunrize.immersiveengineering.common.blocks.BlockIESlab)
			return true;
		else if(Loader.isModLoaded("architecturecraft") && block instanceof com.elytradev.architecture.common.block.BlockShape)
		{
			TileEntity te = world.getTileEntity(pos);

			if(te instanceof com.elytradev.architecture.common.tile.TileShape)
			{
				return Arrays.asList(com.elytradev.architecture.common.shape.Shape.SLAB,
						com.elytradev.architecture.common.shape.Shape.STAIRS,
						com.elytradev.architecture.common.shape.Shape.STAIRS_INNER_CORNER,
						com.elytradev.architecture.common.shape.Shape.STAIRS_OUTER_CORNER
						).contains(((com.elytradev.architecture.common.tile.TileShape)te).getShape());
			}
		}

		return false;
	}

	/**
	 * Returns whether or not the player is close enough to the block to be able to sit on it
	 * @param player The player
	 * @param pos The position of the block to sit on
	 * @return true if the player is close enough, false otherwhise
	 */
	private static boolean isPlayerInRange(EntityPlayer player, BlockPos pos)
	{
		BlockPos playerPos = player.getPosition();

		if(Configuration.blockReachDistance == 0) //player has to stand on top of the block
			return playerPos.getY() - pos.getY() <= 1 && playerPos.getX() - pos.getX() == 0 && playerPos.getZ() - pos.getZ() == 0;

		pos = pos.add(0.5D, 0.5D, 0.5D);

		AxisAlignedBB range = new AxisAlignedBB(pos.getX() + Configuration.blockReachDistance, pos.getY() + Configuration.blockReachDistance, pos.getZ() + Configuration.blockReachDistance, pos.getX() - Configuration.blockReachDistance, pos.getY() - Configuration.blockReachDistance, pos.getZ() - Configuration.blockReachDistance);

		playerPos = playerPos.add(0.5D, 0.5D, 0.5D);
		return range.minX <= playerPos.getX() && range.minY <= playerPos.getY() && range.minZ <= playerPos.getZ() && range.maxX >= playerPos.getX() && range.maxY >= playerPos.getY() && range.maxZ >= playerPos.getZ();
	}
}
